#!/usr/bin/env python3
"""Coerencia ED-92: tipos/origens/status so do aceite; nenhuma causa de
fabrica alem de ALAGAMENTO; achado sem vocabulario de reparo."""
import pathlib, re, sys
root = pathlib.Path(__file__).resolve().parents[2]
java = "\n".join(p.read_text(encoding="utf-8") for p in root.rglob("*.java") if "/src/main/" in str(p))
bad = []
if 'List.of("ALAGAMENTO")' not in java: bad.append("causa com fonte ALAGAMENTO ausente")
for forbidden in ["RUPTURA_TUBO", "VAZAMENTO_REDE", "ENTUPIMENTO"]:
    if forbidden in java: bad.append("causa inventada: " + forbidden)
finding = (root/"shared/ziipsan-core/src/main/java/br/com/ziipsan/core/incident/LeakDetectionFinding.java").read_text(encoding="utf-8")
code_only = re.sub(r"/\*.*?\*/", "", finding, flags=re.S)
code_only = re.sub(r"//.*", "", code_only)
code_only = re.sub(r'"[^"]*"', '""', code_only)  # literais (mensagens) nao sao API de reparo
if re.search(r"(?i)repair|reparo", code_only):
    bad.append("achado com vocabulario de reparo no codigo (DAD-27)")
print("COHERENCE_ED92=" + ("FAIL " + "; ".join(bad) if bad else "OK"))
sys.exit(1 if bad else 0)
