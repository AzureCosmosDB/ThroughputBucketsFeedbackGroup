using System.Net;
using Microsoft.Azure.Cosmos;
using Azure.Identity;
using System.Diagnostics;
using Bogus;
using Newtonsoft.Json.Linq; // Add this for the Faker library

public class CosmosDbService
{
	private readonly Container _container;
	private static Container _bulkContainer;
	private static List<string> typeList = new List<string> { "Accessories",
		"Apparel",
		"Bags",
		"Climbing",
		"Cycling",
		"Electronics",
		"Footwear",
		"Home",
		"Jackets",
		"Navigation",
		"Ski/boarding",
		"Trekking" };

	private static CosmosClientOptions clientOptions = new CosmosClientOptions
	{
		ConnectionMode = ConnectionMode.Direct,
		ConsistencyLevel = ConsistencyLevel.Session,
		MaxRetryWaitTimeOnRateLimitedRequests = TimeSpan.FromSeconds(1),
		MaxRequestsPerTcpConnection = 50,
		MaxTcpConnectionsPerEndpoint = 500,
		EnableTcpConnectionEndpointRediscovery = true
	};

	public long ReadsSucceeded;
	public long ReadsThrottled;
	public long QueriesSucceeded;
	public long QueriesThrottled;
	public long InsertsSucceeded;
	public long InsertsThrottled;

	public CosmosDbService(string endpointUrl, string databaseId, string containerId)
	{
		CosmosClient client = new CosmosClient(
				endpointUrl,
				new DefaultAzureCredential(),// Uses the managed identity.
				clientOptions);
		
		_container = client.GetContainer(databaseId, containerId);
	}

	public CosmosDbService()
	{
		// Default constructor for scenarios where the CosmosClient is injected or created elsewhere.
	}

