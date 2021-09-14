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
import org.gridsuite.report.server.entities.ReportElementEntity;
import org.gridsuite.report.server.entities.ReportEntity;
import org.gridsuite.report.server.entities.ReportValueEmbeddable;
import org.gridsuite.report.server.entities.TreeReportEntity;
import org.gridsuite.report.server.repositories.ReportElementRepository;
import org.gridsuite.report.server.repositories.ReportRepository;
import org.gridsuite.report.server.repositories.TreeReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Service
public class ReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final TreeReportRepository treeReportRepository;
    private final ReportElementRepository reportElementRepository;

    public ReportService(final ReportRepository reportRepository, TreeReportRepository treeReportRepository, ReportElementRepository reportElementRepository) {
        this.reportRepository = reportRepository;
        this.treeReportRepository = treeReportRepository;
        this.reportElementRepository = reportElementRepository;
    }

    List<ReporterModel> getReports() {
        return toDto(reportRepository.findAll());
    }

    List<ReporterModel> toDto(Collection<ReportEntity> reports) {
        return reports.stream().map(this::toDto).collect(Collectors.toList());
    }

    private ReporterModel toDto(ReportEntity element) {
        Map<String, String> dict = element.getDictionary();
        var report = new ReporterModel(element.getId().toString(), element.getId().toString());
        treeReportRepository.findAllByReportId(element.getId())
            .forEach(root -> report.addSubReporter(toDto(root, dict)));
        return report;
    }

    private Map<String, TypedValue> toDtoValueMap(List<ReportValueEmbeddable> values) {
        Map<String, TypedValue> res = new HashMap<>();
        values.forEach(value -> res.put(value.getName(), toDto(value)));
        return res;
    }

    private TypedValue toDto(ReportValueEmbeddable value) {
        switch (value.getValueType()) {
            case DOUBLE: return new TypedValue(Double.valueOf(value.getValue()), value.getType());
            case INTEGER: return new TypedValue(Integer.valueOf(value.getValue()), value.getType());
            default: return new TypedValue(value.getValue(), value.getType());
        }
    }

    private ReporterModel toDto(TreeReportEntity element, Map<String, String> dict) {
        var reportModel = new ReporterModel(element.getName(), dict.get(element.getName()), toDtoValueMap(element.getValues()));
        reportElementRepository.findAllByParentReportIdNode(element.getIdNode())
            .forEach(report -> reportModel.report(report.getName(), dict.get(report.getName()), toDtoValueMap(report.getValues())));
        treeReportRepository.findAllByParentReportIdNode(element.getIdNode())
            .forEach(report -> reportModel.addSubReporter(toDto(report, dict)));
        return reportModel;
    }

    ReporterModel getReport(UUID id) {
        Objects.requireNonNull(id);
        return toDto(reportRepository.getOne(id));
    }

    private ReportEntity toEntity(UUID id, ReporterModel reportElement) {
        var persistedReport = reportRepository.findById(id).orElseGet(() -> reportRepository.save(new ReportEntity(id, new HashMap<>())));
        toEntity(persistedReport, reportElement, null, persistedReport.getDictionary());
        return persistedReport;
    }

    private TreeReportEntity toEntity(ReportEntity persistedReport, ReporterModel reporterModel, TreeReportEntity parentNode, Map<String, String> dict) {
        dict.put(reporterModel.getTaskKey(), reporterModel.getDefaultName());
        var treeReportEntity = treeReportRepository.save(new TreeReportEntity(null, reporterModel.getTaskKey(), persistedReport,
                toValueEntityList(reporterModel.getTaskValues()), parentNode));
        reporterModel.getReports().forEach(report  -> toEntity(treeReportEntity, report, dict));
        reporterModel.getSubReporters().forEach(subReport -> toEntity(null, subReport, treeReportEntity, dict));
        return treeReportEntity;
    }

    private ReportElementEntity toEntity(TreeReportEntity parentReport, Report report, Map<String, String> dict) {
        dict.put(report.getReportKey(), report.getDefaultMessage());
        return reportElementRepository.save(new ReportElementEntity(null, parentReport, report.getReportKey(), toValueEntityList(report.getValues())));
    }

    private List<ReportValueEmbeddable> toValueEntityList(Map<String, TypedValue> values) {
        return values.entrySet().stream().map(this::toValueEmbeddable).collect(Collectors.toList());
    }

    private ReportValueEmbeddable toValueEmbeddable(Map.Entry<String, TypedValue> entryValue) {
        return new ReportValueEmbeddable(entryValue.getKey(), entryValue.getValue().getValue(), entryValue.getValue().getType());
    }

    @Transactional
    public void createReports(UUID id, ReporterModel report, boolean overwrite) {
        Optional<ReportEntity> reportEntity = reportRepository.findById(id);
        if (reportEntity.isPresent()) {
            LOGGER.debug("Report {} present, append ", report.getDefaultName());
            if (overwrite) {
                treeReportRepository.findAllByReportIdAndName(reportEntity.get().getId(), report.getTaskKey())
                    .forEach(r -> deleteRoot(r.getIdNode()));
            }
            toEntity(reportEntity.get(), report, null, reportEntity.get().getDictionary());
        } else {
            toEntity(id, report);
        }
    }

    private static UUID bytesToUUID(byte[] bytes) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(bytes);
        long high = bb.getLong();
        long low = bb.getLong();
        return new UUID(high, low);
    }

    private void deleteRoot(UUID id) {
        List<UUID> treeReport = treeReportRepository.getSubReportsNodes(id).stream().map(ReportService::bytesToUUID).collect(Collectors.toList());
        List<UUID> elements = reportElementRepository.findIdReportByParentReportIdNodeIn(treeReport)
            .stream().map(ReportElementEntity.ProjectionIdReport::getIdReport).collect(Collectors.toList());
        reportElementRepository.deleteAllByIdReportIn(elements);
        treeReportRepository.deleteAllByIdNodeIn(treeReport);
    }

    @Transactional
    public void deleteReport(UUID id) {
        Objects.requireNonNull(id);
        treeReportRepository.findIdNodeByReportId(id).forEach(r -> deleteRoot(r.getIdNode()));
        reportRepository.deleteById(id);
    }

    public void deleteAll() {
        reportRepository.deleteAll();
    }
}
