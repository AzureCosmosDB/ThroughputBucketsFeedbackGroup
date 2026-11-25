package com.azure.cosmos.samples;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.ThroughputControlGroupConfigBuilder;
import com.azure.cosmos.models.CosmosBulkOperationResponse;
import com.azure.cosmos.models.CosmosItemOperation;
import reactor.core.publisher.Flux;

import java.util.List;

public final class ClientLevelBulkQuickstart {
    public static Flux<CosmosBulkOperationResponse<Object>> execute(
        CosmosAsyncContainer container, List<CosmosItemOperation> operations, int bucket) {
        // All bulk requests on this client's container inherit the default group.
        container.enableServerThroughputControlGroup(new ThroughputControlGroupConfigBuilder()
            .groupName("clientBucket")
            .throughputBucket(bucket)
            .defaultControlGroup(true)
            .build());

        return container.executeBulkOperations(Flux.fromIterable(operations));
    }

    public static void main(String[] args) {
        try (var client = BulkSampleSupport.createClient()) {
            var container = client.getDatabase(System.getenv("COSMOS_DATABASE"))
                .getContainer(System.getenv("COSMOS_CONTAINER"));
            int bucket = Integer.parseInt(System.getenv().getOrDefault("COSMOS_THROUGHPUT_BUCKET", "1"));
            BulkSampleSupport.printResults(execute(container, BulkSampleSupport.createOperations("client"), bucket));
        }
    }
}
