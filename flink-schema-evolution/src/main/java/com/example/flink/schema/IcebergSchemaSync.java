package com.example.flink.schema;

import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.Table;
import org.apache.iceberg.UpdateSchema;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Synchronizes schema changes from Avro to Iceberg tables.
 * Handles adding new columns to Iceberg tables based on schema evolution.
 */
public class IcebergSchemaSync implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(IcebergSchemaSync.class);

    private final String warehousePath;
    private final String s3Endpoint;
    private final String s3AccessKey;
    private final String s3SecretKey;
    private transient Catalog catalog;

    public IcebergSchemaSync(String warehousePath, String s3Endpoint,
                             String s3AccessKey, String s3SecretKey) {
        this.warehousePath = warehousePath;
        this.s3Endpoint = s3Endpoint;
        this.s3AccessKey = s3AccessKey;
        this.s3SecretKey = s3SecretKey;
    }

    /**
     * Initializes the Iceberg catalog. Must be called before using other methods.
     */
    public void initialize() {
        if (catalog == null) {
            Configuration conf = new Configuration();
            conf.set("fs.s3a.endpoint", s3Endpoint);
            conf.set("fs.s3a.access.key", s3AccessKey);
            conf.set("fs.s3a.secret.key", s3SecretKey);
            conf.set("fs.s3a.path.style.access", "true");
            conf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");
            conf.set("fs.s3a.connection.ssl.enabled", "false");

            catalog = new HadoopCatalog(conf, warehousePath);
            LOG.info("Iceberg catalog initialized with warehouse: {}", warehousePath);
        }
    }

    /**
     * Adds a column to the Iceberg table if it doesn't already exist.
     * This operation is idempotent.
     *
     * @param tableId The table identifier (database.table)
     * @param columnName The name of the column to add
     * @param icebergType The Iceberg type for the column
     */
    public void addColumnIfNotExists(TableIdentifier tableId, String columnName, Type icebergType) {
        initialize();

        Table table = catalog.loadTable(tableId);
        org.apache.iceberg.Schema tableSchema = table.schema();

        // Check if column already exists
        Set<String> existingColumns = tableSchema.columns().stream()
                .map(Types.NestedField::name)
                .collect(Collectors.toSet());

        if (existingColumns.contains(columnName)) {
            LOG.debug("Column '{}' already exists in table {}", columnName, tableId);
            return;
        }

        // Add the new column
        UpdateSchema updateSchema = table.updateSchema();
        updateSchema.addColumn(columnName, icebergType);
        updateSchema.commit();

        LOG.info("Added column '{}' (type: {}) to table {}", columnName, icebergType, tableId);
    }

    /**
     * Synchronizes schema changes by adding new fields from Avro to Iceberg.
     *
     * @param tableId The table identifier
     * @param newFields List of new Avro fields to add
     */
    public void syncSchemaChanges(TableIdentifier tableId, List<Schema.Field> newFields) {
        initialize();

        if (newFields.isEmpty()) {
            LOG.debug("No new fields to sync for table {}", tableId);
            return;
        }

        Table table = catalog.loadTable(tableId);
        org.apache.iceberg.Schema tableSchema = table.schema();

        // Get existing column names
        Set<String> existingColumns = tableSchema.columns().stream()
                .map(Types.NestedField::name)
                .collect(Collectors.toSet());

        // Add each new field
        UpdateSchema updateSchema = table.updateSchema();
        boolean hasChanges = false;

        for (Schema.Field field : newFields) {
            if (existingColumns.contains(field.name())) {
                LOG.debug("Column '{}' already exists, skipping", field.name());
                continue;
            }

            Type icebergType = avroToIcebergType(field.schema());
            if (icebergType != null) {
                updateSchema.addColumn(field.name(), icebergType);
                hasChanges = true;
                LOG.info("Preparing to add column '{}' (type: {})", field.name(), icebergType);
            } else {
                LOG.warn("Unable to convert Avro type for field '{}': {}",
                        field.name(), field.schema());
            }
        }

        if (hasChanges) {
            updateSchema.commit();
            LOG.info("Schema changes committed for table {}", tableId);
        }
    }

    /**
     * Converts an Avro schema type to an Iceberg type.
     *
     * @param avroSchema The Avro schema to convert
     * @return The corresponding Iceberg type, or null if not supported
     */
    public Type avroToIcebergType(Schema avroSchema) {
        Schema.Type avroType = avroSchema.getType();

        // Handle union types (typically [null, actualType] for optional fields)
        if (avroType == Schema.Type.UNION) {
            List<Schema> types = avroSchema.getTypes();
            for (Schema innerSchema : types) {
                if (innerSchema.getType() != Schema.Type.NULL) {
                    // Return the non-null type (Iceberg handles nullability separately)
                    return avroToIcebergType(innerSchema);
                }
            }
            return null;
        }

        // Handle logical types
        LogicalType logicalType = avroSchema.getLogicalType();
        if (logicalType != null) {
            switch (logicalType.getName()) {
                case "timestamp-millis":
                    return Types.TimestampType.withoutZone();
                case "timestamp-micros":
                    return Types.TimestampType.withoutZone();
                case "date":
                    return Types.DateType.get();
                case "time-millis":
                case "time-micros":
                    return Types.TimeType.get();
                case "decimal":
                    // Get precision and scale from logical type properties
                    Object precision = avroSchema.getObjectProp("precision");
                    Object scale = avroSchema.getObjectProp("scale");
                    int p = precision != null ? ((Number) precision).intValue() : 38;
                    int s = scale != null ? ((Number) scale).intValue() : 0;
                    return Types.DecimalType.of(p, s);
                case "uuid":
                    return Types.UUIDType.get();
                default:
                    LOG.warn("Unsupported logical type: {}", logicalType.getName());
            }
        }

        // Handle primitive types
        switch (avroType) {
            case STRING:
                return Types.StringType.get();
            case INT:
                return Types.IntegerType.get();
            case LONG:
                return Types.LongType.get();
            case FLOAT:
                return Types.FloatType.get();
            case DOUBLE:
                return Types.DoubleType.get();
            case BOOLEAN:
                return Types.BooleanType.get();
            case BYTES:
                return Types.BinaryType.get();
            case FIXED:
                return Types.FixedType.ofLength(avroSchema.getFixedSize());
            case ENUM:
                return Types.StringType.get(); // Map enum to string
            case ARRAY:
                Type elementType = avroToIcebergType(avroSchema.getElementType());
                if (elementType != null) {
                    return Types.ListType.ofOptional(1, elementType);
                }
                return null;
            case MAP:
                Type valueType = avroToIcebergType(avroSchema.getValueType());
                if (valueType != null) {
                    return Types.MapType.ofOptional(1, 2, Types.StringType.get(), valueType);
                }
                return null;
            case RECORD:
                LOG.warn("Nested record types require special handling");
                return null;
            case NULL:
                return null;
            default:
                LOG.warn("Unsupported Avro type: {}", avroType);
                return null;
        }
    }

    /**
     * Checks if a table exists in the catalog.
     *
     * @param tableId The table identifier
     * @return true if the table exists
     */
    public boolean tableExists(TableIdentifier tableId) {
        initialize();
        return catalog.tableExists(tableId);
    }

    /**
     * Gets the current Iceberg schema for a table.
     *
     * @param tableId The table identifier
     * @return The Iceberg schema
     */
    public org.apache.iceberg.Schema getTableSchema(TableIdentifier tableId) {
        initialize();
        Table table = catalog.loadTable(tableId);
        return table.schema();
    }
}
