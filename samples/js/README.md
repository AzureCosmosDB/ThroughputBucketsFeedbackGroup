# Throughput buckets with JavaScript

Two standalone scenarios mirror the concepts in the
[SDK request documentation](https://learn.microsoft.com/azure/cosmos-db/throughput-buckets?tabs=javascript#using-throughput-buckets-in-sdk-requests),
[Python retail scenarios](../../retail-demo/python/README.md#simulation-scenarios),
and [.NET quickstarts](../dotnet/Quickstarts):

| Concept | Open the relevant code | What runs concurrently |
| --- | --- | --- |
| Request-level bucketing | [request-level-bucket.js](request-level-bucket.js) | One shared client: standard tenant queries receive `{ throughputBucket: 2 }` request options; premium queries omit a bucket. No bulk execution here. |
| Client-level bucketing | [client-level-bucket.js](client-level-bucket.js) | An inventory client is constructed with `throughputBucket: 1` for bulk writes; a separate client runs customer queries without selecting a bucket. |

The inventory bulk call uses `executeBulkOperations(operations)` with **no
request-level override**. Bucket selection stays visible in the scenario files;
[workload-support.js](workload-support.js) contains only query execution, product
generation, and result counting.

## Run

Use Node.js 20+ and an existing Cosmos DB for NoSQL container with dedicated
provisioned throughput, configured buckets **1** and **2**, and hierarchical
partition keys **`/tenant`, `/id`**. Bucket IDs identify configured limits; these
samples do not configure them.

Authenticate with `az login`. Your identity needs **Cosmos DB Built-in Data
Contributor** data-plane access. Choose two tenants with existing products.
From this directory:

```powershell
npm ci
$env:COSMOS_ENDPOINT = "https://<account>.documents.azure.com:443/"
$env:COSMOS_DATABASE = "<database>"
$env:COSMOS_CONTAINER = "<container>"
$env:COSMOS_STANDARD_TENANT = "tenant_1"
$env:COSMOS_PREMIUM_TENANT = "tenant_7"
$env:USE_THROUGHPUT_BUCKETS = "true"

# Request-level: standard versus premium queries on one client
npm run start:request

# Client-level: bulk inventory writes alongside customer queries
npm run start:client

# Repeat either command without assigning buckets
$env:USE_THROUGHPUT_BUCKETS = "false"
```

Each tenant runs 20 queries by default (`COSMOS_QUERY_COUNT`). The client-level
example also creates 10 uniquely identified products (`COSMOS_BULK_ITEMS`).
Only that example writes data, and it leaves those products for inspection.

Output reports completed queries or successful bulk items and **surfaced 429s**.
Query throttling retries are disabled for this demonstration; SDK bulk processing
can still retry internally. Counts are not all server throttles or a guaranteed
performance comparison. A container default bucket can also apply to requests
that omit a bucket; use a container without that default for an unassigned
baseline.

## Tests

Readback, assertions, and cleanup stay under `tests/`, not in the samples:

```powershell
npm test
npm run test:e2e
```

Unit tests check selective request assignment, an unassigned baseline, concurrent
inventory/customer workloads, and error reporting. Four live scenarios exercise
both enabled and disabled modes, seed isolated tenant products, verify query
results and created inventory, and delete only their fixtures. These are
functional tests, not throughput-cap benchmarks.
