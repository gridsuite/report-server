/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.error;

import com.powsybl.ws.commons.error.BusinessErrorCode;

/**
 * Business error codes emitted by the report service.
 */
public enum ReportBusinessErrorCode implements BusinessErrorCode {
    REPORT_NOT_FOUND("report.notFound"),
    REPORT_NOT_A_ROOT("report.notARoot");

    private final String code;

    ReportBusinessErrorCode(String code) {
        this.code = code;
    }

    public String value() {
        return code;
    }
}
