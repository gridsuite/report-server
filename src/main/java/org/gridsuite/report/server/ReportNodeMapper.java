/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.powsybl.commons.report.*;
import jakarta.persistence.EntityNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.gridsuite.report.server.entities.ValueEntity;

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

    public static ReportNode mapper(OptimizedReportNodeEntities optimizedReportNodeEntities, @Nullable Set<String> severityLevels, @Nullable String reportNameFilter, @Nullable ReportService.ReportNameMatchingType reportNameMatchingType) {
        UUID rootId = getRootId(optimizedReportNodeEntities);

        Map<UUID, ReportNode> reportsMap = new HashMap<>();
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

    private static void mapRootNode(ReportNodeEntity rootReportNodeEntity, Map<UUID, ReportNode> reportsMap) {
        ReportNodeBuilder builder = ReportNode.newRootReportNode();
        if (!Objects.isNull(rootReportNodeEntity.getMessageKey())) {
            builder.withMessageTemplate(rootReportNodeEntity.getMessageKey(), rootReportNodeEntity.getMessage());
        } else {
            builder.withMessageTemplate(rootReportNodeEntity.getId().toString(), rootReportNodeEntity.getId().toString());
        }
        mapValues(rootReportNodeEntity, builder);
        ReportNode rootReportNode = builder.build();
        reportsMap.put(rootReportNodeEntity.getId(), rootReportNode);
    }

    private static void mapLevel(OptimizedReportNodeEntities optimizedReportNodeEntities, Map<UUID, ReportNode> reportNodesById, int level, Predicate<ReportNodeEntity> filter) {
        List<UUID> nodeIdsToMap = optimizedReportNodeEntities.treeIds().get(level).stream()
            .map(optimizedReportNodeEntities.reportNodeEntityById()::get)
            .sorted(Comparator.comparing(ReportNodeEntity::getNanos))
            .filter(filter)
            .map(ReportNodeEntity::getId)
            .toList();
        nodeIdsToMap.forEach(id -> mapReportNodesEntity(id, optimizedReportNodeEntities.reportNodeEntityById(), reportNodesById));
    }

    private static void mapReportNodesEntity(UUID id, Map<UUID, ReportNodeEntity> reportEntities, Map<UUID, ReportNode> reports) {
        ReportNodeEntity reportNodeEntity = reportEntities.get(id);
        Optional.ofNullable(reports.get(reportNodeEntity.getParent().getId())).ifPresent(parentReport -> {
            ReportNodeAdder adder = parentReport.newReportNode()
                .withMessageTemplate(reportNodeEntity.getMessageKey(), reportNodeEntity.getMessage());
            mapValues(reportNodeEntity, adder);
            ReportNode newNode = adder.add();
            reports.put(id, newNode);
        });
    }

    private static void mapValues(ReportNodeEntity rootReportNodeEntity, ReportNodeAdderOrBuilder<?> adderOrBuilder) {
        for (ValueEntity valueEntity : rootReportNodeEntity.getValues()) {
            addTypedValue(valueEntity, adderOrBuilder);
        }
        if (hasNoReportSeverity(rootReportNodeEntity)) {
            adderOrBuilder.withTypedValue("id", rootReportNodeEntity.getId().toString(), "ID");
            if (!rootReportNodeEntity.getSeverities().isEmpty()) {
                adderOrBuilder.withTypedValue(ReportService.SEVERITY_LIST_KEY, rootReportNodeEntity.getSeverities().toString(), TypedValue.SEVERITY);
            }
        }
    }

    private static void addTypedValue(ValueEntity value, ReportNodeAdderOrBuilder<?> adder) {
        switch (value.getLocalValueType()) {
            case DOUBLE -> adder.withTypedValue(value.getKey(), Double.parseDouble(value.getValue()), value.getValueType());
            case INTEGER -> adder.withTypedValue(value.getKey(), Integer.parseInt(value.getValue()), value.getValueType());
            default -> adder.withTypedValue(value.getKey(), value.getValue(), value.getValueType());
        }
    }

    private static Predicate<ReportNodeEntity> hasOneOfSeverityLevels(Set<String> severityLevels) {
        return reportNodeEntity -> severityLevels == null ||
            hasNoReportSeverity(reportNodeEntity) ||
            reportNodeEntity.getValues()
                .stream()
                .filter(v -> v.getKey().equals(ReportConstants.REPORT_SEVERITY_KEY))
                .findFirst()
                .map(ValueEntity::getValue)
                .map(severityLevels::contains)
                .orElse(false);
    }

    private static Predicate<ReportNodeEntity> reportMessageKeyMatches(@Nullable String reportNameFilter, @Nullable ReportService.ReportNameMatchingType reportNameMatchingType) {
        return reportNodeEntity -> StringUtils.isBlank(reportNameFilter)
            || reportNodeEntity.getMessageKey().startsWith("Root") // FIXME remove this hack when "Root" report will follow the same rules than computations and modifications
            || reportNameMatchingType == ReportService.ReportNameMatchingType.EXACT_MATCHING && reportNodeEntity.getMessageKey().equals(reportNameFilter)
            || reportNameMatchingType == ReportService.ReportNameMatchingType.ENDS_WITH && reportNodeEntity.getMessageKey().endsWith(reportNameFilter);
    }

    private static boolean hasNoReportSeverity(ReportNodeEntity reportNodeEntity) {
        return reportNodeEntity.getValues().stream().noneMatch(v -> v.getKey().equals(ReportConstants.REPORT_SEVERITY_KEY));
    }
}
