package com.example.flink.schema;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages schema evolution by detecting changes from Confluent Schema Registry.
 * Compares cached schemas with latest versions to identify new fields.
 */
public class SchemaEvolutionManager implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(SchemaEvolutionManager.class);
    private static final int SCHEMA_CACHE_CAPACITY = 100;

    private final String schemaRegistryUrl;
    private transient SchemaRegistryClient schemaRegistryClient;
    private final Map<String, Schema> cachedSchemas;
    private final Map<String, Integer> cachedVersions;

    public SchemaEvolutionManager(String schemaRegistryUrl) {
        this.schemaRegistryUrl = schemaRegistryUrl;
        this.cachedSchemas = new ConcurrentHashMap<>();
        this.cachedVersions = new ConcurrentHashMap<>();
    }

    /**
     * Initializes the Schema Registry client. Must be called before using other methods.
     */
    public void initialize() {
        if (schemaRegistryClient == null) {
            Map<String, Object> configs = new HashMap<>();
            schemaRegistryClient = new CachedSchemaRegistryClient(
                    schemaRegistryUrl,
                    SCHEMA_CACHE_CAPACITY,
                    configs
            );
            LOG.info("Schema Registry client initialized with URL: {}", schemaRegistryUrl);
        }
    }

    /**
     * Fetches the current (latest) schema for a given subject from Schema Registry.
     *
     * @param subject The subject name (e.g., "orders_avro-value")
     * @return The latest Avro schema
     * @throws SchemaEvolutionException if schema cannot be fetched
     */
    public Schema getCurrentSchema(String subject) throws SchemaEvolutionException {
        initialize();
        try {
            io.confluent.kafka.schemaregistry.client.SchemaMetadata metadata =
                    schemaRegistryClient.getLatestSchemaMetadata(subject);
            String schemaString = metadata.getSchema();
            return new Schema.Parser().parse(schemaString);
        } catch (IOException | RestClientException e) {
            throw new SchemaEvolutionException("Failed to fetch schema for subject: " + subject, e);
        }
    }

    /**
     * Gets the latest version number for a subject.
     *
     * @param subject The subject name
     * @return The latest version number
     * @throws SchemaEvolutionException if version cannot be fetched
     */
    public int getLatestVersion(String subject) throws SchemaEvolutionException {
        initialize();
        try {
            io.confluent.kafka.schemaregistry.client.SchemaMetadata metadata =
                    schemaRegistryClient.getLatestSchemaMetadata(subject);
            return metadata.getVersion();
        } catch (IOException | RestClientException e) {
            throw new SchemaEvolutionException("Failed to fetch version for subject: " + subject, e);
        }
    }

    /**
     * Checks if the schema has evolved (new version available) since last check.
     *
     * @param subject The subject name
     * @return true if schema has evolved, false otherwise
     */
    public boolean hasSchemaEvolved(String subject) throws SchemaEvolutionException {
        int latestVersion = getLatestVersion(subject);
        Integer cachedVersion = cachedVersions.get(subject);

        if (cachedVersion == null) {
            // First time seeing this subject, cache it
            Schema currentSchema = getCurrentSchema(subject);
            cachedSchemas.put(subject, currentSchema);
            cachedVersions.put(subject, latestVersion);
            LOG.info("Initial schema cached for subject: {} (version {})", subject, latestVersion);
            return false;
        }

        if (latestVersion > cachedVersion) {
            LOG.info("Schema evolution detected for subject: {} (version {} -> {})",
                    subject, cachedVersion, latestVersion);
            return true;
        }

        return false;
    }

    /**
     * Gets the list of new fields added in the latest schema compared to the cached version.
     *
     * @param subject The subject name
     * @return List of new Schema.Field objects
     */
    public List<Schema.Field> getNewFields(String subject) throws SchemaEvolutionException {
        List<Schema.Field> newFields = new ArrayList<>();

        Schema cachedSchema = cachedSchemas.get(subject);
        Schema latestSchema = getCurrentSchema(subject);

        if (cachedSchema == null) {
            // No cached schema, all fields are "new" but we treat this as initial state
            cachedSchemas.put(subject, latestSchema);
            cachedVersions.put(subject, getLatestVersion(subject));
            return newFields;
        }

        // Find fields in latest schema that don't exist in cached schema
        Map<String, Schema.Field> cachedFieldMap = new HashMap<>();
        for (Schema.Field field : cachedSchema.getFields()) {
            cachedFieldMap.put(field.name(), field);
        }

        for (Schema.Field field : latestSchema.getFields()) {
            if (!cachedFieldMap.containsKey(field.name())) {
                newFields.add(field);
                LOG.info("New field detected: {} (type: {})", field.name(), field.schema().toString());
            }
        }

        // Update cache with latest schema
        cachedSchemas.put(subject, latestSchema);
        cachedVersions.put(subject, getLatestVersion(subject));

        return newFields;
    }

    /**
     * Gets the cached schema for a subject.
     *
     * @param subject The subject name
     * @return The cached schema, or null if not cached
     */
    public Schema getCachedSchema(String subject) {
        return cachedSchemas.get(subject);
    }

    /**
     * Forces a refresh of the cached schema for a subject.
     *
     * @param subject The subject name
     */
    public void refreshSchema(String subject) throws SchemaEvolutionException {
        Schema currentSchema = getCurrentSchema(subject);
        int version = getLatestVersion(subject);
        cachedSchemas.put(subject, currentSchema);
        cachedVersions.put(subject, version);
        LOG.info("Schema refreshed for subject: {} (version {})", subject, version);
    }

    /**
     * Gets all registered versions for a subject.
     *
     * @param subject The subject name
     * @return List of version numbers
     */
    public List<Integer> getAllVersions(String subject) throws SchemaEvolutionException {
        initialize();
        try {
            return schemaRegistryClient.getAllVersions(subject);
        } catch (IOException | RestClientException e) {
            throw new SchemaEvolutionException("Failed to get versions for subject: " + subject, e);
        }
    }

    /**
     * Exception class for schema evolution errors.
     */
    public static class SchemaEvolutionException extends Exception {
        public SchemaEvolutionException(String message) {
            super(message);
        }

        public SchemaEvolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
