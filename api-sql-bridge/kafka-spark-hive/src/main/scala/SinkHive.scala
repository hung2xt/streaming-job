import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.Row
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.streaming._


object SinkHive {
  def main(args: Array[String]): Unit = {
    
    Logger.getLogger("org").setLevel(Level.WARN)

    val spark = SparkSession.builder
                    .appName("SinkKafkaToHive")
                    .config("spark.sql.warehouse.dir", "hdfs://localhost:9000/user/hive/warehouse/hive_data_sandbox.db")
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
    

    // val query = jsonDF.writeStream
    //                 .outputMode("append")
    //                 .format("console")
    //                 .start()


    val query = jsonDF.writeStream
                    .outputMode("append")
                    .format("parquet")
                    .option("path", "hdfs://localhost:9000/user/hive/warehouse/hive_data_sandbox.db/website_visit")
                    .option("checkpointLocation", "hdfs://localhost:9000/user/hive/warehouse/hive_data_sandbox.db/website_visit_checkpoint")
                    .start()

    query.awaitTermination()
  }
}
