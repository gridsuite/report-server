/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import org.gridsuite.report.server.dto.Report;
import org.gridsuite.report.server.entities.ReportProjection;

import java.util.*;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public final class ReportNodeMapper {

    private ReportNodeMapper() {
        // Should not be instantiated
    }

    public static Report map(List<ReportProjection> reportNodeEntities) {
        if (reportNodeEntities == null || reportNodeEntities.isEmpty()) {
            return null;
        }
        Map<UUID, Report> reportsMap = new HashMap<>();
        mapRootNode(reportNodeEntities.get(0), reportsMap);
        reportNodeEntities.subList(1, reportNodeEntities.size()).forEach(entity -> mapReportNodeEntity(entity, reportsMap));
        return reportsMap.get(reportNodeEntities.get(0).id());
    }

    private static void mapRootNode(ReportProjection rootReportNodeEntity, Map<UUID, Report> reportsMap) {
        Report rootReport = createReportFromNode(rootReportNodeEntity);
        reportsMap.put(rootReportNodeEntity.id(), rootReport);
    }

    private static void mapReportNodeEntity(ReportProjection reportNodeEntity, Map<UUID, Report> reports) {
        Optional.ofNullable(reports.get(reportNodeEntity.id()))
            .ifPresentOrElse(
                report -> report.getSeverities().add(Severity.fromValue(reportNodeEntity.severity())),
                () -> Optional.ofNullable(reportNodeEntity.parentId())
                    .map(reports::get)
                    .ifPresent(parentReport -> {
                        Report report = parentReport.addEmptyReport();
                        report.setMessage(reportNodeEntity.message());
                        mapValues(reportNodeEntity, report);
                        reports.put(reportNodeEntity.id(), report);
                    }));
    }

    private static Report createReportFromNode(ReportProjection reportNodeEntity) {
        Report report = new Report();
        report.setMessage(Optional.ofNullable(reportNodeEntity.message()).orElse(reportNodeEntity.id().toString()));
        mapValues(reportNodeEntity, report);
        return report;
    }

    private static void mapValues(ReportProjection reportNodeEntity, Report report) {
        report.getSeverities().add(Severity.fromValue(reportNodeEntity.severity()));
        report.setId(reportNodeEntity.id());
        report.setParentId(reportNodeEntity.parentId());
    }
}
