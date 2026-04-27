package com.example.flink.config;

/**
 * Configuration constants for the Schema Evolution pipeline.
 */
public final class SchemaEvolutionConfig {

    private SchemaEvolutionConfig() {
        // Prevent instantiation
    }

    // Kafka Configuration
    public static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9093";
    public static final String KAFKA_TOPIC = "orders_avro";
    public static final String KAFKA_GROUP_ID = "flink-schema-evolution-consumer";

    // Schema Registry Configuration
    public static final String SCHEMA_REGISTRY_URL = "http://localhost:8081";
    public static final String SCHEMA_SUBJECT = "orders_avro-value";

    // Iceberg Configuration
    public static final String ICEBERG_CATALOG_NAME = "iceberg_catalog";
    public static final String ICEBERG_CATALOG_TYPE = "hadoop";
    public static final String ICEBERG_WAREHOUSE = "s3a://iceberg-data-warehouse/warehouse";
    public static final String ICEBERG_DATABASE = "default";
    public static final String ICEBERG_TABLE = "orders";

    // MinIO/S3 Configuration
    public static final String S3_ENDPOINT = "http://localhost:9000";
    public static final String S3_ACCESS_KEY = "minioadmin";
    public static final String S3_SECRET_KEY = "minioadmin";
    public static final String S3_PATH_STYLE_ACCESS = "true";

    // Flink Configuration
    public static final long CHECKPOINT_INTERVAL_MS = 60000L;
    public static final String CHECKPOINT_MODE = "EXACTLY_ONCE";

    // Schema Evolution Configuration
    public static final long SCHEMA_CHECK_INTERVAL_MS = 10000L;
}
