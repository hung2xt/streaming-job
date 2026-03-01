#!/bin/bash

# Run script for Flink Schema Evolution project
# This script submits the job to a Flink cluster or runs locally

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAR_FILE="target/flink-schema-evolution-1.0-SNAPSHOT.jar"

# Job classes
STATIC_SCHEMA_JOB="com.example.flink.KafkaFlinkSchemaEvolutionJob"
DYNAMIC_SCHEMA_JOB="com.example.flink.KafkaFlinkDynamicSchemaJob"

# Default to dynamic schema job (recommended)
MAIN_CLASS="$DYNAMIC_SCHEMA_JOB"

# Default Flink installation path
FLINK_HOME="${FLINK_HOME:-/opt/flink}"

echo "========================================"
echo "Flink Schema Evolution Pipeline"
echo "========================================"

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "JAR file not found: $JAR_FILE"
    echo "Please run ./build.sh first"
    exit 1
fi

# Check run mode
MODE="${1:-local}"

case "$MODE" in
    "local"|"dynamic-local")
        echo "Running DYNAMIC schema job in local mode..."
        echo "Job: $DYNAMIC_SCHEMA_JOB"
        echo ""
        java -cp "$JAR_FILE:$FLINK_HOME/lib/*" "$DYNAMIC_SCHEMA_JOB"
        ;;
    "static-local")
        echo "Running STATIC schema job in local mode..."
        echo "Job: $STATIC_SCHEMA_JOB"
        echo ""
        java -cp "$JAR_FILE:$FLINK_HOME/lib/*" "$STATIC_SCHEMA_JOB"
        ;;
    "cluster"|"dynamic-cluster")
        echo "Submitting DYNAMIC schema job to Flink cluster..."
        echo "Job: $DYNAMIC_SCHEMA_JOB"
        echo ""
        if [ ! -f "$FLINK_HOME/bin/flink" ]; then
            echo "Flink not found at $FLINK_HOME"
            echo "Set FLINK_HOME environment variable or install Flink"
            exit 1
        fi
        "$FLINK_HOME/bin/flink" run -c "$DYNAMIC_SCHEMA_JOB" "$JAR_FILE"
        ;;
    "static-cluster")
        echo "Submitting STATIC schema job to Flink cluster..."
        echo "Job: $STATIC_SCHEMA_JOB"
        echo ""
        if [ ! -f "$FLINK_HOME/bin/flink" ]; then
            echo "Flink not found at $FLINK_HOME"
            echo "Set FLINK_HOME environment variable or install Flink"
            exit 1
        fi
        "$FLINK_HOME/bin/flink" run -c "$STATIC_SCHEMA_JOB" "$JAR_FILE"
        ;;
    "docker"|"dynamic-docker")
        echo "Submitting DYNAMIC schema job to Flink cluster via Docker..."
        echo "Job: $DYNAMIC_SCHEMA_JOB"
        echo ""
        # Copy JAR to Flink jobmanager container
        docker cp "$JAR_FILE" flink-jobmanager:/opt/flink/usrlib/
        # Submit the job
        docker exec flink-jobmanager flink run \
            -c "$DYNAMIC_SCHEMA_JOB" \
            /opt/flink/usrlib/flink-schema-evolution-1.0-SNAPSHOT.jar
        ;;
    "static-docker")
        echo "Submitting STATIC schema job to Flink cluster via Docker..."
        echo "Job: $STATIC_SCHEMA_JOB"
        echo ""
        # Copy JAR to Flink jobmanager container
        docker cp "$JAR_FILE" flink-jobmanager:/opt/flink/usrlib/
        # Submit the job
        docker exec flink-jobmanager flink run \
            -c "$STATIC_SCHEMA_JOB" \
            /opt/flink/usrlib/flink-schema-evolution-1.0-SNAPSHOT.jar
        ;;
    "producer")
        echo "Running Avro producer..."
        echo ""
        cd producers
        if [ ! -d "venv" ]; then
            echo "Python environment not set up. Running build.sh first..."
            cd ..
            ./build.sh
            cd producers
        fi
        source venv/bin/activate
        python avro_orders_producer.py
        deactivate
        ;;
    *)
        echo "Usage: $0 [MODE]"
        echo ""
        echo "DYNAMIC Schema Jobs (Recommended - auto-adapts to schema changes):"
        echo "  local          - Run dynamic job locally (default)"
        echo "  cluster        - Submit dynamic job to Flink cluster"
        echo "  docker         - Submit dynamic job via Docker"
        echo ""
        echo "STATIC Schema Jobs (Original - hardcoded schema):"
        echo "  static-local   - Run static job locally"
        echo "  static-cluster - Submit static job to Flink cluster"
        echo "  static-docker  - Submit static job via Docker"
        echo ""
        echo "Other:"
        echo "  producer       - Run the Avro producer"
        echo ""
        echo "Examples:"
        echo "  ./run.sh local      # Run dynamic schema job locally"
        echo "  ./run.sh producer   # Start sending test messages"
        exit 1
        ;;
esac
