from faker import Faker
from google.cloud import pubsub_v1
import google.auth
import random
import json
from datetime import datetime
import time
import os
import numpy as np
import numpy as np

rng = np.random.default_rng()

os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = "streaming_service_account.json" #This a service account key file that is downloaded in the previous step
PROJECT_ID = 'sa-128-ak'
TOPIC = 'created-users'

usernames = []
faker = Faker()
publisher = pubsub_v1.PublisherClient()
topic_path = publisher.topic_path(PROJECT_ID, TOPIC)


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


if __name__ == '__main__':
    for i in range(200):
        newprofile = faker.simple_profile()
        usernames.append(newprofile['username'])
    print("Hit CTRL-C to stop Tweeping!")
    while True:
        publish(publisher, topic_path, generate_tweep())
        time.sleep(0.5)