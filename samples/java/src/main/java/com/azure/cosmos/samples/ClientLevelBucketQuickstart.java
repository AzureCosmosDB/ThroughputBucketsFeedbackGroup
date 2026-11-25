package com.azure.cosmos.samples;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.ThroughputControlGroupConfigBuilder;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public final class ClientLevelBucketQuickstart {
    public static Mono<WorkloadSupport.InventoryStats> run(CosmosAsyncContainer inventoryContainer,
        CosmosAsyncContainer customerContainer, boolean useBuckets, List<CosmosItemOperation> operations,
        String standardTenant, String premiumTenant, int queryCount) {
        if (useBuckets) {
            // All requests on the inventory client's container inherit bucket 1.
            inventoryContainer.enableServerThroughputControlGroup(new ThroughputControlGroupConfigBuilder()
                .groupName("inventoryClient")
                .throughputBucket(1)
                .defaultControlGroup(true)
                .build());
        }

        var inventory = inventoryContainer.executeBulkOperations(Flux.fromIterable(operations))
            .collectList()
            .map(WorkloadSupport::countBulkResults);
        var customers = Mono.zip(
            WorkloadSupport.queryTenant(customerContainer, standardTenant, CosmosQueryRequestOptions::new, queryCount),
            WorkloadSupport.queryTenant(customerContainer, premiumTenant, CosmosQueryRequestOptions::new, queryCount)
        ).map(results -> new WorkloadSupport.TenantStats(results.getT1(), results.getT2()));

        return Mono.zip(inventory, customers)
            .map(results -> new WorkloadSupport.InventoryStats(results.getT1(), results.getT2()));
    }

    public static void main(String[] args) {
        // Separate clients keep the inventory default away from customer requests.
        try (var inventoryClient = WorkloadSupport.createClient();
             var customerClient = WorkloadSupport.createClient()) {
            String database = System.getenv("COSMOS_DATABASE");
            String container = System.getenv("COSMOS_CONTAINER");
            String standardTenant = System.getenv().getOrDefault("COSMOS_STANDARD_TENANT", "tenant_1");
            String premiumTenant = System.getenv().getOrDefault("COSMOS_PREMIUM_TENANT", "tenant_7");
            boolean useBuckets = Boolean.parseBoolean(System.getenv().getOrDefault("USE_THROUGHPUT_BUCKETS", "true"));
            var results = run(inventoryClient.getDatabase(database).getContainer(container),
                customerClient.getDatabase(database).getContainer(container), useBuckets,
                WorkloadSupport.createInventoryOperations(
                    Integer.parseInt(System.getenv().getOrDefault("COSMOS_BULK_ITEMS", "10")),
                    standardTenant, premiumTenant),
                standardTenant, premiumTenant,
                Integer.parseInt(System.getenv().getOrDefault("COSMOS_QUERY_COUNT", "20"))).block();
            WorkloadSupport.printStats("Inventory bulk writes", results.inventory());
            WorkloadSupport.printTenantStats(results.customers());
        }
    }
}
