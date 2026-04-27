package com.example.flink.schema;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dynamically generates Flink SQL DDL statements based on Avro schema from Schema Registry.
 * This enables runtime schema evolution without hardcoding column definitions.
 */
public class DynamicSchemaGenerator implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DynamicSchemaGenerator.class);
    private static final int SCHEMA_CACHE_CAPACITY = 100;

    private final String schemaRegistryUrl;
    private transient SchemaRegistryClient schemaRegistryClient;

    public DynamicSchemaGenerator(String schemaRegistryUrl) {
        this.schemaRegistryUrl = schemaRegistryUrl;
    }

    /**
     * Initializes the Schema Registry client.
     */
    public void initialize() {
        if (schemaRegistryClient == null) {
            Map<String, Object> configs = new HashMap<>();
            schemaRegistryClient = new CachedSchemaRegistryClient(
                    schemaRegistryUrl,
                    SCHEMA_CACHE_CAPACITY,
                    configs
            );
            LOG.info("DynamicSchemaGenerator initialized with Schema Registry: {}", schemaRegistryUrl);
        }
    }

    /**
     * Fetches the latest Avro schema from Schema Registry.
     *
     * @param subject The subject name (e.g., "orders_avro-value")
     * @return The Avro Schema
     */
    public Schema getLatestSchema(String subject) throws IOException, RestClientException {
        initialize();
        var metadata = schemaRegistryClient.getLatestSchemaMetadata(subject);
        return new Schema.Parser().parse(metadata.getSchema());
    }

    /**
     * Generates Flink SQL DDL for Kafka source table based on Avro schema.
     *
     * @param tableName The Flink table name
     * @param subject The Schema Registry subject
     * @param kafkaTopic The Kafka topic
     * @param bootstrapServers Kafka bootstrap servers
     * @param groupId Consumer group ID
     * @return The CREATE TABLE DDL statement
     */
    public String generateKafkaSourceDDL(
            String tableName,
            String subject,
            String kafkaTopic,
            String bootstrapServers,
            String groupId) throws IOException, RestClientException {

        Schema avroSchema = getLatestSchema(subject);
        List<ColumnDefinition> columns = extractColumns(avroSchema);

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(tableName).append(" (\n");

        // Add column definitions
        List<String> columnDefs = columns.stream()
                .map(col -> String.format("  `%s` %s", col.name, col.flinkType))
                .collect(Collectors.toList());
        ddl.append(String.join(",\n", columnDefs));

        ddl.append("\n) WITH (\n");
        ddl.append("  'connector' = 'kafka',\n");
        ddl.append(String.format("  'topic' = '%s',\n", kafkaTopic));
        ddl.append(String.format("  'properties.bootstrap.servers' = '%s',\n", bootstrapServers));
        ddl.append(String.format("  'properties.group.id' = '%s',\n", groupId));
        ddl.append("  'scan.startup.mode' = 'earliest-offset',\n");
        ddl.append("  'format' = 'avro-confluent',\n");
        ddl.append(String.format("  'avro-confluent.url' = '%s'\n", schemaRegistryUrl));
        ddl.append(")");

        String result = ddl.toString();
        LOG.info("Generated Kafka source DDL with {} columns:\n{}", columns.size(), result);
        return result;
    }

    /**
     * Generates Flink SQL DDL for Iceberg sink table based on Avro schema.
     *
     * @param catalogName The Iceberg catalog name
     * @param database The database name
     * @param tableName The table name
     * @param subject The Schema Registry subject
     * @return The CREATE TABLE DDL statement
     */
    public String generateIcebergTableDDL(
            String catalogName,
            String database,
            String tableName,
            String subject) throws IOException, RestClientException {

        Schema avroSchema = getLatestSchema(subject);
        List<ColumnDefinition> columns = extractColumns(avroSchema);

        StringBuilder ddl = new StringBuilder();
        ddl.append(String.format("CREATE TABLE IF NOT EXISTS %s.%s.%s (\n", catalogName, database, tableName));

        // Add column definitions with Iceberg-compatible types
        List<String> columnDefs = columns.stream()
                .map(col -> String.format("  `%s` %s", col.name, col.flinkTypeForIceberg))
                .collect(Collectors.toList());
        ddl.append(String.join(",\n", columnDefs));

        ddl.append("\n) WITH (\n");
        ddl.append("  'format-version' = '2',\n");
        ddl.append("  'write.upsert.enabled' = 'true'\n");
        ddl.append(")");

        String result = ddl.toString();
        LOG.info("Generated Iceberg table DDL with {} columns:\n{}", columns.size(), result);
        return result;
    }

    /**
     * Generates INSERT INTO ... SELECT statement dynamically.
     *
     * @param targetCatalog Target Iceberg catalog
     * @param targetDatabase Target database
     * @param targetTable Target table
     * @param sourceTable Source Kafka table
     * @param subject Schema Registry subject
     * @return The INSERT SQL statement
     */
    public String generateInsertSQL(
            String targetCatalog,
            String targetDatabase,
            String targetTable,
            String sourceTable,
            String subject) throws IOException, RestClientException {

        Schema avroSchema = getLatestSchema(subject);
        List<ColumnDefinition> columns = extractColumns(avroSchema);

        StringBuilder sql = new StringBuilder();
        sql.append(String.format("INSERT INTO %s.%s.%s\n", targetCatalog, targetDatabase, targetTable));
        sql.append("SELECT\n");

        // Generate SELECT columns with type conversions
        List<String> selectCols = new ArrayList<>();
        for (ColumnDefinition col : columns) {
            if (col.needsTimestampConversion) {
                // Convert BIGINT timestamp-millis to TIMESTAMP(3)
                selectCols.add(String.format("  TO_TIMESTAMP_LTZ(`%s`, 3) AS `%s`", col.name, col.name));
            } else {
                selectCols.add(String.format("  `%s`", col.name));
            }
        }
        sql.append(String.join(",\n", selectCols));
        sql.append(String.format("\nFROM %s", sourceTable));

        String result = sql.toString();
        LOG.info("Generated INSERT SQL:\n{}", result);
        return result;
    }

    /**
     * Gets list of column names from schema.
     */
    public List<String> getColumnNames(String subject) throws IOException, RestClientException {
        Schema avroSchema = getLatestSchema(subject);
        return avroSchema.getFields().stream()
                .map(Schema.Field::name)
                .collect(Collectors.toList());
    }

    /**
     * Extracts column definitions from Avro schema.
     */
    private List<ColumnDefinition> extractColumns(Schema avroSchema) {
        List<ColumnDefinition> columns = new ArrayList<>();

        for (Schema.Field field : avroSchema.getFields()) {
            ColumnDefinition col = new ColumnDefinition();
            col.name = field.name();
            col.avroSchema = field.schema();
            col.flinkType = avroToFlinkType(field.schema(), false);
            col.flinkTypeForIceberg = avroToFlinkType(field.schema(), true);
            col.needsTimestampConversion = isTimestampMillis(field.schema());
            columns.add(col);
        }

        return columns;
    }

    /**
     * Converts Avro schema type to Flink SQL type.
     *
     * @param avroSchema The Avro schema
     * @param forIceberg Whether this is for Iceberg (use TIMESTAMP vs BIGINT)
     * @return Flink SQL type string
     */
    private String avroToFlinkType(Schema avroSchema, boolean forIceberg) {
        Schema.Type avroType = avroSchema.getType();

        // Handle union types (typically [null, actualType])
        if (avroType == Schema.Type.UNION) {
            List<Schema> types = avroSchema.getTypes();
            for (Schema innerSchema : types) {
                if (innerSchema.getType() != Schema.Type.NULL) {
                    return avroToFlinkType(innerSchema, forIceberg);
                }
            }
            return "STRING"; // Fallback
        }

        // Handle logical types
        LogicalType logicalType = avroSchema.getLogicalType();
        if (logicalType != null) {
            switch (logicalType.getName()) {
                case "timestamp-millis":
                case "timestamp-micros":
                    // For Kafka source: keep as BIGINT (raw value)
                    // For Iceberg: use TIMESTAMP(3)
                    return forIceberg ? "TIMESTAMP(3)" : "BIGINT";
                case "date":
                    return "DATE";
                case "time-millis":
                case "time-micros":
                    return "TIME";
                case "decimal":
                    Object precision = avroSchema.getObjectProp("precision");
                    Object scale = avroSchema.getObjectProp("scale");
                    int p = precision != null ? ((Number) precision).intValue() : 38;
                    int s = scale != null ? ((Number) scale).intValue() : 0;
                    return String.format("DECIMAL(%d, %d)", p, s);
                case "uuid":
                    return "STRING";
                default:
                    LOG.warn("Unknown logical type: {}, using STRING", logicalType.getName());
                    return "STRING";
            }
        }

        // Handle primitive types
        switch (avroType) {
            case STRING:
                return "STRING";
            case INT:
                return "INT";
            case LONG:
                return "BIGINT";
            case FLOAT:
                return "FLOAT";
            case DOUBLE:
                return "DOUBLE";
            case BOOLEAN:
                return "BOOLEAN";
            case BYTES:
                return "BYTES";
            case FIXED:
                return "BYTES";
            case ENUM:
                return "STRING";
            case ARRAY:
                String elementType = avroToFlinkType(avroSchema.getElementType(), forIceberg);
                return String.format("ARRAY<%s>", elementType);
            case MAP:
                String valueType = avroToFlinkType(avroSchema.getValueType(), forIceberg);
                return String.format("MAP<STRING, %s>", valueType);
            case RECORD:
                // For nested records, create ROW type
                return generateRowType(avroSchema, forIceberg);
            case NULL:
                return "STRING"; // Fallback
            default:
                LOG.warn("Unknown Avro type: {}, using STRING", avroType);
                return "STRING";
        }
    }

    /**
     * Generates ROW type for nested Avro records.
     */
    private String generateRowType(Schema recordSchema, boolean forIceberg) {
        List<String> fields = recordSchema.getFields().stream()
                .map(f -> String.format("`%s` %s", f.name(), avroToFlinkType(f.schema(), forIceberg)))
                .collect(Collectors.toList());
        return String.format("ROW<%s>", String.join(", ", fields));
    }

    /**
     * Checks if the schema represents a timestamp-millis logical type.
     */
    private boolean isTimestampMillis(Schema avroSchema) {
        // Handle union
        if (avroSchema.getType() == Schema.Type.UNION) {
            for (Schema innerSchema : avroSchema.getTypes()) {
                if (innerSchema.getType() != Schema.Type.NULL && isTimestampMillis(innerSchema)) {
                    return true;
                }
            }
            return false;
        }

        LogicalType logicalType = avroSchema.getLogicalType();
        return logicalType != null &&
                (logicalType.getName().equals("timestamp-millis") ||
                        logicalType.getName().equals("timestamp-micros"));
    }

    /**
     * Internal class to hold column definition.
     */
    private static class ColumnDefinition {
        String name;
        Schema avroSchema;
        String flinkType;           // Type for Kafka source (BIGINT for timestamps)
        String flinkTypeForIceberg; // Type for Iceberg (TIMESTAMP for timestamps)
        boolean needsTimestampConversion;
    }
}
