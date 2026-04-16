/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import org.gridsuite.report.server.dto.Report;
import org.gridsuite.report.server.entities.ReportProjection;

import java.util.*;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public final class ReportMapper {

    private ReportMapper() {
        // Should not be instantiated
    }

    public static Report map(List<ReportProjection> reportProjections) {
        if (reportProjections == null || reportProjections.isEmpty()) {
            return null;
        }

        Map<Integer, Report> orderToReport = new HashMap<>();
        Deque<int[]> stack = new ArrayDeque<>(); // [order, endOrder]

        ReportProjection rootProj = reportProjections.get(0);
        Report root = createReport(rootProj);
        orderToReport.put(rootProj.order(), root);
        stack.push(new int[]{rootProj.order(), rootProj.endOrder()});

        for (int i = 1; i < reportProjections.size(); i++) {
            ReportProjection proj = reportProjections.get(i);

            // Pop nodes whose range no longer contains this node
            while (!stack.isEmpty() && stack.peek()[1] < proj.order()) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                break;
            }

            Report parent = orderToReport.get(stack.peek()[0]);
            Report child = parent.addEmptyReport();
            child.setMessage(proj.message());
            child.setSeverity(Severity.fromValue(proj.severity()));
            child.setDepth(proj.depth());
            child.setId(proj.id());
            child.setOrder(proj.order());
            child.setParentOrder(proj.parentOrder());

            orderToReport.put(proj.order(), child);
            stack.push(new int[]{proj.order(), proj.endOrder()});
        }

        return root;
    }

    private static Report createReport(ReportProjection proj) {
        Report report = new Report();
        report.setMessage(Optional.ofNullable(proj.message()).orElse(proj.id().toString()));
        report.setSeverity(Severity.fromValue(proj.severity()));
        report.setDepth(proj.depth());
        report.setId(proj.id());
        report.setOrder(proj.order());
        report.setParentOrder(proj.parentOrder());
        return report;
    }
}
