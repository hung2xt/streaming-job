# Troubleshooting Guide

Common issues and solutions for the Kafka → Flink → Iceberg pipeline.

## Build Issues

### Error: "class file version 55.0" or "Unsupported class version"

**Problem:** JAR compiled with Java 11 but Flink running on Java 8

**Solution:**
```bash
# Set Java 11 before building AND running
export JAVA_HOME=/path/to/java-11
java -version  # Should show 11.x

# Rebuild
cd flink-job
./build.sh

# Restart Flink with Java 11
export JAVA_HOME=/path/to/java-11
~/bigdata/flink-1.19.1/bin/stop-cluster.sh
~/bigdata/flink-1.19.1/bin/start-cluster.sh
```

### Error: "Failed to execute goal... compilation failure"

**Problem:** Missing Java 11 or wrong JAVA_HOME

**Solution:**
```bash
# Find Java 11
/usr/libexec/java_home -V

# Set JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
```

## Flink Cluster Issues

### Error: "TaskManager not connecting" or "slots-total: 0"

**Problem:** Missing `jobmanager.rpc.address` in configuration

**Solution:**
Add to `~/bigdata/flink-1.19.1/conf/flink-conf.yaml`:
```yaml
jobmanager.rpc.address: localhost
```

Then restart:
```bash
~/bigdata/flink-1.19.1/bin/stop-cluster.sh
~/bigdata/flink-1.19.1/bin/start-cluster.sh
```

### Error: "Couldn't retrieve standalone cluster"

**Problem:** Flink cluster not running or wrong port

**Solution:**
```bash
# Check if cluster is running
curl http://localhost:8081/overview

# If not running, start it
export JAVA_HOME=/path/to/java-11
~/bigdata/flink-1.19.1/bin/start-cluster.sh

# Verify TaskManagers are connected
curl http://localhost:8081/overview | python3 -m json.tool
# Should show: "taskmanagers": 1, "slots-total": 2
```

### Error: "Memory configuration failed"

**Problem:** Missing memory settings in Flink 1.19

**Solution:**
Ensure these are in `flink-conf.yaml`:
```yaml
jobmanager.memory.process.size: 1600m
taskmanager.memory.process.size: 1728m
taskmanager.numberOfTaskSlots: 2
```

## Job Submission Issues

### Error: "ClassNotFoundException: org.apache.iceberg..."

**Problem:** Iceberg JAR not in Flink lib directory

**Solution:**
```bash
# Download Iceberg Flink runtime
cd ~/bigdata/flink-1.19.1/lib
wget https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-flink-runtime-1.17/1.4.3/iceberg-flink-runtime-1.17-1.4.3.jar

# Restart Flink
~/bigdata/flink-1.19.1/bin/stop-cluster.sh
~/bigdata/flink-1.19.1/bin/start-cluster.sh
```

### Error: "ClassNotFoundException: org.apache.hadoop..."

**Problem:** Hadoop JARs missing

**Solution:**
Download all required Hadoop JARs to `~/bigdata/flink-1.19.1/lib/`:
```bash
cd ~/bigdata/flink-1.19.1/lib
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/3.3.4/hadoop-aws-3.3.4.jar
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-client-api/3.3.4/hadoop-client-api-3.3.4.jar
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-client-runtime/3.3.4/hadoop-client-runtime-3.3.4.jar
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-common/3.3.4/hadoop-common-3.3.4.jar
wget https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/1.12.262/aws-java-sdk-bundle-1.12.262.jar
wget https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar
```

### Error: "LinkageError" or "MethodNotFoundError"

**Problem:** Classloader conflicts between Flink and Iceberg

**Solution:**
Ensure `flink-conf.yaml` has:
```yaml
classloader.resolve-order: parent-first
classloader.parent-first-patterns.additional: org.apache.hadoop.;com.amazonaws.;org.apache.iceberg.
```

## Runtime Issues

### Job runs but no data written to Iceberg

**Problem 1:** Checkpointing not enabled

**Check:**
```bash
curl http://localhost:8081/jobs/<JOB_ID>/checkpoints | python3 -m json.tool
```

Should show `"completed" > 0`

**Solution:** Already enabled in code at 30s interval. Wait at least 35 seconds after job start.

---

**Problem 2:** Kafka producers not running

**Check:**
```bash
# Check topic offsets
~/bigdata/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list localhost:9093 --topic orders --time -1
```

**Solution:** Start producers:
```bash
cd scripts/kafka-producers
python3 orders_producer.py &
python3 customers_producer.py &
```

---

**Problem 3:** Job reading from wrong offset

**Check:** Job uses `'scan.startup.mode' = 'latest-offset'`

**Solution:** Modify job to read from beginning:
```java
"'scan.startup.mode' = 'earliest-offset',"
```

Rebuild and resubmit.

### Error: "Failed to deserialize JSON"

**Problem:** Kafka message format mismatch

**Common cause:** Using `upsert-kafka` connector with non-JSON keys

**Solution:** Use regular `kafka` connector:
```java
"'connector' = 'kafka',"  // Not 'upsert-kafka'
```

### Job fails with "NoRestartBackoffTimeStrategy"

**Problem:** Job encountering errors and not restarting

**Check exceptions:**
```bash
curl http://localhost:8081/jobs/<JOB_ID>/exceptions | python3 -m json.tool
```

Common issues:
- JSON deserialization errors
- Kafka connection issues
- S3/MinIO connection failures

## Kafka Issues

### Error: "Connection refused" to Kafka

**Problem:** Kafka not running or wrong port

**Solution:**
```bash
# Check Kafka is running
netstat -an | grep 9093

# Start Kafka if needed
~/bigdata/kafka/bin/kafka-server-start.sh \
  ~/bigdata/kafka/config/server.properties &

# Verify topics exist
~/bigdata/kafka/bin/kafka-topics.sh --list \
  --bootstrap-server localhost:9093
```

