package org.gridsuite.report.server.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TreeReportProjection(
    @JsonProperty("id_node")
    UUID idNode,
    @JsonProperty("parent_report")
    UUID parentIdNode,
    UUID report,
    String name,
    Long nanos,
    Map<String, String> dictionary,
    List<ReportValueEmbeddable> values
) {
}
