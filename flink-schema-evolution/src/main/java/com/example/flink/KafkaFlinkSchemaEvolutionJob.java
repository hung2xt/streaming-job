package com.example.flink;

import com.example.flink.config.SchemaEvolutionConfig;
import com.example.flink.schema.IcebergSchemaSync;
import com.example.flink.schema.SchemaEvolutionManager;
import org.apache.avro.Schema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.iceberg.catalog.TableIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Flink job demonstrating schema evolution with Avro, Confluent Schema Registry, and Iceberg.
 *
 * Pipeline:
 * 1. Read Avro messages from Kafka (with Schema Registry)
 * 2. Detect schema evolution (new fields)
 * 3. Sync schema changes to Iceberg
 * 4. Write data to Iceberg tables
 */
public class KafkaFlinkSchemaEvolutionJob {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaFlinkSchemaEvolutionJob.class);

    public static void main(String[] args) throws Exception {
        LOG.info("Starting Kafka-Flink Schema Evolution Job");

        // Create execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Configure checkpointing
        env.enableCheckpointing(SchemaEvolutionConfig.CHECKPOINT_INTERVAL_MS);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30000L);
        env.getCheckpointConfig().setCheckpointTimeout(120000L);

        // Create Table Environment
        EnvironmentSettings settings = EnvironmentSettings.newInstance()
                .inStreamingMode()
                .build();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);

        // Initialize schema evolution components
        SchemaEvolutionManager schemaManager = new SchemaEvolutionManager(
                SchemaEvolutionConfig.SCHEMA_REGISTRY_URL
        );
        schemaManager.initialize();

        IcebergSchemaSync icebergSync = new IcebergSchemaSync(
                SchemaEvolutionConfig.ICEBERG_WAREHOUSE,
                SchemaEvolutionConfig.S3_ENDPOINT,
                SchemaEvolutionConfig.S3_ACCESS_KEY,
                SchemaEvolutionConfig.S3_SECRET_KEY
        );
        icebergSync.initialize();

        // Register Iceberg catalog
        registerIcebergCatalog(tableEnv);

        // Create Iceberg table if not exists
        createIcebergTableIfNotExists(tableEnv);

        // Create Kafka source table with Avro-Confluent format
        createKafkaSourceTable(tableEnv);

        // Start schema evolution monitoring in background
        startSchemaEvolutionMonitor(schemaManager, icebergSync);

        // Execute the streaming pipeline
        executeStreamingPipeline(tableEnv);
    }

    /**
     * Registers the Iceberg catalog with Flink.
     */
    private static void registerIcebergCatalog(StreamTableEnvironment tableEnv) {
        String catalogDDL = String.format(
                "CREATE CATALOG %s WITH (" +
                "  'type' = 'iceberg'," +
                "  'catalog-type' = '%s'," +
                "  'warehouse' = '%s'," +
                "  'io-impl' = 'org.apache.iceberg.aws.s3.S3FileIO'," +
                "  's3.endpoint' = '%s'," +
                "  's3.access-key-id' = '%s'," +
                "  's3.secret-access-key' = '%s'," +
                "  's3.path-style-access' = '%s'" +
                ")",
                SchemaEvolutionConfig.ICEBERG_CATALOG_NAME,
                SchemaEvolutionConfig.ICEBERG_CATALOG_TYPE,
                SchemaEvolutionConfig.ICEBERG_WAREHOUSE,
                SchemaEvolutionConfig.S3_ENDPOINT,
                SchemaEvolutionConfig.S3_ACCESS_KEY,
                SchemaEvolutionConfig.S3_SECRET_KEY,
                SchemaEvolutionConfig.S3_PATH_STYLE_ACCESS
        );

        tableEnv.executeSql(catalogDDL);
        tableEnv.useCatalog(SchemaEvolutionConfig.ICEBERG_CATALOG_NAME);
        LOG.info("Iceberg catalog registered: {}", SchemaEvolutionConfig.ICEBERG_CATALOG_NAME);
    }

    /**
     * Creates the Iceberg orders table if it doesn't exist.
     */
    private static void createIcebergTableIfNotExists(StreamTableEnvironment tableEnv) {
        // Create database if not exists
        String createDbSql = String.format(
                "CREATE DATABASE IF NOT EXISTS %s.%s",
                SchemaEvolutionConfig.ICEBERG_CATALOG_NAME,
                SchemaEvolutionConfig.ICEBERG_DATABASE
        );
        tableEnv.executeSql(createDbSql);

        // Create table with initial schema
        String createTableSql = String.format(
                "CREATE TABLE IF NOT EXISTS %s.%s.%s (" +
                "  order_id STRING," +
                "  customer_id BIGINT," +
                "  product_id STRING," +
                "  quantity INT," +
                "  total_amount DOUBLE," +
                "  order_time TIMESTAMP(3)" +
                ") WITH (" +
                "  'format-version' = '2'," +
                "  'write.upsert.enabled' = 'true'" +
                ")",
                SchemaEvolutionConfig.ICEBERG_CATALOG_NAME,
                SchemaEvolutionConfig.ICEBERG_DATABASE,
                SchemaEvolutionConfig.ICEBERG_TABLE
        );
        tableEnv.executeSql(createTableSql);
        LOG.info("Iceberg table created/verified: {}.{}.{}",
                SchemaEvolutionConfig.ICEBERG_CATALOG_NAME,
                SchemaEvolutionConfig.ICEBERG_DATABASE,
                SchemaEvolutionConfig.ICEBERG_TABLE);
    }

    /**
     * Creates the Kafka source table with Avro-Confluent format.
     */
    private static void createKafkaSourceTable(StreamTableEnvironment tableEnv) {
        String kafkaSourceDDL = String.format(
                "CREATE TABLE kafka_orders_avro (" +
                "  order_id STRING," +
                "  customer_id BIGINT," +
                "  product_id STRING," +
                "  quantity INT," +
                "  total_amount DOUBLE," +
                "  order_time BIGINT" +
                ") WITH (" +
                "  'connector' = 'kafka'," +
                "  'topic' = '%s'," +
                "  'properties.bootstrap.servers' = '%s'," +
                "  'properties.group.id' = '%s'," +
                "  'scan.startup.mode' = 'earliest-offset'," +
                "  'format' = 'avro-confluent'," +
                "  'avro-confluent.url' = '%s'" +
                ")",
                SchemaEvolutionConfig.KAFKA_TOPIC,
                SchemaEvolutionConfig.KAFKA_BOOTSTRAP_SERVERS,
                SchemaEvolutionConfig.KAFKA_GROUP_ID,
                SchemaEvolutionConfig.SCHEMA_REGISTRY_URL
        );

        tableEnv.executeSql(kafkaSourceDDL);
        LOG.info("Kafka source table created with Avro-Confluent format");
    }

    /**
     * Starts a background thread to monitor schema evolution.
     */
    private static void startSchemaEvolutionMonitor(
            SchemaEvolutionManager schemaManager,
            IcebergSchemaSync icebergSync) {

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        TableIdentifier tableId = TableIdentifier.of(
                SchemaEvolutionConfig.ICEBERG_DATABASE,
                SchemaEvolutionConfig.ICEBERG_TABLE
        );

        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (schemaManager.hasSchemaEvolved(SchemaEvolutionConfig.SCHEMA_SUBJECT)) {
                    LOG.info("Schema evolution detected! Fetching new fields...");

                    List<Schema.Field> newFields = schemaManager.getNewFields(
                            SchemaEvolutionConfig.SCHEMA_SUBJECT
                    );

                    if (!newFields.isEmpty()) {
                        LOG.info("Found {} new field(s). Syncing to Iceberg...", newFields.size());
                        icebergSync.syncSchemaChanges(tableId, newFields);
                        LOG.info("Schema changes synced to Iceberg successfully");
                    }
                }
            } catch (Exception e) {
                LOG.error("Error during schema evolution check", e);
            }
        },
        0,
        SchemaEvolutionConfig.SCHEMA_CHECK_INTERVAL_MS,
        TimeUnit.MILLISECONDS);

        LOG.info("Schema evolution monitor started (interval: {}ms)",
                SchemaEvolutionConfig.SCHEMA_CHECK_INTERVAL_MS);
    }

    /**
     * Executes the main streaming pipeline.
     */
    private static void executeStreamingPipeline(StreamTableEnvironment tableEnv) throws Exception {
        // Insert from Kafka to Iceberg with timestamp conversion
        String insertSql = String.format(
                "INSERT INTO %s.%s.%s " +
                "SELECT " +
                "  order_id," +
                "  customer_id," +
                "  product_id," +
                "  quantity," +
                "  total_amount," +
                "  TO_TIMESTAMP_LTZ(order_time, 3) as order_time " +
                "FROM kafka_orders_avro",
                SchemaEvolutionConfig.ICEBERG_CATALOG_NAME,
                SchemaEvolutionConfig.ICEBERG_DATABASE,
                SchemaEvolutionConfig.ICEBERG_TABLE
        );

        LOG.info("Starting streaming pipeline: Kafka -> Iceberg");
        TableResult result = tableEnv.executeSql(insertSql);

        // Wait for the job (this blocks indefinitely in streaming mode)
        result.await();
    }
}
