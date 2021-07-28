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
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Repository
public interface ReportElementRepository extends JpaRepository<ReportElementEntity, UUID> {

    List<ReportElementEntity> findAllByParentReportIdNode(UUID uuid);

    @Transactional
    default void deleteAllById(Collection<UUID> lst) {
        lst.forEach(this::deleteById);
    }

    @Query(value = "select Cast(r.idReport as varchar) from ReportElement r where r.parentReport in (?1)", nativeQuery = true)
    List<String> getNodesIdForReportNative(Collection<UUID> reportId);

}
