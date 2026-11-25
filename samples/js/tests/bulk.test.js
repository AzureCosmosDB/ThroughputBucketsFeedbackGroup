import assert from "node:assert/strict";
import { test } from "node:test";
import { executeBulk as clientLevelBulk } from "../client-level-bulk.js";
import { executeBulk as requestLevelBulk } from "../request-level-bulk.js";

test("client-level bulk leaves request options unset to inherit the client bucket", async () => {
  const operations = [{ operationType: "Create", resourceBody: { id: "product" } }];
  const container = {
    items: {
      executeBulkOperations: async (...args) => {
        assert.deepEqual(args, [operations]);
        return [];
      },
    },
  };
  await clientLevelBulk(container, operations);
});

test("request-level bulk assigns the override to the bulk call, not individual operations", async () => {
  const operations = [{ operationType: "Create", resourceBody: { id: "product" } }];
  const container = {
    items: {
      executeBulkOperations: async (...args) => {
        assert.deepEqual(args, [operations, { throughputBucket: 2 }]);
        assert.equal(operations[0].throughputBucket, undefined);
        return [];
      },
    },
  };
  await requestLevelBulk(container, operations, 2);
});
