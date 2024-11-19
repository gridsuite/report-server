/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Getter
@Setter
public class SizedReportNode {
    private String message;
    private int order;
    private int size;
    private List<SizedReportNode> children;
    private Set<String> severities;

    public SizedReportNode(String message, int order, int size, List<SizedReportNode> children, Set<String> severities) {
        this.message = message;
        this.order = order;
        this.size = size;
        this.children = children;
        this.severities = severities;
    }
}
