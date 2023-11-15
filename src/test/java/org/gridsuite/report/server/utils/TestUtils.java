/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.ReporterModel;
import lombok.SneakyThrows;

import java.util.List;
import java.util.UUID;

import static com.vladmihalcea.sql.SQLStatementCountValidator.*;
import static org.assertj.core.api.Assertions.assertThat;

public final class TestUtils {

    private TestUtils() {
    }

    public static void assertRequestsCount(long select, long insert, long update, long delete) {
        assertSelectCount(select);
        assertInsertCount(insert);
        assertUpdateCount(update);
        assertDeleteCount(delete);
    }

    @SneakyThrows
    public static void assertReportEquals(String reportExpectedJsonString, String reportResultJsonString, ObjectMapper mapper) {
        List<ReporterModel> expectedReport = mapper.readValue(reportExpectedJsonString, new TypeReference<>() {
        });
        List<ReporterModel> resultReport = mapper.readValue(reportResultJsonString, new TypeReference<>() {
        });
        assertThat(resultReport)
            .usingRecursiveComparison()
            .ignoringAllOverriddenEquals()
            .ignoringFieldsOfTypes(UUID.class)
            .ignoringFieldsMatchingRegexes(".*taskValues.id.value$")
            .isEqualTo(expectedReport);
    }
}