	public async Task RunSustainedPointReadsAsync(int totalReads = 10000, int maxConcurrency = 100)
	{
		SemaphoreSlim semaphore = new SemaphoreSlim(maxConcurrency);
		List<Task> tasks = new List<Task>();

		var stopwatch = Stopwatch.StartNew();
		for (int i = 1; i <= totalReads; i++)
		{
			string id = i.ToString();

			await semaphore.WaitAsync(); // allow task launching in parallel

			tasks.Add(Task.Run(async () =>
			{
				try
				{
					var response = await _container.ReadItemAsync<Product>(id, new PartitionKey(id));
					Interlocked.Increment(ref ReadsSucceeded); // increment on success
				}
				catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.TooManyRequests)
				{
					Interlocked.Increment(ref ReadsThrottled); // increment on throttle
					Console.WriteLine($"[Throttled] {id} - RetryAfter:");
					// Optional: await Task.Delay(ex.RetryAfter); // to backoff
				}
				catch (Exception ex)
				{
					Console.WriteLine($"[Error] {id} - {ex.Message}");
				}
				finally
				{
					semaphore.Release();
				}
			}));
		}
		await Task.WhenAll(tasks);
		stopwatch.Stop();
		Console.WriteLine($" All reads completed in {stopwatch.Elapsed.TotalSeconds:F2} sec");
	}


	public async Task RunSustainedQueryAsync(int totalQueries = 1000, int maxConcurrency = 50, bool useThroughputBucket = false)
	{
		SemaphoreSlim semaphore = new SemaphoreSlim(maxConcurrency);
		List<Task> tasks = new List<Task>();

		QueryRequestOptions queryRequestOptions = new QueryRequestOptions();
		if (useThroughputBucket)
		{
			queryRequestOptions.ThroughputBucket = 1; // Optional: Use throughput bucket if configured.
		}

		var stopwatch = Stopwatch.StartNew();
		for (int i = 1; i <= totalQueries; i++)
		{
			string id = i.ToString();

			await semaphore.WaitAsync(); // allow task launching in parallel
			tasks.Add(Task.Run(async () =>
			{
				try
				{
					QueryDefinition queryDefinition = new QueryDefinition("SELECT * FROM c WHERE c.id = @id");
					FeedIterator<Product> feedIterator = _container.GetItemQueryIterator<Product>(
										queryDefinition.WithParameter("@id", id), requestOptions: queryRequestOptions);

					while (feedIterator.HasMoreResults)
					{
						FeedResponse<Product> response = await feedIterator.ReadNextAsync();
						Interlocked.Increment(ref QueriesSucceeded); // increment on success
					}
				}
				catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.TooManyRequests)
				{
					Console.WriteLine($"[Throttled] {id} - RetryAfter:");
					Interlocked.Increment(ref QueriesThrottled); // increment on throttle
				}
				catch (Exception ex)
				{
					Console.WriteLine($"[Error] {id} - {ex.Message}");
				}
				finally
				{
					semaphore.Release();
				}
			}));
		}

		await Task.WhenAll(tasks);
		stopwatch.Stop();
		Console.WriteLine($" All queries completed in {stopwatch.Elapsed.TotalSeconds:F2} sec");
	}

	public async Task RunBulkInsertAsync(int totalDocs)
    {
        QueryDefinition queryDefinition = new QueryDefinition("SELECT max(c.Id) as maxId FROM c");
        FeedIterator<dynamic> feedIterator = _container.GetItemQueryIterator<dynamic>(queryDefinition);
        int maxId = 0;
        while (feedIterator.HasMoreResults)
        {
            FeedResponse<dynamic> response = await feedIterator.ReadNextAsync();
            if (response.Count > 0)
            {
                var obj = response.First();
                maxId = obj["maxId"];
            }
        }
	   	//Generate products starting from maxId + 1
		Console.WriteLine($"Max Id found: {maxId}. Starting bulk insert from {maxId + 1} to {maxId + totalDocs}.");
        List<Task> bulkInsertTasks = GenerateProducts(totalDocs, maxId);
        await Task.WhenAll(bulkInsertTasks);
        Console.WriteLine($"Bulk insert completed for {totalDocs} documents.");
    }

    private List<Task> GenerateProducts(int totalDocs, int maxId)
    {
		var faker = new Faker();
        List<Task> bulkInsertTasks = new List<Task>();
        for (int i = maxId + 1; i <= maxId + totalDocs; i++)
        {
            Product product = new Product
            {
                id = i.ToString(),
                Id = i,
                Type = faker.PickRandom(typeList),
                Brand = faker.Company.CompanyName(),
                Name = faker.Commerce.ProductName(),
                Description = faker.Lorem.Sentence(),
                Price = faker.Random.Number(50, 500)
            };
			//catch any exceptions during product creation
			var task = _bulkContainer.CreateItemAsync(product, new PartitionKey(product.id));
			bulkInsertTasks.Add(task.ContinueWith(t =>
			{
				if (t.IsFaulted)
				{
					Console.WriteLine($"Error creating product {product.id}: {t.Exception?.Message}");
					Interlocked.Increment(ref InsertsThrottled); // increment on throttle
				}
				else
				{
					Interlocked.Increment(ref InsertsSucceeded); // increment on success
				}
			}));
        }
        return bulkInsertTasks;
    }

    public void CreateBulkCosmosClient(string endpointUrl, string databaseId, string containerId)
	{
		CosmosClient bulkClient = new CosmosClient(endpointUrl, new DefaultAzureCredential(), new CosmosClientOptions
		{
			ConnectionMode = ConnectionMode.Direct,
			ConsistencyLevel = ConsistencyLevel.Session,
			MaxRetryWaitTimeOnRateLimitedRequests = TimeSpan.FromSeconds(1),
			MaxRequestsPerTcpConnection = 50,
			MaxTcpConnectionsPerEndpoint = 500,
			EnableTcpConnectionEndpointRediscovery = true,
			AllowBulkExecution = true, // Enable bulk execution
			ThroughputBucket = 1 // Enable throughput bucket if needed
		});

		_bulkContainer = bulkClient.GetContainer(databaseId, containerId);
	}
}
