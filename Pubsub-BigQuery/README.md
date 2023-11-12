## How to handle streaming job from Google PubSub to BigQuery via Apache Beam (Python)
##### Table of Contents  
* [Step 1 - Resource requirements](#step-1-resource-requirements)
* [Step 2 — Create a demo streaming job](#step-2-create-a-demo-streaming-job)
* [Step 3 — Using Apache Beam to read the messages](#step-3-using-apache-beam-to-read-the-messages)
* [Step 4 - Checking the result](#step-4-checking-the-result)

### Step 1 - Resource requirements

1. Install Apache Beam for Python 

We shall use Google cloud shell or local machine to perform this task. In additions, we need to install Beam with GCP option

```bash
mkdir dataflow && cd dataflow

pip install --upgrade apache-beam[gcp]
```
2. Enable API for Dataflow, BigQuery, Pubsub, VPC Network

Visit https://console.cloud.google.com/apis.
Go to the "API & Services" > "Library".
Search for the Service API:

    For Dataflow: Search for "Dataflow API" and click on it.
    For BigQuery: Search for "BigQuery API" and click on it.
    For VPC Network: Search for "Compute Engine API" (as VPC network services are part of Compute Engine) and click on it.
    For PubSub: Search for "PubSub API" and click on it.
    For Google Storage: Search for "Cloud Storage API" and click on it.

Or using gcloud command

For Dataflow, Bigquery, VPC Network, PubSu, Cloud Storage:

```bash
gcloud config set project [PROJECT_ID]          #Replace with your project_id

gcloud services enable dataflow.googleapis.com
gcloud services enable bigquery.googleapis.com
gcloud services enable compute.googleapis.com
gcloud services enable pubsub.googleapis.com
gcloud services enable storage.googleapis.com

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

#Fore Cloud Storage
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/storage.objectAdmin"

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

#Check to verify topic created successfully

gcloud pubsub topics list

```

* Create a Google Bucket for storing Dataflow logging

```bash
export BUCKET_NAME="dataflow"
export LOCATION="us-central1"
gcloud storage buckets create $BUCKET_NAME --location=$LOCATION

#Create 2 sub folders for further demos
echo "" | gsutil cp - gs://$BUCKET_NAME/staging/
echo "" | gsutil cp - gs://$BUCKET_NAME/temp/

```
* Create a BigQuery table for writing the data
```bash
#Create a new dataset
bq mk -d --location=$LOCATION $PROJECT_ID:SCD

#Create a new table with provided schema for further writting data
bq mk -t --schema 'created_at:TIMESTAMP,tweep_id:STRING,text:STRING,user:STRING,flagged:BOOLEAN' $PROJECT_ID:SCD.streaming-job-demo

```

### Step 2 - Create a demo streaming job
In order to run this experiment, I shall use a fake stream:

* Install extral libaries
```bash
pip install --upgrade faker
pip install google-cloud
```

Everything now is ready. I use `publisher.py` to generate some faked message and use Google Pub/Sub to publish an subscribe the messages.
Audiences can see the file in the repository.

publish: A function to publish a message to a specified Pub/Sub topic.
generate_tweep: This function generates a simulated message (tweep) with a timestamp, a unique ID, a random sentence, and a randomly selected user.

```python
def publish(publisher, topic, message):
    data = message.encode('utf-8')
    return publisher.publish(topic_path, data=data)


def generate_tweep():
    data = {}
    data['created_at'] = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    data['tweep_id'] = faker.uuid4()
    data['text'] = faker.sentence()
    data['user'] = random.choice(usernames)
    return json.dumps(data)

```

After the messages are published. You can verify it here: https://console.cloud.google.com/cloudpubsub/topic/.
Otherwise, we might use `subscription.py` to view the messages.

### Step 3 - Using Apache Beam to read the messages

We shall use the `pipeline.py` to perform the streaming pipeline.

This is a pipeline using Apache Beam to create a data processing pipeline for streaming data. 
There are some step in the pipeline. Below is pipeline definition:
    - The pipeline is instantiated with streaming mode enabled.
    - Data is read from a `Google Cloud Pub/Sub` subscription.
    - The data (in bytes) is decoded into a string format.
    - The string is parsed into `JSON` format using the parse_pubsub function.
    - The fix_timestamp function adjusts the created_at field in each message to a specific format.
    - The check_tweep function adds a flagged boolean field to each message, marking it as true if it contains any predefined "bad words".
    - The processed data is then written to a `BigQuery` table with a specified schema/table.


