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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.gridsuite.report.server.utils.UuidUtil;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Getter
@Setter
@Table(name = "report_node", indexes = {
    @Index(name = "root_node_orders_idx", columnList = "root_node_id, order_, end_order"),
    @Index(name = "root_node_and_container_idx", columnList = "root_node_id, is_leaf")
})
public class ReportNodeEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> {

    @Id
    @Builder.Default
    private UUID id = UuidUtil.generateV7();

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

    @Column(name = "root_node_id")
    private UUID rootNodeId;

    @Column(name = "parent_id")
    private UUID parentId;

    // Transient - not persisted, used by service test helper only
    @Transient
    @Builder.Default
    private List<ReportNodeEntity> children = new ArrayList<>();
}
