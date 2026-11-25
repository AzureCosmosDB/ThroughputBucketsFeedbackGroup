# Changelog

## Java and JavaScript throughput bucket scenarios (2026-11-25)

### Added

- Request-level scenarios: standard tenant queries select a bucket on one shared
  client; premium requests omit a bucket.
- Client-level scenarios: inventory bulk writes inherit a client default bucket
  alongside customer queries from a separate, unbucketed client.
- Enabled/disabled comparison, simple success/throttling counts, and separate
  tests for bucket routing, concurrent workloads, live queries, bulk writes, and cleanup.
- Short setup guides linking directly to the scenario methods, pinned SDK
  dependencies, and links from the root sample index.
