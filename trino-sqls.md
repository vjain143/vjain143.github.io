Below are copy/paste-ready SQLs (MySQL/MariaDB style) you can use to power executive panels from your trino_queries table.

Assumptions:
	•	You have a timestamp column somewhere (you didn’t show one). Exec dashboards need time.
If you don’t have it, add it (recommended):

ALTER TABLE trino_queries
  ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN finished_at TIMESTAMP NULL,
  ADD INDEX idx_created_at (created_at),
  ADD INDEX idx_state_created (query_state, created_at),
  ADD INDEX idx_user_created (`user`, created_at),
  ADD INDEX idx_catalog_schema_created (catalog, `schema`, created_at);

If you already have created_at/finished_at, just use yours.

⸻

1) Executive KPI Cards (last 24h / 7d)

1A. Core KPI cards (Queries, Success rate, Active users, P95 latency)

SELECT
  COUNT(*) AS total_queries,
  SUM(query_state = 'FINISHED') AS finished_queries,
  ROUND(100 * SUM(query_state = 'FINISHED') / NULLIF(COUNT(*), 0), 2) AS success_rate_pct,
  COUNT(DISTINCT `user`) AS active_users,
  ROUND(AVG(wall_time_millis) / 1000, 2) AS avg_wall_seconds,
  ROUND(
    (SELECT wall_time_millis
     FROM trino_queries q2
     WHERE q2.created_at >= NOW() - INTERVAL 7 DAY
     ORDER BY wall_time_millis
     LIMIT 1 OFFSET FLOOR(0.95 * (SELECT COUNT(*) FROM trino_queries q3 WHERE q3.created_at >= NOW() - INTERVAL 7 DAY))
    ) / 1000, 2
  ) AS p95_wall_seconds
FROM trino_queries
WHERE created_at >= NOW() - INTERVAL 7 DAY;

Note: MySQL doesn’t have a native percentile_cont in many versions; the OFFSET trick works but can be heavy. For dashboards, consider pre-aggregating (see section 8).

1B. Week-over-week growth (queries + active users)

SELECT
  this_week.total_queries AS this_week_queries,
  last_week.total_queries AS last_week_queries,
  ROUND(100 * (this_week.total_queries - last_week.total_queries) / NULLIF(last_week.total_queries, 0), 2) AS wow_queries_pct,
  this_week.active_users AS this_week_users,
  last_week.active_users AS last_week_users,
  ROUND(100 * (this_week.active_users - last_week.active_users) / NULLIF(last_week.active_users, 0), 2) AS wow_users_pct
FROM
  (SELECT COUNT(*) total_queries, COUNT(DISTINCT `user`) active_users
   FROM trino_queries
   WHERE created_at >= CURDATE() - INTERVAL 7 DAY) this_week
CROSS JOIN
  (SELECT COUNT(*) total_queries, COUNT(DISTINCT `user`) active_users
   FROM trino_queries
   WHERE created_at >= CURDATE() - INTERVAL 14 DAY
     AND created_at <  CURDATE() - INTERVAL 7 DAY) last_week;


⸻

2) Adoption & Usage Trends

2A. Daily queries + active users trend (time series)

SELECT
  DATE(created_at) AS day,
  COUNT(*) AS queries,
  COUNT(DISTINCT `user`) AS users
FROM trino_queries
WHERE created_at >= CURDATE() - INTERVAL 30 DAY
GROUP BY DATE(created_at)
ORDER BY day;

2B. Top teams/domains by source (or resource group)

SELECT
  COALESCE(source, 'unknown') AS source,
  COUNT(*) AS queries,
  COUNT(DISTINCT `user`) AS users
FROM trino_queries
WHERE created_at >= NOW() - INTERVAL 30 DAY
GROUP BY COALESCE(source, 'unknown')
ORDER BY queries DESC
LIMIT 20;

2C. Top catalogs/schemas driving platform usage

SELECT
  COALESCE(catalog, 'unknown') AS catalog,
  COALESCE(`schema`, 'unknown') AS schema_name,
  COUNT(*) AS queries,
  COUNT(DISTINCT `user`) AS users
FROM trino_queries
WHERE created_at >= NOW() - INTERVAL 30 DAY
GROUP BY COALESCE(catalog, 'unknown'), COALESCE(`schema`, 'unknown')
ORDER BY queries DESC
LIMIT 30;


⸻

3) Reliability & SLA

3A. Success / failure rates by day

SELECT
  DATE(created_at) AS day,
  COUNT(*) AS total,
  SUM(query_state = 'FINISHED') AS finished,
  SUM(query_state IN ('FAILED')) AS failed,
  SUM(query_state IN ('CANCELED')) AS canceled,
  ROUND(100 * SUM(query_state = 'FINISHED') / NULLIF(COUNT(*),0), 2) AS success_pct
