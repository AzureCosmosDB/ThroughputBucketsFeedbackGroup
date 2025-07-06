import asyncio
import time
import itertools
from azure.cosmos.exceptions import CosmosHttpResponseError
from configs.config import DATABASE_NAME, CONTAINER_NAME
from models.product import get_all_product_types
from models.tenant_sku_mapping import get_basic_sku_tenants, get_premium_sku_tenants
from core.client_factory import create_cosmos_client
from core.logging_config import get_logger

logger = get_logger()


async def execute_query(container, tenant, is_premium, throughput_bucket, product_type):
    success = 0
    throttled = 0
    try:
        items = container.query_items(
            query="SELECT * FROM c WHERE c.tenant = @tenant AND c.Type = @type",
            parameters=[
                {"name": "@tenant", "value": tenant},
                {"name": "@type", "value": product_type},
            ],
            max_item_count=10,
            throughput_bucket=throughput_bucket,
        )
        await items.__anext__()
        success += 1
    except StopAsyncIteration:
        pass
    except CosmosHttpResponseError as e:
        if e.status_code == 429:
            throttled += 1
            logger.debug(
                f"[Read Simulation] Throttled (429): {'Premium' if is_premium else 'Basic'} tenant {tenant}, product type {product_type}"
            )
        else:
            logger.error(
                f"HTTP {e.status_code} Error for tenant={tenant}, type={product_type}: {str(e)}"
            )
    except Exception as e:
        logger.error(f"Error Tenant={tenant}: {str(e)}")
    return {
        "tenant": tenant,
        "is_premium": is_premium,
        "throttled": throttled,
        "success": success,
    }


async def simulate_product_searches(throughput_bucket=None, num_queries=50):
    async with create_cosmos_client() as client:
        db = client.get_database_client(DATABASE_NAME)
        container = db.get_container_client(CONTAINER_NAME)

        basic_tenants = get_basic_sku_tenants()
        premium_tenants = get_premium_sku_tenants()
        product_types = get_all_product_types()
        
        bucket_info = f" using throughput bucket {throughput_bucket}" if throughput_bucket else " without throughput buckets"
        logger.info(f"[Read Simulation] Starting multi-tenant query simulation{bucket_info}")
        logger.info(f"[Read Simulation] Configuration - Basic tenants: {len(basic_tenants)}, Premium tenants: {len(premium_tenants)}")
        logger.info(f"[Read Simulation] Product types: {len(product_types)}, Queries per tenant-type: {num_queries}")
        
        start = time.time()

        # Add semaphore to limit concurrent requests
        # semaphore = asyncio.Semaphore(150)  # Adjust this number as needed

        # async def throttled_single_query(*args, **kwargs):
        #     async with semaphore:
        #         return await single_query(*args, **kwargs)

        # Flattened task creation using itertools.product
        basic_combos = itertools.product(
            basic_tenants, product_types, range(num_queries)
        )
        premium_combos = itertools.product(
            premium_tenants, product_types, range(num_queries)
        )
        tasks = [
            execute_query(container, tenant, True, None, type)
            for (tenant, type, i) in premium_combos
        ] + [
            execute_query(container, tenant, False, throughput_bucket, type)
            for (tenant, type, i) in basic_combos
        ]
        
        total_tasks = len(tasks)
        basic_task_count = len(basic_tenants) * len(product_types) * num_queries
        premium_task_count = len(premium_tenants) * len(product_types) * num_queries
        
        logger.info(f"[Read Simulation] Executing {total_tasks} concurrent queries (Basic: {basic_task_count}, Premium: {premium_task_count})")
        
        all_stats = await asyncio.gather(*tasks)
        
        execution_time = time.time() - start
        queries_per_second = total_tasks / execution_time if execution_time > 0 else 0
        
        logger.info(f"[Read Simulation] Completed all queries in {execution_time:.2f} seconds ({queries_per_second:.2f} queries/sec)")
        log_stats(all_stats, basic_tenants, premium_tenants)


def log_stats(all_stats, basic_tenants, premium_tenants):
    # Separate stats for basic and premium
    basic_stats = [s for s in all_stats if s["tenant"] in basic_tenants]
    premium_stats = [s for s in all_stats if s["tenant"] in premium_tenants]

    # Calculate basic tenant statistics
    total_basic_success = sum(s["success"] for s in basic_stats)
    total_basic_throttled = sum(s["throttled"] for s in basic_stats)
    total_basic_operations = total_basic_success + total_basic_throttled
    basic_throttled_percentage = (
        total_basic_throttled * 1.0 / total_basic_operations * 100 if total_basic_operations > 0 else 0
    )

    # Calculate premium tenant statistics
    total_premium_success = sum(s["success"] for s in premium_stats)
    total_premium_throttled = sum(s["throttled"] for s in premium_stats)
    total_premium_operations = total_premium_success + total_premium_throttled
    premium_throttled_percentage = (
        total_premium_throttled * 1.0 / total_premium_operations * 100 if total_premium_operations > 0 else 0
    )

    # Log comprehensive performance summary
    logger.info(f"  [Basic Tenant Details]:")
    logger.info(f"    - Total operations: {total_basic_operations}")
    logger.info(f"    - Successful queries: {total_basic_success}")
    logger.info(f"    - Throttled queries: {total_basic_throttled}")
    logger.info(f"    - Throttling rate: {basic_throttled_percentage:.2f}%")
    logger.info(f"")
    logger.info(f"  [Premium Tenant Details]:")
    logger.info(f"    - Total operations: {total_premium_operations}")
    logger.info(f"    - Successful queries: {total_premium_success}")
    logger.info(f"    - Throttled queries: {total_premium_throttled}")
    logger.info(f"    - Throttling rate: {premium_throttled_percentage:.2f}%")
