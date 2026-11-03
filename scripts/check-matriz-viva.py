#!/usr/bin/env python3
"""P1.25: confere a matriz viva e os roteiros de POC (so documentos).

    python3 scripts/check-matriz-viva.py

Verifica, a partir da raiz do repositorio:
  - cabecalho exato de docs/levantamento/editais/matriz-viva.csv;
  - nenhum requirement_id duplicado; formato PREFIXO-NNN (zeros a esquerda);
  - todo requirement_id do CSV existe nas tabelas de 18d-18h §2 (mesma
    normalizacao de zeros e mesma expansao de faixas do gerador da P1.25);
  - todo delivery e uma ED do consolidado 18 §5.1;
  - poc so usa o vocabulario do consolidado 18 §7.1 (+ PENDENTE e
    V:POC-rules, documentados em 21-MATRIZ-VIVA.md);
  - 19-ROTEIRO-POC-VISAN.md contem POC-01 a POC-81;
  - 20-ROTEIRO-POC-SAMAE.md contem as secoes criticas do Edital 7.7.9;
  - links relativos de 19-23 resolvem para arquivos do repositorio.

Exit 0 = ok; 1 = falha de validacao; 2 = uso/arquivo.
Nao toca em banco de dados, nao gera codigo de produto.
"""
import csv
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ED = ROOT / "docs" / "levantamento" / "editais"

EXPECTED_HEADER = ["requirement_id", "canonical_id", "tender", "section",
                   "area", "source_matrix", "source_line", "poc",
                   "code_status", "code_status_note", "plan_raw", "delivery",
                   "task_phase", "tests", "rehearsal", "legal_note",
                   "poc_origin"]

POC_RE = re.compile(r"^(V:POC-\d\d–POC-\d\d|V:POC-\d\d|V:possible|"
                    r"V:POC-rules|S:critical|S:essential|S:assessable|"
                    r"none|PENDENTE)$")
ID_RE = re.compile(r"^(TR|SG|ED|ETP|PP)-\d{3,}$")
RANGE_RE = re.compile(r"(TR|SG|ED|ETP|PP)-(\d+)\s*(?:a|-)\s*"
                      r"(?:(TR|SG|ED|ETP|PP)-)?(\d+)")
SINGLE_RE = re.compile(r"(TR|SG|ED|ETP|PP)-(\d+)")

CRITICAL_SECTIONS = ["2.4", "2.5", "2.7", "2.8", "2.9", "2.10", "2.11",
                     "2.12", "2.13", "2.14", "2.15", "2.16.18", "2.17",
                     "2.18", "2.19", "2.22", "2.24", "2.26", "2.27",
                     "2.28", "Item 3"]


def split_cells(row):
    row = row.strip()
    if row.startswith("|"):
        row = row[1:]
    if row.endswith("|"):
        row = row[:-1]
    return [c.strip() for c in row.split("|")]


def expand_ids(cell):
    """IDs de uma celula, com faixas expandidas e zeros a esquerda."""
    ids = []

    def repl(m):
        p1, a, p2, b = m.group(1), int(m.group(2)), m.group(3), int(m.group(4))
        p2 = p2 or p1
        if p1 != p2 or b < a or b - a > 5000:
            return m.group(0)
        ids.extend(f"{p1}-{n:03d}" for n in range(a, b + 1))
        return " "

    txt = cell.replace("–", "-").replace("—", "-")
    txt = RANGE_RE.sub(repl, txt)
    ids.extend(f"{m.group(1)}-{int(m.group(2)):03d}"
               for m in SINGLE_RE.finditer(txt))
    seen = set()
    out = []
    for i in ids:
        if i not in seen:
            seen.add(i)
            out.append(i)
    return out


def section_lines(path, start_pat, end_pat):
    lines = path.read_text(encoding="utf-8").splitlines()
    out = []
    on = False
    for ln in lines:
        if re.match(start_pat, ln):
            on = True
            continue
        if on and re.match(end_pat, ln):
            break
        if on:
            out.append(ln)
    return out


