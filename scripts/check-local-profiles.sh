#!/usr/bin/env python3
"""P0.27: guardas estaticas dos perfis locais (sem container, sem rede).

- Toda porta publicada do compose esta em 127.0.0.1 (reusa check-compose-ports.py).
- Nenhum recurso AWS e referenciado (nada de amazonaws, nem de servico AWS).
- ziipsan.fiscal.production-allowed e falsa em todos os servicos que a definem
  (hoje so api/migrate; a P0.32 deve ler a variavel — ver docker-compose.yml).
- O realm do Keycloak tem sslRequired=all (P0.29).

Uso: scripts/check-local-profiles.sh [docker-compose.yml]
"""
import json
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, __file__.rsplit("/", 1)[0])
import importlib.util

spec = importlib.util.spec_from_file_location(
    "check_ports", __file__.rsplit("/", 1)[0] + "/check-compose-ports.py")
check_ports = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check_ports)


def main() -> int:
    compose = sys.argv[1] if len(sys.argv) > 1 else "docker-compose.yml"
    errors = []
    bad = check_ports.check(compose)
    if bad:
        errors += [f"porta fora de 127.0.0.1: {line}" for _, _, line in bad]
    text = open(compose, encoding="utf-8").read()
    for token in ("amazonaws", "arn:aws:", "s3.amazonaws", "rds.amazonaws"):
        if token in text:
            errors.append(f"recurso AWS referenciado no compose: {token}")
    for line in text.splitlines():
        if "ZIIPSAN_FISCAL_PRODUCTION_ALLOWED" in line:
            value = line.split(":", 1)[1].strip().strip("\"'").lower()
            if value == "true":
                errors.append(f"fiscal.production-allowed verdadeira no compose: {line.strip()}")
    realm_path = "infra/keycloak/realms/ziipsan-realm.json"
    try:
        realm = json.load(open(realm_path, encoding="utf-8"))
        if realm.get("sslRequired") != "all":
            errors.append(f"realm sslRequired={realm.get('sslRequired')!r}, esperado 'all'")
    except FileNotFoundError:
        errors.append(f"realm nao encontrado: {realm_path}")
    if errors:
        print("FALHAS NOS PERFIS LOCAIS:")
        for error in errors:
            print("  - " + error)
        return 1
    print("OK: perfis locais (portas 127.0.0.1, sem AWS, fiscal=false, realm sslRequired=all)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
