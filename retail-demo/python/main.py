import asyncio
from scripts.setup import setup_container
from scenarios.simulate_searches import simulate_product_searches
from scenarios.simulate_inventory_job import execute_bulk_inventory_update
from configs.config import *
from core.logging_config import get_logger

logger = get_logger()


async def main():

    logger.info("=== Cosmos DB Throughput Buckets Simulation ===")

    try:
        scenario = int(
            input(
                "Select simulation scenario (1 or 2):\n"
                "1: Multi-tenant product search workload\n"
                "2: Concurrent inventory updates with customer searches\n"
            )
        )
        if scenario not in [1, 2]:
            logger.error("Invalid scenario. Must be 1 or 2.")
            return
    except ValueError:
        logger.error("Invalid input. Please enter a number (1 or 2).")
        return

    try:
        use_throughput_buckets = int(input("Use throughput buckets? (0=No, 1=Yes)\n"))
        if use_throughput_buckets not in [0, 1]:
            logger.error("Invalid choice. Must be 0 or 1.")
            return
        use_throughput_buckets = bool(use_throughput_buckets)
    except ValueError:
        logger.error("Invalid input. Please enter a number (0 or 1).")
        return

    try:
        do_setup = int(input("Setup container? (0=No, 1=Yes)\n"))
        if do_setup not in [0, 1]:
            logger.error("Invalid choice. Must be 0 or 1.")
            return
        do_setup = bool(do_setup)
    except ValueError:
        logger.error("Invalid input. Please enter a number (0 or 1).")
        return

    if do_setup:
        logger.info("Setting up Cosmos DB container...")
        setup_container()
    else:
        logger.info("Skipping container setup")

    await run_simulation(use_throughput_buckets, scenario)


async def run_simulation(use_throughput_buckets, scenario):
    # Run simulation based on scenario
    if scenario == 1:
        logger.info("--- Running Scenario 1: Multi-tenant workload ---")
        logger.info(f"Throughput buckets enabled: {use_throughput_buckets}")

        num_queries = NUM_QUERIES
        throughput_bucket = (
            BASIC_TENANTS_THROUGHPUT_BUCKET if use_throughput_buckets else None
        )
        await simulate_product_searches(throughput_bucket, num_queries)

    elif scenario == 2:
        logger.info("--- Running Scenario 2: Background job for inventory update ---")
        logger.info(f"Throughput buckets enabled: {use_throughput_buckets}")

        num_queries = NUM_QUERIES_INVENTORY_JOB
        throughput_bucket = (
            INVENTORY_JOB_THROUGHPUT_BUCKET if use_throughput_buckets else None
        )
        await asyncio.gather(
            execute_bulk_inventory_update(
                throughput_bucket,
                docs_to_insert=INVENTORY_JOB_DOCS_TO_INSERT,
                max_concurrency=INVENTORY_JOB_CONCURRENCY,
            ),
            simulate_product_searches(num_queries=num_queries),
        )


if __name__ == "__main__":
    asyncio.run(main())
