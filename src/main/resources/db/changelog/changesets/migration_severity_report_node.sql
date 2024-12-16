WITH ranked_severities AS (
    SELECT
        s.report_node_id,
        s.severity,
        RANK() OVER (
            PARTITION BY s.report_node_id
            ORDER BY
                CASE s.severity
                    WHEN 'FATAL' THEN 1
                    WHEN 'ERROR' THEN 2
                    WHEN 'WARN' THEN 3
                    WHEN 'INFO' THEN 4
                    WHEN 'DEBUG' THEN 5
                    WHEN 'TRACE' THEN 6
                END
        ) AS rank
    FROM severity s
)
UPDATE report_node
SET severity = ranked_severities.severity
    FROM ranked_severities
WHERE id = ranked_severities.report_node_id AND ranked_severities.rank = 1;
