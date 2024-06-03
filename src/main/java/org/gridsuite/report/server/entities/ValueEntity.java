/**
 *  Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Embeddable
public class ValueEntity {
    @Column(name = "key_", nullable = false)
    private String key;

    @Column(name = "value_", nullable = false)
    private String value;

    @Column(name = "value_type")
    private String valueType;

    public ValueEntity(String key, String value, String valueType) {
        this.key = key;
        this.value = value;
        this.valueType = valueType;
    }
}
