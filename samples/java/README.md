# Throughput buckets with Java bulk execution

Two independently runnable examples, following the bucket assignment patterns
in the [Python client factory](../../retail-demo/python/core/client_factory.py)
and [.NET quickstarts](../dotnet/Quickstarts):

| Scenario | Open this example | Bucket assignment |
| --- | --- | --- |
| Client-level bucket | [ClientLevelBulkQuickstart.java](src/main/java/com/azure/cosmos/samples/ClientLevelBulkQuickstart.java) | Default group inherited by bulk execution |
| Request-level bucket | [RequestLevelBulkQuickstart.java](src/main/java/com/azure/cosmos/samples/RequestLevelBulkQuickstart.java) | Group selected in `CosmosBulkExecutionOptions` |

Java uses a default **server throughput control group on this client's container
handle**, not a bucket option on `CosmosClientBuilder`. The request-level scenario
selects a group for the entire bulk call, with no client default. Neither scenario uses per-item
request options or a global throughput control container.

## Run either scenario

Use JDK 17+, Maven 3.9+, and an existing Cosmos DB for NoSQL container with
dedicated provisioned throughput, an enabled bucket **1**, and hierarchical
partition keys **`/tenant`, `/id`**. For a container partitioned only by `/id`,
change the partition-key line in `createOperations` to `new PartitionKey(id)`.
See the [throughput bucket documentation](https://learn.microsoft.com/azure/cosmos-db/throughput-buckets).

Authenticate with `az login`. Your identity needs **Cosmos DB Built-in Data
Contributor** data-plane access. From this directory:

```powershell
$env:COSMOS_ENDPOINT = "https://<account>.documents.azure.com:443/"
$env:COSMOS_DATABASE = "<database>"
$env:COSMOS_CONTAINER = "<container>"
$env:COSMOS_THROUGHPUT_BUCKET = "1"

# Client-level bucket
mvn compile exec:java

# Request-level bucket
mvn compile exec:java "-Dexec.mainClass=com.azure.cosmos.samples.RequestLevelBulkQuickstart"
```

Each example creates **10 products** using the real SDK bulk API and
prints per-item status/RU charges. IDs are unique. Products are left in the
container so you can inspect them. Bucket IDs are identifiers, not RU/s budgets.
Change `COSMOS_THROUGHPUT_BUCKET` to another enabled ID if needed.

## E2E verification

Assertions, readback, and cleanup live only in the test file, not the sample:

```powershell
$env:COSMOS_RUN_E2E = "true"
mvn clean test
```

The two live tests run the same example methods using separate clients, verify every created item, and
delete only their test products. Without `COSMOS_RUN_E2E=true`, they are skipped.
These are functional tests, not throughput-cap benchmarks.
