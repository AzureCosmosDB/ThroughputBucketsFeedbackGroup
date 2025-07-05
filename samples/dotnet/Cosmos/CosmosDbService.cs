using Microsoft.Azure.Cosmos;
using Azure.Identity;

namespace Cosmos
{
	public class CosmosDbService
	{
		private readonly Container _container;
		private readonly Container? _bulkContainer;

		public CosmosDbService(string endpointUrl, string databaseId, string containerId, bool allowBulk = false, int? throughputBucket = null)
		{
			CosmosClientOptions options = new CosmosClientOptions
			{
				ConnectionMode = ConnectionMode.Direct,
				ConsistencyLevel = ConsistencyLevel.Session,
				MaxRetryWaitTimeOnRateLimitedRequests = TimeSpan.FromSeconds(1),
				MaxRequestsPerTcpConnection = 50,
				MaxTcpConnectionsPerEndpoint = 500,
				EnableTcpConnectionEndpointRediscovery = true,
				AllowBulkExecution = allowBulk
			};
			if (throughputBucket.HasValue)
				options.ThroughputBucket = throughputBucket.Value;
			CosmosClient client = new CosmosClient(
				endpointUrl,
				new DefaultAzureCredential(),
				options);
			_container = client.GetContainer(databaseId, containerId);
			if (allowBulk)
				_bulkContainer = _container;
		}

		public Task<ItemResponse<Product>> ReadItemAsync(string id)
		{
			return _container.ReadItemAsync<Product>(id, new PartitionKey(id));
		}

		public FeedIterator<Product> GetItemQueryIterator(QueryDefinition queryDefinition, QueryRequestOptions options = null)
		{
			return _container.GetItemQueryIterator<Product>(queryDefinition, requestOptions: options);
		}

		public Task<ItemResponse<Product>> CreateBulkItemsAsync(Product product)
		{
			if (_bulkContainer == null)
				throw new InvalidOperationException("Bulk operations are not enabled for this CosmosDbService instance.");
			return _bulkContainer.CreateItemAsync(product, new PartitionKey(product.id));
		}

		public Task<ItemResponse<Product>> CreateItemAsync(Product product)
		{
			return _container.CreateItemAsync(product, new PartitionKey(product.id));
		}


		public async Task<int> GetMaxIdAsync()
		{
			QueryDefinition queryDefinition = new QueryDefinition("SELECT max(c.id) as maxId FROM c");
			FeedIterator<dynamic> feedIterator = _container.GetItemQueryIterator<dynamic>(queryDefinition);
			int maxId = 0;
			while (feedIterator.HasMoreResults)
			{
				FeedResponse<dynamic> response = await feedIterator.ReadNextAsync();
				if (response.Count > 0)
				{
					var obj = response.First();
					maxId = (int)obj["maxId"];
				}
			}
			return maxId;
		}
	}
}
