# Project Brief: VulcanoDB

## Overview
VulcanoDB is a high-performance vector database system designed for efficient storage, indexing, and querying of vector data. The system is built with modern Java technologies and leverages advanced indexing techniques including HNSW (Hierarchical Navigable Small World) graphs for fast similarity search.

## Core Architecture
- **Core Module**: Main database functionality with document management and query capabilities
- **MCP Module**: Multi-Client Protocol implementation for database interaction
- **Test Module**: Comprehensive test suite including performance tests

## Key Features
- Vector similarity search using HNSW indexing
- Document-based storage with flexible field types
- Multi-threaded query execution with virtual threads
- Support for various field types (String, Integer, Vector, Matrix)
- Efficient memory management and disk-based storage
- Query compilation and optimization
