from google.cloud import pubsub_v1
import os
import csv
from google.cloud import storage

# Set your project ID
project_id = 'sa-128-ak'

# Set your subscription name
subscription_name = 'created-users-sub'

# Create a subscriber object
subscriber = pubsub_v1.SubscriberClient()

def callback(message: pubsub_v1.subscriber.message.Message) -> None:
    # Extract the data from the message
    data = message.data

    # Convert data to a string if it's not already
    if isinstance(data, bytes):
        data = data.decode('utf-8')

    # Print the data
    print(f"Received message: {data}")

    # Acknowledge the message to mark it as processed
    message.ack()

subscription_path = subscriber.subscription_path(project_id, subscription_name)

# Subscribe to the subscription and pass the callback function
streaming_pull_future = subscriber.subscribe(subscription_path, callback=callback)

# Wrap the subscriber in a 'with' block to automatically call close() when done
with subscriber:
    # Wait for the subscription to stop
    streaming_pull_future.result()


