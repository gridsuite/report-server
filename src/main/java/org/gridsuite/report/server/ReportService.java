/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.google.common.collect.Lists;
import com.powsybl.commons.report.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.report.server.dto.Report;
import org.gridsuite.report.server.entities.ReportElementEntity;
import org.gridsuite.report.server.entities.ReportEntity;
import org.gridsuite.report.server.entities.ReportValueEmbeddable;
import org.gridsuite.report.server.entities.TreeReportEntity;
import org.gridsuite.report.server.repositories.ReportElementRepository;
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
import java.util.stream.IntStream;


/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Service
public class ReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);

    private static final long NANOS_FROM_EPOCH_TO_START;

    // the maximum number of parameters allowed in an In query. Prevents the number of parameters to reach the maximum allowed (65,535)
    private static final int SQL_QUERY_MAX_PARAM_NUMBER = 10000;

    private static final String SEVERITY_LIST_VALUE_NAME = "severityList";
    private static final String REPORT_SEVERITY_VALUE_NAME = "reportSeverity";

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

    public ReportService(final ReportRepository reportRepository, TreeReportRepository treeReportRepository, ReportElementRepository reportElementRepository) {
        this.reportRepository = reportRepository;
        this.treeReportRepository = treeReportRepository;
        this.reportElementRepository = reportElementRepository;
    }

    @Transactional(readOnly = true)
    public List<Report> getReport(UUID reportId, @Nullable Set<String> severityLevels, String reportNameFilter, @Nullable ReportNameMatchingType reportNameMatchingType) {
        Objects.requireNonNull(reportId);
        ReportEntity reportEntity = reportRepository.findById(reportId).orElseThrow(EntityNotFoundException::new);

        // When matching is ENDS_WITH, we may have N modification node reports to collect: the node itself and
        // all its un-built parents (until the next built one). This means several TreeReportEntity to process.
        // When matching is EXACT_MATCHING, we also may have to process several TreeReportEntity: one per build / incremental build.
        List<TreeReportEntity> treeReportEntities = treeReportRepository.findAllByReportIdOrderByNanos(reportEntity.getId())
            .stream()
                .filter(tre -> StringUtils.isBlank(reportNameFilter)
                        || tre.getName().startsWith("Root") // FIXME remove this hack when "Root" report will follow the same rules than computations and modifications
                        || reportNameMatchingType == ReportNameMatchingType.EXACT_MATCHING && tre.getName().equals(reportNameFilter)
                        || reportNameMatchingType == ReportNameMatchingType.ENDS_WITH && tre.getName().endsWith(reportNameFilter)).toList();

        Report reportContainer = new Report();

        // Create/reuse a single Report per TreeReportEntity found
        Map<String, Report> reportsByTreeReportName = new LinkedHashMap<>();
        treeReportEntities.forEach(treeReportEntity -> {
            if (!reportsByTreeReportName.containsKey(treeReportEntity.getName())) {
                reportsByTreeReportName.put(treeReportEntity.getName(), reportContainer.addEmptyReport());
            }
        });
        treeReportEntities.forEach(treeReportEntity -> {
            Report report = reportsByTreeReportName.get(treeReportEntity.getName());
            report.setId(reportId);
            String nodeId = treeReportEntity.getName().split("@")[0];
            report.setMessage(nodeId);
            addSubReportNode(report, treeReportEntity, severityLevels);
        });

        return reportContainer.getSubReports();
    }

    @Transactional(readOnly = true)
    public Report getSubReport(UUID reporterId, Set<String> severityLevels) {
        Objects.requireNonNull(reporterId);
        TreeReportEntity treeReportEntity = treeReportRepository.findById(reporterId).orElseThrow(EntityNotFoundException::new);
        Report report = new Report();
        report.setId(treeReportEntity.getIdNode());
        report.setMessage(treeReportEntity.getName());
        addSubReportNode(report, treeReportEntity, severityLevels);
        return report;
    }

    private void addSubReportNode(Report rootReportNode, TreeReportEntity subTreeReport, @Nullable Set<String> severityLevels) {
        // Let's find all the treeReportEntities ids that inherit from the parent treeReportEntity
        final List<UUID> treeReportEntitiesIds = treeReportRepository.findAllTreeReportIdsRecursivelyByParentTreeReport(subTreeReport.getIdNode())
                .stream()
                .map(UUID::fromString)
                .toList();

        // Let's find all the reportElements that are linked to the found treeReports
        List<ReportElementEntity> allReportElements = reportElementRepository.findAllByParentReportIdNodeInOrderByNanos(treeReportEntitiesIds)
            .stream()
            .filter(reportElementEntity -> reportElementEntity.hasSeverity(severityLevels) || reportElementEntity.getValues().isEmpty()) // reportElementEntity.getValues().isEmpty() is a hack to get the empty subreports
            .toList();

        // We need to get the entities to have access to the dictionaries
        List<TreeReportEntity> treeReportEntities = treeReportRepository.findAllByIdNodeInOrderByNanos(treeReportEntitiesIds);

        // Now we can rebuild the tree
        rebuildReportTree(rootReportNode, subTreeReport, treeReportEntities, allReportElements);
    }

    private Severity getElementSeverity(final ReportElementEntity reportElement) {
        return reportElement.getValues()
                .stream()
                .filter(v -> v.getName().equalsIgnoreCase(REPORT_SEVERITY_VALUE_NAME))
                .findFirst()
                .map(ReportValueEmbeddable::getValue)
                .map(Severity::fromValue)
                .orElse(null);
    }

    private List<Severity> getReportSeverityList(final TreeReportEntity treeReport) {
        return treeReport.getValues()
                .stream()
                .filter(v -> v.getName().equalsIgnoreCase(SEVERITY_LIST_VALUE_NAME) && v.getValue() != null && v.getValue().startsWith("[") && v.getValue().endsWith("]"))
                .findFirst()
                .map(v -> {
                    // convert "[INFO, WARN]" string into List.of(INFO, WARN)
                    List<String> values = Arrays.asList(v.getValue().substring(1, v.getValue().length() - 1).split(","));
                    return values.stream().map(String::trim).filter(s -> !s.isEmpty()).map(Severity::fromValue).toList();
                })
                .orElse(null);
    }

    private void rebuildReportTree(final Report rootReportNode, final TreeReportEntity rootTreeReportEntity, final List<TreeReportEntity> allTreeReports,
                             @Nullable final List<ReportElementEntity> allReportElements) {
        Map<UUID, Map<String, String>> treeReportEntityDictionaries = new HashMap<>(allTreeReports.size());
        Map<UUID, List<TreeReportEntity>> reportNodeIdToChildTreeReports = new HashMap<>(allTreeReports.size());
        Map<UUID, Report> treeReportIdToReportNodes = new HashMap<>(allTreeReports.size());

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
        treeReportIdToReportNodes.put(rootTreeReportEntity.getIdNode(), rootReportNode);

        addChildNodes(rootReportNode, rootTreeReportEntity.getIdNode(), reportNodeIdToChildTreeReports, treeReportIdToReportNodes);

        if (allReportElements != null) {
            for (final ReportElementEntity entity : allReportElements) {
                final Map<String, String> dict = treeReportEntityDictionaries.get(entity.getParentReport().getIdNode());
                Report report = treeReportIdToReportNodes.get(entity.getParentReport().getIdNode());
                String message = dict.get(entity.getName()); // TODO should build a full message here
                if (entity.getValues().isEmpty()) {
                    // reports without values are considered as sub-reports
                    report.addReportChild(entity.getIdReport(), List.of(), message);
                } else {
                    // add a leaf sub-report for each report element
                    report.addReportElement(getElementSeverity(entity), message);
                }
            }
        }
    }

    private void addChildNodes(Report parentNode, UUID reportNodeId, Map<UUID, List<TreeReportEntity>> reportNodeIdToChildren, Map<UUID, Report> treeReportIdToReportNodes) {
        List<TreeReportEntity> children = reportNodeIdToChildren.get(reportNodeId);
        if (children == null) {
            return;
        }
        children.forEach(childEntity -> {
            String childMessage = childEntity.getDictionary().get(childEntity.getName()); // TODO should build a full message here
            Report childReport = parentNode.addReportChild(childEntity.getIdNode(), getReportSeverityList(childEntity), childMessage);
            treeReportIdToReportNodes.put(childEntity.getIdNode(), childReport);
            if (reportNodeIdToChildren.containsKey(childEntity.getIdNode())) {
                addChildNodes(childReport, childEntity.getIdNode(), reportNodeIdToChildren, treeReportIdToReportNodes);
            }
        });
    }

    public Report getEmptyReport(@NonNull String defaultName) {
        Report emptyReport = new Report();
        emptyReport.setMessage(defaultName);
        return emptyReport;
    }

    private ReportEntity toEntity(UUID id, ReportNode reportElement) {
        var persistedReport = reportRepository.findById(id).orElseGet(() -> reportRepository.save(new ReportEntity(id)));
        saveAllReportElements(persistedReport, reportElement, null);
        return persistedReport;
    }

    private void saveAllReportElements(ReportEntity persistedReport, ReportNode reportNode, TreeReportEntity parentNode) {
        // This return a list of ReportElementEntity to be saved at the end, otherwise
        // hibernate.order_insert don't work properly since https://hibernate.atlassian.net/browse/HHH-16485 hibernate 6.2.2
        List<ReportElementEntity> reportElementEntities = new ArrayList<>();
        traverseReportModel(persistedReport, reportNode, parentNode, reportElementEntities);
        reportElementRepository.saveAll(reportElementEntities);
    }

    private void traverseReportModel(ReportEntity persistedReport, ReportNode reportNode, TreeReportEntity parentNode, List<ReportElementEntity> reportElementEntities) {
        Map<String, String> dict = new HashMap<>();
        dict.put(reportNode.getMessageKey(), reportNode.getMessageTemplate());

        List<ReportValueEmbeddable> reportValueEmbeddableList = toValueEntityList(reportNode.getValues());
        reportValueEmbeddableList.add(new ReportValueEmbeddable(SEVERITY_LIST_VALUE_NAME, severityList(reportNode), TypedValue.SEVERITY));

        TreeReportEntity treeReportEntity = new TreeReportEntity(null, reportNode.getMessageKey(), persistedReport,
                reportValueEmbeddableList, parentNode, dict,
                System.nanoTime() - NANOS_FROM_EPOCH_TO_START);

        treeReportRepository.save(treeReportEntity);

        List<ReportNode> subReporters = reportNode.getChildren().stream().filter(report -> !report.getChildren().isEmpty()).toList();
        IntStream.range(0, subReporters.size()).forEach(idx -> traverseReportModel(null, subReporters.get(idx), treeReportEntity, reportElementEntities));

        List<ReportNode> reports = reportNode.getChildren().stream().filter(report -> report.getChildren().isEmpty()).toList();
        IntStream.range(0, reports.size()).forEach(idx -> reportElementEntities.add(toReportElementEntity(treeReportEntity, reports.get(idx), dict)));
    }

    private static List<String> severityList(ReportNode reportNode) {
        return reportNode.getChildren()
                .stream()
                .filter(report -> report.getChildren().isEmpty() && !report.getValues().isEmpty()) // reports without values are considered as subreports so we don't want them in the severity list
                .map(report -> report.getValues().get(REPORT_SEVERITY_VALUE_NAME))
                .map(severity -> severity == null ? Severity.UNKNOWN.toString() : Severity.fromValue(Objects.toString(severity.getValue())).toString())
                .distinct().toList();
    }

    private ReportElementEntity toReportElementEntity(TreeReportEntity parentReport, ReportNode report, Map<String, String> dict) {
        dict.put(report.getMessageKey(), report.getMessageTemplate());
        return new ReportElementEntity(null, parentReport,
            System.nanoTime() - NANOS_FROM_EPOCH_TO_START,
            report.getMessageKey(), toValueEntityList(report.getValues()));
    }

    private List<ReportValueEmbeddable> toValueEntityList(Map<String, TypedValue> values) {
        return values.entrySet().stream().map(this::toValueEmbeddable).collect(Collectors.toList());
    }

    private ReportValueEmbeddable toValueEmbeddable(Map.Entry<String, TypedValue> entryValue) {
        return new ReportValueEmbeddable(entryValue.getKey(), entryValue.getValue().getValue(), entryValue.getValue().getType());
    }

    @Transactional
    public void createReport(UUID id, ReportNode reportNode) {
        Optional<ReportEntity> reportEntity = reportRepository.findById(id);
        if (reportEntity.isPresent()) {
            LOGGER.debug("Reporter {} present, append ", reportNode.getMessage());
            saveAllReportElements(reportEntity.get(), reportNode, null);
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
        /**
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
