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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private List<SizedReportNode> children;
    private Set<String> severities;

    public SizedReportNode(String message, int order, int size, boolean isLeaf, List<SizedReportNode> children, Set<String> severities) {
        this.message = message;
        this.order = order;
        this.size = size;
        this.isLeaf = isLeaf;
        this.children = children;
        this.severities = severities;
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
            SizedReportNode sizedReportNode = new SizedReportNode(
                truncatedMessage(reportNode.getMessage()),
                counter++,
                0,
                isLeaf(reportNode),
                new ArrayList<>(),
                severities(reportNode)
            );
            int subTreeSize = reportNode.getChildren().stream().map(child -> {
                var childSizedReportNode = map(child);
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

        private static Set<String> severities(ReportNode reportNode) {
            Set<String> severities = new HashSet<>();
            if (reportNode.getValues().containsKey(ReportConstants.SEVERITY_KEY)) {
                severities.add(reportNode.getValues().get(ReportConstants.SEVERITY_KEY).getValue().toString());
            }
            if (!isLeaf(reportNode)) {
                reportNode.getChildren().forEach(child -> severities.addAll(severities(child)));
            }
            return severities;
        }

        private static boolean isLeaf(ReportNode reportNode) {
            return reportNode.getChildren().isEmpty() && reportNode.getValues().containsKey(ReportConstants.SEVERITY_KEY);
        }
    }
}
