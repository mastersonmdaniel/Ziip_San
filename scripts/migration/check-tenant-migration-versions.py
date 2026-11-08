#!/usr/bin/env python3
"""P3.12 — guarda da sequência global de migrations de tenant.

Réplica em script da política de TenantMigrationVersionPolicy (a regra que
vale é a do teste de CI, TenantMigrationGlobalSequenceCiTest, que roda no
mvn verify): depois do fim do outOfOrder, migration de tenant nova — arquivo
V<versão>__*.sql em qualquer src/main/resources/db/migration/tenant fora da
lista de publicadas — precisa ter versão numérica, >= V100 e maior que a
maior publicada, sem duplicar outra migration nova.

Uso:
  python3 scripts/migration/check-tenant-migration-versions.py [--repo RAIZ]
  python3 scripts/migration/check-tenant-migration-versions.py --write-baseline

--write-baseline regenera published-tenant-migrations.txt a partir do repo
(só deve ser usado no commit de integração que publica migration de faixa
ainda pendente; a lista entregue no corte da P3.12 foi gerada do HEAD
8b3d9fc + V1_8 da P3.30, ver o cabeçalho dela).
Sai 1 com as violações listadas; 0 se estiver tudo na sequência.
"""
import re
import sys
from pathlib import Path

BASELINE_REL = Path(
    "platform/ziipsan-platform/src/main/resources/migration-policy/"
    "published-tenant-migrations.txt"
)
VERSIONED = re.compile(r"^V([0-9]+(?:_[0-9]+)*)__.*\.sql$")
LOOSE = re.compile(r"^V.*__.*\.sql$")
FIRST_GLOBAL_MAJOR = 100


def version_of(name):
    m = VERSIONED.match(name)
    if not m:
        return None
    return tuple(int(p) for p in m.group(1).split("_"))


def fmt(version):
    return ".".join(str(p) for p in version)


def load_baseline(repo):
    path = repo / BASELINE_REL
    names = []
    for line in path.read_text(encoding="utf-8").splitlines():
        name = line.strip()
        if name and not name.startswith("#"):
            names.append(name)
    return names


def scan(repo):
    names = set()
    for directory in repo.rglob("src/main/resources/db/migration/tenant"):
        if directory.is_dir():
            for f in directory.iterdir():
                if f.is_file() and f.name.endswith(".sql"):
                    names.add(f.name)
    return sorted(names)


def check(all_names, published):
    published_set = set(published)
    versions = [version_of(n) for n in published]
    versions = [v for v in versions if v is not None]
    if not versions:
        return ["lista de migrations publicadas vazia ou ilegível"]
    max_published = max(versions)
    new_files = [n for n in all_names if LOOSE.match(n) and n not in published_set]
    violations = []
    for name in new_files:
        version = version_of(name)
        if version is None:
            violations.append(
                f"{name}: versão ilegível — migration nova precisa de versão "
                f"numérica na sequência global (a partir de V{FIRST_GLOBAL_MAJOR})"
            )
            continue
        if version[0] < FIRST_GLOBAL_MAJOR:
            violations.append(
                f"{name}: versão {fmt(version)} abaixo de V{FIRST_GLOBAL_MAJOR} — "
                "as faixas de módulo acabaram na P3.12"
            )
        if version <= max_published:
            violations.append(
                f"{name}: versão {fmt(version)} menor ou igual à maior "
                f"publicada ({fmt(max_published)})"
            )
        for other in new_files:
            if other != name and version_of(other) == version:
                violations.append(
                    f"{name}: versão {fmt(version)} duplicada com a migration nova {other}"
                )
    return violations


def main(argv):
    repo = Path(__file__).resolve().parents[2]
    if "--repo" in argv:
        repo = Path(argv[argv.index("--repo") + 1]).resolve()
    if "--write-baseline" in argv:
        names = [n for n in scan(repo) if LOOSE.match(n)]
        header = (repo / BASELINE_REL).read_text(encoding="utf-8").splitlines()
        header = [l for l in header if l.startswith("#")]
        body = "\n".join(header + sorted(names)) + "\n"
        (repo / BASELINE_REL).write_text(body, encoding="utf-8")
        print(f"baseline regenerada: {len(names)} migrations publicadas")
        return 0
    published = load_baseline(repo)
    violations = check(scan(repo), published)
    if violations:
        print("P3.12: sequência global de migrations de tenant violada:")
        for v in violations:
            print(" -", v)
        return 1
    print("P3.12: sequência global ok (nenhuma migration nova fora da ordem)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
