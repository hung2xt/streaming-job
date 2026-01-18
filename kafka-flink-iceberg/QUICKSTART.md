# Quick Start Guide

Get the Kafka → Flink → Iceberg pipeline running in 10 minutes!

## Prerequisites Check

Before starting, verify you have:

```bash
# Java 11
java -version  # Should show version 11.x

# Maven
mvn -version   # Should show 3.6+

# Python 3
python3 --version

# Kafka running
netstat -an | grep 9093  # Should show LISTEN

# MinIO running
mc admin info myminio  # Should show server info
```

## Step-by-Step Setup

### 1. Download and Install Flink 1.19.1 (5 minutes)

```bash
cd ~/bigdata

# Download Flink
wget https://archive.apache.org/dist/flink/flink-1.19.1/flink-1.19.1-bin-scala_2.12.tgz
tar -xzf flink-1.19.1-bin-scala_2.12.tgz

# Copy configuration
cp config/flink-conf.yaml ~/bigdata/flink-1.19.1/conf/

# Setup Hadoop config for S3A
mkdir -p ~/bigdata/flink-1.19.1/hadoop-conf
cp config/core-site.xml ~/bigdata/flink-1.19.1/hadoop-conf/
```

### 2. Download Required JARs (3 minutes)

```bash
cd ~/bigdata/flink-1.19.1/lib

# Kafka connector
wget https://repo1.maven.org/maven2/org/apache/flink/flink-sql-connector-kafka/1.17.1/flink-sql-connector-kafka-1.17.1.jar

# Iceberg
wget https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-flink-runtime-1.17/1.4.3/iceberg-flink-runtime-1.17-1.4.3.jar

# Hadoop + AWS
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/3.3.4/hadoop-aws-3.3.4.jar
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-client-api/3.3.4/hadoop-client-api-3.3.4.jar
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-client-runtime/3.3.4/hadoop-client-runtime-3.3.4.jar
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-common/3.3.4/hadoop-common-3.3.4.jar
wget https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/1.12.262/aws-java-sdk-bundle-1.12.262.jar
wget https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar
```

### 3. Build the Job (1 minute)

```bash
cd flink-job

# Set Java 11
export JAVA_HOME=$(/usr/libexec/java_home -v 11)

# Build
./build.sh
```

Expected output:
```
✅ BUILD SUCCESSFUL!
📦 JAR size: 6.8M
```

### 4. Start Services (1 minute)

```bash
# Start Flink cluster
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
~/bigdata/flink-1.19.1/bin/start-cluster.sh

# Verify cluster
curl http://localhost:8081/overview
# Should show: "taskmanagers": 1, "slots-total": 2
```

### 5. Configure Credentials

**IMPORTANT:** Update S3/MinIO credentials before running!

```bash
# Edit core-site.xml with your credentials
nano config/core-site.xml

# Replace YOUR_ACCESS_KEY_HERE and YOUR_SECRET_KEY_HERE
# For MinIO default: minioadmin / minioadmin
```

Or use the template:
```bash
cp config/core-site.xml.template config/core-site.xml
# Then edit with your credentials
```

### 6. Create MinIO Bucket

```bash
mc mb myminio/iceberg-data-warehouse
```

### 7. Start Kafka Producers

```bash
# Terminal 1: Install Python dependencies
cd scripts/kafka-producers
pip3 install -r requirements.txt

# Start orders producer
python3 orders_producer.py
```

```bash
# Terminal 2: Start customers producer
python3 customers_producer.py
```

### 8. Submit Flink Job

```bash
cd flink-job
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
./run.sh
```

Expected output:
```
✅ JOB SUBMITTED SUCCESSFULLY!
Job has been submitted with JobID xxxxxxxxxx
```

## Verify It's Working

### Check Flink UI

Open browser: http://localhost:8081

- Should show 1 running job
- TaskManagers: 1
- Slots: 2/2 used

### Wait for First Checkpoint (30 seconds)

The job commits data to Iceberg every 30 seconds via checkpoints.

```bash
# After 35 seconds, check checkpoints
curl -s http://localhost:8081/jobs/<JOB_ID>/checkpoints | \
  python3 -c "import sys, json; data = json.load(sys.stdin); print(f\"Completed: {data['counts']['completed']}\")"
```

Should show: `Completed: 1` or more

### Check Data in Iceberg

```bash
# List data files
mc ls -r myminio/iceberg-data-warehouse/warehouse/default/enriched_orders/

# Should show Parquet files:
# [timestamp] 16KiB data/00000-0-xxx.parquet
# [timestamp] 4.3KiB data/00001-0-xxx.parquet
```

### Count Records

```bash
mc cat myminio/iceberg-data-warehouse/warehouse/default/enriched_orders/metadata/v2.metadata.json | \
  python3 -c "import sys, json; data = json.load(sys.stdin); \
  snap = [s for s in data['snapshots'] if s['snapshot-id'] == data['current-snapshot-id']][0]; \
  print(f\"Total records: {snap['summary'].get('total-records', 0)}\")"
```

Should show increasing record count!

## Success! 🎉

You now have a working streaming pipeline:

```
Kafka Topics → Flink Stream Join → Iceberg Table (MinIO)
```

Data is being:
- Read from Kafka in real-time
- Joined on customer_id
- Written to Iceberg every 30 seconds
- Stored as Parquet files in MinIO

## What's Next?

1. **Monitor the job:** http://localhost:8081
2. **Check metrics:** Backpressure, throughput, checkpoints
3. **Query data:** Use Spark, Trino, or Athena to query Iceberg table
4. **Tune performance:** Adjust parallelism, checkpoint interval
5. **Read the full README:** For advanced features and troubleshooting

## Stopping Everything

```bash
# Stop Flink job
~/bigdata/flink-1.19.1/bin/flink list
~/bigdata/flink-1.19.1/bin/flink cancel <JOB_ID>

# Stop Flink cluster
~/bigdata/flink-1.19.1/bin/stop-cluster.sh

# Stop producers (Ctrl+C in each terminal)
```

## Troubleshooting

If something doesn't work, see [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)

Common issues:
- **Java version:** Must be Java 11
- **TaskManager not connecting:** Check `jobmanager.rpc.address` in flink-conf.yaml
- **No data in Iceberg:** Wait for first checkpoint (30 seconds)
- **Build fails:** Ensure JAVA_HOME points to Java 11

## Need Help?

Check the full [README.md](README.md) for detailed documentation.
