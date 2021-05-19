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
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.List;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "treeReport", indexes = @Index(name = "treeReport_idnode_idx", columnList = "idNode"))
public class TreeReportEntity {

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "idNode")
    UUID idNode;

    @Column(name = "name")
    String name;

    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "treeReportEmbeddable_name_fk"))
    List<ReportValueEmbeddable> values;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name  =  "treeReportEntity_idNode",
        referencedColumnName  =  "idNode",
        foreignKey = @ForeignKey(
            name = "reportElementEntity_idNode_fk"
        ), nullable = true)
    List<ReportElementEntity> reports;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name  =  "treeReportEntity_idNode",
        referencedColumnName  =  "idNode",
        foreignKey = @ForeignKey(
            name = "treeReportEntity_idNode_fk"
        ), nullable = true)
    List<TreeReportEntity> subReports;
}
