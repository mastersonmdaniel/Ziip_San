#!/usr/bin/env python3
"""Teste (positivo e negativo) de scripts/check-compose-ports.py. Sem dependências.

Uso: python3 scripts/test-check-compose-ports.py
Deve sair 0 e imprimir OK em cada caso.
"""

import importlib.util
import tempfile
from pathlib import Path

_spec = importlib.util.spec_from_file_location(
    "check_compose_ports", Path(__file__).parent / "check-compose-ports.py")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
check = _mod.check

GOOD = """services:
  postgres-primary:
    image: postgis/postgis:15-3.4
    ports:
      - "127.0.0.1:5432:5432"
  minio:
    profiles: ["storage"]
    image: chrislusf/seaweedfs:3.60
    ports:
      - "127.0.0.1:9000:9000"
  # servico comentado nao conta:
  # ghost:
  #   ports:
  #     - "9000:9000"
"""

BAD_ALL_INTERFACES = """services:
  timescaledb:
    image: timescale/timescaledb:latest-pg15
    ports:
      - "5434:5432"
"""

BAD_SHORT_FORM = """services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
"""

BAD_OTHER_HOST = """services:
  vault:
    image: hashicorp/vault:1.15
    ports:
      - "0.0.0.0:8200:8200"
"""


def run_case(name, content, expect_bad):
    with tempfile.NamedTemporaryFile("w", suffix=".yml", delete=False) as fh:
        fh.write(content)
        path = fh.name
    bad = check(path)
    ok = bool(bad) == expect_bad
    print(("OK  " if ok else "FAIL") + f" {name}: bad={bad}")
    return ok


def main():
    results = [
        run_case("bom (só 127.0.0.1, comentado ignorado)", GOOD, False),
        run_case("negativo: porta em todas as interfaces", BAD_ALL_INTERFACES, True),
        run_case("negativo: forma curta sem host", BAD_SHORT_FORM, True),
        run_case("negativo: host 0.0.0.0", BAD_OTHER_HOST, True),
    ]
    # E o compose real do repositório deve passar.
    real = Path(__file__).parent.parent / "docker-compose.yml"
    bad = check(str(real))
    print(("OK  " if not bad else "FAIL") + f" compose real: bad={bad}")
    results.append(not bad)
    if not all(results):
        return 1
    print("OK: check-compose-ports passa no positivo e nos 3 negativos")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
