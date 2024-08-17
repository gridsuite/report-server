/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import jakarta.persistence.EntityNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.report.server.dto.Report;
import org.gridsuite.report.server.entities.ReportNodeEntity;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public final class ReportNodeMapper {

    private ReportNodeMapper() {
        // Should not be instantiated
    }

    public static Report map(OptimizedReportNodeEntities optimizedReportNodeEntities, @Nullable Set<String> severityLevels, @Nullable String reportNameFilter, @Nullable ReportService.ReportNameMatchingType reportNameMatchingType) {
        UUID rootId = getRootId(optimizedReportNodeEntities);

        Map<UUID, Report> reportsMap = new HashMap<>();
        mapRootNode(optimizedReportNodeEntities.reportNodeEntityById().get(rootId), reportsMap);
        if (optimizedReportNodeEntities.treeDepth() > 1) {
            mapLevel(optimizedReportNodeEntities, reportsMap, 1, reportMessageKeyMatches(reportNameFilter, reportNameMatchingType).and(hasOneOfSeverityLevels(severityLevels)));
        }
        for (int i = 2; i < optimizedReportNodeEntities.treeDepth(); i++) {
            mapLevel(optimizedReportNodeEntities, reportsMap, i, hasOneOfSeverityLevels(severityLevels));
        }

        return reportsMap.get(rootId);
    }

    private static UUID getRootId(OptimizedReportNodeEntities optimizedReportNodeEntities) {
        List<UUID> rootIds = optimizedReportNodeEntities.treeIds().get(0);
        if (rootIds == null || rootIds.size() != 1) {
            throw new EntityNotFoundException();
        }
        return rootIds.get(0);
    }

    private static void mapRootNode(ReportNodeEntity rootReportNodeEntity, Map<UUID, Report> reportsMap) {
        Report rootReport = new Report();
        if (!Objects.isNull(rootReportNodeEntity.getMessage())) {
            rootReport.setMessage(rootReportNodeEntity.getMessage());
        } else {
            rootReport.setMessage(rootReportNodeEntity.getId().toString());
        }
        mapValues(rootReportNodeEntity, rootReport);
        reportsMap.put(rootReportNodeEntity.getId(), rootReport);
    }

    private static void mapLevel(OptimizedReportNodeEntities optimizedReportNodeEntities, Map<UUID, Report> reportNodesById, int level, Predicate<ReportNodeEntity> filter) {
        List<UUID> nodeIdsToMap = optimizedReportNodeEntities.treeIds().get(level).stream()
            .map(optimizedReportNodeEntities.reportNodeEntityById()::get)
            .sorted(Comparator.comparing(ReportNodeEntity::getNanos))
            .filter(filter)
            .map(ReportNodeEntity::getId)
            .toList();
        nodeIdsToMap.forEach(id -> mapReportNodesEntity(id, optimizedReportNodeEntities.reportNodeEntityById(), reportNodesById));
    }

    private static void mapReportNodesEntity(UUID id, Map<UUID, ReportNodeEntity> reportEntities, Map<UUID, Report> reports) {
        ReportNodeEntity reportNodeEntity = reportEntities.get(id);
        Optional.ofNullable(reports.get(reportNodeEntity.getParent().getId())).ifPresent(parentReport -> {
            Report report = parentReport.addEmptyReport();
            if (reportNodeEntity.getMessage().contains("@")) {
                report.setMessage(reportNodeEntity.getMessage().split("@")[0]);
            } else {
                report.setMessage(reportNodeEntity.getMessage());
            }
            mapValues(reportNodeEntity, report);
            reports.put(id, report);
        });
    }

    private static void mapValues(ReportNodeEntity rootReportNodeEntity, Report report) {
        report.setSeverities(rootReportNodeEntity.getSeverities().stream().map(Severity::valueOf).toList());
        if (!rootReportNodeEntity.getChildren().isEmpty() || rootReportNodeEntity.getSeverities().isEmpty()) {
            report.setId(rootReportNodeEntity.getId());
        }
    }

    private static Predicate<ReportNodeEntity> hasOneOfSeverityLevels(Set<String> severityLevels) {
        return reportNodeEntity -> severityLevels == null ||
            hasNoReportSeverity(reportNodeEntity) || reportNodeEntity.getSeverities().stream().anyMatch(severityLevels::contains);
    }

    private static Predicate<ReportNodeEntity> reportMessageKeyMatches(@Nullable String reportNameFilter, @Nullable ReportService.ReportNameMatchingType reportNameMatchingType) {
        return reportNodeEntity -> StringUtils.isBlank(reportNameFilter)
            || reportNodeEntity.getMessage().startsWith("Root") // FIXME remove this hack when "Root" report will follow the same rules than computations and modifications
            || reportNameMatchingType == ReportService.ReportNameMatchingType.EXACT_MATCHING && reportNodeEntity.getMessage().equals(reportNameFilter)
            || reportNameMatchingType == ReportService.ReportNameMatchingType.ENDS_WITH && reportNodeEntity.getMessage().endsWith(reportNameFilter);
    }

    private static boolean hasNoReportSeverity(ReportNodeEntity reportNodeEntity) {
        return reportNodeEntity.getSeverities().isEmpty();
    }
}
