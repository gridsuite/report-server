/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.vladmihalcea.sql.SQLStatementCountValidator;
import org.gridsuite.report.server.entities.TreeReportEntity;
import org.gridsuite.report.server.repositories.TreeReportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.util.unit.DataSize;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.report.server.utils.TestUtils.assertRequestsCount;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Tag("IntegrationTest")
class TreeReportRepositoryTest {
    private static final UUID TEST_ID_1 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID TEST_ID_2 = UUID.fromString("5809dabf-60f8-46e5-9e58-57b03d6b1818");
    private static final UUID TEST_ID_3 = UUID.fromString("de67bab1-f47b-4199-80a7-10bd77285675");
    private static final UUID TEST_ID_4 = UUID.fromString("15bd10ba-6cd8-11ee-b962-0242ac120002");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSqlContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:13.4-alpine"))
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                    .withMemory(DataSize.ofMegabytes(200L).toBytes())
                    .withMemorySwap(DataSize.ofMegabytes(200L).toBytes()));

    @Autowired
    private TreeReportRepository treeReportRepository;

    private static TreeReportEntity buildTreeReport(UUID uuid) {
        TreeReportEntity entity = new TreeReportEntity();
        entity.setName(uuid.toString());
        entity.setNanos(10L);
        entity.setParentReport(null);
        entity.setReport(null);
        entity.setIdNode(uuid);
        return entity;
    }

    @AfterEach
    void tearOff() {
        // clean DB
        treeReportRepository.deleteAllInBatch();
    }

    @Test
    void testCreateTreeReport() {
        SQLStatementCountValidator.reset();

        TreeReportEntity parent = buildTreeReport(TEST_ID_1);
        parent = treeReportRepository.save(parent);

        TreeReportEntity childrenA = buildTreeReport(TEST_ID_2);
        childrenA.setParentReport(parent);
        childrenA = treeReportRepository.save(childrenA);

        TreeReportEntity childrenB = buildTreeReport(TEST_ID_3);
        childrenB.setParentReport(parent);
        treeReportRepository.save(childrenB);

        TreeReportEntity grandChildrenAA = buildTreeReport(TEST_ID_4);
        grandChildrenAA.setParentReport(childrenA);
        treeReportRepository.saveAndFlush(grandChildrenAA);

        List<TreeReportEntity> listFindAll = treeReportRepository.findAll();
        assertEquals(4, listFindAll.size());

        assertRequestsCount(5, 4, 0, 0);
    }
}
