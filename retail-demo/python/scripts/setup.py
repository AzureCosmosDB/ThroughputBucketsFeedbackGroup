from azure.cosmos import exceptions, PartitionKey
from configs.config import (
    DATABASE_NAME,
    CONTAINER_NAME,
    PARTITION_KEY_PATH,
    CONTAINER_THROUGHPUT,
)
from core.client_factory import create_cosmos_client
from core.logging_config import get_logger
from scripts.data_ingestion import ingest_to_cosmos

logger = get_logger()


def setup_container():
    client = create_cosmos_client()
    try:
        db = client.get_database_client(DATABASE_NAME)
        db.create_container_if_not_exists(
            id=CONTAINER_NAME,
            partition_key=PartitionKey(path=PARTITION_KEY_PATH),
            offer_throughput=CONTAINER_THROUGHPUT,
        )
        ingest_to_cosmos()
        logger.info("Container setup complete.")
    except exceptions.CosmosResourceExistsError:
        logger.info("Container already exists.")
