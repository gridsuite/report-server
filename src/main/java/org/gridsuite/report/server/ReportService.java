/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.google.common.collect.Lists;
import com.powsybl.commons.report.ReportConstants;
import com.powsybl.commons.report.ReportNode;
import lombok.NonNull;
import org.gridsuite.report.server.dto.Report;
import org.gridsuite.report.server.dto.ReportLog;
import org.gridsuite.report.server.entities.LogProjection;
import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.gridsuite.report.server.repositories.ReportNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.gridsuite.report.server.ReportNodeMapper.map;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Service
public class ReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);

    private static final long NANOS_FROM_EPOCH_TO_START;

    // the maximum number of parameters allowed in an In query. Prevents the number of parameters to reach the maximum allowed (65,535)
    private static final int SQL_QUERY_MAX_PARAM_NUMBER = 10000;

    private final ReportNodeRepository reportNodeRepository;

    private final ReportLogRepository reportLogRepository;

    static {
        long nanoNow = System.nanoTime();
        long nanoViaMillis = Instant.now().toEpochMilli() * 1000000;
        NANOS_FROM_EPOCH_TO_START = nanoNow - nanoViaMillis;
    }

    public ReportService(ReportNodeRepository reportNodeRepository) {
        this.reportNodeRepository = reportNodeRepository;
    }

    // To use only for tests to fetch an entity with all the relationships
    @Transactional(readOnly = true)
    public Optional<ReportNodeEntity> getReportNodeEntity(UUID id) {
        return Optional.ofNullable(reportNodeRepository.findAllWithChildrenByIdIn(List.of(id)).get(0))
            .map(reportNodeEntity -> {
                reportNodeRepository.findAllWithSeveritiesByIdIn(List.of(id));
                return reportNodeEntity;
            });
    }

    @Transactional(readOnly = true)
    public Report getReport(UUID reportId, Set<String> severityLevels) {
        Objects.requireNonNull(reportId);
        OptimizedReportNodeEntities optimizedReportNodeEntities = getOptimizedReportNodeEntities(reportId);
        return map(optimizedReportNodeEntities, severityLevels);
    }

    public List<ReportLog> getReportLogs(UUID rootReportNodeId, @Nullable Set<String> severityLevelsFilter, @Nullable String messageFilter) {
        // We first do a recursive find from the root node to aggregate all the IDs of the tree by their tree depth
        Map<Integer, List<UUID>> treeIds = getTreeFromRootReport(rootReportNodeId);

        // Then we flatten the ID list to be able to fetch related data in one request (what if this list is too big ?)
        List<UUID> idList = treeIds.values().stream().flatMap(Collection::stream).toList();

        List<ReportLog> reportLogs = new ArrayList<>();
        Lists.partition(idList, SQL_QUERY_MAX_PARAM_NUMBER).forEach(ids -> {
            List<LogProjection> logProjections;
            if (severityLevelsFilter == null) {
                logProjections = reportNodeRepository.findAllByIdInAndMessageContainingIgnoreCase(ids, messageFilter == null ? "" : messageFilter);
            } else {
                logProjections = reportNodeRepository.findAllByIdInAndMessageContainingIgnoreCaseAndSeveritiesIn(ids, messageFilter == null ? "" : messageFilter, severityLevelsFilter);
            }
            reportLogs.addAll(logProjections.stream().map(ReportService::toReportLog).toList());
        });
        return reportLogs;
    }

    private OptimizedReportNodeEntities getOptimizedReportNodeEntities(UUID rootReportNodeId) {
        // We first do a recursive find from the root node to aggregate all the IDs of the tree by their tree depth
        Map<Integer, List<UUID>> treeIds = getTreeFromRootReport(rootReportNodeId);

        // Then we flatten the ID list to be able to fetch related data in one request (what if this list is too big ?)
        List<UUID> idList = treeIds.values().stream().flatMap(Collection::stream).toList();

        Map<UUID, ReportNodeEntity> reportNodeEntityById = new HashMap<>();
        Lists.partition(idList, SQL_QUERY_MAX_PARAM_NUMBER).forEach(ids -> {
            // We do these 2 requests to load all data related to ReportNodeEntity thanks to JPA first-level of cache, and we just do a mapping to find fast an entity by its ID
            List<ReportNodeEntity> reportNodeEntities = reportNodeRepository.findAllWithSeveritiesByIdIn(ids);
            reportNodeRepository.findAllWithChildrenByIdIn(ids);
            reportNodeEntities.forEach(reportNodeEntity -> reportNodeEntityById.put(reportNodeEntity.getId(), reportNodeEntity));
        });

        return new OptimizedReportNodeEntities(treeIds, reportNodeEntityById);
    }

    public Report getEmptyReport(@NonNull UUID id, @NonNull String defaultName) {
        Report emptyReport = new Report();
        emptyReport.setId(id);
        emptyReport.setMessage(defaultName);
        return emptyReport;
    }

    @Transactional
    public void createReport(UUID id, ReportNode reportNode) {
        reportNodeRepository.findById(id).ifPresentOrElse(
            reportEntity -> {
                LOGGER.debug("Reporter {} present, append ", reportNode.getMessage());
                appendReportElements(reportEntity, reportNode);
            },
            () -> {
                LOGGER.debug("Reporter {} absent, create ", reportNode.getMessage());
                createNewReport(id, reportNode);
            }
        );
    }

    private Map<Integer, List<UUID>> getTreeFromRootReport(UUID rootTreeReportId) {
        return reportNodeRepository.findTreeFromRootReport(rootTreeReportId)
                .stream()
                .collect(Collectors.groupingBy(
                        result -> (Integer) result[0],
                        Collectors.mapping(
                                result -> UUID.fromString((String) result[1]),
                                Collectors.toList()
                        )
                ));
    }

    private void appendReportElements(ReportNodeEntity reportEntity, ReportNode reportNode) {
        // We don't have to update more ancestors because we only append at root level, and we know it
        // But if we want to generalize appending to any report we should update the severity list of all the ancestors recursively
        reportEntity.addSeverities(reportNode.getChildren().stream().map(ReportService::severities)
                .flatMap(Collection::stream).collect(Collectors.toSet()));
        reportNode.getChildren().forEach(c -> saveReportNodeRecursively(reportEntity, c));

    }

    private void createNewReport(UUID id, ReportNode reportNode) {
        var persistedReport = reportNodeRepository.save(
            new ReportNodeEntity(id, reportNode.getMessage(), System.nanoTime() - NANOS_FROM_EPOCH_TO_START, null, severities(reportNode))
        );
        reportNode.getChildren().forEach(c -> saveReportNodeRecursively(persistedReport, c));
    }

    private void saveReportNodeRecursively(ReportNodeEntity parentReportNodeEntity, ReportNode reportNode) {
        var reportNodeEntity = new ReportNodeEntity(
            reportNode.getMessage(),
            System.nanoTime() - NANOS_FROM_EPOCH_TO_START,
            parentReportNodeEntity,
            severities(reportNode)
        );

        reportNodeRepository.save(reportNodeEntity);
        reportNode.getChildren().forEach(child -> saveReportNodeRecursively(reportNodeEntity, child));
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

    @Transactional
    public void deleteReport(UUID reportUuid) {
        ReportNodeEntity reportNodeEntity = reportNodeRepository.findById(reportUuid).orElseThrow(() -> new EmptyResultDataAccessException("No element found", 1));
        deleteRoot(reportNodeEntity.getId());
    }

    @Transactional
    public void deleteReports(List<UUID> reportUuids) {
        Objects.requireNonNull(reportUuids);
        reportUuids.forEach(this::deleteRoot);
    }

    /**
     * delete all the reports depending on a root report
     */
    private void deleteRoot(UUID rootTreeReportId) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());

        reportNodeRepository.findTreeFromRootReport(rootTreeReportId)
            .stream()
            .collect(Collectors.groupingBy(
                result -> (Integer) result[0],
                Collectors.mapping(
                    result -> UUID.fromString((String) result[1]),
                    Collectors.toList()
                )
            ))
            .entrySet()
            .stream()
            .sorted(Map.Entry.<Integer, List<UUID>>comparingByKey().reversed())
            .forEach(entry ->
                Lists.partition(entry.getValue(), SQL_QUERY_MAX_PARAM_NUMBER).forEach(reportNodeRepository::deleteByIdIn)
            );
        LOGGER.info("All the reports of '{}' have been deleted in {}ms", rootTreeReportId, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
    }

    // package private for tests
    void deleteAll() {
        reportNodeRepository.deleteAll();
    }

    private static ReportLog toReportLog(LogProjection entity) {
        return new ReportLog(entity.getMessage(), entity.getSeverities().stream().map(Severity::valueOf).collect(Collectors.toSet()), entity.getParent().getId());
    }
}
