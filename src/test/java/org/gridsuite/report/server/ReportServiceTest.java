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
import org.gridsuite.report.server.entities.*;
import org.gridsuite.report.server.repositories.ReportNodeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.UUID;

import static org.gridsuite.report.server.utils.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

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

        assertEquals(2, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId);
        assertTrue(parentReportEntity.isPresent());
        assertEquals(1, parentReportEntity.get().getChildren().size());
        var childReportEntity = reportService.getReportNodeEntity(parentReportEntity.get().getChildren().get(0).getId());
        assertReportsAreEqual(childReportEntity.orElseThrow(), reportNode, Set.of());
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
        assertRequestsCount(1, 2, 0, 0);

        assertEquals(5, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId);
        assertTrue(parentReportEntity.isPresent());

        assertEquals(1, parentReportEntity.get().getChildren().size());
        var childReportEntity = reportService.getReportNodeEntity(parentReportEntity.get().getChildren().get(0).getId()).orElseThrow();
        assertReportsAreEqual(childReportEntity, reportNode, Set.of(ReportSeverity.ERROR.toString(), ReportSeverity.INFO.toString()));

        childReportEntity = reportService.getReportNodeEntity(childReportEntity.getId()).orElseThrow();
        assertEquals(2, childReportEntity.getChildren().size());
        var subChildReportNode1 = reportService.getReportNodeEntity(childReportEntity.getChildren().get(0).getId()).orElseThrow();
        assertReportsAreEqual(subChildReportNode1, subReportNode1, Set.of(ReportSeverity.INFO.toString()));
        var subChildReportNode2 = reportService.getReportNodeEntity(childReportEntity.getChildren().get(1).getId()).orElseThrow();
        assertReportsAreEqual(subChildReportNode2, subReportNode2, Set.of(ReportSeverity.ERROR.toString()));

        subChildReportNode1 = reportService.getReportNodeEntity(subChildReportNode1.getId()).orElseThrow();
        assertEquals(1, subChildReportNode1.getChildren().size());
        var subSubChildReportNode = reportService.getReportNodeEntity(subChildReportNode1.getChildren().get(0).getId()).orElseThrow();
        assertReportsAreEqual(subSubChildReportNode, subSubReportNode1, Set.of(ReportSeverity.INFO.toString()));
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

        SQLStatementCountValidator.reset();
        reportService.createReport(parentReportId, anotherReport);
        assertRequestsCount(2, 1, 0, 0);

        assertEquals(3, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId);
        assertTrue(parentReportEntity.isPresent());
        assertEquals(2, parentReportEntity.get().getChildren().size());
        var anotherChildReportEntity = reportService.getReportNodeEntity(parentReportEntity.get().getChildren().get(1).getId()).orElseThrow();
        assertReportsAreEqual(anotherChildReportEntity, anotherReport, Set.of());
    }

    @Test
    void appendIncermentalModificationReportToExistingReport() {
        var reportNode = ReportNode.newRootReportNode()
            .withMessageTemplate("test", "958de6eb-b5cb-4069-bd1f-fd75301b4a54@NetworkModification")
            .build();
        reportNode.newReportNode()
            .withMessageTemplate("genMod", "GENERATOR_MODIFICATION")
            .add();
        var parentReportId = UUID.randomUUID();
        reportService.createReport(parentReportId, reportNode);

        var anotherReport = ReportNode.newRootReportNode()
            .withMessageTemplate("test", "958de6eb-b5cb-4069-bd1f-fd75301b4a54@NetworkModification")
            .build();
        anotherReport.newReportNode()
            .withMessageTemplate("twtMod", "TWO_WINDINGS_TRANSFORMER_MODIFICATION")
            .add();
        SQLStatementCountValidator.reset();
        reportService.createReport(parentReportId, anotherReport);
        assertRequestsCount(2, 1, 0, 0);

        assertEquals(4, reportNodeRepository.findAll().size());
        var parentReportEntity = reportService.getReportNodeEntity(parentReportId);
        assertTrue(parentReportEntity.isPresent());
        // the two subreports "GENERATOR_MODIFICATION" and "TWO_WINDINGS_TRANSFORMER_MODIFICATION" are added to the same the parent report
        assertEquals(1, parentReportEntity.get().getChildren().size());
        assertEquals(2, reportService.getReportNodeEntity(parentReportEntity.get().getChildren().get(0).getId()).orElseThrow().getChildren().size());
    }

    private static void assertReportsAreEqual(ReportNodeEntity entity, ReportNode reportNode, Set<String> severityList) {
        assertEquals(reportNode.getMessage(), entity.getMessage());
        assertEquals(severityList, entity.getSeverities());
    }
}
