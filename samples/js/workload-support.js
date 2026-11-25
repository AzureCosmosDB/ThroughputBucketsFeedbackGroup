import { randomUUID } from "node:crypto";

export async function queryTenant(container, tenant, requestOptions, count) {
  const outcomes = await Promise.all(Array.from({ length: count }, async () => {
    try {
      await container.items.query({
        query: "SELECT TOP 10 c.id FROM c WHERE c.tenant = @tenant",
        parameters: [{ name: "@tenant", value: tenant }],
      }, requestOptions).fetchAll();
      return { succeeded: 1, throttled: 0 };
    } catch (error) {
      if (error.code === 429) return { succeeded: 0, throttled: 1 };
      throw error;
    }
  }));
  return outcomes.reduce((total, outcome) => ({
    succeeded: total.succeeded + outcome.succeeded,
    throttled: total.throttled + outcome.throttled,
  }), { succeeded: 0, throttled: 0 });
}

export function createInventoryOperations(count, standardTenant, premiumTenant) {
  return Array.from({ length: count }, (_, index) => {
    const id = `inventory-${randomUUID()}`;
    const tenant = index % 2 === 0 ? standardTenant : premiumTenant;
    return {
      operationType: "Create",
      partitionKey: [tenant, id],
      resourceBody: { id, tenant, name: `Product ${index}` },
    };
  });
}

export function countBulkResults(results) {
  const stats = { succeeded: 0, throttled: 0 };
  for (const result of results) {
    if (result.error) {
      if (result.error.code !== 429) throw result.error;
      stats.throttled++;
    } else if (result.response.statusCode === 429) {
      stats.throttled++;
    } else if (result.response.statusCode >= 200 && result.response.statusCode < 300) {
      stats.succeeded++;
    } else {
      throw new Error(`Inventory bulk status=${result.response.statusCode}`, { cause: result.response });
    }
  }
  return stats;
}

export function printStats(label, stats) {
  console.log(`${label}: succeeded=${stats.succeeded}, throttled=${stats.throttled}`);
}

export function printTenantStats(stats) {
  printStats("Standard tenant queries", stats.standard);
  printStats("Premium tenant queries", stats.premium);
}
