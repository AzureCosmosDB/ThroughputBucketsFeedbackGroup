# Cosmos DB Throughput Buckets Quickstart

This repository provides quickstart samples for using the **Throughput Buckets** feature in Azure Cosmos DB, demonstrating both request-level and bulk API usage. The samples are designed to help you understand and test how throughput buckets can be leveraged for high-throughput, cost-efficient workloads.

## Features
- **Request-level throughput bucket usage** (see `ThroughputBucketQuickstart.cs`)
- **Bulk API throughput bucket usage** (see `ThroughputBucketBulkQuickstart.cs`)

## Prerequisites
- [.NET 8.0 SDK](https://dotnet.microsoft.com/download)
- An Azure Cosmos DB account with the required features enabled
- Properly configured [Azure Identity](https://learn.microsoft.com/azure/developer/dotnet/azure-sdk-authentication?tabs=command-line) for authentication (uses `DefaultAzureCredential`)

## Configuration
All connection and workload parameters are set in `App.config`:
> **Note:** You can adjust these values to match your Cosmos DB setup and desired workload.

## How to Run

### 1. Build the Project
```pwsh
# In the project root
dotnet build
```

### 2. Run a Sample
You can run either sample by specifying the class name as the entry point:

#### Bulk API Sample
```pwsh
dotnet run --project bucketing-sample.csproj --no-build --no-launch-profile -- ThroughputBucketBulkQuickstart
```

#### Request-level Throughput Bucket Sample
```pwsh
dotnet run --project bucketing-sample.csproj --no-build --no-launch-profile -- ThroughputBucketQuickstart
```

> If you are using Visual Studio, right-click the desired file and select "Set as Startup Object" before running.

## Output
The samples will print per-second statistics to the console, such as:
```
Reads/sec: 5000, Reads Throttled/sec: 0, Inserts/sec: 1000, Inserts Throttled/sec: 0
```

## Project Structure
- `ThroughputBucketBulkQuickstart.cs` — Demonstrates bulk API usage with throughput buckets
- `ThroughputBucketQuickstart.cs` — Demonstrates request-level throughput bucket usage
- `CosmosDbService.cs` — Shared service logic for Cosmos DB operations
- `App.config` — All configuration values
- `Product.cs` — Model for product documents

## Best Practices
- Use separate files for each sample for clarity and maintainability
- Configure all parameters in `App.config` for easy experimentation
- Monitor throttling and adjust workload or Cosmos DB RU/s as needed

## Contributing
Pull requests and issues are welcome! Please open an issue if you have questions or suggestions.

## License
MIT