FROM trino_queries
WHERE created_at >= CURDATE() - INTERVAL 30 DAY
GROUP BY DATE(created_at)
ORDER BY day;

3B. Failure reasons (error_type / error_code / failure_type)

SELECT
  COALESCE(error_type, 'unknown') AS error_type,
  COALESCE(failure_type, 'unknown') AS failure_type,
  COALESCE(error_code, 'unknown') AS error_code,
  COUNT(*) AS failures
FROM trino_queries
WHERE created_at >= NOW() - INTERVAL 30 DAY
  AND query_state = 'FAILED'
GROUP BY COALESCE(error_type, 'unknown'), COALESCE(failure_type, 'unknown'), COALESCE(error_code, 'unknown')
ORDER BY failures DESC
LIMIT 30;

3C. Queueing pain (p95 queued time over time)

SELECT
  DATE(created_at) AS day,
  ROUND(AVG(queued_time_millis) / 1000, 2) AS avg_queue_s,
  ROUND(MAX(queued_time_millis) / 1000, 2) AS max_queue_s
FROM trino_queries
WHERE created_at >= CURDATE() - INTERVAL 30 DAY
GROUP BY DATE(created_at)
ORDER BY day;


⸻

4) Cost & Efficiency (best exec panel set)

4A. Total “compute seconds” trend (proxy cost = cpu_time_millis)

SELECT
  DATE(created_at) AS day,
  ROUND(SUM(cpu_time_millis) / 1000, 2) AS cpu_seconds,
  ROUND(SUM(wall_time_millis) / 1000, 2) AS wall_seconds,
  COUNT(*) AS queries
FROM trino_queries
WHERE created_at >= CURDATE() - INTERVAL 30 DAY
GROUP BY DATE(created_at)
ORDER BY day;

4B. Cost by team/source (top 20)

SELECT
  COALESCE(source, 'unknown') AS source,
  ROUND(SUM(cpu_time_millis) / 1000, 2) AS cpu_seconds,
  COUNT(*) AS queries,
  ROUND((SUM(cpu_time_millis) / 1000) / NULLIF(COUNT(*),0), 2) AS cpu_seconds_per_query
FROM trino_queries
WHERE created_at >= NOW() - INTERVAL 30 DAY
GROUP BY COALESCE(source, 'unknown')
ORDER BY cpu_seconds DESC
LIMIT 20;

4C. “Waste” panel: failed + canceled CPU burn

SELECT
  DATE(created_at) AS day,
  ROUND(SUM(CASE WHEN query_state IN ('FAILED','CANCELED') THEN cpu_time_millis ELSE 0 END) / 1000, 2) AS wasted_cpu_seconds,
  ROUND(100 * SUM(CASE WHEN query_state IN ('FAILED','CANCELED') THEN cpu_time_millis ELSE 0 END)
            / NULLIF(SUM(cpu_time_millis),0), 2) AS wasted_cpu_pct
FROM trino_queries
WHERE created_at >= CURDATE() - INTERVAL 30 DAY
GROUP BY DATE(created_at)
ORDER BY day;

4D. Top 20 most expensive queries (for drill-down)

SELECT
  query_id,
  `user`,
  COALESCE(source,'unknown') AS source,
  COALESCE(catalog,'unknown') AS catalog,
  COALESCE(`schema`,'unknown') AS schema_name,
  ROUND(cpu_time_millis/1000, 2) AS cpu_seconds,
  ROUND(wall_time_millis/1000, 2) AS wall_seconds,
  peak_memory_bytes,
  LEFT(query, 300) AS query_preview
FROM trino_queries
WHERE created_at >= NOW() - INTERVAL 7 DAY
ORDER BY cpu_time_millis DESC
LIMIT 20;


⸻

5) Performance (P50/P95 style panels)

5A. Latency buckets (simple exec-friendly histogram)

SELECT
  CASE
    WHEN wall_time_millis < 1000 THEN '<1s'
    WHEN wall_time_millis < 5000 THEN '1-5s'
    WHEN wall_time_millis < 30000 THEN '5-30s'
    WHEN wall_time_millis < 120000 THEN '30-120s'
    ELSE '>120s'
  END AS latency_bucket,
  COUNT(*) AS queries
FROM trino_queries
WHERE created_at >= NOW() - INTERVAL 7 DAY
GROUP BY latency_bucket
ORDER BY queries DESC;

5B. Slow queries trend (% over 30s)

SELECT
  DATE(created_at) AS day,
  ROUND(100 * SUM(wall_time_millis >= 30000) / NULLIF(COUNT(*),0), 2) AS slow_query_pct
FROM trino_queries
WHERE created_at >= CURDATE() - INTERVAL 30 DAY
GROUP BY DATE(created_at)
ORDER BY day;


⸻

6) Data Movement & Volume (what execs call “scale”)

6A. Daily bytes scanned / output

