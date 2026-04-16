/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Getter
@Setter
@IdClass(ReportNodeId.class)
@Table(name = "report_node", indexes = {
    @Index(name = "report_node_container_idx", columnList = "id, is_leaf")
})
public class ReportNodeEntity extends AbstractManuallyAssignedIdentifierEntity<ReportNodeId> {

    @Id
    @Getter(AccessLevel.NONE)
    private UUID id;

    @Id
    @Column(name = "order_")
    private int order;

    @Column(name = "end_order")
    private int endOrder;

    @Column(name = "is_leaf")
    private boolean isLeaf;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "severity")
    private String severity;

    @Column(name = "depth", columnDefinition = "integer default 0")
    private int depth;

    @Column(name = "parent_order")
    private Integer parentOrder;

    public UUID getReportId() {
        return id;
    }

    @Override
    public ReportNodeId getId() {
        return new ReportNodeId(id, order);
    }
}
