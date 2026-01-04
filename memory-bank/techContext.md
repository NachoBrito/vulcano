# Tech Context

## Technologies used
- **Java**: Primary programming language.
- **Maven**: Build automation tool.
- **HNSW (Hierarchical Navigable Small World)**: Indexing algorithm for vector similarity search.
- **Roaring Bitmaps**: Used for efficient `DocIdSet` implementations.
- **Netty (MCP Module)**: Potentially for network communication in the Multi-Client Protocol.
- **Micronaut (MCP Module)**: Framework for building microservices.

## Development setup
- **IDE**: IntelliJ IDEA Ultimate (as observed from `environment_details`).
- **Build**: Maven-based project, standard `mvn clean install` for building.
- **Test**: JUnit 5 for unit and integration tests.

## Technical constraints
- **Memory management**: Efficient handling of large datasets and indices in memory, especially for HNSW.
- **Disk I/O**: Optimized read/write operations for persistent storage.
- **Concurrency**: Managing concurrent access to data structures and indices with virtual threads.

## Dependencies
- Standard Java libraries.
- HNSW library (likely a specific implementation for Java).
- Roaring Bitmaps library.
- Potentially Netty and Micronaut for the MCP module.

## Tool usage patterns
- **CLI**: `mvn` for building, testing, and running.
- **Git**: For version control.
- **IDE Debugging**: For development and troubleshooting.
