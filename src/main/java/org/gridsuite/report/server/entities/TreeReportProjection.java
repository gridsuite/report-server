package org.gridsuite.report.server.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class TreeReportProjection {
    @JsonProperty("id_node")
    private UUID idNode;
    @JsonProperty("parent_report")
    private UUID parentIdNode;
    private UUID report;
    private String name;
    private Long nanos;
    private Map<String, String> dictionary;
    private List<ReportValueEmbeddable> values;
}
