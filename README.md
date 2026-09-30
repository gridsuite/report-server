# Report Server

[![Actions Status](https://github.com/gridsuite/report-server/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/gridsuite/report-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Areport-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Areport-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)

## Description

The **report-server** is a microservice of the [GridSuite](https://github.com/gridsuite) platform dedicated to **storing and serving hierarchical computation reports** produced by PowSyBl-based calculations (load flow, security analysis, dynamic simulation, etc.).

Clients push a tree of `ReportNode` objects after a computation; the server persists that tree and exposes it back through REST endpoints for display in the front-end (full tree view, flat paginated logs, aggregated severities, cross-report search).

It provides the following capabilities:

- **Create or append to a report**: push a new `ReportNode` tree, or append children to an existing root report.
- **Append a child report with a server-generated id**: attach a new subtree under an existing root, returning the generated child identifier to the caller.
- **Replace a report's children**: keep the root but replace its whole subtree.
- **Duplicate a report**: clone an entire report tree under a new identifier.
- **Read the tree view**: get the report as a recursive structure of container nodes (no leaf logs).
- **Read flat logs**: get paginated, filterable (message/severity) logs for a single report or across multiple reports at once.
- **Search logs**: locate the page/index of a search-term match within a filtered, paginated log view, for a single report or across multiple reports.
- **Get aggregated severities**: the set of distinct severities present in a report.
- **Delete reports**: individually or in bulk.

---

## Technical Stack

- Java, Spring Boot
- Spring Data JPA / Hibernate, PostgreSQL (H2 for tests)
- Liquibase
- API documentation: OpenAPI / Swagger (`springdoc`)
- Micrometer / Prometheus (via Spring Actuator)
- [powsybl-commons](https://github.com/powsybl/powsybl-core) `ReportNode` model

---

## Development Scripts

Build Docker image

```shell
mvn install -DskipTests -Dpowsybl.docker.install
```

Please read [liquibase usage](https://github.com/powsybl/powsybl-parent/#liquibase-usage) for instructions to automatically generate changesets. After you generated a changeset do not forget to add it to git and in `src/main/resources/db/changelog/db.changelog-master.yaml`.

---

## Domain Model

The report tree is stored in a single `report_node` table using a **nested-sets model**: each node has an `order_`/`end_order` pair computed by a depth-first walk, so any subtree can be fetched with a single `BETWEEN` range scan (no joins, no recursion).

| Concept | Description |
|---|---|
| **Report node** | A node of a report tree: a message, a severity, its position (`order_`/`end_order`/`depth`), and a reference to its root and parent. |
| **Root report** | A node that is its own root (`root_node_id = id`), identified by the caller-supplied UUID (the computation UUID). |
| **Container node** | A non-leaf node, used to build the tree view. |
| **Leaf node** | A node with no children carrying an actual log message/severity. |
| **Severity** | The highest severity of the subtree rooted at a given node (`UNKNOWN` to `FATAL`, 8 levels), aggregated bottom-up. |


## Useful Links

See [ARCHITECTURE.md](./ARCHITECTURE.md) for implementation details: the nested-sets storage model, the report lifecycle (ingestion, reading, search, deletion, duplication), key tuning constants, and the testing approach.
