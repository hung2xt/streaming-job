# Flink Schema Evolution with Avro, Schema Registry, and Iceberg

This project demonstrates schema evolution in a streaming pipeline using Apache Flink, Avro, Confluent Schema Registry, and Apache Iceberg.

## Architecture

```
Kafka (Avro) → Schema Registry → Flink Job → Schema Sync → Iceberg Tables
      │              │                │            │
      │              │                │            └── ALTER TABLE ADD COLUMN
      │              │                └── Detect new fields
      │              └── Store/retrieve schemas
      └── Binary Avro messages with schema ID
```

## Scope

This implementation supports **adding new columns only** (forward-compatible schema evolution). It does not handle:
- Column deletions
- Type changes
- Column renames

## Project Structure

```
flink-schema-evolution/
├── avro/
│   ├── orders_v1.avsc                    # Initial orders schema (6 fields)
│   └── orders_v2.avsc                    # Evolved schema (7 fields with shipping_address)
├── producers/
│   ├── requirements.txt                  # Python dependencies
│   └── avro_orders_producer.py           # Avro producer with schema evolution demo
├── src/main/java/com/example/flink/
│   ├── config/
│   │   └── SchemaEvolutionConfig.java    # Configuration constants
│   ├── schema/
│   │   ├── SchemaEvolutionManager.java   # Detects schema changes from Registry
│   │   ├── IcebergSchemaSync.java        # Syncs new columns to Iceberg
│   │   └── DynamicSchemaGenerator.java   # Generates DDL from Schema Registry (NEW)
│   ├── KafkaFlinkSchemaEvolutionJob.java # Static schema Flink job (original)
│   └── KafkaFlinkDynamicSchemaJob.java   # Dynamic schema Flink job (NEW - recommended)
├── pom.xml                               # Maven dependencies
├── build.sh                              # Build script
├── run.sh                                # Run script
└── README.md                             # This file
```

## Prerequisites

- Java 11+
- Maven 3.6+
- Python 3.8+ (for producer)
- Docker with:
  - Kafka (port 9093)
  - Confluent Schema Registry (port 8081)
  - MinIO (port 9000)
  - Flink cluster (optional, can run locally)

## Configuration

Edit `src/main/java/com/example/flink/config/SchemaEvolutionConfig.java` to change:

| Setting | Default | Description |
|---------|---------|-------------|
| KAFKA_BOOTSTRAP_SERVERS | localhost:9093 | Kafka brokers |
| KAFKA_TOPIC | orders_avro | Topic name |
| SCHEMA_REGISTRY_URL | http://localhost:8081 | Schema Registry URL |
| ICEBERG_WAREHOUSE | s3a://iceberg-data-warehouse/warehouse | Iceberg warehouse path |
| S3_ENDPOINT | http://localhost:9000 | MinIO/S3 endpoint |

## Build

```bash
./build.sh
```

This compiles the Java code, creates a fat JAR, and sets up the Python environment.

## Run

### Start the Flink Job

There are two job types available:

#### Dynamic Schema Job (Recommended)

The **Dynamic Schema Job** automatically fetches schema from Schema Registry and generates DDL at runtime. All columns are processed without hardcoding.

```bash
# Run locally (default - uses dynamic schema)
./run.sh local

# Submit to Flink cluster
./run.sh cluster

# Submit via Docker
./run.sh docker
```

#### Static Schema Job (Original)

The **Static Schema Job** uses hardcoded schema definitions. New columns require code changes.

```bash
# Run locally
./run.sh static-local

# Submit to Flink cluster
./run.sh static-cluster

# Submit via Docker
./run.sh static-docker
```

### Start the Avro Producer

```bash
./run.sh producer
```

Or manually:

```bash
cd producers
source venv/bin/activate
python avro_orders_producer.py
```

## Dynamic vs Static Schema

| Feature | Dynamic Schema Job | Static Schema Job |
|---------|-------------------|-------------------|
| **Schema Source** | Schema Registry (runtime) | Hardcoded in Java |
| **New Columns** | Auto-detected & processed | Requires code change |
| **DDL Generation** | Dynamic from Avro schema | Static SQL strings |
| **Recommended For** | Production | Learning/Simple cases |

