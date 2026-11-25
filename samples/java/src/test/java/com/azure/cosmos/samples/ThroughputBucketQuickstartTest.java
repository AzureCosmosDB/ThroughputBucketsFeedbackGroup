package com.azure.cosmos.samples;

import com.azure.cosmos.models.PartitionKeyBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ThroughputBucketQuickstartTest {
    private static final Map<String, String> ENV = Map.of(
        "COSMOS_ENDPOINT", "https://example.documents.azure.com",
        "COSMOS_DATABASE", "db", "COSMOS_CONTAINER", "products");

    @Test
    void rejectsMissingConnectionSettingsAndInvalidBucketOrScope() {
        assertThrows(IllegalArgumentException.class,
            () -> ThroughputBucketQuickstart.Config.fromEnvironment(Map.of()));
        for (String bucket : List.of("0", "6", "1.5", "abc", "01")) {
            Map<String, String> env = new HashMap<>(ENV);
            env.put("COSMOS_THROUGHPUT_BUCKET", bucket);
            assertThrows(IllegalArgumentException.class, () -> ThroughputBucketQuickstart.Config.fromEnvironment(env));
        }
        Map<String, String> env = new HashMap<>(ENV);
        env.put("COSMOS_BUCKET_SCOPE", "bulk");
        assertThrows(IllegalArgumentException.class, () -> ThroughputBucketQuickstart.Config.fromEnvironment(env));
    }

    @Test
    void requestScopeUsesAnExplicitGroupWithoutMakingItDefault() {
        Map<String, String> env = new HashMap<>(ENV);
        env.put("COSMOS_THROUGHPUT_BUCKET", "3");
        var config = ThroughputBucketQuickstart.Config.fromEnvironment(env);
        assertEquals(3, config.groupConfig().getThroughputBucket());
        assertFalse(config.groupConfig().isDefault());
        assertEquals(ThroughputBucketQuickstart.GROUP_NAME, config.itemOptions().getThroughputControlGroupName());
        assertEquals(ThroughputBucketQuickstart.GROUP_NAME,
            config.queryOptions(new PartitionKeyBuilder().add("isolated").build()).getThroughputControlGroupName());
        assertNotSame(config.itemOptions(), config.itemOptions());
    }

    @Test
    void clientScopeUsesADefaultContainerGroupAndNoRequestOverride() {
        Map<String, String> env = new HashMap<>(ENV);
        env.put("COSMOS_BUCKET_SCOPE", "client");
        var config = ThroughputBucketQuickstart.Config.fromEnvironment(env);
        assertTrue(config.groupConfig().isDefault());
        assertNull(config.itemOptions().getThroughputControlGroupName());
        assertNull(config.queryOptions(new PartitionKeyBuilder().add("isolated").build())
            .getThroughputControlGroupName());
    }

    @Test
    void isolatedProductSupportsSimpleNestedAndHierarchicalPartitionPaths() {
        for (List<String> paths : List.of(List.of("/id"), List.of("/tenant"), List.of("/tenant/id"),
            List.of("/tenant", "/id"), List.of("/tenant/id", "/sku"))) {
            var product = ThroughputBucketQuickstart.createProduct(paths, "isolated");
            assertEquals("isolated", product.document().get("id").asText());
            PartitionKeyBuilder expected = new PartitionKeyBuilder();
            for (String path : paths) {
                assertEquals("isolated", product.document().at(path).asText());
                expected.add("isolated");
            }
            assertEquals(expected.build(), product.partitionKey());
        }
        assertThrows(IllegalArgumentException.class,
            () -> ThroughputBucketQuickstart.createProduct(List.of(), "isolated"));
        assertThrows(IllegalArgumentException.class,
            () -> ThroughputBucketQuickstart.createProduct(List.of("/id/nested"), "isolated"));
    }

    @Test
    void replacementFieldNeverChangesAPartitionKeyRoot() {
        assertEquals("_Price", ThroughputBucketQuickstart.updateField(List.of("/Price")));
        assertEquals("__Price", ThroughputBucketQuickstart.updateField(List.of("/Price/nested", "/_Price")));
    }
}
