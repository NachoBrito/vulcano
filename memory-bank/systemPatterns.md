# System Patterns

## Core Philosophy: Industrial Excellence
VulcanoDB is not a teaching project or a simplistic example. It is a **production-ready engine** where every line of code is evaluated for performance, scalability, and robustness. **Simple or "naive" implementations are prohibited** in the core modules, except when strictly necessary for isolated unit testing comparison.

## System architecture
VulcanoDB is modular and engineered for high throughput:
- **Core Module**: Provides the fundamental database engine, document model, and query API.
- **Axon Storage Engine**: A specialized disk-based storage system within Core that implements paged storage, indexing, and WAL.
- **MCP Module**: A Multi-Client Protocol server (Micronaut-based) that exposes VulcanoDB capabilities to AI agents.
- **Test Module**: A separate module for integration, performance, and robustness testing.

The system leverages a disk-based storage engine with paged memory-mapped files and multiple indexing strategies.

## Key technical decisions
- **HNSW Indexing**: Used for efficient approximate nearest neighbor search. Implemented with paged storage (`PagedVectorIndex`, `PagedGraphIndex`) for scalability.
- **Persistent Inverted Index**: Used for high-performance string filtering. Leverages the `KeyValueStore` to map terms to document IDs.
- **Virtual Threads**: Utilized for concurrent query execution and parallelized similarity matching.
- **WAL (Write-Ahead Log)**: Ensures ACID properties (atomicity and durability). Operations are logged to a persistent `WalManager` before being applied to the main store.
- **Binary Serialization**: Custom high-performance `WalSerializer` using `DataOutputStream` for compact and type-safe data persistence.
- **Paged Memory Management**: Uses `MemorySegment` and paged arrays (`PagedLongArray`) to manage large datasets beyond heap limits.
- **Atomic Space Reservation**: `DataLog` uses atomic `getAndAdd` with safety margins to ensure thread-safe concurrent writes to memory-mapped segments.
- **Tucana Separation of Concerns**: `TucanaIndex` manages the logical B&epsilon;-tree structure (key-to-offset), while `TucanaStorage` manages physical block allocation and persistence for both data and nodes.
- **Tucana Atomic Commits**: Uses a dual-superblock mechanism (`Superblock`) at the start of the database file. Commits swap the active superblock only after data and index nodes have been successfully flushed to disk.
- **Tucana Paged Storage**: `AxonTucanaStorage` leverages a `FilePageManager` to map fixed-size pages (1MB) of a single database file into memory on-demand using the FFM API.

## Design patterns in use
- **Builder Pattern**: Extensive use for `Document`, `Query`, and `QueryResult`.
- **Strategy Pattern**: Used for similarity metrics and execution nodes.
- **Repository/Store Pattern**: `DataStore` interface abstracts the underlying storage implementation (Axon, Naive).
- **Coordinator Pattern**: `WalManager` coordinates persistence of operations.
- **Facade Pattern**: `TucanaKeyValueStore` provides a high-level key-value API while coordinating the underlying `TucanaIndex` and `TucanaStorage`.
- **Compiler/Physical Plan**: `QueryCompiler` transforms logical `Query` objects into a tree of physical execution nodes (`AndNode`, `EqualsNode`, etc.).
- **Provider Pattern**: `ExecutorProvider` abstracts how threads are managed (Virtual vs. Standard).
- **Handler Pattern**: `IndexHandler` implementations (`HnswIndexHandler`, `StringIndexHandler`) encapsulate specific indexing logic.

## Component relationships
- `VulcanoDb` (Facade): The main entry point that ties together the `DataStore`, `QueryCompiler`, and `QueryExecutor`.
- `AxonDataStore`: Orchestrates `DocumentPersister`, `FieldDiskStore`, `IndexHandler` registry, and `WalManager`.
- `WalManager`: Relies on `WalSerializer` to record operations in a persistent log.
- `TucanaKeyValueStore`: Coordinates `TucanaIndex` (mapping) and `TucanaStorage` (persistence).
- `StringIndexHandler`: Uses a persistent `InvertedIndex` (built on `KeyValueStore`) to provide O(1) exact matches and efficient partial matches.
- `QueryExecutor`: Uses a `VectorizedRunner` and physical plan nodes to process results in parallel.

## Critical implementation paths
- **Document Ingestion**: Operation -> WAL Log (Binary) -> Document Persistence (Fields) -> Index Updates (Vector + String) -> WAL Commit.
- **Tucana Ingestion**: Serialization (`Entry`) -> Physical Write (`TucanaStorage`) -> Logical Map Update (`TucanaIndex`).
- **Query Execution**: Query -> `QueryCompiler` -> Physical Plan -> `QueryExecutor` -> Result Ranking.
- **Crash Recovery**: `AxonDataStore.initialize()` -> `WalManager.readEntries()` -> Deserialization -> Re-apply pending operations.
- **Search**: Entry Point -> Index Search (HNSW or Inverted) -> Boolean Filtering -> Top-K Selection.
