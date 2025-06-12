/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import org.gridsuite.report.server.dto.ReportLog;
import org.gridsuite.report.server.entities.ReportProjection;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public final class ReportLogMapper {

    private ReportLogMapper() {
        // Should not be instantiated
    }

    public static ReportLog map(ReportProjection entity) {
        return new ReportLog(entity.message(), Severity.fromValue(entity.severity()), entity.depth(), entity.parentId());
    }
}
