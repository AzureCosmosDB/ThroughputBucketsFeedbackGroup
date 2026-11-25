package com.azure.cosmos.samples;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.ThroughputControlGroupConfig;
import com.azure.cosmos.ThroughputControlGroupConfigBuilder;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.PartitionKeyBuilder;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ThroughputBucketQuickstart {
    static final String GROUP_NAME = "quickstartBucket";

    record Config(String endpoint, String database, String container, int bucket, String scope) {
        static Config fromEnvironment(Map<String, String> env) {
            String bucket = env.getOrDefault("COSMOS_THROUGHPUT_BUCKET", "1");
            if (!bucket.matches("[1-5]")) {
                throw new IllegalArgumentException("COSMOS_THROUGHPUT_BUCKET must be an integer from 1 to 5.");
            }
            String scope = env.getOrDefault("COSMOS_BUCKET_SCOPE", "request");
            if (!scope.equals("request") && !scope.equals("client")) {
                throw new IllegalArgumentException("COSMOS_BUCKET_SCOPE must be request or client.");
            }
            return new Config(required(env, "COSMOS_ENDPOINT"), required(env, "COSMOS_DATABASE"),
                required(env, "COSMOS_CONTAINER"), Integer.parseInt(bucket), scope);
        }

        private static String required(Map<String, String> env, String name) {
            String value = env.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Set " + name + " before running the sample.");
            }
            return value.trim();
        }

        ThroughputControlGroupConfig groupConfig() {
            return new ThroughputControlGroupConfigBuilder()
                .groupName(GROUP_NAME)
                .throughputBucket(bucket)
                .defaultControlGroup(scope.equals("client"))
                .build();
        }

        CosmosItemRequestOptions itemOptions() {
            CosmosItemRequestOptions options = new CosmosItemRequestOptions();
            if (scope.equals("request")) {
                options.setThroughputControlGroupName(GROUP_NAME);
            }
            return options;
        }

        CosmosQueryRequestOptions queryOptions(PartitionKey partitionKey) {
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            options.setPartitionKey(partitionKey);
            if (scope.equals("request")) {
                options.setThroughputControlGroupName(GROUP_NAME);
            }
            return options;
        }
    }

    record Product(ObjectNode document, PartitionKey partitionKey) {}

    static String updateField(List<String> paths) {
        String field = "Price";
        List<String> roots = paths.stream().map(path -> path.substring(1).split("/")[0]).toList();
        while (roots.contains(field)) {
            field = "_" + field;
        }
        return field;
    }

    static Product createProduct(List<String> paths, String id) {
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("The container must define partition key paths.");
        }
        ObjectNode document = JsonNodeFactory.instance.objectNode();
        document.put("id", id);
        document.put("Type", "Accessories");
        document.put("Name", "Throughput bucket quickstart");
        document.put("Price", 10);
        PartitionKeyBuilder partitionKey = new PartitionKeyBuilder();
        for (String path : paths) {
            if (path == null || !path.matches("/[^/]+(?:/[^/]+)*")) {
                throw new IllegalArgumentException("Unsupported partition key path: " + path);
            }
            String[] segments = path.substring(1).split("/");
            ObjectNode object = document;
            for (int i = 0; i < segments.length - 1; i++) {
                JsonNode child = object.get(segments[i]);
                if (child == null) {
                    object = object.putObject(segments[i]);
                } else if (child instanceof ObjectNode nested) {
                    object = nested;
                } else {
                    throw new IllegalArgumentException("Conflicting partition key path: " + path);
                }
            }
            object.put(segments[segments.length - 1], id);
            partitionKey.add(id);
        }
        return new Product(document, partitionKey.build());
    }

    public static void main(String[] args) {
        try {
            Config config = Config.fromEnvironment(System.getenv());
            try (CosmosAsyncClient client = new CosmosClientBuilder()
                .endpoint(config.endpoint())
                .credential(new DefaultAzureCredentialBuilder().build())
                .directMode()
                .buildAsyncClient()) {
                CosmosAsyncContainer container = client.getDatabase(config.database()).getContainer(config.container());
                runQuickstart(container, config);
            }
        } catch (RuntimeException error) {
            if (error instanceof CosmosException cosmosError) {
                System.err.println("Cosmos status=" + cosmosError.getStatusCode()
                    + ", diagnostics=" + cosmosError.getDiagnostics());
            }
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void runQuickstart(CosmosAsyncContainer container, Config config) {
        List<String> paths = container.read().block().getProperties().getPartitionKeyDefinition().getPaths();
        Product product = createProduct(paths, "throughput-bucket-java-" + UUID.randomUUID());
        String updateField = updateField(paths);
        String id = product.document().get("id").asText();
        PartitionKey partitionKey = product.partitionKey();
        container.enableServerThroughputControlGroup(config.groupConfig());
        System.out.println("Isolated product: " + id);
        boolean created = false;
        RuntimeException failure = null;
        try {
            // In client scope the default group also applies to create and baseline read.
            logResponse("Create isolated product", container.createItem(product.document(), partitionKey,
                new CosmosItemRequestOptions()).block());
            created = true;
            CosmosItemResponse<ObjectNode> baseline = container.readItem(id, partitionKey, ObjectNode.class).block();
            require(baseline.getItem().get("id").asText().equals(id), "Baseline read returned the wrong product.");
            logResponse("Read without request override", baseline);

            CosmosItemResponse<ObjectNode> read = container.readItem(id, partitionKey, config.itemOptions(),
                ObjectNode.class).block();
            require(read.getItem().get("id").asText().equals(id), "Bucket read returned the wrong product.");
            logResponse("Read using bucket " + config.bucket(), read);

            SqlQuerySpec query = new SqlQuerySpec("SELECT * FROM c WHERE c.id = @id",
                List.of(new SqlParameter("@id", id)));
            List<ObjectNode> results = container.queryItems(query, config.queryOptions(partitionKey), ObjectNode.class)
                .byPage(1)
                .doOnNext(page -> System.out.println("Query page: RU=" + page.getRequestCharge()))
                .flatMapIterable(page -> page.getResults())
                .collectList().block();
            require(results.size() == 1 && results.get(0).get("id").asText().equals(id),
                "The query must return exactly the isolated product.");

            ObjectNode updated = read.getItem().deepCopy();
            updated.put(updateField, 20);
            logResponse("Replace using bucket", container.replaceItem(updated, id, partitionKey,
                config.itemOptions()).block());
            CosmosItemResponse<ObjectNode> verified = container.readItem(id, partitionKey, config.itemOptions(),
                ObjectNode.class).block();
            require(verified.getItem().get(updateField).asInt() == 20, "The replacement must be persisted.");
            logResponse("Verify replacement", verified);
        } catch (RuntimeException error) {
            failure = error;
            throw error;
        } finally {
            if (created) {
                try {
                    // No request override during cleanup, even if a request bucket is throttled.
                    logResponse("Delete isolated product", container.deleteItem(id, partitionKey,
                        new CosmosItemRequestOptions()).block());
                    verifyDeleted(container, id, partitionKey);
                } catch (RuntimeException cleanupError) {
                    if (failure != null) {
                        failure.addSuppressed(cleanupError);
                    } else {
                        throw cleanupError;
                    }
                }
            }
        }
        System.out.println("E2E PASS: " + config.scope() + " scope, bucket " + config.bucket() + "; product removed.");
    }

    private static void verifyDeleted(CosmosAsyncContainer container, String id, PartitionKey partitionKey) {
        try {
            container.readItem(id, partitionKey, ObjectNode.class).block();
        } catch (CosmosException error) {
            if (error.getStatusCode() != 404) {
                System.err.println("Cleanup verification diagnostics: " + error.getDiagnostics());
                throw error;
            }
            System.out.println("Verified deletion: status=404");
            return;
        }
        throw new IllegalStateException("The isolated product must be deleted.");
    }

    private static void logResponse(String operation, CosmosItemResponse<?> response) {
        System.out.println(operation + ": status=" + response.getStatusCode() + ", RU=" + response.getRequestCharge());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
