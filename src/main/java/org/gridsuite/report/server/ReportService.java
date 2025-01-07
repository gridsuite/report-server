/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.google.common.collect.Lists;
import com.powsybl.commons.report.ReportNode;
import lombok.NonNull;
import org.gridsuite.report.server.dto.Report;
import org.gridsuite.report.server.dto.ReportLog;
import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.gridsuite.report.server.repositories.ReportNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Service
public class ReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);

    // the maximum number of parameters allowed in an In query. Prevents the number of parameters to reach the maximum allowed (65,535)
    private static final int SQL_QUERY_MAX_PARAM_NUMBER = 10000;

    private final ReportNodeRepository reportNodeRepository;

    public ReportService(ReportNodeRepository reportNodeRepository) {
        this.reportNodeRepository = reportNodeRepository;
    }

    // To use only for tests to fetch an entity with all the relationships
    @Transactional(readOnly = true)
    public Optional<ReportNodeEntity> getReportNodeEntity(UUID id) {
        return Optional.ofNullable(reportNodeRepository.findAllWithChildrenById(id).get(0));
    }

    @Transactional(readOnly = true)
    public Report getReport(UUID reportId) {
        Objects.requireNonNull(reportId);
        return ReportMapper.map(reportNodeRepository.findAllContainersByRootNodeId(reportId));
    }

    public List<ReportLog> getReportLogs(UUID rootReportNodeId, @Nullable Set<String> severityLevelsFilter, @Nullable String messageFilter) {
        return reportNodeRepository.findById(rootReportNodeId)
            .map(entity -> {
                if (severityLevelsFilter == null) {
                    return reportNodeRepository.findAllReportsByRootNodeIdAndOrderAndMessage(
                        Optional.ofNullable(entity.getRootNode()).map(ReportNodeEntity::getId).orElse(entity.getId()),
                        entity.getOrder(),
                        entity.getEndOrder(),
                        messageFilter == null ? "%" : "%" + messageFilter + "%");
                } else {
                    return reportNodeRepository.findAllReportsByRootNodeIdAndOrderAndMessageAndSeverities(
                        Optional.ofNullable(entity.getRootNode()).map(ReportNodeEntity::getId).orElse(entity.getId()),
                        entity.getOrder(),
                        entity.getEndOrder(),
                        messageFilter == null ? "%" : "%" + messageFilter + "%",
                        severityLevelsFilter);
                }
            })
            .map(ReportLogMapper::map)
            .orElse(Collections.emptyList());
    }

    public Set<String> getReportAggregatedSeverities(UUID reportId) {
        return reportNodeRepository.findById(reportId)
            .map(entity -> reportNodeRepository.findDistinctSeveritiesByRootNodeIdAndOrder(
                Optional.ofNullable(entity.getRootNode()).map(ReportNodeEntity::getId).orElse(entity.getId()),
                entity.getOrder(),
                entity.getEndOrder()))
            .orElse(Collections.emptySet());
    }

    public Report getEmptyReport(@NonNull UUID id, @NonNull String defaultName) {
        Report emptyReport = new Report();
        emptyReport.setId(id);
        emptyReport.setMessage(defaultName);
        emptyReport.setSeverity(Severity.UNKNOWN);
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

    private void appendReportElements(ReportNodeEntity reportEntity, ReportNode reportNode) {
        List<SizedReportNode> sizedReportNodeChildren = new ArrayList<>();
        int appendedSize = 0;
        int startingOrder = reportEntity.getEndOrder() + 1;
        for (ReportNode child : reportNode.getChildren()) {
            SizedReportNode sizedReportNode = SizedReportNode.from(child, startingOrder);
            sizedReportNodeChildren.add(sizedReportNode);
            appendedSize += sizedReportNode.getSize();
            startingOrder = sizedReportNode.getOrder() + sizedReportNode.getSize() + 1;
        }
        reportEntity.setEndOrder(reportEntity.getEndOrder() + appendedSize);

        // We don't have to update more ancestors because we only append at root level, and we know it
        // But if we want to generalize appending to any report we should update the severity list of all the ancestors recursively
        String highestSeverity = sizedReportNodeChildren.stream().map(SizedReportNode::getSeverity).reduce((severity, severity2) -> Severity.fromValue(severity).getLevel() > Severity.fromValue(severity2).getLevel() ? severity : severity2).orElse(Severity.UNKNOWN.toString());
        if (Severity.fromValue(highestSeverity).getLevel() > Severity.fromValue(reportEntity.getSeverity()).getLevel()) {
            reportEntity.setSeverity(highestSeverity);
        }
        sizedReportNodeChildren.forEach(c -> saveReportNodeRecursively(reportEntity, reportEntity, c));
    }

    private void createNewReport(UUID id, ReportNode reportNode) {
        SizedReportNode sizedReportNode = SizedReportNode.from(reportNode);
        ReportNodeEntity persistedReport = reportNodeRepository.save(
            new ReportNodeEntity(
                id,
                sizedReportNode.getMessage(),
                sizedReportNode.getOrder(),
                sizedReportNode.getOrder() + sizedReportNode.getSize() - 1,
                sizedReportNode.isLeaf(),
                null,
                null,
                sizedReportNode.getSeverity()
            )
        );
        persistedReport.setRootNode(persistedReport);
        sizedReportNode.getChildren().forEach(c -> saveReportNodeRecursively(persistedReport, persistedReport, c));
    }

    private void saveReportNodeRecursively(ReportNodeEntity rootReportNodeEntity, ReportNodeEntity parentReportNodeEntity, SizedReportNode sizedReportNode) {
        var reportNodeEntity = new ReportNodeEntity(
            sizedReportNode.getMessage(),
            sizedReportNode.getOrder(),
            sizedReportNode.getOrder() + sizedReportNode.getSize() - 1,
            sizedReportNode.isLeaf(),
            rootReportNodeEntity,
            parentReportNodeEntity,
            sizedReportNode.getSeverity()
        );

        reportNodeRepository.save(reportNodeEntity);
        sizedReportNode.getChildren().forEach(child -> saveReportNodeRecursively(rootReportNodeEntity, reportNodeEntity, child));
    }

    @Transactional
    public UUID duplicateReport(UUID rootNodeId) {
        ReportNodeEntity rootNode = reportNodeRepository.findById(rootNodeId)
            .orElseThrow(() -> new NoSuchElementException("Root node not found"));

        ReportNodeEntity duplicatedRootNode = duplicateReportNodeRecursively(rootNode, null);
        return duplicatedRootNode.getId();
    }

    private ReportNodeEntity duplicateReportNodeRecursively(ReportNodeEntity node, ReportNodeEntity newParent) {
        ReportNodeEntity duplicatedNode = new ReportNodeEntity(
            node.getMessage(),
            node.getOrder(),
            node.getEndOrder(),
            node.isLeaf(),
            null,
            newParent,
            node.getSeverity()
        );

        reportNodeRepository.save(duplicatedNode);

        if (newParent == null) {
            duplicatedNode.setRootNode(duplicatedNode);
        } else {
            duplicatedNode.setRootNode(newParent.getRootNode());
        }

        for (ReportNodeEntity child : node.getChildren()) {
            duplicateReportNodeRecursively(child, duplicatedNode);
        }

        return duplicatedNode;
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
}
