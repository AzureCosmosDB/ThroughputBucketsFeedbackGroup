# Cosmos DB Throughput Buckets - Retail Demo

A practical demonstration of **Azure Cosmos DB Throughput Buckets** in a multi-tenant retail marketplace scenario, showing how to prevent noisy neighbor problems and manage resource allocation.

## What This Demo Shows

This retail demo demonstrates how Azure Cosmos DB Throughput Buckets can:

1. **Prevent noisy neighbor problems** in a multi-tenant retail workload
2. **Isolate background inventory jobs** to avoid RU starvation for customer reads

## Retail Use Case

This demo simulates a **multi-tenant retail marketplace** with:

- **Premium Tenants**: Contoso Marketplace offers premium features like better throughput and availability to Premium sku tenants at added cost.
- **Basic Tenants**: Contoso Marketplace offers standard marketplace platform to basic sku tenants.

- **Background Inventory Jobs**: Different tenants run bulk inventory updates which affect end consumer's product experience due to high throughput usage.

- **Product Catalog**: Shared container with hierarchical partition key `/tenant/id`

## Current Project Structure

```text/plain
retail-demo/python/
├── main.py                           # Main entry point with user prompts
├── configs/
│   └── config.py                     # Cosmos DB connection and simulation settings
├── core/
│   └── client_factory.py             # Cosmos DB client with throughput bucket support
├── models/
│   ├── product.py                    # Product data model and generation
│   └── tenant.py                     # Tenant SKU mapping (basic/premium)
├── scenarios/
│   ├── simulate_reads.py             # Multi-tenant read simulation
│   └── simulate_inventory_job.py     # Background inventory job simulation
├── scripts/
│   └── setup.py                      # Container setup with hierarchical partition key
├── data/
│   └── products.json                 # Sample retail product data
└── requirements.txt                  # Python dependencies
```

## Quick Start

### Prerequisites

- Python 3.9+
- Azure Cosmos DB account with NoSQL API
- `pip install azure-cosmos`

### Setup

1. **Configure your Cosmos DB connection** in `configs/config.py`:

```python
   COSMOS_DB_URI = "https://your-cosmos-account.documents.azure.com:443/"
   COSMOS_DB_KEY = "your-primary-key"
   ```

2.[Configure Cosmos DB container with Throughput buckets](../../README.md#how-to-create-throughput-buckets)

3.**Run the demo**:

   ```bash
   python main.py
   ```

4.**Follow the prompts**:

- Choose simulation scenario (1 or 2)
- Enable/disable throughput buckets (0 or 1)
- Setup container if needed (0 or 1)

## Simulation Scenarios

### Scenario 1: Multi-Tenant Retail Workload

**Simulates**: Concurrent product queries from multiple marketplace tenants

**What happens**:

- Premium tenants query products without restrictions
- Basic tenants use throughput bucket with configured max limit
- Measures throttling impact on different tenant tiers
- Shows how buckets prevent one tenant from monopolizing resources

**Expected outcome**:

- Both basic and premium sku tenants experience throttling without throughput buckets.
- Premium tenants observe better performance characteristics when throughput buckets are enabled.

### Scenario 2: Background Inventory Job Isolation

**Simulates**: Inventory updates running alongside customer queries

**What happens**:

- Background job inserts or updates products (inventory update)
- Customer read operations continue simultaneously
- Inventory job uses throughput bucket 1 (10% limit)
- Measures impact on customer query performance

**Expected outcome**:

- Both customer queries and inventory job face throttling due to resource contention when throughput buckets are disabled.
- Customer queries maintain better performance alongside bulk updates when throughput buckets are enabled.
- Inventory job is throttled to prevent resource contention.
- Clear separation between background and customer operations.

## ⚙️ Configuration

### Throughput Bucket Settings

```python
# configs/config.py
CONTAINER_THROUGHPUT = 400                    # Total RU/s for container
BASIC_TENANTS_THROUGHPUT_BUCKET = 2           # Bucket for basic tenants (50% limit)
INVENTORY_JOB_THROUGHPUT_BUCKET = 1           # Bucket for inventory jobs (10% limit)
```

### Simulation Parameters

```python
NUM_QUERIES = 60                              # Queries per tenant/product type
NUM_QUERIES_INVENTORY_JOB = 10                # Queries per tenant during inventory scenario
INVENTORY_JOB_DOCS_TO_INSERT = 1000           # Products to insert in inventory job
INVENTORY_JOB_CONCURRENCY = 30                # Concurrent insert operations
```

## 📊 Understanding Results

### Key Metrics

- **Successful Requests**: Queries completed without throttling
- **Throttled Requests**: Queries that hit 429 (Too Many Requests)
- **Throttling Percentage**: % of requests throttled per tenant type

### Sample Output

```text/plain
[Basic] Successful requests: 750, Throttled: 50, Throttling percentage: (6.25%)
[Premium] Successful requests: 300, Throttled: 0, Throttling percentage: (0.0%)
```

### What This Means

- **Without buckets**: All tenants compete equally, unpredictable performance
- **With buckets**: Basic tenants limited, premium tenants protected, improved performance characteristic for priority requests.

### Modifying Simulation Load

- Increase `NUM_QUERIES` for higher load testing
- Adjust `INVENTORY_JOB_DOCS_TO_INSERT` for different batch sizes
- Modify `INVENTORY_JOB_CONCURRENCY` for different job intensities

## 🛠️ Troubleshooting

### Common Issues

- **Connection errors**: Verify Cosmos DB URI and key in `configs/config.py`
- **No throttling observed**: Increase `NUM_QUERIES` or reduce container throughput
- **Performance issues**: Monitor Cosmos DB metrics in Azure Portal

### Performance Tuning

- Adjust bucket percentages based on your tenant mix
- Consider increasing container throughput for higher load
- Monitor RU consumption patterns in Azure Portal

## Learn More

- [Azure Cosmos DB Throughput Buckets](https://learn.microsoft.com/azure/cosmos-db/nosql/throughput-buckets)
