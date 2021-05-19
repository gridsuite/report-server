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
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Getter
@NoArgsConstructor
@Entity
public class ReportElementEntity {

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "idReport")
    UUID idReport;

    @Column(name = "name")
    String name;

    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "treeReportEmbeddable_subReports_fk"))
    List<ReportValueEmbeddable> values;

}
