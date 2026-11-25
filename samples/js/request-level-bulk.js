import { pathToFileURL } from "node:url";
import { CosmosClient } from "@azure/cosmos";
import { DefaultAzureCredential } from "@azure/identity";
import { createOperations, printResults } from "./bulk-sample-support.js";

export function createClient() {
  return new CosmosClient({
    endpoint: process.env.COSMOS_ENDPOINT,
    aadCredentials: new DefaultAzureCredential(),
  });
}

export function executeBulk(container, operations, bucket) {
  // Only this bulk call uses the bucket. There is no client-level bucket.
  return container.items.executeBulkOperations(operations, { throughputBucket: bucket });
}

async function main() {
  const client = createClient();
  try {
    const container = client.database(process.env.COSMOS_DATABASE).container(process.env.COSMOS_CONTAINER);
    printResults(await executeBulk(
      container, createOperations("request"), Number(process.env.COSMOS_THROUGHPUT_BUCKET ?? "1")));
  } finally {
    client.dispose();
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
