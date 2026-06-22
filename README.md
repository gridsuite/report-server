# Report Server

[![Actions Status](https://github.com/gridsuite/report-server/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/gridsuite/report-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Areport-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Areport-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)

Please read [liquibase usage](https://github.com/powsybl/powsybl-parent/#liquibase-usage) for instructions to automatically generate changesets.
After you generated a changeset do not forget to add it to git and in src/resource/db/changelog/db.changelog-master.yml


The old way to automatically generate the sql schema file (directly using hibernate) can still be used for debugging. Use the following command:
```
mvn package -DskipTests && rm -f src/main/resources/report.sql && java -jar target/gridsuite-report-server-1.0.0-SNAPSHOT-exec.jar --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create
```
 

## Purpose

The report server stores and serves hierarchical computation reports produced by PowSyBl-based calculations (load flow, security analysis, etc.) in the GridSuite platform. Clients push a tree of `ReportNode` objects after a computation; the server persists that tree and exposes it back through REST endpoints for display in the front-end.

## Technology Stack

| Layer | Technology |
| --- | --- |
| Runtime | Java, Spring Boot |
| Persistence | Spring Data JPA / Hibernate, PostgreSQL (H2 for tests) |
| Migrations | Liquibase |
| Build | Maven |
| API docs | SpringDoc OpenAPI (Swagger UI at `/swagger-ui.html`) |
| Metrics | Micrometer → Prometheus (via Spring Actuator) |
| Report model | PowSyBl `ReportNode` / `powsybl-commons` |

---

## Database Schema

There is a single table, `report_node`, that stores every node of every report tree.

```
report_node
┌──────────────┬─────────────┬───────────────────────────────────────────────────────────┐
│ Column       │ Type        │ Notes                                                     │
├──────────────┼─────────────┼───────────────────────────────────────────────────────────┤
│ id           │ UUID (PK)   │ Manually assigned. Root nodes use the UUID given by the   │
│              │             │ caller (the computation UUID, currently always uuidv4). Child nodes are uuidv7 with monotonicity (new random only generated when changing milliseconds, otherwise just previous value incremented  │
│ message      │ VARCHAR(500)│ Human-readable log text. Truncated at 500 chars.          │
│ severity     │ VARCHAR(255)│ Highest severity in the subtree rooted here.              │
│              │             │ Values: UNKNOWN, TRACE, DEBUG, DETAIL, INFO, WARN,        │
│              │             │ ERROR, FATAL (ordered by increasing level 0-7).           │
│ order_       │ INTEGER     │ DFS pre-order index (left bound in the nested-sets model).│
│ end_order    │ INTEGER     │ DFS post-order index (right bound).                       │
│ is_leaf      │ BOOLEAN     │ True when the node has no children AND carries a severity.│
│ depth        │ INTEGER     │ Distance from the root node (root = 0).                   │
│ root_node_id │ UUID (FK)   │ Points to the root of the tree this node belongs to.      │
│              │             │ For the root node itself, root_node_id = id (self-ref).   │
│ parent_id    │ UUID        │ Direct parent. NULL only for the root node. No FK         │
│              │             │ constraint (dropped in changelog_20250422T150510Z.xml     │
│              │             │ to avoid cascade performance issues on bulk deletes).     │
└──────────────┴─────────────┴───────────────────────────────────────────────────────────┘
```

### Indexes

| Index name | Columns | Purpose |
| --- | --- | --- |
| `report_node_parent_id_idx` | `parent_id` | Recursive CTE traversal during delete |
| `root_node_orders_idx` | `root_node_id, order_, end_order` | All subtree range scans (logs, severities, search) |
| `root_node_and_container_idx` | `root_node_id, is_leaf` | Fetching only container (non-leaf) nodes for the tree view |

---

## The Nested-Sets Storage Model

This is the most important design decision in the application and must be understood before touching any read or write path.

### Principle

Every node is assigned two integers at write time: `order_` (pre-order) and `end_order` (post-order). They are produced by a single depth-first walk of the in-memory `ReportNode` tree, implemented in `SizedReportNode`. The walk increments a counter for each node visited; the first number assigned to a node becomes its `order_`, and `end_order = order_ + subtree_size - 1`.

For any node N, all nodes in N's subtree satisfy:

```
order_ BETWEEN N.order_ AND N.end_order
```

This makes fetching an entire subtree a **single range scan** on `root_node_orders_idx`, with no joins and no recursion.

### Example

Given a tree:

```
Root (order=0, end=4)
├── Child A (order=1, end=3)
│   ├── Leaf A1 (order=2, end=2)
│   └── Leaf A2 (order=3, end=3)
└── Child B (order=4, end=4)
```

To fetch all nodes under Child A:

```
SELECT * FROM report_node
WHERE root_node_id = :rootId AND order_ BETWEEN 1 AND 3;
```

### Trade-offs

- **Reads are O(1) SQL operations** (one range query regardless of depth).
- **Appending is supported** only at the root level. `appendReportElements` extends `root.end_order` and assigns new ranges starting from the old `end_order + 1`.
 Inserting at arbitrary positions in the middle of an existing tree is 
not implemented and would require renumbering the entire tree.
- **Deletes** do not renumber. The ranges become sparse after deletion but this has no functional impact because all queries use `BETWEEN` comparisons.

---

## Filtering and Search Across Multiple Report Trees

When logs must be fetched across several report trees at once (e.g., displaying results from multiple computations in a single view), the single-report `BETWEEN` approach cannot be used because each tree has its own independent `order_` numbering. A different strategy is used instead.

### Preserving caller-defined order with unnest … WITH ORDINALITY

The multi-report queries pass the list of root IDs as a **PostgreSQL array** (`UUID[]`) and use `unnest … WITH ORDINALITY` to join against the table:

```
SELECT CAST(rn.id AS VARCHAR), rn.message, rn.severity, rn.depth, CAST(rn.parent_id AS VARCHAR)
FROM unnest(:rootNodeIds) WITH ORDINALITY AS input_id(id, ord)
JOIN report_node rn ON rn.root_node_id = input_id.id
WHERE UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
ORDER BY input_id.ord, rn.order_ ASC
```

`WITH ORDINALITY` attaches a sequential position (`ord`) to each element of the array. The `ORDER BY input_id.ord, rn.order_ ASC` clause ensures that:

- logs from different trees are grouped in the **same order as the caller specified the report IDs**;
- within each tree the DFS pre-order (`order_`) is preserved.

This makes the combined result deterministic and stable for pagination across reports.

### Manual result reconstruction

Because the result set is a raw `Object[]` (Spring Data cannot map native multi-join queries to a typed projection automatically), the service manually reconstructs `ReportProjection` records from the array:

```
// ReportService.getMultipleReportsLogsPage
List<ReportLog> logs = projections.stream()
    .map(row -> new ReportProjection(
        UUID.fromString((String) row[0]),  // id
        (String) row[1],                   // message
        (String) row[2],                   // severity
        (Integer) row[3],                  // depth
        row[4] != null ? UUID.fromString((String) row[4]) : null  // parentId
    ))
    .map(ReportLogMapper::map)
    .toList();
```

The explicit `CAST(… AS VARCHAR)` in the SQL is required because PostgreSQL returns UUID columns as either `java.util.UUID` or `String` depending on the JDBC driver version; the cast pins the type to `String` and matches the manual reconstruction above.

### Search-term position resolution across multiple reports

The multi-report search endpoint (`GET /reports/logs/search?reportIds=…`) returns `List<MatchPosition>` — each entry is `{page, index}` telling the front-end exactly where a search hit falls within the combined paged view, with no need to fetch intermediate pages.

The query mirrors the log query's ordering and applies a two-step CTE:

```
WITH ordered_reports AS (
    SELECT ROW_NUMBER() OVER (
               ORDER BY input_id.ord, rn.order_ ASC
           ) - 1 AS row_position,
           rn.message
    FROM unnest(:rootNodeIds) WITH ORDINALITY AS input_id(id, ord)
    JOIN report_node rn ON rn.root_node_id = input_id.id
    WHERE UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
    -- optional: AND rn.severity IN (:severities)
)
SELECT row_position
FROM ordered_reports
WHERE UPPER(message) LIKE UPPER(:searchPattern) ESCAPE '\\'
ORDER BY row_position
```

- The inner CTE applies the **display filter** (message and optional severity) and numbers the matching rows starting at 0 (`ROW_NUMBER() - 1`), using the same `input_id.ord, rn.order_` ordering as the log query so that row positions are consistent with what the front-end displays.
- The outer query applies the **search term** as a second `LIKE` filter on those already-numbered rows and returns only the positions of the hits.

The service converts each integer position to `{page = position / pageSize, index = position % pageSize}`:

```
// ReportService.searchTermMatchesInMultipleReportsFilteredLogs
return positions.stream()
    .map(position -> new MatchPosition(position / pageSize, position % pageSize))
    .toList();
```

The critical invariant is that the `ORDER BY` clause in the inner CTE of the search query must be **identical** to the one in the corresponding log query. If they diverge, the computed positions will point to the wrong rows in the front-end view.

---

## Lifecycle of a Report

### 1. Ingestion (PUT /v1/reports/{id})

The caller pushes a PowSyBl `ReportNode` tree (JSON, deserialized via `ReportNodeJsonModule`).

**Case A — new report** (`createNewReport`):

1. `SizedReportNode.from(reportNode)` walks the in-memory tree, assigns `order_`/`end_order` to each node, and computes the highest severity bottom-up.
2. The root `ReportNodeEntity` is built with `id = caller-supplied UUID` and `root_node_id = self`.
3. `saveReportNodeRecursively` walks the sized tree, building `ReportNodeEntity` objects and accumulating them in a batch list.
4. Every 512 nodes, `self.saveBatchedReports(batch)` is called (via the self-injected proxy to get a fresh `@Transactional` boundary), which calls `saveAllAndFlush` and clears the list.

**Case B1 — append to existing report** (`appendReportElements`): Only children of the incoming `ReportNode` are appended. New ranges start at `existing_root.end_order + 1`. The root's `end_order` and `severity` are updated in-place.

**Case B2 — append to existing report**(`appendChildReportElements`): Append as one child with a caller supplied UIID

**Case C — replace children** (`PUT /v1/reports/{id}/replace` → `createOrReplaceReport`): Existing children are deleted (see delete logic below), the root entity properties are updated, and new children are inserted using the same batch mechanism as Case A.

### 2. Reading the tree view (GET /v1/reports/{id})

Returns the `Report` DTO: a recursive structure of containers (non-leaf nodes), no leaf logs.

Query: `findAllContainersByRootNodeId` — JPQL, filters `is_leaf = false`, orders by `order_ ASC`. Uses `root_node_and_container_idx`.

`ReportMapper.map()` reconstructs the tree in memory from the flat ordered list: it builds a `Map<UUID, Report>` and wires parent-child relationships by looking up `parentId` in that map.

### 3. Reading logs (GET /v1/reports/{id}/logs)

Returns flat `ReportLog` objects (message + severity + depth + parentId) with optional filtering and pagination.

The endpoint resolves the requested node's `order_`/`end_order` bounds first (one `findById`), then issues one of:

| Condition | Query method | Index used |
| --- | --- | --- |
| No filters | `findPagedReportsByRootNodeIdAndOrderAndMessage` (message=`%`) | `root_node_orders_idx` |
| Severity filter | `findPagedReportsByRootNodeIdAndOrderAndMessageAndSeverities` | `root_node_orders_idx` |
| Multi-report | `findPagedReportsByMultipleRootNodeIdsAndOrderAndMessage` | `root_node_orders_idx` |

All log queries use `UPPER(message) LIKE UPPER(:pattern) ESCAPE '\\'`. The `_` and `%` characters in caller-supplied filters are escaped in `createMessageSqlPattern()` before being sent to SQL.

The `paged` flag controls whether a Spring `Pageable` is applied (`Pageable.unpaged()` returns everything in one shot).

### 4. Search (GET /v1/reports/{id}/logs/search)

Returns `List<MatchPosition>` — each entry is a `{page, indexInPage}` pair telling the front-end exactly where a search term appears within a filtered/paged log view.

Implementation: a native SQL CTE first numbers all rows matching the display filter with `ROW_NUMBER()`, then the outer query filters those numbered rows by the search pattern. The integer positions are mapped to `{page = pos / pageSize, index = pos % pageSize}`.

### 5. Deletion (DELETE /v1/reports/{id})

Deletion uses a **recursive CTE** (`findTreeFromRootReport`) to discover all descendant node IDs grouped by tree level:

```
WITH RECURSIVE included_nodes(id, level) AS (
    SELECT id, 0 FROM report_node WHERE id = :id
    UNION ALL
    SELECT r.id, level + 1
    FROM included_nodes i INNER JOIN report_node r ON r.parent_id = i.id
)
SELECT DISTINCT level, cast(id as varchar) FROM included_nodes;
```

This uses `report_node_parent_id_idx`. The results are grouped by `level` and deleted from deepest level to shallowest to respect the (now soft) parent reference. Each level's IDs are chunked to groups of 10 000 (`SQL_QUERY_MAX_PARAM_NUMBER`) to avoid hitting PostgreSQL's 65 535-parameter limit.

> **Note:** The FK constraint `parent_fk` was dropped in `changelog_20250422T150510Z.xml`. This avoids cascading constraint checks on bulk deletes but means referential integrity is enforced only by application logic.

### 6. Duplication (POST /v1/reports/{id}/duplicate)

Fetches all nodes via `findAllNodeDataByRootNodeId` (ordered by `depth, order_` so parents are always processed before children), generates new UUIDs for every node, wires `rootNode` and `parent` references using an `entityMapping`, and inserts in batches of 512. `order_`/`end_order` values are copied verbatim — they remain valid because the relative structure is identical.

---

## Key Constants and Tuning Parameters

| Constant | Value | Location |
| --- | --- | --- |
| `MAX_SIZE_INSERT_REPORT_BATCH` | 512 | `ReportService` — controls flush frequency during tree inserts |
| `SQL_QUERY_MAX_PARAM_NUMBER` | 10 000 | `ReportService` — chunks `IN` lists to stay under PG's 65 535-param limit |
| `MAX_MESSAGE_CHAR` | 500 | `SizedReportNode` — messages longer than 500 chars are truncated and an ERROR is logged |
| Hibernate `jdbc.batch_size` | 512 | `application.yaml` — must match `MAX_SIZE_INSERT_REPORT_BATCH` |
| `reWriteBatchedInserts` | true | JDBC URL flag — enables PostgreSQL driver-level batch rewriting |

---

## Self-Injection Pattern in ReportService

`ReportService` injects itself (`@Lazy ReportService self`). This is necessary because `saveBatchedReports` is annotated `@Transactional`: calling it from within the same bean instance would bypass the Spring proxy and the transaction would not be started. By calling `self.saveBatchedReports(...)`, each batch flush runs in its own transaction, allowing the JPA session to be cleared between batches and preventing memory exhaustion for large trees.

---

## Data Transfer Objects

| DTO | Used for | Key fields |
| --- | --- | --- |
| `Report` | Tree view response | `id, parentId, message, severity, depth, subReports[]` |
| `ReportLog` | Log list response | `message, severity, depth, parentId` |
| `ReportPage` | Wraps `Page<ReportLog>` | Thin wrapper for serialization |
| `MatchPosition` | Search response | `page, index` — position of a search-term hit in a paged log view |
| `ReportProjection` | Internal query result | Record; two constructors (with and without `order/endOrder/isLeaf`) |

---

## Severity Model

`Severity` is an enum with an explicit integer level:

| Value | Level |
| --- | --- |
| UNKNOWN | 0 |
| TRACE | 1 |
| DEBUG | 2 |
| DETAIL | 3 |
| INFO | 4 |
| WARN | 5 |
| ERROR | 6 |
| FATAL | 7 |

Each non-leaf container node stores the **highest severity of its entire subtree**. This is computed bottom-up by `SizedReportNode.getHighestSeverity()` at write time. Severity aggregation on append is handled incrementally in `appendReportElements`.

A node is a **leaf** (`is_leaf = true`) if and only if it has no children AND it carries a `SEVERITY_KEY` value in its PowSyBl `TypedValue` map. Container nodes have `is_leaf = false`.

---

## Liquibase Migration History (condensed)

| Changeset file | Key change |
| --- | --- |
| `20240610T080207Z` | Major rewrite: dropped old multi-table schema (REPORT, TREE_REPORT, REPORT_ELEMENT, …), created new `report_node` table with `parent_id`, `message`. Added `report_node_parent_id_idx`. |
| `20240925T080218Z` | Data  migration: collapsed the 2-level hierarchy (anonymous root + named  child) to a flat root. Moved severities and re-pointed grandchildren. |
| `20241121T111657Z` | Added `order_`, `end_order`, `is_leaf`, `root_node_id` columns. Added `root_node_orders_idx` and `root_node_and_container_idx`. Added `root_node_fk` FK. Dropped unused `nanos` column. |
| `20241206T151555Z` | Added `severity` column to `report_node` (was previously a separate `severity` table). Migrated data, dropped old table. |
| `20250422T150510Z` | Dropped `parent_fk` FK constraint (for bulk delete performance). |
| `20250514T114615Z` | Added `depth` column (integer, default 0). |

---

## REST API Summary

All endpoints are under `/v1/`.

| Method | Path | Description |
| --- | --- | --- |
| `PUT` | `/reports/{id}` | Create or append to a report |
| `PUT` | `/reports/{id}/replace` | Create or replace all children of a report |
| `POST` | `/reports/{id}/duplicate` | Duplicate an entire report tree, returns new UUID |
| `GET` | `/reports/{id}` | Get tree of container nodes (no leaf logs). Returns an empty report if not found. |
| `GET` | `/reports/{id}/aggregated-severities` | Get set of distinct severities present in the report |
| `GET` | `/reports/{id}/logs` | Get flat log list with optional severity/message filter and pagination |
| `GET` | `/reports/logs` | Same as above, across multiple report IDs (array param `reportIds`) |
| `GET` | `/reports/{id}/logs/search` | Get `MatchPosition` list for a search term within the filtered log view |
| `GET` | `/reports/logs/search` | Same as above, across multiple report IDs |
| `DELETE` | `/reports/{id}` | Delete a single report (optional `errorOnReportNotFound` param) |
| `DELETE` | `/reports` | Bulk delete by list of UUIDs (body) |

---

## Testing Approach

Tests use `@SpringBootTest` with H2 (no mocking of persistence). The `db-util` library (`DatasourceProxyBeanPostProcessor` + `SQLStatementCountValidator`) intercepts every SQL statement so that tests can assert exact counts of SELECT / INSERT / UPDATE / DELETE statements — for example:

```
SQLStatementCountValidator.reset();
reportService.createReport(id, reportNode);
assertRequestsCount(1, 1, 0, 0); // 1 SELECT, 1 INSERT, 0 UPDATEs, 0 DELETEs
```

This is the primary guard against N+1 query regressions. Any change to a read path should verify these counts do not increase unexpectedly.

