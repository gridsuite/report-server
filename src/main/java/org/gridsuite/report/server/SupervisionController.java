/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/" + ReportApi.API_VERSION + "/supervision")
@Tag(name = "Report server - Supervision")
public class SupervisionController {
    private final ReportService reportService;

    public SupervisionController(ReportService reportService) {
        this.reportService = reportService;
    }

    public static final String LOADFLOW_TYPE_REPORT = "LoadFlow";
    public static final String SENSITIVITY_ANALYSIS_TYPE_REPORT = "SensitivityAnalysis";
    public static final String SECURITY_ANALYSIS_TYPE_REPORT = "SecurityAnalysis";
    public static final String SHORTCIRCUIT_TYPE_REPORT = "ShortCircuitAnalysis";

    @DeleteMapping(value = "/loadflow")
    @Operation(summary = "delete loadflow subreport from a parent report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The reports have been deleted")})
    public ResponseEntity<Void> deleteLoadflowSubreport(@Parameter(description = "reports containing loadflow elements") @RequestParam(name = "reportsList") List<UUID> reportsList) {
        reportService.deleteSubreporterByName(reportsList, LOADFLOW_TYPE_REPORT);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/sensitivity-analysis")
    @Operation(summary = "delete sensitivity analysis subreport from a parent report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The reports have been deleted")})
    public ResponseEntity<Void> deleteSensitivityAnalysisSubreport(@Parameter(description = "reports containing sensitivity analysis elements") @RequestParam(name = "reportsList") List<UUID> reportsList) {
        reportService.deleteSubreporterByName(reportsList, SENSITIVITY_ANALYSIS_TYPE_REPORT);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/security-analysis")
    @Operation(summary = "delete security analysis subreport from a parent report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The reports have been deleted")})
    public ResponseEntity<Void> deleteSecurityAnalysisSubreport(@Parameter(description = "reports containing security analysis elements") @RequestParam(name = "reportsList") List<UUID> reportsList) {
        reportService.deleteSubreporterByName(reportsList, SECURITY_ANALYSIS_TYPE_REPORT);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/shortcircuit")
    @Operation(summary = "delete shortcircuit subreport from a parent report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The reports have been deleted")})
    public ResponseEntity<Void> deleteShortcircuitSubreport(@Parameter(description = "reports containing shortcircuit elements") @RequestParam(name = "reportsList") List<UUID> reportsList) {
        reportService.deleteSubreporterByName(reportsList, SHORTCIRCUIT_TYPE_REPORT);
        return ResponseEntity.ok().build();
    }
}
