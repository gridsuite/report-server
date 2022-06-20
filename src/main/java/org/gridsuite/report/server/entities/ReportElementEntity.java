/**
 *  Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reportElement", indexes = {@Index(name = "reportElementEntity_idReport", columnList = "idReport"),
    @Index(name = "reportElementEntity_parentReport", columnList = "parentReport")
})
public class ReportElementEntity {

    public interface ProjectionIdReport {
        UUID getIdReport();
    }

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "idReport", columnDefinition = "uuid")
    UUID idReport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentReport", foreignKey = @ForeignKey(name = "treeReportElement_id_fk_constraint"))
    TreeReportEntity parentReport;

    @Column
    long nanos;

    @Column(name = "name")
    String name;

    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "treeReportEmbeddable_subReports_fk"),
        indexes = @Index(name = "reportElementValues_index", columnList = "report_element_entity_id_report"))
    List<ReportValueEmbeddable> values;

}
