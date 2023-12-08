/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.vladmihalcea.sql.SQLStatementCountValidator;
import org.gridsuite.report.server.entities.ReportElementEntity;
import org.gridsuite.report.server.entities.ReportEntity;
import org.gridsuite.report.server.entities.TreeReportEntity;
import org.gridsuite.report.server.repositories.ReportElementRepository;
import org.gridsuite.report.server.repositories.ReportRepository;
import org.gridsuite.report.server.repositories.TreeReportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.report.server.utils.TestUtils.*;

@SpringBootTest
class ReportServiceTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    private TreeReportRepository treeReportRepository;

    @Autowired
    private ReportElementRepository reportElementRepository;

    @BeforeEach
    public void setUp() {
        cleanDB();
    }

    @AfterEach
    public void tearOff() {
        cleanDB();
    }

    private void cleanDB() {
        reportElementRepository.deleteAll();
        treeReportRepository.deleteAll();
        reportRepository.deleteAll();
    }

    private ReportElementEntity createReportElement(String name, TreeReportEntity parent, long nanos) {
        return new ReportElementEntity(null, parent, nanos, name, List.of());
    }

    @Test
    void testDeleteRootReport() {
        UUID idReport = UUID.randomUUID();
        ReportEntity reportEntity = reportRepository.save(new ReportEntity(idReport));
        TreeReportEntity treeReportEntity = createTreeReport("root1", reportEntity, null, 2000);
        treeReportRepository.save(treeReportEntity);

        TreeReportEntity treeReportEntity1 = createTreeReport("root2", null, treeReportEntity, 3000);
        TreeReportEntity treeReportEntity2 = createTreeReport("root3", null, treeReportEntity, 1000);
        TreeReportEntity treeReportEntity3 = createTreeReport("root4", null, treeReportEntity2, 4000);
        treeReportRepository.saveAll(List.of(treeReportEntity1, treeReportEntity2, treeReportEntity3));

        ReportElementEntity reportElement1 = createReportElement("log1", treeReportEntity1, 2000);
        ReportElementEntity reportElement2 = createReportElement("log2", treeReportEntity1, 3000);
        ReportElementEntity reportElement3 = createReportElement("log3", treeReportEntity2, 1000);
        reportElementRepository.saveAll(List.of(reportElement1, reportElement2, reportElement3));

        SQLStatementCountValidator.reset();
        reportService.deleteReport(idReport, null);
        /* 12 delete for one report
            * 2 for report-element (report-element + report-element-values)
            * 3 for each tree-report level (3) (tree-report + tree-report-values + tree-report-dictionary)
            * 1 for report
         */
        assertRequestsCount(3,0,0,12);

        Assertions.assertEquals(0, treeReportRepository.findAll().size());
        Assertions.assertEquals(0, reportElementRepository.findAll().size());
        Assertions.assertEquals(0, reportRepository.findAll().size());
    }
}
