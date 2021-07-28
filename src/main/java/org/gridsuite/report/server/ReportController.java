/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.powsybl.commons.reporter.ReporterModel;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import com.powsybl.commons.reporter.Report;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + ReportApi.API_VERSION)
@Api(value = "Reports server")
public class ReportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportController.class);

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping(value = "reports", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get all reports", response = Collection.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "All reports")})
    public ResponseEntity<List<ReporterModel>> getReports() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getReports());
    }

    @GetMapping(value = "reports/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get report by id", response = ReporterModel.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The report"),
        @ApiResponse(code = 404, message = "The report does not exists")})
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
    @ApiOperation(value = "Create reports", response = Report.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The reports have been successfully created")})
    public void createReport(@PathVariable("id") UUID id, @RequestParam(name = "overwrite", defaultValue = "false", required = false) Boolean overwrite, @RequestBody(required = true) ReporterModel report) {
        LOGGER.info("start import " + id.toString());
        service.createReports(id, report, overwrite);
        LOGGER.info("end import " + id.toString());
    }

    @DeleteMapping(value = "reports/{id}")
    @ApiOperation(value = "delete the report")
    @ApiResponse(code = 200, message = "The report has been deleted")
    public ResponseEntity<Void> deleteReport(@PathVariable("id") UUID id) {
        try {
            LOGGER.info("start delete " + id.toString());
            service.deleteReport(id);
            LOGGER.info("end delete " + id.toString());
        } catch (EmptyResultDataAccessException | EntityNotFoundException ignored) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

}
