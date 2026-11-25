package com.azure.cosmos.samples;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.ThrottlingRetryOptions;
import com.azure.cosmos.models.CosmosBulkOperationResponse;
import com.azure.cosmos.models.CosmosBulkOperations;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKeyBuilder;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

final class WorkloadSupport {
    record Stats(int succeeded, int throttled) {
        Stats add(Stats other) {
            return new Stats(succeeded + other.succeeded, throttled + other.throttled);
        }
    }
    record TenantStats(Stats standard, Stats premium) {}
    record InventoryStats(Stats inventory, TenantStats customers) {}

    static CosmosAsyncClient createClient() {
        return new CosmosClientBuilder()
            .endpoint(System.getenv("COSMOS_ENDPOINT"))
            .credential(new DefaultAzureCredentialBuilder().build())
            .directMode()
            .throttlingRetryOptions(new ThrottlingRetryOptions().setMaxRetryAttemptsOnThrottledRequests(0))
            .buildAsyncClient();
    }

    static Mono<Stats> queryTenant(CosmosAsyncContainer container, String tenant,
        Supplier<CosmosQueryRequestOptions> requestOptions, int count) {
        return Flux.range(0, count).flatMap(index -> {
            var options = requestOptions.get()
                .setPartitionKey(new PartitionKeyBuilder().add(tenant).build());
            var query = new SqlQuerySpec("SELECT TOP 10 c.id FROM c WHERE c.tenant = @tenant",
                List.of(new SqlParameter("@tenant", tenant)));
            return container.queryItems(query, options, JsonNode.class).then(Mono.just(new Stats(1, 0)))
                .onErrorResume(CosmosException.class, error -> {
                    if (error.getStatusCode() == 429) return Mono.just(new Stats(0, 1));
                    System.err.println("Query diagnostics: " + error.getDiagnostics());
                    return Mono.error(error);
                });
        }, 4).reduce(new Stats(0, 0), Stats::add);
    }

    static List<CosmosItemOperation> createInventoryOperations(int count, String standardTenant, String premiumTenant) {
        return IntStream.range(0, count).mapToObj(index -> {
            String id = "inventory-" + UUID.randomUUID();
            String tenant = index % 2 == 0 ? standardTenant : premiumTenant;
            Map<String, String> product = Map.of("id", id, "tenant", tenant, "name", "Product " + index);
            var partitionKey = new PartitionKeyBuilder().add(tenant).add(id).build();
            return CosmosBulkOperations.getCreateItemOperation(product, partitionKey);
        }).toList();
    }

    static Stats countBulkResults(List<? extends CosmosBulkOperationResponse<?>> results) {
        return results.stream().map(WorkloadSupport::bulkResult).reduce(new Stats(0, 0), Stats::add);
    }

    private static Stats bulkResult(CosmosBulkOperationResponse<?> result) {
        if (result.getException() != null) {
            if (result.getException() instanceof CosmosException error && error.getStatusCode() == 429) {
                return new Stats(0, 1);
            }
            throw new IllegalStateException("Inventory bulk operation failed", result.getException());
        }
        var response = result.getResponse();
        if (response.getStatusCode() == 429) return new Stats(0, 1);
        if (response.isSuccessStatusCode()) return new Stats(1, 0);
        throw new IllegalStateException("Inventory bulk status=" + response.getStatusCode()
            + ", diagnostics=" + response.getCosmosDiagnostics());
    }

    static void printStats(String label, Stats stats) {
        System.out.printf("%s: succeeded=%d, throttled=%d%n", label, stats.succeeded(), stats.throttled());
    }

    static void printTenantStats(TenantStats stats) {
        printStats("Standard tenant queries", stats.standard());
        printStats("Premium tenant queries", stats.premium());
    }
}
