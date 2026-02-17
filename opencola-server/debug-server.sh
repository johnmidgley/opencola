#!/bin/bash
# Debug script for OpenCola Server
# This script runs the server with debug port 5005 open

echo "Starting OpenCola Server in debug mode..."
echo "Debug port: 5005"
echo "Attach your debugger to localhost:5005"
echo ""

export JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
./gradlew :opencola-server:run
