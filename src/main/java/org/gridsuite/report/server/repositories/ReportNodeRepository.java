/**
 *  Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.repositories;

import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query(value = "SELECT r FROM ReportNodeEntity r WHERE r.messageTemplate.key = :key")
    List<ReportNodeEntity> findByMessageTemplateKey(String key);
}
