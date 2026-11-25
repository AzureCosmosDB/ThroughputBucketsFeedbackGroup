package com.azure.cosmos.samples;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosBulkOperationResponse;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "COSMOS_RUN_E2E", matches = "true")
class BulkQuickstartsTest {
    @Test
    void clientLevelBulkCreatesProductsUsingTheDefaultGroup() {
        verifyScenario("client-level", (container, operations) ->
            ClientLevelBulkQuickstart.execute(container, operations, bucket()));
    }

    @Test
    void requestLevelBulkCreatesProductsUsingOnlyTheExplicitGroup() {
        verifyScenario("request-level", (container, operations) ->
            RequestLevelBulkQuickstart.execute(container, operations, bucket()));
    }

    private static int bucket() {
        return Integer.parseInt(System.getenv().getOrDefault("COSMOS_THROUGHPUT_BUCKET", "1"));
    }

    private static void verifyScenario(String name,
        BiFunction<CosmosAsyncContainer, List<CosmosItemOperation>,
            Flux<CosmosBulkOperationResponse<Object>>> execute) {
        try (var client = BulkSampleSupport.createClient()) {
            var container = client.getDatabase(System.getenv("COSMOS_DATABASE"))
                .getContainer(System.getenv("COSMOS_CONTAINER"));
            assertEquals(List.of("/tenant", "/id"),
                container.read().block().getProperties().getPartitionKeyDefinition().getPaths());
            List<CosmosItemOperation> operations = BulkSampleSupport.createOperations("e2e-" + name);
            System.out.println("E2E items: " + operations.stream()
                .map(operation -> operation.<Map<String, String>>getItem().get("id")).toList());
            Throwable failure = null;
            try {
                var results = execute.apply(container, operations).collectList().block();
                assertEquals(operations.size(), results.size());
                for (var result : results) {
                    assertNull(result.getException());
                    assertEquals(201, result.getResponse().getStatusCode());
                }
                for (var operation : operations) {
                    Map<String, String> product = operation.getItem();
                    var read = container.readItem(product.get("id"), operation.getPartitionKeyValue(), JsonNode.class)
                        .block();
                    assertEquals(200, read.getStatusCode());
                    assertEquals(product.get("name"), read.getItem().get("name").asText());
                }
            } catch (RuntimeException | AssertionError error) {
                failure = error;
                throw error;
            } finally {
                List<Throwable> cleanupErrors = new ArrayList<>();
                for (var operation : operations) {
                    Map<String, String> product = operation.getItem();
                    try {
                        try {
                            container.deleteItem(product.get("id"), operation.getPartitionKeyValue(),
                                new CosmosItemRequestOptions()).block();
                        } catch (CosmosException error) {
                            if (error.getStatusCode() != 404) {
                                System.err.println("Cleanup diagnostics: " + error.getDiagnostics());
                                throw error;
                            }
                        }
                        var notFound = assertThrows(CosmosException.class, () -> container.readItem(
                            product.get("id"), operation.getPartitionKeyValue(), JsonNode.class).block());
                        assertEquals(404, notFound.getStatusCode());
                    } catch (RuntimeException | AssertionError error) {
                        cleanupErrors.add(error);
                    }
                }
                if (!cleanupErrors.isEmpty()) {
                    AssertionError cleanupFailure = new AssertionError("E2E cleanup failed", cleanupErrors.get(0));
                    cleanupErrors.stream().skip(1).forEach(cleanupFailure::addSuppressed);
                    if (failure != null) failure.addSuppressed(cleanupFailure);
                    else throw cleanupFailure;
                }
            }
            System.out.println("E2E PASS: " + name + ", " + operations.size()
                + " bulk creates, readback and cleanup verified.");
        }
    }
}
