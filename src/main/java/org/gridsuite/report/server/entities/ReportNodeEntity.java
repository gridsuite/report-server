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
import java.util.UUID;

@NoArgsConstructor
@Entity
@Getter
@Setter
@Table(name = "report_node", indexes = {
    @Index(name = "report_node_parent_id_idx", columnList = "parent_id"),
    @Index(name = "root_node_orders_idx", columnList = "root_node_id, order_, end_order"),
    @Index(name = "root_node_and_container_idx", columnList = "root_node_id, is_leaf")
})
public class ReportNodeEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> {

    @Id
    private UUID id;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_node_id", foreignKey = @ForeignKey(name = "root_node_fk"))
    private ReportNodeEntity rootNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "parent_fk"))
    private ReportNodeEntity parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    private List<ReportNodeEntity> children;

    public ReportNodeEntity(String message, int order, int endOrder, boolean isLeaf, ReportNodeEntity rootNode, ReportNodeEntity parent, String severity) {
        this.id = UUID.randomUUID();
        this.message = message;
        this.order = order;
        this.endOrder = endOrder;
        this.isLeaf = isLeaf;
        this.rootNode = rootNode;
        this.parent = parent;
        this.severity = severity;
    }

    public ReportNodeEntity(UUID id, String message, int order, int endOrder, boolean isLeaf, ReportNodeEntity rootNode, ReportNodeEntity parent, String severity) {
        this.id = id;
        this.message = message;
        this.order = order;
        this.endOrder = endOrder;
        this.isLeaf = isLeaf;
        this.rootNode = rootNode;
        this.parent = parent;
        this.severity = severity;
    }
}