def matrix_ids():
    files = {
        "18d": "18d-aderencia-cadastro-medicao-gis.md",
        "18e": "18e-aderencia-faturamento-arrecadacao-cobranca.md",
        "18f": "18f-aderencia-atendimento-os-portal.md",
        "18g": "18g-aderencia-operacao-qualidade-bi.md",
        "18h": "18h-aderencia-nao-funcionais-servicos-habilitacao.md",
    }
    found = set()
    for mat, fname in files.items():
        for ln in section_lines(ED / fname, r"## 2\. ", r"## 3\. "):
            if not ln.startswith("|"):
                continue
            c = split_cells(ln)
            if len(c) < 5:
                continue
            if mat in ("18d", "18h"):
                if not re.match(r"(\d+|M\d+)$", c[0]):
                    continue
                ids_cell = c[1]
            else:
                if not SINGLE_RE.search(c[0]):
                    continue
                ids_cell = c[0]
            found.update(expand_ids(ids_cell))
    return found


def ed_set():
    lines = (ED / "18-ADERENCIA-EDITAIS-CONSOLIDADO.md").read_text(
        encoding="utf-8").splitlines()
    eds = set()
    in51 = False
    for ln in lines:
        if ln.startswith("### 5.1"):
            in51 = True
            continue
        if in51 and ln.startswith("### 5.2"):
            break
        if in51 and ln.startswith("| ED-"):
            c = split_cells(ln)
            if len(c) >= 7 and re.match(r"ED-\d+$", c[0]):
                eds.add(c[0])
    return eds


def check_links(errors):
    link_re = re.compile(r"\[[^\]]*\]\(([^)]+)\)")
    for name in ["19-ROTEIRO-POC-VISAN.md", "20-ROTEIRO-POC-SAMAE.md",
                 "21-MATRIZ-VIVA.md", "22-MODELOS-JURIDICOS.md",
                 "23-NOTA-LICENCA-ODA.md"]:
        path = ED / name
        if not path.exists():
            errors.append(f"ausente: {name}")
            continue
        for m in link_re.finditer(path.read_text(encoding="utf-8")):
            target = m.group(1)
            if target.startswith("http"):
                continue
            target = target.split("#")[0]
            if target and not (ED / target).exists():
                errors.append(f"{name}: link quebrado: {target}")


def main():
    errors = []
    csv_path = ED / "matriz-viva.csv"
    if not csv_path.exists():
        print("FALHA: matriz-viva.csv ausente", file=sys.stderr)
        return 2
    with open(csv_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames != EXPECTED_HEADER:
            errors.append(f"cabecalho inesperado: {reader.fieldnames}")
            rows = []
        else:
            rows = list(reader)
    seen = set()
    for r in rows:
        rid = r["requirement_id"]
        if rid in seen:
            errors.append(f"requirement_id duplicado: {rid}")
        seen.add(rid)
        if not ID_RE.match(rid):
            errors.append(f"formato de ID invalido: {rid}")
        if not POC_RE.match(r["poc"]):
            errors.append(f"poc fora do vocabulario: {rid} -> {r['poc']}")
        if r["delivery"] != "PENDENTE":
            for ed in r["delivery"].split(";"):
                if not re.match(r"ED-\d+$", ed.strip()):
                    errors.append(f"delivery invalido: {rid} -> {ed}")
    sources = matrix_ids()
    for rid in sorted(seen - sources):
        errors.append(f"ID do CSV ausente nas matrizes 18d-18h: {rid}")
    for rid in sorted(sources - seen):
        errors.append(f"ID das matrizes ausente no CSV: {rid}")
    eds = ed_set()
    for r in rows:
        if r["delivery"] != "PENDENTE":
            for ed in r["delivery"].split(";"):
                if ed.strip() not in eds:
                    errors.append(f"delivery fora do §5.1: {r['requirement_id']} -> {ed}")
    visan = (ED / "19-ROTEIRO-POC-VISAN.md").read_text(encoding="utf-8") \
        if (ED / "19-ROTEIRO-POC-VISAN.md").exists() else ""
    for n in range(1, 82):
        if f"POC-{n:02d}" not in visan:
            errors.append(f"roteiro VISAN sem POC-{n:02d}")
    samae = (ED / "20-ROTEIRO-POC-SAMAE.md").read_text(encoding="utf-8") \
        if (ED / "20-ROTEIRO-POC-SAMAE.md").exists() else ""
    for sec in CRITICAL_SECTIONS:
        if sec not in samae:
            errors.append(f"roteiro SAMAE sem secao critica: {sec}")
    check_links(errors)
    if errors:
        print(f"FALHA ({len(errors)}):", file=sys.stderr)
        for e in errors[:50]:
            print(f"  - {e}", file=sys.stderr)
        return 1
    print(f"OK: matriz-viva.csv ({len(rows)} requisitos), roteiros 19/20, "
          f"links 19-23 conferidos.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
