package org.gridsuite.report.server.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Getter
@NoArgsConstructor
@Entity
@Table(name = "reportElement", indexes = @Index(name = "reportElementEntity_idReport", columnList = "idReport"))
public class ReportElementEntity {

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "idReport")
    UUID idReport;

    @Column(name = "name")
    String name;

    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "treeReportEmbeddable_subReports_fk"),
        indexes = @Index(name = "reportElement_values_index", columnList = "reportElementEntity_idReport"))
    List<ReportValueEmbeddable> values;

}
