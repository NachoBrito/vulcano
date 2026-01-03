[![Apache 2](http://img.shields.io/badge/license-Apache%202-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

# VulcanoDb - An in-process light-weight vector database

VulcanoDb is a minimalistic vector database, to be used when a full-featured system is too much. The project is open
source, published under the Apache2.0 license.

## In-Process execution

This vector database is aimed at projects that require semantic search but would prefer not to connect to external
systems. As long as you don’t need any advanced feature like multi-user, cluster deployment, etc., having
your data in the same process as your application will be the most convenient approach, and will provide the best
performance.

VulcanoDb can be installed as a standard project dependency in any Java-based system. It is written entirely in Java
and has no transitive dependencies.

You can read more about this project
in [this article](https://www.nachobrito.es/artificial-intelligence/project-vulcano/).

## Sample applications

You can find some example applications in the [vulcanodb-test](./vulcanodb-test) module.

## AI-Generated project documentation

You can find an AI-generated wiki about VulcanoDb
at [deepwiki.com/NachoBrito/vulcano](https://deepwiki.com/NachoBrito/vulcano), covering the project architecture and
main workflows.