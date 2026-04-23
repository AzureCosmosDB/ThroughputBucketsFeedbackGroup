package com.azure.cosmosdb;

public class AppConfig {

    private Database database;
    private Workload workload;

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public Workload getWorkload() {
        return workload;
    }

    public void setWorkload(Workload workload) {
        this.workload = workload;
    }

    public static class Database {
        private String endpoint;
        private String database;
        private String container;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getContainer() {
            return container;
        }

        public void setContainer(String container) {
            this.container = container;
        }
    }

    public static class Workload {
        private int totalReads;
        private int totalQueries;
        private int totalCreates;
        private int maxReadConcurrency;
        private int maxQueryConcurrency;
        private int maxBulkInsertConcurrency;
        private int runDurationInSecs;

        public int getTotalReads() {
            return totalReads;
        }

        public void setTotalReads(int totalReads) {
            this.totalReads = totalReads;
        }

        public int getTotalQueries() {
            return totalQueries;
        }

        public void setTotalQueries(int totalQueries) {
            this.totalQueries = totalQueries;
        }

        public int getTotalCreates() {
            return totalCreates;
        }

        public void setTotalCreates(int totalCreates) {
            this.totalCreates = totalCreates;
        }

        public int getMaxReadConcurrency() {
            return maxReadConcurrency;
        }

        public void setMaxReadConcurrency(int maxReadConcurrency) {
            this.maxReadConcurrency = maxReadConcurrency;
        }

        public int getMaxQueryConcurrency() {
            return maxQueryConcurrency;
        }

        public void setMaxQueryConcurrency(int maxQueryConcurrency) {
            this.maxQueryConcurrency = maxQueryConcurrency;
        }

        public int getMaxBulkInsertConcurrency() {
            return maxBulkInsertConcurrency;
        }

        public void setMaxBulkInsertConcurrency(int maxBulkInsertConcurrency) {
            this.maxBulkInsertConcurrency = maxBulkInsertConcurrency;
        }

        public int getRunDurationInSecs() {
            return runDurationInSecs;
        }

        public void setRunDurationInSecs(int runDurationInSecs) {
            this.runDurationInSecs = runDurationInSecs;
        }
    }
}