SELECT
  DATE(created_at) AS day,
  SUM(physical_input_bytes) AS physical_input_bytes,
  SUM(output_bytes) AS output_bytes,
  SUM(written_bytes) AS written_bytes,
  SUM(total_rows) AS total_rows
FROM trino_queries
WHERE created_at >= CURDATE() - INTERVAL 30 DAY
GROUP BY DATE(created_at)
ORDER BY day;

6B. “Big scans” top sources (drives infra bills)

SELECT
  COALESCE(source,'unknown') AS source,
  SUM(physical_input_bytes) AS scanned_bytes,
  COUNT(*) AS queries,
  ROUND(SUM(physical_input_bytes) / NULLIF(COUNT(*),0)) AS avg_scan_bytes
FROM trino_queries
WHERE created_at >= NOW() - INTERVAL 30 DAY
GROUP BY COALESCE(source,'unknown')
ORDER BY scanned_bytes DESC
LIMIT 20;


⸻

7) Governance / Security Proxies (given your schema)

You don’t have explicit “policy decision” columns here, but you can still create exec panels using proxies:

7A. Queries touching “sensitive” catalogs/schemas (define your list)

SELECT
  DATE(created_at) AS day,
  COUNT(*) AS sensitive_queries
FROM trino_queries
WHERE created_at >= CURDATE() - INTERVAL 30 DAY
  AND (
    catalog IN ('pii_catalog', 'security_catalog') OR
    `schema` IN ('pii', 'hr', 'customer_sensitive')
  )
GROUP BY DATE(created_at)
ORDER BY day;

7B. Top users accessing sensitive areas

SELECT
  `user`,
  COUNT(*) AS sensitive_queries,
  ROUND(SUM(cpu_time_millis)/1000,2) AS cpu_seconds
FROM trino_queries
WHERE created_at >= NOW() - INTERVAL 30 DAY
  AND (catalog IN ('pii_catalog','security_catalog') OR `schema` IN ('pii','hr','customer_sensitive'))
GROUP BY `user`
ORDER BY sensitive_queries DESC
LIMIT 20;

If you later integrate OPA/Moat decisions, add columns like policy_decision, policy_id, masked_columns_json and these panels become first-class.

⸻

8) Make Dashboards Fast: Pre-Aggregated Exec Tables

For Grafana/exec dashboards, you’ll want summary tables refreshed every 1–5 mins (or hourly).

8A. Daily exec rollup table

CREATE TABLE IF NOT EXISTS trino_exec_daily (
  day DATE NOT NULL PRIMARY KEY,
  total_queries BIGINT NOT NULL,
  finished BIGINT NOT NULL,
  failed BIGINT NOT NULL,
  canceled BIGINT NOT NULL,
  active_users BIGINT NOT NULL,
  cpu_seconds DOUBLE NOT NULL,
  wall_seconds DOUBLE NOT NULL,
  avg_wall_s DOUBLE NOT NULL,
  slow_pct DOUBLE NOT NULL,
  wasted_cpu_pct DOUBLE NOT NULL
);

8B. Populate/refresh (idempotent upsert)

REPLACE INTO trino_exec_daily
SELECT
  DATE(created_at) AS day,
  COUNT(*) AS total_queries,
  SUM(query_state = 'FINISHED') AS finished,
  SUM(query_state = 'FAILED') AS failed,
  SUM(query_state = 'CANCELED') AS canceled,
  COUNT(DISTINCT `user`) AS active_users,
  SUM(cpu_time_millis) / 1000 AS cpu_seconds,
  SUM(wall_time_millis) / 1000 AS wall_seconds,
  AVG(wall_time_millis) / 1000 AS avg_wall_s,
  100 * SUM(wall_time_millis >= 30000) / NULLIF(COUNT(*),0) AS slow_pct,
  100 * SUM(CASE WHEN query_state IN ('FAILED','CANCELED') THEN cpu_time_millis ELSE 0 END)
      / NULLIF(SUM(cpu_time_millis),0) AS wasted_cpu_pct
FROM trino_queries
WHERE created_at >= CURDATE() - INTERVAL 90 DAY
GROUP BY DATE(created_at);


⸻

9) Grafana Panel Mapping (quick)

Use these as panels:
	•	KPI cards: Section 1A
	•	Usage trend: 2A
	•	Top teams: 2B
	•	Success rate trend: 3A
	•	Cost trend: 4A
	•	Cost by team: 4B
	•	Waste trend: 4C
	•	Latency buckets: 5A
	•	Big scan drivers: 6B
	•	Sensitive access trend: 7A

⸻

If you tell me what timestamp column you actually have (or if you don’t), and your Trino states (FINISHED/FAILED/RUNNING etc), I can tailor these queries exactly to your dataset and also give you the materialized rollup tables for hourly + 5-min refresh (Grafana-friendly).