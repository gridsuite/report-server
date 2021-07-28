/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.google.common.collect.Lists;
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

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;
import java.util.function.Consumer;
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

    class CacheEntities {
        List<TreeReportEntity> tre = new ArrayList<>();
        List<ReportEntity> re = new ArrayList<>();
        List<ReportElementEntity> ree = new ArrayList<>();

        TreeReportEntity cache(TreeReportEntity t) {
            tre.add(t);
            return t;
        }

        ReportEntity cache(ReportEntity r) {
            re.add(r);
            return r;
        }

        ReportElementEntity cache(ReportElementEntity r) {
            ree.add(r);
            return r;
        }

        void commitInsert() {

            partitionRun(re, reportRepository::saveAll);
            partitionRun(tre, treeReportRepository::saveAll);
            partitionRun(ree, reportElementRepository::saveAll);

/*
            re.forEach(entityManager::persist);
            tre.forEach(entityManager::persist);
            ree.forEach(entityManager::persist);
*/
            tre.clear();
            re.clear();
            ree.clear();
        }
    }

    private Map<String, TypedValue> toDtoValueMap(List<ReportValueEmbeddable> values) {
        Map<String, TypedValue> res = new HashMap<>();
        values.forEach(value -> res.put(value.getName(), toDto(value)));
        return res;
    }

    private TypedValue toDto(ReportValueEmbeddable value) {
        switch (value.getValueType()) {
            case DOUBLE:
                return new TypedValue(Double.valueOf(value.getValue()), value.getType());
            case INTEGER:
                return new TypedValue(Integer.valueOf(value.getValue()), value.getType());
            default:
                return new TypedValue(value.getValue(), value.getType());
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

    private ReportEntity toEntity(UUID id, ReporterModel reportElement, CacheEntities em) {
        var persistedReport = reportRepository.findById(id).orElseGet(() -> em.cache(new ReportEntity(id, new HashMap<>())));
        em.cache(toEntity(persistedReport, reportElement, null, persistedReport.getDictionary(), em));
        return persistedReport;
    }

    private TreeReportEntity toEntity(ReportEntity persistedReport, ReporterModel reporterModel, TreeReportEntity parentNode, Map<String, String> dict, CacheEntities em) {
        dict.put(reporterModel.getTaskKey(), reporterModel.getDefaultName());

        var treeReportEntity = new TreeReportEntity(null, reporterModel.getTaskKey(), persistedReport,
            toValueEntityList(reporterModel.getTaskValues()), parentNode);
        em.cache(treeReportEntity);
        reporterModel.getSubReporters()
            .forEach(report -> em.cache(toEntity(null, report, treeReportEntity, dict, em)));
        reporterModel.getReports()
            .forEach(report -> em.cache(toEntity(treeReportEntity, report, dict)));
        return treeReportEntity;
    }

    private ReportElementEntity toEntity(TreeReportEntity parentReport, Report report, Map<String, String> dict) {
        dict.put(report.getReportKey(), report.getDefaultMessage());
        return new ReportElementEntity(null, parentReport, report.getReportKey(), toValueEntityList(report.getValues()));
    }

    private List<ReportValueEmbeddable> toValueEntityList(Map<String, TypedValue> values) {
        return values.entrySet().stream().map(this::toValueEmbeddable).collect(Collectors.toList());
    }

    private ReportValueEmbeddable toValueEmbeddable(Map.Entry<String, TypedValue> entryValue) {
        return new ReportValueEmbeddable(entryValue.getKey(), entryValue.getValue().getValue(), entryValue.getValue().getType());
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void createReports(UUID id, ReporterModel report, boolean overwrite) {
        CacheEntities em = new CacheEntities();
        long start = System.currentTimeMillis();
        Optional<ReportEntity> reportEntity = reportRepository.findById(id);
        if (reportEntity.isPresent()) {
            LOGGER.debug("Report {} present, append ", report.getDefaultName());
            if (overwrite) {
                treeReportRepository.findAllByReportIdAndName(reportEntity.get().getId(), report.getTaskKey())
                    .forEach(r -> deleteRoot(r.getIdNode()));
            }
            em.cache(toEntity(reportEntity.get(), report, null, reportEntity.get().getDictionary(), em));
        } else {
            toEntity(id, report, em);
        }
        LOGGER.info("end " + (System.currentTimeMillis() - start));
        em.commitInsert();
        LOGGER.info("commit " + (System.currentTimeMillis() - start));
    }

    <E> void partitionRun(List<E> elements, Consumer<List<E>> function) {
        for (List<E> slice : Lists.partition(elements, 100)) {
            function.accept(slice);
        }
    }

    private void deleteRoot(UUID id) {
        List<String> treeReport = treeReportRepository.getSubReportsNodes(id);
        LOGGER.info("get all subreports" + treeReport.size());
        List<String> elements = new ArrayList<>();
        partitionRun(treeReport, e ->
            elements.addAll(reportElementRepository.getNodesIdForReportNative(e.stream().map(UUID::fromString).collect(Collectors.toSet()))));
        LOGGER.info("get all reports leafs" + elements.size());
        partitionRun(elements, e ->
            reportElementRepository.deleteAllById(e.stream().map(UUID::fromString).collect(Collectors.toList())));
        LOGGER.info("deleted all reports leafs" + elements.size());
        partitionRun(treeReport, e ->
            treeReportRepository.deleteAllById(e.stream().map(UUID::fromString).collect(Collectors.toList())));
        LOGGER.info("deleted all subreports" + treeReport.size());
    }

    @Transactional
    public void deleteReport(UUID id) {
        Objects.requireNonNull(id);
        treeReportRepository.getIdNodesByParentReportId(id).forEach(r -> deleteRoot(UUID.fromString(r)));
        reportRepository.deleteById(id);
    }

    public void deleteAll() {
        reportRepository.deleteAll();
    }
}
