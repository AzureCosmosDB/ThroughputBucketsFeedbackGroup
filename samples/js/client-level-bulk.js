import { pathToFileURL } from "node:url";
import { CosmosClient } from "@azure/cosmos";
import { DefaultAzureCredential } from "@azure/identity";
import { createOperations, printResults } from "./bulk-sample-support.js";

export function createClient(bucket) {
  return new CosmosClient({
    endpoint: process.env.COSMOS_ENDPOINT,
    aadCredentials: new DefaultAzureCredential(),
    throughputBucket: bucket,
  });
}

export function executeBulk(container, operations) {
  // No request override: the bulk call inherits the client bucket.
  return container.items.executeBulkOperations(operations);
}

async function main() {
  const client = createClient(Number(process.env.COSMOS_THROUGHPUT_BUCKET ?? "1"));
  try {
    const container = client.database(process.env.COSMOS_DATABASE).container(process.env.COSMOS_CONTAINER);
    printResults(await executeBulk(container, createOperations("client")));
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
