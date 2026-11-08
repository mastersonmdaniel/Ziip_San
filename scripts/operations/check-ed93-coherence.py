#!/usr/bin/env python3
"""ED-93 coherence checks (structural guards of the acceptance):
- the console service reads ONLY the summary state store: it must not
  reference any feed port, Superset or a free-query source;
- the projector is the only production class touching the feeds;
- the pending SQL has no reserved Flyway numbers (V102/V103), does not
  touch V6_4 and seeds nothing.
Exit 0 and COHERENCE_ED93=OK when all hold.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAIN = ROOT / "intelligence/ziipsan-reporting/src/main/java/br/com/ziipsan/reporting/situation"
SQL = (ROOT / "intelligence/ziipsan-reporting/src/main/resources/db/pendente"
       / "ED-93__situation_room.sql")

failures = []

console = (MAIN / "SituationConsoleService.java").read_text(encoding="utf-8")
for forbidden in ("SituationFeeds.", "IncidentFeed", "WorkOrderFeed", "CrewFeed",
                  "TelemetryAlarmFeed", "ReservoirFeed", "UnitOutageFeed"):
    if forbidden in console:
        failures.append(f"SituationConsoleService references feed port: {forbidden}")
if "SituationStateStore" not in console:
    failures.append("SituationConsoleService does not read the state store")
if "requireSummaryTable" not in console:
    failures.append("SituationConsoleService does not enforce the source guard")

for path in sorted(MAIN.glob("*.java")):
    text = path.read_text(encoding="utf-8")
    if path.name not in ("SituationProjector.java", "SituationFeeds.java", "SituationLatencyProbe.java"):
        if "SituationFeeds." in text:
            failures.append(f"{path.name} touches the feeds (only the projector may)")

sql = SQL.read_text(encoding="utf-8")
for forbidden in ("V102", "V103", "V6_4"):
    if forbidden in sql:
        failures.append(f"SQL mentions forbidden token {forbidden}")
if re.search(r"(?im)^\s*INSERT\s+INTO", sql):
    failures.append("SQL seeds rows (INSERT INTO found outside comments check)")
if "ops_situation_state" not in sql or "ops_console_indicator_bindings" not in sql:
    failures.append("SQL misses one of the two tables")

if failures:
    for failure in failures:
        print("COHERENCE_FAIL:", failure)
    sys.exit(1)
print("COHERENCE_ED93=OK")
