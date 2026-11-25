package com.azure.cosmos.samples;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosBulkOperationResponse;
import com.azure.cosmos.models.CosmosBulkOperations;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.PartitionKeyBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

final class BulkSampleSupport {
    static CosmosAsyncClient createClient() {
        return new CosmosClientBuilder()
            .endpoint(System.getenv("COSMOS_ENDPOINT"))
            .credential(new DefaultAzureCredentialBuilder().build())
            .directMode()
            .buildAsyncClient();
    }

    static List<CosmosItemOperation> createOperations(String scenario) {
        return IntStream.range(0, 10).mapToObj(index -> {
            String id = scenario + "-" + UUID.randomUUID();
            String tenant = "tenant-" + index;
            Map<String, String> product = Map.of("id", id, "tenant", tenant, "name", "Product " + index);
            // The sample container uses hierarchical partition keys /tenant and /id.
            var partitionKey = new PartitionKeyBuilder().add(tenant).add(id).build();
            return CosmosBulkOperations.getCreateItemOperation(product, partitionKey);
        }).toList();
    }

    static void printResults(Flux<CosmosBulkOperationResponse<Object>> execution) {
        for (var result : execution.collectList().block()) {
            Map<String, String> product = result.getOperation().getItem();
            if (result.getException() != null) {
                throw new IllegalStateException("Bulk operation failed: " + product.get("id"),
                    result.getException());
            }
            var response = result.getResponse();
            if (!response.isSuccessStatusCode()) {
                throw new IllegalStateException("Bulk status=" + response.getStatusCode()
                    + ", diagnostics=" + response.getCosmosDiagnostics());
            }
            System.out.printf("%s: status=%d, RU=%.2f%n", product.get("id"),
                response.getStatusCode(), response.getRequestCharge());
        }
    }
}
