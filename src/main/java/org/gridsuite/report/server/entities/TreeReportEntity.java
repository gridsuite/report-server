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

import javax.persistence.CascadeType;
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
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "treeReport", indexes = {
    @Index(name = "treeReport_idnode_idx", columnList = "idNode"),
    @Index(name = "treeReport_name_idx", columnList = "name"),
    @Index(name = "treeReport_repordId_idx", columnList = "report"),
})
public class TreeReportEntity {

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "idNode")
    UUID idNode;

    @Column(name = "name")
    String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "report", foreignKey = @ForeignKey(name = "report_id_fk_constraint"))
    private ReportEntity report;

    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "treeReportEmbeddable_name_fk"),
        indexes = @Index(name = "treeReportEntity_value_ixd", columnList = "treereportentity_idNode"))
    List<ReportValueEmbeddable> values;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinTable(foreignKey = @ForeignKey(name = "treeReportEntity_ReportElementEntity_reportIdNode_fk"),
        indexes = @Index(name = "TreeReportEntity_report_idNode_idx",  columnList = "TreeReportEntity_idNode"))
    List<ReportElementEntity> reports;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinTable(foreignKey = @ForeignKey(name = "treeReportEntity_treeReportElementEntity_reportIdNode_fk"),
        indexes = @Index(name = "TreeReportEntity_treeReport_idNode_idx",  columnList = "TreeReportEntity_idNode"))
    List<TreeReportEntity> subReports;
}
