/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.utils;

import org.gridsuite.report.server.entities.ReportEntity;
import org.gridsuite.report.server.entities.TreeReportEntity;

import java.util.Map;

import static com.vladmihalcea.sql.SQLStatementCountValidator.assertDeleteCount;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertInsertCount;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertSelectCount;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertUpdateCount;

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
}
