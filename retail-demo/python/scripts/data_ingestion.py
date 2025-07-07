import json
import os
from azure.cosmos import CosmosClient, exceptions
from azure.identity import DefaultAzureCredential
from configs.config import COSMOS_DB_URI, DATABASE_NAME, CONTAINER_NAME
from core.logging_config import get_logger

logger = get_logger()

# TODO: Change to async client for better performance and consistency with other modules
# Current implementation uses synchronous CosmosClient which blocks during I/O operations
# Consider using azure.cosmos.aio.CosmosClient with asyncio for concurrent ingestion

INPUT_FILE = os.path.join(os.path.dirname(__file__), "data", "products.json")
BATCH_SIZE = 100


def get_partition_key(doc):
    # For /tenant/id, the partition key value is [doc['tenant'], doc['id']]
    return [doc["tenant"], doc["id"]]


def ingest_to_cosmos():
    # Load products
    with open(INPUT_FILE, "r", encoding="utf-8") as f:
        products = json.load(f)

    # Build a mapping: tenant -> sku (first encountered)
    tenant_sku = {}
    for doc in products:
        tenant = doc["tenant"]
        sku = doc["sku"]
        if tenant not in tenant_sku:
            tenant_sku[tenant] = sku

    # Filter products: only keep docs where sku matches the tenant's assigned sku
    filtered_products = [
        doc for doc in products if doc["sku"] == tenant_sku[doc["tenant"]]
    ]

    # Connect to Cosmos DB using managed identity
    credential = DefaultAzureCredential()
    client = CosmosClient(COSMOS_DB_URI, credential)
    db = client.get_database_client(DATABASE_NAME)
    container = db.get_container_client(CONTAINER_NAME)

    # Ingest in batches
    success, fail = 0, 0
    for i in range(0, len(filtered_products), BATCH_SIZE):
        batch = filtered_products[i : i + BATCH_SIZE]
        for doc in batch:
            try:
                container.upsert_item(doc)
                success += 1
            except exceptions.CosmosHttpResponseError as e:
                logger.error(f"Failed to ingest doc id={doc.get('id')}: {e}")
                fail += 1
        logger.info(f"Ingested {i+len(batch)} / {len(filtered_products)}")

    logger.info(f"Done. Success: {success}, Failed: {fail}")
