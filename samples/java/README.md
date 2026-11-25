# Throughput buckets with Java

Two standalone scenarios mirror the concepts in the
[SDK request documentation](https://learn.microsoft.com/azure/cosmos-db/throughput-buckets?tabs=java#using-throughput-buckets-in-sdk-requests),
[Python retail scenarios](../../retail-demo/python/README.md#simulation-scenarios),
and [.NET quickstarts](../dotnet/Quickstarts):

| Concept | Open the relevant code | What runs concurrently |
| --- | --- | --- |
| Request-level bucketing | [RequestLevelBucketQuickstart.java](src/main/java/com/azure/cosmos/samples/RequestLevelBucketQuickstart.java) | One shared client: standard tenant queries select bucket **2** through `CosmosQueryRequestOptions`; premium queries omit a group. No bulk execution here. |
| Client-level bucketing | [ClientLevelBucketQuickstart.java](src/main/java/com/azure/cosmos/samples/ClientLevelBucketQuickstart.java) | An inventory client uses a default bucket **1** for bulk writes; a separate client runs customer queries without selecting a bucket. |

Java represents a client default using a default **server throughput control
group on that client's container handle**, not a `CosmosClientBuilder` bucket
property. The request-level group is non-default and selected only in standard
query request options. Bulk writes use `executeBulkOperations` with no request
bucket override. No global throughput-control container is needed.

## Run

Use JDK 17+, Maven 3.9+, and an existing Cosmos DB for NoSQL container with
dedicated provisioned throughput, configured buckets **1** and **2**, and
hierarchical partition keys **`/tenant`, `/id`**. Bucket IDs identify configured
limits; these samples do not configure them.

Authenticate with `az login`. Your identity needs **Cosmos DB Built-in Data
Contributor** data-plane access. Choose two tenants with existing products.
From this directory:

```powershell
$env:COSMOS_ENDPOINT = "https://<account>.documents.azure.com:443/"
$env:COSMOS_DATABASE = "<database>"
$env:COSMOS_CONTAINER = "<container>"
$env:COSMOS_STANDARD_TENANT = "tenant_1"
$env:COSMOS_PREMIUM_TENANT = "tenant_7"
$env:USE_THROUGHPUT_BUCKETS = "true"

# Request-level: standard versus premium queries on one client
mvn compile exec:java

# Client-level: bulk inventory writes alongside customer queries
mvn compile exec:java "-Dexec.mainClass=com.azure.cosmos.samples.ClientLevelBucketQuickstart"

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

## E2E verification

Readback, assertions, and cleanup stay outside the samples:

```powershell
$env:COSMOS_RUN_E2E = "true"
mvn clean test
```

Four live tests exercise both scenarios with buckets enabled and disabled, seed
isolated tenant products, verify query outcomes and created inventory, and delete
only their fixtures. Without `COSMOS_RUN_E2E=true`, they are skipped. These are
functional tests, not throughput-cap benchmarks.
