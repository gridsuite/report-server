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
public final class ReportMapper {

    private ReportMapper() {
        // Should not be instantiated
    }

    public static Report map(List<ReportProjection> reportProjections) {
        if (reportProjections == null || reportProjections.isEmpty()) {
            return null;
        }
        Map<UUID, Report> reportsMap = new HashMap<>();
        mapRootNode(reportProjections.get(0), reportsMap);
        reportProjections.subList(1, reportProjections.size()).forEach(entity -> mapReportNodeEntity(entity, reportsMap));
        return reportsMap.get(reportProjections.get(0).id());
    }

    private static void mapRootNode(ReportProjection reportProjection, Map<UUID, Report> reportsMap) {
        Report rootReport = createReportFromNode(reportProjection);
        reportsMap.put(reportProjection.id(), rootReport);
    }

    private static void mapReportNodeEntity(ReportProjection reportProjection, Map<UUID, Report> reports) {
        Optional.ofNullable(reports.get(reportProjection.id()))
            .ifPresentOrElse(
                report -> report.getSeverities().add(Severity.fromValue(reportProjection.severity())),
                () -> Optional.ofNullable(reportProjection.parentId())
                    .map(reports::get)
                    .ifPresent(parentReport -> {
                        Report report = parentReport.addEmptyReport();
                        report.setMessage(reportProjection.message());
                        mapValues(reportProjection, report);
                        reports.put(reportProjection.id(), report);
                    }));
    }

    private static Report createReportFromNode(ReportProjection reportProjection) {
        Report report = new Report();
        report.setMessage(Optional.ofNullable(reportProjection.message()).orElse(reportProjection.id().toString()));
        mapValues(reportProjection, report);
        return report;
    }

    private static void mapValues(ReportProjection reportProjection, Report report) {
        report.getSeverities().add(Severity.fromValue(reportProjection.severity()));
        report.setId(reportProjection.id());
        report.setParentId(reportProjection.parentId());
    }
}
