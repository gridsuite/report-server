
    create table report (
       reportId uuid not null,
        primary key (reportId)
    );

    create table ReportElementEntity (
       idReport uuid not null,
        name varchar(255),
        treeReportEntity_idNode uuid,
        primary key (idReport)
    );

    create table ReportElementEntity_values (
       ReportElementEntity_idReport uuid not null,
        name varchar(255),
        type varchar(255),
        value varchar(255),
        valueType int4
    );

    create table ReportEntity_dictionary (
       ReportEntity_reportId uuid not null,
        dictionary varchar(255),
        dictionary_KEY varchar(255) not null,
        primary key (ReportEntity_reportId, dictionary_KEY)
    );

    create table treeReport (
       idNode uuid not null,
        name varchar(255),
        treeReportEntity_idNode uuid,
        reportEntity_reportId uuid,
        primary key (idNode)
    );

    create table TreeReportEntity_values (
       TreeReportEntity_idNode uuid not null,
        name varchar(255),
        type varchar(255),
        value varchar(255),
        valueType int4
    );
create index reportEntity_reportId_idx on report (reportId);
create index reportEntity_dictionary_id_index on ReportEntity_dictionary (ReportEntity_reportId);
create index treeReport_idnode_idx on treeReport (idNode);

    alter table if exists ReportElementEntity 
       add constraint reportElementEntity_idNode_fk 
       foreign key (treeReportEntity_idNode) 
       references treeReport;

    alter table if exists ReportElementEntity_values 
       add constraint treeReportEmbeddable_subReports_fk 
       foreign key (ReportElementEntity_idReport) 
       references ReportElementEntity;

    alter table if exists ReportEntity_dictionary 
       add constraint reportEntity_dictionary_fk 
       foreign key (ReportEntity_reportId) 
       references report;

    alter table if exists treeReport 
       add constraint treeReportEntity_idNode_fk 
       foreign key (treeReportEntity_idNode) 
       references treeReport;

    alter table if exists treeReport 
       add constraint treeReportEntity_reportId_fk 
       foreign key (reportEntity_reportId) 
       references report;

    alter table if exists TreeReportEntity_values 
       add constraint treeReportEmbeddable_name_fk 
       foreign key (TreeReportEntity_idNode) 
       references treeReport;