### How Dynamic Schema Works

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      DYNAMIC SCHEMA FLOW                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  1. Job Startup:                                                        │
│     Schema Registry ──GET latest schema──▶ DynamicSchemaGenerator      │
│                                                 │                       │
│                                                 ▼                       │
│     Generate DDL dynamically:                                           │
│     - CREATE TABLE kafka_orders_dynamic (...)  ← All columns from Avro  │
│     - CREATE TABLE iceberg_catalog.default.orders (...)                │
│     - INSERT INTO ... SELECT col1, col2, ... ← All columns             │
│                                                                         │
│  2. Schema Evolution Detected (background thread):                      │
│     Schema Registry ──version changed!──▶ SchemaEvolutionManager       │
│                                                 │                       │
│                                                 ▼                       │
│     - Sync Iceberg: ALTER TABLE ADD COLUMN new_col                     │
│     - Rebuild Kafka source table with new columns                       │
│     - New INSERT statement includes new columns                         │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## Schema Evolution Demo

The producer demonstrates schema evolution:

1. **Messages 1-50**: Uses schema v1 (6 fields)
2. **Messages 51+**: Uses schema v2 (7 fields with `shipping_address`)

The Flink job automatically:
1. Detects the new schema version in Schema Registry
2. Identifies the new `shipping_address` field
3. Executes `ALTER TABLE ADD COLUMN shipping_address STRING` on Iceberg
4. **(Dynamic Job Only)** Rebuilds Kafka source table with new column
5. **(Dynamic Job Only)** Processes `shipping_address` values for new records

## Verification

### 1. Check Schema Registry

```bash
# List all versions
curl http://localhost:8081/subjects/orders_avro-value/versions

# Get latest schema
curl http://localhost:8081/subjects/orders_avro-value/versions/latest
```

### 2. Check Iceberg Table Schema

```sql
-- In Spark SQL or Flink SQL
DESCRIBE iceberg_catalog.default.orders;
```

### 3. Query Data

```sql
SELECT order_id, shipping_address
FROM iceberg_catalog.default.orders
LIMIT 10;

-- Results:
-- Old records: shipping_address = NULL
-- New records: shipping_address = '123 Main St...'
```

## Type Mapping

| Avro Type | Iceberg Type |
|-----------|--------------|
| string | StringType |
| int | IntegerType |
| long | LongType |
| long (timestamp-millis) | TimestampType |
| double | DoubleType |
| float | FloatType |
| boolean | BooleanType |
| bytes | BinaryType |
| union [null, X] | Optional X |

## Components

### SchemaEvolutionManager

Connects to Confluent Schema Registry and tracks schema changes:
- `getCurrentSchema(subject)`: Fetches latest schema
- `hasSchemaEvolved(subject)`: Checks for new versions
- `getNewFields(subject)`: Returns list of new fields

### IcebergSchemaSync

Syncs schema changes to Iceberg tables:
- `addColumnIfNotExists(table, column, type)`: Adds column idempotently
- `syncSchemaChanges(table, newFields)`: Bulk add new columns
- `avroToIcebergType(schema)`: Converts Avro to Iceberg types

### DynamicSchemaGenerator (NEW)

Generates Flink SQL DDL dynamically from Schema Registry:
- `getLatestSchema(subject)`: Fetches current Avro schema
- `generateKafkaSourceDDL(...)`: Creates Kafka source table DDL with all columns
- `generateIcebergTableDDL(...)`: Creates Iceberg table DDL with all columns
- `generateInsertSQL(...)`: Creates INSERT statement with all columns
- `avroToFlinkType(schema)`: Converts Avro types to Flink SQL types

### KafkaFlinkSchemaEvolutionJob (Static Schema)

Original Flink job with hardcoded schema:
1. Creates Kafka source with fixed 6 columns
2. Registers Iceberg catalog
3. Starts background schema monitoring thread
4. Streams data from Kafka to Iceberg
5. **Limitation**: New columns not processed without code change

### KafkaFlinkDynamicSchemaJob (Dynamic Schema - Recommended)

Enhanced Flink job with runtime schema detection:
1. Fetches schema from Schema Registry at startup
2. Generates all DDL dynamically (no hardcoded columns)
3. Registers Iceberg catalog
4. Starts background schema monitoring thread
5. When schema evolves:
   - Syncs Iceberg schema (add new columns)
   - Rebuilds Kafka source table with new columns
   - New INSERT includes all columns
6. **Advantage**: All columns processed automatically

## Troubleshooting

### Schema Registry Connection Failed
```
Caused by: io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
```
Ensure Schema Registry is running at the configured URL.

### Iceberg Table Not Found
```
org.apache.iceberg.exceptions.NoSuchTableException
```
The job creates the table automatically. Check MinIO/S3 connectivity.

