/**
 *  Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Getter
@NoArgsConstructor
@Embeddable
public class ReportValueEmbeddable {

    public ReportValueEmbeddable(String key, Object value, String type) {

        this.name = key;
        this.value = value.toString();
        this.type = type;
        if (value instanceof Double || value instanceof Float) {
            this.valueType = ValueType.DOUBLE;
        } else if (value instanceof Number) {
            this.valueType = ValueType.INTEGER;
        } else {
            this.valueType = ValueType.STRING;
        }
    }

    public enum ValueType {
        DOUBLE,
        INTEGER,
        STRING
    }

    String name;
    @Column(columnDefinition = "TEXT")
    String value;
    String type;
    ValueType valueType;
}
