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

    public static void assertReportsAreEqualIgnoringIds(ReportNode node1, ReportNode node2) {
        assertEquals(node2.getMessageKey(), node1.getMessageKey());
        assertEquals(node2.getMessageTemplate(), node1.getMessageTemplate());
        assertEquals(node2.getValues().size(), node1.getValues().size());
        for (var entry : node2.getValues().entrySet()) {
            TypedValue value = node1.getValues().getOrDefault(entry.getKey(), null);
            assertNotNull(value);
            assertEquals(entry.getValue().getType(), value.getType());
            if (entry.getKey().equals("id")) {
                continue;
            }
            assertEquals(entry.getValue().getValue(), value.getValue());
        }
        assertEquals(node1.getChildren().size(), node2.getChildren().size());
        for (int i = 0; i < node1.getChildren().size(); i++) {
            assertReportsAreEqualIgnoringIds(node1.getChildren().get(i), node2.getChildren().get(i));
        }
    }

    public static void assertReportListsAreEqualIgnoringIds(List<ReportNode> nodeList1, List<ReportNode> nodeList2) {
        assertEquals(nodeList1.size(), nodeList2.size());
        for (int i = 0; i < nodeList1.size(); i++) {
            assertReportsAreEqualIgnoringIds(nodeList1.get(i), nodeList2.get(i));
        }
    }
}
