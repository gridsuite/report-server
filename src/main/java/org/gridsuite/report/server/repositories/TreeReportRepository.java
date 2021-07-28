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
import org.springframework.transaction.annotation.Transactional;

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

    @Query(value = "Select Cast(t.idNode as varchar) from treereport t where t.report = ?1", nativeQuery = true)
    List<String> getIdNodesByParentReportId(UUID parentId);

    @Transactional
    default void deleteAllById(List<UUID> lst) {
        lst.forEach(this::deleteById);
    }

    /* get all treeReports id, from the given root to the last leaf sub report */
    @Query(value = "WITH RECURSIVE get_nodes(idNode, strIdNode) AS ("
        + "SELECT t.idNode, Cast(t.idNode as varchar) FROM treeReport t where t.idNode = ?1 "
        + "UNION ALL("
        + "SELECT t.idNode, Cast(t.idNode as varchar)"
        + "FROM treeReport t, get_nodes sg "
        + "WHERE t.parentreport = sg.idNode)) "
        + "SELECT strIdNode from get_nodes", nativeQuery = true)
    List<String> getSubReportsNodes(UUID reportId);

}
