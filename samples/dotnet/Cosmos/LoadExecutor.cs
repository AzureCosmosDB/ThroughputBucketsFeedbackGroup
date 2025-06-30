using Microsoft.Azure.Cosmos;
using System.Collections.Concurrent;
using System.Net;
using Newtonsoft.Json;

namespace Cosmos
{
    public class LoadExecutor
    {
        private readonly CosmosDbService _dbService;
        public long ReadsSucceeded;
        public long ReadsThrottled;
        public long QueriesSucceeded;
        public long QueriesThrottled;
        public long CreatesSucceeded;
        public long CreatesThrottled;

        public LoadExecutor(CosmosDbService dbService)
        {
            _dbService = dbService;
        }

        private async Task ProcessWorkAsync<T>(
            int totalItems,
            int maxConcurrency,
            Func<int, T> itemFactory,
            Func<T, Task> workerFunc)
        {
            var queue = new ConcurrentQueue<T>();
            for (int i = 1; i <= totalItems; i++)
                queue.Enqueue(itemFactory(i));

            var workers = new List<Task>();
            for (int w = 0; w < maxConcurrency; w++)
            {
                workers.Add(Task.Run(async () =>
                {
                    while (queue.TryDequeue(out var item))
                    {
                        await workerFunc(item);
                    }
                }));
            }
            await Task.WhenAll(workers);
        }

        public async Task RunPointReadsAsync(int totalReads = 10000, int maxConcurrency = 100)
        {
            await ProcessWorkAsync(
                totalReads,
                maxConcurrency,
                i => i.ToString(),
                async id =>
                {
                    try
                    {
                        var response = await _dbService.ReadItemAsync(id);
                        Interlocked.Increment(ref ReadsSucceeded);
                    }
                    catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.TooManyRequests)
                    {
                        Interlocked.Increment(ref ReadsThrottled);
                        Console.WriteLine($"Read throttled with id: {id} - RetryAfter: {ex.RetryAfter?.TotalMilliseconds ?? 0} seconds");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Error] {id} - {ex.Message}");
                    }
                });
        }

        public async Task RunQueryAsync(int totalQueries = 1000, int maxConcurrency = 50, bool useThroughputBucket = false)
        {
            QueryRequestOptions queryRequestOptions = new QueryRequestOptions();
            if (useThroughputBucket)
            {
                queryRequestOptions.ThroughputBucket = 1;
            }
            await ProcessWorkAsync(
                totalQueries,
                maxConcurrency,
                i => i.ToString(),
                async id =>
                {
                    try
                    {
                        QueryDefinition queryDefinition = new QueryDefinition("SELECT * FROM c WHERE c.id = @id");
                        FeedIterator<Product> feedIterator = _dbService.GetItemQueryIterator(queryDefinition.WithParameter("@id", id), queryRequestOptions);
                        while (feedIterator.HasMoreResults)
                        {
                            FeedResponse<Product> response = await feedIterator.ReadNextAsync();
                            Interlocked.Increment(ref QueriesSucceeded);
                        }
                    }
                    catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.TooManyRequests)
                    {
                        Interlocked.Increment(ref QueriesThrottled);
                        Console.WriteLine($"Query throttled with pk: {id} - RetryAfter: {ex.RetryAfter?.TotalMilliseconds ?? 0} ms");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Error] {id} - {ex.Message}");
                    }
                });
        }

        public async Task RunBulkInsertAsync(int totalDocs, int maxConcurrency = 100)
        {
            int maxId = await _dbService.GetMaxIdAsync();
            Console.WriteLine($"Max Id found: {maxId}. Starting bulk insert from {maxId + 1} to {maxId + totalDocs}.");
            await ProcessWorkAsync(
                totalDocs,
                maxConcurrency,
                i => Product.GenerateProduct(maxId + i),
                async product =>
                {
                    try
                    {
                        await _dbService.CreateBulkItemsAsync(product);
                        Interlocked.Increment(ref CreatesSucceeded);
                    }
                    catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.TooManyRequests)
                    {
                        Console.WriteLine($"Create throttled with id: {product.id} - RetryAfter: {ex.RetryAfter?.TotalMilliseconds ?? 0} ms");
                        Interlocked.Increment(ref CreatesThrottled);
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"Error creating product {product.id}: {ex.Message}");
                        Interlocked.Increment(ref CreatesThrottled);
                    }
                });
            Console.WriteLine($"Bulk insert completed for {totalDocs} documents.");
        }

        public async Task UploadProductsFromFileAsync(string filePath, int maxConcurrency = 100)
        {
            Console.WriteLine($"Reading products from {filePath} ...");
            var json = await File.ReadAllTextAsync(filePath);
            var products = JsonConvert.DeserializeObject<List<Product>>(json);
            if (products == null || products.Count == 0)
            {
                Console.WriteLine("No products found in file.");
                return;
            }
            Console.WriteLine($"Uploading {products.Count} products into Cosmos DB ...");
            var queue = new ConcurrentQueue<Product>(products);
            var workers = new List<Task>();
            for (int w = 0; w < maxConcurrency; w++)
            {
                workers.Add(Task.Run(async () =>
                {
                    while (queue.TryDequeue(out var product))
                    {
                        try
                        {
                            await _dbService.CreateBulkItemsAsync(product);
                            Interlocked.Increment(ref CreatesSucceeded);
                        }
                        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.TooManyRequests)
                        {
                            Console.WriteLine($"Create throttled with id: {product.id} - RetryAfter: {ex.RetryAfter?.TotalMilliseconds ?? 0} ms");
                            Interlocked.Increment(ref CreatesThrottled);
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"Error creating product {product.id}: {ex.Message}");
                            Interlocked.Increment(ref CreatesThrottled);
                        }
                    }
                }));
            }
            await Task.WhenAll(workers);
            Console.WriteLine($"File upload completed for {products.Count} products.");
        }
    }
}