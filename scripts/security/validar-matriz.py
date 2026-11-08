#!/usr/bin/env python3
"""Validador estatico da matriz requisito-teste-evidencia da P3.8 (CSV).

Uso: validar-matriz.py <matriz.csv> [SecurityCase.java]
Confere: toda linha tem requisito, caso, teste e evidencia; os 14
capitulos do ASVS aparecem; A03 e A10 do Top 10:2025 aparecem; API1..API10
aparecem; a linha do LLM aponta para a ED-43. Sai 1 com a lista de
problemas; 0 se completa; 2 em erro de uso/leitura.

Conferencia cruzada CSV x Java (correcao D10 da revisao cruzada): se o
registro SecurityCase.java for localizado (argumento explicito ou, por
padrao, no caminho do repo relativo a matriz:
<raiz>/platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/
securityreview/SecurityCase.java), os nomes de caso e os conjuntos
ASVS/OWASP/API de cada caso sao cruzados linha a linha com o registro.
A UNICA linha do CSV sem caso Java e APPS_LC8 — excecao DECLARADA: e a
linha da LC.8/RNF-39 (apps), coberta pelo pentest externo da LC.8, sem
caso local na P3.8 (ver o anexo ASVS). Qualquer outra divergencia
reprova (exit 1). Se o registro nao for localizado (ex.: CSV copiado
sozinho para fora do repo), a conferencia cruzada e pulada com AVISO
impresso — nunca em silencio.
"""
import csv
import os
import re
import sys

CSV_ONLY_EXCEPTIONS = {"APPS_LC8"}

DEFAULT_JAVA_RELATIVE = os.path.join(
    "platform", "ziipsan-platform", "src", "main", "java", "br", "com",
    "ziipsan", "platform", "securityreview", "SecurityCase.java")


def parse_java_cases(path):
    """Extrai {nome: (asvs, owasp, api)} das constantes do enum SecurityCase."""
    with open(path, encoding="utf-8") as fh:
        source = fh.read()
    cases = {}
    pattern = re.compile(
        r"^\s{4}([A-Z][A-Z0-9_]*)\(\"(?:[^\"\\]|\\.)*\",\s*\"(?:[^\"\\]|\\.)*\",\s*"
        r"Set\.of\(([^)]*)\),\s*Set\.of\(([^)]*)\),\s*Set\.of\(([^)]*)\)",
        re.MULTILINE)
    for match in pattern.finditer(source):
        name, asvs_raw, owasp_raw, api_raw = match.groups()
        asvs = {tok.strip() for tok in asvs_raw.split(",") if tok.strip()}
        owasp = set(re.findall(r'"([^"]+)"', owasp_raw))
        api = set(re.findall(r'"([^"]+)"', api_raw))
        cases[name] = (asvs, owasp, api)
    return cases


def split_set(cell):
    return {tok.strip() for tok in (cell or "").replace(";", " ").split() if tok.strip()}


def main() -> int:
    if len(sys.argv) not in (2, 3):
        print("uso: validar-matriz.py <matriz.csv> [SecurityCase.java]", file=sys.stderr)
        return 2
    with open(sys.argv[1], encoding="utf-8", newline="") as fh:
        rows = list(csv.DictReader(fh))
    problems = []
    chapters = set()
    codes = set()
    for i, row in enumerate(rows, start=2):
        for field in ("requisito", "caso", "teste", "evidencia"):
            if not (row.get(field) or "").strip():
                problems.append(f"linha {i}: campo {field} vazio")
        for token in (row.get("asvs") or "").replace(";", " ").split():
            chapters.add(token.strip())
        for token in (row.get("owasp") or "").replace(";", " ").split():
            codes.add(token.strip())
    for n in range(1, 15):
        if str(n) not in chapters:
            problems.append(f"capitulo ASVS {n} ausente da matriz")
    for code in ("A03", "A10"):
        if code not in codes:
            problems.append(f"categoria nova do Top 10:2025 ausente: {code}")
    api = set()
    for row in rows:
        for token in (row.get("api_top10") or "").replace(";", " ").split():
            api.add(token.strip())
    for n in range(1, 11):
        if f"API{n}" not in api:
            problems.append(f"OWASP API Top 10 ausente: API{n}")
    if not any("ED-43" in (row.get("requisito") or "") or "ED-43" in (row.get("teste") or "") for row in rows):
        problems.append("criterio LLM (ED-43) ausente da matriz")

    # ---- Conferencia cruzada CSV x registro Java (D10) ----
    java_path = None
    if len(sys.argv) == 3:
        java_path = sys.argv[2]
    else:
        candidate = os.path.join(os.path.dirname(os.path.abspath(sys.argv[1])),
                                 "..", "..", DEFAULT_JAVA_RELATIVE)
        if os.path.isfile(candidate):
            java_path = candidate
    if java_path is None:
        print("AVISO: SecurityCase.java nao localizado — conferencia cruzada "
              "CSV x Java NAO executada (rode a partir da matriz no repo ou "
              "passe o caminho do registro como 2o argumento)")
    else:
        java_cases = parse_java_cases(java_path)
        if not java_cases:
            problems.append(f"registro Java sem casos parseaveis: {java_path}")
        csv_cases = {}
        for row in rows:
            name = (row.get("caso") or "").strip()
            if name:
                csv_cases[name] = (split_set(row.get("asvs")),
                                   split_set(row.get("owasp")),
                                   split_set(row.get("api_top10")))
        for name in sorted(set(csv_cases) - CSV_ONLY_EXCEPTIONS - set(java_cases)):
            problems.append(f"caso no CSV sem registro Java: {name}")
        for name in sorted(set(java_cases) - set(csv_cases)):
            problems.append(f"caso no registro Java ausente do CSV: {name}")
        for name in sorted(set(csv_cases) & set(java_cases)):
            csv_sets = csv_cases[name]
            java_sets = java_cases[name]
            for label, csv_set, java_set in zip(("ASVS", "OWASP", "API"), csv_sets, java_sets):
                if csv_set != java_set:
                    problems.append(
                        f"caso {name}: {label} diverge CSV x Java "
                        f"(CSV={sorted(csv_set)}; Java={sorted(java_set)})")
        print(f"conferencia cruzada CSV x Java: {len(java_cases)} casos no registro, "
              f"{len(csv_cases)} linhas de caso no CSV, excecao declarada: "
              f"{sorted(CSV_ONLY_EXCEPTIONS)}")

    for problem in problems:
        print(f"PROBLEMA: {problem}")
    print(f"linhas: {len(rows)}; problemas: {len(problems)}")
    return 1 if problems else 0

if __name__ == "__main__":
    sys.exit(main())
