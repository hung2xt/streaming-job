#!/bin/bash


# Define the Docker container name or ID of the Spark master
SPARK_MASTER_CONTAINER=spark_master

# Define the path to your Spark application's jar file and your main class
APP_JAR="/opt/bitnami/spark/jars/spark-sql-kafka-0-10_2.13-3.3.0.jar,\
/opt/bitnami/spark/jars/hadoop-aws-3.3.2.jar,\
/opt/bitnami/spark/jars/aws-java-sdk-s3-1.11.375.jar,\
/opt/bitnami/spark/jars/ccommons-pool-1.5.4.jar,/opt/bitnami/spark/jars/scala-library-2.13.jar"

# Kafka and AWS related dependencies
PACKAGES="org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0, \
com.amazonaws:aws-java-sdk-s3:1.12.31, \
org.apache.hadoop:hadoop-aws:3.2.0, \
com.amazonaws:aws-java-sdk-bundle:1.11.375"


# AWS credentials - replace with your credentials or use IAM roles if running on AWS services like EC2 or EMR


# Spark submit command
docker exec -it $SPARK_MASTER_CONTAINER /bin/bash \
    spark-submit \
    --master "local[2]" \
    --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,com.amazonaws:aws-java-sdk-s3:1.12.31,org.apache.hadoop:hadoop-aws:3.2.0,com.amazonaws:aws-java-sdk-bundle:1.11.375 \
    /opt/bitnami/spark/spark_from_kafka_to_s3.py 



