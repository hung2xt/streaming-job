package com.example.flink;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.StatementSet;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

/**
 * Flink Streaming Job: Kafka → Stream Join → Iceberg (MinIO)
 *
 * This job:
 * 1. Reads orders from Kafka topic 'orders'
 * 2. Reads customers from Kafka topic 'customers'
 * 3. Performs temporal join (stream-stream join)
 * 4. Writes enriched data to Iceberg table on MinIO
 */
public class KafkaFlinkIcebergJob {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("🚀 Kafka → Flink → Iceberg Streaming Job");
        System.out.println("=".repeat(70));

        // Create Stream Execution Environment with checkpointing
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Enable checkpointing every 30 seconds for Iceberg commits
        env.enableCheckpointing(30000); // 30 seconds
        System.out.println("   ✅ Checkpointing enabled (30s interval)");

        // Create Table Environment from stream environment
        EnvironmentSettings settings = EnvironmentSettings
                .newInstance()
                .inStreamingMode()
                .build();

        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);

        // Configure job
        tableEnv.getConfig().set("parallelism.default", "2");
        tableEnv.getConfig().set("pipeline.name", "Kafka-Flink-Iceberg-Java");

        // Configure S3/MinIO (using Hadoop S3A properties)
        // NOTE: Credentials are loaded from core-site.xml in HADOOP_CONF_DIR
        System.out.println("\n🔧 Configuring S3/MinIO...");
        tableEnv.getConfig().set("fs.s3a.endpoint", "http://localhost:9000");
        tableEnv.getConfig().set("fs.s3a.path.style.access", "true");
        tableEnv.getConfig().set("fs.s3a.connection.ssl.enabled", "false");
        System.out.println("   ✅ S3A configuration set (credentials from core-site.xml)");

        // Step 1: Create Kafka source for ORDERS
        System.out.println("\n📊 Step 1/5: Creating Kafka source 'kafka_orders'");
        tableEnv.executeSql(
            "CREATE TABLE kafka_orders (" +
            "  order_id STRING," +
            "  customer_id BIGINT," +
            "  product_id STRING," +
            "  product_name STRING," +
            "  quantity INT," +
            "  total_amount DOUBLE," +
            "  order_time STRING," +
            "  status STRING," +
            "  proc_time AS PROCTIME()" +
            ") WITH (" +
            "  'connector' = 'kafka'," +
            "  'topic' = 'orders'," +
            "  'properties.bootstrap.servers' = 'localhost:9093'," +
            "  'properties.group.id' = 'flink-java-group'," +
            "  'scan.startup.mode' = 'latest-offset'," +
            "  'format' = 'json'," +
            "  'json.ignore-parse-errors' = 'true'" +
            ")"
        );
        System.out.println("   ✅ kafka_orders created");

        // Step 2: Create Kafka source for CUSTOMERS
        System.out.println("\n📊 Step 2/5: Creating Kafka source 'kafka_customers'");
        tableEnv.executeSql(
            "CREATE TABLE kafka_customers (" +
            "  customer_id BIGINT," +
            "  name STRING," +
            "  email STRING," +
            "  city STRING," +
            "  tier STRING," +
            "  loyalty_points INT," +
            "  proc_time AS PROCTIME()" +
            ") WITH (" +
            "  'connector' = 'kafka'," +
            "  'topic' = 'customers'," +
            "  'properties.bootstrap.servers' = 'localhost:9093'," +
            "  'properties.group.id' = 'flink-java-group'," +
            "  'scan.startup.mode' = 'latest-offset'," +
            "  'format' = 'json'," +
            "  'json.ignore-parse-errors' = 'true'" +
            ")"
        );
        System.out.println("   ✅ kafka_customers created");

        // Step 3: Create Iceberg catalog
        System.out.println("\n📊 Step 3/5: Creating Iceberg catalog");
        tableEnv.executeSql(
            "CREATE CATALOG iceberg_catalog WITH (" +
            "  'type' = 'iceberg'," +
            "  'catalog-type' = 'hadoop'," +
            "  'warehouse' = 's3a://iceberg-data-warehouse/warehouse'" +
            ")"
        );
        System.out.println("   ✅ Iceberg catalog created");

        tableEnv.executeSql("USE CATALOG iceberg_catalog");
        System.out.println("   ✅ Using iceberg_catalog");

        // Step 4: Create Iceberg table
        System.out.println("\n📊 Step 4/5: Creating Iceberg table 'enriched_orders'");
        tableEnv.executeSql(
            "CREATE TABLE IF NOT EXISTS enriched_orders (" +
            "  order_id STRING," +
            "  customer_id BIGINT," +
            "  customer_name STRING," +
            "  email STRING," +
            "  city STRING," +
            "  tier STRING," +
            "  loyalty_points INT," +
            "  product_id STRING," +
            "  product_name STRING," +
            "  quantity INT," +
            "  total_amount DOUBLE" +
            ")"
        );
        System.out.println("   ✅ enriched_orders table created");

        // Step 5: Execute streaming join and insert
        System.out.println("\n📊 Step 5/5: Submitting streaming join job");

        StatementSet statementSet = tableEnv.createStatementSet();

        statementSet.addInsertSql(
            "INSERT INTO enriched_orders " +
            "SELECT " +
            "  o.order_id," +
            "  o.customer_id," +
            "  c.name AS customer_name," +
            "  c.email," +
            "  c.city," +
            "  c.tier," +
            "  c.loyalty_points," +
            "  o.product_id," +
            "  o.product_name," +
            "  o.quantity," +
            "  o.total_amount " +
            "FROM default_catalog.default_database.kafka_orders AS o " +
            "LEFT JOIN default_catalog.default_database.kafka_customers AS c " +
            "ON o.customer_id = c.customer_id"
        );

        System.out.println("   ⏳ Executing job...");
        statementSet.execute();

        System.out.println("\n" + "=".repeat(70));
        System.out.println("✅ Job submitted successfully!");
        System.out.println("=".repeat(70));
        System.out.println("\n🌐 Monitor in Flink UI: http://localhost:8081");
        System.out.println("📁 Data location: s3a://iceberg-data-warehouse/warehouse/enriched_orders");
        System.out.println("=".repeat(70));
    }
}
