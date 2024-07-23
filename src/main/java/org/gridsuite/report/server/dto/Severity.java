/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.report.server.dto;

public enum Severity {
    UNKNOWN,
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL;

    public static Severity fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(value);
        } catch (final IllegalArgumentException | NullPointerException e) {
            return UNKNOWN;
        }
    }
}
