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
    private int depth;
    private List<Report> subReports = new ArrayList<>();

    public Report addEmptyReport() {
        Report subReport = new Report();
        subReports.add(subReport);
        return subReport;
    }
}
