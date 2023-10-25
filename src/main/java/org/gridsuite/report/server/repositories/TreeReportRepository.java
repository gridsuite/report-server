/**
 *  Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.repositories;

import org.gridsuite.report.server.entities.TreeReportEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Repository
public interface TreeReportRepository extends JpaRepository<TreeReportEntity, UUID> {

    List<TreeReportEntity> findAllByReportId(UUID uuid);

    @EntityGraph(attributePaths = {"values", "dictionary"}, type = EntityGraph.EntityGraphType.LOAD)
    List<TreeReportEntity> findAllByIdNodeIn(Collection<UUID> uuids);

    List<TreeReportEntity.ProjectionIdNode> findIdNodeByReportId(UUID parentId);

    @Query(value = "WITH RECURSIVE cte AS ("
        + "SELECT t.id_node FROM tree_report t WHERE t.id_node = ?1 "
        + "UNION "
        + "SELECT t.id_node FROM tree_report t, cte ft WHERE t.parent_report = ft.id_node"
        + ") SELECT id_node FROM cte", nativeQuery = true)
    //TODO we should be able to get hibernate to do this projection..
    List<String> findAllTreeReportIdsRecursivelyByParentTreeReport(UUID parentId);

    /**
     * get all treeReports id, from the given root to the last leaf subreport
     */
    @Query(value = "WITH RECURSIVE cte AS ("
        + "SELECT t.id_node FROM tree_report t where t.id_node = ?1 "
        + "UNION "
        + "SELECT t.id_node FROM tree_report t, cte sg WHERE t.parent_report = sg.id_node"
        + ") SELECT id_node from cte", nativeQuery = true)
    //TODO we should be able to get hibernate to do this projection..
    List<String> getSubReportsNodes(UUID reportId);

    List<TreeReportEntity> findAllByReportIdAndName(UUID uuid, String name);

    List<TreeReportEntity> findByName(String name);
}
