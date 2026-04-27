#!/bin/bash
# Build Flink Job JAR

echo "============================================================"
echo "🔨 Building Flink Job"
echo "============================================================"

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" != "11" ]; then
    echo "❌ Java 11 is required!"
    echo "   Current version: $(java -version 2>&1 | head -1)"
    echo ""
    echo "   Set JAVA_HOME to Java 11:"
    echo "   export JAVA_HOME=/path/to/java-11"
    exit 1
fi

echo "✅ Java version: $(java -version 2>&1 | head -1)"
echo ""

# Clean and build
echo "📦 Cleaning previous builds..."
mvn clean -q

echo "🔧 Compiling and packaging..."
mvn package -q

if [ $? -eq 0 ]; then
    echo ""
    echo "============================================================"
    echo "✅ BUILD SUCCESSFUL!"
    echo "============================================================"
    echo ""
    echo "📦 JAR location:"
    echo "   $(pwd)/target/kafka-flink-iceberg-1.0-SNAPSHOT.jar"
    echo ""
    JAR_SIZE=$(ls -lh target/kafka-flink-iceberg-1.0-SNAPSHOT.jar | awk '{print $5}')
    echo "📊 JAR size: $JAR_SIZE"
    echo "============================================================"
else
    echo ""
    echo "❌ BUILD FAILED!"
    echo "   Check errors above"
    exit 1
fi
