package com.azure.cosmosdb.quickstarts;

import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.yaml.snakeyaml.Yaml;

import com.azure.cosmosdb.AppConfig;
import com.azure.cosmosdb.LoadExecutor;
import com.azure.cosmosdb.cosmos.CosmosDBService;

public class ThroughputBucketQuickstart {

    public static void main(String[] args) {
        // This is a placeholder for the Throughput Bucket Quickstart sample code.
        // The actual implementation will demonstrate how to use throughput buckets in
        // Azure Cosmos DB.
        System.out.println("Welcome to the Azure Cosmos DB Throughput Bucket Quickstart!");
        Yaml yaml = new Yaml();
        InputStream inputStream = ThroughputBucketQuickstart.class
                .getClassLoader()
                .getResourceAsStream("config.yml");
        AppConfig config = yaml.loadAs(inputStream, AppConfig.class);

        CosmosDBService cosmosDBService = new CosmosDBService(
                config.getDatabase().getEndpoint(),
                config.getDatabase().getDatabase(),
                config.getDatabase().getContainer());
        LoadExecutor loadExecutor = new LoadExecutor(cosmosDBService);

        // === User Prompts ===
        Scanner scanner = new Scanner(System.in);
        int uploadChoice = PromptForYesNo(scanner,
                "Do you want to upload data to Cosmos DB container? \nEnter 1 for Yes, 0 for No: ");
        if (uploadChoice == 1)
            loadExecutor.uploadProductsFromFile("data/products.json", 4);
        else
            System.out.println("[Info] Skipping data upload.");

        int tbChoice = PromptForYesNo(scanner, "Do you want to use throughput buckets? Enter 1 for Yes, 0 for No: ");
        boolean useThroughputBucket = tbChoice == 1;
        System.out.println(useThroughputBucket ? "[Info] Running with throughput buckets enabled."
                : "[Info] Running without throughput buckets.");
        scanner.close();
        RunWorkload(loadExecutor, config, useThroughputBucket);
    }

    private static void RunWorkload(LoadExecutor loadExecutor, AppConfig config, boolean useThroughputBucket) {
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        CompletableFuture<Void> statsTask = logStats(loadExecutor, stopRequested);
        long startTime = System.currentTimeMillis();
        long runDuration = config.getWorkload().getRunDurationInSecs() * 1000L;
        System.out.println("Running point reads and queries concurrently");
        while (System.currentTimeMillis() - startTime < runDuration) {
            loadExecutor.runPointReads(config.getWorkload().getTotalReads(),
                    config.getWorkload().getMaxReadConcurrency());
            loadExecutor.runQuery(config.getWorkload().getTotalQueries(), config.getWorkload().getMaxQueryConcurrency(),
                    useThroughputBucket);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("All concurrent operations completed.");
        stopRequested.set(true);
        statsTask.join();
    }

    // Helper for yes/no prompts
    private static int PromptForYesNo(Scanner scanner, String message) {
        int choice = -1;
        while (choice != 0 && choice != 1) {
            System.out.print(message);
            String input = scanner.nextLine();
            try {
                choice = Integer.parseInt(input);
                if (choice != 0 && choice != 1) {
                    System.out.println("Invalid input. Please enter 1 (Yes) or 0 (No).");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter 1 (Yes) or 0 (No).");
            }
        }
        return choice;
    }

    private static CompletableFuture<Void> logStats(LoadExecutor loadExecutor, AtomicBoolean stopRequested) {
        return CompletableFuture.runAsync(() -> {
            long totalReadsSucceeded = 0;
            long totalReadsThrottled = 0;
            long totalQueriesSucceeded = 0;
            long totalQueriesThrottled = 0;

            while (!stopRequested.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                long reads = loadExecutor.readsSucceeded.getAndSet(0);
                long readsThrottled = loadExecutor.readsThrottled.getAndSet(0);
                long queries = loadExecutor.queriesSucceeded.getAndSet(0);
                long queriesThrottled = loadExecutor.queriesThrottled.getAndSet(0);

                totalReadsSucceeded += reads;
                totalReadsThrottled += readsThrottled;
                totalQueriesSucceeded += queries;
                totalQueriesThrottled += queriesThrottled;

                System.out.println(String.format(
                        "Reads succeeded/sec: %d, Reads throttled/sec: %d, Queries succeeded/sec: %d, Queries throttled/sec: %d",
                        reads, readsThrottled, queries, queriesThrottled));
            };

            System.out.println(String.format(
                    "=== OPERATION SUMMARY ===%nReads: %d succeeded, %d throttled%nQueries: %d succeeded, %d throttled%n=========================",
                    totalReadsSucceeded, totalReadsThrottled, totalQueriesSucceeded, totalQueriesThrottled));
        });
    }

}