### Avro Deserialization Error
```
org.apache.avro.AvroRuntimeException
```
Ensure the Kafka messages are serialized with the correct schema ID.

## Data Flow & Processing Scenarios

### Overall Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    KAFKA-FLINK SCHEMA EVOLUTION PIPELINE                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────┐                                                   │
│  │ Avro Producer    │  ← Python script tao Order messages               │
│  │ (Python)         │  ← Schema v1: msg 1-50 (6 fields)                 │
│  │                  │  ← Schema v2: msg 51+ (7 fields + shipping_addr)  │
│  └────────┬─────────┘                                                   │
│           │                                                             │
│           ▼                                                             │
│  ┌──────────────────┐     ┌──────────────────┐                          │
│  │ Kafka Topic      │────▶│ Schema Registry  │                          │
│  │ (orders_avro)    │     │ (Confluent)      │                          │
│  │                  │     │ - Schema v1      │                          │
│  │ Avro messages +  │     │ - Schema v2      │                          │
│  │ Schema ID        │     └──────────────────┘                          │
│  └────────┬─────────┘              │                                    │
│           │                        │                                    │
│           ▼                        ▼                                    │
│  ┌─────────────────────────────────────────────┐                        │
│  │ Apache Flink Job                            │                        │
│  │ ┌─────────────────────────────────────────┐ │                        │
│  │ │ KafkaFlinkSchemaEvolutionJob            │ │                        │
│  │ │ - Doc tu Kafka (avro-confluent)         │ │                        │
│  │ │ - Chuyen doi timestamp                  │ │                        │
│  │ │ - INSERT INTO Iceberg table             │ │                        │
│  │ └─────────────────────────────────────────┘ │                        │
│  │ ┌─────────────────────────────────────────┐ │                        │
│  │ │ Schema Monitoring Thread (10s interval) │ │                        │
│  │ │ - SchemaEvolutionManager: phat hien doi │ │                        │
│  │ │ - IcebergSchemaSync: cap nhat Iceberg   │ │                        │
│  │ └─────────────────────────────────────────┘ │                        │
│  └────────┬────────────────────────────────────┘                        │
│           │                                                             │
│           ▼                                                             │
│  ┌──────────────────┐     ┌──────────────────┐                          │
│  │ Iceberg Catalog  │────▶│ MinIO/S3 Storage │                          │
│  │ (default.orders) │     │                  │                          │
│  │ - 6 cols → 7 cols│     │ - Data files     │                          │
│  │ - Auto-evolve    │     │ - Metadata       │                          │
│  └──────────────────┘     └──────────────────┘                          │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Kafka's Role

| Responsibility | Details |
|----------------|---------|
| **Event Broker** | Central message broker storing Avro-serialized order messages |
| **Topic** | `orders_avro` - receives messages from producer, distributes to Flink |
| **Schema Integration** | Each message contains Schema ID in Avro binary header |
| **Decoupling** | Producer and Flink consumer operate independently |
| **Buffering & Replay** | Kafka retains messages; Flink can replay from any offset |
| **Offset Management** | Flink reads from `earliest-offset` with consumer group |

**Kafka = Streaming Data Source**

### Flink's Role

| Responsibility | Details |
|----------------|---------|
| **Stream Processing** | Consumes Kafka messages in real-time |
| **Deserialization** | Parses Avro messages with `avro-confluent` format |
| **Transformation** | Converts timestamp-millis to Flink LTZ timestamp |
| **Schema Monitoring** | Background thread detects schema evolution (every 10s) |
| **Schema Sync** | Triggers Iceberg table updates when schema changes |
| **Output Sink** | Writes transformed records to Iceberg tables |
| **Exactly-Once** | Checkpointing every 60s with EXACTLY_ONCE mode |
| **Fault Recovery** | Replays from checkpoints if job fails |

**Flink = Stream Processor & Orchestrator**

### Processing Scenario A: Initial State (Messages 1-50)

```
1. Producer creates 50 orders with schema v1 (6 fields)
   → order_id, customer_id, product_id, quantity, total_amount, order_time

2. Schema Registry stores schema v1
   → Subject: orders_avro-value, Version: 1

3. Flink Job:
   → Deserialize Avro messages using Schema Registry
   → Create Iceberg table with 6 columns
   → INSERT 50 records into Iceberg
```

### Processing Scenario B: Schema Evolution (Messages 51+)

