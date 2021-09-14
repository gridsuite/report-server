/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.powsybl.commons.reporter.ReporterModel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.persistence.EntityNotFoundException;
import java.util.List;
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

    @GetMapping(value = "reports", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all reports")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All reports")})
    public ResponseEntity<List<ReporterModel>> getReports() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getReports());
    }

    @GetMapping(value = "reports/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get report by id")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report"),
        @ApiResponse(responseCode = "404", description = "The report does not exists")})
    public ResponseEntity<ReporterModel> getReport(@PathVariable("id") UUID id) {
        try {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.getReport(id));
        } catch (EntityNotFoundException ignored) {
            return ResponseEntity.notFound().build();
        }

    }

    @PutMapping(value = "reports/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create reports")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The reports have been successfully created")})
    public void createReport(@PathVariable("id") UUID id, @RequestParam(name = "overwrite", defaultValue = "false", required = false) Boolean overwrite, @RequestBody(required = true) ReporterModel report) {

        service.createReports(id, report, overwrite);
    }

    @DeleteMapping(value = "reports/{id}")
    @Operation(summary = "delete the report")
    @ApiResponse(responseCode = "200", description = "The report has been deleted")
    public ResponseEntity<Void> deleteReport(@PathVariable("id") UUID id) {
        try {
            service.deleteReport(id);
        } catch (EmptyResultDataAccessException | EntityNotFoundException ignored) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

}
