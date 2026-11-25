import assert from "node:assert/strict";
import { createClient as clientBucketClient, executeBulk as clientLevelBulk } from "../client-level-bulk.js";
import { createClient as requestBucketClient, executeBulk as requestLevelBulk } from "../request-level-bulk.js";
import { createOperations } from "../bulk-sample-support.js";

async function verifyScenario(name, client, execute) {
  const container = client.database(process.env.COSMOS_DATABASE).container(process.env.COSMOS_CONTAINER);
  const operations = createOperations(`e2e-${name}`);
  let failure;
  try {
    const { resource } = await container.read();
    assert.deepEqual(resource.partitionKey.paths, ["/tenant", "/id"], "Use the documented sample partition keys.");
    const results = await execute(container, operations);
    assert.equal(results.length, operations.length);
    for (const result of results) {
      assert.equal(result.error, undefined);
      assert.equal(result.response?.statusCode, 201);
    }
    for (const { resourceBody: product } of operations) {
      const read = await container.item(product.id, [product.tenant, product.id]).read();
      assert.equal(read.statusCode, 200);
      assert.equal(read.resource?.name, product.name);
    }
  } catch (error) {
    failure = error;
    throw error;
  } finally {
    const cleanupErrors = [];
    for (const { resourceBody: product } of operations) {
      try {
        const item = container.item(product.id, [product.tenant, product.id]);
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
    if (cleanupErrors.length) {
      client.dispose();
      throw new AggregateError(failure ? [failure, ...cleanupErrors] : cleanupErrors, "E2E cleanup failed");
    }
    client.dispose();
  }
  console.log(`E2E PASS: ${name}, ${operations.length} bulk creates, readback and cleanup verified.`);
}

const bucket = Number(process.env.COSMOS_THROUGHPUT_BUCKET ?? "1");
await verifyScenario("client-level", clientBucketClient(bucket), clientLevelBulk);
await verifyScenario("request-level", requestBucketClient(),
  (container, operations) => requestLevelBulk(container, operations, bucket));
