/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import com.powsybl.commons.report.ReportNode;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.report.server.dto.MatchPosition;
import org.gridsuite.report.server.dto.Report;
import org.gridsuite.report.server.dto.ReportLog;
import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.gridsuite.report.server.entities.ReportProjection;
import org.gridsuite.report.server.repositories.ReportNodeRepository;
import org.gridsuite.report.server.utils.UuidUtil;
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
import jakarta.persistence.EntityNotFoundException;
import java.util.*;

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

    // To use only for tests to fetch an entity with its direct children
    @Transactional(readOnly = true)
    public Optional<ReportNodeEntity> getReportNodeEntity(UUID id) {
        return reportNodeRepository.findById(id).map(entity -> {
            entity.setChildren(reportNodeRepository.findByParentIdOrderByOrderAsc(id));
            return entity;
        });
    }

    @Transactional(readOnly = true)
    public Report getReport(UUID reportId) {
        Objects.requireNonNull(reportId);
        return ReportMapper.map(reportNodeRepository.findAllContainersByRootNodeId(reportId));
    }

    public Page<ReportLog> getReportLogs(UUID rootReportNodeId, @Nullable Set<String> severityLevelsFilter, @Nullable String messageFilter, boolean paged, Pageable pageable) {
        Pageable page = paged ? pageable : Pageable.unpaged();
        String messageSqlPattern = createMessageSqlPattern(messageFilter);
        return reportNodeRepository.findById(rootReportNodeId)
            .map(entity -> {
                if (severityLevelsFilter == null) {
                    return reportNodeRepository.findPagedReportsByRootNodeIdAndOrderAndMessage(
                        entity.getRootNodeId(),
                        entity.getOrder(),
                        entity.getEndOrder(),
                        messageSqlPattern,
                        page)
                        .map(ReportLogMapper::map);
                } else {
                    return reportNodeRepository.findPagedReportsByRootNodeIdAndOrderAndMessageAndSeverities(
                        entity.getRootNodeId(),
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

        Page<Object[]> projections = severityLevelsFilter == null ? reportNodeRepository.findPagedReportsByMultipleRootNodeIdsAndOrderAndMessage(
            reportIdsArray, messageSqlPattern, page) : reportNodeRepository.findPagedReportsByMultipleRootNodeIdsAndOrderAndMessageAndSeverities(
                reportIdsArray, messageSqlPattern, severityLevelsFilter, page);

        // Convert Object[] results back to ReportProjection and then to ReportLog
        List<ReportLog> logs = projections.stream()
            .map(row -> new ReportProjection(UUID.fromString((String) row[0]), (String) row[1], (String) row[2], (Integer) row[3], row[4] != null ? UUID.fromString((String) row[4]) : null))
            .map(ReportLogMapper::map)
            .toList();

        return new PageImpl<>(logs, pageable, projections.getTotalElements());
    }

    public Set<String> getReportAggregatedSeverities(UUID reportId) {
        return reportNodeRepository.findById(reportId)
            .map(entity -> reportNodeRepository.findDistinctSeveritiesByRootNodeIdAndOrder(
                entity.getRootNodeId(),
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

    /**
     * Creates a new child report under an existing root report.
     * The child identifier is generated server-side and returned to the caller.
     */
    @Transactional
    public UUID createChildReport(UUID rootId, ReportNode reportNode) {
        ReportNodeEntity rootReportEntity = reportNodeRepository.findById(rootId)
            .orElseThrow(() -> new EntityNotFoundException("Root report " + rootId + " not found"));

        if (!isRootReport(rootReportEntity)) {
            throw new IllegalStateException("Report id " + rootId + " is not a root report");
        }

        return appendChildReportElements(rootReportEntity, reportNode);
    }

    private static boolean isRootReport(ReportNodeEntity reportNodeEntity) {
        UUID id = reportNodeEntity.getId();
        return id != null && id.equals(reportNodeEntity.getRootNodeId());
    }

    @Transactional
    public void createOrReplaceReport(UUID id, ReportNode reportNode) {
        reportNodeRepository.findById(id).ifPresentOrElse(
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
        if (!isRootReport(rootEntity)) {
            throw new IllegalStateException("Report id " + rootEntity.getId() + " is not a root report");
        }
        // Delete only the children, not the root itself
        reportNodeRepository.deleteAllChildrenByRootNodeId(rootEntity.getId());

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
        TimeBasedEpochGenerator uuidGenerator = UuidUtil.newV7Generator();
        sizedReportNode.getChildren().forEach(child ->
                saveReportNodeRecursively(uuidGenerator, rootEntity.getId(), rootEntity.getId(), child, entitiesToSave)
        );

        if (!entitiesToSave.isEmpty()) {
            self.saveBatchedReports(entitiesToSave);
        }
    }

    private void appendReportElements(ReportNodeEntity reportEntity, ReportNode reportNode) {
        List<SizedReportNode> sizedReportNodeChildren = new ArrayList<>(reportNode.getChildren().size());
        int newEndOrder = reportEntity.getEndOrder();
        int depth = reportEntity.getDepth() + 1;
        for (ReportNode child : reportNode.getChildren()) {
            SizedReportNode sizedReportNode = SizedReportNode.from(child, newEndOrder + 1, depth);
            sizedReportNodeChildren.add(sizedReportNode);
            newEndOrder += sizedReportNode.getSize();
        }
        // compute endOrder from the actual last order position of the last child subtree
        reportEntity.setEndOrder(newEndOrder);
        updateParentSeverity(reportEntity, sizedReportNodeChildren);
        List<ReportNodeEntity> entitiesToSave = new ArrayList<>(MAX_SIZE_INSERT_REPORT_BATCH);
        entitiesToSave.add(reportEntity);
        TimeBasedEpochGenerator uuidGenerator = UuidUtil.newV7Generator();
        sizedReportNodeChildren.forEach(c -> saveReportNodeRecursively(uuidGenerator, reportEntity.getRootNodeId(), reportEntity.getId(), c, entitiesToSave));

        if (!entitiesToSave.isEmpty()) {
            self.saveBatchedReports(entitiesToSave);
        }
    }

    /**
     * Appends a report node as a new child entity under the given root, updating order bounds and severity.
     * Returns the identifier of the new child report.
     */
    private UUID appendChildReportElements(ReportNodeEntity rootReportEntity, ReportNode reportNode) {
        int startingOrder = rootReportEntity.getEndOrder() + 1;
        int depth = rootReportEntity.getDepth() + 1;
        SizedReportNode sizedChildReportNode = SizedReportNode.from(reportNode, startingOrder, depth);

        rootReportEntity.setEndOrder(rootReportEntity.getEndOrder() + sizedChildReportNode.getSize());
        rootReportEntity.setLeaf(false);
        updateParentSeverity(rootReportEntity, List.of(sizedChildReportNode));

        List<ReportNodeEntity> entitiesToSave = new ArrayList<>(MAX_SIZE_INSERT_REPORT_BATCH);
        entitiesToSave.add(rootReportEntity);

        TimeBasedEpochGenerator uuidGenerator = UuidUtil.newV7Generator();
        ReportNodeEntity childReportEntity = ReportNodeEntity.builder()
            .id(uuidGenerator.generate())
            .message(sizedChildReportNode.getMessage())
            .order(sizedChildReportNode.getOrder())
            .endOrder(sizedChildReportNode.getOrder() + sizedChildReportNode.getSize() - 1)
            .isLeaf(sizedChildReportNode.isLeaf())
            .rootNodeId(rootReportEntity.getId())
            .parentId(rootReportEntity.getId())
            .severity(sizedChildReportNode.getSeverity())
            .depth(sizedChildReportNode.getDepth())
            .build();
        entitiesToSave.add(childReportEntity);
        sizedChildReportNode.getChildren().forEach(child ->
            saveReportNodeRecursively(uuidGenerator, rootReportEntity.getId(), childReportEntity.getId(), child, entitiesToSave));

        if (!entitiesToSave.isEmpty()) {
            self.saveBatchedReports(entitiesToSave);
        }
        return childReportEntity.getId();
    }

    // We don't have to update more ancestors because we only append at root level.
    // If appending were generalized to deeper levels we would update severities recursively.
    private static void updateParentSeverity(ReportNodeEntity reportEntity, List<SizedReportNode> children) {
        String highestSeverity = children.stream()
            .map(SizedReportNode::getSeverity)
            .reduce((severity, severity2) -> Severity.fromValue(severity).getLevel() > Severity.fromValue(severity2).getLevel() ? severity : severity2)
            .orElse(Severity.UNKNOWN.toString());
        if (Severity.fromValue(highestSeverity).getLevel() > Severity.fromValue(reportEntity.getSeverity()).getLevel()) {
            reportEntity.setSeverity(highestSeverity);
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
            .rootNodeId(id)
            .build();

        entitiesToSave.add(persistedReport);
        TimeBasedEpochGenerator uuidGenerator = UuidUtil.newV7Generator();
        sizedReportNode.getChildren().forEach(c ->
            saveReportNodeRecursively(uuidGenerator, id, id, c, entitiesToSave)
        );

        if (!entitiesToSave.isEmpty()) {
            self.saveBatchedReports(entitiesToSave);
        }
    }

    protected void saveReportNodeRecursively(
        TimeBasedEpochGenerator uuidGenerator,
        UUID rootNodeId,
        UUID parentId,
        SizedReportNode sizedReportNode,
        List<ReportNodeEntity> entitiesToSave
    ) {
        var reportNodeEntity = ReportNodeEntity.builder()
            .id(uuidGenerator.generate())
            .message(sizedReportNode.getMessage())
            .order(sizedReportNode.getOrder())
            .endOrder(sizedReportNode.getOrder() + sizedReportNode.getSize() - 1)
            .isLeaf(sizedReportNode.isLeaf())
            .rootNodeId(rootNodeId)
            .parentId(parentId)
            .severity(sizedReportNode.getSeverity())
            .depth(sizedReportNode.getDepth())
            .build();

        entitiesToSave.add(reportNodeEntity);
        if (entitiesToSave.size() % MAX_SIZE_INSERT_REPORT_BATCH == 0) {
            self.saveBatchedReports(entitiesToSave);
        }
        sizedReportNode.getChildren().forEach(child -> saveReportNodeRecursively(uuidGenerator, rootNodeId, reportNodeEntity.getId(), child, entitiesToSave));

    }

    @Transactional
    public void saveBatchedReports(List<ReportNodeEntity> batch) {
        reportNodeRepository.saveAllAndFlush(batch);
        batch.clear();
    }

    @Transactional
    public UUID duplicateReport(UUID rootNodeId) {
        List<ReportProjection> sourceNodes = reportNodeRepository.findAllNodeDataByRootNodeId(rootNodeId);
        if (sourceNodes.isEmpty()) {
            throw new NoSuchElementException("Root node not found");
        }

        // Map old UUIDs to new UUIDs (ordered by depth, so parents are processed before children)
        Map<UUID, UUID> oldToNewId = new HashMap<>();
        List<ReportNodeEntity> batch = new ArrayList<>(MAX_SIZE_INSERT_REPORT_BATCH);
        // UUID v4 is intentionally used for the root node,
        // to avoid having two different UUID versions for root reports in the database which would be confusing and surprising
        // root report IDs are managed by study server which generates UUID v4 when creating root reports.
        // we need to switch to UUID v7 here if the study server generates UUID v7 for root report IDs.
        UUID newRootId = UUID.randomUUID();
        TimeBasedEpochGenerator uuidGenerator = UuidUtil.newV7Generator();

        for (ReportProjection source : sourceNodes) {
            boolean isRoot = source.id().equals(rootNodeId);
            UUID newId = isRoot ? newRootId : uuidGenerator.generate();
            oldToNewId.put(source.id(), newId);
            UUID newParentId = source.parentId() != null ? oldToNewId.get(source.parentId()) : null;

            batch.add(ReportNodeEntity.builder()
                .id(newId)
                .message(source.message())
                .order(source.order())
                .endOrder(source.endOrder())
                .isLeaf(source.isLeaf())
                .severity(source.severity())
                .depth(source.depth())
                .rootNodeId(newRootId)
                .parentId(newParentId)
                .build());

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
        if (reportNodeRepository.deleteAllByRootNodeId(reportUuid) == 0) {
            throw new EmptyResultDataAccessException("No element found", 1);
        }
    }

    @Transactional
    public void deleteReports(List<UUID> reportUuids) {
        Objects.requireNonNull(reportUuids);
        reportUuids.forEach(reportNodeRepository::deleteAllByRootNodeId);
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

        List<Integer> positions = reportNodeRepository.findById(rootReportNodeId)
            .map(entity -> {
                UUID rootId = entity.getRootNodeId();

                return severityLevelsFilter == null ?
                    reportNodeRepository.findRelativePositionsByRootNodeIdAndOrderAndMessage(
                        rootId, entity.getOrder(), entity.getEndOrder(), messageSqlPattern, searchPattern) :
                    reportNodeRepository.findRelativePositionsByRootNodeIdAndOrderAndMessageAndSeverities(
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
            reportNodeRepository.findRelativePositionsByMultipleRootNodeIdsAndOrderAndMessage(
                reportIdsArray, messageSqlPattern, searchPattern) :
            reportNodeRepository.findRelativePositionsByMultipleRootNodeIdsAndOrderAndMessageAndSeverities(
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
