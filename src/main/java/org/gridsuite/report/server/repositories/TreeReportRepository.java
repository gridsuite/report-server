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

    List<TreeReportEntity.ProjectionIdNode> findIdNodeByReportId(UUID parentId);

    @Query(value = "WITH " +
            "RECURSIVE ids AS (" +
            "SELECT t.id_node FROM tree_report t WHERE t.id_node = ?1" +
            " UNION " +
            "SELECT t.id_node FROM tree_report t, ids ft WHERE t.parent_report = ft.id_node" +
            ")," +
            "treeReports as (" +
            "select tr.*," +
            "(select jsonb_object_agg(d.dictionary_key, d.dictionary) from tree_report_entity_dictionary d where tr.id_node=d.tree_report_entity_id_node group by tree_report_entity_id_node) as dictionary," +
            //"(select jsonb_object_agg(v.name, jsonb_build_object('type',v.type, 'value',v.value_, 'value_type',v.value_type)) from tree_report_entity_values v where tr.id_node=v.tree_report_entity_id_node group by tree_report_entity_id_node) as values" +
            "(select jsonb_agg(jsonb_build_object('name',v.name, 'type',v.type, 'value',v.value_, 'valueType',v.value_type)) from tree_report_entity_values v where tr.id_node=v.tree_report_entity_id_node group by tree_report_entity_id_node) as values" +
            " from ids left join tree_report tr using(id_node) order by nanos asc" +
            ") " +
            "SELECT row_to_json(treeReports) FROM treeReports", nativeQuery = true)
    List<String> findAllTreeReportRecursivelyByParentTreeReport(UUID parentId);

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
