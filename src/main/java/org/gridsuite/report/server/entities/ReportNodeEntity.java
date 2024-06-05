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

import java.util.List;
import java.util.Set;
import java.util.UUID;

@NoArgsConstructor
@Entity
@Getter
@Setter
@Table(name = "report_node")
public class ReportNodeEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> {

    @Id
    private UUID id;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "severity_level",
        joinColumns = {@JoinColumn(name = "report_node_id")},
        foreignKey = @ForeignKey(name = "report_node_severity_fk"),
        indexes = @Index(name = "report_node_severity_idx", columnList = "report_node_id")
    )
    @Column(name = "severities")
    private Set<String> severities;

    @Column
    long nanos;

    @ManyToOne(cascade = CascadeType.ALL)
    private MessageTemplateEntity messageTemplate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "report_node_values",
        joinColumns = {@JoinColumn(name = "report_node_id")},
        foreignKey = @ForeignKey(name = "report_node_value_fk"),
        indexes = @Index(name = "report_node_value_idx", columnList = "report_node_id")
    )
    private List<ValueEntity> values;

    @ManyToOne(fetch = FetchType.LAZY)
    private ReportNodeEntity parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, orphanRemoval = true, cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
    private List<ReportNodeEntity> children;

    public ReportNodeEntity(long nanos, MessageTemplateEntity messageTemplate, List<ValueEntity> values, ReportNodeEntity parent, Set<String> severities) {
        this.id = UUID.randomUUID();
        this.nanos = nanos;
        this.messageTemplate = messageTemplate;
        this.values = values;
        this.parent = parent;
        this.severities = severities;
    }

    public ReportNodeEntity(UUID id, long nanos) {
        this.id = id;
        this.nanos = nanos;
    }
}
