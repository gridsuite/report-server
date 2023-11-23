/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum SeverityLevel {
    UNKNOWN(0), TRACE(1), DEBUG(2), INFO(3), WARN(4), ERROR(5), FATAL(6);

    private final int level;

    private static final Logger LOGGER = LoggerFactory.getLogger(SeverityLevel.class);

    SeverityLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static SeverityLevel fromValue(String value) {
        try {
            return valueOf(value);
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("Report severity level unknown : {}", value);
            return UNKNOWN;
        }
    }
}
