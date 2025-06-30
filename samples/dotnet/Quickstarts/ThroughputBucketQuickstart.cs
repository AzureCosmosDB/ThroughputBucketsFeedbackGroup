using System.Diagnostics;
using Cosmos;
using Config;

namespace Quickstarts
{
    public class ThroughputBucketQuickstart
    {
        static async Task Main(string[] args)
        {
            var config = AppConfig.FromAppSettings();
            var dbService = new CosmosDbService(config.EndpointUrl, config.DatabaseId, config.ContainerId);
            var loadExecutor = new LoadExecutor(dbService);

            // === User Prompts ===
            int uploadChoice = PromptForYesNo("Do you want to upload data to Cosmos DB container? \nEnter 1 for Yes, 0 for No: ");
            if (uploadChoice == 1)
                await loadExecutor.UploadProductsFromFileAsync("data/products.json", config.MaxInsertConcurrency);
            else
                Console.WriteLine("[Info] Skipping data upload.");

            int tbChoice = PromptForYesNo("Do you want to use throughput buckets? Enter 1 for Yes, 0 for No: ");
            bool useThroughputBucket = tbChoice == 1;
            Console.WriteLine(useThroughputBucket ? "[Info] Running with throughput buckets enabled." : "[Info] Running without throughput buckets.");

            await RunWorkload(loadExecutor, config, useThroughputBucket);
        }

        private static async Task RunWorkload(LoadExecutor loadExecutor, AppConfig config, bool useThroughputBucket)
        {
            using var cts = new CancellationTokenSource();
            var statsTask = LogStats(loadExecutor, cts.Token);
            var stopwatch = Stopwatch.StartNew();
            TimeSpan runDuration = TimeSpan.FromSeconds(config.RunDurationInSecs);
            Console.WriteLine("Running point reads and queries concurrently");
            while (stopwatch.Elapsed < runDuration)
            {
                var tasks = new List<Task>
            {
                loadExecutor.RunPointReadsAsync(totalReads: config.TotalReads, maxConcurrency: config.MaxReadConcurrency),
                loadExecutor.RunQueryAsync(totalQueries: config.TotalQueries, maxConcurrency: config.MaxQueryConcurrency, useThroughputBucket: useThroughputBucket),
            };
                await Task.WhenAll(tasks);
                await Task.Delay(100);
            }
            Console.WriteLine("All concurrent operations completed.");
            cts.Cancel();
            await statsTask;
        }

        // Helper for yes/no prompts
        private static int PromptForYesNo(string message)
        {
            int choice = -1;
            while (choice != 0 && choice != 1)
            {
                Console.Write(message);
                var input = Console.ReadLine();
                if (!int.TryParse(input, out choice) || (choice != 0 && choice != 1))
                    Console.WriteLine("Invalid input. Please enter 1 (Yes) or 0 (No).");
            }
            return choice;
        }

        private static Task LogStats(LoadExecutor loadExecutor, CancellationToken token)
        {
            return Task.Run(async () =>
            {
                long totalReadsSucceeded = 0;
                long totalReadsThrottled = 0;
                long totalQueriesSucceeded = 0;
                long totalQueriesThrottled = 0;

                while (!token.IsCancellationRequested)
                {
                    await Task.Delay(1000);
                    long reads = Interlocked.Exchange(ref loadExecutor.ReadsSucceeded, 0);
                    long readsThrottled = Interlocked.Exchange(ref loadExecutor.ReadsThrottled, 0);
                    long queries = Interlocked.Exchange(ref loadExecutor.QueriesSucceeded, 0);
                    long queriesThrottled = Interlocked.Exchange(ref loadExecutor.QueriesThrottled, 0);

                    totalReadsSucceeded += reads;
                    totalReadsThrottled += readsThrottled;
                    totalQueriesSucceeded += queries;
                    totalQueriesThrottled += queriesThrottled;

                    Console.WriteLine($"Reads succeeded/sec: {reads}, Reads throttled/sec: {readsThrottled}, Queries succeeded/sec: {queries}, Queries throttled/sec: {queriesThrottled}");
                }
                Console.WriteLine($"=== OPERATION SUMMARY ===\n" +
                   $"Reads: {totalReadsSucceeded} succeeded, {totalReadsThrottled} throttled\n" +
                   $"Queries: {totalQueriesSucceeded} succeeded, {totalQueriesThrottled} throttled\n" +
                   $"=========================");
            }, token);
        }
    }
}
