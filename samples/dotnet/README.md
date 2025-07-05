
# Azure Cosmos DB Throughput buckets quickstart (C#)

This repository provides quickstart samples for using the **Throughput Buckets** feature in Azure Cosmos DB, demonstrating both request-level and bulk API usage. The samples are designed to help you understand and test how throughput buckets can be leveraged for high-throughput, cost-efficient workloads.

## Features

- **Request-level throughput bucket usage** (see `Quickstarts/ThroughputBucketQuickstart.cs`)
- **Bulk API throughput bucket usage** (see `Quickstarts/ThroughputBucketBulkQuickstart.cs`)

## Prerequisites

- [Cosmos DB .NET SDK >= 3.50.0-preview.0](https://www.nuget.org/packages/Microsoft.Azure.Cosmos/3.50.0-preview.0)
- An Azure Cosmos DB account with the required features enabled
- Properly configured [Azure Identity](https://learn.microsoft.com/azure/developer/dotnet/azure-sdk-authentication?tabs=command-line) for authentication (uses `DefaultAzureCredential`)
- [Throughput bucket](../../README.md#How-to-create-Throughput-buckets) created with id *1* for Azure Cosmos DB container.

## Configuration

All connection and workload parameters are set in `App.Config`.
> **Tip:** Adjust these values to match your Cosmos DB setup and desired workload.

## How to run

### 1. Build the project

```sh
# In the project root
 dotnet build
```

### 2. Run a sample

You can run either sample by specifying the class name as the entry point:

#### Bulk API sample

```sh
dotnet run --project bucketing-sample.csproj --no-build --no-launch-profile -- Quickstarts.ThroughputBucketBulkQuickstart
```

#### Request-level Throughput bucket sample

```sh
dotnet run --project bucketing-sample.csproj --no-build --no-launch-profile -- Quickstarts.ThroughputBucketQuickstart
```

> Try running the samples both with and without Throughput buckets enabled to observe the difference in performance.

> If you are using Visual Studio, right-click the desired file in `Quickstarts/` and select "Set as Startup Object" before running.

## Output

The samples will print per-second statistics to the console, such as:

```

Reads Succeeded/sec: 181, Reads Throttled/sec: 0, Queries Succeeded/sec: 30, Queries Throttled/sec: 4

=== OPERATION SUMMARY ===
Reads: 10000 succeeded, 0 throttled
Queries: 1694 succeeded, 306 throttled
=========================

```

## Contributing

Pull requests and issues are welcome! Please open an issue if you have questions or suggestions.

## License

MIT
