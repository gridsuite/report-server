/**
 *  Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;
import static org.gridsuite.report.server.utils.TestUtils.assertRequestsCount;
import java.util.*;

import org.gridsuite.report.server.entities.TreeReportEntity;
import org.gridsuite.report.server.repositories.TreeReportRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Tag;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.vladmihalcea.sql.SQLStatementCountValidator;

/**
 * @author Charly Boutier <charly.boutier at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Tag("IntegrationTest")
public class TreeReportRepositoryTest {

    @Autowired
    private TreeReportRepository treeReportRepository;

    @Before
    public void setUp() {
        SQLStatementCountValidator.reset();
    }

    @Test
    public void testCreateTreeReportQueryCount() {
        TreeReportEntity entity = new TreeReportEntity();
        entity.setName("test1");
        entity.setNanos(10L);
        entity.setParentReport(null);
        entity.setReport(null);
        entity.setIdNode(UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4"));

        treeReportRepository.saveAndFlush(entity);
        assertRequestsCount(0, 1, 0, 0);
    }
}
