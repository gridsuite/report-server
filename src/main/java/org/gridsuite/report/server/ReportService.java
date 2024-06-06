/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.powsybl.commons.report.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.report.server.entities.*;
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
import java.util.function.Predicate;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Service
public class ReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);

    private static final long NANOS_FROM_EPOCH_TO_START;

    // the maximum number of parameters allowed in an In query. Prevents the number of parameters to reach the maximum allowed (65,535)
//    private static final int SQL_QUERY_MAX_PARAM_NUMBER = 10000;

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

    @Transactional(readOnly = true)
    public ReportNode getReport(UUID reportId, Set<String> severityLevels, String reportNameFilter, ReportNameMatchingType reportNameMatchingType) {
        Objects.requireNonNull(reportId);
        ReportNodeEntity reportNodeEntity = reportNodeRepository.findById(reportId).orElseThrow(EntityNotFoundException::new);

        return mapper(reportNodeEntity, severityLevels, reportNameFilter, reportNameMatchingType);
    }

    @Transactional(readOnly = true)
    public ReportNode getSubReport(UUID reporterId, Set<String> severityLevels) {
        Objects.requireNonNull(reporterId);
        ReportNodeEntity reportNodeEntity = reportNodeRepository.findById(reporterId).orElseThrow(EntityNotFoundException::new);

        return mapper(reportNodeEntity, severityLevels, null, null);
    }

    private static ReportNode mapper(ReportNodeEntity reportNodeEntity, @Nullable Set<String> severityLevels, @Nullable String reportNameFilter, @Nullable ReportNameMatchingType reportNameMatchingType) {
        ReportNodeBuilder builder = ReportNode.newRootReportNode();
        if (!Objects.isNull(reportNodeEntity.getMessageTemplate())) {
            builder.withMessageTemplate(reportNodeEntity.getMessageTemplate().getKey(), reportNodeEntity.getMessageTemplate().getMessage());
        } else {
            builder.withMessageTemplate(reportNodeEntity.getId().toString(), reportNodeEntity.getId().toString());
        }
        for (ValueEntity valueEntity : reportNodeEntity.getValues()) {
            addTypedValue(valueEntity, builder);
        }
        if (reportNodeEntity.getValues().stream().noneMatch(v -> v.getKey().equals(ReportConstants.REPORT_SEVERITY_KEY))) {
            builder.withTypedValue("id", reportNodeEntity.getId().toString(), "ID");
            if (!reportNodeEntity.getChildren().isEmpty()) {
                builder.withTypedValue(SEVERITY_LIST_KEY, reportNodeEntity.getSeverities().toString(), TypedValue.SEVERITY);
            }
        }
        ReportNode reportNode = builder.build();
        reportNodeEntity.getChildren()
            .stream()
            .filter(child -> StringUtils.isBlank(reportNameFilter)
                || child.getMessageTemplate().getKey().startsWith("Root") // FIXME remove this hack when "Root" report will follow the same rules than computations and modifications
                || reportNameMatchingType == ReportNameMatchingType.EXACT_MATCHING && child.getMessageTemplate().getKey().equals(reportNameFilter)
                || reportNameMatchingType == ReportNameMatchingType.ENDS_WITH && child.getMessageTemplate().getKey().endsWith(reportNameFilter))
            .filter(hasOneOfSeverityLevels(severityLevels))
            .forEach(child -> mapper(reportNode, child, severityLevels));
        return reportNode;
    }

    private static void mapper(ReportNode parentNode, ReportNodeEntity reportNodeEntity, Set<String> severityLevels) {
        ReportNodeAdder adder = parentNode.newReportNode()
            .withMessageTemplate(reportNodeEntity.getMessageTemplate().getKey(), reportNodeEntity.getMessageTemplate().getMessage());
        for (ValueEntity valueEntity : reportNodeEntity.getValues()) {
            addTypedValue(valueEntity, adder);
        }
        if (hasNoReportSeverity(reportNodeEntity)) {
            adder.withTypedValue("id", reportNodeEntity.getId().toString(), "ID");
            if (!reportNodeEntity.getChildren().isEmpty()) {
                adder.withTypedValue(SEVERITY_LIST_KEY, reportNodeEntity.getSeverities().toString(), TypedValue.SEVERITY);
            }
        }
        ReportNode newNode = adder.add();
        reportNodeEntity.getChildren()
            .stream()
            .filter(hasOneOfSeverityLevels(severityLevels))
            .forEach(child -> mapper(newNode, child, severityLevels));
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

    private static boolean hasNoReportSeverity(ReportNodeEntity reportNodeEntity) {
        return reportNodeEntity.getValues().stream().noneMatch(v -> v.getKey().equals(ReportConstants.REPORT_SEVERITY_KEY));
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
//        /*
//         * Groups tree report node IDs by level for batch deletion.
//         * This is necessary otherwise H2 throws JdbcSQLIntegrityConstraintViolationException when issuing the delete query with 'where id in (x1,x2,...)' (we use h2 for unit tests).
//         * For postgres, this is not necessary if all the ids are in the same delete query, but would be a problem
//         * if we decided to partition the deletes in smaller batches in multiple transactions (in multiple deletes in one transaction we could defer the checks at the commit with 'SET CONSTRAINTS DEFERRED')
//         */
//        Map<Integer, List<UUID>> treeReportIdsByLevel = treeReportRepository.getSubReportsNodesWithLevel(rootTreeReportId)
//            .stream()
//            .collect(Collectors.groupingBy(
//                result -> (Integer) result[1],
//                Collectors.mapping(
//                    result -> UUID.fromString((String) result[0]),
//                    Collectors.toList()
//                )
//            ));
//
//        // Deleting the report elements in subsets because they can exceed the limit of 64k elements in the IN clause
//        List<UUID> groupedTreeReportIds = treeReportIdsByLevel.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
//        List<UUID> reportElementIds = reportElementRepository.findIdReportByParentReportIdNodeIn(groupedTreeReportIds)
//            .stream()
//            .map(ReportElementEntity.ProjectionIdReport::getIdReport)
//            .toList();
//        Lists.partition(reportElementIds, SQL_QUERY_MAX_PARAM_NUMBER)
//            .forEach(ids -> {
//                reportElementRepository.deleteAllReportElementValuesByIdReportIn(ids);
//                reportElementRepository.deleteAllByIdReportIn(ids);
//            });
//
//        // Delete all the report elements values and dictionaries since doesn't have any parent-child relationship
//        treeReportRepository.deleteAllTreeReportValuesByIdNodeIn(groupedTreeReportIds);
//        treeReportRepository.deleteAllTreeReportDictionaryByIdNodeIn(groupedTreeReportIds);
//
//        // Deleting the tree reports level by level, starting from the highest level
//        treeReportIdsByLevel.entrySet().stream()
//            .sorted(Map.Entry.<Integer, List<UUID>>comparingByKey().reversed())
//            .forEach(entry -> treeReportRepository.deleteAllByIdNodeIn(entry.getValue()));
        reportNodeRepository.deleteById(rootTreeReportId);
        LOGGER.info("The report and tree report elements of '{}' has been deleted in {}ms", rootTreeReportId, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
    }

    @Transactional
    public void deleteReport(UUID id, String reportType) {
        Objects.requireNonNull(id);
        ReportNodeEntity reportNodeEntity = reportNodeRepository.findById(id).orElseThrow(() -> new EmptyResultDataAccessException("No element found", 1));
        List<ReportNodeEntity> filteredChildrenList = reportNodeEntity.getChildren()
            .stream()
            .filter(child -> StringUtils.isBlank(reportType) || child.getMessageTemplate().getKey().endsWith(reportType))
            .toList();
        filteredChildrenList.forEach(child -> deleteRoot(child.getId()));

        if (filteredChildrenList.size() == reportNodeEntity.getChildren().size()) {
            // let's remove the whole Report only if we have removed all its treeReport
            reportNodeRepository.deleteById(id);
        }
    }

    // package private for tests
    void deleteAll() {
        reportNodeRepository.deleteAll();
    }

    @Transactional
    public void deleteTreeReports(Map<UUID, String> identifiers) {
        Objects.requireNonNull(identifiers);
        identifiers.forEach(this::deleteTreeReport);
    }

    private void deleteTreeReport(UUID reportId, String reportName) {
        Objects.requireNonNull(reportId);
        List<ReportNodeEntity> reportNodeEntities = reportNodeRepository.findAllByParentIdAndMessageTemplateKey(reportId, reportName);
        reportNodeEntities.forEach(reportNodeEntity -> deleteRoot(reportNodeEntity.getId()));
    }
}
