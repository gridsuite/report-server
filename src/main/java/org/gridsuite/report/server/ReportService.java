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
import org.gridsuite.report.server.repositories.ReportRepository;
import org.gridsuite.report.server.repositories.TreeReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public ReportService(final ReportRepository reportRepository, TreeReportRepository treeReportRepository) {
        this.reportRepository = reportRepository;
        this.treeReportRepository = treeReportRepository;
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
        treeReportRepository.findAllRootsByReportId(element.getId())
            .forEach(root -> report.addSubReporter(toDto(root, dict)));
        return report;
    }

    private Map<String, TypedValue>  toDtoValueMap(List<ReportValueEmbeddable> values) {
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
        element.getReports().forEach(report -> reportModel.report(report.getName(), dict.get(report.getName()), toDtoValueMap(report.getValues())));
        element.getSubReports().forEach(report -> reportModel.addSubReporter(toDto(report, dict)));
        return reportModel;
    }

    ReporterModel getReport(UUID id) {
        Objects.requireNonNull(id);
        return toDto(reportRepository.getOne(id));
    }

    private ReportEntity toEntity(UUID id, ReporterModel reportElement) {
        var persistedReport = reportRepository.findById(id).orElseGet(() -> reportRepository.save(new ReportEntity(id, new HashMap<>())));
        treeReportRepository.save(toEntity(persistedReport, reportElement, persistedReport.getDictionary()));
        return persistedReport;
    }

    private TreeReportEntity toEntity(ReportEntity persistedReport, ReporterModel reporterModel, Map<String, String> dict) {
        dict.put(reporterModel.getTaskKey(), reporterModel.getDefaultName());
        return new TreeReportEntity(null, reporterModel.getTaskKey(), persistedReport,
            toValueEntityList(reporterModel.getTaskValues()),
            reporterModel.getReports().stream().map(report  -> toEntity(report, dict)).collect(Collectors.toList()),
            reporterModel.getSubReporters().stream().map(subReport -> toEntity(null, subReport, dict)).collect(Collectors.toList()));
    }

    private ReportElementEntity toEntity(Report report, Map<String, String> dict) {
        dict.put(report.getReportKey(), report.getDefaultMessage());
        return new ReportElementEntity(null, report.getReportKey(), toValueEntityList(report.getValues()));
    }

    private List<ReportValueEmbeddable> toValueEntityList(Map<String, TypedValue> values) {
        return values.entrySet().stream().map(this::toValueEmbeddable).collect(Collectors.toList());
    }

    private ReportValueEmbeddable toValueEmbeddable(Map.Entry<String, TypedValue> entryValue) {
        return new ReportValueEmbeddable(entryValue.getKey(), entryValue.getValue().getValue(), entryValue.getValue().getType());
    }

    public void createReports(UUID id, ReporterModel report, boolean overwrite) {
        Optional<ReportEntity> reportEntity = reportRepository.findById(id);
        if (reportEntity.isPresent()) {
            LOGGER.debug("Report {} present, append ", report.getDefaultName());
            if (overwrite) {
                treeReportRepository.deleteByReportIdAndName(reportEntity.get().getId(), report.getTaskKey());
            }
            treeReportRepository.save(toEntity(reportEntity.get(), report, reportEntity.get().getDictionary()));
        } else {
            toEntity(id, report);
        }
    }

    public void deleteReport(UUID id) {
        Objects.requireNonNull(id);
        treeReportRepository.deleteByReportId(id);
        reportRepository.deleteById(id);
    }

    public void deleteAll() {
        reportRepository.deleteAll();
    }
}
