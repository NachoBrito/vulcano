[![Apache 2](http://img.shields.io/badge/license-Apache%202-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

# VulcanoDB: High-Performance, In-Process Vector Database

VulcanoDB is a robust, lightweight vector database engineered for high-performance similarity search and efficient document management. Designed as an in-process solution, it eliminates the operational complexity and latency of external database systems, making it an ideal choice for applications requiring seamless semantic search capabilities.

## Why VulcanoDB?

In modern application development, integrating vector search often introduces unnecessary overhead through distributed architectures. VulcanoDB provides a streamlined alternative by embedding advanced indexing and retrieval directly within your Java application.

### Key Benefits

- **Exceptional Performance**: Leverages Hierarchical Navigable Small World (HNSW) graphs for ultra-fast similarity search and a multi-threaded query engine utilizing Java's virtual threads.
- **Zero-Dependency Architecture**: Built entirely in Java with no transitive dependencies, ensuring a small footprint and straightforward integration into any existing codebase.
- **Embedded Efficiency**: Minimizes latency by avoiding network hops and serialization overhead, processing queries directly in the application's memory space.
- **Versatile Document Storage**: Supports complex document structures with various field types, including strings, integers, vectors, and matrices, all backed by an efficient disk-based storage engine.
- **Reliability & Scalability**: Features a sophisticated storage architecture with Write-Ahead Logging (WAL) and segmented data storage for robust data integrity and efficient memory management.

## Technical Insights

VulcanoDB is built on a foundation of modern database principles:

- **HNSW Indexing**: Advanced graph-based indexing for high-recall, low-latency approximate nearest neighbor search. [Learn more about the implementation here](https://www.nachobrito.es/artificial-intelligence/efficient-vector-search-hnsw/).
- **Axon Storage Engine**: A custom-built storage layer optimized for both memory and disk performance.
- **Native Java Design**: Fully optimized for the Java ecosystem, taking advantage of the latest language features for maximum performance. [Read about the project's vision and goals](https://www.nachobrito.es/artificial-intelligence/project-vulcano/).

## Documentation & Resources

- **[Getting Started](doc/user-guide/INTRODUCTION.md)**: A comprehensive guide to integrating VulcanoDB into your project.
- **[Sample Applications](./vulcanodb-test)**: Practical examples and performance benchmarks to help you understand real-world usage.
- **[Low-Level Design (LLD)](doc/lld/LLD.md)**: In-depth documentation of the internal architecture, storage layouts, and indexing strategies.
- **[AI-Generated Wiki](https://deepwiki.com/NachoBrito/vulcano)**: Extended project details and automated documentation at deepwiki.com/NachoBrito/vulcano.

---

VulcanoDB is open-source software released under the [Apache 2.0 License](LICENSE.md).
