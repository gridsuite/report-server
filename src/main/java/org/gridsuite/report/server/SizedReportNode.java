/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.powsybl.commons.report.ReportConstants;
import com.powsybl.commons.report.ReportNode;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Getter
@Setter
public class SizedReportNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(SizedReportNode.class);
    static final int MAX_MESSAGE_CHAR = 500;

    private String message;
    private int order;
    private int size;
    private boolean isLeaf;
    private int depth;
    private List<SizedReportNode> children;
    private String severity;

    public SizedReportNode(String message, int order, int size, boolean isLeaf, List<SizedReportNode> children, String severity, int depth) {
        this.message = message;
        this.order = order;
        this.size = size;
        this.isLeaf = isLeaf;
        this.children = children;
        this.severity = severity;
        this.depth = depth;
    }

    public static SizedReportNode from(ReportNode reportNode) {
        return new SizedReportNodeMapper().map(reportNode);
    }

    public static SizedReportNode from(ReportNode reportNode, int startingOrder) {
        return new SizedReportNodeMapper(startingOrder).map(reportNode);
    }

    private static final class SizedReportNodeMapper {

        private int counter;

        public SizedReportNodeMapper(int counter) {
            this.counter = counter;
        }

        public SizedReportNodeMapper() {
            this(0);
        }

        public SizedReportNode map(ReportNode reportNode) {
            return mapWithDepth(reportNode, 0);
        }

        private SizedReportNode mapWithDepth(ReportNode reportNode, int currentDepth) {
            SizedReportNode sizedReportNode = new SizedReportNode(
                truncatedMessage(reportNode.getMessage()),
                counter++,
                0,
                isLeaf(reportNode),
                new ArrayList<>(reportNode.getChildren().size()),
                getHighestSeverity(reportNode),
                currentDepth
            );
            int subTreeSize = reportNode.getChildren().stream().map(child -> {
                var childSizedReportNode = mapWithDepth(child, currentDepth + 1);
                sizedReportNode.getChildren().add(childSizedReportNode);
                return childSizedReportNode.getSize();
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

        private static String getHighestSeverity(ReportNode reportNode) {
            String highestSeverity = reportNode.getValues().containsKey(ReportConstants.SEVERITY_KEY)
                ? reportNode.getValues().get(ReportConstants.SEVERITY_KEY).getValue().toString()
                : Severity.UNKNOWN.toString();

            for (ReportNode child : reportNode.getChildren()) {
                String childSeverity = getHighestSeverity(child);
                if (Severity.fromValue(childSeverity).getLevel() > Severity.fromValue(highestSeverity).getLevel()) {
                    highestSeverity = childSeverity;
                }
            }
            return highestSeverity;
        }

        private static boolean isLeaf(ReportNode reportNode) {
            return reportNode.getChildren().isEmpty() && reportNode.getValues().containsKey(ReportConstants.SEVERITY_KEY);
        }
    }
}
