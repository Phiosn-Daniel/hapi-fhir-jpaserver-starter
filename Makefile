# =============================================================================
# Omnichan Makefile
# =============================================================================

# 版本和基本設定
VERSION=$(shell git describe --tags --always)
PROTO_FILES=$(shell find api -name *.proto)

# Go 相關設定
GO_VERSION = 1.25.1
GO_ARCHIVE = go$(GO_VERSION).linux-amd64.tar.gz
GO_URL = https://go.dev/dl/$(GO_ARCHIVE)
GO_PATH = /usr/local/go/bin

# ?= 允許從外部環境變數或命令列參數覆蓋預設值
# Docker 相關變數
DOCKER_REGISTRY ?= harbor.phison.com
DOCKER_NAMESPACE ?= fhir
DOCKER_IMAGE_NAME ?= hapi-fhir
DOCKER_TAG ?= vtest
DOCKER_FULL_IMAGE = $(DOCKER_REGISTRY)/$(DOCKER_NAMESPACE)/$(DOCKER_IMAGE_NAME):$(DOCKER_TAG)

# Docker 登入相關變數
DOCKER_USERNAME ?= ai_sw
DOCKER_PASSWORD ?= AIsw9999

# =============================================================================
# 主要目標
# =============================================================================

.DEFAULT_GOAL := help

.PHONY: help
# 顯示幫助資訊
help:
	@echo ''
	@echo 'Usage:'
	@echo ' make [target]'
	@echo ''
	@echo 'Targets:'
	@awk '/^[a-zA-Z\-0-9]+:/ { \
        helpMessage = match(lastLine, /^# (.*)/); \
                if (helpMessage) { \
                        helpCommand = substr($$1, 0, index($$1, ":")-1); \
                        helpMessage = substr(lastLine, RSTART + 2, RLENGTH); \
 printf "\033[36m%-22s\033[0m %s\n", helpCommand,helpMessage; \
                } \
        } \
        { lastLine = $$0 }' $(MAKEFILE_LIST)

.PHONY: all-docker
# 一鍵：準備環境並在 Docker 模式啟動
all-docker: prepare go-run-docker

.PHONY: all-kube
# 一鍵：準備環境並在 Kubernetes 模式啟動
all-kube: prepare go-run-kube

# 整理 go 依賴（go mod tidy）
.PHONY: tidy
tidy:
	go mod tidy

# 產生程式碼（go generate）
.PHONY: generate
generate:
	go generate ./...

# 聚合：初始化 proxy、整理依賴、產生 proto 與程式碼
.PHONY: prepare
prepare: init-proxy tidy proto generate

# =============================================================================
# Go 開發相關
# =============================================================================

.PHONY: build
# 建構 CLI 程式
build:
	mkdir -p ./bin && go build -ldflags "-w -s -X main.version=$(VERSION)" -o ./bin/ ./cmd/omnichan/...

.PHONY: go-vet
# 檢查程式碼
go-vet:
	go vet ./...

.PHONY: test
# 執行測試
test:
	go test -race ./...
	mkdir -p coverage/unit
	go test -cover ./... -args -test.gocoverdir="$$PWD/coverage/unit"
	go tool covdata textfmt -i=./coverage/unit -o coverage/profile
	go tool cover -func coverage/profile

.PHONY: lint
# 程式碼檢查
lint:
	golangci-lint run

.PHONY: proto
# 產生 protobuf 檔案
proto:
	protoc -I=. \
        --go_out=paths=source_relative:. \
        --go_opt=default_api_level=API_OPAQUE \
        --connect-go_out=paths=source_relative:. \
        $(PROTO_FILES)

.PHONY: wire
# 產生依賴注入程式碼
wire:
	go run -mod=mod github.com/google/wire/cmd/wire ./cmd/omnichan

# =============================================================================
# 環境設定
# =============================================================================

.PHONY: init
# 初始化所有環境
init: openssl init-proxy init-config

.PHONY: init-proxy
# 設定 Go proxy
init-proxy:
	go env -w GONOSUMDB="192.168.1.154"
	go env -w GOPROXY="https://goproxy.phison.com,direct"

.PHONY: init-config
# 初始化設定檔
init-config:
	go run ./cmd/omnichan init omnichan.yaml

.PHONY: openssl
# 安裝 SSL 憑證
openssl:
	apt-get update --allow-insecure-repositories && apt-get install -y ca-certificates openssl --allow-unauthenticated
	openssl s_client -showcerts -connect goproxy.phison.com:443 </dev/null 2>/dev/null|openssl x509 -outform PEM >  /usr/local/share/ca-certificates/proxy.golang.crt
	update-ca-certificates

