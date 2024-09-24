/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.utils;

import org.gridsuite.report.server.dto.Report;
import org.gridsuite.report.server.dto.ReportLog;

import java.util.*;
import java.util.stream.IntStream;

import static com.vladmihalcea.sql.SQLStatementCountValidator.assertDeleteCount;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertInsertCount;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertSelectCount;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertUpdateCount;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class TestUtils {

    private TestUtils() {
    }

    public static void assertRequestsCount(long select, long insert, long update, long delete) {
        assertSelectCount(select);
        assertInsertCount(insert);
        assertUpdateCount(update);
        assertDeleteCount(delete);
    }

    public static void assertReportsAreEqualIgnoringIds(Report expectedNode, Report actualNode) {
        assertEquals(expectedNode.getMessage(), actualNode.getMessage());
        assertEquals(expectedNode.getSeverities().toString(), actualNode.getSeverities().toString());
        assertEquals(expectedNode.getSubReports().size(), actualNode.getSubReports().size());
        for (int i = 0; i < expectedNode.getSubReports().size(); i++) {
            assertReportsAreEqualIgnoringIds(expectedNode.getSubReports().get(i), actualNode.getSubReports().get(i));
        }
    }

    public static void assertReportMessagesAreEqual(List<ReportLog> expectedReportLogs, List<ReportLog> actualReportLogs) {
        assertTrue(areEqual(expectedReportLogs, actualReportLogs));
    }

    //To compare the lists of reportLogs (with order)
    public static boolean areEqual(List<ReportLog> first, List<ReportLog> second) {
        return first.size() == second.size() &&
                IntStream.range(0, first.size()).allMatch(index ->
                        customCompare(first.get(index), second.get(index)));
    }

    //to compare the ReportLogs without checking for the parentId
    static boolean customCompare(ReportLog log1, ReportLog log2) {
        if (log1 == log2) {
            return true;
        }
        if (!Objects.equals(log1.message(), log2.message())) {
            return false;
        }
        return log1.severity().containsAll(log2.severity());
    }
}
