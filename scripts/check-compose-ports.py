#!/usr/bin/env python3
"""Verificação P0.7: toda porta publicada do docker-compose.yml deve estar em 127.0.0.1.

Lê o compose como texto (sem depender de PyYAML), ignora linhas de comentário e
só inspeciona entradas dentro de blocos `ports:` de serviços ativos ou com perfil.
Falha (saída 1) com a lista de linhas fora de 127.0.0.1 — incluindo a forma curta
sem host (`- "5432:5432"`), que publica em todas as interfaces.

Uso: scripts/check-compose-ports.py [caminho-do-compose]
O ci-linux.sh chama este script na etapa (d).
"""

import re
import sys

PORT_ENTRY = re.compile(r'^\s*-\s*["\']?(?P<spec>[^"\']+)["\']?\s*$')
HOST_PORT = re.compile(r'^(?P<host>[^:]+):(?P<hostport>\d+):(?P<containerport>\d+)$')
SHORT_PORT = re.compile(r'^\d+:\d+$')


def check(path):
    bad = []
    in_ports = False
    service = None
    with open(path) as fh:
        for n, raw in enumerate(fh, 1):
            line = raw.rstrip("\n")
            stripped = line.strip()
            if stripped.startswith("#") or not stripped:
                continue
            # Serviço novo: 2 espaços + nome + ":"
            m_service = re.match(r'^  ([A-Za-z0-9_-]+):\s*$', line)
            if m_service:
                service = m_service.group(1)
                in_ports = False
                continue
            # Bloco ports: dentro de um serviço
            if re.match(r'^    ports:\s*$', line):
                in_ports = True
                continue
            # Qualquer outra chave de serviço (4 espaços) fecha o bloco ports
            if re.match(r'^    [A-Za-z0-9_-]+:', line):
                in_ports = False
                continue
            if not in_ports:
                continue
            m = PORT_ENTRY.match(line)
            if not m:
                continue
            spec = m.group("spec")
            hm = HOST_PORT.match(spec)
            if hm:
                if hm.group("host") != "127.0.0.1":
                    bad.append((n, service, line.strip()))
            elif SHORT_PORT.match(spec):
                bad.append((n, service, line.strip()))
            else:
                # Formato não reconhecido: falha fechada, não ignora.
                bad.append((n, service, line.strip() + "  # formato nao reconhecido"))
    return bad


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "docker-compose.yml"
    bad = check(path)
    if bad:
        print("PORTAS EM TODAS AS INTERFACES (falha):")
        for n, service, line in bad:
            print(f"  linha {n} [{service}]: {line}")
        return 1
    print(f"OK: todas as portas publicadas de {path} estao em 127.0.0.1")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
