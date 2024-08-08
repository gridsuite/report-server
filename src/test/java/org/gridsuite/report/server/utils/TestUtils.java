/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

 package org.gridsuite.report.server.utils;

import org.gridsuite.report.server.dto.Report;

import java.util.*;
 
 import static com.vladmihalcea.sql.SQLStatementCountValidator.assertDeleteCount;
 import static com.vladmihalcea.sql.SQLStatementCountValidator.assertInsertCount;
 import static com.vladmihalcea.sql.SQLStatementCountValidator.assertSelectCount;
 import static com.vladmihalcea.sql.SQLStatementCountValidator.assertUpdateCount;
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
 
     public static void assertReportListsAreEqualIgnoringIds(List<Report> expectedNodeList, List<Report> actualNodeList) {
         assertEquals(expectedNodeList.size(), actualNodeList.size());
         for (int i = 0; i < expectedNodeList.size(); i++) {
             assertReportsAreEqualIgnoringIds(expectedNodeList.get(i), actualNodeList.get(i));
         }
     }
 
     public static void assertReportsAreEqualIgnoringIds(Report expectedNode, Report actualNode) {
         assertEquals(expectedNode.getMessage(), actualNode.getMessage());
         assertEquals(expectedNode.getSeverities().toString(), actualNode.getSeverities().toString());
         assertEquals(expectedNode.getSubReports().size(), actualNode.getSubReports().size());
         for (int i = 0; i < expectedNode.getSubReports().size(); i++) {
             assertReportsAreEqualIgnoringIds(expectedNode.getSubReports().get(i), actualNode.getSubReports().get(i));
         }
     }
 }