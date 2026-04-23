package com.azure.cosmosdb;

import com.azure.cosmosdb.cosmos.CosmosDBService;
import com.azure.cosmosdb.models.Product;

import reactor.core.publisher.Flux;

import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.util.CosmosPagedFlux;

import java.io.IOException;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class LoadExecutor {
    private final CosmosDBService dbService;
    public final AtomicLong readsSucceeded = new AtomicLong();
    public final AtomicLong readsThrottled = new AtomicLong();
    public final AtomicLong queriesSucceeded = new AtomicLong();
    public final AtomicLong queriesThrottled = new AtomicLong();
    public final AtomicLong createsSucceeded = new AtomicLong();
    public final AtomicLong createsThrottled = new AtomicLong();

    public LoadExecutor(CosmosDBService dbService) {
        this.dbService = dbService;
    }

    private <T> void processWork(int totalItems, int maxConcurrency, java.util.function.IntFunction<T> itemFactory,
            java.util.function.Consumer<T> workerFunc) {
        Queue<T> queue = new ConcurrentLinkedQueue<>();
        for (int i = 1; i <= totalItems; i++) {
            queue.add(itemFactory.apply(i));
        }
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency);
        for (int w = 0; w < maxConcurrency; w++) {
            executor.submit(() -> {
                T item;
                while ((item = queue.poll()) != null) {
                    workerFunc.accept(item);
                }
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void runPointReads(int totalReads, int maxConcurrency) {
        processWork(totalReads, maxConcurrency,
                i -> String.valueOf(i),
                id -> {
                    try {
                        dbService.readItem(id, Product.class);
                        readsSucceeded.incrementAndGet();
                    } catch (CosmosException ex) {
                        if (ex.getStatusCode() == 429) {
                            readsThrottled.incrementAndGet();
                            System.out.println("Read throttled with id: " + id + " - RetryAfter: "
                                    + ex.getRetryAfterDuration().toMillis() + " ms");
                        } else {
                            System.out.println("[Error] " + id + " - " + ex.getMessage());
                        }
                    } catch (Exception ex) {
                        System.out.println("[Error] " + id + " - " + ex.getMessage());
                    }
                });
    }

    public void runQuery(int totalQueries, int maxConcurrency, boolean useThroughputBucket) {
        CosmosQueryRequestOptions queryRequestOptions = new CosmosQueryRequestOptions();
        if (useThroughputBucket) {
            queryRequestOptions
                    .setThroughputControlGroupName(dbService.getThroughputControlGroupConfig().getGroupName());
        }
        processWork(totalQueries, maxConcurrency,
                i -> String.valueOf(i),
                id -> {
                    try {
                        String sql = "SELECT * FROM c WHERE c.id = '" + id + "'";

                        CosmosPagedFlux<Product> feedResponse = dbService
                                .queryItems(sql, queryRequestOptions, Product.class);

                        feedResponse.byPage().flatMap(page -> {
                            if (page.getResults().size() > 0) {
                                queriesSucceeded.incrementAndGet();
                            }
                            return Flux.empty();
                        }).subscribe();
                    } catch (CosmosException ex) {
                        if (ex.getStatusCode() == 429) {
                            queriesThrottled.incrementAndGet();
                            System.out.println("Query throttled with pk: " + id + " - RetryAfter: "
                                    + ex.getRetryAfterDuration().toMillis() + " ms");
                        } else {
                            System.out.println("[Error] " + id + " - " + ex.getMessage());
                        }
                    } catch (Exception ex) {
                        System.out.println("[Error] " + id + " - " + ex.getMessage());
                    }
                });
    }

    // public void runBulkInsert(int totalDocs, int maxConcurrency) {
    // CompletableFuture<Integer> maxIdFuture = dbService.getMaxIdAsync();
    // maxIdFuture.thenAccept(maxId -> {
    // System.out.println("Max Id found: " + maxId + ". Starting bulk insert from "
    // + (maxId + 1) + " to "
    // + (maxId + totalDocs) + ".");
    // processWork(totalDocs, maxConcurrency,
    // i -> Product.generateProduct(maxId + i),
    // product -> {
    // try {
    // dbService.createBulkItem(product);
    // createsSucceeded.incrementAndGet();
    // } catch (CosmosException ex) {
    // if (ex.getStatusCode() == 429) {
    // System.out.println("Create throttled with id: " + product.getId() + " -
    // RetryAfter: "
    // + ex.getRetryAfterDuration().toMillis() + " ms");
    // createsThrottled.incrementAndGet();
    // } else {
    // System.out.println("Error creating product " + product.getId() + ": " +
    // ex.getMessage());
    // createsThrottled.incrementAndGet();
    // }
    // } catch (Exception ex) {
    // System.out.println("Error creating product " + product.getId() + ": " +
    // ex.getMessage());
    // createsThrottled.incrementAndGet();
    // }
    // });
    // System.out.println("Bulk insert completed for " + totalDocs + " documents.");
    // }

    public void uploadProductsFromFile(String filePath, int maxConcurrency) {
        System.out.println("Reading products from " + filePath + " ...");
        List<Product> products = Collections.emptyList();
        try {
            String json = new String(Files.readAllBytes(Paths.get(filePath)));
            // Use your preferred JSON library to deserialize
            // products = new ObjectMapper().readValue(json, new
            // TypeReference<List<Product>>(){});
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
            return;
        }
        if (products == null || products.isEmpty()) {
            System.out.println("No products found in file.");
            return;
        }
        System.out.println("Uploading " + products.size() + " products into Cosmos DB ...");
        Queue<Product> queue = new ConcurrentLinkedQueue<>(products);
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency);
        for (int w = 0; w < maxConcurrency; w++) {
            executor.submit(() -> {
                Product product;
                while ((product = queue.poll()) != null) {
                    try {
                        dbService.createItem(product);
                        createsSucceeded.incrementAndGet();
                    } catch (CosmosException ex) {
                        if (ex.getStatusCode() == 429) {
                            System.out.println("Create throttled with id: " + product.getId() + " - RetryAfter: "
                                    + ex.getRetryAfterDuration().toMillis() + " ms");
                            createsThrottled.incrementAndGet();
                        } else {
                            System.out.println("Error creating product " + product.getId() + ": " + ex.getMessage());
                            createsThrottled.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        System.out.println("Error creating product " + product.getId() + ": " + ex.getMessage());
                        createsThrottled.incrementAndGet();
                    }
                }
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("File upload completed for " + products.size() + " products.");
    }
}
