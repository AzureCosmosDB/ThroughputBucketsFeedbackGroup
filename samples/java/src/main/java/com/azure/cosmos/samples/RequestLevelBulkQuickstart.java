package com.azure.cosmos.samples;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.ThroughputControlGroupConfigBuilder;
import com.azure.cosmos.models.CosmosBulkExecutionOptions;
import com.azure.cosmos.models.CosmosBulkOperationResponse;
import com.azure.cosmos.models.CosmosItemOperation;
import reactor.core.publisher.Flux;

import java.util.List;

public final class RequestLevelBulkQuickstart {
    public static Flux<CosmosBulkOperationResponse<Object>> execute(
        CosmosAsyncContainer container, List<CosmosItemOperation> operations, int bucket) {
        container.enableServerThroughputControlGroup(new ThroughputControlGroupConfigBuilder()
            .groupName("requestBucket")
            .throughputBucket(bucket)
            .build());

        // Only this bulk execution uses the group. There is no client default.
        var options = new CosmosBulkExecutionOptions()
            .setThroughputControlGroupName("requestBucket");
        return container.executeBulkOperations(Flux.fromIterable(operations), options);
    }

    public static void main(String[] args) {
        try (var client = BulkSampleSupport.createClient()) {
            var container = client.getDatabase(System.getenv("COSMOS_DATABASE"))
                .getContainer(System.getenv("COSMOS_CONTAINER"));
            int bucket = Integer.parseInt(System.getenv().getOrDefault("COSMOS_THROUGHPUT_BUCKET", "1"));
            BulkSampleSupport.printResults(execute(container, BulkSampleSupport.createOperations("request"), bucket));
        }
    }
}
