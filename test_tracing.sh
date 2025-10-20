#!/bin/bash

echo "🚀 Testing FHIR Tracing Setup..."

# Start the server in background
echo "📋 Starting HAPI FHIR server..."
cd /workspace
timeout 60 mvn spring-boot:run > server.log 2>&1 &
SERVER_PID=$!

# Wait for server to start
echo "⏳ Waiting for server to start..."
sleep 30

# Check if server is running
if ps -p $SERVER_PID > /dev/null; then
    echo "✅ Server is running (PID: $SERVER_PID)"
    
    # Test basic endpoint
    echo "🔍 Testing basic FHIR endpoint..."
    curl -s http://localhost:8080/fhir/metadata > /dev/null
    if [ $? -eq 0 ]; then
        echo "✅ FHIR endpoint is responding"
        
        # Test metrics endpoint
        echo "📊 Testing metrics endpoint..."
        curl -s http://localhost:8080/fhir-metrics/summary > /dev/null
        if [ $? -eq 0 ]; then
            echo "✅ Metrics endpoint is working"
        else
            echo "❌ Metrics endpoint not responding"
        fi
    else
        echo "❌ FHIR endpoint not responding"
    fi
else
    echo "❌ Server failed to start"
fi

# Show server logs
echo ""
echo "📋 Server logs:"
tail -20 server.log

# Cleanup
kill $SERVER_PID 2>/dev/null
echo ""
echo "🧹 Cleanup completed"