.PHONY: install-go
# 安裝 Go
install-go:
	@echo "正在下載 Go $(GO_VERSION)..."
	wget -q --show-progress $(GO_URL) -O /tmp/$(GO_ARCHIVE)
	@echo "正在解壓縮 Go 檔案..."
	sudo tar -C /usr/local -xzf /tmp/$(GO_ARCHIVE)
	@echo "正在設定 Go 環境變數..."
	@# 檢查 ~/.bashrc 是否已包含 Go 的路徑
	grep -q "export PATH=$$PATH:$(GO_PATH)" ~/.bashrc || echo 'export PATH=$$PATH:$(GO_PATH)' >> ~/.bashrc
	@echo "清理暫存檔案..."
	rm /tmp/$(GO_ARCHIVE)
	@echo "Go 安裝完成！"
	@echo "請執行 'source ~/.bashrc' 來立即套用變數，或重新啟動您的終端機。"

# =============================================================================
# 執行相關
# =============================================================================

.PHONY: go-run-docker
# 在 Docker 環境下執行
go-run-docker:
	export OMNICHAN_ENV=Docker &&\
        go run ./cmd/omnichan serve --address :8000 --config omnichan.yaml

.PHONY: go-run-kube
# 在 Kubernetes 環境下執行
go-run-kube:
	export OMNICHAN_ENV=Kubernetes &&\
        go run ./cmd/omnichan serve --address :8000 --config omnichan.yaml

.PHONY: call-healthy
# 呼叫健康檢查 API
call-healthy:
		curl -X POST http://localhost:8000/omnichan.healthcheck.v1.HealthCheckService/HealthCheck -H "Content-Type: application/json" -H "Connect-Protocol-Version: 1" -d "{}" && echo

# =============================================================================
# Docker Compose
# =============================================================================

.PHONY: docker-compose
# 啟動 Docker Compose
docker-compose:
	docker compose up

.PHONY: docker-compose-config
# 檢查 Docker Compose 設定
docker-compose-config:
	docker compose config

# =============================================================================
# Docker 相關
# =============================================================================

.PHONY: docker-login
# Docker 登入
docker-login:
	@echo "登入 Docker Registry: $(DOCKER_REGISTRY)"
	@echo "使用帳號: $(DOCKER_USERNAME)"
	docker login $(DOCKER_REGISTRY) -u $(DOCKER_USERNAME) -p $(DOCKER_PASSWORD)

.PHONY: docker-build
# 建構 Docker image
docker-build:
	@echo "建構 Docker image: $(DOCKER_FULL_IMAGE)"
	docker build -t $(DOCKER_FULL_IMAGE) .
	@echo "Docker image 建構完成: $(DOCKER_FULL_IMAGE)"

.PHONY: docker-push
# 推送 Docker image
docker-push:
	@echo "推送 Docker image 到 registry..."
	docker push $(DOCKER_FULL_IMAGE)
	@echo "Docker image 推送完成: $(DOCKER_FULL_IMAGE)"

.PHONY: docker-clean
# 清理本地 Docker images
docker-clean:
		@echo "清理本地 Docker images..."
	docker rmi $(DOCKER_FULL_IMAGE) || true
	@echo "Docker images 清理完成"

.PHONY: docker-info
# 顯示 Docker 相關資訊
docker-info:
	@echo "Docker 設定資訊:"
	@echo "  Registry: $(DOCKER_REGISTRY)"
	@echo "  Namespace: $(DOCKER_NAMESPACE)"
	@echo "  Image Name: $(DOCKER_IMAGE_NAME)"
	@echo "  Version: $(VERSION)"
	@echo "  Tag: $(DOCKER_TAG)"
	@echo "  Full Image: $(DOCKER_FULL_IMAGE)"
	@echo "  Username: $(DOCKER_USERNAME)"
	@echo "  Password: $(DOCKER_PASSWORD)"

.PHONY: docker-run
# 執行 Docker container
docker-run:
	docker run -p 8000:8000 -e OMNICHAN_ENV=Docker $(DOCKER_FULL_IMAGE)

# =============================================================================
# Git Tag 相關 (CI/CD 友善)
# =============================================================================

.PHONY: git-tag-build
# 使用 Git tag 建構並推送
git-tag-build:
	@echo "=== Git Tag 建構流程 ==="
	@$(MAKE) -s docker-login
	@$(MAKE) -s docker-build
	@$(MAKE) -s docker-push
	@echo "版本標籤: $(DOCKER_FULL_IMAGE)"

.PHONY: git-info
# 顯示 Git 和 Docker 版本資訊
git-info:
	@echo "=== Git 和 Docker 版本資訊 ==="
	@echo "Git 版本: $(VERSION)"
	@echo "Git 分支: $(shell git branch --show-current)"
	@echo "Git 提交: $(shell git rev-parse --short HEAD)"
	@$(MAKE) -s docker-info

.PHONY: gitlab-lint
# install linter for gitlab-ci
gitlab-lint:
	go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@latest

.PHONY: gitlab-gocover
# install gocover for gitlab-ci
gitlab-gocover:
	go install github.com/boumenot/gocover-cobertura@latest

.PHONY: gitlab-openssl
# update certificates for gitlab-ci
gitlab-openssl:
	apt-get update && apt-get install -y ca-certificates openssl
	openssl s_client -showcerts -connect goproxy.phison.com:443 </dev/null 2>/dev/null|openssl x509 -outform PEM | tee -a /usr/local/share/ca-certificates/proxy.golang.crt
	update-ca-certificates
