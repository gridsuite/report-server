/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import org.gridsuite.report.server.entities.ReportNodeEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public record OptimizedReportNodeEntities(Map<Integer, List<UUID>> treeIds, Map<UUID, ReportNodeEntity> reportNodeEntityById) {

    public int treeDepth() {
        return treeIds.keySet().size();
    }
}
