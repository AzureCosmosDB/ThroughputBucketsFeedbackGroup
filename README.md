# Introducing Throughput buckets

Throughput buckets help manage resource consumption for workloads sharing a Cosmos DB container by limiting the maximum throughput a bucket can consume.

- Each bucket has a maximum throughput percentage, capping the fraction of the container's total throughput that it can consume.
- Requests assigned to a bucket can consume throughput only up to this limit.
- If the bucket exceeds its configured limit, subsequent requests are throttled.
- This mechanism helps in preventing resource contention, ensuring that no single workload consumes excessive throughput and impacts others.

Read the [official documentation](https://learn.microsoft.com/azure/cosmos-db/nosql/throughput-buckets) to learn more.

## How to create Throughput buckets

Follow the instructions to [create and manage](https://learn.microsoft.com/azure/cosmos-db/nosql/throughput-buckets?tabs=dotnet#configuring-throughput-buckets) throughput buckets.

## Quickstart sample

- [Dotnet](samples/dotnet/README.md)
