# Tech Context

## Technologies used
- **Java 21+**: Primary programming language, utilizing Virtual Threads and the new Foreign Function & Memory API (via `MemorySegment`).
- **Maven**: Build automation tool.
- **HNSW (Hierarchical Navigable Small World)**: Indexing algorithm for vector similarity search.
- **Roaring Bitmaps**: Used for efficient `DocIdSet` implementations.
- **Netty (MCP Module)**: For network communication in the Multi-Client Protocol.
- **Micronaut (MCP Module)**: Framework for building the MCP server.
- **LangChain4j (MCP Module)**: For ONNX embedding model integration.

## Development setup
- **IDE**: IntelliJ IDEA Ultimate.
- **Build**: Maven-based project, standard `mvn clean install` for building.
- **Test**: JUnit 5 for unit and integration tests.

## Technical constraints
- **Atomicity & Durability**: Guaranteed by the Write-Ahead Log (WAL) before operations are applied to the main store.
- **High-Performance Serialization**: The WAL uses custom binary serialization (`WalSerializer`) to handle complex vector/matrix data with minimal overhead.
- **Memory management**: Efficient handling of large datasets and indices using memory-mapped files and `MemorySegment`.
- **Disk I/O**: Optimized append-only writes for the data log and WAL.
- **Concurrency**: High concurrency handled via virtual threads.

## Dependencies
- Standard Java libraries (inc. `java.lang.foreign`).
- HNSW library (Java implementation).
- Roaring Bitmaps.
- Micronaut and Netty.
- `snakeyaml`.

## Tool usage patterns
- **CLI**: `./mvnw` or `mvn` for building and testing.
- **MCP Server**: Exposes VulcanoDB tools to AI agents for automated RAG workflows.
