WITH
    root_reports AS (
        SELECT id
        FROM report_node
        WHERE
            message IS NULL
    ),
    child_reports AS (
        SELECT id, parent_id
        FROM report_node
        WHERE
            parent_id IN (
                SELECT id
                FROM root_reports
            )
    ),
    grandchild_reports AS (
        SELECT id, parent_id
        FROM report_node
        WHERE
            parent_id IN (
                SELECT id
                FROM child_reports
            )
    ),
    updated_root AS (
        -- Update root messages
        UPDATE report_node
        SET
            message = 'Root'
        WHERE
            id IN (
                SELECT id
                FROM root_reports
            )
        RETURNING
            id
    ),
    updated_severity AS (
        -- Move severities from child reports to root reports
        INSERT INTO
            severity (report_node_id, severity)
        SELECT cr.parent_id, s.severity
        FROM severity s
            JOIN child_reports cr ON s.report_node_id = cr.id
        RETURNING
            report_node_id
    ),
    deleted_severity AS (
        -- Delete severities for child reports
        DELETE FROM severity
        WHERE
            report_node_id IN (
                SELECT id
                FROM child_reports
            )
        RETURNING
            report_node_id
    ),
    updated_grandchild AS (
        -- Update grandchild reports to point to root reports
        UPDATE report_node
        SET
            parent_id = (
                SELECT parent_id
                FROM child_reports
                WHERE
                    child_reports.id = report_node.parent_id
            )
        WHERE
            id IN (
                SELECT id
                FROM grandchild_reports
            )
        RETURNING
            id
    )
    -- Delete child reports
DELETE FROM report_node
WHERE
    id IN (
        SELECT id
        FROM child_reports
    );