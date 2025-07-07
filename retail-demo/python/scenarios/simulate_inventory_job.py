import time
import asyncio
from core.client_factory import create_cosmos_client_with_bucket
from core.logging_config import get_logger
from configs.config import DATABASE_NAME, CONTAINER_NAME
from models.product import Product
from azure.cosmos.exceptions import CosmosHttpResponseError

logger = get_logger()


async def insert_product(container, product, stats, lock):
    try:
        await container.upsert_item(body=product.to_dict())
        async with lock:
            stats["inserted"] += 1
    except CosmosHttpResponseError as e:
        if hasattr(e, "status_code") and e.status_code == 429:
            async with lock:
                stats["throttled"] += 1
                logger.debug(
                    f"[Inventory Job] Throttled (429): Product {product.id} (SKU: {product.sku}, Tenant: {product.tenant})"
                )
        else:
            logger.error(f"[Inventory Job] HTTP Error {e.status_code}: Failed to insert product {product.id} (SKU: {product.sku}, Tenant: {product.tenant}) - {str(e)}")
    except Exception as e:
        logger.error(f"[Inventory Job] Unexpected error inserting product {product.id}: {str(e)}")


async def execute_bulk_inventory_update(throughputBucket=None, docs_to_insert=1000, max_concurrency=30):
    async with create_cosmos_client_with_bucket(throughputBucket) as client:
        container = client.get_database_client(DATABASE_NAME).get_container_client(
            CONTAINER_NAME
        )
        bucket_info = f" using throughput bucket {throughputBucket}" if throughputBucket else " without throughput buckets"
        logger.info(f"[Inventory Job] Starting bulk inventory upload{bucket_info}")
        logger.info(f"[Inventory Job] Configuration - Documents to insert: {docs_to_insert}, Max concurrency: {max_concurrency}")
        start = time.time()
        products = [Product.generate_product() for _ in range(docs_to_insert)]
        semaphore = asyncio.Semaphore(max_concurrency)
        stats = {"inserted": 0, "throttled": 0}
        lock = asyncio.Lock()

        async def sem_insert(product):
            async with semaphore:
                await insert_product(container, product, stats, lock)

        tasks = [sem_insert(product) for product in products]
        await asyncio.gather(*tasks)
        
        execution_time = time.time() - start
        logger.info(f"[Inventory Job] Completed bulk upload in {execution_time:.2f} seconds")
        
        total_operations = stats["inserted"] + stats["throttled"]
        throttling_percentage = (
            stats["throttled"] * 1.0 / total_operations * 100 if total_operations > 0 else 0
        )
        
        # Calculate operations per second
        ops_per_second = total_operations / execution_time if execution_time > 0 else 0
        
        logger.info(f"[Inventory Job] Performance Summary:")
        logger.info(f"  - Total operations: {total_operations}")
        logger.info(f"  - Successful insertions: {stats['inserted']}")
        logger.info(f"  - Throttled requests: {stats['throttled']}")
        logger.info(f"  - Throttling rate: {throttling_percentage:.2f}%")
        logger.info(f"  - Operations per second: {ops_per_second:.2f}")
        logger.info(f"  - Average time per operation: {(execution_time/total_operations)*1000:.2f}ms" if total_operations > 0 else "  - Average time per operation: N/A")
