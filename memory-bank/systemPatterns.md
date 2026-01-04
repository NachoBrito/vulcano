# System Patterns

## System architecture
VulcanoDB is modular, consisting of a Core Module for fundamental database operations, an MCP Module for multi-client interaction, and a Test Module for comprehensive validation. The core leverages a disk-based storage engine with an in-memory index for fast lookups.

## Key technical decisions
- **HNSW Indexing**: Chosen for efficient approximate nearest neighbor search on vector data.
- **Virtual Threads**: Utilized for highly concurrent query execution, improving resource utilization.
- **Document-based storage**: Provides flexibility for varied data schemas.
- **Pluggable similarity metrics**: Allows for different vector comparison methods (e.g., Cosine Similarity).

## Design patterns in use
- **Builder Pattern**: For constructing complex objects like `Document` and `Query`.
- **Strategy Pattern**: For `VectorSimilarity` implementations.
- **Repository Pattern**: Abstracting data storage and retrieval (e.g., `DataStore`).
- **Command Pattern**: Potentially for query operations.

## Component relationships
- `VulcanoDb` orchestrates interactions between `DataStore`, `QueryCompiler`, and `QueryExecutor`.
- `DataStore` manages document persistence and retrieval, interacting with `DocumentPersister` and `FieldDiskStore`.
- `HnswIndexHandler` integrates HNSW indexing with the `DataStore` for vector search.

## Critical implementation paths
- **Document ingestion**: How documents are received, parsed, indexed, and stored.
- **Query execution**: The flow from query parsing, compilation, optimization, to execution and result retrieval, especially for vector similarity queries.
- **Index management**: Creation, updating, and persistence of HNSW graphs and other indices.
