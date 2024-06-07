/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.utils;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import org.gridsuite.report.server.entities.ReportEntity;
import org.gridsuite.report.server.entities.TreeReportEntity;

import java.util.List;
import java.util.Map;

import static com.vladmihalcea.sql.SQLStatementCountValidator.assertDeleteCount;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertInsertCount;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertSelectCount;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertUpdateCount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class TestUtils {

    private TestUtils() {
    }

    public static void assertRequestsCount(long select, long insert, long update, long delete) {
        assertSelectCount(select);
        assertInsertCount(insert);
        assertUpdateCount(update);
        assertDeleteCount(delete);
    }

    public static TreeReportEntity createTreeReport(String name, ReportEntity reportEntity, TreeReportEntity parent, long nanos) {
        TreeReportEntity entity = new TreeReportEntity();
        entity.setName(name);
        entity.setNanos(nanos);
        entity.setParentReport(parent);
        entity.setReport(reportEntity);
        entity.setDictionary(Map.of(
                "test", "test",
                "log1", "log1",
                "log2", "log2",
                "log3", "log3")
        );
        return entity;
    }

    public static void assertReportsAreEqualIgnoringIds(ReportNode expectedNode, ReportNode actualNode) {
        assertEquals(expectedNode.getMessageKey(), actualNode.getMessageKey());
        assertEquals(expectedNode.getMessageTemplate(), actualNode.getMessageTemplate());
        assertEquals(expectedNode.getValues().size(), actualNode.getValues().size());
        for (var actualNodeEntry : actualNode.getValues().entrySet()) {
            TypedValue expectedValue = expectedNode.getValues().getOrDefault(actualNodeEntry.getKey(), null);
            assertNotNull(expectedValue);
            assertEquals(expectedValue.getType(), actualNodeEntry.getValue().getType());
            if (actualNodeEntry.getKey().equals("subReportId")) {
                continue;
            }
            assertEquals(expectedValue.getValue(), actualNodeEntry.getValue().getValue());
        }
        assertEquals(expectedNode.getChildren().size(), actualNode.getChildren().size());
        for (int i = 0; i < expectedNode.getChildren().size(); i++) {
            assertReportsAreEqualIgnoringIds(expectedNode.getChildren().get(i), actualNode.getChildren().get(i));
        }
    }

    public static void assertReportListsAreEqualIgnoringIds(List<ReportNode> expectedNodeList, List<ReportNode> actualNodeList) {
        assertEquals(expectedNodeList.size(), actualNodeList.size());
        for (int i = 0; i < expectedNodeList.size(); i++) {
            assertReportsAreEqualIgnoringIds(expectedNodeList.get(i), actualNodeList.get(i));
        }
    }
}
