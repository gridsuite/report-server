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

    @Column(name = "nanos")
    private long nanos;

    @Column(name = "message_key")
    private String messageKey;

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

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "report_node_value",
        joinColumns = {@JoinColumn(name = "report_node_id")},
        foreignKey = @ForeignKey(name = "report_node_value_fk"),
        indexes = @Index(name = "report_node_value_idx", columnList = "report_node_id")
    )
    private List<ValueEntity> values;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "parent_fk"))
    private ReportNodeEntity parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    private List<ReportNodeEntity> children;

    public ReportNodeEntity(String messageKey, String message, long nanos, List<ValueEntity> values, ReportNodeEntity parent, Set<String> severities) {
        this.id = UUID.randomUUID();
        this.messageKey = messageKey;
        this.message = message;
        this.nanos = nanos;
        this.values = values;
        this.parent = parent;
        this.severities = severities;
    }

    public ReportNodeEntity(UUID id, long nanos) {
        this.id = id;
        this.nanos = nanos;
    }
}
