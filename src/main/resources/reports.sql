
    create table report (
       id uuid not null,
        primary key (id)
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
       ReportEntity_id uuid not null,
        dictionary varchar(255),
        dictionary_KEY varchar(255) not null,
        primary key (ReportEntity_id, dictionary_KEY)
    );

    create table treeReportEntity (
       idNode uuid not null,
        name varchar(255),
        report uuid,
        treeReportEntity_idNode uuid,
        primary key (idNode)
    );

    create table TreeReportEntity_values (
       TreeReportEntity_idNode uuid not null,
        name varchar(255),
        type varchar(255),
        value varchar(255),
        valueType int4
    );
create index reportEntity_reportId_idx on report (id);
create index reportEntity_dictionary_id_index on ReportEntity_dictionary (ReportEntity_id);
create index treeReport_idnode_idx on treeReportEntity (idNode);
create index treeReport_name_idx on treeReportEntity (name);
create index treeReport_repordId_idx on treeReportEntity (report);

    alter table if exists ReportElementEntity 
       add constraint reportElementEntity_idNode_fk 
       foreign key (treeReportEntity_idNode) 
       references treeReportEntity;

    alter table if exists ReportElementEntity_values 
       add constraint treeReportEmbeddable_subReports_fk 
       foreign key (ReportElementEntity_idReport) 
       references ReportElementEntity;

    alter table if exists ReportEntity_dictionary 
       add constraint reportEntity_dictionary_fk 
       foreign key (ReportEntity_id) 
       references report;

    alter table if exists treeReportEntity 
       add constraint report_id_fk_constraint 
       foreign key (report) 
       references report;

    alter table if exists treeReportEntity 
       add constraint treeReportEntity_idNode_fk 
       foreign key (treeReportEntity_idNode) 
       references treeReportEntity;

    alter table if exists TreeReportEntity_values 
       add constraint treeReportEmbeddable_name_fk 
       foreign key (TreeReportEntity_idNode) 
       references treeReportEntity;
