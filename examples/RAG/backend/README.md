# VulcanoDB RAG Example (Backend)

This project demonstrates a Retrieval-Augmented Generation (RAG) implementation using **VulcanoDB** as the vector database. It is built with **Micronaut** and integrates with **LangChain4j** for embedding generation and LLM interaction.

The example uses a pre-defined dataset of **arXiv papers** to provide context for AI-driven questions and answers.

## Overview

- **Vector Database**: VulcanoDB (High-performance vector similarity search).
- **Framework**: Micronaut 4.x.
- **Dataset**: arXiv metadata (subset of papers for research-oriented Q&A).
- **Communication**: WebSocket-based interaction for real-time RAG processing.

## Getting Started

### Prerequisites

1.  **Dataset**: The example expects the arXiv metadata file.
    - Download `arxiv-metadata-oai-snapshot.json` from [Kaggle](https://www.kaggle.com/datasets/Cornell-University/arxiv).
    - Place it in the `dataset/` directory or update `src/main/resources/application.properties` with the correct path:
      ```properties
      dataset.arxiv=file:/path/to/your/arxiv-metadata-oai-snapshot.json
      ```

2.  **Java**: JDK 17 or higher.

### Running the Application

You can start the backend application using the Micronaut Maven plugin:

```bash
./mvnw mn:run
```

The server will start by default on port `8080`.

## Features

- **Ingestion**: Automatically indexes arXiv paper abstracts into VulcanoDB.
- **Vector Search**: Efficiently retrieves relevant papers based on user queries.
- **Contextual RAG**: Uses retrieved paper content to augment LLM prompts for accurate answers.

---

## Micronaut 4.10.1 Documentation

- [User Guide](https://docs.micronaut.io/4.10.1/guide/index.html)
- [API Reference](https://docs.micronaut.io/4.10.1/api/index.html)
- [Configuration Reference](https://docs.micronaut.io/4.10.1/guide/configurationreference.html)
- [Micronaut Guides](https://guides.micronaut.io/index.html)
---

- [Micronaut Maven Plugin documentation](https://micronaut-projects.github.io/micronaut-maven-plugin/latest/)
## Feature websocket documentation

- [Micronaut Websocket documentation](https://docs.micronaut.io/latest/guide/#websocket)


## Feature micronaut-aot documentation

- [Micronaut AOT documentation](https://micronaut-projects.github.io/micronaut-aot/latest/guide/)


## Feature maven-enforcer-plugin documentation

- [https://maven.apache.org/enforcer/maven-enforcer-plugin/](https://maven.apache.org/enforcer/maven-enforcer-plugin/)


## Feature serialization-jackson documentation

- [Micronaut Serialization Jackson Core documentation](https://micronaut-projects.github.io/micronaut-serialization/latest/guide/)
