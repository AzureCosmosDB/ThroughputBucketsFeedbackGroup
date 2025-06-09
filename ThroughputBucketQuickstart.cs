using System;
using System.Configuration;
using System.Diagnostics;
using System.Threading.Tasks;

public class ThroughputBucketQuickstart
{
    static string endpointUrl = ConfigurationManager.AppSettings["CosmosEndpoint"];
    static string databaseId = ConfigurationManager.AppSettings["CosmosDatabase"];
    static string containerId = ConfigurationManager.AppSettings["CosmosContainer"];
    static int totalReads = int.Parse(ConfigurationManager.AppSettings["TotalReads"]) ;
    static int totalQueries = int.Parse(ConfigurationManager.AppSettings["TotalQueries"]);
    static int maxReadConcurrency = int.Parse(ConfigurationManager.AppSettings["MaxReadConcurrency"]);
    static int maxQueryConcurrency = int.Parse(ConfigurationManager.AppSettings["MaxQueryConcurrency"]);
    static int runDurationMinutes = int.Parse(ConfigurationManager.AppSettings["RunDurationMinutes"]);

    static async Task Main(string[] args)
    {
        CosmosDbService cosmosDbService = new CosmosDbService(endpointUrl, databaseId, containerId);
        using var cts = new CancellationTokenSource();
        var statsTask = LogStats(cosmosDbService, cts.Token);
        var stopwatch = Stopwatch.StartNew();
        TimeSpan runDuration = TimeSpan.FromMinutes(runDurationMinutes);
        Console.WriteLine("Using Cosmos DB with throughput bucket enabled.");
        while (stopwatch.Elapsed < runDuration)
        {
            List<Task> tasks =
            [
                cosmosDbService.RunSustainedPointReadsAsync(totalReads: totalReads, maxConcurrency: maxReadConcurrency),
                cosmosDbService.RunSustainedQueryAsync(totalQueries: totalQueries, maxConcurrency: maxQueryConcurrency, useThroughputBucket: true),
            ];
            await Task.WhenAll(tasks);
            await Task.Delay(100); // Wait 100 ms after both ops complete
        }
        Console.WriteLine("All sustained operations completed.");
        cts.Cancel();
        await statsTask;
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
				long queries = Interlocked.Exchange(ref cosmosDbService.QueriesSucceeded, 0);
				long queriesThrottled = Interlocked.Exchange(ref cosmosDbService.QueriesThrottled, 0);

				Console.WriteLine($"Reads/sec: {reads}, Reads Throttled/sec: {readsThrottled}, Queries/sec: {queries}, Queries Throttled/sec: {queriesThrottled}");
			}
		}, token);
	}
}
