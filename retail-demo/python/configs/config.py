import os
from dotenv import load_dotenv

load_dotenv()

COSMOS_DB_URI = "https://cosmos-retail-db.documents.azure.com:443/"
COSMOS_DB_KEY = os.getenv("COSMOS_DB_KEY", "your_cosmos_db_key_here")
DATABASE_NAME = "ContosoMarketplace"
CONTAINER_NAME = "ProductCatalog"
# Hierarchical partition key
PARTITION_KEY_PATH = "/tenant/id"
# Provisioned throughput for the container (adjust for demo)
CONTAINER_THROUGHPUT = 400
INVENTORY_JOB_THROUGHPUT_BUCKET = 1
BASIC_TENANTS_THROUGHPUT_BUCKET = 2
NUM_QUERIES = 100
NUM_QUERIES_INVENTORY_JOB = 10
INVENTORY_JOB_DOCS_TO_INSERT = 1000
INVENTORY_JOB_CONCURRENCY = 30
