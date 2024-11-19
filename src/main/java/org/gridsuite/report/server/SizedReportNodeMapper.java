/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.powsybl.commons.report.ReportConstants;
import com.powsybl.commons.report.ReportNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public final class SizedReportNodeMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SizedReportNodeMapper.class);
    static final int MAX_MESSAGE_CHAR = 500;

    private int counter;

    public static SizedReportNode map(ReportNode reportNode) {
        return new SizedReportNodeMapper().mapInternal(reportNode);
    }

    public static SizedReportNode map(ReportNode reportNode, int startingOrder) {
        return new SizedReportNodeMapper(startingOrder).mapInternal(reportNode);
    }

    public SizedReportNodeMapper(int counter) {
        this.counter = counter;
    }

    public SizedReportNodeMapper() {
        this(0);
    }

    private SizedReportNode mapInternal(ReportNode reportNode) {
        SizedReportNode sizedReportNode = new SizedReportNode(
            truncatedMessage(reportNode.getMessage()),
            counter++,
            0,
            new ArrayList<>(),
            severities(reportNode)
        );
        int subTreeSize = reportNode.getChildren().stream().map(child -> {
            var childCoucou = map(child);
            sizedReportNode.getChildren().add(childCoucou);
            return childCoucou.getSize();
        }).reduce(1, Integer::sum);
        sizedReportNode.setSize(subTreeSize);
        return sizedReportNode;
    }

    private static String truncatedMessage(String message) {
        if (message.length() <= MAX_MESSAGE_CHAR) {
            return message;
        }
        String truncatedMessage = message.substring(0, MAX_MESSAGE_CHAR);
        LOGGER.error("Message {}... exceeds max character length ({}). It will be truncated", truncatedMessage, MAX_MESSAGE_CHAR);
        return truncatedMessage;
    }

    private static Set<String> severities(ReportNode reportNode) {
        Set<String> severities = new HashSet<>();
        if (reportNode.getChildren().isEmpty() && reportNode.getValues().containsKey(ReportConstants.SEVERITY_KEY)) {
            severities.add(reportNode.getValues().get(ReportConstants.SEVERITY_KEY).getValue().toString());
        } else {
            reportNode.getChildren().forEach(child -> severities.addAll(severities(child)));
        }
        return severities;
    }
}
