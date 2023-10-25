/**
 *  Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.repositories;

import org.gridsuite.report.server.entities.ReportElementEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Repository
public interface ReportElementRepository extends JpaRepository<ReportElementEntity, UUID> {

    @EntityGraph(attributePaths = {"values"}, type = EntityGraph.EntityGraphType.LOAD)
    List<ReportElementEntity> findAllByParentReportIdNodeIn(Collection<UUID> uuids);

    List<ReportElementEntity.ProjectionIdReport> findIdReportByParentReportIdNodeIn(Collection<UUID> reportId);

    /* TODO to remove when upgrade to new spring-data-jpa, use deleteAllByIdInBatch */
    void deleteAllByIdReportIn(List<UUID> lst);
}
