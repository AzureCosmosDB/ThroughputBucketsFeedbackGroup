# Azure Cosmos DB throughput bucket quickstart (JavaScript)

This quickstart demonstrates request-level and client-level throughput buckets
using the Azure Cosmos DB JavaScript SDK. It creates one uniquely named product,
reads and queries it, replaces it, verifies the replacement, then deletes it and
verifies deletion. It never upserts existing products or changes container settings.

## Prerequisites

- Node.js 20 or later.
- An existing Azure Cosmos DB for NoSQL container with dedicated provisioned
  throughput and an enabled throughput bucket. Shared database throughput and
  serverless accounts are not supported.
- An Azure identity with the **Cosmos DB Built-in Data Contributor** role scoped
  to the test container or account. An Azure management-plane role alone does not
  grant data-plane access.
- Permission to create and delete isolated test items.

Enable the bucket in **Data Explorer > Scale & Settings > Throughput Buckets**.
See the [official throughput bucket documentation](https://learn.microsoft.com/azure/cosmos-db/throughput-buckets).
The SDK is pinned in `package.json` to a version supporting throughput buckets.

## Configure and run

Authentication uses `DefaultAzureCredential`, as in the existing .NET sample.
For local development, sign in with `az login`. Do not place account keys or
credentials in source files.

From this directory, run the following in PowerShell:

```powershell
npm install
$env:COSMOS_ENDPOINT = "https://<account>.documents.azure.com:443/"
$env:COSMOS_DATABASE = "<database>"
$env:COSMOS_CONTAINER = "<container>"
$env:COSMOS_THROUGHPUT_BUCKET = "1"
$env:COSMOS_BUCKET_SCOPE = "request"
npm start
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
partition keys without assuming `/id`.

In **request** scope, the point read, query, and replacement use
`{ throughputBucket: config.bucket }`. Create, baseline read, and cleanup omit a
request bucket. Those requests still use the container default bucket if configured.

In **client** scope, `new CosmosClient({ ..., throughputBucket: config.bucket })`
assigns the bucket to all requests from that client. Per-request options are
left unset so they inherit that setting, including cleanup.

## Tests

```powershell
npm test
$env:COSMOS_BUCKET_SCOPE = "request"
npm run test:e2e
$env:COSMOS_BUCKET_SCOPE = "client"
npm run test:e2e
```

Unit tests use fakes and require no Azure access. E2E runs use the configured
live container and real SDK. Each successful run prints operation status/RU
charges and ends with `E2E PASS` only after verifying reads, query results,
replacement, and deletion. Failures, including cleanup failures, exit nonzero.
The sample prints its isolated item ID before creation for troubleshooting an
ambiguous network failure.

This is a bounded functional quickstart, not a saturation benchmark. A successful
CRUD run does not prove the bucket's throughput cap. To observe enforcement,
use an approved load workload and split the **Total Requests** and **Total Request
Units** metrics by **ThroughputBucket** in the Azure portal.
