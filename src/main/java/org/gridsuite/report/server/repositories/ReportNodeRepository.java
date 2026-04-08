/**
 *  Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.repositories;

import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.gridsuite.report.server.entities.ReportNodeId;
import org.gridsuite.report.server.entities.ReportProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Repository
public interface ReportNodeRepository extends JpaRepository<ReportNodeEntity, ReportNodeId> {

    Optional<ReportNodeEntity> findByIdAndOrder(UUID id, int order);

    List<ReportNodeEntity> findByIdOrderByOrder(UUID id);

    @Modifying
    @Query(value = "DELETE FROM report_node WHERE id = :reportId", nativeQuery = true)
    void deleteAllByReportId(UUID reportId);

    @Modifying
    @Query(value = "DELETE FROM report_node WHERE id = :reportId AND order_ > 0", nativeQuery = true)
    void deleteChildrenByReportId(UUID reportId);

    @Query("""
        SELECT new org.gridsuite.report.server.entities.ReportProjection(
            rn.id, rn.message, rn.severity, rn.depth, rn.parentOrder,
            rn.order, rn.endOrder, rn.isLeaf
        )
        FROM ReportNodeEntity rn
        WHERE rn.id = :reportId
        ORDER BY rn.depth, rn.order
        """)
    List<ReportProjection> findAllNodeDataByReportId(UUID reportId);

    @Query("""
        SELECT new org.gridsuite.report.server.entities.ReportProjection(
            rn.id,
            rn.message,
            rn.severity,
            rn.depth,
            rn.parentOrder,
            rn.order,
            rn.endOrder,
            rn.isLeaf
        )
        FROM ReportNodeEntity rn
        WHERE rn.id = :reportId AND rn.isLeaf = false
        ORDER BY rn.order ASC
        """)
    List<ReportProjection> findAllContainersByReportId(UUID reportId);

    @Query("""
        SELECT DISTINCT rn.severity
        FROM ReportNodeEntity rn
        WHERE
            rn.id = :reportId
            AND rn.order BETWEEN :orderAfter AND :orderBefore
        """)
    Set<String> findDistinctSeveritiesByReportIdAndOrder(UUID reportId, int orderAfter, int orderBefore);

    @Query("""
        SELECT new org.gridsuite.report.server.entities.ReportProjection(
            rn.id,
            rn.message,
            rn.severity,
            rn.depth,
            rn.parentOrder
        )
        FROM ReportNodeEntity rn
        WHERE
                rn.id = :reportId
                AND rn.order BETWEEN :orderAfter AND :orderBefore
                AND UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
        ORDER BY rn.order ASC
        """)
    Page<ReportProjection> findPagedReportsByReportIdAndOrderAndMessage(UUID reportId, int orderAfter, int orderBefore, String message, Pageable pageable);

    @Query("""
        SELECT new org.gridsuite.report.server.entities.ReportProjection(
            rn.id,
            rn.message,
            rn.severity,
            rn.depth,
            rn.parentOrder
        )
        FROM ReportNodeEntity rn
        WHERE
                rn.id = :reportId
                AND rn.order BETWEEN :orderAfter AND :orderBefore
                AND UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
                AND rn.severity IN (:severities)
        ORDER BY rn.order ASC
        """)
    Page<ReportProjection> findPagedReportsByReportIdAndOrderAndMessageAndSeverities(UUID reportId, int orderAfter, int orderBefore, String message, Set<String> severities, Pageable pageable);

    @Query(value = """
        WITH filtered_rows AS (
            SELECT ROW_NUMBER() OVER (ORDER BY rn.order_ ASC) - 1 as row_position, rn.message
            FROM report_node rn
            WHERE
                rn.id = :reportId
                AND rn.order_ BETWEEN :orderAfter AND :orderBefore
                AND UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
        )
        SELECT row_position
        FROM filtered_rows
        WHERE UPPER(message) LIKE UPPER(:searchPattern) ESCAPE '\\'
        ORDER BY row_position ASC
        """, nativeQuery = true)
    List<Integer> findRelativePositionsByReportIdAndOrderAndMessage(UUID reportId, int orderAfter, int orderBefore, String message, String searchPattern);

    @Query(value = """
        WITH filtered_rows AS (
            SELECT ROW_NUMBER() OVER (ORDER BY rn.order_ ASC) - 1 as row_position, rn.message
            FROM report_node rn
            WHERE
                rn.id = :reportId
                AND rn.order_ BETWEEN :orderAfter AND :orderBefore
                AND UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
                AND rn.severity IN (:severities)
        )
        SELECT row_position
        FROM filtered_rows
        WHERE UPPER(message) LIKE UPPER(:searchPattern) ESCAPE '\\'
        ORDER BY row_position ASC
        """, nativeQuery = true)
    List<Integer> findRelativePositionsByReportIdAndOrderAndMessageAndSeverities(UUID reportId, int orderAfter, int orderBefore, String message, String searchPattern, Set<String> severities);

    @Query(value = """
        SELECT CAST(rn.id AS VARCHAR), rn.message, rn.severity, rn.depth, rn.parent_order
        FROM unnest(:reportIds) WITH ORDINALITY AS input_id(id, ord)
        JOIN report_node rn ON rn.id = input_id.id
        WHERE
            UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
        ORDER BY
            input_id.ord,
            rn.order_ ASC
        """, nativeQuery = true)
    Page<Object[]> findPagedReportsByMultipleReportIdsAndOrderAndMessage(
        UUID[] reportIds, String message, Pageable pageable);

    @Query(value = """
        SELECT CAST(rn.id AS VARCHAR), rn.message, rn.severity, rn.depth, rn.parent_order
        FROM unnest(:reportIds) WITH ORDINALITY AS input_id(id, ord)
        JOIN report_node rn ON rn.id = input_id.id
        WHERE
            UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
            AND rn.severity IN (:severities)
        ORDER BY
            input_id.ord,
            rn.order_ ASC
        """, nativeQuery = true)
    Page<Object[]> findPagedReportsByMultipleReportIdsAndOrderAndMessageAndSeverities(
        UUID[] reportIds, String message, Set<String> severities, Pageable pageable);

    @Query(value = """
        WITH ordered_reports AS (
            SELECT
                ROW_NUMBER() OVER (
                    ORDER BY input_id.ord, rn.order_ ASC
                ) - 1 as row_position,
                rn.message
            FROM unnest(:reportIds) WITH ORDINALITY AS input_id(id, ord)
            JOIN report_node rn ON rn.id = input_id.id
            WHERE UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
        )
        SELECT row_position
        FROM ordered_reports
        WHERE UPPER(message) LIKE UPPER(:searchPattern) ESCAPE '\\'
        ORDER BY row_position
        """, nativeQuery = true)
    List<Integer> findRelativePositionsByMultipleReportIdsAndOrderAndMessage(
        UUID[] reportIds, String message, String searchPattern);

    @Query(value = """
        WITH ordered_reports AS (
            SELECT
                ROW_NUMBER() OVER (
                    ORDER BY input_id.ord, rn.order_ ASC
                ) - 1 as row_position,
                rn.message
            FROM unnest(:reportIds) WITH ORDINALITY AS input_id(id, ord)
            JOIN report_node rn ON rn.id = input_id.id
            WHERE UPPER(rn.message) LIKE UPPER(:message) ESCAPE '\\'
            AND rn.severity IN (:severities)
        )
        SELECT row_position
        FROM ordered_reports
        WHERE UPPER(message) LIKE UPPER(:searchPattern) ESCAPE '\\'
        ORDER BY row_position
        """, nativeQuery = true)
    List<Integer> findRelativePositionsByMultipleReportIdsAndOrderAndMessageAndSeverities(
        UUID[] reportIds, String message, String searchPattern, Set<String> severities);
}
