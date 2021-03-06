/**
 *  Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.repositories;

import org.gridsuite.report.server.entities.TreeReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Repository
public interface TreeReportRepository extends JpaRepository<TreeReportEntity, UUID> {

    List<TreeReportEntity> findAllByReportId(UUID uuid);

    List<TreeReportEntity> findAllByReportIdAndName(UUID reportId, String name);

    List<TreeReportEntity> findAllByParentReportIdNode(UUID uuid);

    List<TreeReportEntity.ProjectionIdNode> findIdNodeByReportId(UUID parentId);

    /* TODO to remove when upgrade to new spring-data-jpa, use deleteAllByIdInBatch */
    void deleteAllByIdNodeIn(List<UUID> lst);

    /* get all treeReports id, from the given root to the last leaf sub report */
    @Query(value = "WITH RECURSIVE get_nodes(idNode) AS ("
        + "SELECT t.id_node FROM tree_report t where t.id_node = ?1 "
        + "UNION ALL("
        + "SELECT t.id_node "
        + "FROM tree_report t, get_nodes sg "
        + "WHERE t.parent_report = sg.idNode)) "
        + "SELECT cast(idNode as varchar) from get_nodes", nativeQuery = true)
    //TODO we should be able to get hibernate to do this projection..
    //TODO we cast to varchar otherwise we get
    //     org.hibernate.MappingException: No Dialect mapping for JDBC type: 1111
    //     To be revisited when https://github.com/spring-projects/spring-data-jpa/issues/1796
    //     is fixed.
    List<String> getSubReportsNodes(UUID reportId);
}
