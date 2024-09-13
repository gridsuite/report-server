/**
 *  Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.repositories;

import org.gridsuite.report.server.entities.LogProjection;
import org.gridsuite.report.server.entities.ReportNodeEntity;
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
    List<ReportNodeEntity> findAllWithChildrenByIdIn(List<UUID> ids);

    @EntityGraph(attributePaths = {"severities"}, type = EntityGraph.EntityGraphType.LOAD)
    List<ReportNodeEntity> findAllWithSeveritiesByIdIn(List<UUID> ids);

    List<ReportNodeEntity> findAllByMessage(String message);

    List<ReportNodeEntity> findAllByParentIdAndMessage(UUID parentId, String messageKey);

    @EntityGraph(attributePaths = {"severities"}, type = EntityGraph.EntityGraphType.LOAD)
    List<LogProjection> findAllByIdInAndMessageContainingIgnoreCase(List<UUID> ids, String message);

    @EntityGraph(attributePaths = {"severities"}, type = EntityGraph.EntityGraphType.LOAD)
    List<LogProjection> findAllByIdInAndMessageContainingIgnoreCaseAndSeveritiesIn(List<UUID> ids, String message, Set<String> severities);

    @Modifying
    @Query(value = """
        BEGIN;
        DELETE FROM severity WHERE report_node_id IN :ids ;
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
