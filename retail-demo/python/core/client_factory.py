from azure.cosmos.aio import CosmosClient
from configs.config import COSMOS_DB_URI, COSMOS_DB_KEY


def create_cosmos_client():
    return CosmosClient(
        COSMOS_DB_URI,
        COSMOS_DB_KEY,
        retry_total=1,  # maximum number of retries - for demo purposes, not recommended for production
    )


def create_cosmos_client_with_bucket(throughputBucket=None):
    return CosmosClient(
        COSMOS_DB_URI,
        COSMOS_DB_KEY,
        retry_total=1,  # maximum number of retries - for demo purposes, not recommended for production
        throughput_bucket=throughputBucket,
    )
