# From Kafka, Spark to S3
## **Introduction: Building a Dynamic Data Engineering Project**

In today's data-centric world, data engineering is pivotal. With data volumes growing, real-time processing and analytics are essential. This guide dives into building a strong data pipeline, integrating Kafka, Spark, Airflow, Docker, S3, and Python.

We use the Random Name API to mimic real-time data flow. A Python script fetches data from this API, sending it to a Kafka topic. Airflow DAGs ensure regular data streaming. Spark processes this data and transfers it to S3.

Each component, including Kafka, Spark, and Airflow, operates in isolated Docker environments, enhancing functionality and scalability.

## **Getting Started: Prerequisites and Setup**

For this project, we are leveraging a GitHub repository that hosts our entire setup, making it easy for anyone to get started.

**a. Docker:**
Docker will be our primary tool to orchestrate and run various services.

- **Installation:** Visit Docker's official website to download and install Docker Desktop for your OS.
- **Verification:** Open a terminal or command prompt and execute `docker --version` to ensure a successful installation.

**b. S3:**
AWS S3 is our go-to for data storage.

- **Setup:** Log in to the AWS Management Console, navigate to the S3 service, and establish a new bucket, ensuring it's configured according to your data storage preferences.

**c. Setting Up the Project:**

- **Clone the Repository:** First, you'll need to clone the project from its GitHub repository using the following command:

```
git clone https://github.com/hung2xt/streaming-job.git
```

Navigate to the project directory:

```
cd ./spark-kafka-s3
```

- **Deploy Services using `docker-compose`:** Within the project directory, you'll find a `docker-compose.yml` file. This file describes all the services and their

```
docker network create docker_streaming
docker-compose -f docker-compose.yml up -d
```

This command orchestrates the start-up of all necessary services like Kafka, Spark, Airflow, etc., in Docker containers.

## **Breaking Down the projects Files**

#### 1) `docker-compose.yml`

The heart of our project setup lies in the **`docker-compose.yml`** file. It orchestrates our services, ensuring smooth communication and initialization. Here's a breakdown:

* Version

We're using Docker Compose file format version '3.7', ensuring compatibility with our services.

* Services

Our project encompasses several services:

- **Airflow:**
- **Database (`airflow_db`):** Uses PostgreSQL.
- **Web Server (`airflow_webserver`):** Initiates the database and sets up an admin user.
- **Kafka:**
- **Zookeeper (`kafka_zookeeper`):** Manages broker metadata.
- **Brokers:** Three instances (**`kafka_broker_1`**, **`2`**, and **`3`**).
- **Base Configuration (`kafka_base`):** Common settings for brokers.
- **Kafka Connect (`kafka_connect`):** Facilitates stream processing.
- **Schema Registry (`kafka_schema_registry`):** Manages Kafka schemas.
- **User Interface (`kafka_ui`):** Visual interface for Kafka insights.
- **Spark:**
- **Master Node (`spark_master`):** The central control node for Apache Spark.

* Volumes

We utilize a persistent volume, **`spark_data`**, ensuring data consistency for Spark.

* Networks

Two networks anchor our services:

- **Kafka Network (`kafka_network`):** Dedicated to Kafka.
- **Default Network (`default`):** Externally named as **`spark-kafka-network`**.

#### 2) `kafka_streaming_airflow_dag.py`

This file primarily defines an Airflow Directed Acyclic Graph (DAG) that handles the streaming of data to a Kafka topic.

* Imports

Essential modules and functions are imported, notably the Airflow DAG and PythonOperator, as well as a custom **`initiate_stream`** function from **`kafka_publisher.py`**.

* Configuration

- **DAG Start Date (`DAG_START_DATE`):** Sets when the DAG begins its execution.
- **Default Arguments (`DAG_DEFAULT_ARGS`):** Configures the DAG's basic parameters, such as owner, start date, and retry settings.

* DAG Definition

A new DAG is created with the name **`name_stream_dag`**, configured to run daily at every 5 minutes.

* Tasks

A single task, **`kafka_publisher`**, is defined using the PythonOperator. This task calls the **`initiate_stream`** function, effectively streaming data to Kafka when the DAG runs.

#### 3) `kafka_publisher.py`

* Imports & Configuration

Essential libraries are imported, and constants are set, such as the API endpoint, Kafka bootstrap servers, topic name, and streaming interval details.

* User Data Retrieval

The **`retrieve_user_data`** function fetches random user details from the specified API endpoint.

* Data Transformation

The **`transform_user_data`** function formats the raw user data for Kafka streaming, while **`encrypt_zip`** hashes the zip code to maintain user privacy.

* Kafka Configuration & Publishing

- **`configure_kafka`** sets up a Kafka producer.
- **`publish_to_kafka`** sends transformed user data to a Kafka topic.
- **`delivery_status`** provides feedback on whether data was successfully sent to Kafka.

* Main Streaming Function

