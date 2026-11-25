import { pathToFileURL } from "node:url";
import { CosmosClient } from "@azure/cosmos";
import { DefaultAzureCredential } from "@azure/identity";
import { countBulkResults, createInventoryOperations, printStats, printTenantStats, queryTenant } from "./workload-support.js";

export function createClients(useBuckets) {
  const credential = new DefaultAzureCredential();
  const connectionPolicy = { retryOptions: { maxRetryAttemptCount: 0 } };
  const inventoryClient = new CosmosClient({
    endpoint: process.env.COSMOS_ENDPOINT,
    aadCredentials: credential,
    connectionPolicy,
    throughputBucket: useBuckets ? 1 : undefined,
  });
  const customerClient = new CosmosClient({
    endpoint: process.env.COSMOS_ENDPOINT,
    aadCredentials: credential,
    connectionPolicy,
  });
  return { inventoryClient, customerClient };
}

export async function runScenario(inventoryContainer, customerContainer, operations,
  standardTenant, premiumTenant, queryCount) {
  // Bulk writes inherit bucket 1. Customer requests use the separate, unbucketed client.
  const [inventory, standard, premium] = await Promise.all([
    inventoryContainer.items.executeBulkOperations(operations).then(countBulkResults),
    queryTenant(customerContainer, standardTenant, {}, queryCount),
    queryTenant(customerContainer, premiumTenant, {}, queryCount),
  ]);
  return { inventory, customers: { standard, premium } };
}

async function main() {
  const { inventoryClient, customerClient } = createClients(
    (process.env.USE_THROUGHPUT_BUCKETS ?? "true") === "true");
  try {
    const database = process.env.COSMOS_DATABASE;
    const container = process.env.COSMOS_CONTAINER;
    const standardTenant = process.env.COSMOS_STANDARD_TENANT ?? "tenant_1";
    const premiumTenant = process.env.COSMOS_PREMIUM_TENANT ?? "tenant_7";
    const results = await runScenario(inventoryClient.database(database).container(container),
      customerClient.database(database).container(container),
      createInventoryOperations(Number(process.env.COSMOS_BULK_ITEMS ?? "10"), standardTenant, premiumTenant),
      standardTenant, premiumTenant, Number(process.env.COSMOS_QUERY_COUNT ?? "20"));
    printStats("Inventory bulk writes", results.inventory);
    printTenantStats(results.customers);
  } finally {
    inventoryClient.dispose();
    customerClient.dispose();
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
