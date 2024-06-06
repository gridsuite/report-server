/**
 *  Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.entities;

import static com.powsybl.commons.report.TypedValue.SEVERITY;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.report.server.ReportService;
import org.gridsuite.report.server.entities.ReportValueEmbeddable.ValueType;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
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
    @GeneratedValue(strategy = GenerationType.AUTO)
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

    public boolean hasSeverity(@Nullable Set<String> severityLevels) {
        if (CollectionUtils.isEmpty(severityLevels)) {
            return false;
        } else {
            return severityLevels.contains(values.stream()
                    .filter(value -> value.getValueType() == ValueType.STRING && SEVERITY.equalsIgnoreCase(value.getType()))
                    .findAny()
                    .map(ReportValueEmbeddable::getValue)
                    .orElse(ReportService.SeverityLevel.UNKNOWN.name()));
        }
    }
}
