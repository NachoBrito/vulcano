# Tech Context

## Technologies used
- **Java 21+**: Primary programming language.
    - **Virtual Threads**: For high-concurrency query execution.
    - **Foreign Function & Memory API (`MemorySegment`)**: For efficient off-heap memory management and paged storage.
- **Maven**: Build and dependency management.
- **HNSW (Hierarchical Navigable Small World)**: Custom paged implementation of the HNSW indexing algorithm.
- **Roaring Bitmaps (Roaring64)**: For efficient `DocIdSet` and bitmap-based query operations.
- **Micronaut & Netty**: Powers the MCP Module's server and networking.
- **LangChain4j**: Used in the MCP Module for ONNX embedding model integration.

## Development setup
- **IDE**: IntelliJ IDEA Ultimate.
- **Build**: `mvn clean install` for full build and test.
- **Test**: JUnit 5 and Mockito for testing.
- **Logging**: SLF4J with SimpleLogger for testing.

## Technical constraints
- **ACID Persistence**: Operations must be logged to the WAL and flushed to disk before completion.
- **Binary I/O Performance**: Use `DataOutputStream`/`DataInputStream` and `MemorySegment` for high-performance data access.
- **Off-heap Scaling**: Paged structures must support datasets that exceed the JVM heap size.
- **Concurrency**: Database operations must be thread-safe and optimized for Virtual Thread execution.

## Dependencies
- `org.roaringbitmap:RoaringBitmap`
- `io.micronaut:micronaut-runtime`
- `dev.langchain4j:langchain4j-embeddings-onnx`
- `org.yaml:snakeyaml`
- `org.slf4j:slf4j-api`

## Tool usage patterns
- **Maven Wrapper**: Use `./mvnw` for consistent builds across environments.
- **MCP Server**: The `vulcano-mcp` module allows AI tools to interact with the database via standard MCP prompts and tools.
