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
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.gridsuite.report.server.entities.ValueEntity;
import org.gridsuite.report.server.repositories.ReportNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.gridsuite.report.server.ReportNodeMapper.mapper;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Service
public class ReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);

    private static final long NANOS_FROM_EPOCH_TO_START;

    // the maximum number of parameters allowed in an In query. Prevents the number of parameters to reach the maximum allowed (65,535)
    private static final int SQL_QUERY_MAX_PARAM_NUMBER = 10000;

    public static final String SEVERITY_LIST_KEY = "severityList";

    private final ReportNodeRepository reportNodeRepository;


    public enum ReportNameMatchingType {
        EXACT_MATCHING, ENDS_WITH
    }

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
                reportNodeRepository.findAllWithValuesByIdIn(List.of(id));
                reportNodeRepository.findAllWithSeveritiesByIdIn(List.of(id));
                return reportNodeEntity;
            });
    }

    @Transactional(readOnly = true)
    public ReportNode getReport(UUID reportId, Set<String> severityLevels, String reportNameFilter, ReportNameMatchingType reportNameMatchingType) {
        Objects.requireNonNull(reportId);
        OptimizedReportNodeEntities optimizedReportNodeEntities = getOptimizedReportNodeEntities(reportId);
        return mapper(optimizedReportNodeEntities, severityLevels, reportNameFilter, reportNameMatchingType);
    }

    private OptimizedReportNodeEntities getOptimizedReportNodeEntities(UUID rootReportNodeId) {
        // We first do a recursive find from the root node to aggregate all the IDs of the tree by their tree depth
        Map<Integer, List<UUID>> treeIds = reportNodeRepository.findTreeFromRootReport(rootReportNodeId)
            .stream()
            .collect(Collectors.groupingBy(
                result -> (Integer) result[0],
                Collectors.mapping(
                    result -> UUID.fromString((String) result[1]),
                    Collectors.toList()
                )
            ));

        // Then we flatten the ID list to be able to fetch related data in one request (what if this list is too big ?)
        List<UUID> idList = treeIds.values().stream().flatMap(Collection::stream).toList();

        Map<UUID, ReportNodeEntity> reportNodeEntityById = new HashMap<>();
        Lists.partition(idList, SQL_QUERY_MAX_PARAM_NUMBER).forEach(ids -> {
            // We do these 2 requests to load all data related to ReportNodeEntity thanks to JPA first-level of cache, and we just do a mapping to find fast an entity by its ID
            List<ReportNodeEntity> reportNodeEntities = reportNodeRepository.findAllWithSeveritiesByIdIn(ids);
            reportNodeRepository.findAllWithChildrenByIdIn(ids);
            reportNodeRepository.findAllWithValuesByIdIn(ids);
            reportNodeEntities.forEach(reportNodeEntity -> reportNodeEntityById.put(reportNodeEntity.getId(), reportNodeEntity));
        });

        return new OptimizedReportNodeEntities(treeIds, reportNodeEntityById);
    }

    public static ReportNode getEmptyReport(@NonNull UUID id, @NonNull String defaultName) {
        ReportNode reportNode = ReportNode.newRootReportNode()
            .withMessageTemplate(id.toString(), id.toString())
            .build();

        reportNode.newReportNode()
            .withMessageTemplate(defaultName, defaultName)
            .add();

        return reportNode;
    }

    @Transactional
    public void createReport(UUID id, ReportNode reportNode) {
        Optional<ReportNodeEntity> reportNodeEntity = reportNodeRepository.findById(id);
        if (reportNodeEntity.isPresent()) {
            LOGGER.debug("Reporter {} present, append ", reportNode.getMessage());
            saveAllReportElements(reportNodeEntity.get(), reportNode);
        } else {
            LOGGER.debug("Reporter {} absent, create ", reportNode.getMessage());
            toEntity(id, reportNode);
        }
    }

    private void toEntity(UUID id, ReportNode reportElement) {
        var persistedReport = reportNodeRepository.save(new ReportNodeEntity(id, System.nanoTime() - NANOS_FROM_EPOCH_TO_START));
        saveAllReportElements(persistedReport, reportElement);
    }

    private void saveAllReportElements(ReportNodeEntity parentReportNodeEntity, ReportNode reportNode) {
        traverseReportModel(parentReportNodeEntity, reportNode);
        reportNodeRepository.save(parentReportNodeEntity);
    }

    private void traverseReportModel(ReportNodeEntity parentReportNodeEntity, ReportNode reportNode) {
        var reportNodeEntity = new ReportNodeEntity(
            reportNode.getMessageKey(),
            reportNode.getMessageTemplate(),
            System.nanoTime() - NANOS_FROM_EPOCH_TO_START,
            createValues(reportNode),
            parentReportNodeEntity,
            severities(reportNode)
        );

        reportNodeRepository.save(reportNodeEntity);
        reportNode.getChildren().forEach(child -> traverseReportModel(reportNodeEntity, child));
    }

    private static List<ValueEntity> createValues(ReportNode reportNode) {
        return reportNode.getValues()
            .entrySet()
            .stream()
            .map(k -> new ValueEntity(k.getKey(), k.getValue().getValue(), k.getValue().getType())).toList();
    }

    private static Set<String> severities(ReportNode reportNode) {
        Set<String> severities = new HashSet<>();
        reportNode.getChildren().forEach(child -> Optional.ofNullable(child.getValues().get(ReportConstants.REPORT_SEVERITY_KEY)).ifPresent(s -> severities.add(s.getValue().toString())));
        return severities;
    }

    @Transactional
    public void deleteReport(UUID id, String reportType) {
        Objects.requireNonNull(id);
        ReportNodeEntity reportNodeEntity = reportNodeRepository.findById(id).orElseThrow(() -> new EmptyResultDataAccessException("No element found", 1));
        List<ReportNodeEntity> filteredChildrenList = reportNodeEntity.getChildren()
            .stream()
            .filter(child -> StringUtils.isBlank(reportType) || child.getMessageKey().endsWith(reportType))
            .toList();
        filteredChildrenList.forEach(child -> deleteRoot(child.getId()));

        if (filteredChildrenList.size() == reportNodeEntity.getChildren().size()) {
            // let's remove the whole Report only if we have removed all its treeReport
            reportNodeRepository.deleteByIdIn(List.of(id));
        }
    }

    @Transactional
    public void deleteTreeReports(Map<UUID, String> identifiers) {
        Objects.requireNonNull(identifiers);
        identifiers.forEach(this::deleteTreeReport);
    }

    private void deleteTreeReport(UUID reportId, String reportName) {
        Objects.requireNonNull(reportId);
        List<ReportNodeEntity> reportNodeEntities = reportNodeRepository.findAllByParentIdAndMessageKey(reportId, reportName);
        reportNodeEntities.forEach(reportNodeEntity -> deleteRoot(reportNodeEntity.getId()));
    }

    /**
     * delete all the report and tree report elements depending on a root tree report
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
        LOGGER.info("The report and tree report elements of '{}' has been deleted in {}ms", rootTreeReportId, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
    }

    // package private for tests
    void deleteAll() {
        reportNodeRepository.deleteAll();
    }
}
