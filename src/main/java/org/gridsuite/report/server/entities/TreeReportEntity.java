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

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "treeReport", indexes = {
    @Index(name = "tree_report_idnode_idx", columnList = "idNode"),
    @Index(name = "tree_report_name_idx", columnList = "name"),
    @Index(name = "tree_report_repordId_idx", columnList = "report"),
    @Index(name = "tree_report_parentReport_idx", columnList = "parentReport"),
})
public class TreeReportEntity {

    public interface ProjectionIdNode {
        UUID getIdNode();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "idNode", columnDefinition = "uuid")
    UUID idNode;

    @Column(name = "name")
    String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report", foreignKey = @ForeignKey(name = "report_id_fk_constraint"))
    private ReportEntity report;

    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "treeReportEmbeddable_name_fk"),
        indexes = @Index(name = "treeReportEntity_value_ixd", columnList = "tree_report_entity_id_node"))
    List<ReportValueEmbeddable> values;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentReport", foreignKey = @ForeignKey(name = "treeReport_id_fk_constraint"))
    TreeReportEntity parentReport;

    @Column(name = "dictionary", length = 500)
    @CollectionTable(foreignKey = @ForeignKey(name = "treeReportEntity_dictionary_fk"), indexes = @Index(name = "treeReportEntity_dictionary_idNode_index", columnList = "tree_report_entity_id_node"))
    @ElementCollection
    Map<String, String> dictionary;

    @Column
    long nanos;
}
