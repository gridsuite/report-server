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

import jakarta.persistence.*;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "report", indexes = @Index(name = "reportEntity_reportId_idx", columnList = "id"))
public class ReportEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;
}
