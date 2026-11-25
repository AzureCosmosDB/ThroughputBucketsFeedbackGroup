import assert from "node:assert/strict";
import { test } from "node:test";
import { createProduct, readConfig, runQuickstart } from "./throughput-bucket-quickstart.js";

const env = { COSMOS_ENDPOINT: "https://example.documents.azure.com", COSMOS_DATABASE: "db", COSMOS_CONTAINER: "products" };

test("configuration rejects missing connection settings and invalid bucket/scope", () => {
  assert.throws(() => readConfig({}), /COSMOS_ENDPOINT/);
  for (const bucket of ["0", "6", "1.5", "abc", "01"]) {
    assert.throws(() => readConfig({ ...env, COSMOS_THROUGHPUT_BUCKET: bucket }), /integer from 1 to 5/);
  }
  assert.throws(() => readConfig({ ...env, COSMOS_BUCKET_SCOPE: "bulk" }), /request or client/);
  assert.equal(readConfig(env).bucket, 1);
});

test("isolated product uses actual simple, nested and hierarchical partition paths", () => {
  for (const paths of [["/id"], ["/tenant"], ["/tenant/id"], ["/tenant", "/id"], ["/tenant/id", "/sku"]]) {
    const { product, partitionKey } = createProduct(paths, "isolated");
    assert.equal(product.id, "isolated");
    assert.deepEqual(partitionKey, paths.map(() => "isolated"));
    for (const path of paths) {
      assert.equal(path.slice(1).split("/").reduce((value, key) => value[key], product), "isolated");
    }
  }
  assert.throws(() => createProduct([]), /partition key paths/);
  assert.throws(() => createProduct(["/id/nested"]), /Conflicting/);
  assert.throws(() => createProduct(["/__proto__/polluted"]), /Unsupported/);
});

function fakeContainer({ queryFailure = false, cleanupFailure = false, paths = ["/tenant/id"] } = {}) {
  const requests = [];
  let stored;
  let removed = false;
  const response = (resource, statusCode = 200) => ({ resource, statusCode, requestCharge: 1 });
  const container = {
    read: async () => response({ partitionKey: { paths } }),
    items: {
      create: async (product) => {
        stored = product;
        return response(product, 201);
      },
      query: (query, options) => {
        requests.push(["query", options]);
        let page = 0;
        return {
          hasMoreResults: () => page < 2,
          fetchNext: async () => {
            if (queryFailure) throw new Error("query failed");
            page++;
            return { ...response(undefined), resources: page === 1 ? [] : [stored] };
          },
        };
      },
    },
    item: (id, partitionKey) => {
      assert.deepEqual(partitionKey, paths.map(() => id));
      return {
        read: async (options = {}) => {
          requests.push(["read", options]);
          return response(removed ? undefined : stored, removed ? 404 : 200);
        },
        replace: async (product, options) => {
          requests.push(["replace", options]);
          stored = product;
          return response(product);
        },
        delete: async () => {
          if (cleanupFailure) throw new Error("cleanup failed");
          removed = true;
          return response(undefined, 204);
        },
      };
    },
  };
  return { container, requests, removed: () => removed, stored: () => stored };
}

test("request scope tags reads, query and replace, drains empty pages and cleans up", async () => {
  const fake = fakeContainer();
  await runQuickstart(fake.container, { bucket: 3, scope: "request" });
  assert.equal(fake.removed(), true);
  assert.equal(fake.requests[0][1].throughputBucket, undefined);
  for (const [, options] of fake.requests.slice(1, -1)) assert.equal(options.throughputBucket, 3);
  assert.equal(fake.requests.at(-1)[1].throughputBucket, undefined);
});

test("client scope leaves request options unset to inherit the client bucket", async () => {
  const fake = fakeContainer();
  await runQuickstart(fake.container, { bucket: 1, scope: "client" });
  for (const [, options] of fake.requests) assert.equal(options.throughputBucket, undefined);
});

test("replacement preserves a partition key field even when it is Price", async () => {
  const fake = fakeContainer({ paths: ["/Price"] });
  await runQuickstart(fake.container, { bucket: 1, scope: "request" });
  assert.equal(fake.stored().Price, fake.stored().id);
  assert.equal(fake.stored()._Price, 20);
});

test("operation failure still cleans up and is not reported as success", async () => {
  const fake = fakeContainer({ queryFailure: true });
  await assert.rejects(runQuickstart(fake.container, { bucket: 1, scope: "request" }), /query failed/);
  assert.equal(fake.removed(), true);
});

test("operation and cleanup failures are both surfaced", async () => {
  const fake = fakeContainer({ queryFailure: true, cleanupFailure: true });
  await assert.rejects(runQuickstart(fake.container, { bucket: 1, scope: "request" }), (error) => {
    assert.ok(error instanceof AggregateError);
    assert.deepEqual(error.errors.map((failure) => failure.message), ["query failed", "cleanup failed"]);
    return true;
  });
});
