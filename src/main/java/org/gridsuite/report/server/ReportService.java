/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.google.common.collect.Lists;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeAdder;
import com.powsybl.commons.report.ReportNodeAdderOrBuilder;
import com.powsybl.commons.report.TypedValue;
import jakarta.persistence.EntityNotFoundException;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.report.server.entities.*;
import org.gridsuite.report.server.repositories.ReportElementRepository;
import org.gridsuite.report.server.repositories.ReportNodeRepository;
import org.gridsuite.report.server.repositories.ReportRepository;
import org.gridsuite.report.server.repositories.TreeReportRepository;
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

    public enum ReportNameMatchingType {
        EXACT_MATCHING, ENDS_WITH
    }

    static {
        long nanoNow = System.nanoTime();
        long nanoViaMillis = Instant.now().toEpochMilli() * 1000000;
        NANOS_FROM_EPOCH_TO_START = nanoNow - nanoViaMillis;
    }

    private final ReportRepository reportRepository;
    private final TreeReportRepository treeReportRepository;
    private final ReportElementRepository reportElementRepository;

    public ReportService(final ReportRepository reportRepository, TreeReportRepository treeReportRepository, ReportElementRepository reportElementRepository, ReportNodeRepository reportNodeRepository) {
        this.reportRepository = reportRepository;
        this.treeReportRepository = treeReportRepository;
        this.reportElementRepository = reportElementRepository;
        this.reportNodeRepository = reportNodeRepository;
    }

    private static void addTypedValue(ReportValueEmbeddable value, ReportNodeAdderOrBuilder<ReportNodeAdder> adder) {
        switch (value.getValueType()) {
            case DOUBLE:
                adder.withTypedValue(value.getName(), Double.valueOf(value.getValue()), value.getType());
                break;
            case INTEGER:
                adder.withTypedValue(value.getName(), Integer.valueOf(value.getValue()), value.getType());
                break;
            default:
                adder.withTypedValue(value.getName(), value.getValue(), value.getType());
        }
    }

    @Transactional(readOnly = true)
    public ReportNode getReport(UUID reportId, boolean withElements, Set<String> severityLevels, String reportNameFilter, ReportNameMatchingType reportNameMatchingType) {
        Objects.requireNonNull(reportId);
        ReportEntity reportEntity = reportRepository.findById(reportId).orElseThrow(EntityNotFoundException::new);

        var rootReportNode = ReportNode.newRootReportNode()
            .withMessageTemplate(reportId.toString(), reportId.toString())
            .build();

        List<TreeReportEntity> treeReportEntities = treeReportRepository.findAllByReportIdOrderByNanos(reportEntity.getId())
            .stream()
            .filter(tre -> StringUtils.isBlank(reportNameFilter)
                || tre.getName().startsWith("Root") // FIXME remove this hack when "Root" report will follow the same rules than computations and modifications
                || reportNameMatchingType == ReportNameMatchingType.EXACT_MATCHING && tre.getName().equals(reportNameFilter)
                || reportNameMatchingType == ReportNameMatchingType.ENDS_WITH && tre.getName().endsWith(reportNameFilter)).toList();

        treeReportEntities.forEach(treeReportEntity -> addSubReportNode(rootReportNode, treeReportEntity, withElements, severityLevels));

        return rootReportNode;
    }

    @Transactional(readOnly = true)
    public ReportNode getSubReport(UUID reporterId, Set<String> severityLevels) {
        Objects.requireNonNull(reporterId);
        TreeReportEntity treeReportEntity = treeReportRepository.findById(reporterId).orElseThrow(EntityNotFoundException::new);

        ReportNode report = ReportNode.newRootReportNode()
            .withMessageTemplate(treeReportEntity.getIdNode().toString(), treeReportEntity.getIdNode().toString())
            .build();

        addSubReportNode(report, treeReportEntity, true, severityLevels);

        return report;
    }

    private ReportNode addSubReportNode(ReportNode rootReportNdoe, TreeReportEntity subTreeReport, boolean withElements, Set<String> severityLevels) {
        // Let's find all the treeReportEntities ids that inherit from the parent treeReportEntity
        final List<UUID> treeReportEntitiesIds = treeReportRepository.findAllTreeReportIdsRecursivelyByParentTreeReport(subTreeReport.getIdNode())
            .stream()
            .map(UUID::fromString)
            .toList();

        List<ReportElementEntity> allReportElements = null;
        if (withElements) {
            // Let's find all the reportElements that are linked to the found treeReports
            allReportElements = reportElementRepository.findAllByParentReportIdNodeInOrderByNanos(treeReportEntitiesIds)
                .stream()
                .filter(reportElementEntity -> reportElementEntity.hasSeverity(severityLevels) || reportElementEntity.getValues().isEmpty()) // reportElementEntity.getValues().isEmpty() is a hack to get the empty subreports
                .toList();
        }

        // We need to get the entities to have access to the dictionaries
        List<TreeReportEntity> treeReportEntities = treeReportRepository.findAllByIdNodeInOrderByNanos(treeReportEntitiesIds);

        // Now we can rebuild the tree
        addSubReportNode(rootReportNdoe, subTreeReport, treeReportEntities, allReportElements);
        return rootReportNdoe;
    }

    private void addSubReportNode(final ReportNode rootReportNode, final TreeReportEntity rootTreeReportEntity, final List<TreeReportEntity> allTreeReports,
                                  @Nullable final List<ReportElementEntity> allReportElements) {
        // We convert our entities to PowSyBl Reporter
        Map<UUID, Map<String, String>> treeReportEntityDictionaries = new HashMap<>(allTreeReports.size());

        Map<UUID, List<TreeReportEntity>> reportNodeIdToChildTreeReports = new HashMap<>(allTreeReports.size());
        Map<UUID, ReportNode> treeReportIdToReportNodes = new HashMap<>(allTreeReports.size());

        for (final TreeReportEntity treeReportEntity : allTreeReports) {
            TreeReportEntity parentTreeReport = treeReportEntity.getParentReport();
            if (parentTreeReport != null) {
                UUID parentId = parentTreeReport.getIdNode();
                if (!reportNodeIdToChildTreeReports.containsKey(parentId)) {
                    reportNodeIdToChildTreeReports.put(parentId, new ArrayList<>());
                }
                reportNodeIdToChildTreeReports.get(parentId).add(treeReportEntity);
            }
            treeReportEntityDictionaries.put(treeReportEntity.getIdNode(), treeReportEntity.getDictionary());
        }

        final Map<String, String> rootDictionnary = rootTreeReportEntity.getDictionary();
        rootTreeReportEntity.getValues().add(new ReportValueEmbeddable("id", rootTreeReportEntity.getIdNode(), "ID"));
        ReportNodeAdder reportNodeAdder = rootReportNode.newReportNode()
            .withMessageTemplate(rootTreeReportEntity.getName(), rootDictionnary.get(rootTreeReportEntity.getName()));
        rootTreeReportEntity.getValues().forEach(value -> addTypedValue(value, reportNodeAdder));
        ReportNode subReportNode = reportNodeAdder.add();
        treeReportIdToReportNodes.put(rootTreeReportEntity.getIdNode(), subReportNode);

        addChildNodes(subReportNode, rootTreeReportEntity.getIdNode(), reportNodeIdToChildTreeReports, treeReportIdToReportNodes);

        if (allReportElements != null) {
            for (final ReportElementEntity entity : allReportElements) {
                final Map<String, String> dict = treeReportEntityDictionaries.get(entity.getParentReport().getIdNode());
                ReportNodeAdder reportElementAdder = treeReportIdToReportNodes.get(entity.getParentReport().getIdNode()).newReportNode().withMessageTemplate(entity.getName(), dict.get(entity.getName()));
                entity.getValues().forEach(value -> addTypedValue(value, reportElementAdder));
                // reports without values are considered as subreports
                if (entity.getValues().isEmpty()) {
                    reportElementAdder.withUntypedValue("id", entity.getIdReport().toString());
                }
                reportElementAdder.add();
            }
        }
    }

    private static void addChildNodes(ReportNode parentNode, UUID reportNodeId, Map<UUID, List<TreeReportEntity>> reportNodeIdToChildren, Map<UUID, ReportNode> treeReportIdToReportNodes) {
        List<TreeReportEntity> children = reportNodeIdToChildren.get(reportNodeId);
        if (children == null) {
            return;
        }
        children.forEach(childEntity -> {
            ReportNodeAdder adder = parentNode.newReportNode().withMessageTemplate(childEntity.getName(), childEntity.getDictionary().get(childEntity.getName()));
            childEntity.getValues().forEach(value -> addTypedValue(value, adder));
            adder.withUntypedValue("id", childEntity.getIdNode().toString());
            ReportNode childReportNode = adder.add();
            treeReportIdToReportNodes.put(childEntity.getIdNode(), childReportNode);
            if (reportNodeIdToChildren.containsKey(childEntity.getIdNode())) {
                addChildNodes(childReportNode, childEntity.getIdNode(), reportNodeIdToChildren, treeReportIdToReportNodes);
            }
        });
    }

    public ReportNode getEmptyReport(@NonNull UUID id, @NonNull String defaultName) {
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withMessageTemplate(id.toString(), id.toString())
                .build();

        reportNode.newReportNode()
                .withMessageTemplate(defaultName, defaultName)
                .add();

        return reportNode;
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
        var messageTemplateEntity = new MessageTemplateEntity(reportNode.getMessageKey(), reportNode.getMessageTemplate());
        var reportNodeEntity = new ReportNodeEntity(
            System.nanoTime() - NANOS_FROM_EPOCH_TO_START,
            messageTemplateEntity,
            createValues(reportNode),
            parentReportNodeEntity
        );

        reportNodeRepository.save(reportNodeEntity);
        reportNode.getChildren().forEach(child -> traverseReportModel(reportNodeEntity, child));
    }

    private static List<ValueEntity> createValues(ReportNode reportNode) {
        var values = new ArrayList<>(reportNode.getValues()
            .entrySet()
            .stream()
            .map(k -> new ValueEntity(k.getKey(), k.getValue().getValue().toString(), k.getValue().getType())).toList());
        values.add(new ValueEntity("severityList", severityList(reportNode).toString(), TypedValue.SEVERITY));
        return values;
    }

    private static List<String> severityList(ReportNode reportNode) {
        return reportNode.getChildren()
            .stream()
            .filter(report -> report.getChildren().isEmpty() && !report.getValues().isEmpty()) // reports without values are considered as subreports so we don't want them in the severity list
            .map(report -> report.getValues().get("reportSeverity"))
            .map(severity -> severity == null ? ReportSeverity.UNKNOWN.toString() : ReportSeverity.valueOf(Objects.toString(severity.getValue())).toString())
            .distinct().toList();
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

    /**
     * delete all the report and tree report elements depending on a root tree report
     */
    private void deleteRoot(UUID rootTreeReportId) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        /*
         * Groups tree report node IDs by level for batch deletion.
         * This is necessary otherwise H2 throws JdbcSQLIntegrityConstraintViolationException when issuing the delete query with 'where id in (x1,x2,...)' (we use h2 for unit tests).
         * For postgres, this is not necessary if all the ids are in the same delete query, but would be a problem
         * if we decided to partition the deletes in smaller batches in multiple transactions (in multiple deletes in one transaction we could defer the checks at the commit with 'SET CONSTRAINTS DEFERRED')
         */
        Map<Integer, List<UUID>> treeReportIdsByLevel = treeReportRepository.getSubReportsNodesWithLevel(rootTreeReportId)
            .stream()
            .collect(Collectors.groupingBy(
                result -> (Integer) result[1],
                Collectors.mapping(
                    result -> UUID.fromString((String) result[0]),
                    Collectors.toList()
                )
            ));

        // Deleting the report elements in subsets because they can exceed the limit of 64k elements in the IN clause
        List<UUID> groupedTreeReportIds = treeReportIdsByLevel.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        List<UUID> reportElementIds = reportElementRepository.findIdReportByParentReportIdNodeIn(groupedTreeReportIds)
            .stream()
            .map(ReportElementEntity.ProjectionIdReport::getIdReport)
            .toList();
        Lists.partition(reportElementIds, SQL_QUERY_MAX_PARAM_NUMBER)
            .forEach(ids -> {
                reportElementRepository.deleteAllReportElementValuesByIdReportIn(ids);
                reportElementRepository.deleteAllByIdReportIn(ids);
            });

        // Delete all the report elements values and dictionaries since doesn't have any parent-child relationship
        treeReportRepository.deleteAllTreeReportValuesByIdNodeIn(groupedTreeReportIds);
        treeReportRepository.deleteAllTreeReportDictionaryByIdNodeIn(groupedTreeReportIds);

        // Deleting the tree reports level by level, starting from the highest level
        treeReportIdsByLevel.entrySet().stream()
            .sorted(Map.Entry.<Integer, List<UUID>>comparingByKey().reversed())
            .forEach(entry -> treeReportRepository.deleteAllByIdNodeIn(entry.getValue()));
        LOGGER.info("The report and tree report elements of '{}' has been deleted in {}ms", rootTreeReportId, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
    }

    @Transactional
    public void deleteReport(UUID id, String reportType) {
        Objects.requireNonNull(id);
        List<TreeReportEntity> allTreeReportsInReport = treeReportRepository.findAllByReportId(id);
        List<TreeReportEntity> filteredTreeReportsInReport = allTreeReportsInReport
            .stream()
            .filter(tre -> StringUtils.isBlank(reportType) || tre.getName().endsWith(reportType))
            .toList();
        filteredTreeReportsInReport.forEach(tre -> deleteRoot(tre.getIdNode()));

        if (filteredTreeReportsInReport.size() == allTreeReportsInReport.size()) {
            // let's remove the whole Report only if we have removed all its treeReport
            Integer nbReportDeleted = reportRepository.deleteReportById(id);
            if (nbReportDeleted == 0) {
                throw new EmptyResultDataAccessException("No element found", 1);
            }
        }
    }

    // package private for tests
    void deleteAll() {
        reportElementRepository.deleteAll();
        treeReportRepository.deleteAll();
        reportRepository.deleteAll();
        reportNodeRepository.deleteAll();
    }

    @Transactional
    public void deleteTreeReports(Map<UUID, String> identifiers) {
        Objects.requireNonNull(identifiers);
        identifiers.forEach(this::deleteTreeReport);
    }

    private void deleteTreeReport(UUID reportId, String reportName) {
        Objects.requireNonNull(reportId);
        List<TreeReportEntity> treeReports = treeReportRepository.findAllByReportIdAndName(reportId, reportName);
        treeReports.forEach(treeReport -> deleteRoot(treeReport.getIdNode()));
    }
}
