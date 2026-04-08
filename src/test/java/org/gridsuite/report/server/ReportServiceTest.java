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
import java.util.List;
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
        reportService.createReport(parentReportId, reportNode);
        assertRequestsCount(1, 1, 0, 0);

        assertEquals(1, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId, 0);
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
        reportService.createReport(parentReportId, reportNode);
        assertRequestsCount(1, 1, 0, 0);

        assertEquals(4, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId, 0);
        assertTrue(parentReportEntity.isPresent());

        var allEntities = reportService.getReportNodeEntities(parentReportId);
        var rootChildren = allEntities.stream().filter(e -> e.getParentOrder() != null && e.getParentOrder() == 0).toList();
        assertEquals(2, rootChildren.size());

        var childReportEntity = reportService.getReportNodeEntity(parentReportId, 1).orElseThrow();
        assertReportsAreEqual(childReportEntity, subReportNode1, Severity.INFO.toString());
        assertReportsAreEqual(parentReportEntity.get(), reportNode, Severity.ERROR.toString());

        var child1Children = allEntities.stream().filter(e -> e.getParentOrder() != null && e.getParentOrder() == childReportEntity.getOrder()).toList();
        assertEquals(1, child1Children.size());
        var subChildReportNode1 = reportService.getReportNodeEntity(parentReportId, 2).orElseThrow();
        assertReportsAreEqual(subChildReportNode1, subSubReportNode1, Severity.INFO.toString());
        var subChildReportNode2 = reportService.getReportNodeEntity(parentReportId, 3).orElseThrow();
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
        reportService.createReport(parentReportId, reportNode);

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
        reportService.createReport(parentReportId, anotherReport);
        assertRequestsCount(2, 1, 1, 0);

        assertEquals(2, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId, 0);
        assertTrue(parentReportEntity.isPresent());
        var allEntities = reportService.getReportNodeEntities(parentReportId);
        var rootChildren = allEntities.stream().filter(e -> e.getParentOrder() != null && e.getParentOrder() == 0).toList();
        assertEquals(1, rootChildren.size());
        var anotherChildReportEntity = rootChildren.get(0);
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
        reportService.createReport(parentReportId, reportNode);

        var anotherReport = ReportNode.newRootReportNode()
            .withResourceBundles("i18n.reports")
            .withMessageTemplate("test")
            .withUntypedValue("message", "958de6eb-b5cb-4069-bd1f-fd75301b4a54")
            .build();
        anotherReport.newReportNode()
            .withMessageTemplate("twtMod")
            .add();
        SQLStatementCountValidator.reset();
        reportService.createReport(parentReportId, anotherReport);
        assertRequestsCount(2, 1, 1, 0);

        assertEquals(3, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId, 0);
        assertTrue(parentReportEntity.isPresent());
        var allEntities = reportService.getReportNodeEntities(parentReportId);
        var rootChildren = allEntities.stream().filter(e -> e.getParentOrder() != null && e.getParentOrder() == 0).toList();
        // the two subreports "GENERATOR_MODIFICATION" and "TWO_WINDINGS_TRANSFORMER_MODIFICATION" are added to the same the parent report
        assertEquals(2, rootChildren.size());
        var firstChild = rootChildren.get(0);
        var firstChildChildren = allEntities.stream().filter(e -> e.getParentOrder() != null && e.getParentOrder() == firstChild.getOrder()).toList();
        assertEquals(0, firstChildChildren.size());
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
        reportService.createReport(parentReportId, reportNode);
        var rootReportNodeEntity = reportService.getReportNodeEntity(parentReportId, 0).orElseThrow();
        var allEntities = reportService.getReportNodeEntities(parentReportId);
        var rootChildren = allEntities.stream().filter(e -> e.getParentOrder() != null && e.getParentOrder() == 0).toList();
        var reportNodeEntity = rootChildren.get(2); // genMod at order=3
        assertEquals("WARN", rootReportNodeEntity.getSeverity());
        assertEquals(0, rootReportNodeEntity.getDepth());
        var child3Children = allEntities.stream().filter(e -> e.getParentOrder() != null && e.getParentOrder() == reportNodeEntity.getOrder()).toList();
        var subReportNodeEntity = child3Children.get(0);
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
        reportService.createReport(parentReportId, anotherReport);
        assertRequestsCount(2, 1, 1, 0);

        var rootReportNodeEntityBis = reportService.getReportNodeEntity(parentReportId, 0).orElseThrow();
        var allEntitiesBis = reportService.getReportNodeEntities(parentReportId);
        var rootChildrenBis = allEntitiesBis.stream().filter(e -> e.getParentOrder() != null && e.getParentOrder() == 0).toList();
        assertEquals("ERROR", rootReportNodeEntityBis.getSeverity());
        assertEquals(4, rootChildrenBis.size());
        var reportNodeEntityBis = rootChildrenBis.get(3); // twtMod
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
        reportService.createReport(reportUuid, rootReportNode);

        var rootReportNodeEntity = reportService.getReportNodeEntity(reportUuid, 0).orElseThrow();
        assertEquals(veryLongString.substring(0, MAX_MESSAGE_CHAR), rootReportNodeEntity.getMessage());

        var reportNodeEntity = reportService.getReportNodeEntity(reportUuid, 1).orElseThrow();
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
        reportService.createReport(parentReportId, reportNode);
        var rootReportNodeEntity = reportService.getReportNodeEntity(parentReportId, 0).orElseThrow();
        assertEquals("ERROR", rootReportNodeEntity.getSeverity());
        var allEntities = reportService.getReportNodeEntities(parentReportId);
        var rootChildren = allEntities.stream().filter(e -> e.getParentOrder() != null && e.getParentOrder() == 0).toList();
        var reportNodeEntity = rootChildren.get(1); // infoMessage at order=2
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
        reportService.createReport(reportUuid, rootReportNode);
        assertRequestsCount(1, 5, 0, 0);
    }

    private static void assertReportsAreEqual(ReportNodeEntity entity, ReportNode reportNode, String severity) {
        assertEquals(reportNode.getMessage(), entity.getMessage());
        assertEquals(severity, entity.getSeverity());
    }
}