**`initiate_stream`** orchestrates the entire process, retrieving, transforming, and publishing user data to Kafka at regular intervals.

* Execution

When the script is run directly, the **`initiate_stream`** function is executed, streaming data for the duration specified by **`STREAMING_DURATION`**.

#### 4)  **`spark_from_kafka_to_s3.py`**

* Imports & Logging Initialization

The necessary libraries are imported, and a logging setup is created for better debugging and monitoring.

* Spark Session Initialization

* `initialize_spark_session`: This function sets up the Spark session with configurations required to access data from S3.

* Data Retrieval & Transformation

- **`get_streaming_dataframe`**: Fetches a streaming dataframe from Kafka with specified brokers and topic details.
- **`transform_streaming_data`**: Transforms the raw Kafka data into a desired structured format.

* Streaming to S3

**`initiate_streaming_to_bucket`**: This function streams the transformed data to an S3 bucket in parquet format. It uses a checkpoint mechanism to ensure data integrity during streaming.

* Main Execution

The **`main`** function orchestrates the entire process: initializing the Spark session, fetching data from Kafka, transforming it, and streaming it to S3.

* Script Execution

If the script is the main module being run, it will execute the **`main`** function, initiating the entire streaming process.

## **Building the Data Pipeline: Step-by-Step**

* Set Up Kafka Cluster

Start your Kafka cluster with the following commands:

```bash
docker network create spark-kafka-network
docker-compose -f docker-compose.yml up -d
```

* 2. Create the topic for Kafka (**http://localhost:8888/)

- Access the Kafka UI at http://localhost:8888/.
- Observe the active cluster.
- Navigate to 'Topics'.
- Create a new topic named "created_users".
- Set the replication factor to 3.

* Configure Airflow User

Create an Airflow user with admin privileges:

```bash
docker-compose run airflow_webserver airflow users create --role Admin --username admin --email admin --firstname admin --lastname admin --password admin
```

* Access Airflow Bash & Install Dependencies

Use the provided script to access the Airflow bash and install required packages:

```bash
./airflow.sh bash
pip install -r ./requirements.txt
```

* Validate DAGs

Ensure there are no errors with your DAGs:

```bash
airflow dags list
```

* Start Airflow Scheduler

To initiate the DAG, run the scheduler:

```bash
airflow scheduler
```

* Verify the data is uploaded to Kafka Cluster

- Access the Kafka UI at http://localhost:8888/ and verify that the data is uploaded for the topic

* Transfer Spark Script

Copy your Spark script into the Docker container:

```bash
docker cp spark_from_kafka_to_s3.py spark_master:/opt/bitnami/spark/
```

* Initiate Spark Master & Submit

Access Spark bash and submit the Spark job:

```bash
docker exec -it spark_master /bin/bash

spark-submit \
--master "local[2]" \
--packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,com.amazonaws:aws-java-sdk-s3:1.12.31,org.apache.hadoop:hadoop-aws:3.2.0,com.amazonaws:aws-java-sdk-bundle:1.11.375 \
spark_from_kafka_to_s3.py 

```
Alternative option - we might use docker `spark-submit.sh`

```bash
chmod +x spark-submit.sh
./spark-submit.sh
```
* Verify Data on S3

After executing the steps, check your S3 bucket to ensure data has been uploaded

## C**hallenges and Troubleshooting**

1. **Configuration Challenges**: Ensuring environment variables and configurations (like in the **`docker-compose.yaml`** file) are correctly set can be tricky. An incorrect setting might prevent services from starting or communicating.
2. **Service Dependencies**: Services like Kafka or Airflow have dependencies on other services (e.g., Zookeeper for Kafka). Ensuring the correct order of service initialization is crucial.
3. **Airflow DAG Errors**: Syntax or logical errors in the DAG file (`kafka_streaming_airflow_dag.py`) can prevent Airflow from recognizing or executing the DAG correctly.
4. **Data Transformation Issues**: The data transformation logic in the Python script might not always produce expected results, especially when handling various data inputs from the Random Name API.
5. **Spark Dependencies**: Ensuring all required JARs are available and compatible is essential for Spark's streaming job. Missing or incompatible JARs can lead to job failures.
6. **Kafka Topic Management**: Creating topics with the correct configuration (like replication factor) is essential for data durability and fault tolerance.
7. **Networking Challenges**: Docker networking, as set up in the **`docker-compose.yaml`**, must correctly facilitate communication between services, especially for Kafka brokers and Zookeeper.
8. **S3 Bucket Permissions**: Ensuring correct permissions when writing to S3 is crucial. Misconfigured permissions can prevent Spark from saving data to the bucket.
9. **Deprecation Warnings**: The provided logs show deprecation warnings, indicating that some methods or configurations used might become obsolete in future versions.

## **Conclusion:**

This project is a journey through advanced data engineering, leveraging Kafka, Spark, and Airflow. It's an exploration of tool integration, inviting further customization and innovation.


