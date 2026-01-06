# System Patterns

## System architecture
VulcanoDB is modular, consisting of a Core Module for fundamental database operations, an MCP Module for multi-client interaction, and a Test Module for comprehensive validation. The core leverages a disk-based storage engine with an in-memory index for fast lookups.

## Key technical decisions
- **HNSW Indexing**: Chosen for efficient approximate nearest neighbor search on vector data.
- **Virtual Threads**: Utilized for highly concurrent query execution, improving resource utilization.
- **Document-based storage**: Provides flexibility for varied data schemas.
- **Pluggable similarity metrics**: Allows for different vector comparison methods (e.g., Cosine Similarity).
- **Write-Ahead Log (WAL)**: Added to `AxonDataStore` to ensure ACID properties (atomicity and durability).
- **Binary Serialization**: The WAL uses high-performance binary serialization (`WalSerializer`) for documents and vectors, supporting all native types with minimal overhead.

## Design patterns in use
- **Builder Pattern**: For constructing complex objects like `Document` and `Query`.
- **Strategy Pattern**: For `VectorSimilarity` implementations.
- **Repository Pattern**: Abstracting data storage and retrieval (e.g., `DataStore`).
- **Coordinator Pattern**: The `WalManager` coordinates the persistence of intentions before execution.
- **Facade Pattern**: `VulcanoDb` provides a simple entry point to the system.
- **Serialization Pattern**: Specialized `WalSerializer` for binary conversion.

## Component relationships
- `VulcanoDb` orchestrates interactions between `DataStore`, `QueryCompiler`, and `QueryExecutor`.
- `DataStore` manages document persistence and retrieval, interacting with `DocumentPersister`, `FieldDiskStore`, and `WalManager`.
- `WalManager` ensures that any operation (Add/Remove) is logged before being applied.
- `WalSerializer` provides binary data conversion services to `WalManager`.
- `HnswIndexHandler` integrates HNSW indexing with the `DataStore`.

## Critical implementation paths
- **Document ingestion**: WAL Record (Binary) -> Document Persistence (Fields) -> Dictionary Update -> Index Update -> WAL Commit.
- **Query execution**: Flow from parsing to similarity matching.
- **Crash Recovery**: On startup, `AxonDataStore` reads binary WAL entries, deserializes them, and re-applies any pending operations.
- **Index management**: Creation, updating, and persistence of HNSW graphs.
