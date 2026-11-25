package com.azure.cosmos.samples;

import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.PartitionKeyBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "COSMOS_RUN_E2E", matches = "true")
class BucketingScenariosTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void requestLevelTenantQueries(boolean useBuckets) {
        verifyScenario(false, useBuckets);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void clientLevelInventoryAndCustomerQueries(boolean useBuckets) {
        verifyScenario(true, useBuckets);
    }

    private static PartitionKey partitionKey(Map<String, String> product) {
        return new PartitionKeyBuilder().add(product.get("tenant")).add(product.get("id")).build();
    }

    private static void verifyScenario(boolean clientLevel, boolean useBuckets) {
        String prefix = "e2e-" + UUID.randomUUID();
        String standardTenant = prefix + "-standard";
        String premiumTenant = prefix + "-premium";
        var seeds = List.of(
            Map.of("id", "seed-" + UUID.randomUUID(), "tenant", standardTenant, "name", "Seed product"),
            Map.of("id", "seed-" + UUID.randomUUID(), "tenant", premiumTenant, "name", "Seed product"));
        var operations = clientLevel
            ? WorkloadSupport.createInventoryOperations(4, standardTenant, premiumTenant) : List.<CosmosItemOperation>of();
        List<Map<String, String>> products = new ArrayList<>(seeds);
        operations.forEach(operation -> products.add(operation.getItem()));
        System.out.println("E2E items: " + products);

        try (var validator = new CosmosClientBuilder().endpoint(System.getenv("COSMOS_ENDPOINT"))
                .credential(new DefaultAzureCredentialBuilder().build()).directMode().buildAsyncClient();
             var customerClient = WorkloadSupport.createClient();
             var inventoryClient = WorkloadSupport.createClient()) {
            String database = System.getenv("COSMOS_DATABASE");
            String containerName = System.getenv("COSMOS_CONTAINER");
            var validationContainer = validator.getDatabase(database).getContainer(containerName);
            var customers = customerClient.getDatabase(database).getContainer(containerName);
            var inventory = inventoryClient.getDatabase(database).getContainer(containerName);
            assertEquals(List.of("/tenant", "/id"),
                validationContainer.read().block().getProperties().getPartitionKeyDefinition().getPaths());
            Throwable failure = null;
            try {
                for (var seed : seeds) {
                    validationContainer.createItem(seed, partitionKey(seed), new CosmosItemRequestOptions()).block();
                }
                WorkloadSupport.TenantStats queries;
                if (clientLevel) {
                    var results = ClientLevelBucketQuickstart.run(inventory, customers, useBuckets,
                        operations, standardTenant, premiumTenant, 2).block();
                    queries = results.customers();
                    assertEquals(operations.size(), results.inventory().succeeded() + results.inventory().throttled());
                    int created = 0;
                    for (var operation : operations) {
                        Map<String, String> product = operation.getItem();
                        try {
                            var read = validationContainer.readItem(product.get("id"), partitionKey(product),
                                JsonNode.class).block();
                            assertEquals(product.get("name"), read.getItem().get("name").asText());
                            created++;
                        } catch (CosmosException error) {
                            assertEquals(404, error.getStatusCode());
                        }
                    }
                    assertEquals(results.inventory().succeeded(), created);
                } else {
                    queries = RequestLevelBucketQuickstart.run(customers, useBuckets,
                        standardTenant, premiumTenant, 2).block();
                }
                assertEquals(2, queries.standard().succeeded() + queries.standard().throttled());
                assertEquals(2, queries.premium().succeeded() + queries.premium().throttled());
            } catch (RuntimeException | AssertionError error) {
                failure = error;
                throw error;
            } finally {
                List<Throwable> cleanupErrors = new ArrayList<>();
                for (var product : products) {
                    try {
                        try {
                            validationContainer.deleteItem(product.get("id"), partitionKey(product),
                                new CosmosItemRequestOptions()).block();
                        } catch (CosmosException error) {
                            if (error.getStatusCode() != 404) throw error;
                        }
                        var notFound = assertThrows(CosmosException.class, () -> validationContainer.readItem(
                            product.get("id"), partitionKey(product), JsonNode.class).block());
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
            System.out.println("E2E PASS: " + (clientLevel ? "client-level" : "request-level")
                + ", buckets=" + useBuckets + ", query outcomes and cleanup verified.");
        }
    }
}
