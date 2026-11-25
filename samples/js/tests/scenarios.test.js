import assert from "node:assert/strict";
import { test } from "node:test";
import { runScenario as requestScenario } from "../request-level-bucket.js";
import { runScenario as inventoryScenario } from "../client-level-bucket.js";
import { countBulkResults, queryTenant } from "../workload-support.js";

function queryContainer(calls, fetchAll = async () => ({ resources: [] })) {
  return {
    items: {
      query: (query, options) => {
        calls.push({ tenant: query.parameters[0].value, query, options });
        return { fetchAll };
      },
    },
  };
}

test("request-level scenario buckets only standard requests on the shared client", async () => {
  const calls = [];
  const results = await requestScenario(queryContainer(calls), true, "standard", "premium", 2);
  assert.deepEqual(results, {
    standard: { succeeded: 2, throttled: 0 },
    premium: { succeeded: 2, throttled: 0 },
  });
  for (const call of calls) {
    assert.equal(call.options.throughputBucket, call.tenant === "standard" ? 2 : undefined);
    assert.equal(call.query.query, "SELECT TOP 10 c.id FROM c WHERE c.tenant = @tenant");
  }
});

test("disabled request-level scenario assigns no bucket to either tenant", async () => {
  const calls = [];
  await requestScenario(queryContainer(calls), false, "standard", "premium", 1);
  assert.equal(calls.length, 2);
  assert.ok(calls.every((call) => call.options.throughputBucket === undefined));
});

test("client-level scenario runs bulk and unbucketed customer queries concurrently on separate containers", async () => {
  const calls = [];
  let releaseBulk;
  const inventory = {
    items: {
      executeBulkOperations: (...args) => {
        assert.equal(args.length, 1, "The inventory client default must not become a bulk request option.");
        return new Promise((resolve) => {
          releaseBulk = () => resolve([{ response: { statusCode: 201 } }]);
        });
      },
    },
  };
  const customers = queryContainer(calls, async () => {
    if (calls.length === 2) releaseBulk();
    return { resources: [] };
  });
  const results = await inventoryScenario(inventory, customers, [{}], "standard", "premium", 1);
  assert.deepEqual(results.inventory, { succeeded: 1, throttled: 0 });
  assert.ok(calls.every((call) => call.options.throughputBucket === undefined));
});

test("query statistics count surfaced 429s but propagate other failures", async () => {
  const throttled = queryContainer([], async () => { throw { code: 429 }; });
  assert.deepEqual(await queryTenant(throttled, "tenant", {}, 2), { succeeded: 0, throttled: 2 });
  const denied = new Error("access denied");
  denied.code = 403;
  await assert.rejects(queryTenant(queryContainer([], async () => { throw denied; }), "tenant", {}, 1),
    (error) => error === denied);
});

test("bulk statistics count successes and surfaced 429s without masking other errors", () => {
  assert.deepEqual(countBulkResults([
    { response: { statusCode: 201 } },
    { response: { statusCode: 429 } },
    { error: { code: 429 } },
  ]), { succeeded: 1, throttled: 2 });
  const failure = new Error("service failure");
  failure.code = 500;
  assert.throws(() => countBulkResults([{ error: failure }]), (error) => error === failure);
});
