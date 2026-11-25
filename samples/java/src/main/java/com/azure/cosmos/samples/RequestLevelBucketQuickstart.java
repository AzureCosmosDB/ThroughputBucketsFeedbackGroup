package com.azure.cosmos.samples;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.ThroughputControlGroupConfigBuilder;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

public final class RequestLevelBucketQuickstart {
    public static Mono<WorkloadSupport.TenantStats> run(CosmosAsyncContainer container, boolean useBuckets,
        String standardTenant, String premiumTenant, int queryCount) {
        if (useBuckets) {
            container.enableServerThroughputControlGroup(new ThroughputControlGroupConfigBuilder()
                .groupName("standardTenantRequests")
                .throughputBucket(2)
                .build());
        }

        // Only standard-tenant query requests select bucket 2.
        Supplier<CosmosQueryRequestOptions> standardRequestOptions = () -> {
            var options = new CosmosQueryRequestOptions();
            if (useBuckets) options.setThroughputControlGroupName("standardTenantRequests");
            return options;
        };

        return Mono.zip(
            WorkloadSupport.queryTenant(container, standardTenant, standardRequestOptions, queryCount),
            WorkloadSupport.queryTenant(container, premiumTenant, CosmosQueryRequestOptions::new, queryCount)
        ).map(results -> new WorkloadSupport.TenantStats(results.getT1(), results.getT2()));
    }

    public static void main(String[] args) {
        // One shared client, with no default throughput bucket.
        try (var client = WorkloadSupport.createClient()) {
            var container = client.getDatabase(System.getenv("COSMOS_DATABASE"))
                .getContainer(System.getenv("COSMOS_CONTAINER"));
            boolean useBuckets = Boolean.parseBoolean(System.getenv().getOrDefault("USE_THROUGHPUT_BUCKETS", "true"));
            var results = run(container, useBuckets,
                System.getenv().getOrDefault("COSMOS_STANDARD_TENANT", "tenant_1"),
                System.getenv().getOrDefault("COSMOS_PREMIUM_TENANT", "tenant_7"),
                Integer.parseInt(System.getenv().getOrDefault("COSMOS_QUERY_COUNT", "20"))).block();
            WorkloadSupport.printTenantStats(results);
        }
    }
}
