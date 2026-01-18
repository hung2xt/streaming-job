# Kafka → Flink → Iceberg Streaming Pipeline

A production-ready streaming data pipeline that reads from Kafka, performs stream-stream joins in Apache Flink, and writes to Apache Iceberg tables on MinIO (S3-compatible storage).

## Architecture

```
┌─────────┐    ┌─────────┐
│ Orders  │───▶│         │
│ Topic   │    │  Flink  │    ┌──────────┐    ┌────────┐
└─────────┘    │ Stream  │───▶│ Iceberg  │───▶│ MinIO  │
┌─────────┐    │  Join   │    │  Table   │    │  (S3)  │
│Customer │───▶│         │    └──────────┘    └────────┘
│ Topic   │    │         │
└─────────┘    └─────────┘
```

## Features

✅ Stream-stream LEFT JOIN on customer_id
✅ Exactly-once semantics with Flink checkpointing
✅ Iceberg ACID transactions
✅ S3-compatible storage (MinIO)
✅ Parquet data format
✅ Time-travel and schema evolution support

## Prerequisites

### Required Software

- **Java 11** (OpenJDK recommended)
- **Apache Flink 1.19.1**
- **Apache Kafka 2.6.3+**
- **MinIO** (or AWS S3)
- **Maven 3.6+**

### Version Compatibility

| Component | Version | Notes |
|-----------|---------|-------|
| Flink | 1.19.1 | Required for Iceberg classloader fixes |
| Java | 11 | Flink 1.19 requires Java 11 |
| Iceberg | 1.4.3 | Compatible with Flink 1.17+ runtime |
| Kafka Connector | 1.17.1 | SQL connector for Flink Table API |
| Hadoop AWS | 3.3.4 | S3A filesystem support |

## Project Structure

```
kafka-flink-iceberg/
├── README.md                 # This file
├── flink-job/
│   ├── pom.xml              # Maven dependencies
│   ├── src/
│   │   └── main/java/com/example/flink/
│   │       ├── KafkaFlinkIcebergJob.java  # Main job
│   │       ├── KafkaFlinkPostgresJob.java # Alternative PostgreSQL sink
│   │       └── KafkaFlinkTestJob.java     # Test job with console output
│   ├── build.sh             # Build script
│   └── run.sh              # Job submission script
├── config/
│   ├── flink-conf.yaml      # Flink cluster configuration
│   └── core-site.xml        # Hadoop S3A configuration
├── scripts/
│   └── kafka-producers/     # Kafka data producers
└── docs/
    └── TROUBLESHOOTING.md   # Common issues and solutions
```

## Setup Instructions

### 1. Install Apache Flink 1.19.1

```bash
cd ~/bigdata
wget https://archive.apache.org/dist/flink/flink-1.19.1/flink-1.19.1-bin-scala_2.12.tgz
tar -xzf flink-1.19.1-bin-scala_2.12.tgz
```

### 2. Configure Flink

Copy the configuration file:

```bash
cp config/flink-conf.yaml ~/bigdata/flink-1.19.1/conf/
```

Create Hadoop configuration directory:

```bash
mkdir -p ~/bigdata/flink-1.19.1/hadoop-conf
cp config/core-site.xml ~/bigdata/flink-1.19.1/hadoop-conf/
```

### 3. Download Required JARs

Copy these JARs to `~/bigdata/flink-1.19.1/lib/`:

```bash
# Kafka connector
wget https://repo1.maven.org/maven2/org/apache/flink/flink-sql-connector-kafka/1.17.1/flink-sql-connector-kafka-1.17.1.jar

# Iceberg Flink runtime
wget https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-flink-runtime-1.17/1.4.3/iceberg-flink-runtime-1.17-1.4.3.jar

# Hadoop AWS dependencies
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/3.3.4/hadoop-aws-3.3.4.jar
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-client-api/3.3.4/hadoop-client-api-3.3.4.jar
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-client-runtime/3.3.4/hadoop-client-runtime-3.3.4.jar
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-common/3.3.4/hadoop-common-3.3.4.jar

# AWS SDK v1
wget https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/1.12.262/aws-java-sdk-bundle-1.12.262.jar

# Commons Logging
wget https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar
```

