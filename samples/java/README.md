# Azure Cosmos DB throughput bucket quickstart (Java)

This quickstart demonstrates request-level and default-container throughput
bucket assignment using the Azure Cosmos DB Java SDK. It creates one uniquely
named product, reads and queries it, replaces it, verifies the replacement,
then deletes it and verifies deletion. It never upserts existing products or
changes container settings.

## Prerequisites

- JDK 17 or later and Maven 3.9 or later.
- An existing Azure Cosmos DB for NoSQL container with dedicated provisioned
  throughput and an enabled throughput bucket. Shared database throughput and
  serverless accounts are not supported.
- An Azure identity with the **Cosmos DB Built-in Data Contributor** role scoped
  to the test container or account. An Azure management-plane role alone does not
  grant data-plane access.
- Permission to create and delete isolated test items.

Enable the bucket in **Data Explorer > Scale & Settings > Throughput Buckets**.
See the [official throughput bucket documentation](https://learn.microsoft.com/azure/cosmos-db/throughput-buckets).
The sample uses SDK `4.83.0` and `DefaultAzureCredential`.

## Configure and run

For local development, authenticate with `az login`. Do not place account keys
or credentials in source files.

From this directory, run the following in PowerShell:

```powershell
$env:COSMOS_ENDPOINT = "https://<account>.documents.azure.com:443/"
$env:COSMOS_DATABASE = "<database>"
$env:COSMOS_CONTAINER = "<container>"
$env:COSMOS_THROUGHPUT_BUCKET = "1"
$env:COSMOS_BUCKET_SCOPE = "request"
mvn test
mvn compile exec:java
```

| Variable | Meaning | Default |
| --- | --- | --- |
| `COSMOS_ENDPOINT` | Account data-plane endpoint, not its portal URL | Required |
| `COSMOS_DATABASE` | Existing database ID | Required |
| `COSMOS_CONTAINER` | Existing container ID | Required |
| `COSMOS_THROUGHPUT_BUCKET` | Enabled bucket ID, from 1 to 5, not an RU/s value | `1` |
| `COSMOS_BUCKET_SCOPE` | `request` or `client` | `request` |

The sample reads the container's actual partition key paths and builds an
isolated product matching them. It supports simple, nested, and hierarchical
partition keys without assuming `/id`. One async client is reused for the run
and closed afterward.

## Bucket assignment

Java uses a server-side throughput control group, rather than a
`throughputBucket` property on individual item request options:

```java
ThroughputControlGroupConfig group = new ThroughputControlGroupConfigBuilder()
    .groupName("quickstartBucket")
    .throughputBucket(1)
    .defaultControlGroup(false)
    .build();
container.enableServerThroughputControlGroup(group);

CosmosItemRequestOptions options = new CosmosItemRequestOptions()
    .setThroughputControlGroupName("quickstartBucket");
```

In **request** scope, the point read, query, and replacement explicitly reference
the group. Create, baseline read, and cleanup omit it. Those requests still use
the container default bucket if configured.

In **client** scope, the group has `defaultControlGroup(true)`. Operations without
an explicit group on this container handle inherit its bucket, including cleanup.
This is a default for this client's container handle, not a client-builder option
or an account-wide setting. No global throughput control container is needed.

## Tests

```powershell
mvn test
$env:COSMOS_BUCKET_SCOPE = "request"
mvn compile exec:java
$env:COSMOS_BUCKET_SCOPE = "client"
mvn compile exec:java
```

Unit tests require no Azure access. E2E runs use the configured live container
and real SDK. Each successful run prints operation status/RU charges and ends
with `E2E PASS` only after verifying reads, query results, replacement, and
deletion. Failures, including cleanup failures, exit nonzero. The sample prints
its isolated item ID before creation for troubleshooting an ambiguous network failure.

This is a bounded functional quickstart, not a saturation benchmark. A successful
CRUD run does not prove the bucket's throughput cap. To observe enforcement,
use an approved load workload and split the **Total Requests** and **Total Request
Units** metrics by **ThroughputBucket** in the Azure portal.
