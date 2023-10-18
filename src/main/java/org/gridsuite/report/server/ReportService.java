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
import lombok.NonNull;
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

import jakarta.persistence.EntityNotFoundException;
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

    public static final String UNKNOWN_SEVERITY = "UNKNOWN";
    static final Map<String, Integer> SEVERITY_LEVELS = Map.of(UNKNOWN_SEVERITY, 0, "TRACE", 1, "INFO", 2, "WARN", 3, "ERROR", 4, "FATAL", 5);

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

    // TODO Should be optimized
    @Deprecated
    @Transactional(readOnly = true)
    public ReporterModel getReportStructureAndElements(UUID id) {
        Objects.requireNonNull(id);
        return toDto(reportRepository.findById(id).orElseThrow(EntityNotFoundException::new));
    }

    private ReporterModel toDto(ReportEntity element) {
        UUID elementId = Objects.requireNonNull(element.getId());
        var report = new ReporterModel(elementId.toString(), elementId.toString());
        // using Long.signum (and not '<' ) to circumvent possible long overflow
        treeReportRepository.findAllByReportId(elementId)
            .stream()
            .sorted((tre1, tre2) -> Long.signum(tre1.getNanos() - tre2.getNanos()))
            .forEach(treeReport -> report.addSubReporter(toDto(treeReport)));
        return report;
    }

    private ReporterModel toDto(TreeReportEntity element) {
        Map<String, String> dict = element.getDictionary();
        var reportModel = new ReporterModel(element.getName(), dict.get(element.getName()), toDtoValueMap(element.getValues()));
        // using Long.signum (and not '<' ) to circumvent possible long overflow
        reportElementRepository.findAllByParentReportIdNode(element.getIdNode())
            .stream()
            .sorted((re1, re2) -> Long.signum(re1.getNanos() - re2.getNanos()))
            .forEach(report ->
                reportModel.report(report.getName(), dict.get(report.getName()), toDtoValueMap(report.getValues()))
            );
        treeReportRepository.findAllByParentReportIdNode(element.getIdNode())
            .stream()
            .sorted((tre1, tre2) -> Long.signum(tre1.getNanos() - tre2.getNanos()))
            .forEach(treeReport -> reportModel.addSubReporter(toDto(treeReport)));
        return reportModel;
    }

    @Transactional(readOnly = true)
    public ReporterModel getReportStructure(UUID reportId) {
        Objects.requireNonNull(reportId);
        ReportEntity reportEntity = reportRepository.findById(reportId).orElseThrow(EntityNotFoundException::new);
        UUID elementId = Objects.requireNonNull(reportEntity.getId());
        var report = new ReporterModel(elementId.toString(), elementId.toString());

        // We want to track to which reportId a TreeReport/taskkey is linked with (used by the Front)
        List<TreeReportEntity> allTreeReportForReportId = treeReportRepository.findAllByReportId(elementId);
        allTreeReportForReportId.forEach(tre -> {
            tre.getValues().add(new ReportValueEmbeddable(tre.getName(), reportId.toString(), "REPORTID"));
        });

        // TODO Should use a native recursive SQL function instead of a recursive java implementation
        allTreeReportForReportId
            .stream()
            .sorted((tre1, tre2) -> Long.signum(tre1.getNanos() - tre2.getNanos())) // using Long.signum (and not '<' ) to circumvent possible long overflow
            .forEach(treeReport -> report.addSubReporter(recursiveTreeReportBuilder(treeReport)));

        return report;
    }

    private ReporterModel recursiveTreeReportBuilder(TreeReportEntity element) {
        Map<String, String> dict = element.getDictionary();
        element.getValues().add(new ReportValueEmbeddable("id", element.getIdNode(), "ID"));
        var reportModel = new ReporterModel(element.getName(), dict.get(element.getName()), toDtoValueMap(element.getValues()));

        // using Long.signum (and not '<' ) to circumvent possible long overflow
        treeReportRepository.findAllByParentReportIdNode(element.getIdNode())
            .stream()
            .sorted((tre1, tre2) -> Long.signum(tre1.getNanos() - tre2.getNanos()))
            .forEach(treeReport -> reportModel.addSubReporter(recursiveTreeReportBuilder(treeReport)));

        return reportModel;
    }

    @Transactional(readOnly = true)
    public ReporterModel getReportElements(UUID reportId, Set<String> severityLevels, String taskKeyFilter) {
        Objects.requireNonNull(reportId);

        var report = new ReporterModel(reportId.toString(), reportId.toString());
        treeReportRepository.findAllByReportId(reportId)
            .stream()
            .filter(tre -> taskKeyFilter == null || taskKeyFilter.isEmpty() || tre.getName().startsWith(taskKeyFilter + "@")) // TODO later we should use exact matching, not starstWith
            .sorted((tre1, tre2) -> Long.signum(tre1.getNanos() - tre2.getNanos())) // using Long.signum (and not '<' ) to circumvent possible long overflow
            .forEach(treeReportEntity -> report.addSubReporter(getTreeReportAndDescendantElements(treeReportEntity, severityLevels)));
        return report;
    }

    @Transactional(readOnly = true)
    public ReporterModel getReporterElements(UUID reporterId, Set<String> severityLevels) {
        Objects.requireNonNull(reporterId);

        TreeReportEntity treeReportEntity = treeReportRepository.findById(reporterId).orElseThrow(EntityNotFoundException::new);

        var report = new ReporterModel(treeReportEntity.getIdNode().toString(), treeReportEntity.getIdNode().toString());
        report.addSubReporter(getTreeReportAndDescendantElements(treeReportEntity, severityLevels));
        return report;
    }

    private ReporterModel getTreeReportAndDescendantElements(TreeReportEntity treeReportEntity, Set<String> severityLevels) {
        Map<String, String> dict = treeReportEntity.getDictionary();

        // Let's find all the treeReportEntities ids that inherit from the parent treeReportEntity
        List<UUID> treeReportEntitiesIds = treeReportRepository.findAllTreeReportIdsRecursivelyByParentTreeReport(treeReportEntity.getIdNode())
                .stream()
                .map(UUID::fromString)
                .toList();

        // Let's find all the reportElements that are linked to the found treeReports
        Map<UUID, List<ReportElementEntity>> allReportElementsByParent = reportElementRepository.findAllByParentReportIdNodeIn(treeReportEntitiesIds)
            .stream()
            .filter(reportElementEntity -> reportElementEntity.hasSeverity(severityLevels))
            .collect(Collectors.groupingBy(reportElementEntity -> reportElementEntity.getParentReport().getIdNode()));

        // We need to get the entities to have access to the dictionaries
        List<TreeReportEntity> treeReportEntities = treeReportRepository.findAllByIdNodeIn(treeReportEntitiesIds);

        // Now we can rebuild the tree
        return buildTreeFromReportersAndElements(treeReportEntity, treeReportEntities, allReportElementsByParent);
    }

    private ReporterModel buildTreeFromReportersAndElements(TreeReportEntity reporter, List<TreeReportEntity> allTreeReports, Map<UUID, List<ReportElementEntity>> allReportElementsByParent) {
        // This ID is used by the front for direct access to the reporter
        reporter.getValues().add(new ReportValueEmbeddable("id", reporter.getIdNode(), "ID"));

        Map<String, String> dict = reporter.getDictionary();
        var reportModel = new ReporterModel(reporter.getName(), dict.get(reporter.getName()), toDtoValueMap(reporter.getValues()));

        // add treeReport elements
        allReportElementsByParent.getOrDefault(reporter.getIdNode(), List.of())
            .stream()
            .sorted((re1, re2) -> Long.signum(re1.getNanos() - re2.getNanos()))
            .forEach(report ->
                reportModel.report(report.getName(), dict.get(report.getName()), toDtoValueMap(report.getValues()))
            );
        // recursively add its sub-reporters
        allTreeReports
            .stream()
            .filter(sub -> sub.getParentReport() != null && sub.getParentReport().getIdNode() == reporter.getIdNode())
            .sorted((tre1, tre2) -> Long.signum(tre1.getNanos() - tre2.getNanos()))
            .forEach(treeReport -> reportModel.addSubReporter(buildTreeFromReportersAndElements(treeReport, allTreeReports, allReportElementsByParent)));
        return reportModel;
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
        newTreeReportEntity.getValues().add(new ReportValueEmbeddable("reporterSeverity", maxSeverity(reporterModel), "SEVERITY"));
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

    private static int getReportSeverity(Report report) {
        var severity = report.getValues().get("reportSeverity");
        return severity == null ? 0 : SEVERITY_LEVELS.getOrDefault(severity.toString(), 0);
    }

    private String maxSeverity(ReporterModel reporter) {
        int max = reporter.getReports().stream()
            .mapToInt(ReportService::getReportSeverity)
            .max().orElse(0);
        return SEVERITY_LEVELS.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(UNKNOWN_SEVERITY);
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
    public void deleteReport(UUID id) {
        Objects.requireNonNull(id);
        treeReportRepository.findIdNodeByReportId(id).forEach(r -> deleteRoot(r.getIdNode()));
        if (reportRepository.deleteReportById(id) == 0) {
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
