/**
 *  Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.repositories;

import org.gridsuite.report.server.entities.ReportElementEntity;
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
public interface ReportElementRepository extends JpaRepository<ReportElementEntity, UUID> {

    @Query(value = "WITH RECURSIVE cte AS ("
            + "SELECT t.id_node FROM tree_report t WHERE t.id_node = ?1 "
            + "UNION "
            + "SELECT t.id_node FROM tree_report t, cte ft WHERE t.parent_report = ft.id_node"
            + ") SELECT re.* FROM cte left join report_element re on cte.id_node=re.parent_report where re.id_report is not null order by nanos asc", nativeQuery = true)
    List<ReportElementEntity> findAllReportElementsRecursivelyByParentReportId(UUID parentUuid);

    List<ReportElementEntity.ProjectionIdReport> findIdReportByParentReportIdIn(Collection<UUID> reportId);
}
