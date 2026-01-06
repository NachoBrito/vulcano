# Active Context

## Current work focus
Integrating and optimizing the Write-Ahead Log (WAL) for `AxonDataStore` to ensure data safety and atomicity.

## Recent changes
- Implemented `WalManager` and `WalEntry` in `es.nachobrito.vulcanodb.core.store.axon.wal`.
- Created `WalSerializer` for high-performance **binary serialization** of documents and transaction data.
- Updated `KeyValueStore`, `DataLog`, and `ValueType` to support raw `byte[]` storage (`BYTES` type).
- Refactored `DefaultWalManager` to use binary serialization instead of fragile string formatting.
- Integrated WAL into `AxonDataStore` with automatic crash recovery in `initialize()`.
- Enhanced `WalManagerTest` to verify complex types (vectors/matrices) in the log.

## Next steps
- Verify the implementation with tests (currently blocked by environment issues with `mvn`/`java`).
- Proceed with the RAG-focused iteration:
    - Implement `Collection` and `Schema` API.
    - Integrate `Embedder` abstractions for automatic vectorization.
    - Enhance `AxonDataStore` with metadata persistence improvements.
    - Implement hybrid search optimization.

## Active decisions and considerations
- **Binary Format**: Using `DataOutputStream` for binary WAL entries provides a robust, fast, and type-safe way to record all document types, including large vectors.
- **KVStore Re-use**: Leveraging `KeyValueStore` for the WAL provides persistent, crash-consistent storage for log entries with minimal new infrastructure.

## Important patterns and preferences
- TDD (Test-Driven Development) for critical recovery logic.
- High-performance binary I/O for system-level data.

## Learnings and project insights
- The `DataLog` architecture is flexible enough to support raw bytes, which is essential for specialized logs like the WAL.
