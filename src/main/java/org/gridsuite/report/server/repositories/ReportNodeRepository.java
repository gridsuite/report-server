/**
 *  Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.repositories;

import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.gridsuite.report.server.entities.ReportProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Repository
public interface ReportNodeRepository extends JpaRepository<ReportNodeEntity, UUID> {

    @Query("""
        SELECT new org.gridsuite.report.server.entities.ReportProjection(
            rn.id, rn.message, rn.severity, rn.depth, rn.parentId,
            rn.order, rn.endOrder, rn.isLeaf
        )
        FROM ReportNodeEntity rn
        WHERE rn.rootNodeId = :rootNodeId
        ORDER BY rn.depth, rn.order
        """)
    List<ReportProjection> findAllNodeDataByRootNodeId(UUID rootNodeId);

    @Query("""
        SELECT new org.gridsuite.report.server.entities.ReportProjection(
            rn.id,
            rn.message,
            rn.severity,
            rn.depth,
            rn.parentId
        )
        FROM ReportNodeEntity rn
        WHERE rn.rootNodeId = :rootNodeId AND rn.isLeaf = false
        ORDER BY rn.order ASC
        """)
    List<ReportProjection> findAllContainersByRootNodeId(UUID rootNodeId);

    @Query("""
        SELECT DISTINCT rn.severity
        FROM ReportNodeEntity rn
        WHERE
            rn.rootNodeId = :rootNodeId
            AND rn.order BETWEEN :orderAfter AND :orderBefore
        """)
    Set<String> findDistinctSeveritiesByRootNodeIdAndOrder(UUID rootNodeId, int orderAfter, int orderBefore);

    @Query("""
        SELECT new org.gridsuite.report.server.entities.ReportProjection(
            rn.id,
            rn.message,
            rn.severity,
            rn.depth,
            rn.parentId
        )
        FROM ReportNodeEntity rn
        WHERE
                rn.rootNodeId = :rootNodeId
                AND rn.order BETWEEN :orderAfter AND :orderBefore
                AND UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
        ORDER BY rn.order ASC
        """)
    Page<ReportProjection> findPagedReportsByRootNodeIdAndOrderAndMessage(UUID rootNodeId, int orderAfter, int orderBefore, String message, Pageable pageable);

    @Query("""
        SELECT new org.gridsuite.report.server.entities.ReportProjection(
            rn.id,
            rn.message,
            rn.severity,
            rn.depth,
            rn.parentId
        )
        FROM ReportNodeEntity rn
        WHERE
                rn.rootNodeId = :rootNodeId
                AND rn.order BETWEEN :orderAfter AND :orderBefore
                AND UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
                AND rn.severity IN (:severities)
        ORDER BY rn.order ASC
        """)
    Page<ReportProjection> findPagedReportsByRootNodeIdAndOrderAndMessageAndSeverities(UUID rootNodeId, int orderAfter, int orderBefore, String message, Set<String> severities, Pageable pageable);

    // report_node has two self-referential FK constraints: root_node_fk (root_node_id -> id) and parent_fk (parent_id -> id).
    // a single statement DELETE WHERE root_node_id = ? is safe in PostgreSQL because it evaluates FK constraints
    // at statement end, once all rows in the tree are already removed.
    // See: https://www.postgresql.org/docs/current/sql-set-constraints.html
    @Modifying
    @Query("DELETE FROM ReportNodeEntity rn WHERE rn.rootNodeId = :rootNodeId")
    int deleteAllByRootNodeId(@Param("rootNodeId") UUID rootNodeId);

    @Modifying
    @Query("DELETE FROM ReportNodeEntity rn WHERE rn.rootNodeId = :rootNodeId AND rn.id != :rootNodeId")
    void deleteAllChildrenByRootNodeId(@Param("rootNodeId") UUID rootNodeId);

    @Query(value = """
        WITH filtered_rows AS (
            SELECT ROW_NUMBER() OVER (ORDER BY rn.order_ ASC) - 1 as row_position, rn.message
            FROM report_node rn
            WHERE
                rn.root_node_id = :rootNodeId
                AND rn.order_ BETWEEN :orderAfter AND :orderBefore
                AND UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
        )
        SELECT row_position
        FROM filtered_rows
        WHERE UPPER(message) LIKE UPPER(:searchPattern) ESCAPE '\\'
        ORDER BY row_position ASC
        """, nativeQuery = true)
    List<Integer> findRelativePositionsByRootNodeIdAndOrderAndMessage(UUID rootNodeId, int orderAfter, int orderBefore, String message, String searchPattern);

    @Query(value = """
        WITH filtered_rows AS (
            SELECT ROW_NUMBER() OVER (ORDER BY rn.order_ ASC) - 1 as row_position, rn.message
            FROM report_node rn
            WHERE
                rn.root_node_id = :rootNodeId
                AND rn.order_ BETWEEN :orderAfter AND :orderBefore
                AND UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
                AND rn.severity IN (:severities)
        )
        SELECT row_position
        FROM filtered_rows
        WHERE UPPER(message) LIKE UPPER(:searchPattern) ESCAPE '\\'
        ORDER BY row_position ASC
        """, nativeQuery = true)
    List<Integer> findRelativePositionsByRootNodeIdAndOrderAndMessageAndSeverities(UUID rootNodeId, int orderAfter, int orderBefore, String message, String searchPattern, Set<String> severities);

    @Query(value = """
        SELECT CAST(rn.id AS VARCHAR), rn.message, rn.severity, rn.depth, CAST(rn.parent_id AS VARCHAR)
        FROM unnest(:rootNodeIds) WITH ORDINALITY AS input_id(id, ord)
        JOIN report_node rn ON rn.root_node_id = input_id.id
        WHERE
            UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
        ORDER BY
            input_id.ord,
            rn.order_ ASC
        """, nativeQuery = true)
    Page<Object[]> findPagedReportsByMultipleRootNodeIdsAndOrderAndMessage(
        UUID[] rootNodeIds, String message, Pageable pageable);

    @Query(value = """
        SELECT CAST(rn.id AS VARCHAR), rn.message, rn.severity, rn.depth, CAST(rn.parent_id AS VARCHAR)
        FROM unnest(:rootNodeIds) WITH ORDINALITY AS input_id(id, ord)
        JOIN report_node rn ON rn.root_node_id = input_id.id
        WHERE
            UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
            AND rn.severity IN (:severities)
        ORDER BY
            input_id.ord,
            rn.order_ ASC
        """, nativeQuery = true)
    Page<Object[]> findPagedReportsByMultipleRootNodeIdsAndOrderAndMessageAndSeverities(
        UUID[] rootNodeIds, String message, Set<String> severities, Pageable pageable);

    @Query(value = """
        WITH ordered_reports AS (
            SELECT
                ROW_NUMBER() OVER (
                    ORDER BY input_id.ord, rn.order_ ASC
                ) - 1 as row_position,
                rn.message
            FROM unnest(:rootNodeIds) WITH ORDINALITY AS input_id(id, ord)
            JOIN report_node rn ON rn.root_node_id = input_id.id
            WHERE UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
        )
        SELECT row_position
        FROM ordered_reports
        WHERE UPPER(message) LIKE UPPER(:searchPattern) ESCAPE '\\'
        ORDER BY row_position
        """, nativeQuery = true)
    List<Integer> findRelativePositionsByMultipleRootNodeIdsAndOrderAndMessage(
        UUID[] rootNodeIds, String message, String searchPattern);

    @Query(value = """
        WITH ordered_reports AS (
            SELECT
                ROW_NUMBER() OVER (
                    ORDER BY input_id.ord, rn.order_ ASC
                ) - 1 as row_position,
                rn.message
            FROM unnest(:rootNodeIds) WITH ORDINALITY AS input_id(id, ord)
            JOIN report_node rn ON rn.root_node_id = input_id.id
            WHERE UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
            AND rn.severity IN (:severities)
        )
        SELECT row_position
        FROM ordered_reports
        WHERE UPPER(message) LIKE UPPER(:searchPattern) ESCAPE '\\'
        ORDER BY row_position
        """, nativeQuery = true)
    List<Integer> findRelativePositionsByMultipleRootNodeIdsAndOrderAndMessageAndSeverities(
        UUID[] rootNodeIds, String message, String searchPattern, Set<String> severities);
}
