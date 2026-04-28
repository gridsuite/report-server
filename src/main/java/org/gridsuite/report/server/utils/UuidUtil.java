/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.utils;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

public final class UuidUtil {

    private UuidUtil() {
    }

    /**
    * Create a uuidv7 generator that will generate all uuids for the same root report. The generator is created per root report to avoid having similar uuids for unrelated reports which would be surprising to people looking at the ids.
    * NOTE: The uuids would be similar because we generate incremented uuids when generating many uuids during the same millisecond,
    * and we observe that currently we dogenerate up to ~500 report entities in the same millisecond.
    * So for a given root report, we have monotonicity because:
    * - in the same milisecond, the ids will be incremented starting from the random value.
    * - in separate miliseconds, the random value will reset possibly to a smaller value, but the millisecond is greater and the milliseconds comes first.
    * Performance gains:
    *   - With the millisecond prefix, we already remove almost all problems of having many Full Page Images (FPIs) in the PostgreSQL WAL (the FPI come from the index pages, where a new random uuid would have been inserted in the index in a different page virtually every time).
    *   - With the submillisecond increments, it should additionally improve performance because of sequential writes to the index (not at as impactful as the milliseconds prefix but not hard to implement and may still be worthwile)
    * NOTE: PostgreSQL 14 does not support uuid v7 natively (gen_uuidv7() was only introduced in PostgreSQL 17).
    * This is not a problem because uuid v7 generation happens entirely in Java
    * PostgreSQL only stores the resulting value as a standard UUID column (it does not care about the uuid version, it treats all UUIDs as 128-bit values.)
    * NOTE: with this approach no data migration is needed since uuid v7 is stored as a plain UUID column (fully compatible with existing uuid v4 rows).
    * we kept the UUID alongside order_ because frontend uses parentId UUIDs to navigate the tree, it is simpler than switching to a composite id (rootNodeId + order_) which would be a breaking API change.
    */
    public static TimeBasedEpochGenerator newUUIDv7Generator() {
        return Generators.timeBasedEpochGenerator();
    }
}
