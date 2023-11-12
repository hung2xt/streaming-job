## How to handle streaming job from Google PubSub to BigQuery via Apache Beam (Python)
##### Table of Contents  
* [Step 1 - Resource requirements](#step-1-resource-requirements)
* [Step 2 — Installing and Configuring MySQL Server](#step-2-installing-and-configuring-MySQL-Server)
* [Step 3 — Installing Zookeeper](#step-3-Installing-Zookeeper)
* [Step 4 — Installing Kafka](#step-4-installing-Kafka)
* [Step 5 — Setting Up the Debezium MySQL Connector](#Step-5—Setting-Up-the-Debezium-MySQL-Connector)
* [Step 6 - Operation](#step-6-operation)
* [Step 7 - Using Apache Spark to read streaming data from Kafka](#Step-7-Using-Apache-Spark-to-read-streaming-data-from-Kafka)
### Step 1 - Resource requirements

1. Install Apache Beam for Python 

We shall use Google cloud shell or local machine to perform this task. In additions, we need to install Beam with GCP option

```bash
mkdir dataflow && cd dataflow

pip install --upgrade apache-beam[gcp]
```
2. Enable API for Dataflow, BigQuery, Pubsub, VPC Network

Visit https://console.cloud.google.com/apis.
Go to the "API & Services" > "Library" \n
Search for the Service API: \n
    For Dataflow: Search for "Dataflow API" and click on it. \n
    For BigQuery: Search for "BigQuery API" and click on it. \n
    For VPC Network: Search for "Compute Engine API" (as VPC network services are part of Compute Engine) and click on it. \n
    For PubSub: Search for "PubSub API" and click on it. \n

Or using gcloud command

For Dataflow, Bigquery, VPC Network, PubSub:

```bash
gcloud config set project [PROJECT_ID]          #Replace with your project_id

gcloud services enable dataflow.googleapis.com
gcloud services enable bigquery.googleapis.com
gcloud services enable compute.googleapis.com
gcloud services enable pubsub.googleapis.com

gcloud services list --enabled
```
3. Assign resources to run these services.

* Set up the environment variables for the further using

```bash
export SA_NAME="streaming-job-123"                  #Replace it if needed
export PROJECT_ID="sa-128-ak"                       #Replace with you project_id
export FILE_NAME="streaming_service_account"        #Replace it if needed
export VPC_NETWORK="dataflow-vpc"                   #Replace it if needed
export SUBNET="dataflow-vpc-subnet"                 #Replace it if needed
export REGION="us-central1"                         #Replace it if needed
export TOPIC_NAME="created-users"                   #Replace it by your value
export SUBSCRIPTION_NAME="created-users-sub"        #Replace it if needed
```    
* Create a service account (if not already existing)

```bash
gcloud iam service-accounts create $SA_NAME
```
* Assign roles to the service account 

```bash
#For Datataflow role
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/dataflow.worker"

#For Bigquery role
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/bigquery.dataEditor"

#For VPC Network 
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/compute.networkAdmin"

#For Pub/Sub

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/pubsub.editor"

```

* Create service account key file

```bash
gcloud iam service-accounts keys create $FILE_NAME.json --iam-account $SA_NAME@$PROJECT_ID.iam.gserviceaccount.com
```

* Create a VPC Network and Subnet to run Dataflow

```bash
# Create VPC
gcloud compute networks create $VPC_NETWORK --subnet-mode=custom

# Create Subnet
gcloud compute networks subnets create $SUBNET \
  --network=$VPC_NETWORK \
  --region=$REGION \
  --range=10.0.0.0/20
```

* Create a topic and subscription for Pub/Sub in order to process message

```bash
#Create a topic
gcloud pubsub topics create $TOPIC_NAME
gcloud pubsub subscriptions create $SUBSCRIPTION_NAME --topic=$TOPIC_NAME

```