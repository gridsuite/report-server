package org.gridsuite.report.server.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.powsybl.commons.reporter.TypedValue;
import org.gridsuite.report.server.ReportService;
import org.gridsuite.report.server.entities.ReportValueEmbeddable.ValueType;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ReportElementProjection(
    @JsonProperty("id_node")
    UUID idReport,
    @JsonProperty("parent_report")
    UUID parentReportId,
    Long nanos,
    String name,
    List<ReportValueEmbeddable> values
) {
    public boolean hasSeverity(Set<String> severityLevels) {
        return CollectionUtils.isEmpty(severityLevels) || severityLevels.contains(values.stream()
                .filter(value -> value.getValueType() == ValueType.STRING && TypedValue.SEVERITY.equalsIgnoreCase(value.getType()))
                .findAny()
                .map(ReportValueEmbeddable::getValue)
                .orElse(ReportService.UNKNOWN_SEVERITY));
    }
}
