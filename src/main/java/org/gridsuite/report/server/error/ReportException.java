/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.error;

import com.powsybl.ws.commons.error.AbstractBusinessException;
import lombok.NonNull;

import java.util.Objects;
import java.util.UUID;

public class ReportException extends AbstractBusinessException {

    private static final String NOT_FOUND_SUFFIX = "' not found";

    private final ReportBusinessErrorCode errorCode;

    public ReportException(ReportBusinessErrorCode errorCode, String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public static ReportException createReportNotFound(@NonNull UUID id) {
        return new ReportException(ReportBusinessErrorCode.REPORT_NOT_FOUND,
                "Report '" + id + NOT_FOUND_SUFFIX);
    }

    public static ReportException createNotARootReport(@NonNull UUID id) {
        return new ReportException(ReportBusinessErrorCode.REPORT_NOT_A_ROOT,
                "Report '" + id + "' is not a root report");
    }

    @NonNull
    @Override
    public ReportBusinessErrorCode getBusinessErrorCode() {
        return errorCode;
    }
}
