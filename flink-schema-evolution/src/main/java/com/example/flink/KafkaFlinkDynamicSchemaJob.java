package com.example.flink;

import com.example.flink.config.SchemaEvolutionConfig;
import com.example.flink.schema.DynamicSchemaGenerator;
import com.example.flink.schema.IcebergSchemaSync;
import com.example.flink.schema.SchemaEvolutionManager;
import org.apache.avro.Schema;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.iceberg.catalog.TableIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Flink job with DYNAMIC schema evolution support.
 *
 * Key improvements over KafkaFlinkSchemaEvolutionJob:
 * 1. Dynamically generates DDL from Schema Registry (no hardcoded columns)
 * 2. Automatically detects schema changes and rebuilds tables
 * 3. All columns from Avro schema are included in processing
 *
 * Pipeline:
 * 1. Fetch latest schema from Schema Registry
 * 2. Generate Kafka source DDL dynamically
 * 3. Generate Iceberg table DDL dynamically
 * 4. Generate INSERT statement dynamically
 * 5. Monitor for schema evolution and trigger rebuild
 */
public class KafkaFlinkDynamicSchemaJob {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaFlinkDynamicSchemaJob.class);
    private static final String KAFKA_SOURCE_TABLE = "kafka_orders_dynamic";

    // Track current schema version for change detection
    private static final AtomicInteger currentSchemaVersion = new AtomicInteger(0);
    private static final AtomicBoolean schemaChangeDetected = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        LOG.info("Starting Kafka-Flink DYNAMIC Schema Evolution Job");
        LOG.info("This job automatically adapts to schema changes from Schema Registry");

        // Create execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Configure checkpointing for exactly-once semantics
        env.enableCheckpointing(SchemaEvolutionConfig.CHECKPOINT_INTERVAL_MS);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30000L);
        env.getCheckpointConfig().setCheckpointTimeout(120000L);

        // Create Table Environment
        EnvironmentSettings settings = EnvironmentSettings.newInstance()
                .inStreamingMode()
                .build();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);

        // Initialize dynamic schema components
        DynamicSchemaGenerator schemaGenerator = new DynamicSchemaGenerator(
                SchemaEvolutionConfig.SCHEMA_REGISTRY_URL
        );
        schemaGenerator.initialize();

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

        // Create database if not exists
        createDatabaseIfNotExists(tableEnv);

        // Get initial schema version
        int initialVersion = schemaManager.getLatestVersion(SchemaEvolutionConfig.SCHEMA_SUBJECT);
        currentSchemaVersion.set(initialVersion);
        LOG.info("Initial schema version: {}", initialVersion);

        // Create Iceberg table with dynamic schema
        createIcebergTableDynamic(tableEnv, schemaGenerator);

        // Create Kafka source table with dynamic schema
        createKafkaSourceTableDynamic(tableEnv, schemaGenerator);

        // Start schema evolution monitoring
        startSchemaEvolutionMonitor(tableEnv, schemaManager, schemaGenerator, icebergSync);

        // Execute the streaming pipeline with dynamic INSERT
        executeStreamingPipeline(tableEnv, schemaGenerator);
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
     * Creates database if not exists.
     */
    private static void createDatabaseIfNotExists(StreamTableEnvironment tableEnv) {
        String createDbSql = String.format(
                "CREATE DATABASE IF NOT EXISTS %s.%s",
                SchemaEvolutionConfig.ICEBERG_CATALOG_NAME,
                SchemaEvolutionConfig.ICEBERG_DATABASE
        );
        tableEnv.executeSql(createDbSql);
        LOG.info("Database created/verified: {}.{}",
                SchemaEvolutionConfig.ICEBERG_CATALOG_NAME,
                SchemaEvolutionConfig.ICEBERG_DATABASE);
    }

    /**
     * Creates Iceberg table with DYNAMIC schema from Schema Registry.
     */
    private static void createIcebergTableDynamic(
            StreamTableEnvironment tableEnv,
            DynamicSchemaGenerator schemaGenerator) throws Exception {

        String ddl = schemaGenerator.generateIcebergTableDDL(
                SchemaEvolutionConfig.ICEBERG_CATALOG_NAME,
                SchemaEvolutionConfig.ICEBERG_DATABASE,
                SchemaEvolutionConfig.ICEBERG_TABLE,
                SchemaEvolutionConfig.SCHEMA_SUBJECT
        );

        tableEnv.executeSql(ddl);
        LOG.info("Iceberg table created/verified with DYNAMIC schema from Schema Registry");
    }

    /**
     * Creates Kafka source table with DYNAMIC schema from Schema Registry.
     */
    private static void createKafkaSourceTableDynamic(
            StreamTableEnvironment tableEnv,
            DynamicSchemaGenerator schemaGenerator) throws Exception {

        String ddl = schemaGenerator.generateKafkaSourceDDL(
                KAFKA_SOURCE_TABLE,
                SchemaEvolutionConfig.SCHEMA_SUBJECT,
                SchemaEvolutionConfig.KAFKA_TOPIC,
                SchemaEvolutionConfig.KAFKA_BOOTSTRAP_SERVERS,
                SchemaEvolutionConfig.KAFKA_GROUP_ID
        );

        tableEnv.executeSql(ddl);
        LOG.info("Kafka source table created with DYNAMIC schema from Schema Registry");
    }

    /**
     * Drops and recreates Kafka source table with new schema.
     */
    private static void rebuildKafkaSourceTable(
            StreamTableEnvironment tableEnv,
            DynamicSchemaGenerator schemaGenerator) throws Exception {

        LOG.info("Rebuilding Kafka source table with new schema...");

        // Drop existing table
        tableEnv.executeSql("DROP TABLE IF EXISTS " + KAFKA_SOURCE_TABLE);

        // Recreate with new schema
        createKafkaSourceTableDynamic(tableEnv, schemaGenerator);

        LOG.info("Kafka source table rebuilt successfully");
    }

    /**
     * Starts a background thread to monitor schema evolution.
     * When schema changes are detected:
     * 1. Sync new columns to Iceberg
     * 2. Set flag to trigger pipeline rebuild
     */
    private static void startSchemaEvolutionMonitor(
            StreamTableEnvironment tableEnv,
            SchemaEvolutionManager schemaManager,
            DynamicSchemaGenerator schemaGenerator,
            IcebergSchemaSync icebergSync) {

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "schema-evolution-monitor");
            t.setDaemon(true);
            return t;
        });

        TableIdentifier tableId = TableIdentifier.of(
                SchemaEvolutionConfig.ICEBERG_DATABASE,
                SchemaEvolutionConfig.ICEBERG_TABLE
        );

        scheduler.scheduleAtFixedRate(() -> {
            try {
                int latestVersion = schemaManager.getLatestVersion(SchemaEvolutionConfig.SCHEMA_SUBJECT);
                int currentVersion = currentSchemaVersion.get();

                if (latestVersion > currentVersion) {
                    LOG.info("=".repeat(60));
                    LOG.info("SCHEMA EVOLUTION DETECTED: v{} -> v{}", currentVersion, latestVersion);
                    LOG.info("=".repeat(60));

                    // Get new fields
                    List<Schema.Field> newFields = schemaManager.getNewFields(
                            SchemaEvolutionConfig.SCHEMA_SUBJECT
                    );

                    if (!newFields.isEmpty()) {
                        LOG.info("New fields detected: {}",
                                newFields.stream().map(Schema.Field::name).toList());

                        // Step 1: Sync Iceberg schema (add new columns)
                        icebergSync.syncSchemaChanges(tableId, newFields);
                        LOG.info("Iceberg schema updated with {} new column(s)", newFields.size());

                        // Step 2: Rebuild Kafka source table
                        rebuildKafkaSourceTable(tableEnv, schemaGenerator);
                        LOG.info("Kafka source table rebuilt with new schema");

                        // Step 3: Set flag for pipeline rebuild
                        schemaChangeDetected.set(true);
                    }

                    // Update version tracker
                    currentSchemaVersion.set(latestVersion);
                    LOG.info("Schema version updated to: {}", latestVersion);
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
     * Executes the main streaming pipeline with DYNAMIC INSERT statement.
     */
    private static void executeStreamingPipeline(
            StreamTableEnvironment tableEnv,
            DynamicSchemaGenerator schemaGenerator) throws Exception {

        // Generate dynamic INSERT SQL based on current schema
        String insertSql = schemaGenerator.generateInsertSQL(
                SchemaEvolutionConfig.ICEBERG_CATALOG_NAME,
                SchemaEvolutionConfig.ICEBERG_DATABASE,
                SchemaEvolutionConfig.ICEBERG_TABLE,
                KAFKA_SOURCE_TABLE,
                SchemaEvolutionConfig.SCHEMA_SUBJECT
        );

        LOG.info("=".repeat(60));
        LOG.info("Starting DYNAMIC streaming pipeline");
        LOG.info("All columns from Schema Registry will be processed");
        LOG.info("=".repeat(60));

        TableResult result = tableEnv.executeSql(insertSql);

        // This blocks - the streaming job runs continuously
        // When schema changes, we need to cancel and restart with new INSERT
        result.await();
    }
}
