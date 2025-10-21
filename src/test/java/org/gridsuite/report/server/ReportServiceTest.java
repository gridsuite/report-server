/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import com.vladmihalcea.sql.SQLStatementCountValidator;
import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.gridsuite.report.server.repositories.ReportNodeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.UUID;

import static org.gridsuite.report.server.SizedReportNode.MAX_MESSAGE_CHAR;
import static org.gridsuite.report.server.utils.TestUtils.assertRequestsCount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ReportServiceTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportNodeRepository reportNodeRepository;

    @BeforeEach
    public void setUp() {
        cleanDB();
    }

    @AfterEach
    public void tearOff() {
        cleanDB();
    }

    private void cleanDB() {
        reportService.deleteAll();
    }

    @Test
    void createNonExistingReport() {
        var reportNode = ReportNode.newRootReportNode()
            .withResourceBundles("i18n.reports")
            .withMessageTemplate("templateTest")
            .withTypedValue("test", "hello", TypedValue.UNTYPED_TYPE)
            .build();
        var parentReportId = UUID.randomUUID();

        SQLStatementCountValidator.reset();
        reportService.createOrReplaceReport(parentReportId, reportNode, false);
        assertRequestsCount(1, 1, 0, 0);

        assertEquals(1, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId);
        assertTrue(parentReportEntity.isPresent());
        assertReportsAreEqual(parentReportEntity.orElseThrow(), reportNode, Severity.UNKNOWN.toString());
    }

    @Test
    void createComplexNonExistingReport() {
        var reportNode = ReportNode.newRootReportNode()
            .withResourceBundles("i18n.reports")
            .withMessageTemplate("templateTest")
            .withTypedValue("test", "hello", TypedValue.UNTYPED_TYPE)
            .build();
        var subReportNode1 = reportNode.newReportNode()
            .withMessageTemplate("hellohello")
            .withTypedValue("mood", "welcoming", TypedValue.REACTANCE)
            .withTypedValue("smth", "idk", "newType")
            .add();
        var subSubReportNode1 = subReportNode1.newReportNode()
            .withMessageTemplate("noidea")
            .withUntypedValue("idea", "none")
            .withSeverity(TypedValue.INFO_SEVERITY)
            .add();
        var subReportNode2 = reportNode.newReportNode()
            .withMessageTemplate("hehehe")
            .withSeverity(TypedValue.ERROR_SEVERITY)
            .add();
        var parentReportId = UUID.randomUUID();

        SQLStatementCountValidator.reset();
        reportService.createOrReplaceReport(parentReportId, reportNode, false);
        assertRequestsCount(1, 1, 0, 0);

        assertEquals(4, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId);
        assertTrue(parentReportEntity.isPresent());

        assertEquals(2, parentReportEntity.get().getChildren().size());
        var childReportEntity = reportService.getReportNodeEntity(parentReportEntity.get().getChildren().get(0).getId()).orElseThrow();
        assertReportsAreEqual(childReportEntity, subReportNode1, Severity.INFO.toString());
        assertReportsAreEqual(parentReportEntity.get(), reportNode, Severity.ERROR.toString());

        childReportEntity = reportService.getReportNodeEntity(childReportEntity.getId()).orElseThrow();
        assertEquals(1, childReportEntity.getChildren().size());
        var subChildReportNode1 = reportService.getReportNodeEntity(childReportEntity.getChildren().get(0).getId()).orElseThrow();
        assertReportsAreEqual(subChildReportNode1, subSubReportNode1, Severity.INFO.toString());
        var subChildReportNode2 = reportService.getReportNodeEntity(parentReportEntity.get().getChildren().get(1).getId()).orElseThrow();
        assertReportsAreEqual(subChildReportNode2, subReportNode2, Severity.ERROR.toString());
    }

    @Test
    void appendToExistingReport() {
        var reportNode = ReportNode.newRootReportNode()
            .withResourceBundles("i18n.reports")
            .withMessageTemplate("templateTest")
            .withTypedValue("test", "hello", TypedValue.UNTYPED_TYPE)
            .build();
        var parentReportId = UUID.randomUUID();
        reportService.createOrReplaceReport(parentReportId, reportNode, false);

        var anotherReport = ReportNode.newRootReportNode()
            .withResourceBundles("i18n.reports")
            .withMessageTemplate("templateTest2")
            .withTypedValue("test", "hello", TypedValue.UNTYPED_TYPE)
            .build();
        anotherReport.newReportNode()
            .withMessageTemplate("templateTest3")
            .withTypedValue("test", "hello", TypedValue.UNTYPED_TYPE)
            .add();

        SQLStatementCountValidator.reset();
        reportService.createOrReplaceReport(parentReportId, anotherReport, false);
        assertRequestsCount(3, 1, 1, 0);

        assertEquals(2, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId);
        assertTrue(parentReportEntity.isPresent());
        assertEquals(1, parentReportEntity.get().getChildren().size());
        var anotherChildReportEntity = reportService.getReportNodeEntity(parentReportEntity.get().getChildren().get(0).getId()).orElseThrow();
        assertReportsAreEqual(anotherChildReportEntity, anotherReport.getChildren().get(0), Severity.UNKNOWN.toString());
    }

    @Test
    void appendIncrementalModificationReportToExistingReport() {
        var reportNode = ReportNode.newRootReportNode()
            .withResourceBundles("i18n.reports")
            .withMessageTemplate("test")
            .withUntypedValue("message", "958de6eb-b5cb-4069-bd1f-fd75301b4a54")
            .build();
        reportNode.newReportNode()
            .withMessageTemplate("genMod")
            .add();
        var parentReportId = UUID.randomUUID();
        reportService.createOrReplaceReport(parentReportId, reportNode, false);

        var anotherReport = ReportNode.newRootReportNode()
            .withResourceBundles("i18n.reports")
            .withMessageTemplate("test")
            .withUntypedValue("message", "958de6eb-b5cb-4069-bd1f-fd75301b4a54")
            .build();
        anotherReport.newReportNode()
            .withMessageTemplate("twtMod")
            .add();
        SQLStatementCountValidator.reset();
        reportService.createOrReplaceReport(parentReportId, anotherReport, false);
        assertRequestsCount(3, 1, 1, 0);

        assertEquals(3, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId);
        assertTrue(parentReportEntity.isPresent());
        // the two subreports "GENERATOR_MODIFICATION" and "TWO_WINDINGS_TRANSFORMER_MODIFICATION" are added to the same the parent report
        assertEquals(2, parentReportEntity.get().getChildren().size());
        assertEquals(0, reportService.getReportNodeEntity(parentReportEntity.get().getChildren().get(0).getId()).orElseThrow().getChildren().size());
    }

    @Test
    void testParentReportsSeverityListIsUpdatedAfterAppendingNewReport() {
        var reportNode = ReportNode.newRootReportNode()
            .withResourceBundles("i18n.reports")
            .withMessageTemplate("test")
            .withUntypedValue("message", "958de6eb-b5cb-4069-bd1f-fd75301b4a54")
            .build();
        reportNode.newReportNode()
            .withMessageTemplate("okok")
            .withSeverity(TypedValue.WARN_SEVERITY)
            .add();
        reportNode.newReportNode()
            .withMessageTemplate("okok")
            .withSeverity(TypedValue.INFO_SEVERITY)
            .add();
        var subReportNode = reportNode.newReportNode()
            .withMessageTemplate("genMod")
            .add();
        subReportNode.newReportNode()
            .withMessageTemplate("hehe")
            .withSeverity(TypedValue.WARN_SEVERITY)
            .add();
        var parentReportId = UUID.randomUUID();
        reportService.createOrReplaceReport(parentReportId, reportNode, false);
        var rootReportNodeEntity = reportService.getReportNodeEntity(parentReportId).orElseThrow();
        var reportNodeEntity = reportService.getReportNodeEntity(rootReportNodeEntity.getChildren().get(2).getId()).orElseThrow();
        assertEquals("WARN", rootReportNodeEntity.getSeverity());
        assertEquals(0, rootReportNodeEntity.getDepth());
        var subReportNodeEntity = reportService.getReportNodeEntity(reportNodeEntity.getChildren().get(0).getId()).orElseThrow();
        assertEquals("WARN", subReportNodeEntity.getSeverity());
        assertEquals(2, subReportNodeEntity.getDepth());

        var anotherReport = ReportNode.newRootReportNode()
            .withResourceBundles("i18n.reports")
            .withMessageTemplate("test")
            .withUntypedValue("message", "958de6eb-b5cb-4069-bd1f-fd75301b4a54")
            .build();
        anotherReport.newReportNode()
            .withMessageTemplate("twtMod")
            .withSeverity(TypedValue.ERROR_SEVERITY)
            .add();
        SQLStatementCountValidator.reset();
        reportService.createOrReplaceReport(parentReportId, anotherReport, false);
        assertRequestsCount(3, 1, 1, 0);

        var rootReportNodeEntityBis = reportService.getReportNodeEntity(parentReportId).orElseThrow();
        var reportNodeEntityBis = reportService.getReportNodeEntity(rootReportNodeEntityBis.getChildren().get(3).getId()).orElseThrow();
        assertEquals("ERROR", rootReportNodeEntityBis.getSeverity());
        assertEquals(4, rootReportNodeEntityBis.getChildren().size());
        assertEquals("ERROR", reportNodeEntityBis.getSeverity());
        assertEquals(1, reportNodeEntityBis.getDepth());
    }

    @Test
    void testCreateReportWithTooLongMessage() {
        String veryLongString = String.join("", Collections.nCopies(1000, "verylongstring"));

        var rootReportNode = ReportNode.newRootReportNode()
            .withResourceBundles("i18n.reports")
            .withMessageTemplate("test")
            .withUntypedValue("message", veryLongString)
            .build();

        rootReportNode.newReportNode()
            .withMessageTemplate("test")
            .withUntypedValue("message", veryLongString)
            .add();

        var reportUuid = UUID.randomUUID();
        reportService.createOrReplaceReport(reportUuid, rootReportNode, false);

        var rootReportNodeEntity = reportService.getReportNodeEntity(reportUuid).orElseThrow();
        assertEquals(veryLongString.substring(0, MAX_MESSAGE_CHAR), rootReportNodeEntity.getMessage());

        var reportNodeEntity = reportService.getReportNodeEntity(rootReportNodeEntity.getChildren().get(0).getId()).orElseThrow();
        assertEquals(veryLongString.substring(0, MAX_MESSAGE_CHAR), reportNodeEntity.getMessage());
    }

    @Test
    void testParentReportSeverityAggregation() {
        var reportNode = ReportNode.newRootReportNode()
            .withResourceBundles("i18n.reports")
            .withMessageTemplate("test")
            .withUntypedValue("message", "958de6eb-b5cb-4069-bd1f-fd75301b4a54")
            .withSeverity(TypedValue.ERROR_SEVERITY)
            .build();
        reportNode.newReportNode()
            .withMessageTemplate("traceMessage")
            .withSeverity(TypedValue.TRACE_SEVERITY)
            .add();
        var subReportNode = reportNode.newReportNode()
            .withMessageTemplate("infoMessage")
            .withSeverity(TypedValue.INFO_SEVERITY)
            .add();
        subReportNode.newReportNode()
            .withMessageTemplate("debugMessage")
            .withSeverity(TypedValue.DEBUG_SEVERITY)
            .add();
        var parentReportId = UUID.randomUUID();
        reportService.createOrReplaceReport(parentReportId, reportNode, false);
        var rootReportNodeEntity = reportService.getReportNodeEntity(parentReportId).orElseThrow();
        assertEquals("ERROR", rootReportNodeEntity.getSeverity());
        var reportNodeEntity = reportService.getReportNodeEntity(rootReportNodeEntity.getChildren().get(1).getId()).orElseThrow();
        assertEquals("INFO", reportNodeEntity.getSeverity());
    }

    @Test
    void testCreateSubstantialReport() {
        var rootReportNode = ReportNode.newRootReportNode()
            .withResourceBundles("i18n.reports")
            .withMessageTemplate("test2")
            .build();

        for (int i = 0; i < 2048; i++) {
            rootReportNode.newReportNode()
                .withMessageTemplate("test2")
                .add();
        }

        var reportUuid = UUID.randomUUID();
        SQLStatementCountValidator.reset();
        reportService.createOrReplaceReport(reportUuid, rootReportNode, false);
        assertRequestsCount(5, 5, 0, 0);
    }

    private static void assertReportsAreEqual(ReportNodeEntity entity, ReportNode reportNode, String severity) {
        assertEquals(reportNode.getMessage(), entity.getMessage());
        assertEquals(severity, entity.getSeverity());
    }
}
