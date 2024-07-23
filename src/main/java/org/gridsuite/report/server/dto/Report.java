/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.report.server.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Report data")
public class Report {
    private UUID id;
    private UUID parentId;
    private String message;
    private Severity severity;
    private List<Severity> subReportsSeverities = new ArrayList<>();
    private List<Report> subReports = new ArrayList<>();

    public void addReportElement(final Severity severity, final String message) {
        Report reportElement = new Report();
        reportElement.setParentId(this.getId());
        reportElement.setSeverity(severity);
        reportElement.setMessage(message);
        subReports.add(reportElement);
    }

    public Report addReportChild(final UUID id, final List<Severity> severityList, final String message) {
        Report reportChild = new Report();
        reportChild.setId(id);
        reportChild.setSubReportsSeverities(severityList);
        reportChild.setMessage(message);
        subReports.add(reportChild);
        return reportChild;
    }

    public Report addEmptyReport() {
        Report subReport = new Report();
        subReports.add(subReport);
        return subReport;
    }
}
