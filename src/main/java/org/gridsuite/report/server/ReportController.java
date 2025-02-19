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
import jakarta.persistence.EntityNotFoundException;
import org.gridsuite.report.server.dto.Report;
import org.gridsuite.report.server.dto.ReportLog;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
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
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The elements of the report, reporters and subreporters")})
    public ResponseEntity<Report> getReport(@PathVariable("id") UUID id,
                                            @Parameter(description = "Empty report with default name") @RequestParam(name = "defaultName", required = false, defaultValue = "defaultName") String defaultName) {
        try {
            Report report = service.getReport(id);
            return report == null ?
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getEmptyReport(id, defaultName)) :
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(report);
        } catch (EntityNotFoundException ignored) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getEmptyReport(id, defaultName));
        }
    }

    @GetMapping(value = "/reports/{id}/aggregated-severities", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the severities of the report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The severities of the report")})
    public ResponseEntity<Set<String>> getReportAggregatedSeverities(@PathVariable("id") UUID id) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(service.getReportAggregatedSeverities(id));
    }

    @GetMapping(value = "/reports/{id}/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the messages, severity and the parent id contained in the report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of message (severity and parent id) of the reporter and its subreporters"),
        @ApiResponse(responseCode = "404", description = "The reporter does not exists")})
    public ResponseEntity<List<ReportLog>> getReportLogs(@PathVariable("id") UUID id,
                                                         @Parameter(description = "Filter on message. Will only return elements containing the filter message in them.") @RequestParam(name = "message", required = false) String messageFilter,
                                                         @Parameter(description = "Filter on severity levels. Will only return elements with those severities") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevelsFilter) {
        try {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.getReportLogs(id, severityLevelsFilter, messageFilter != null ? URLDecoder.decode(messageFilter, StandardCharsets.UTF_8) : null));
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

    @PostMapping(value = "reports/{id}/duplicate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Duplicate a report")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The report has been duplicated"),
        @ApiResponse(responseCode = "404", description = "Report not found")
    })
    public ResponseEntity<UUID> duplicateReport(@PathVariable("id") UUID id) {
        try {
            return ResponseEntity.ok(service.duplicateReport(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping(value = "reports/{id}")
    @Operation(summary = "delete the report")
    @ApiResponse(responseCode = "200", description = "The report has been deleted")
    public ResponseEntity<Void> deleteReport(@PathVariable("id") UUID reportUuid,
                                             @Parameter(description = "Return 404 if report is not found") @RequestParam(name = "errorOnReportNotFound", required = false, defaultValue = "true") boolean errorOnReportNotFound) {
        try {
            service.deleteReport(reportUuid);
        } catch (EmptyResultDataAccessException | EntityNotFoundException ignored) {
            return errorOnReportNotFound ? ResponseEntity.notFound().build() : ResponseEntity.ok().build();
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "reports", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "delete reports by their UUIDs")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The reports have been deleted")})
    public ResponseEntity<Void> deleteReports(@Parameter(description = "list of reports UUIDs to delete") @RequestBody List<UUID> reportUuids) {
        service.deleteReports(reportUuids);
        return ResponseEntity.ok().build();
    }
}
