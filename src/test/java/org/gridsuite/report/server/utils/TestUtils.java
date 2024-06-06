/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.utils;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import org.gridsuite.report.server.ReportService;

import java.util.*;
import java.util.stream.Collectors;

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

    public static void assertReportListsAreEqualIgnoringIds(List<ReportNode> expectedNodeList, List<ReportNode> actualNodeList) {
        assertEquals(expectedNodeList.size(), actualNodeList.size());
        for (int i = 0; i < expectedNodeList.size(); i++) {
            assertReportsAreEqualIgnoringIds(expectedNodeList.get(i), actualNodeList.get(i));
        }
    }

    public static void assertReportsAreEqualIgnoringIds(ReportNode expectedNode, ReportNode actualNode) {
        assertEquals(expectedNode.getMessageKey(), actualNode.getMessageKey());
        assertEquals(expectedNode.getMessageTemplate(), actualNode.getMessageTemplate());
        assertEquals(expectedNode.getValues().size(), actualNode.getValues().size());
        for (var actualNodeEntry : actualNode.getValues().entrySet()) {
            TypedValue expectedValue = expectedNode.getValues().getOrDefault(actualNodeEntry.getKey(), null);
            assertNotNull(expectedValue);
            assertEquals(expectedValue.getType(), actualNodeEntry.getValue().getType());
            if (actualNodeEntry.getKey().equals("id")) {
                continue;
            }
            if (actualNodeEntry.getKey().equals(ReportService.SEVERITY_LIST_KEY)) {
                Set<String> expectedSeveritySet = parseSeverityList(expectedValue);
                Set<String> actualSeveritySet = parseSeverityList(actualNodeEntry.getValue());
                assertEquals(expectedSeveritySet, actualSeveritySet);
            } else {
                assertEquals(expectedValue.getValue(), actualNodeEntry.getValue().getValue());
            }
        }
        assertEquals(expectedNode.getChildren().size(), actualNode.getChildren().size());
        for (int i = 0; i < expectedNode.getChildren().size(); i++) {
            assertReportsAreEqualIgnoringIds(expectedNode.getChildren().get(i), actualNode.getChildren().get(i));
        }
    }

    private static Set<String> parseSeverityList(TypedValue severityList) {
        String expectedSeverityListToParse = severityList.getValue().toString();
        return Arrays.stream(expectedSeverityListToParse.substring(1, expectedSeverityListToParse.length() - 1).split(", "))
            .collect(Collectors.toSet());
    }
}
