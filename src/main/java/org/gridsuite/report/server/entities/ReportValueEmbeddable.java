package org.gridsuite.report.server.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Embeddable;

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
    String value;
    String type;
    ValueType valueType;
}
