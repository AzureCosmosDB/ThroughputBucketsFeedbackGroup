import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { CosmosClient } from "@azure/cosmos";
import { DefaultAzureCredential } from "@azure/identity";
import { createClient, runScenario as requestScenario } from "../request-level-bucket.js";
import { createClients, runScenario as inventoryScenario } from "../client-level-bucket.js";
import { createInventoryOperations } from "../workload-support.js";

const validator = new CosmosClient({
  endpoint: process.env.COSMOS_ENDPOINT,
  aadCredentials: new DefaultAzureCredential(),
});
const validationContainer = validator.database(process.env.COSMOS_DATABASE).container(process.env.COSMOS_CONTAINER);
const containerFor = (client) => client.database(process.env.COSMOS_DATABASE).container(process.env.COSMOS_CONTAINER);

function observeQueries(container, observations) {
  return {
    items: {
      query: (query, options) => ({
        fetchAll: async () => {
          const response = await container.items.query(query, options).fetchAll();
          assert.ok(response.resources.length > 0, "Queries must read the seeded tenant products.");
          observations.push({ tenant: query.parameters[0].value, bucket: options.throughputBucket });
          return response;
        },
      }),
    },
  };
}

async function verifyScenario(scenario, useBuckets) {
  const prefix = `e2e-${randomUUID()}`;
  const standardTenant = `${prefix}-standard`;
  const premiumTenant = `${prefix}-premium`;
  const seeds = [standardTenant, premiumTenant].map((tenant) => ({
    id: `seed-${randomUUID()}`, tenant, name: "Seed product",
  }));
  const inventoryOperations = scenario === "client-level"
    ? createInventoryOperations(4, standardTenant, premiumTenant) : [];
  const products = [...seeds, ...inventoryOperations.map((operation) => operation.resourceBody)];
  console.log(`E2E items: ${JSON.stringify(products.map(({ id, tenant }) => ({ id, tenant })))}`);
  const observations = [];
  const clients = scenario === "request-level" ? { customerClient: createClient() } : createClients(useBuckets);
  let failure;
  try {
    for (const seed of seeds) await validationContainer.items.create(seed);
    const customers = observeQueries(containerFor(clients.customerClient), observations);
    let customerStats;
    if (scenario === "request-level") {
      customerStats = await requestScenario(customers, useBuckets, standardTenant, premiumTenant, 2);
    } else {
      const results = await inventoryScenario(containerFor(clients.inventoryClient), customers,
        inventoryOperations, standardTenant, premiumTenant, 2);
      customerStats = results.customers;
      assert.equal(results.inventory.succeeded + results.inventory.throttled, inventoryOperations.length);
      let created = 0;
      for (const { resourceBody: product } of inventoryOperations) {
        const read = await validationContainer.item(product.id, [product.tenant, product.id]).read();
        if (read.statusCode === 200) {
          created++;
          assert.equal(read.resource.name, product.name);
        } else {
          assert.equal(read.statusCode, 404);
        }
      }
      assert.equal(created, results.inventory.succeeded);
    }
    for (const stats of [customerStats.standard, customerStats.premium]) {
      assert.equal(stats.succeeded + stats.throttled, 2);
    }
    assert.equal(observations.length, customerStats.standard.succeeded + customerStats.premium.succeeded);
    for (const observation of observations) {
      const expected = scenario === "request-level" && useBuckets && observation.tenant === standardTenant
        ? 2 : undefined;
      assert.equal(observation.bucket, expected);
    }
  } catch (error) {
    failure = error;
    throw error;
  } finally {
    const cleanupErrors = [];
    for (const product of products) {
      try {
        const item = validationContainer.item(product.id, [product.tenant, product.id]);
        try {
          await item.delete();
        } catch (error) {
          if (error.code !== 404) throw error;
        }
        assert.equal((await item.read()).statusCode, 404);
      } catch (error) {
        cleanupErrors.push(error);
      }
    }
    clients.inventoryClient?.dispose();
    clients.customerClient.dispose();
    if (cleanupErrors.length) {
      throw new AggregateError(failure ? [failure, ...cleanupErrors] : cleanupErrors, "Scenario cleanup failed");
    }
  }
  console.log(`E2E PASS: ${scenario}, buckets=${useBuckets}, query outcomes and cleanup verified.`);
}

try {
  assert.deepEqual((await validationContainer.read()).resource.partitionKey.paths, ["/tenant", "/id"]);
  for (const scenario of ["request-level", "client-level"]) {
    for (const useBuckets of [false, true]) await verifyScenario(scenario, useBuckets);
  }
} finally {
  validator.dispose();
}