### Error: "Group coordinator not available"

**Problem:** Consumer group initialization issue

**Solution:**
Wait a few seconds and retry. If persists, delete consumer group:
```bash
~/bigdata/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9093 \
  --delete --group flink-java-group
```

## MinIO/S3 Issues

### Error: "No FileSystem for scheme: s3a"

**Problem:** Missing Hadoop AWS dependencies or core-site.xml

**Solution:**
1. Ensure hadoop-aws JAR is in Flink lib
2. Set HADOOP_CONF_DIR:
```bash
export HADOOP_CONF_DIR=~/bigdata/flink-1.19.1/hadoop-conf
```

3. Verify core-site.xml exists:
```bash
ls ~/bigdata/flink-1.19.1/hadoop-conf/core-site.xml
```

### Error: "Access Denied" or "403 Forbidden" from MinIO

**Problem:** Wrong credentials in core-site.xml

**Solution:**
Check MinIO credentials:
```bash
mc admin info myminio
```

Update `config/core-site.xml`:
```xml
<property>
    <name>fs.s3a.access.key</name>
    <value>your-access-key</value>
</property>
<property>
    <name>fs.s3a.secret.key</name>
    <value>your-secret-key</value>
</property>
```

### Error: "Bucket does not exist"

**Problem:** MinIO bucket not created

**Solution:**
```bash
mc mb myminio/iceberg-data-warehouse
mc ls myminio  # Verify bucket exists
```

## Performance Issues

### High checkpoint duration

**Problem:** Checkpoints taking too long

**Solutions:**
1. Increase checkpoint interval:
```java
env.enableCheckpointing(60000); // 60 seconds instead of 30
```

2. Increase parallelism:
```java
tableEnv.getConfig().set("parallelism.default", "4");
```

3. Tune MinIO upload settings in core-site.xml:
```xml
<property>
    <name>fs.s3a.multipart.size</name>
    <value>128M</value>
</property>
```

### High backpressure

**Problem:** Operators can't keep up with data rate

**Check:** Flink UI → Job → Metrics → Backpressure

**Solutions:**
1. Increase parallelism
2. Add more TaskManager slots
3. Optimize join logic
4. Enable object reuse:
```java
env.getConfig().enableObjectReuse();
```

### Out of Memory errors

**Problem:** TaskManager heap exhausted

**Solution:**
Increase TaskManager memory in flink-conf.yaml:
```yaml
taskmanager.memory.process.size: 2048m  # Was 1728m
```

Restart cluster after change.

## Data Quality Issues

### Duplicate records in Iceberg

**Problem:** Job restarted without checkpointing

**Solution:**
- Ensure checkpoints are completing successfully
- Use Iceberg MERGE for deduplication
- Check for exactly-once semantics

### Missing records (data loss)

**Problem:** Kafka messages expired or deleted

**Check:**
```bash
# Check retention settings
~/bigdata/kafka/bin/kafka-configs.sh --describe \
  --bootstrap-server localhost:9093 --topic orders
```

**Solution:**
1. Increase Kafka retention
2. Use `earliest-offset` for backfill
3. Ensure checkpoints complete before Kafka cleanup

## Debugging Tips

### Enable debug logging

Add to `~/bigdata/flink-1.19.1/conf/log4j.properties`:
```properties
logger.iceberg.name = org.apache.iceberg
logger.iceberg.level = DEBUG

logger.kafka.name = org.apache.kafka
logger.kafka.level = DEBUG
```

### Check job logs

```bash
# JobManager logs
tail -f ~/bigdata/flink-1.19.1/log/flink-*-standalonesession*.log

# TaskManager logs (where actual work happens)
tail -f ~/bigdata/flink-1.19.1/log/flink-*-taskexecutor*.log

# Search for errors
grep -i "error\|exception" ~/bigdata/flink-1.19.1/log/*.log
```

### Verify data files

```bash
# List all files in Iceberg table
mc ls -r myminio/iceberg-data-warehouse/warehouse/default/enriched_orders/

# Check metadata
mc cat myminio/iceberg-data-warehouse/warehouse/default/enriched_orders/metadata/v2.metadata.json | python3 -m json.tool

# Count records in latest snapshot
mc cat myminio/iceberg-data-warehouse/warehouse/default/enriched_orders/metadata/v2.metadata.json | \
  python3 -c "import sys, json; data = json.load(sys.stdin); \
  snap = [s for s in data['snapshots'] if s['snapshot-id'] == data['current-snapshot-id']][0]; \
  print(f\"Total records: {snap['summary'].get('total-records', 0)}\")"
```

## Getting Help

If issues persist:

1. Check Flink logs for detailed error messages
2. Verify all prerequisites are met (Java 11, Flink 1.19.1, etc.)
3. Test with simpler job first (KafkaFlinkTestJob.java)
4. Check Flink documentation: https://nightlies.apache.org/flink/flink-docs-release-1.19/
5. Check Iceberg documentation: https://iceberg.apache.org/docs/latest/flink/

## Clean Start

If all else fails, clean restart:

```bash
# Stop Flink
~/bigdata/flink-1.19.1/bin/stop-cluster.sh

# Clean checkpoints and state
rm -rf /tmp/flink-*

# Clear MinIO bucket (CAUTION: deletes all data!)
mc rm -r --force myminio/iceberg-data-warehouse/warehouse/default/enriched_orders/

# Restart Flink
export JAVA_HOME=/path/to/java-11
~/bigdata/flink-1.19.1/bin/start-cluster.sh

# Rebuild and resubmit job
cd flink-job
./build.sh
./run.sh
```
