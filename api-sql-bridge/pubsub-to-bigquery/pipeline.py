from apache_beam.options.pipeline_options import PipelineOptions
from sys import argv
import google.auth
import apache_beam as beam
import argparse
import os
from apache_beam.io import ReadFromPubSub, WriteToText
import json
from datetime import datetime


os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = "streaming_service_account.json" #This a service account key file that is downloaded in the previous step
PROJECT_ID = 'sa-128-ak'
TOPIC = 'created-users'
SUBSCRIPTION = 'projects/' + PROJECT_ID + '/subscriptions/created-users-sub'
SCHEMA = 'created_at:TIMESTAMP,tweep_id:STRING,text:STRING,user:STRING,flagged:BOOLEAN'


def parse_pubsub(data):
    import json
    return json.loads(data)


def fix_timestamp(data):
    d = datetime.strptime(data['created_at'], "%d/%b/%Y:%H:%M:%S")
    data['created_at'] = d.strftime("%Y-%m-%d %H:%M:%S")
    return data


def check_tweep(data):
    BAD_WORDS = ['attack', 'drug', 'gun']
    data['flagged'] = False
    for word in BAD_WORDS:
        if word in data['text']:
            data['flagged'] = True
    return data

class CustomOptions(PipelineOptions):
    @classmethod
    def _add_argparse_args(cls, parser):
        parser.add_value_provider_argument('--input_subscription', type=str, help='Input PubSub subscription')
        parser.add_value_provider_argument('--output_path', type=str, help='Output GCS path prefix')

class FormatAsCsvFn(beam.DoFn):
    def process(self, element):
        # Parse the message to dictionary
        message = json.loads(element.decode('utf-8'))
        # Convert the dictionary to a CSV string
        csv_str = f'{message["created_at"]},{message["tweep_id"]},"{message["text"]}",{message["user"]}'
        yield csv_str

def run(argv=None):
    # Command line arguments
    parser = argparse.ArgumentParser(description='Load from Json into BigQuery')
    parser.add_argument('--project',required=False, help='Specify Google Cloud project', default='sa-128-ak') # Replace the default value (project_id) if needed
    parser.add_argument('--region', required=False, help='Specify Google Cloud region', default='us-central1') # Replace the default value
    parser.add_argument('--staging_location', required=False, help='Specify Cloud Storage bucket for staging', default='gs://dataflow/staging')
    parser.add_argument('--temp_location', required=False, help='Specify Cloud Storage bucket for temp', default='gs://dataflow/temp')
    parser.add_argument('--runner', required=False, help='Specify Apache Beam Runner', default='DataflowRunner')
    parser.add_argument('--table_name', required=False, help='BigQuery table name', default='sa-128-ak:SCD.streaming-job-demo')


    known_args, pipeline_args = parser.parse_known_args(argv)

    p = beam.Pipeline(options=PipelineOptions(streaming=True))

    (p | 'ReadData' >> beam.io.ReadFromPubSub(subscription=SUBSCRIPTION).with_output_types(bytes)

       | 'Decode' >> beam.Map(lambda x: x.decode('utf-8'))
       | 'PubSubToJSON' >> beam.Map(parse_pubsub)
       | 'FixTimestamp' >> beam.Map(fix_timestamp)
       | 'CheckTweep' >> beam.Map(check_tweep)
       | 'WriteToBigQuery' >> beam.io.WriteToBigQuery(
           known_args.table_name,
           schema=SCHEMA,
           write_disposition=beam.io.BigQueryDisposition.WRITE_APPEND)
    )

    p.run().wait_until_finish()

if __name__ == '__main__':
    run()
