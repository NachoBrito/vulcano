# Project Brief: VulcanoDB

## Overview
VulcanoDB is a **production-ready, high-performance vector database system** engineered for extreme efficiency in storage, indexing, and querying of high-dimensional vector and attribute data. Unlike experimental or educational projects, VulcanoDB is designed for real-world scalability, robustness, and industrial-grade reliability.

The system is built with modern Java 21+ technologies and leverages advanced, persistent indexing techniques including HNSW (Hierarchical Navigable Small World) graphs for fast similarity search and persistent inverted indexes for attribute filtering.

## Core Architecture
- **Core Module**: Main database functionality with document management and query capabilities.
- **Axon Storage Engine**: A custom disk-based storage engine with WAL (Write-Ahead Log) for ACID-compliant persistence.
- **MCP Module**: Multi-Client Protocol implementation for database interaction, exposing tools for AI agents.
- **Test Module**: Comprehensive test suite including performance and recovery tests.

## Key Features
- Vector similarity search using HNSW indexing.
- **Attribute indexing**: Persistent inverted index for high-performance string filtering (EQUALS, STARTS_WITH, ENDS_WITH, CONTAINS).
- ACID-compliant persistence with Write-Ahead Logging (WAL).
- Document-based storage with flexible field types (String, Integer, Vector, Matrix).
- Multi-threaded query execution using Java 21 Virtual Threads.
- High-performance binary serialization for storage and logging.
- Memory-efficient data handling with MemorySegment and paged structures.