### 4. Configure MinIO

Update `config/core-site.xml` with your MinIO credentials:

```xml
<property>
    <name>fs.s3a.endpoint</name>
    <value>http://localhost:9000</value>
</property>
<property>
    <name>fs.s3a.access.key</name>
    <value>YOUR_ACCESS_KEY_HERE</value>  <!-- Replace with your MinIO access key -->
</property>
<property>
    <name>fs.s3a.secret.key</name>
    <value>YOUR_SECRET_KEY_HERE</value>  <!-- Replace with your MinIO secret key -->
</property>
```

**Note:** For MinIO default installation, use `minioadmin` for both access and secret keys.

Create the MinIO bucket:

```bash
mc mb myminio/iceberg-data-warehouse
```

### 5. Build the Flink Job

```bash
cd flink-job
export JAVA_HOME=/path/to/java-11
./build.sh
```

This creates `target/kafka-flink-iceberg-1.0-SNAPSHOT.jar`.

## Running the Pipeline

### 1. Start Flink Cluster

```bash
export JAVA_HOME=/path/to/java-11
cd ~/bigdata/flink-1.19.1
./bin/start-cluster.sh
```

Verify cluster is running:
```bash
curl http://localhost:8081/overview
```

### 2. Start Kafka Producers

In separate terminals:

```bash
# Terminal 1: Orders producer
cd scripts/kafka-producers
python3 orders_producer.py

# Terminal 2: Customers producer
python3 customers_producer.py
```

### 3. Submit Flink Job

```bash
cd flink-job
export JAVA_HOME=/path/to/java-11
./run.sh
```

Expected output:
```
============================================================
🚀 Submitting Flink Job to Cluster
============================================================
✅ Checkpointing enabled (30s interval)
✅ kafka_orders created
✅ kafka_customers created
✅ Iceberg catalog created
✅ enriched_orders table created
Job has been submitted with JobID xxxxxxxxxx
```

### 4. Monitor the Job

**Flink Web UI:**
```
http://localhost:8081
```

**Check running jobs:**
```bash
export JAVA_HOME=/path/to/java-11
~/bigdata/flink-1.19.1/bin/flink list
```

**View checkpoints:**
```bash
curl http://localhost:8081/jobs/<JOB_ID>/checkpoints | python3 -m json.tool
```

## Verifying Data in Iceberg

### Check MinIO for data files

```bash
mc ls -r myminio/iceberg-data-warehouse/warehouse/default/enriched_orders/
```

Expected output:
```
[timestamp] 16KiB data/00000-0-xxx.parquet
[timestamp] 4.3KiB data/00001-0-xxx.parquet
[timestamp] 3.1KiB metadata/v2.metadata.json
```

### Check record count

```bash
mc cat myminio/iceberg-data-warehouse/warehouse/default/enriched_orders/metadata/v2.metadata.json | \
  python3 -c "import sys, json; data = json.load(sys.stdin); \
  snap = [s for s in data['snapshots'] if s['snapshot-id'] == data['current-snapshot-id']][0]; \
  print(f\"Total records: {snap['summary'].get('total-records', 0)}\")"
```

## Schema

### Source Tables (Kafka)

**kafka_orders:**
```sql
order_id STRING
customer_id BIGINT
product_id STRING
product_name STRING
quantity INT
total_amount DOUBLE
order_time STRING
status STRING
```

**kafka_customers:**
```sql
customer_id BIGINT
name STRING
email STRING
city STRING
tier STRING
loyalty_points INT
```

### Target Table (Iceberg)

**enriched_orders:**
```sql
order_id STRING
customer_id BIGINT
customer_name STRING
email STRING
city STRING
tier STRING
loyalty_points INT
product_id STRING
product_name STRING
quantity INT
total_amount DOUBLE
```

## Configuration

### Checkpointing

Configured for **30-second intervals** to balance between:
- Data freshness (more frequent commits)
- System overhead (checkpoint cost)

