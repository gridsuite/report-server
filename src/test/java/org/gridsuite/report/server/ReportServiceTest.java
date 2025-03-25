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
            .withMessageTemplate("test", "template test ${test}")
            .withTypedValue("test", "hello", TypedValue.UNTYPED)
            .build();
        var parentReportId = UUID.randomUUID();

        SQLStatementCountValidator.reset();
        reportService.createReport(parentReportId, reportNode);
        assertRequestsCount(1, 1, 0, 0);

        List<ReportNodeEntity> entities = reportNodeRepository.findAll();
        assertEquals(1, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId);
        assertTrue(parentReportEntity.isPresent());
        assertReportsAreEqual(parentReportEntity.orElseThrow(), reportNode, Severity.UNKNOWN.toString());
    }

    @Test
    void createComplexNonExistingReport() {
        var reportNode = ReportNode.newRootReportNode()
            .withMessageTemplate("test", "template test ${test}")
            .withTypedValue("test", "hello", TypedValue.UNTYPED)
            .build();
        var subReportNode1 = reportNode.newReportNode().withMessageTemplate("hellohello", "this is a ${mood} message template with ${smth}")
            .withTypedValue("mood", "welcoming", TypedValue.REACTANCE)
            .withTypedValue("smth", "idk", "newType")
            .add();
        var subSubReportNode1 = subReportNode1.newReportNode()
            .withMessageTemplate("noidea", "I have no idea of ${idea}")
            .withUntypedValue("idea", "none")
            .withSeverity(TypedValue.INFO_SEVERITY)
            .add();
        var subReportNode2 = reportNode.newReportNode().withMessageTemplate("hehehe", "It's so funny")
            .withSeverity(TypedValue.ERROR_SEVERITY)
            .add();
        var parentReportId = UUID.randomUUID();

        SQLStatementCountValidator.reset();
        reportService.createReport(parentReportId, reportNode);
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
            .withMessageTemplate("test", "template test ${test}")
            .withTypedValue("test", "hello", TypedValue.UNTYPED)
            .build();
        var parentReportId = UUID.randomUUID();
        reportService.createReport(parentReportId, reportNode);

        var anotherReport = ReportNode.newRootReportNode()
            .withMessageTemplate("test2", "template test2 ${test}")
            .withTypedValue("test", "hello", TypedValue.UNTYPED)
            .build();
        anotherReport.newReportNode()
            .withMessageTemplate("test3", "template test3 ${test}")
            .withTypedValue("test", "hello", TypedValue.UNTYPED)
            .add();

        SQLStatementCountValidator.reset();
        reportService.createReport(parentReportId, anotherReport);
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
            .withMessageTemplate("test", "958de6eb-b5cb-4069-bd1f-fd75301b4a54")
            .build();
        reportNode.newReportNode()
            .withMessageTemplate("genMod", "GENERATOR_MODIFICATION")
            .add();
        var parentReportId = UUID.randomUUID();
        reportService.createReport(parentReportId, reportNode);

        var anotherReport = ReportNode.newRootReportNode()
            .withMessageTemplate("test", "958de6eb-b5cb-4069-bd1f-fd75301b4a54")
            .build();
        anotherReport.newReportNode()
            .withMessageTemplate("twtMod", "TWO_WINDINGS_TRANSFORMER_MODIFICATION")
            .add();
        SQLStatementCountValidator.reset();
        reportService.createReport(parentReportId, anotherReport);
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
            .withMessageTemplate("test", "958de6eb-b5cb-4069-bd1f-fd75301b4a54")
            .build();
        reportNode.newReportNode()
            .withMessageTemplate("okok", "test")
            .withSeverity(TypedValue.WARN_SEVERITY)
            .add();
        reportNode.newReportNode()
            .withMessageTemplate("okok", "test")
            .withSeverity(TypedValue.INFO_SEVERITY)
            .add();
        var subReportNode = reportNode.newReportNode()
            .withMessageTemplate("genMod", "GENERATOR_MODIFICATION")
            .add();
        subReportNode.newReportNode()
            .withMessageTemplate("hehe", "stuff")
            .withSeverity(TypedValue.WARN_SEVERITY)
            .add();
        var parentReportId = UUID.randomUUID();
        reportService.createReport(parentReportId, reportNode);
        var rootReportNodeEntity = reportService.getReportNodeEntity(parentReportId).orElseThrow();
        var reportNodeEntity = reportService.getReportNodeEntity(rootReportNodeEntity.getChildren().get(2).getId()).orElseThrow();
        assertEquals("WARN", rootReportNodeEntity.getSeverity());
        var subReportNodeEntity = reportService.getReportNodeEntity(reportNodeEntity.getChildren().get(0).getId()).orElseThrow();
        assertEquals("WARN", subReportNodeEntity.getSeverity());

        var anotherReport = ReportNode.newRootReportNode()
            .withMessageTemplate("test", "958de6eb-b5cb-4069-bd1f-fd75301b4a54")
            .build();
        anotherReport.newReportNode()
            .withMessageTemplate("twtMod", "TWO_WINDINGS_TRANSFORMER_MODIFICATION")
            .withSeverity(TypedValue.ERROR_SEVERITY)
            .add();
        SQLStatementCountValidator.reset();
        reportService.createReport(parentReportId, anotherReport);
        assertRequestsCount(3, 1, 1, 0);

        var rootReportNodeEntityBis = reportService.getReportNodeEntity(parentReportId).orElseThrow();
        var reportNodeEntityBis = reportService.getReportNodeEntity(rootReportNodeEntityBis.getChildren().get(3).getId()).orElseThrow();
        assertEquals("ERROR", rootReportNodeEntityBis.getSeverity());
        assertEquals(4, rootReportNodeEntityBis.getChildren().size());
        assertEquals("ERROR", reportNodeEntityBis.getSeverity());
    }

    @Test
    void testCreateReportWithTooLongMessage() {
        String veryLongString = String.join("", Collections.nCopies(1000, "verylongstring"));

        var rootReportNode = ReportNode.newRootReportNode()
            .withMessageTemplate("test", veryLongString)
            .build();

        rootReportNode.newReportNode()
            .withMessageTemplate("test", veryLongString)
            .add();

        var reportUuid = UUID.randomUUID();
        reportService.createReport(reportUuid, rootReportNode);

        var rootReportNodeEntity = reportService.getReportNodeEntity(reportUuid).orElseThrow();
        assertEquals(veryLongString.substring(0, MAX_MESSAGE_CHAR), rootReportNodeEntity.getMessage());

        var reportNodeEntity = reportService.getReportNodeEntity(rootReportNodeEntity.getChildren().get(0).getId()).orElseThrow();
        assertEquals(veryLongString.substring(0, MAX_MESSAGE_CHAR), reportNodeEntity.getMessage());
    }

    @Test
    void testParentReportSeverityAggregation() {
        var reportNode = ReportNode.newRootReportNode()
            .withMessageTemplate("test", "958de6eb-b5cb-4069-bd1f-fd75301b4a54")
            .withSeverity(TypedValue.ERROR_SEVERITY)
            .build();
        reportNode.newReportNode()
            .withMessageTemplate("traceMessage", "traceMessage")
            .withSeverity(TypedValue.TRACE_SEVERITY)
            .add();
        var subReportNode = reportNode.newReportNode()
            .withMessageTemplate("infoMessage", "infoMessage")
            .withSeverity(TypedValue.INFO_SEVERITY)
            .add();
        subReportNode.newReportNode()
            .withMessageTemplate("debugMessage", "debugMessage")
            .withSeverity(TypedValue.DEBUG_SEVERITY)
            .add();
        var parentReportId = UUID.randomUUID();
        reportService.createReport(parentReportId, reportNode);
        var rootReportNodeEntity = reportService.getReportNodeEntity(parentReportId).orElseThrow();
        assertEquals("ERROR", rootReportNodeEntity.getSeverity());
        var reportNodeEntity = reportService.getReportNodeEntity(rootReportNodeEntity.getChildren().get(1).getId()).orElseThrow();
        assertEquals("INFO", reportNodeEntity.getSeverity());
    }

    private static void assertReportsAreEqual(ReportNodeEntity entity, ReportNode reportNode, String severity) {
        assertEquals(reportNode.getMessage(), entity.getMessage());
        assertEquals(severity, entity.getSeverity());
    }
}
