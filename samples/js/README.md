# Throughput buckets with JavaScript bulk execution

Two independently runnable examples, following the bucket assignment patterns
in the [Python client factory](../../retail-demo/python/core/client_factory.py)
and [.NET quickstarts](../dotnet/Quickstarts):

| Scenario | Open this example | Bucket assignment |
| --- | --- | --- |
| Client-level bucket | [client-level-bulk.js](client-level-bulk.js) | `CosmosClient.throughputBucket` |
| Request-level bucket | [request-level-bulk.js](request-level-bulk.js) | `executeBulkOperations(operations, { throughputBucket: bucket })` |

The request-level example uses a client without a bucket and assigns one to the
**entire bulk call**, not individual item operations. Both scenarios use the SDK's
`executeBulkOperations`, not a loop of point creates or `Promise.all`.

## Run either scenario

Use Node.js 20+ and an existing Cosmos DB for NoSQL container with dedicated
provisioned throughput and an enabled bucket **1**. The sample products have
`id`, `tenant`, and `name` fields, with explicit hierarchical partition keys
**`/tenant`, `/id`**. For a container partitioned only by `/id`, change the
`partitionKey` line in `createOperations` to `id`.
See the [throughput bucket documentation](https://learn.microsoft.com/azure/cosmos-db/throughput-buckets).

Authenticate with `az login`. Your identity needs **Cosmos DB Built-in Data
Contributor** data-plane access. From this directory:

```powershell
npm ci
$env:COSMOS_ENDPOINT = "https://<account>.documents.azure.com:443/"
$env:COSMOS_DATABASE = "<database>"
$env:COSMOS_CONTAINER = "<container>"
$env:COSMOS_THROUGHPUT_BUCKET = "1"

# Client-level bucket
npm run start:client

# Request-level bucket
npm run start:request
```

Each example creates **10 products** and prints per-item status/RU
charges. IDs are unique. Products are left in the container so you can inspect
them. Bucket IDs are identifiers, not RU/s budgets. Change the bucket environment
variable to another enabled ID if needed.

## Tests

Assertions, readback, and cleanup live only under `tests/`, not in the sample:

```powershell
npm test
npm run test:e2e
```

Unit tests check bulk bucket routing without Azure access. E2E tests use the
configured live container, verify every created item, and delete only their test
products. These are functional tests, not throughput-cap benchmarks.
