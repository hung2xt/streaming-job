#!/bin/bash

# Build script for Flink Schema Evolution project
# This script compiles the Java code and creates a fat JAR

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================"
echo "Building Flink Schema Evolution Project"
echo "========================================"

# Clean previous build
echo "Cleaning previous build..."
mvn clean

# Compile and package
echo "Compiling and packaging..."
mvn package -DskipTests

# Check if build was successful
if [ -f "target/flink-schema-evolution-1.0-SNAPSHOT.jar" ]; then
    echo ""
    echo "========================================"
    echo "Build successful!"
    echo "JAR file: target/flink-schema-evolution-1.0-SNAPSHOT.jar"
    echo "========================================"
else
    echo "Build failed! JAR file not found."
    exit 1
fi

# Optional: Setup Python producer environment
echo ""
echo "Setting up Python producer environment..."
if [ -d "producers" ]; then
    cd producers
    if [ ! -d "venv" ]; then
        echo "Creating Python virtual environment..."
        python3 -m venv venv
    fi
    echo "Installing Python dependencies..."
    source venv/bin/activate
    pip install -q -r requirements.txt
    deactivate
    echo "Python environment ready."
    cd ..
fi

echo ""
echo "Build complete! Run ./run.sh to start the Flink job."
