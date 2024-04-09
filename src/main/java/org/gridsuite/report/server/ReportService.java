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
import com.powsybl.commons.report.ReportNodeBuilder;
import com.powsybl.commons.report.TypedValue;
import jakarta.persistence.EntityNotFoundException;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
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

    /**
     * @see TypedValue
     */
    public enum SeverityLevel {
        UNKNOWN, TRACE, DEBUG, INFO, WARN, ERROR, FATAL;

        public static SeverityLevel fromValue(String value) {
            try {
                return valueOf(value);
            } catch (final IllegalArgumentException | NullPointerException e) {
                return UNKNOWN;
            }
        }
    }

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

//    private Map<String, TypedValue> toDtoValueMap(List<ReportValueEmbeddable> values) {
//        Map<String, TypedValue> res = new HashMap<>();
//        values.forEach(value -> res.put(value.getName(), toTypedValue(value)));
//        return res;
//    }
//
//    private TypedValue toTypedValue(ReportValueEmbeddable value) {
//        switch (value.getValueType()) {
//            case DOUBLE: return new TypedValue(Double.valueOf(value.getValue()), value.getType());
//            case INTEGER: return new TypedValue(Integer.valueOf(value.getValue()), value.getType());
//            default: return new TypedValue(value.getValue(), value.getType());
//        }
//    }

    @Transactional(readOnly = true)
    public ReportNode getReport(UUID reportId, boolean withElements, Set<String> severityLevels, String reportNameFilter, ReportNameMatchingType reportNameMatchingType) {
        Objects.requireNonNull(reportId);
        ReportEntity reportEntity = reportRepository.findById(reportId).orElseThrow(EntityNotFoundException::new);

        var report = ReportNode.newRootReportNode()
                .withMessageTemplate(reportId.toString(), reportId.toString())
                .build();

        treeReportRepository.findAllByReportIdOrderByNanos(reportEntity.getId())
            .stream()
                .filter(tre -> StringUtils.isBlank(reportNameFilter)
                        || tre.getName().startsWith("Root") // FIXME remove this hack when "Root" report will follow the same rules than computations and modifications
                        || reportNameMatchingType == ReportNameMatchingType.EXACT_MATCHING && tre.getName().equals(reportNameFilter)
                        || reportNameMatchingType == ReportNameMatchingType.ENDS_WITH && tre.getName().endsWith(reportNameFilter))
            .forEach(treeReportEntity -> report.include(getTreeReport(treeReportEntity, withElements, severityLevels)));
        return report;
    }

    @Transactional(readOnly = true)
    public ReportNode getSubReport(UUID reporterId, Set<String> severityLevels) {
        Objects.requireNonNull(reporterId);
        TreeReportEntity treeReportEntity = treeReportRepository.findById(reporterId).orElseThrow(EntityNotFoundException::new);

        ReportNode report = ReportNode.newRootReportNode()
                .withMessageTemplate(treeReportEntity.getIdNode().toString(), treeReportEntity.getIdNode().toString())
                .build();

        report.include(getTreeReport(treeReportEntity, true, severityLevels));
        return report;
    }

    private ReportNode getTreeReport(TreeReportEntity treeReportEntity, boolean withElements, Set<String> severityLevels) {
        // Let's find all the treeReportEntities ids that inherit from the parent treeReportEntity
        final List<UUID> treeReportEntitiesIds = treeReportRepository.findAllTreeReportIdsRecursivelyByParentTreeReport(treeReportEntity.getIdNode())
                .stream()
                .map(UUID::fromString)
                .toList();

        List<ReportElementEntity> allReportElements = null;
        if (withElements) {
            // Let's find all the reportElements that are linked to the found treeReports
            allReportElements = reportElementRepository.findAllByParentReportIdNodeInOrderByNanos(treeReportEntitiesIds)
                    .stream()
                    .filter(reportElementEntity -> reportElementEntity.hasSeverity(severityLevels))
                    .toList();
        }

        // We need to get the entities to have access to the dictionaries
        List<TreeReportEntity> treeReportEntities = treeReportRepository.findAllByIdNodeInOrderByNanos(treeReportEntitiesIds);

        // Now we can rebuild the tree
        return toDto(treeReportEntity, treeReportEntities, allReportElements);
    }

    private ReportNode toDto(final TreeReportEntity rootTreeReportEntity, final List<TreeReportEntity> allTreeReports,
                                @Nullable final List<ReportElementEntity> allReportElements) {
        // We convert our entities to PowSyBl Reporter
        Map<UUID, ReportNode> reportNodes = new HashMap<>(allTreeReports.size());
        Map<UUID, Map<String, String>> treeReportEntityDictionaries = new HashMap<>(allTreeReports.size());
        for (final TreeReportEntity entity : allTreeReports) {
            final Map<String, String> dict = entity.getDictionary();
            treeReportEntityDictionaries.put(entity.getIdNode(), dict);

            // This ID is used by the front for direct access to the reporter
            entity.getValues().add(new ReportValueEmbeddable("id", entity.getIdNode(), "ID"));

            ReportNodeBuilder reportNodeBuilder = ReportNode.newRootReportNode()
                    .withMessageTemplate(entity.getName(), dict.get(entity.getName()));

            entity.getValues().forEach(value -> reportNodeBuilder.withTypedValue(value.getName(), value.getValue(), value.getType()));

            ReportNode reportNode = reportNodeBuilder.build();

            reportNodes.put(entity.getIdNode(), reportNode);
        }

        // We rebuild parent-child links between reporters
        final UUID rootUuid = rootTreeReportEntity.getIdNode();
        for (final TreeReportEntity entity : allTreeReports) {
            // we exclude root node to not get reporters outside scope
            if (entity.getParentReport() != null && !rootUuid.equals(entity.getIdNode())) {
                reportNodes.get(entity.getParentReport().getIdNode()).include(reportNodes.get(entity.getIdNode()));
            }
        }
        if (allReportElements != null) {
            // We convert ReportElementEntities to dto and add it to the corresponding ReporterModel
            for (final ReportElementEntity entity : allReportElements) {
                final Map<String, String> dict = treeReportEntityDictionaries.get(entity.getParentReport().getIdNode());
                ReportNodeAdder reportNodeAdder = reportNodes.get(entity.getParentReport().getIdNode()).newReportNode().withMessageTemplate(entity.getName(), dict.get(entity.getName()));
                entity.getValues().forEach(value -> reportNodeAdder.withTypedValue(value.getName(), value.getValue(), value.getType()));
            }
        }
        return reportNodes.get(rootUuid);
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
        dict.put(reportNode.getMessageKey(), reportNode.getMessage());

        List<ReportValueEmbeddable> reportValueEmbeddableList = toValueEntityList(reportNode.getValues());
        reportValueEmbeddableList.add(new ReportValueEmbeddable("severityList", severityList(reportNode), TypedValue.SEVERITY));

        TreeReportEntity treeReportEntity = new TreeReportEntity(null, reportNode.getMessageKey(), persistedReport,
                reportValueEmbeddableList, parentNode, dict,
                System.nanoTime() - NANOS_FROM_EPOCH_TO_START);

        treeReportRepository.save(treeReportEntity);

        List<ReportNode> subReporters = reportNode.getChildren();
        IntStream.range(0, subReporters.size()).forEach(idx -> traverseReportModel(null, subReporters.get(idx), treeReportEntity, reportElementEntities));

        Collection<ReportNode> reports = reportNode.getChildren();
        List<ReportNode> reportsAsList = new ArrayList<>(reports);
        IntStream.range(0, reportsAsList.size()).forEach(idx -> reportElementEntities.add(toReportElementEntity(treeReportEntity, reportsAsList.get(idx), dict)));
    }

    private static List<String> severityList(ReportNode reportNode) {
        return reportNode.getChildren()
                .stream()
                .map(report -> report.getValues().get("reportSeverity"))
                .map(severity -> severity == null ? SeverityLevel.UNKNOWN.toString() : SeverityLevel.fromValue(Objects.toString(severity.getValue())).toString())
                .distinct().toList();
    }

    private ReportElementEntity toReportElementEntity(TreeReportEntity parentReport, ReportNode report, Map<String, String> dict) {
        dict.put(report.getMessageKey(), report.getMessage());
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
