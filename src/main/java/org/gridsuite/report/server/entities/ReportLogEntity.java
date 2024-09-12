/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@NoArgsConstructor
@Entity
@Getter
@Setter
@Table(name = "report_node", indexes = {
    @Index(name = "report_node_parent_id_idx", columnList = "parent_id")
})
public class ReportLogEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "severity",
        joinColumns = {@JoinColumn(name = "report_node_id")},
        foreignKey = @ForeignKey(name = "report_node_severity_fk"),
        indexes = @Index(name = "report_node_severity_idx", columnList = "report_node_id")
    )
    @Column(name = "severity")
    private Set<String> severities;

    @Column(name = "parent_id")
    private UUID parentId;

}
