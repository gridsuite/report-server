/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.powsybl.commons.report.ReportNode;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.report.server.dto.MatchPosition;
import org.gridsuite.report.server.dto.Report;
import org.gridsuite.report.server.dto.ReportLog;
import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.gridsuite.report.server.entities.ReportProjection;
import org.gridsuite.report.server.repositories.ReportNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Service
public class ReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);

    private static final int MAX_SIZE_INSERT_REPORT_BATCH = 512;

    private final ReportService self;

    private final ReportNodeRepository reportNodeRepository;

    public ReportService(ReportNodeRepository reportNodeRepository, @Lazy ReportService reportService) {
        this.reportNodeRepository = reportNodeRepository;
        this.self = reportService;
    }

    // To use only for tests to fetch an entity with all the relationships
    @Transactional(readOnly = true)
    public List<ReportNodeEntity> getReportNodeEntities(UUID reportId) {
        return reportNodeRepository.findByIdOrderByOrder(reportId);
    }

    @Transactional(readOnly = true)
    public Optional<ReportNodeEntity> getReportNodeEntity(UUID reportId, int order) {
        return reportNodeRepository.findByIdAndOrder(reportId, order);
    }

    @Transactional(readOnly = true)
    public Report getReport(UUID reportId) {
        Objects.requireNonNull(reportId);
        return ReportMapper.map(reportNodeRepository.findAllContainersByReportId(reportId));
    }

    public Page<ReportLog> getReportLogs(UUID rootReportNodeId, @Nullable Set<String> severityLevelsFilter, @Nullable String messageFilter, boolean paged, Pageable pageable) {
        Pageable page = paged ? pageable : Pageable.unpaged();
        String messageSqlPattern = createMessageSqlPattern(messageFilter);
        return reportNodeRepository.findByIdAndOrder(rootReportNodeId, 0)
            .map(entity -> {
                if (severityLevelsFilter == null) {
                    return reportNodeRepository.findPagedReportsByReportIdAndOrderAndMessage(
                        entity.getReportId(),
                        entity.getOrder(),
                        entity.getEndOrder(),
                        messageSqlPattern,
                        page)
                        .map(ReportLogMapper::map);
                } else {
                    return reportNodeRepository.findPagedReportsByReportIdAndOrderAndMessageAndSeverities(
                        entity.getReportId(),
                        entity.getOrder(),
                        entity.getEndOrder(),
                        messageSqlPattern,
                        severityLevelsFilter,
                        page)
                        .map(ReportLogMapper::map);
                }
            })
            .orElse(Page.empty());
    }

    public Page<ReportLog> getMultipleReportsLogsPage(List<UUID> reportIds, @Nullable Set<String> severityLevelsFilter,
            @Nullable String messageFilter, boolean paged, Pageable pageable) {

        Pageable page = paged ? pageable : Pageable.unpaged();
        String messageSqlPattern = createMessageSqlPattern(messageFilter);
        // Convert collection to arrays for PostgreSQL compatibility
        UUID[] reportIdsArray = reportIds.toArray(new UUID[0]);

        Page<Object[]> projections = severityLevelsFilter == null ? reportNodeRepository.findPagedReportsByMultipleReportIdsAndOrderAndMessage(
            reportIdsArray, messageSqlPattern, page) : reportNodeRepository.findPagedReportsByMultipleReportIdsAndOrderAndMessageAndSeverities(
                reportIdsArray, messageSqlPattern, severityLevelsFilter, page);

        // Convert Object[] results back to ReportProjection and then to ReportLog
        List<ReportLog> logs = projections.stream()
            .map(row -> new ReportProjection(UUID.fromString((String) row[0]), (String) row[1], (String) row[2], (Integer) row[3], row[4] != null ? (Integer) row[4] : null))
            .map(ReportLogMapper::map)
            .toList();

        return new PageImpl<>(logs, pageable, projections.getTotalElements());
    }

    public Set<String> getReportAggregatedSeverities(UUID reportId) {
        return reportNodeRepository.findByIdAndOrder(reportId, 0)
            .map(entity -> reportNodeRepository.findDistinctSeveritiesByReportIdAndOrder(
                entity.getReportId(),
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

    public void createReport(UUID id, ReportNode reportNode) {
        reportNodeRepository.findByIdAndOrder(id, 0).ifPresentOrElse(
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

    @Transactional
    public void createOrReplaceReport(UUID id, ReportNode reportNode) {
        reportNodeRepository.findByIdAndOrder(id, 0).ifPresentOrElse(
                reportEntity -> {
                    LOGGER.debug("Reporter {} present, replacing children", reportNode.getMessage());
                    replaceReportChildren(reportEntity, reportNode);
                },
                () -> {
                    LOGGER.debug("Reporter {} absent, create", reportNode.getMessage());
                    createNewReport(id, reportNode);
                }
        );
    }

    /**
     * Replaces all children of an existing report while keeping the root entity.
     * This avoids Hibernate session conflicts when recreating reports with the same ID.
     */
    private void replaceReportChildren(ReportNodeEntity rootEntity, ReportNode newReportNode) {
        // Delete only the children, not the root
        reportNodeRepository.deleteChildrenByReportId(rootEntity.getReportId());

        // Update root entity properties
        SizedReportNode sizedReportNode = SizedReportNode.from(newReportNode);
        rootEntity.setMessage(sizedReportNode.getMessage());
        rootEntity.setSeverity(sizedReportNode.getSeverity());
        rootEntity.setOrder(sizedReportNode.getOrder());
        rootEntity.setEndOrder(sizedReportNode.getOrder() + sizedReportNode.getSize() - 1);
        rootEntity.setLeaf(sizedReportNode.isLeaf());

        // Save updated root
        reportNodeRepository.save(rootEntity);

        // Add new children
        List<ReportNodeEntity> entitiesToSave = new ArrayList<>(MAX_SIZE_INSERT_REPORT_BATCH);
        sizedReportNode.getChildren().forEach(child ->
                saveReportNodeRecursively(rootEntity.getReportId(), rootEntity.getOrder(), child, entitiesToSave)
        );

        if (!entitiesToSave.isEmpty()) {
            self.saveBatchedReports(entitiesToSave);
        }
    }

    private void appendReportElements(ReportNodeEntity reportEntity, ReportNode reportNode) {
        List<SizedReportNode> sizedReportNodeChildren = new ArrayList<>(reportNode.getChildren().size());
        int appendedSize = 0;
        int startingOrder = reportEntity.getEndOrder() + 1;
        int depth = reportEntity.getDepth() + 1;
        for (ReportNode child : reportNode.getChildren()) {
            SizedReportNode sizedReportNode = SizedReportNode.from(child, startingOrder, depth);
            sizedReportNodeChildren.add(sizedReportNode);
            appendedSize += sizedReportNode.getSize();
            startingOrder = sizedReportNode.getOrder() + sizedReportNode.getSize();
        }
        reportEntity.setEndOrder(reportEntity.getEndOrder() + appendedSize);

        // We don't have to update more ancestors because we only append at root level, and we know it
        // But if we want to generalize appending to any report we should update the severity list of all the ancestors recursively
        String highestSeverity = sizedReportNodeChildren.stream().map(SizedReportNode::getSeverity).reduce((severity, severity2) -> Severity.fromValue(severity).getLevel() > Severity.fromValue(severity2).getLevel() ? severity : severity2).orElse(Severity.UNKNOWN.toString());
        if (Severity.fromValue(highestSeverity).getLevel() > Severity.fromValue(reportEntity.getSeverity()).getLevel()) {
            reportEntity.setSeverity(highestSeverity);
        }
        List<ReportNodeEntity> entitiesToSave = new ArrayList<>(MAX_SIZE_INSERT_REPORT_BATCH);
        entitiesToSave.add(reportEntity);
        sizedReportNodeChildren.forEach(c -> saveReportNodeRecursively(reportEntity.getReportId(), reportEntity.getOrder(), c, entitiesToSave));

        if (!entitiesToSave.isEmpty()) {
            self.saveBatchedReports(entitiesToSave);
        }
    }

    private void createNewReport(UUID id, ReportNode reportNode) {
        SizedReportNode sizedReportNode = SizedReportNode.from(reportNode);
        List<ReportNodeEntity> entitiesToSave = new ArrayList<>(MAX_SIZE_INSERT_REPORT_BATCH);
        ReportNodeEntity persistedReport = ReportNodeEntity.builder()
            .id(id)
            .message(sizedReportNode.getMessage())
            .order(sizedReportNode.getOrder())
            .endOrder(sizedReportNode.getOrder() + sizedReportNode.getSize() - 1)
            .isLeaf(sizedReportNode.isLeaf())
            .severity(sizedReportNode.getSeverity())
            .depth(sizedReportNode.getDepth())
            .build();

        entitiesToSave.add(persistedReport);
        sizedReportNode.getChildren().forEach(c ->
            saveReportNodeRecursively(id, persistedReport.getOrder(), c, entitiesToSave)
        );

        if (!entitiesToSave.isEmpty()) {
            self.saveBatchedReports(entitiesToSave);
        }
    }

    protected void saveReportNodeRecursively(
        UUID reportId,
        int parentOrder,
        SizedReportNode sizedReportNode,
        List<ReportNodeEntity> entitiesToSave
    ) {
        var reportNodeEntity = ReportNodeEntity.builder()
            .id(reportId)
            .message(sizedReportNode.getMessage())
            .order(sizedReportNode.getOrder())
            .endOrder(sizedReportNode.getOrder() + sizedReportNode.getSize() - 1)
            .isLeaf(sizedReportNode.isLeaf())
            .parentOrder(parentOrder)
            .severity(sizedReportNode.getSeverity())
            .depth(sizedReportNode.getDepth())
            .build();

        entitiesToSave.add(reportNodeEntity);
        if (entitiesToSave.size() % MAX_SIZE_INSERT_REPORT_BATCH == 0) {
            self.saveBatchedReports(entitiesToSave);
        }
        sizedReportNode.getChildren().forEach(child -> saveReportNodeRecursively(reportId, reportNodeEntity.getOrder(), child, entitiesToSave));

    }

    @Transactional
    public void saveBatchedReports(List<ReportNodeEntity> batch) {
        reportNodeRepository.saveAllAndFlush(batch);
        batch.clear();
    }

    @Transactional
    public UUID duplicateReport(UUID reportId) {
        List<ReportProjection> sourceNodes = reportNodeRepository.findAllNodeDataByReportId(reportId);
        if (sourceNodes.isEmpty()) {
            throw new NoSuchElementException("Root node not found");
        }

        UUID newRootId = UUID.randomUUID();
        List<ReportNodeEntity> batch = new ArrayList<>(MAX_SIZE_INSERT_REPORT_BATCH);

        for (ReportProjection source : sourceNodes) {
            ReportNodeEntity duplicate = ReportNodeEntity.builder()
                .id(newRootId)
                .message(source.message())
                .order(source.order())
                .endOrder(source.endOrder())
                .isLeaf(source.isLeaf())
                .severity(source.severity())
                .depth(source.depth())
                .parentOrder(source.parentOrder())
                .build();

            batch.add(duplicate);
            if (batch.size() % MAX_SIZE_INSERT_REPORT_BATCH == 0) {
                self.saveBatchedReports(batch);
            }
        }
        if (!batch.isEmpty()) {
            self.saveBatchedReports(batch);
        }

        return newRootId;
    }

    @Transactional
    public void deleteReport(UUID reportUuid) {
        reportNodeRepository.findByIdAndOrder(reportUuid, 0).orElseThrow(() -> new EmptyResultDataAccessException("No element found", 1));
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        reportNodeRepository.deleteAllByReportId(reportUuid);
        LOGGER.debug("All the reports of '{}' have been deleted in {}ms", reportUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
    }

    @Transactional
    public void deleteReports(List<UUID> reportUuids) {
        Objects.requireNonNull(reportUuids);
        reportUuids.forEach(reportNodeRepository::deleteAllByReportId);
    }

    // package private for tests
    void deleteAll() {
        reportNodeRepository.deleteAll();
    }

    /**
     * Searches for term matches in filtered log messages and returns their positions
     */
    public List<MatchPosition> searchTermMatchesInFilteredLogs(
        UUID rootReportNodeId,
        @Nullable Set<String> severityLevelsFilter,
        @Nullable String messageFilter,
        @NonNull String searchTerm,
        int pageSize
    ) {
        String messageSqlPattern = createMessageSqlPattern(messageFilter);
        String searchPattern = createMessageSqlPattern(searchTerm);

        List<Integer> positions = reportNodeRepository.findByIdAndOrder(rootReportNodeId, 0)
            .map(entity -> {
                UUID rootId = entity.getReportId();

                return severityLevelsFilter == null ?
                    reportNodeRepository.findRelativePositionsByReportIdAndOrderAndMessage(
                        rootId, entity.getOrder(), entity.getEndOrder(), messageSqlPattern, searchPattern) :
                    reportNodeRepository.findRelativePositionsByReportIdAndOrderAndMessageAndSeverities(
                        rootId, entity.getOrder(), entity.getEndOrder(), messageSqlPattern, searchPattern, severityLevelsFilter);
            })
            .orElse(Collections.emptyList());

        return positions.stream()
            .map(position -> new MatchPosition(position / pageSize, position % pageSize))
            .toList();
    }

    /**
     * Searches for term matches in filtered log messages across multiple reports and returns their positions
     */
    public List<MatchPosition> searchTermMatchesInMultipleReportsFilteredLogs(
        List<UUID> reportIds,
        @Nullable Set<String> severityLevelsFilter,
        @Nullable String messageFilter,
        @NonNull String searchTerm,
        int pageSize
    ) {
        String messageSqlPattern = createMessageSqlPattern(messageFilter);
        String searchPattern = createMessageSqlPattern(searchTerm);

        // Convert collections to arrays for PostgreSQL compatibility
        UUID[] reportIdsArray = reportIds.toArray(new UUID[0]);

        List<Integer> positions = severityLevelsFilter == null ?
            reportNodeRepository.findRelativePositionsByMultipleReportIdsAndOrderAndMessage(
                reportIdsArray, messageSqlPattern, searchPattern) :
            reportNodeRepository.findRelativePositionsByMultipleReportIdsAndOrderAndMessageAndSeverities(
                reportIdsArray, messageSqlPattern, searchPattern, severityLevelsFilter);

        return positions.stream()
            .map(position -> new MatchPosition(position / pageSize, position % pageSize))
            .toList();
    }

    private String createMessageSqlPattern(@Nullable String filter) {
        // The '_' and '%' characters have special meaning in the sql LIKE pattern condition
        // So, in order to filter logs containing these characters, we must escape them, using the backslash character,
        // which is then also given as the ESCAPE character in the LIKE condition of the sql request (see ReportNodeRepository.java)
        return filter == null ? "%" :
            "%" + StringUtils.replaceEach(filter, new String[]{"_", "%"}, new String[]{"\\_", "\\%"}) + "%";
    }
}
