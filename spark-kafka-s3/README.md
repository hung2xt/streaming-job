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

**1.1. Version**

We're using Docker Compose file format version '3.7', ensuring compatibility with our services.

**1.2. Services**

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

**1.3. Volumes**

We utilize a persistent volume, **`spark_data`**, ensuring data consistency for Spark.

**1.4. Networks**

Two networks anchor our services:

- **Kafka Network (`kafka_network`):** Dedicated to Kafka.
- **Default Network (`default`):** Externally named as **`spark-kafka-network`**.

#### 2) `kafka_streaming_airflow_dag.py`

This file primarily defines an Airflow Directed Acyclic Graph (DAG) that handles the streaming of data to a Kafka topic.

**2.1. Imports**

Essential modules and functions are imported, notably the Airflow DAG and PythonOperator, as well as a custom **`initiate_stream`** function from **`kafka_publisher.py`**.

**2.2. Configuration**

- **DAG Start Date (`DAG_START_DATE`):** Sets when the DAG begins its execution.
- **Default Arguments (`DAG_DEFAULT_ARGS`):** Configures the DAG's basic parameters, such as owner, start date, and retry settings.

**2.3. DAG Definition**

A new DAG is created with the name **`name_stream_dag`**, configured to run daily at every 5 minutes.

**2.4. Tasks**

A single task, **`kafka_publisher`**, is defined using the PythonOperator. This task calls the **`initiate_stream`** function, effectively streaming data to Kafka when the DAG runs.

#### 3) `kafka_publisher.py`

**3.1. Imports & Configuration**

Essential libraries are imported, and constants are set, such as the API endpoint, Kafka bootstrap servers, topic name, and streaming interval details.

**3.2. User Data Retrieval**

The **`retrieve_user_data`** function fetches random user details from the specified API endpoint.

**3.3. Data Transformation**

The **`transform_user_data`** function formats the raw user data for Kafka streaming, while **`encrypt_zip`** hashes the zip code to maintain user privacy.

**3.4. Kafka Configuration & Publishing**

- **`configure_kafka`** sets up a Kafka producer.
- **`publish_to_kafka`** sends transformed user data to a Kafka topic.
- **`delivery_status`** provides feedback on whether data was successfully sent to Kafka.

**3.5. Main Streaming Function**

**`initiate_stream`** orchestrates the entire process, retrieving, transforming, and publishing user data to Kafka at regular intervals.

**3.6. Execution**

When the script is run directly, the **`initiate_stream`** function is executed, streaming data for the duration specified by **`STREAMING_DURATION`**.

#### 4)  **`spark_from_kafka_to_s3.py`**

**4.1. Imports & Logging Initialization**

The necessary libraries are imported, and a logging setup is created for better debugging and monitoring.

**4.2. Spark Session Initialization**

**`initialize_spark_session`**: This function sets up the Spark session with configurations required to access data from S3.

**4.3. Data Retrieval & Transformation**

- **`get_streaming_dataframe`**: Fetches a streaming dataframe from Kafka with specified brokers and topic details.
- **`transform_streaming_data`**: Transforms the raw Kafka data into a desired structured format.

**4.4. Streaming to S3**

**`initiate_streaming_to_bucket`**: This function streams the transformed data to an S3 bucket in parquet format. It uses a checkpoint mechanism to ensure data integrity during streaming.

**4.5. Main Execution**

The **`main`** function orchestrates the entire process: initializing the Spark session, fetching data from Kafka, transforming it, and streaming it to S3.

**4.6. Script Execution**

If the script is the main module being run, it will execute the **`main`** function, initiating the entire streaming process.

## **Building the Data Pipeline: Step-by-Step**

#### 1. Set Up Kafka Cluster

Start your Kafka cluster with the following commands:

```bash
docker network create spark-kafka-network
docker-compose -f docker-compose.yml up -d
```

#### 2. Create the topic for Kafka (**http://localhost:8888/)

- Access the Kafka UI at http://localhost:8888/.
- Observe the active cluster.
- Navigate to 'Topics'.
- Create a new topic named "created_users".
- Set the replication factor to 3.

#### 3. Configure Airflow User

Create an Airflow user with admin privileges:

```bash
docker-compose run airflow_webserver airflow users create --role Admin --username admin --email admin --firstname admin --lastname admin --password admin
```

#### 4. Access Airflow Bash & Install Dependencies

Use the provided script to access the Airflow bash and install required packages:

```bash
./airflow.sh bash
pip install -r ./requirements.txt
```

#### 5. Validate DAGs

Ensure there are no errors with your DAGs:

```bash
airflow dags list
```

#### 6. Start Airflow Scheduler

To initiate the DAG, run the scheduler:

```bash
airflow scheduler
```

#### 6. Verify the data is uploaded to Kafka Cluster

- Access the Kafka UI at http://localhost:8888/ and verify that the data is uploaded for the topic

#### 7. Transfer Spark Script

Copy your Spark script into the Docker container:

```bash
docker cp spark_from_kafka_to_s3.py spark_master:/opt/bitnami/spark/
```

#### 8. Initiate Spark Master & Submit

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
#### 9. Verify Data on S3

After executing the steps, check your S3 bucket to ensure data has been uploaded

## C**hallenges and Troubleshooting**

## Potential Challenges and Considerations for the Data Pipeline

**Configuration:**

* Accurate configuration of environment variables and files like `docker-compose.yaml` is critical for service functionality. Incorrect settings can lead to service failure or communication issues.

**Dependencies:**

* Services such as Kafka and Airflow rely on other services (e.g., Zookeeper for Kafka). Proper service initialization order is crucial.

**Airflow DAG:**

* Grammatical or logical errors in the DAG file (`kafka_streaming_airflow_dag.py`) can hamper Airflow's recognition and execution.

**Data Transformation:**

* The data transformation logic in the Python script might not always handle diverse data inputs from the Random Name API as expected.

**Spark Dependencies:**

* Missing or incompatible JARs can cause Spark's streaming job to fail. Ensuring all required JARs are available and compatible is essential.

**Kafka Topic Management:**

* Data durability and fault tolerance depend on creating topics with proper configurations like replication factors.

**Networking:**

* Proper Docker networking setup in `docker-compose.yaml` is vital for communication between services, particularly Kafka brokers and Zookeeper.

**S3 Bucket Permissions:**

* Misconfigured permissions for writing to S3 can obstruct Spark's data saving process. Correct permission configuration is crucial.

**Deprecation Warnings:**

* The logs indicate potential obsolescence of certain methods or configurations used in future versions.


## **Conclusion:**

This project is a journey through advanced data engineering, leveraging Kafka, Spark, and Airflow. It's an exploration of tool integration, inviting further customization and innovation.


