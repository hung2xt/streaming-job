from pyspark.sql import SparkSession
import os
from pyspark.sql.types import StructField, StructType, StringType, IntegerType

YOUR_SECRET_ACCESS_KEY = 'GOOGMEYY7ZKQDOWCOP7YEWPK'
YOUR_ACCESS_KEY_ID = '188r1LfhT1KcK2eX3NF1l+q5nGCrqQfo2WUbyhmn'
# Initialize a SparkSession with necessary configurations
from pyspark.sql import SparkSession

# Path to your service account key
service_account_json = """{
}"""

import json

service_account_str = json.dumps(service_account_json)

import os
import tempfile

# Create a temporary file to store the service account JSON
with tempfile.NamedTemporaryFile(mode='w', delete=False) as tmp_file:
    tmp_file.write(service_account_str)
    temp_service_account_path = tmp_file.name


os.environ['GOOGLE_APPLICATION_CREDENTIALS'] = temp_service_account_path

# Create a Spark session with GCS support
spark = SparkSession.builder \
    .appName("GCSIntegration") \
    .config("spark.hadoop.google.cloud.auth.service.account.enable", "true") \
    .config("spark.hadoop.google.cloud.auth.service.account.json.keyfile", temp_service_account_path) \
    .config("spark.hadoop.fs.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS") \
    .getOrCreate()

# spark._jsc.hadoopConfiguration().set("fs.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem")
# spark._jsc.hadoopConfiguration().set("fs.AbstractFileSystem.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS")

# Use a GCS bucket path in your read operation

schema = StructType([
    StructField('ID', IntegerType(), True),
    StructField('ATTR1', StringType(), True)
])


# Specify the GCS bucket path using the S3A connector
json_file_path = "gs://github_tf/public_data/test.json"

# Read data from the GCS bucket

# df = spark.read.csv(gcs_bucket_path)

#df = spark.read.format("csv").option("header", "true").load(gcs_bucket_path)

# Show the DataFrame

df = spark.read.json(json_file_path, schema, multiLine=True)
print(df.schema)
df.show()




'''
spark-submit \
--master "local[2]" \
--packages com.amazonaws:aws-java-sdk-s3:1.12.31,org.apache.hadoop:hadoop-aws:3.2.0,com.amazonaws:aws-java-sdk-bundle:1.11.375 \
spark_read_gcs.py

spark-submit \
--master "local[2]" \
--conf spark.executor.memory=1g \
--packages com.google.cloud.bigdataoss:gcs-connector:hadoop3-2.2.18 \
--jars gcs-connector-hadoop2-latest.jar  \
spark_read_gcs.py


spark-submit \
--master "local[2]" \
spark_read_gcs.py

gcloud iam service-accounts keys create sa.json --iam-account terraform-gcp@sawyer-work-1804.iam.gserviceaccount.com

docker run -v sa.json bitnami/spark:3.3.0-debian-11-r16

docker run -v sa.json:/opt/bitnami/spark/sa.json bitnami/spark:3.3.0-debian-11-r16

docker exec -it spark_master ls -l /opt/bitnami/spark/sa.json
docker run -e GOOGLE_APPLICATION_CREDENTIALS=./sa.json -it spark_master

export GOOGLE_APPLICATION_CREDENTIALS=/opt/bitnami/spark/sa.json


docker exec -it spark_master /bin/bash \
spark-submit \
--master "local[2]" \
--packages com.google.cloud.bigdataoss:gcs-connector:hadoop3-2.2.18 \
--jars gcs-connector-hadoop2-latest.jar \
spark_read_gcs.py
 

docker exec -it spark_master /bin/bash \
spark-submit \
--master "local[2]" \
--jars gcs-connector-hadoop3-latest.jar \
spark_read_gcs.py

'''


