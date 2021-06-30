
    create table report (
       id uuid not null,
        primary key (id)
    );

    create table reportElement (
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

    create table treeReport (
       idNode uuid not null,
        name varchar(255),
        report uuid,
        primary key (idNode)
    );

    create table treeReport_reportElement (
       TreeReportEntity_idNode uuid not null,
        reports_idReport uuid not null
    );

    create table treeReport_treeReport (
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
create index reportElementEntity_idReport on reportElement (idReport);
create index reportElement_values_index on ReportElementEntity_values (ReportElementEntity_idReport);
create index reportEntity_dictionary_id_index on ReportEntity_dictionary (ReportEntity_id);
create index treeReport_idnode_idx on treeReport (idNode);
create index treeReport_name_idx on treeReport (name);
create index treeReport_repordId_idx on treeReport (report);
create index TreeReportEntity_report_idNode_idx on treeReport_reportElement (TreeReportEntity_idNode);

    alter table if exists treeReport_reportElement 
       add constraint UK_j9ate6j6h54wf1dv19k78na1e unique (reports_idReport);
create index TreeReportEntity_treeReport_idNode_idx on treeReport_treeReport (TreeReportEntity_idNode);

    alter table if exists treeReport_treeReport 
       add constraint UK_1npql8ml7fbdm725xxix6wgg unique (subReports_idNode);
create index treeReportEntity_value_ixd on TreeReportEntity_values (TreeReportEntity_idNode);

    alter table if exists ReportElementEntity_values 
       add constraint treeReportEmbeddable_subReports_fk 
       foreign key (ReportElementEntity_idReport) 
       references reportElement;

    alter table if exists ReportEntity_dictionary 
       add constraint reportEntity_dictionary_fk 
       foreign key (ReportEntity_id) 
       references report;

    alter table if exists treeReport 
       add constraint report_id_fk_constraint 
       foreign key (report) 
       references report;

    alter table if exists treeReport_reportElement 
       add constraint FKbog4enyesrpy92vmnmilmyogw 
       foreign key (reports_idReport) 
       references reportElement;

    alter table if exists treeReport_reportElement 
       add constraint treeReportEntity_ReportElementEntity_reportIdNode_fk 
       foreign key (TreeReportEntity_idNode) 
       references treeReport;

    alter table if exists treeReport_treeReport 
       add constraint FKqugvir2upkol001tv1vsur2vb 
       foreign key (subReports_idNode) 
       references treeReport;

    alter table if exists treeReport_treeReport 
       add constraint treeReportEntity_treeReportElementEntity_reportIdNode_fk 
       foreign key (TreeReportEntity_idNode) 
       references treeReport;

    alter table if exists TreeReportEntity_values 
       add constraint treeReportEmbeddable_name_fk 
       foreign key (TreeReportEntity_idNode) 
       references treeReport;
