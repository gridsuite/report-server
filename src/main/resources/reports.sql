
    create table report (
       id uuid not null,
        primary key (id)
    );

    create table ReportElementEntity (
       idReport uuid not null,
        name varchar(255),
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
        primary key (idNode)
    );

    create table treeReportEntity_ReportElementEntity (
       TreeReportEntity_idNode uuid not null,
        reports_idReport uuid not null
    );

    create table treeReportEntity_treeReportEntity (
       TreeReportEntity_idNode uuid not null,
        subReports_idNode uuid not null
    );

    create table TreeReportEntity_values (
       TreeReportEntity_idNode uuid not null,
        name varchar(255),
        type varchar(255),
        value varchar(255),
        valueType int4
    );
create index reportEntity_reportId_idx on report (id);
create index reportElementEntity_idReport on ReportElementEntity (idReport);
create index reportElement_values_index on ReportElementEntity_values (ReportElementEntity_idReport);
create index reportEntity_dictionary_id_index on ReportEntity_dictionary (ReportEntity_id);
create index treeReport_idnode_idx on treeReportEntity (idNode);
create index treeReport_name_idx on treeReportEntity (name);
create index treeReport_repordId_idx on treeReportEntity (report);
create index TreeReportEntity_report_idNode_idx on treeReportEntity_ReportElementEntity (TreeReportEntity_idNode);

    alter table if exists treeReportEntity_ReportElementEntity 
       add constraint UK_dulya1fd4k9yom1yfl158b8k2 unique (reports_idReport);
create index TreeReportEntity_treeReport_idNode_idx on treeReportEntity_treeReportEntity (TreeReportEntity_idNode);

    alter table if exists treeReportEntity_treeReportEntity 
       add constraint UK_sv7yxs1ni1cghjnbykcq5xd21 unique (subReports_idNode);
create index treeReportEntity_value_ixd on TreeReportEntity_values (TreeReportEntity_idNode);

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

    alter table if exists treeReportEntity_ReportElementEntity 
       add constraint FKf9v3guvbjv4p12hvipn376cxh 
       foreign key (reports_idReport) 
       references ReportElementEntity;

    alter table if exists treeReportEntity_ReportElementEntity 
       add constraint treeReportEntity_ReportElementEntity_reportIdNode_fk 
       foreign key (TreeReportEntity_idNode) 
       references treeReportEntity;

    alter table if exists treeReportEntity_treeReportEntity 
       add constraint FKe40ip74ydx1g3n0hg319d4tsk 
       foreign key (subReports_idNode) 
       references treeReportEntity;

    alter table if exists treeReportEntity_treeReportEntity 
       add constraint treeReportEntity_treeReportElementEntity_reportIdNode_fk 
       foreign key (TreeReportEntity_idNode) 
       references treeReportEntity;

    alter table if exists TreeReportEntity_values 
       add constraint treeReportEmbeddable_name_fk 
       foreign key (TreeReportEntity_idNode) 
       references treeReportEntity;
