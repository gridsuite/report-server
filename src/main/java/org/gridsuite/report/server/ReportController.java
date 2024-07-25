/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.powsybl.commons.report.ReportNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + ReportApi.API_VERSION)
@Tag(name = "Reports server")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping(value = "/reports/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the elements of a report, its reporters, and their subreporters")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The elements of the report, reporters and subreporters"),
        @ApiResponse(responseCode = "404", description = "The report does not exists")})
    public ResponseEntity<List<ReportNode>> getReport(@PathVariable("id") UUID id,
                                                      @Parameter(description = "Filter on a the report name. If provided, will only return elements matching this given name.") @RequestParam(name = "reportNameFilter", required = false, defaultValue = "") String reportNameFilter,
                                                      @Parameter(description = "Kind of matching filter to apply to the report name.") @RequestParam(name = "reportNameMatchingType", required = false) ReportService.ReportNameMatchingType reportNameMatchingType,
                                                      @Parameter(description = "Filter on severity levels. Will only return elements with those severities.") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels,
                                                      @Parameter(description = "Empty report with default name") @RequestParam(name = "defaultName", required = false, defaultValue = "defaultName") String defaultName) {
        try {
            List<ReportNode> subReporters = service.getReport(id, severityLevels, reportNameFilter, reportNameMatchingType).getChildren();
            return subReporters.isEmpty() ?
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ReportService.getEmptyReport(id, defaultName).getChildren()) :
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(subReporters);
        } catch (EntityNotFoundException ignored) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ReportService.getEmptyReport(id, defaultName).getChildren());
        }
    }

    @GetMapping(value = "/subreports/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the elements of a reporter and its subreporters")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The elements of the reporter and its subreporters"),
        @ApiResponse(responseCode = "404", description = "The reporter does not exists")})
    public ResponseEntity<List<ReportNode>> getSubReport(@PathVariable("id") UUID id,
                                                         @Parameter(description = "Filter on severity levels. Will only return elements with those severities") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(service.getReport(id, severityLevels, null, null)));
        } catch (EntityNotFoundException ignored) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping(value = "reports/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create reports")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The reports have been successfully created")})
    public void createReport(@PathVariable("id") UUID id, @RequestBody ReportNode reportNode) {

        service.createReport(id, reportNode);
    }

    @DeleteMapping(value = "reports/{id}")
    @Operation(summary = "delete the report")
    @ApiResponse(responseCode = "200", description = "The report has been deleted")
        public ResponseEntity<Void> deleteReport(@PathVariable("id") UUID reportUuid,
                                                 @Parameter(description = "Filter on a given report type. If provided, will only delete elements with the given report type.") @RequestParam(name = "reportTypeFilter", required = false) String reportTypeFilter,
                                                 @Parameter(description = "Return 404 if report is not found") @RequestParam(name = "errorOnReportNotFound", required = false, defaultValue = "true") boolean errorOnReportNotFound) {
        try {
            service.deleteReport(reportUuid, reportTypeFilter);
        } catch (EmptyResultDataAccessException | EntityNotFoundException ignored) {
            return errorOnReportNotFound ? ResponseEntity.notFound().build() : ResponseEntity.ok().build();
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "treereports", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "delete treereports from a list of parent reports based on a key")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The reports have been deleted")})
    public ResponseEntity<Void> deleteTreeReports(@Parameter(description = "parent reports to parse and their associated tree report key to identify which to delete") @RequestBody Map<UUID, String> identifiers) {
        service.deleteTreeReports(identifiers);
        return ResponseEntity.ok().build();
    }
}
