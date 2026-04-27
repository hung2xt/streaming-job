# Streaming Job with Spark, Kafka, and Hive

This project demonstrates how to set up a streaming job using Apache Spark, Kafka, and Hive. The streaming data is generated using a Python script and sent to a Kafka topic, which is then consumed by a Spark job and written to Hive.

## Prerequisites

- Install Java Development Kit (JDK)
- Install Apache Kafka
- Install Apache Spark
- Install Hadoop and configure HDFS
- Install Hive and configure it to use HDFS

## Step-by-Step Instructions

### Step 1: Start HDFS and Hive

1. **Start HDFS**:
    ```bash
    start-dfs.sh
    ```

2. **Start Hive Metastore**:
    ```bash
    hive --service metastore &
    ```

### Step 2: Start Kafka

1. **Start ZooKeeper**:
    ```bash
    zookeeper-server-start.sh /usr/local/etc/kafka/zookeeper.properties &
    ```

2. **Start Kafka**:
    ```bash
    kafka-server-start.sh /usr/local/etc/kafka/server.properties &
    ```

3. **Create Kafka Topic**:
    ```bash
    kafka-topics.sh --create --topic website-visit --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
    ```

### Step 3: Generate Streaming Data and Send to Kafka

1. **Install Required Python Libraries**:
    ```bash
    pip install faker kafka-python geocoder
    ```

2. **Run Data Generator**:
    ```bash
    python data_gen.py --source streaming --num_users 5 --delay_seconds 0.5
    ```

### Step 4: Prepare SinkHive.scala for Spark

Ensure you have the necessary JAR files for Kafka and Hive dependencies in your Spark environment.

### Step 5: Compile and Run SinkHive.scala

1. **Save `SinkHive.scala`**:
    ```scala
    import org.apache.spark.sql.SparkSession
    import org.apache.spark.sql.types._
    import org.apache.spark.sql.functions._
    import org.apache.spark.sql.streaming._
    import org.apache.log4j.{Level, Logger}

    object SinkHive {
      def main(args: Array[String]): Unit = {
        
        Logger.getLogger("org").setLevel(Level.WARN)

        val spark = SparkSession.builder
                        .appName("SinkKafkaToHive")
                        .config("spark.sql.warehouse.dir", "hdfs:cody_sand_us_central1:9000/hive/warehouse/hive_data_sandbox.db")
                        .master("local[*]")
                        .enableHiveSupport()
                        .getOrCreate()
        
        import spark.implicits._

        val schema = new StructType()
                        .add("ip", StringType)
                        .add("id", StringType)
                        .add("lat", DoubleType)
                        .add("lng", DoubleType)
                        .add("user_agent", StringType)
                        .add("age_bracket", StringType)
                        .add("opted_into_marketing", BooleanType)
                        .add("http_request", StringType)
                        .add("http_response", IntegerType)
                        .add("file_size_bytes", IntegerType)
                        .add("event_datetime", StringType)
                        .add("event_ts", LongType)

        val kafkaDF = spark.readStream
                        .format("kafka")
                        .option("kafka.bootstrap.servers", "localhost:9092")
                        .option("subscribe", "website-visit")
                        .load()
        
        val jsonDF = kafkaDF.selectExpr("CAST(value AS STRING)")
                        .select(from_json($"value", schema).as("data"))
                        .select("data.*")
        
        val query = jsonDF.writeStream
                        .outputMode("append")
                        .format("parquet")
                        .option("path", "hdfs://localhost:9000/user/hive/warehouse/hive_data_sandbox.db/website_visit")
                        .option("checkpointLocation", "hdfs:cody_sand_us_central1:9000/hive/warehouse/hive_data_sandbox.db/website_visit_checkpoint")
                        .start()

        query.awaitTermination()
      }
    }
    ```

2. **Compile and Run**:
    ```bash
    spark-submit --class SinkHive --master local[*] /path/to/SinkHive.jar
    ```

### Step 6: Verify Data in Hive

1. **Open Hive Shell**:
    ```bash
    hive
    ```

2. **Query the Data**:
    ```sql
    USE hive_data_sandbox;
    SHOW TABLES;
    SELECT * FROM website_visit;
    ```

By following these steps, you should be able to run your Spark job, read from Kafka, and write data to Hive. Please reach out me for further assistance!
