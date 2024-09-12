/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.utils;

import org.gridsuite.report.server.dto.Report;
import org.gridsuite.report.server.dto.ReportLog;

import static com.vladmihalcea.sql.SQLStatementCountValidator.assertDeleteCount;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertInsertCount;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertSelectCount;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertUpdateCount;
import static org.junit.Assert.assertFalse;
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
        assertEquals(expectedReportLogs.size(), actualReportLogs.size());
        expectedReportLogs.forEach(expectedReportLog -> {
            List<ReportLog> messages = actualReportLogs.stream().filter(reportLog -> reportLog.message().equals(expectedReportLog.message())).toList();
            assertFalse(messages.isEmpty());
            //because we can have the same msg multiple times we can't just check the message value
            assertEquals(messages.size(), expectedReportLogs.stream().filter(reportLog -> reportLog.message().equals(expectedReportLog.message())).toList().size());
        });
    }
}
