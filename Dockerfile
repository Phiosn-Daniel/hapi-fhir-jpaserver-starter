FROM docker.io/library/maven:3.9.9-eclipse-temurin-17 AS build-hapi
WORKDIR /tmp/hapi-fhir-jpaserver-starter

ARG OPENTELEMETRY_JAVA_AGENT_VERSION=2.13.1
RUN curl -k -LSsO https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OPENTELEMETRY_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar

COPY m2-cache /root/.m2
COPY pom.xml .
COPY server.xml .
RUN mvn -ntp dependency:go-offline

COPY src/ /tmp/hapi-fhir-jpaserver-starter/src/
RUN mvn clean install -DskipTests -Djdk.lang.Process.launchMechanism=vfork

FROM build-hapi AS build-distroless
RUN mvn package -DskipTests spring-boot:repackage -Pboot
RUN mkdir /app && cp /tmp/hapi-fhir-jpaserver-starter/target/ROOT.war /app/main.war


########### bitnami tomcat version is suitable for debugging and comes with a shell
########### it can be built using eg. `docker build --target tomcat .`
FROM docker.io/bitnamilegacy/tomcat:10.1.43-debian-12-r0 AS tomcat

USER root
RUN rm -rf /opt/bitnami/tomcat/webapps/ROOT && \
    mkdir -p /opt/bitnami/hapi/data/hapi/lucenefiles && \
    chown -R 1001:1001 /opt/bitnami/hapi/data/hapi/lucenefiles && \
    chmod 775 /opt/bitnami/hapi/data/hapi/lucenefiles

RUN mkdir -p /target && chown -R 1001:1001 target
USER 1001

COPY --chown=1001:1001 catalina.properties /opt/bitnami/tomcat/conf/catalina.properties
COPY --chown=1001:1001 server.xml /opt/bitnami/tomcat/conf/server.xml
COPY --from=build-hapi --chown=1001:1001 /tmp/hapi-fhir-jpaserver-starter/target/ROOT.war /opt/bitnami/tomcat/webapps/ROOT.war
COPY --from=build-hapi --chown=1001:1001 /tmp/hapi-fhir-jpaserver-starter/opentelemetry-javaagent.jar /app

ENV ALLOW_EMPTY_PASSWORD=yes

########### distroless brings focus on security and runs on plain spring boot - this is the default image
FROM eclipse-temurin:17-jre AS default

# 安裝 curl 用於健康檢查
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# 創建非 root 用戶
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# 複製應用程式
COPY --chown=appuser:appuser --from=build-distroless /app /app
COPY --chown=appuser:appuser --from=build-hapi /tmp/hapi-fhir-jpaserver-starter/opentelemetry-javaagent.jar /app

# 切換到非 root 用戶
USER appuser

# 加入健康檢查
HEALTHCHECK --interval=10s --timeout=10s --start-period=60s --retries=10 \
    CMD curl -f http://localhost:8080/fhir/metadata || exit 1

ENTRYPOINT ["java", "--class-path", "/app/main.war", "-XX:MaxRAMPercentage=80.0","-Dloader.path=main.war!/WEB-INF/classes/,main.war!/WEB-INF/,/app/extra-classes", "org.springframework.boot.loader.PropertiesLauncher"]