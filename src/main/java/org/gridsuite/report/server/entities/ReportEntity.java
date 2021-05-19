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
import javax.persistence.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "report", indexes = @Index(name = "reportEntity_reportId_idx", columnList = "reportId"))
public class ReportEntity {

    @Id
    @Column(name = "reportId")
    private UUID reportId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name  =  "reportEntity_reportId",
        referencedColumnName  =  "reportId",
        foreignKey = @ForeignKey(
            name = "treeReportEntity_reportId_fk"
        ), nullable = true)
    List<TreeReportEntity> roots;

    @Column(name = "dictionary")
    @CollectionTable(foreignKey = @ForeignKey(name = "reportEntity_dictionary_fk"), indexes = @Index(name = "reportEntity_dictionary_id_index", columnList = "reportEntity_reportId"))
    @ElementCollection
    Map<String, String> dictionary;

}
