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
        mapLevels(optimizedReportNodeEntities, reportsMap, severityLevels, reportNameFilter, reportNameMatchingType);

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
        Report rootReport = createReportFromNode(rootReportNodeEntity);
        reportsMap.put(rootReportNodeEntity.getId(), rootReport);
    }

    private static Report createReportFromNode(ReportNodeEntity reportNodeEntity) {
        Report report = new Report();
        report.setMessage(Optional.ofNullable(reportNodeEntity.getMessage()).orElse(reportNodeEntity.getId().toString()));
        mapValues(reportNodeEntity, report);
        return report;
    }

    private static void mapLevels(OptimizedReportNodeEntities optimizedReportNodeEntities, Map<UUID, Report> reportsMap, @Nullable Set<String> severityLevels, @Nullable String reportNameFilter, @Nullable ReportService.ReportNameMatchingType reportNameMatchingType) {
        if (optimizedReportNodeEntities.treeDepth() > 1) {
            mapLevel(optimizedReportNodeEntities, reportsMap, 1, reportMessageKeyMatches(reportNameFilter, reportNameMatchingType).and(hasOneOfSeverityLevels(severityLevels)));
        }
        for (int i = 2; i < optimizedReportNodeEntities.treeDepth(); i++) {
            mapLevel(optimizedReportNodeEntities, reportsMap, i, hasOneOfSeverityLevels(severityLevels));
        }
    }

    private static void mapLevel(OptimizedReportNodeEntities optimizedReportNodeEntities, Map<UUID, Report> reportsMap, int level, Predicate<ReportNodeEntity> filter) {
        List<UUID> nodeIdsToMap = optimizedReportNodeEntities.treeIds().get(level).stream()
            .map(optimizedReportNodeEntities.reportNodeEntityById()::get)
            .sorted(Comparator.comparing(ReportNodeEntity::getNanos))
            .filter(filter)
            .map(ReportNodeEntity::getId)
            .toList();
        nodeIdsToMap.forEach(id -> mapReportNodeEntity(id, optimizedReportNodeEntities.reportNodeEntityById(), reportsMap));
    }

    private static void mapReportNodeEntity(UUID id, Map<UUID, ReportNodeEntity> reportEntities, Map<UUID, Report> reports) {
        ReportNodeEntity reportNodeEntity = reportEntities.get(id);
        Optional.ofNullable(reports.get(reportNodeEntity.getParent().getId())).ifPresent(parentReport -> {
            Report report = parentReport.addEmptyReport();
            report.setMessage(extractMessage(reportNodeEntity));
            mapValues(reportNodeEntity, report);
            reports.put(id, report);
        });
    }

    private static String extractMessage(ReportNodeEntity reportNodeEntity) {
        return reportNodeEntity.getMessage().contains("@") ? reportNodeEntity.getMessage().split("@")[0] : reportNodeEntity.getMessage();
    }

    private static void mapValues(ReportNodeEntity reportNodeEntity, Report report) {
        report.setSeverities(reportNodeEntity.getSeverities().stream().map(Severity::valueOf).toList());
        if (!reportNodeEntity.getChildren().isEmpty() || reportNodeEntity.getSeverities().isEmpty()) {
            report.setId(reportNodeEntity.getId());
        }
        report.setParentId(Optional.ofNullable(reportNodeEntity.getParent()).map(ReportNodeEntity::getId).orElse(null));
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
