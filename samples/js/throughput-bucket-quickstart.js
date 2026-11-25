import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { pathToFileURL } from "node:url";

export function readConfig(env = process.env) {
  const required = (name) => {
    const value = env[name]?.trim();
    if (!value) throw new Error(`Set ${name} before running the sample.`);
    return value;
  };
  const bucketText = env.COSMOS_THROUGHPUT_BUCKET ?? "1";
  if (!/^[1-5]$/.test(bucketText)) {
    throw new Error("COSMOS_THROUGHPUT_BUCKET must be an integer from 1 to 5.");
  }
  const scope = env.COSMOS_BUCKET_SCOPE ?? "request";
  if (!["request", "client"].includes(scope)) {
    throw new Error("COSMOS_BUCKET_SCOPE must be request or client.");
  }
  return {
    endpoint: required("COSMOS_ENDPOINT"),
    database: required("COSMOS_DATABASE"),
    container: required("COSMOS_CONTAINER"),
    bucket: Number(bucketText),
    scope,
  };
}

export function createProduct(paths, id = `throughput-bucket-js-${randomUUID()}`) {
  if (!Array.isArray(paths) || paths.length === 0) {
    throw new Error("The container must define partition key paths.");
  }
  const product = { id, Type: "Accessories", Name: "Throughput bucket quickstart", Price: 10 };
  const partitionKey = paths.map((path) => {
    if (!/^\/[^/]+(?:\/[^/]+)*$/.test(path)) {
      throw new Error(`Unsupported partition key path: ${path}`);
    }
    const segments = path.slice(1).split("/");
    let object = product;
    for (const segment of segments.slice(0, -1)) {
      if (["__proto__", "prototype", "constructor"].includes(segment)) {
        throw new Error(`Unsupported partition key path: ${path}`);
      }
      if (object[segment] === undefined) object[segment] = {};
      if (object[segment] === null || typeof object[segment] !== "object") {
        throw new Error(`Conflicting partition key path: ${path}`);
      }
      object = object[segment];
    }
    const leaf = segments.at(-1);
    if (["__proto__", "prototype", "constructor"].includes(leaf)) {
      throw new Error(`Unsupported partition key path: ${path}`);
    }
    object[leaf] = id;
    return id;
  });
  return { product, partitionKey };
}

function logResponse(operation, response) {
  const status = response.statusCode === undefined ? "" : `status=${response.statusCode}, `;
  console.log(`${operation}: ${status}RU=${response.requestCharge}`);
}

export async function runQuickstart(container, config) {
  const { resource: properties } = await container.read();
  const paths = properties?.partitionKey?.paths;
  const { product, partitionKey } = createProduct(paths);
  let updateField = "Price";
  while (paths.some((path) => path.split("/")[1] === updateField)) updateField = `_${updateField}`;
  console.log(`Isolated product: ${product.id}`);
  const item = container.item(product.id, partitionKey);
  const options = () => config.scope === "request" ? { throughputBucket: config.bucket } : {};
  let created = false;
  let failure;
  try {
    // In client scope the client option also applies to this create and baseline read.
    logResponse("Create isolated product", await container.items.create(product));
    created = true;
    const baseline = await item.read();
    assert.equal(baseline.resource?.id, product.id);
    logResponse("Read without request override", baseline);

    const read = await item.read(options());
    assert.equal(read.resource?.id, product.id);
    logResponse(`Read using bucket ${config.bucket}`, read);

    const query = container.items.query({
      query: "SELECT * FROM c WHERE c.id = @id",
      parameters: [{ name: "@id", value: product.id }],
    }, { ...options(), partitionKey, maxItemCount: 1 });
    const results = [];
    while (query.hasMoreResults()) {
      const page = await query.fetchNext();
      results.push(...page.resources);
      logResponse("Query page", page);
    }
    assert.equal(results.length, 1, "The query must return exactly the isolated product.");
    assert.equal(results[0].id, product.id);

    const updated = { ...read.resource, [updateField]: 20 };
    logResponse("Replace using bucket", await item.replace(updated, options()));
    const verified = await item.read(options());
    assert.equal(verified.resource?.[updateField], 20, "The replacement must be persisted.");
    logResponse("Verify replacement", verified);
  } catch (error) {
    failure = error;
    throw error;
  } finally {
    if (created) {
      try {
        // Cleanup has no request override, even if a request bucket is throttled.
        logResponse("Delete isolated product", await item.delete());
        const deleted = await item.read();
        assert.equal(deleted.statusCode, 404, "The isolated product must be deleted.");
      } catch (cleanupError) {
        if (failure) throw new AggregateError([failure, cleanupError], "Sample and cleanup failed.");
        throw cleanupError;
      }
    }
  }
}

async function main() {
  const config = readConfig();
  const [{ CosmosClient }, { DefaultAzureCredential }] = await Promise.all([
    import("@azure/cosmos"),
    import("@azure/identity"),
  ]);
  const client = new CosmosClient({
    endpoint: config.endpoint,
    aadCredentials: new DefaultAzureCredential(),
    ...(config.scope === "client" ? { throughputBucket: config.bucket } : {}),
    userAgentSuffix: "throughput-bucket-js-quickstart",
  });
  try {
    await runQuickstart(client.database(config.database).container(config.container), config);
    console.log(`E2E PASS: ${config.scope} scope, bucket ${config.bucket}; product removed.`);
  } finally {
    client.dispose();
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error("Quickstart failed:", error);
    if (error.diagnostics) console.error("Cosmos diagnostics:", error.diagnostics);
    process.exitCode = 1;
  });
}
