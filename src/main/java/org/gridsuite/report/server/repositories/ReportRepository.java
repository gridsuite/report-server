/**
 *  Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.repositories;

import org.gridsuite.report.server.entities.ReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, UUID> {

    @Query(value = "FROM #{#entityName} as t where t.reportId IN :uuidSet")
    Collection<ReportEntity> findAllByIdReport(Set<UUID> uuidSet);

    @Modifying
    @Query(value = "DELETE FROM #{#entityName} AS t WHERE t.reportId = :id")
    void deleteByReportId(UUID id);
}
