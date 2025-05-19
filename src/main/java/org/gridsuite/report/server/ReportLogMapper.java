/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import org.gridsuite.report.server.dto.ReportLog;
import org.gridsuite.report.server.entities.ReportProjection;

import java.util.*;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public final class ReportLogMapper {

    private ReportLogMapper() {
        // Should not be instantiated
    }

    public static List<ReportLog> map(List<ReportProjection> reports) {
        List<ReportLog> reportLogs = new ArrayList<>();
        Map<UUID, ReportLog> reportLogsById = new HashMap<>();

        for (ReportProjection report : reports) {
            if (reportLogsById.get(report.id()) == null) {
                ReportLog reportLog = createReportLog(report);
                reportLogs.add(reportLog);
                reportLogsById.put(report.id(), reportLog);
            } else {
                reportLogsById.get(report.id()).setSeverity(Severity.fromValue(report.severity()));
            }
        }
        return reportLogs;
    }

    public static ReportLog createReportLog(ReportProjection entity) {
        return new ReportLog(entity.message(), Severity.fromValue(entity.severity()), entity.parentId());
    }
}