Adjust in `KafkaFlinkIcebergJob.java`:
```java
env.enableCheckpointing(30000); // milliseconds
```

### Parallelism

Default parallelism: **2**

Adjust in code:
```java
tableEnv.getConfig().set("parallelism.default", "2");
```

Or configure per-operator in Flink UI.

### Memory Settings

In `flink-conf.yaml`:
```yaml
jobmanager.memory.process.size: 1600m
taskmanager.memory.process.size: 1728m
taskmanager.numberOfTaskSlots: 2
```

## Troubleshooting

### Job fails with ClassNotFoundException

**Issue:** Missing Iceberg/Hadoop dependencies

**Solution:**
1. Verify all JARs are in `~/bigdata/flink-1.19.1/lib/`
2. Check `classloader.resolve-order: parent-first` in `flink-conf.yaml`

### No data written to Iceberg

**Issue:** Checkpointing not enabled

**Solution:**
- Verify checkpointing is enabled in job code
- Check checkpoint metrics: `curl http://localhost:8081/jobs/<JOB_ID>/checkpoints`
- Should show `completed > 0`

### TaskManager not connecting

**Issue:** Missing `jobmanager.rpc.address` in config

**Solution:**
Add to `flink-conf.yaml`:
```yaml
jobmanager.rpc.address: localhost
```

### Java version mismatch

**Issue:** Flink 1.19 requires Java 11

**Solution:**
```bash
export JAVA_HOME=/path/to/java-11
# Verify
java -version  # Should show 11.x
```

## Performance Tuning

### Checkpoint Interval

- **Fast commits:** 10-30 seconds (higher overhead)
- **Balanced:** 30-60 seconds (recommended)
- **Large batches:** 60-300 seconds (lower overhead)

### Write Parallelism

Iceberg writes are parallelized. Increase for higher throughput:

```java
tableEnv.getConfig().set("parallelism.default", "4");
```

### Kafka Consumer Configuration

Adjust in Kafka table DDL:
```sql
'scan.startup.mode' = 'latest-offset'  -- Only new messages
'scan.startup.mode' = 'earliest-offset' -- All messages from beginning
```

## Advanced Features

### Time Travel Queries

Query historical data using Spark/Trino:
```sql
SELECT * FROM enriched_orders
FOR SYSTEM_TIME AS OF '2024-01-15 10:00:00'
```

### Schema Evolution

Add columns without rewriting data:
```sql
ALTER TABLE enriched_orders
ADD COLUMN discount_amount DOUBLE
```

### Compaction

Merge small files for better query performance:
```sql
CALL iceberg_catalog.system.rewrite_data_files(
  table => 'default.enriched_orders',
  strategy => 'binpack'
)
```

## Monitoring

### Key Metrics

- **Checkpoints completed:** Should increase every 30s
- **Records in/out:** Verify data flow
- **Backpressure:** Should be LOW or OK
- **Kafka lag:** Consumer group offset lag

### Logs

```bash
# JobManager logs
tail -f ~/bigdata/flink-1.19.1/log/flink-*-standalonesession*.log

# TaskManager logs
tail -f ~/bigdata/flink-1.19.1/log/flink-*-taskexecutor*.log
```

## Stopping the Job

```bash
# List jobs
export JAVA_HOME=/path/to/java-11
~/bigdata/flink-1.19.1/bin/flink list

# Cancel job
~/bigdata/flink-1.19.1/bin/flink cancel <JOB_ID>

# Stop cluster
~/bigdata/flink-1.19.1/bin/stop-cluster.sh
```

## References

- [Apache Flink Documentation](https://nightlies.apache.org/flink/flink-docs-release-1.19/)
- [Apache Iceberg Documentation](https://iceberg.apache.org/docs/latest/)
- [Flink-Iceberg Integration](https://iceberg.apache.org/docs/latest/flink/)
- [MinIO Documentation](https://min.io/docs/minio/linux/index.html)

## License

MIT License - See LICENSE file for details

## Contributing

Pull requests welcome! Please ensure:
1. Code follows existing style
2. Tests pass
3. Documentation updated
