import { randomUUID } from "node:crypto";

export function createOperations(scenario) {
  return Array.from({ length: 10 }, (_, index) => {
    const id = `${scenario}-${randomUUID()}`;
    const tenant = `tenant-${index}`;
    return {
      operationType: "Create",
      partitionKey: [tenant, id],
      resourceBody: { id, tenant, name: `Product ${index}` },
    };
  });
}

export function printResults(results) {
  for (const result of results) {
    if (result.error) throw result.error;
    const { statusCode, requestCharge } = result.response;
    if (statusCode >= 400) throw new Error(`Bulk status=${statusCode}`);
    console.log(`${result.operationInput.resourceBody.id}: status=${statusCode}, RU=${requestCharge}`);
  }
}
