/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.utils;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

public final class UuidUtil {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UuidUtil() {
    }

    /**
     * Generates a UUID v7 (time-ordered). The millisecond timestamp prefix makes
     * sequential inserts land on the same B-tree leaf page, reducing Full Page
     * Images (FPIs) in the PostgreSQL WAL.
     */
    public static UUID generateV7() {
        return GENERATOR.generate();
    }
}
