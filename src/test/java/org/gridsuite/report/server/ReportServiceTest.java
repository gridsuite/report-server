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
import org.gridsuite.report.server.repositories.ReportElementRepository;
import org.gridsuite.report.server.repositories.ReportNodeRepository;
import org.gridsuite.report.server.repositories.ReportRepository;
import org.gridsuite.report.server.repositories.TreeReportRepository;
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
    ReportRepository reportRepository;

    @Autowired
    private TreeReportRepository treeReportRepository;

    @Autowired
    private ReportElementRepository reportElementRepository;
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
        reportElementRepository.deleteAll();
        treeReportRepository.deleteAll();
        reportRepository.deleteAll();
        reportNodeRepository.deleteAll();
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
        assertRequestsCount(1, 3, 0, 0);

        assertEquals(2, reportNodeRepository.findAll().size());
        var parentReportEntity = reportNodeRepository.findByIdWithChildren(parentReportId);
        assertTrue(parentReportEntity.isPresent());
        assertEquals(1, parentReportEntity.get().getChildren().size());
        var childReportEntity = parentReportEntity.get().getChildren().get(0);
        assertReportsAreEqual(childReportEntity, reportNode, Set.of());
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
        assertRequestsCount(1, 4, 0, 0);

        assertEquals(5, reportNodeRepository.findAll().size());
        var parentReportEntity = reportNodeRepository.findByIdWithChildren(parentReportId);
        assertTrue(parentReportEntity.isPresent());

        assertEquals(1, parentReportEntity.get().getChildren().size());
        var childReportEntity = parentReportEntity.get().getChildren().get(0);
        assertReportsAreEqual(childReportEntity, reportNode, Set.of(ReportSeverity.ERROR.toString()));

        childReportEntity = reportNodeRepository.findByIdWithChildren(childReportEntity.getId()).orElseThrow();
        assertEquals(2, childReportEntity.getChildren().size());
        var subChildReportNode1 = childReportEntity.getChildren().get(0);
        assertReportsAreEqual(subChildReportNode1, subReportNode1, Set.of(ReportSeverity.INFO.toString()));
        var subChildReportNode2 = childReportEntity.getChildren().get(1);
        assertReportsAreEqual(subChildReportNode2, subReportNode2, Set.of());

        subChildReportNode1 = reportNodeRepository.findByIdWithChildren(subChildReportNode1.getId()).orElseThrow();
        assertEquals(1, subChildReportNode1.getChildren().size());
        var subSubChildReportNode = subChildReportNode1.getChildren().get(0);
        assertReportsAreEqual(subSubChildReportNode, subSubReportNode1, Set.of());
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
        assertRequestsCount(2, 3, 0, 0);

        assertEquals(3, reportNodeRepository.findAll().size());
        var parentReportEntity = reportNodeRepository.findByIdWithChildren(parentReportId);
        assertTrue(parentReportEntity.isPresent());
        assertEquals(2, parentReportEntity.get().getChildren().size());
        var anotherChildReportEntity = parentReportEntity.get().getChildren().get(1);
        assertReportsAreEqual(anotherChildReportEntity, anotherReport, Set.of());
    }

    private static void assertReportsAreEqual(ReportNodeEntity entity, ReportNode reportNode, Set<String> severityList) {
        assertEquals(reportNode.getMessageKey(), entity.getMessageTemplate().getKey());
        assertEquals(reportNode.getMessageTemplate(), entity.getMessageTemplate().getMessage());
        assertEquals(reportNode.getValues().size(), entity.getValues().size());
        for (var entry : reportNode.getValues().entrySet()) {
            var valueEntity = entity.getValues().stream().filter(v -> v.getKey().equals(entry.getKey())).findAny().orElse(null);
            assertNotNull(valueEntity);
            assertEquals(entry.getValue().getValue(), valueEntity.getValue());
            assertEquals(entry.getValue().getType(), valueEntity.getValueType());
        }
        assertEquals(severityList, entity.getSeverities());
    }
}
