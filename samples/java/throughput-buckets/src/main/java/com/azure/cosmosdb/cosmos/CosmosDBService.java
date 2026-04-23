package com.azure.cosmosdb.cosmos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedFlux;
import com.azure.identity.DefaultAzureCredentialBuilder;

import reactor.core.publisher.Flux;

public class CosmosDBService {
    private final CosmosAsyncClient  client;
    private final  CosmosAsyncDatabase  database;
    private final CosmosAsyncContainer container;

    public CosmosDBService(String endpoint, String databaseName, String containerName) {
        this.client = new CosmosClientBuilder()
                .endpoint(endpoint)
                .consistencyLevel(ConsistencyLevel.EVENTUAL)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildAsyncClient();
        this.database = client.getDatabase(databaseName);
        this.container = database.getContainer(containerName);
    }

    public <T> void createItem(T item) {
        container.createItem(item).block();
    }

    public <T> T readItem(String id, Class<T> itemType) {
        return container.readItem(id, new PartitionKey(id), itemType).block().getItem();
    }

    public <T> CosmosPagedFlux<T> readAllItems(Class<T> itemType) {
        return container.readAllItems(new PartitionKey(""), itemType);
    }

    public <T> CosmosPagedFlux<T> queryItems(String sql, CosmosQueryRequestOptions options, Class<T> itemType) {
        return container.queryItems(sql, options, itemType);
    }

    public void close() {
        client.close();
    }

    public ThroughputControlGroupConfig getThroughputControlGroupConfig() {
        ThroughputControlGroupConfig groupConfig =
            new ThroughputControlGroupConfigBuilder()
                .groupName("group-" + UUID.randomUUID())
                .throughputBucket(1)
                .build();
        container.enableServerThroughputControlGroup(groupConfig);
        return groupConfig;
    }

    public CompletableFuture<Integer> getMaxIdAsync() {
        String queryDefinition ="SELECT max(c.id) as maxId FROM c";
        CosmosPagedFlux<Object> feedIterator = container.queryItems(queryDefinition, new CosmosQueryRequestOptions(), Object.class);
        CompletableFuture<Integer> future = new CompletableFuture<>();
        AtomicInteger maxId = new AtomicInteger(0);
        feedIterator.byPage().subscribe(page -> {
            if (page.getResults().size() > 0) {
                Map<String, Object> obj = (Map<String, Object>) page.getResults().get(0);
                maxId.set(((Number) obj.get("maxId")).intValue());
            }
        }, future::completeExceptionally, () -> future.complete(maxId.get()));
        return future;
	}
}
