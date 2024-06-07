/**
 *  Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.repositories;

import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Repository
public interface ReportNodeRepository extends JpaRepository<ReportNodeEntity, UUID> {

    @Query(value = "SELECT r FROM ReportNodeEntity r LEFT JOIN FETCH r.children WHERE r.id = :id")
    Optional<ReportNodeEntity> findByIdWithChildren(UUID id);

    @Query(value = "SELECT r FROM ReportNodeEntity r LEFT JOIN FETCH r.messageTemplate WHERE r.id IN :ids")
    List<ReportNodeEntity> findAllWithMessageTemplateByIdIn(List<UUID> ids);

    @Query(value = "SELECT r FROM ReportNodeEntity r LEFT JOIN FETCH r.values WHERE r.id IN :ids")
    List<ReportNodeEntity> findAllWithValuesByIdIn(List<UUID> ids);

    @Query(value = "SELECT r FROM ReportNodeEntity r LEFT JOIN FETCH r.severities WHERE r.id IN :ids")
    List<ReportNodeEntity> findAllWithSeveritiesByIdIn(List<UUID> ids);

    @Query(value = "SELECT r FROM ReportNodeEntity r WHERE r.messageTemplate.key = :key")
    List<ReportNodeEntity> findByMessageTemplateKey(String key);

    List<ReportNodeEntity> findAllByParentIdAndMessageTemplateKey(UUID parentId, String messageTemplateKey);

    @Modifying
    @Query(value = """
        BEGIN;
        DELETE FROM severity_level WHERE report_node_id IN :ids ;
        DELETE FROM report_node_values WHERE report_node_id IN :ids ;
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
