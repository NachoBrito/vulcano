[![Apache 2](http://img.shields.io/badge/license-Apache%202-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

# VulcanoDB: A Lightweight, In-Process Vector Database

VulcanoDB is a minimalistic vector database designed for cases where a full-scale, distributed system is overkill. It is open source and released under the Apache 2.0 license.

## In-Process Execution

VulcanoDB is built for projects that need semantic search capabilities without the overhead of connecting to external systems. If you don't require advanced features like multi-user support or cluster deployments, keeping your data within the same process as your application is the most convenient approach—and often provides the best performance.

### Key Benefits:
- **Zero External Dependencies**: VulcanoDB is written entirely in Java and has no transitive dependencies.
- **Easy Integration**: It can be installed as a standard project dependency in any Java-based system.
- **High Performance**: Minimal latency by avoiding network hops and leveraging in-process memory.

You can learn more about the project's origins and goals in [this article](https://www.nachobrito.es/artificial-intelligence/project-vulcano/).

## Sample Applications

Explore example applications and performance benchmarks in the [vulcanodb-test](./vulcanodb-test) module.

## Getting Started

Check out the [User Documentation](doc/user-guide/INTRODUCTION.md) to learn how to integrate VulcanoDB into your project.

## Technical Details

For a deep dive into the internal architecture, including indexing strategies and storage layout, refer to the [Low-Level Design (LLD)](doc/lld/LLD.md) document.

Additional project architecture details and workflows can be found on the AI-generated wiki at [deepwiki.com/NachoBrito/vulcano](https://deepwiki.com/NachoBrito/vulcano).
