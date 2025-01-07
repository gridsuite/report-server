/**
 *  Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.repositories;

import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.gridsuite.report.server.entities.ReportProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Repository
public interface ReportNodeRepository extends JpaRepository<ReportNodeEntity, UUID> {

    @EntityGraph(attributePaths = {"children"}, type = EntityGraph.EntityGraphType.LOAD)
    List<ReportNodeEntity> findAllWithChildrenById(UUID rootNodeId);

    @Query("""
        SELECT new org.gridsuite.report.server.entities.ReportProjection(
            rn.id,
            rn.message,
            rn.severity,
            rn.parent.id
        )
        FROM ReportNodeEntity rn
        WHERE rn.rootNode.id = :rootNodeId AND rn.isLeaf = false
        ORDER BY rn.order ASC
        """)
    List<ReportProjection> findAllContainersByRootNodeId(UUID rootNodeId);

    @Query("""
        SELECT new org.gridsuite.report.server.entities.ReportProjection(
            rn.id,
            rn.message,
            rn.severity,
            rn.parent.id
        )
        FROM ReportNodeEntity rn
        WHERE
                rn.rootNode.id = :rootNodeId
                AND rn.order BETWEEN :orderAfter AND :orderBefore
                AND UPPER(rn.message) LIKE UPPER(:message)
        ORDER BY rn.order ASC
        """)
    List<ReportProjection> findAllReportsByRootNodeIdAndOrderAndMessage(UUID rootNodeId, int orderAfter, int orderBefore, String message);

    @Query(value = """
        WITH RECURSIVE children(id, severity) AS (
            SELECT id, severity
            FROM report_node
            WHERE parent_id = :parentId

            UNION ALL

            SELECT rn.id, rn.severity
            FROM report_node rn
            JOIN children c ON rn.parent_id = c.id
        )
        SELECT severity FROM children
        GROUP BY severity;
        """, nativeQuery = true)
    Set<String> findDistinctSeveritiesByParentId(UUID parentId);

    @Query("""
            SELECT new org.gridsuite.report.server.entities.ReportProjection(
                rn.id,
                rn.message,
                rn.severity,
                rn.parent.id
            )
            FROM ReportNodeEntity rn
            WHERE
                    rn.rootNode.id = :rootNodeId
                    AND rn.order BETWEEN :orderAfter AND :orderBefore
                    AND UPPER(rn.message) LIKE UPPER(:message)
                    AND rn.severity IN (:severities)
            ORDER BY rn.order ASC
            """)
    List<ReportProjection> findAllReportsByRootNodeIdAndOrderAndMessageAndSeverities(UUID rootNodeId, int orderAfter, int orderBefore, String message, Set<String> severities);

    @Modifying
    @Query(value = """
        BEGIN;
        DELETE FROM report_node WHERE id IN :ids ;
        COMMIT;
        """, nativeQuery = true)
    void deleteByIdIn(List<UUID> ids);

    @Query(value = """
        WITH RECURSIVE included_nodes(id, level) AS (
            SELECT id, 0 as level
            FROM report_node r
            WHERE r.id = :id

            UNION ALL

            SELECT r.id, level + 1
            FROM included_nodes incn
            INNER JOIN report_node r ON r.parent_id = incn.id
        )
        SELECT DISTINCT level, cast(id as varchar) FROM included_nodes;
        """, nativeQuery = true)
    List<Object[]> findTreeFromRootReport(UUID id);
}