```
1. Producer switches to schema v2 (adds shipping_address)
   → New field: shipping_address (optional, union [null, string])

2. Schema Registry automatically creates version 2
   → Subject: orders_avro-value, Version: 2

3. SchemaEvolutionManager (background thread every 10s):
   → Detects schema change v1 → v2
   → Identifies new field: shipping_address

4. IcebergSchemaSync:
   → ALTER TABLE default.orders ADD COLUMN shipping_address STRING
   → Change is idempotent (won't fail if column exists)

5. Flink continues streaming:
   → Deserialize v2 messages with 7 fields
   → INSERT into updated Iceberg table
   → Old records → NULL for shipping_address
   → New records → populated shipping_address values
```

### Complete Data Flow Timeline

```
TIME    EVENT                              COMPONENT          ACTION
────────────────────────────────────────────────────────────────────────────
T0      System Start                       All                Initialize connections
T0+1s   Message 1                          Producer           Send v1 order (6 fields)
T0+1s   Message 1 arrives                  Kafka              Store with Schema ID=1
T0+1s   Flink reads                        Flink Consumer     Deserialize using v1 schema
T0+1s   CREATE TABLE                       Iceberg            Create table with 6 columns
T0+1s   INSERT record 1                    Iceberg            Write first order
T0+2s   Message 2                          Producer           Send v1 order
... (repeat for 50 messages)
T0+50s  Message 50                         Producer           Send v1 order (last v1)
T0+50s  Message 50 INSERT                  Iceberg            Write 50th order
T0+51s  SCHEMA EVOLUTION POINT!            Producer           Switch to v2 schema
T0+51s  Message 51                         Producer           Send v2 order (7 fields)
T0+51s  Message 51 arrives                 Kafka              Store with Schema ID=2
T0+51s  (background monitoring)            SchemaEvolutionMgr Check Schema Registry
        Version changed? v1 → v2?          SchemaEvolutionMgr YES! New field detected
T0+51s  Get new fields                     SchemaEvolutionMgr Extract [shipping_address]
T0+51s  Load Iceberg table                 IcebergSchemaSync  Get current schema
T0+51s  ALTER TABLE ADD COLUMN             IcebergSchemaSync  shipping_address STRING
T0+51s  Schema sync committed              Iceberg            Column added (nullable)
T0+51s  Flink reads msg 51                 Flink Consumer     Deserialize using v2 schema
T0+51s  INSERT record 51                   Iceberg            Write order with shipping_address
T0+52s  Message 52+                        Producer           Continue sending v2 orders
... (continue streaming)
```

### Data Journey Step-by-Step

```
PRODUCER (Python)
  │
  ├── Creates Order object: {order_id, customer_id, product_id,
  │                          quantity, total_amount, order_time,
  │                          [shipping_address]}
  │
  ├── AvroSerializer (with Schema Registry)
  │
  └── Binary Avro packet + Schema ID (v1 or v2)
       │
       ▼
KAFKA BROKER (localhost:9093)
  │
  ├── Stores in topic partition
  │
  └── Maintains offset pointer
       │
       ▼
FLINK CONSUMER
  │
  ├── KafkaSource with avro-confluent format
  │
  ├── Consults Schema Registry for deserialize using schema ID
  │
  └── Row object: [order_id, customer_id, product_id,
                   quantity, total_amount, order_time,
                   (shipping_address if v2)]
       │
       ▼
FLINK TRANSFORMATION
  │
  ├── SQL SELECT with TO_TIMESTAMP_LTZ(order_time, 3) conversion
  │
  └── Schema check: Has schema evolved? → Yes → Trigger sync
       │
       ▼
ICEBERG SCHEMA SYNC (if needed)
  │
  ├── Load table from HadoopCatalog
  │
  ├── Execute: ALTER TABLE ADD COLUMN shipping_address STRING
  │
  └── Commit schema metadata update
       │
       ▼
ICEBERG TABLE (default.orders)
  │
  └── INSERT records with all fields
       │
       ▼
MINIO/S3 STORAGE
  │
  └── Stores:
      ├── Parquet data files with all columns
      ├── Iceberg metadata (version, snapshot info)
      └── Manifest files tracking data file locations
```

### Transaction & Consistency Guarantees

| Guarantee | Implementation |
|-----------|----------------|
| **Exactly-Once Semantics** | Flink checkpointing every 60 seconds |
| **Schema Consistency** | Schema Registry as single source of truth |
| **Backward Compatibility** | Avro default values for new fields |
| **Data Consistency** | Iceberg ACID-compliant atomic column additions |
| **Fault Tolerance** | State saved before processing; restart from checkpoint |

## License

Apache License 2.0
