/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.powsybl.commons.reporter.ReporterModel;
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
public class ReportController { // TODO CHARLY gérer les endpoints ici

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping(value = "reports/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get report by id")
    @Deprecated
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report"),
        @ApiResponse(responseCode = "404", description = "The report does not exists")})
    public ResponseEntity<List<ReporterModel>> getReport(@PathVariable("id") UUID id,
                                                   @Parameter(description = "Return 404 if report is not found or empty report") @RequestParam(name = "errorOnReportNotFound", required = false, defaultValue = "true") boolean errorOnReportNotFound,
                                                   @Parameter(description = "Empty report with default name") @RequestParam(name = "defaultName", required = false, defaultValue = "defaultName") String defaultName) {
        try {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.getReport(id)
                    .getSubReporters()); // TODO Remove the hack when fix to avoid key collision in hades2 will be done
        } catch (EntityNotFoundException ignored) {
            return errorOnReportNotFound ? ResponseEntity.notFound().build() : ResponseEntity.ok().body(service.getEmptyReport(id, defaultName).getSubReporters());
        }
    }

    /*******************
     * NEW CODE
     *******************/

    @GetMapping(value = "reports/{id}/reporters", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a report's reporters and their structure")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report's reporters as a tree"),
        @ApiResponse(responseCode = "404", description = "The report does not exists")})
    public ResponseEntity<List<ReporterModel>> getReportStructure(@PathVariable("id") UUID id,
                                                   @Parameter(description = "Return 404 if report is not found or empty report") @RequestParam(name = "errorOnReportNotFound", required = false, defaultValue = "true") boolean errorOnReportNotFound,
                                                   @Parameter(description = "Empty report with default name") @RequestParam(name = "defaultName", required = false, defaultValue = "defaultName") String defaultName) {
        try {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.getReportStructure(id)
                    .getSubReporters()); // TODO Remove the hack when fix to avoid key collision in hades2 will be done
        } catch (EntityNotFoundException ignored) {
            return errorOnReportNotFound ? ResponseEntity.notFound().build() : ResponseEntity.ok().body(service.getEmptyReport(id, defaultName).getSubReporters());
        }
    }

    @GetMapping(value = "/reports/{id}/elements", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a report and its subreporter's elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report and subreporter's elements"),
        @ApiResponse(responseCode = "404", description = "The report does not exists")})
    public ResponseEntity<List<ReporterModel>> getElementsForReport(@PathVariable("id") UUID id,
                                                         @Parameter(description = "Severity levels") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(service.getElementsForReport(id, severityLevels)
                            .getSubReporters());
        } catch (EntityNotFoundException ignored) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/reports/reporters/{id}/elements", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a reporter and its subreporter's elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The reporter and subreporter's elements"),
        @ApiResponse(responseCode = "404", description = "The reporter does not exists")})
    public ResponseEntity<List<ReporterModel>> getElementsForReporter(@PathVariable("id") UUID id,
                                                         @Parameter(description = "Severity levels") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(service.getElementsForReporter(id, severityLevels)
                            .getSubReporters());
        } catch (EntityNotFoundException ignored) {
            return ResponseEntity.notFound().build();
        }
    }

    /*******************
     * END OF NEW CODE
     *******************/

    @PutMapping(value = "reports/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create reports")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The reports have been successfully created")})
    public void createReport(@PathVariable("id") UUID id, @RequestBody ReporterModel reporter) {

        service.createReports(id, reporter);
    }

    @DeleteMapping(value = "reports/{id}")
    @Operation(summary = "delete the report")
    @ApiResponse(responseCode = "200", description = "The report has been deleted")
        public ResponseEntity<Void> deleteReport(@PathVariable("id") UUID id,
                                                 @Parameter(description = "Return 404 if report is not found") @RequestParam(name = "errorOnReportNotFound", required = false, defaultValue = "true") boolean errorOnReportNotFound) {
        try {
            service.deleteReport(id);
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
