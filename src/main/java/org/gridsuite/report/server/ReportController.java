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
public class ReportController {

    private final ReportService service;

    String reset = "\u001B[0m";
    String blue = reset + "\u001B[44m";

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
            System.out.println(blue + " >START> reports/" + id + " " + reset);
            long startTime = System.currentTimeMillis();
            ResponseEntity<List<ReporterModel>> result = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.getReportStructureAndElements(id)
                    .getSubReporters()); // TODO Remove the hack when fix to avoid key collision in hades2 will be done
            long endTime = System.currentTimeMillis();
            System.out.println(blue + " <END< " + (endTime - startTime) + "ms " + reset);
            return result;
        } catch (EntityNotFoundException ignored) {
            return errorOnReportNotFound ? ResponseEntity.notFound().build() : ResponseEntity.ok().body(service.getEmptyReport(id, defaultName).getSubReporters());
        }
    }

    @GetMapping(value = "reports/{id}/reporters", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the tree structure of a report and its reporters")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The tree structure of a report and its reporters"),
        @ApiResponse(responseCode = "404", description = "The report does not exists")})
    public ResponseEntity<List<ReporterModel>> getReportStructure(@PathVariable("id") UUID id,
                                                   @Parameter(description = "Return 404 if report is not found or empty report") @RequestParam(name = "errorOnReportNotFound", required = false, defaultValue = "true") boolean errorOnReportNotFound,
                                                   @Parameter(description = "Empty report with default name") @RequestParam(name = "defaultName", required = false, defaultValue = "defaultName") String defaultName) {
        try {
            System.out.println(blue + " >START> reports/" + id + "/reporters " + reset);
            long startTime = System.currentTimeMillis();
            ResponseEntity<List<ReporterModel>> result = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.getReportStructure(id)
                    .getSubReporters()); // TODO Remove the hack when fix to avoid key collision in hades2 will be done
            long endTime = System.currentTimeMillis();
            System.out.println(blue + " <END< " + (endTime - startTime) + "ms " + reset);
            return result;
        } catch (EntityNotFoundException ignored) {
            return errorOnReportNotFound ? ResponseEntity.notFound().build() : ResponseEntity.ok().body(service.getEmptyReport(id, defaultName).getSubReporters());
        }
    }

    @GetMapping(value = "/reports/{id}/elements", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the elements of a report, its reporters, and their subreporters")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The elements of the report, reporters and subreporters"),
        @ApiResponse(responseCode = "404", description = "The report does not exists")})
    public ResponseEntity<List<ReporterModel>> getReportElements(@PathVariable("id") UUID id,
                                                         @Parameter(description = "Filter to fetch only with a given task key") @RequestParam(name = "taskKeyFilter", required = false, defaultValue = "") String taskKeyFilter,
                                                         @Parameter(description = "Filter on severity levels. If provided, will only return those severities.") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels) {
        try {
            System.out.println(blue + " >START> reports/" + id + "/elements " + reset);
            long startTime = System.currentTimeMillis();
            ResponseEntity<List<ReporterModel>> result = ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(service.getReportElements(id, severityLevels, taskKeyFilter)
                            .getSubReporters());
            long endTime = System.currentTimeMillis();
            System.out.println(blue + " <END< " + (endTime - startTime) + "ms " + reset);
            return result;
        } catch (EntityNotFoundException ignored) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/reporters/{id}/elements", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the elements of a reporter and its subreporters")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The elements of the reporter and its subreporters"),
        @ApiResponse(responseCode = "404", description = "The reporter does not exists")})
    public ResponseEntity<List<ReporterModel>> getReporterElements(@PathVariable("id") UUID id,
                                                         @Parameter(description = "Filter on severity levels. If provided, will only return those severities.") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels) {
        try {
            System.out.println(blue + " >START> reporters/" + id + "/elements " + reset);
            long startTime = System.currentTimeMillis();
            ResponseEntity<List<ReporterModel>> result = ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(service.getReporterElements(id, severityLevels)
                            .getSubReporters());
            long endTime = System.currentTimeMillis();
            System.out.println(blue + " <END< " + (endTime - startTime) + "ms " + reset);
            return result;
        } catch (EntityNotFoundException ignored) {
            return ResponseEntity.notFound().build();
        }
    }

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
