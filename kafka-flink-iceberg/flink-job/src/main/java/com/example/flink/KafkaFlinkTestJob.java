package com.example.flink;

import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;

public class KafkaFlinkTestJob {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("🧪 Kafka → Flink Join Test (Console Output)");
        System.out.println("=".repeat(70));

        EnvironmentSettings settings = EnvironmentSettings
                .newInstance()
                .inStreamingMode()
                .build();

        TableEnvironment tableEnv = TableEnvironment.create(settings);

        // Create Kafka source for ORDERS
        System.out.println("\n📊 Creating Kafka source 'kafka_orders'");
        tableEnv.executeSql(
            "CREATE TABLE kafka_orders (" +
            "  order_id STRING," +
            "  customer_id BIGINT," +
            "  product_name STRING," +
            "  total_amount DOUBLE" +
            ") WITH (" +
            "  'connector' = 'kafka'," +
            "  'topic' = 'orders'," +
            "  'properties.bootstrap.servers' = 'localhost:9093'," +
            "  'properties.group.id' = 'test-group'," +
            "  'scan.startup.mode' = 'latest-offset'," +
            "  'format' = 'json'" +
            ")"
        );
        System.out.println("   ✅ kafka_orders created");

        // Create Kafka source for CUSTOMERS
        System.out.println("\n📊 Creating Kafka source 'kafka_customers'");
        tableEnv.executeSql(
            "CREATE TABLE kafka_customers (" +
            "  customer_id BIGINT," +
            "  name STRING," +
            "  city STRING" +
            ") WITH (" +
            "  'connector' = 'kafka'," +
            "  'topic' = 'customers'," +
            "  'properties.bootstrap.servers' = 'localhost:9093'," +
            "  'properties.group.id' = 'test-group'," +
            "  'scan.startup.mode' = 'latest-offset'," +
            "  'format' = 'json'" +
            ")"
        );
        System.out.println("   ✅ kafka_customers created");

        // Create print sink
        System.out.println("\n📊 Creating print sink");
        tableEnv.executeSql(
            "CREATE TABLE enriched_print (" +
            "  order_id STRING," +
            "  customer_id BIGINT," +
            "  customer_name STRING," +
            "  city STRING," +
            "  product_name STRING," +
            "  total_amount DOUBLE" +
            ") WITH (" +
            "  'connector' = 'print'" +
            ")"
        );
        System.out.println("   ✅ print sink created");

        // Execute join and print
        System.out.println("\n📊 Executing streaming join...");
        tableEnv.executeSql(
            "INSERT INTO enriched_print " +
            "SELECT " +
            "  o.order_id," +
            "  o.customer_id," +
            "  c.name AS customer_name," +
            "  c.city," +
            "  o.product_name," +
            "  o.total_amount " +
            "FROM kafka_orders AS o " +
            "LEFT JOIN kafka_customers AS c " +
            "ON o.customer_id = c.customer_id"
        );

        System.out.println("\n" + "=".repeat(70));
        System.out.println("✅ Job running - check TaskManager logs for output");
        System.out.println("=".repeat(70));
    }
}
