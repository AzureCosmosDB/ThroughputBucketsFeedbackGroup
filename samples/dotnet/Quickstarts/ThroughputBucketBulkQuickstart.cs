using System.Diagnostics;
using Cosmos;
using Config;

namespace Quickstarts
{

    public class ThroughputBucketBulkQuickstart
    {
        static async Task Main(string[] args)
        {
            var config = AppConfig.FromAppSettings();

            // === User Prompts ===
            int uploadChoice = PromptForYesNo("Do you want to upload data to Cosmos DB container? \nEnter 1 for Yes, 0 for No: ");
            int tbChoice = PromptForYesNo("Do you want to use throughput buckets?\n Enter 1 for Yes, 0 for No: ");
            bool useThroughputBucket = tbChoice == 1;
            Console.WriteLine(useThroughputBucket ? "[Info] Running with throughput buckets enabled." : "[Info] Running without throughput buckets.");

            var dbServiceReads = new CosmosDbService(config.EndpointUrl, config.DatabaseId, config.ContainerId);
            var loadExecutor = new LoadExecutor(dbServiceReads);
            var dbServiceBulk = new CosmosDbService(config.EndpointUrl, config.DatabaseId, config.ContainerId, allowBulk: true, throughputBucket: useThroughputBucket ? 1 : (int?)null);
            var loadExecutorBulk = new LoadExecutor(dbServiceBulk);

            if (uploadChoice == 1)
                await loadExecutor.UploadProductsFromFileAsync("data/products.json");
            else
                Console.WriteLine("[Info] Skipping data upload.");

            await RunWorkload(loadExecutor, loadExecutorBulk, config);
        }

        private static async Task RunWorkload(LoadExecutor loadExecutorReads, LoadExecutor loadExecutorBulk, AppConfig config)
        {
            using var cts = new CancellationTokenSource();
            var statsTask = LogStats(loadExecutorReads, loadExecutorBulk, cts.Token);
            var stopwatch = Stopwatch.StartNew();
            TimeSpan runDuration = TimeSpan.FromSeconds(config.RunDurationInSecs);
            Console.WriteLine("Running point reads and bulk creates concurrently");
            while (stopwatch.Elapsed < runDuration)
            {
                var tasks = new List<Task>
            {
                loadExecutorReads.RunPointReadsAsync(totalReads: config.TotalReads, maxConcurrency: config.MaxReadConcurrency),
                loadExecutorBulk.RunBulkInsertAsync(totalDocs: config.TotalCreates, maxConcurrency: config.MaxInsertConcurrency)
            };
            await Task.WhenAll(tasks);
            await Task.Delay(100);
            }
            Console.WriteLine("All concurrent operations completed.");
            cts.Cancel();
            await statsTask;
        }

        private static Task LogStats(LoadExecutor execReads, LoadExecutor execBulk, CancellationToken token)
        {
            return Task.Run(async () =>
            {
                long totalReadsSucceeded = 0;
                long totalReadsThrottled = 0;
                long totalCreatesSucceeded = 0;
                long totalCreatesThrottled = 0;

                while (!token.IsCancellationRequested)
                {
                    await Task.Delay(1000);
                    long reads = Interlocked.Exchange(ref execReads.ReadsSucceeded, 0);
                    long readsThrottled = Interlocked.Exchange(ref execReads.ReadsThrottled, 0);
                    long creates = Interlocked.Exchange(ref execBulk.CreatesSucceeded, 0);
                    long CreatesThrottled = Interlocked.Exchange(ref execBulk.CreatesThrottled, 0);

                    totalReadsSucceeded += reads;
                    totalReadsThrottled += readsThrottled;
                    totalCreatesSucceeded += creates;
                    totalCreatesThrottled += CreatesThrottled;

                    Console.WriteLine($"Reads succeeded/sec: {reads}, Reads throttled/sec: {readsThrottled}, creates succeeded/sec: {creates}, creates throttled/sec: {CreatesThrottled}");
                }
                Console.WriteLine($"=== OPERATION SUMMARY ===\n" +
                   $"Reads: {totalReadsSucceeded} succeeded, {totalReadsThrottled} throttled\n" +
                   $"creates: {totalCreatesSucceeded} succeeded, {totalCreatesThrottled} throttled\n" +
                   $"=========================");
            }, token);
        }

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
    }
}
