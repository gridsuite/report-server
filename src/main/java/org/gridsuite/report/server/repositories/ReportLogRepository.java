/**
 *  Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.repositories;

import org.gridsuite.report.server.entities.ReportLogEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Repository
public interface ReportLogRepository extends JpaRepository<ReportLogEntity, UUID> {

    @EntityGraph(attributePaths = {"severities"}, type = EntityGraph.EntityGraphType.LOAD)
    List<ReportLogEntity> findAllByIdInAndMessageContainingIgnoreCase(List<UUID> ids, String message);

    @EntityGraph(attributePaths = {"severities"}, type = EntityGraph.EntityGraphType.LOAD)
    List<ReportLogEntity> findAllByIdInAndMessageContainingIgnoreCaseAndSeveritiesIn(List<UUID> ids, String message, Set<String> severities);
}
