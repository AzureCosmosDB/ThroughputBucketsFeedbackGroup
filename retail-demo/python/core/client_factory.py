from azure.cosmos.aio import CosmosClient
from configs.config import COSMOS_DB_URI
from azure.identity import DefaultAzureCredential


def create_cosmos_client():
    return CosmosClient(
        COSMOS_DB_URI,
        credential=DefaultAzureCredential(),
        retry_total=1,  # maximum number of retries - for demo purposes
    )


def create_cosmos_client_with_bucket(throughputBucket=None):
    return CosmosClient(
        COSMOS_DB_URI,
        credential=DefaultAzureCredential(),
        retry_total=1,  # maximum number of retries - for demo purposes
        throughput_bucket=throughputBucket,
    )
