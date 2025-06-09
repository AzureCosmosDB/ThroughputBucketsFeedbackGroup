using System;
using System.Configuration;
using System.Diagnostics;
using System.Threading.Tasks;

// This sample demonstrates how to use throughput buckets and bulk API with Azure Cosmos DB.
// It runs sustained point reads and bulk inserts in parallel, reporting per-second stats.
public class ThroughputBucketBulkQuickstart
{
    static string endpointUrl = ConfigurationManager.AppSettings["CosmosEndpoint"];
    static string databaseId = ConfigurationManager.AppSettings["CosmosDatabase"];
    static string containerId = ConfigurationManager.AppSettings["CosmosContainer"];
    static int totalReads = int.Parse(ConfigurationManager.AppSettings["TotalReads"]);
    static int totalInserts = int.Parse(ConfigurationManager.AppSettings["TotalInserts"]);
    static int maxConcurrency = int.Parse(ConfigurationManager.AppSettings["MaxConcurrency"]);
    static int runDurationMinutes = int.Parse(ConfigurationManager.AppSettings["RunDurationMinutes"]);

    static async Task Main(string[] args)
    {
        CosmosDbService cosmosDbService = new CosmosDbService(endpointUrl, databaseId, containerId);
        using var cts = new CancellationTokenSource();
        var statsTask = LogStats(cosmosDbService, cts.Token);

        var stopwatch = Stopwatch.StartNew();
        TimeSpan runDuration = TimeSpan.FromMinutes(runDurationMinutes);
        cosmosDbService.CreateBulkCosmosClient(endpointUrl, databaseId, containerId);
        while (stopwatch.Elapsed < runDuration)
        {
            Console.WriteLine("Running bulk workload...");
            // Start bulk workload tasks
            var bulkTasks = new List<Task>
            {
                cosmosDbService.RunSustainedPointReadsAsync(totalReads: totalReads, maxConcurrency: maxConcurrency),
                cosmosDbService.RunBulkInsertAsync(totalDocs: totalInserts)
            };

            await Task.WhenAll(bulkTasks.ToArray());
            Console.WriteLine("Bulk workload completed.");
            await Task.Delay(100); // Wait 100 ms after both ops complete
        }
        Console.WriteLine("All sustained operations completed.");
        cts.Cancel(); // Signal logger to stop
        await statsTask; // Wait for logger to finish
    }

    private static Task LogStats(CosmosDbService cosmosDbService, CancellationToken token)
    {
        // Start stats logger
        return Task.Run(async () =>
        {
            while (!token.IsCancellationRequested)
            {
                await Task.Delay(1000);
                long reads = Interlocked.Exchange(ref cosmosDbService.ReadsSucceeded, 0);
                long readsThrottled = Interlocked.Exchange(ref cosmosDbService.ReadsThrottled, 0);
                long inserts = Interlocked.Exchange(ref cosmosDbService.InsertsSucceeded, 0);
                long insertsThrottled = Interlocked.Exchange(ref cosmosDbService.InsertsThrottled, 0);

                Console.WriteLine($"Reads/sec: {reads}, Reads Throttled/sec: {readsThrottled}, Inserts/sec: {inserts}, Inserts Throttled/sec: {insertsThrottled}");
            }
        }, token);
    }
}
