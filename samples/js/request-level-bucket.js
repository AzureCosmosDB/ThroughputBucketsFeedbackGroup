import { pathToFileURL } from "node:url";
import { CosmosClient } from "@azure/cosmos";
import { DefaultAzureCredential } from "@azure/identity";
import { printTenantStats, queryTenant } from "./workload-support.js";

export function createClient() {
  return new CosmosClient({
    endpoint: process.env.COSMOS_ENDPOINT,
    aadCredentials: new DefaultAzureCredential(),
    connectionPolicy: { retryOptions: { maxRetryAttemptCount: 0 } },
  });
}

export async function runScenario(container, useBuckets, standardTenant, premiumTenant, queryCount) {
  // One shared client. Only standard-tenant requests select bucket 2.
  const standardRequestOptions = useBuckets ? { throughputBucket: 2 } : {};
  const premiumRequestOptions = {};
  const [standard, premium] = await Promise.all([
    queryTenant(container, standardTenant, standardRequestOptions, queryCount),
    queryTenant(container, premiumTenant, premiumRequestOptions, queryCount),
  ]);
  return { standard, premium };
}

async function main() {
  const client = createClient();
  try {
    const container = client.database(process.env.COSMOS_DATABASE).container(process.env.COSMOS_CONTAINER);
    printTenantStats(await runScenario(container,
      (process.env.USE_THROUGHPUT_BUCKETS ?? "true") === "true",
      process.env.COSMOS_STANDARD_TENANT ?? "tenant_1",
      process.env.COSMOS_PREMIUM_TENANT ?? "tenant_7",
      Number(process.env.COSMOS_QUERY_COUNT ?? "20")));
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
