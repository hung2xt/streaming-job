#!/bin/bash
# Submit Flink Job to Cluster

# IMPORTANT: Update this path to your Flink installation
FLINK_HOME=${FLINK_HOME:-"$HOME/bigdata/flink-1.19.1"}
JAR_FILE=target/kafka-flink-iceberg-1.0-SNAPSHOT.jar

echo "============================================================"
echo "🚀 Submitting Flink Job to Cluster"
echo "============================================================"

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found: $JAR_FILE"
    echo "   Run './build.sh' first!"
    exit 1
fi

# Check if Flink cluster is running
echo ""
echo "🔍 Checking Flink cluster..."
if curl -s http://localhost:8081/overview > /dev/null 2>&1; then
    echo "   ✅ Flink cluster is running at http://localhost:8081"
else
    echo "   ❌ Flink cluster is not running!"
    echo ""
    echo "   Start it with:"
    echo "   export JAVA_HOME=/path/to/java-11"
    echo "   $FLINK_HOME/bin/start-cluster.sh"
    exit 1
fi

# Set Hadoop configuration directory for S3A credentials
export HADOOP_CONF_DIR="$FLINK_HOME/hadoop-conf"

# Submit job
echo ""
echo "📤 Submitting job..."
echo "   JAR: $JAR_FILE"
echo "   Hadoop Config: $HADOOP_CONF_DIR"
echo ""

$FLINK_HOME/bin/flink run $JAR_FILE

if [ $? -eq 0 ]; then
    echo ""
    echo "============================================================"
    echo "✅ JOB SUBMITTED SUCCESSFULLY!"
    echo "============================================================"
    echo ""
    echo "🌐 Monitor in Flink UI:"
    echo "   http://localhost:8081"
    echo ""
    echo "📊 To view running jobs:"
    echo "   $FLINK_HOME/bin/flink list"
    echo ""
    echo "🛑 To cancel a job:"
    echo "   $FLINK_HOME/bin/flink cancel <JOB_ID>"
    echo "============================================================"
else
    echo ""
    echo "❌ JOB SUBMISSION FAILED!"
    echo "   Check errors above"
    exit 1
fi
