/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.powsybl.commons.report.ReportNode;
import org.gridsuite.report.server.entities.ReportElementEntity;
import org.gridsuite.report.server.entities.ReportEntity;
import org.gridsuite.report.server.entities.ReportValueEmbeddable;
import org.gridsuite.report.server.entities.TreeReportEntity;
import org.gridsuite.report.server.repositories.ReportElementRepository;
import org.gridsuite.report.server.repositories.ReportRepository;
import org.gridsuite.report.server.repositories.TreeReportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.gridsuite.report.server.utils.TestUtils.*;
/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@SpringBootTest
class TreeReportTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    private TreeReportRepository treeReportRepository;

    @Autowired
    private ReportElementRepository reportElementRepository;

    private ReportElementEntity createReportElement(String name, TreeReportEntity parent, long nanos, String severity) {
        return new ReportElementEntity(null, parent, nanos, name, List.of(new ReportValueEmbeddable("reportSeverity", severity, "SEVERITY")));
    }

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

    @Test
    void testRootTreeReportsOrder() {
        UUID idReport = UUID.randomUUID();
        ReportEntity reportEntity = reportRepository.save(new ReportEntity(idReport));
        TreeReportEntity treeReportEntity1 = createTreeReport("log1", reportEntity, null, 2000);
        TreeReportEntity treeReportEntity2 = createTreeReport("log2", reportEntity, null, 3000);
        TreeReportEntity treeReportEntity3 = createTreeReport("log3", reportEntity, null, 1000);
        treeReportRepository.saveAll(List.of(treeReportEntity1, treeReportEntity2, treeReportEntity3));

        ReportNode report = reportService.getReport(idReport, null, "", ReportService.ReportNameMatchingType.EXACT_MATCHING);
        assertEquals(List.of("log3", "log1", "log2"), report.getChildren().stream().map(ReportNode::getMessageKey).toList());
    }

    @Test
    void testSubTreeReportsOrder() {
        UUID idReport = UUID.randomUUID();
        ReportEntity reportEntity = reportRepository.save(new ReportEntity(idReport));
        TreeReportEntity treeReportEntity = createTreeReport("test", reportEntity, null, 1000);
        treeReportEntity = treeReportRepository.save(treeReportEntity);

        TreeReportEntity treeReportEntity1 = createTreeReport("log1", null, treeReportEntity, 2000);
        TreeReportEntity treeReportEntity2 = createTreeReport("log2", null, treeReportEntity, 3000);
        TreeReportEntity treeReportEntity3 = createTreeReport("log3", null, treeReportEntity, 1000);
        treeReportRepository.saveAll(List.of(treeReportEntity1, treeReportEntity2, treeReportEntity3));

        ReportNode report = reportService.getReport(idReport, null, "", ReportService.ReportNameMatchingType.EXACT_MATCHING);
        ReportNode reporter = report.getChildren().get(0);
        assertEquals(List.of("log3", "log1", "log2"), reporter.getChildren().stream().map(ReportNode::getMessageKey).toList());
    }

    @Test
    void testReportElementsOrder() {
        UUID idReport = UUID.randomUUID();
        ReportEntity reportEntity = reportRepository.save(new ReportEntity(idReport));
        TreeReportEntity treeReportEntity = createTreeReport("test", reportEntity, null, 1000);
        treeReportEntity = treeReportRepository.save(treeReportEntity);

        ReportElementEntity reportElement1 = createReportElement("log1", treeReportEntity, 2000, "INFO");
        ReportElementEntity reportElement2 = createReportElement("log2", treeReportEntity, 3000, "ERROR");
        ReportElementEntity reportElement3 = createReportElement("log3", treeReportEntity, 1000, "TRACE");
        reportElementRepository.saveAll(List.of(reportElement1, reportElement2, reportElement3));

        ReportNode report = reportService.getReport(idReport, Set.of("INFO", "TRACE", "ERROR"), "", ReportService.ReportNameMatchingType.EXACT_MATCHING);
        ReportNode reporter = report.getChildren().get(0);

        assertEquals(List.of("log3", "log1", "log2"), reporter.getChildren().stream().map(ReportNode::getMessageKey).toList());
    }
}
