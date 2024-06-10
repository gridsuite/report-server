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

    @Column(name = "value_", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "value_type")
    private String valueType;

    @Column(name = "local_value_type")
    private ValueType localValueType;

    public ValueEntity(String key, Object value, String valueType) {
        this.key = key;
        this.value = value.toString();
        this.valueType = valueType;
        if (value instanceof Double || value instanceof Float) {
            this.localValueType = ValueType.DOUBLE;
        } else if (value instanceof Number) {
            this.localValueType = ValueType.INTEGER;
        } else {
            this.localValueType = ValueType.STRING;
        }
    }
}
