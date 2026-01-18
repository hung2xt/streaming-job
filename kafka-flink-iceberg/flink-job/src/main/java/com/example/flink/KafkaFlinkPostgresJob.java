package com.example.flink;

import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;

public class KafkaFlinkPostgresJob {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("🚀 Kafka → Flink → PostgreSQL Streaming Job");
        System.out.println("=".repeat(70));

        EnvironmentSettings settings = EnvironmentSettings
                .newInstance()
                .inStreamingMode()
                .build();

        TableEnvironment tableEnv = TableEnvironment.create(settings);

        // Create Kafka source for ORDERS
        System.out.println("\n📊 Step 1/4: Creating Kafka source 'kafka_orders'");
        tableEnv.executeSql(
            "CREATE TABLE kafka_orders (" +
            "  order_id STRING," +
            "  customer_id BIGINT," +
            "  product_id STRING," +
            "  product_name STRING," +
            "  quantity INT," +
            "  total_amount DOUBLE," +
            "  order_time STRING," +
            "  status STRING" +
            ") WITH (" +
            "  'connector' = 'kafka'," +
            "  'topic' = 'orders'," +
            "  'properties.bootstrap.servers' = 'localhost:9093'," +
            "  'properties.group.id' = 'flink-postgres-group'," +
            "  'scan.startup.mode' = 'latest-offset'," +
            "  'format' = 'json'," +
            "  'json.ignore-parse-errors' = 'true'" +
            ")"
        );
        System.out.println("   ✅ kafka_orders created");

        // Create Kafka source for CUSTOMERS
        System.out.println("\n📊 Step 2/4: Creating Kafka source 'kafka_customers'");
        tableEnv.executeSql(
            "CREATE TABLE kafka_customers (" +
            "  customer_id BIGINT," +
            "  name STRING," +
            "  email STRING," +
            "  city STRING," +
            "  tier STRING," +
            "  loyalty_points INT" +
            ") WITH (" +
            "  'connector' = 'kafka'," +
            "  'topic' = 'customers'," +
            "  'properties.bootstrap.servers' = 'localhost:9093'," +
            "  'properties.group.id' = 'flink-postgres-group'," +
            "  'scan.startup.mode' = 'latest-offset'," +
            "  'format' = 'json'," +
            "  'json.ignore-parse-errors' = 'true'" +
            ")"
        );
        System.out.println("   ✅ kafka_customers created");

        // Create PostgreSQL sink
        // IMPORTANT: Replace database credentials with your own
        System.out.println("\n📊 Step 3/4: Creating PostgreSQL sink 'enriched_orders'");
        tableEnv.executeSql(
            "CREATE TABLE enriched_orders (" +
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
            "  total_amount DOUBLE," +
            "  PRIMARY KEY (order_id) NOT ENFORCED" +
            ") WITH (" +
            "  'connector' = 'jdbc'," +
            "  'url' = 'jdbc:postgresql://localhost:5432/YOUR_DATABASE'," +
            "  'table-name' = 'enriched_orders'," +
            "  'username' = 'YOUR_USERNAME'," +
            "  'password' = 'YOUR_PASSWORD'," +
            "  'driver' = 'org.postgresql.Driver'," +
            "  'sink.buffer-flush.max-rows' = '100'," +
            "  'sink.buffer-flush.interval' = '1s'" +
            ")"
        );
        System.out.println("   ✅ PostgreSQL sink created");

        // Execute join and insert
        System.out.println("\n📊 Step 4/4: Executing streaming join and writing to PostgreSQL");
        tableEnv.executeSql(
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
            "FROM kafka_orders AS o " +
            "LEFT JOIN kafka_customers AS c " +
            "ON o.customer_id = c.customer_id"
        );

        System.out.println("\n" + "=".repeat(70));
        System.out.println("✅ Job running - data flowing to PostgreSQL!");
        System.out.println("=".repeat(70));
        System.out.println("\n🗄️  Database: YOUR_DATABASE");
        System.out.println("📊 Table: enriched_orders");
        System.out.println("🌐 Monitor: http://localhost:8081");
        System.out.println("=".repeat(70));
    }
}
