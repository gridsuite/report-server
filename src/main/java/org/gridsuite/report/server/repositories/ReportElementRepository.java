/**
 *  Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.repositories;

import jakarta.persistence.criteria.Predicate;
import org.gridsuite.report.server.entities.ReportElementEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Repository
public interface ReportElementRepository extends JpaRepository<ReportElementEntity, UUID>, JpaSpecificationExecutor<ReportElementEntity> {
    @EntityGraph(attributePaths = {"values"}, type = EntityGraph.EntityGraphType.LOAD)
    List<ReportElementEntity> findAllWithValuesByIdReportIn(List<UUID> uuids);

    List<ReportElementEntity.ProjectionIdReport> findIdReportByParentReportIdNodeIn(Collection<UUID> reportId);

    /* TODO to remove when upgrade to new spring-data-jpa, use deleteAllByIdInBatch */
    void deleteAllByIdReportIn(List<UUID> lst);

    default Specification<ReportElementEntity> getReportElementsSpecification(List<UUID> uuids, Set<String> severityLevels) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("parentReport").get("idNode").in(uuids));
            if (severityLevels != null) {
                predicates.add(root.join("values").get("value").in(severityLevels));
            }
            //query.distinct(true);
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
