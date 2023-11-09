/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.TypedValue;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Service
public class ReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);

    private static final long NANOS_FROM_EPOCH_TO_START;

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

    public enum TaskKeyFilterMatchingType {
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

    private Map<String, TypedValue> toDtoValueMap(List<ReportValueEmbeddable> values) {
        Map<String, TypedValue> res = new HashMap<>();
        values.forEach(value -> res.put(value.getName(), toTypedValue(value)));
        return res;
    }

    private TypedValue toTypedValue(ReportValueEmbeddable value) {
        switch (value.getValueType()) {
            case DOUBLE: return new TypedValue(Double.valueOf(value.getValue()), value.getType());
            case INTEGER: return new TypedValue(Integer.valueOf(value.getValue()), value.getType());
            default: return new TypedValue(value.getValue(), value.getType());
        }
    }

    @Transactional(readOnly = true)
    public ReporterModel getReport(UUID reportId, boolean withElements, Set<String> severityLevels, String taskKeyFilter, TaskKeyFilterMatchingType taskKeyFilterMatchingType) {
        Objects.requireNonNull(reportId);
        ReportEntity reportEntity = reportRepository.findById(reportId).orElseThrow(EntityNotFoundException::new);

        var report = new ReporterModel(reportId.toString(), reportId.toString());
        treeReportRepository.findAllByReportId(reportEntity.getId())
            .stream()
                .filter(tre -> StringUtils.isBlank(taskKeyFilter)
                        || tre.getName().startsWith("Root") // Dont know how to better manage this special Root case
                        || taskKeyFilterMatchingType == TaskKeyFilterMatchingType.EXACT_MATCHING && tre.getName().equals(taskKeyFilter)
                        || taskKeyFilterMatchingType == TaskKeyFilterMatchingType.ENDS_WITH && tre.getName().endsWith(taskKeyFilter))
            .sorted((tre1, tre2) -> Long.signum(tre1.getNanos() - tre2.getNanos())) // using Long.signum (and not '<' ) to circumvent possible long overflow
            .forEach(treeReportEntity -> report.addSubReporter(getTreeReport(treeReportEntity, withElements, severityLevels)));
        return report;
    }

    @Transactional(readOnly = true)
    public ReporterModel getSubReport(UUID reporterId, Set<String> severityLevels) {
        Objects.requireNonNull(reporterId);
        TreeReportEntity treeReportEntity = treeReportRepository.findById(reporterId).orElseThrow(EntityNotFoundException::new);

        var report = new ReporterModel(treeReportEntity.getIdNode().toString(), treeReportEntity.getIdNode().toString());
        report.addSubReporter(getTreeReport(treeReportEntity, true, severityLevels));
        return report;
    }

    private ReporterModel getTreeReport(TreeReportEntity treeReportEntity, boolean withElements, Set<String> severityLevels) {
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

    private ReporterModel toDto(final TreeReportEntity rootTreeReportEntity, final List<TreeReportEntity> allTreeReports,
                                @Nullable final List<ReportElementEntity> allReportElements) {
        // We convert our entities to PowSyBl Reporter
        Map<UUID, ReporterModel> reporters = new HashMap<>(allTreeReports.size());
        Map<UUID, Map<String, String>> treeReportEntityDictionaries = new HashMap<>(allTreeReports.size());
        for (final TreeReportEntity entity : allTreeReports) {
            final Map<String, String> dict = entity.getDictionary();
            treeReportEntityDictionaries.put(entity.getIdNode(), dict);

            // This ID is used by the front for direct access to the reporter
            entity.getValues().add(new ReportValueEmbeddable("id", entity.getIdNode(), "ID"));

            ReporterModel reporter = new ReporterModel(entity.getName(), dict.get(entity.getName()), toDtoValueMap(entity.getValues()));
            reporters.put(entity.getIdNode(), reporter);
        }

        // We rebuild parent-child links between reporters
        final UUID rootUuid = rootTreeReportEntity.getIdNode();
        for (final TreeReportEntity entity : allTreeReports) {
            // we exclude root node to not get reporters outside scope
            if (entity.getParentReport() != null && !rootUuid.equals(entity.getIdNode())) {
                reporters.get(entity.getParentReport().getIdNode()).addSubReporter(reporters.get(entity.getIdNode()));
            }
        }
        if (allReportElements != null) {
            // We convert ReportElementEntities to dto and add it to the corresponding ReporterModel
            for (final ReportElementEntity entity : allReportElements) {
                final Map<String, String> dict = treeReportEntityDictionaries.get(entity.getParentReport().getIdNode());
                reporters.get(entity.getParentReport().getIdNode()).report(entity.getName(), dict.get(entity.getName()), toDtoValueMap(entity.getValues()));
            }
        }
        return reporters.get(rootUuid);
    }

    public ReporterModel getEmptyReport(@NonNull UUID id, @NonNull String defaultName) {
        ReporterModel reporter = new ReporterModel(id.toString(), id.toString());
        reporter.addSubReporter(new ReporterModel(defaultName, defaultName));
        return reporter;
    }

    private ReportEntity toEntity(UUID id, ReporterModel reportElement) {
        var persistedReport = reportRepository.findById(id).orElseGet(() -> reportRepository.save(new ReportEntity(id)));
        toEntity(persistedReport, reportElement, null);
        return persistedReport;
    }

    private TreeReportEntity toEntity(ReportEntity persistedReport, ReporterModel reporterModel, TreeReportEntity parentNode) {
        Map<String, String> dict = new HashMap<>();
        dict.put(reporterModel.getTaskKey(), reporterModel.getDefaultName());
        var newTreeReportEntity = new TreeReportEntity(null, reporterModel.getTaskKey(), persistedReport,
                toValueEntityList(reporterModel.getTaskValues()), parentNode, dict,
                System.nanoTime() - NANOS_FROM_EPOCH_TO_START);
        var treeReportEntity = treeReportRepository.save(newTreeReportEntity);

        List<ReporterModel> subReporters = reporterModel.getSubReporters();
        IntStream.range(0, subReporters.size()).forEach(idx -> toEntity(null, subReporters.get(idx), treeReportEntity));

        Collection<Report> reports = reporterModel.getReports();
        List<Report> reportsAsList = new ArrayList<>(reports);
        IntStream.range(0, reportsAsList.size()).forEach(idx -> toEntity(treeReportEntity, reportsAsList.get(idx), dict));

        return treeReportEntity;
    }

    private ReportElementEntity toEntity(TreeReportEntity parentReport, Report report, Map<String, String> dict) {
        dict.put(report.getReportKey(), report.getDefaultMessage());
        return reportElementRepository.save(new ReportElementEntity(null, parentReport,
            System.nanoTime() - NANOS_FROM_EPOCH_TO_START,
            report.getReportKey(), toValueEntityList(report.getValues())));
    }

    private List<ReportValueEmbeddable> toValueEntityList(Map<String, TypedValue> values) {
        return values.entrySet().stream().map(this::toValueEmbeddable).collect(Collectors.toList());
    }

    private ReportValueEmbeddable toValueEmbeddable(Map.Entry<String, TypedValue> entryValue) {
        return new ReportValueEmbeddable(entryValue.getKey(), entryValue.getValue().getValue(), entryValue.getValue().getType());
    }

    @Transactional
    public void createReports(UUID id, ReporterModel reporter) {
        Optional<ReportEntity> reportEntity = reportRepository.findById(id);
        if (reportEntity.isPresent()) {
            LOGGER.debug("Reporter {} present, append ", reporter.getDefaultName());
            toEntity(reportEntity.get(), reporter, null);
        } else {
            LOGGER.debug("Reporter {} absent, create ", reporter.getDefaultName());
            toEntity(id, reporter);
        }
    }

    private void deleteRoot(UUID id) {
        List<UUID> treeReport = treeReportRepository.getSubReportsNodes(id).stream().map(UUID::fromString).collect(Collectors.toList());
        List<UUID> elements = reportElementRepository.findIdReportByParentReportIdNodeIn(treeReport)
            .stream().map(ReportElementEntity.ProjectionIdReport::getIdReport).collect(Collectors.toList());
        reportElementRepository.deleteAllByIdReportIn(elements);
        treeReportRepository.deleteAllByIdNodeIn(treeReport);
    }

    @Transactional
    public void deleteReport(UUID id, String taskKeyTypeFilter) {
        Objects.requireNonNull(id);
        List<TreeReportEntity> allTreeReportsInReport = treeReportRepository.findAllByReportId(id);
        List<TreeReportEntity> filteredTreeReportsInReport = allTreeReportsInReport
                .stream()
                .filter(tre -> StringUtils.isBlank(taskKeyTypeFilter) || tre.getName().endsWith(taskKeyTypeFilter))
                .toList();
        filteredTreeReportsInReport.forEach(tre -> deleteRoot(tre.getIdNode()));

        // remove the whole Report only if we have removed all its treeReport
        if (filteredTreeReportsInReport.size() == allTreeReportsInReport.size() && reportRepository.deleteReportById(id) == 0) {
            throw new EmptyResultDataAccessException("No element found", 1);
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

    private void deleteTreeReport(UUID reportId, String name) {
        Objects.requireNonNull(reportId);
        List<TreeReportEntity> treeReports = treeReportRepository.findAllByReportIdAndName(reportId, name);
        treeReports.forEach(treeReport -> deleteRoot(treeReport.getIdNode()));
    }
}
