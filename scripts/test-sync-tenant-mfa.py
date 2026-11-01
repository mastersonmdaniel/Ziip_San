#!/usr/bin/env python3
"""Teste de scripts/sync-tenant-mfa.py (só as funções puras; sem PG nem Keycloak).

Uso: python3 scripts/test-sync-tenant-mfa.py
Deve sair 0 e imprimir OK em cada caso.
"""

import importlib.util
from pathlib import Path

_spec = importlib.util.spec_from_file_location(
    "sync_tenant_mfa", Path(__file__).parent / "sync-tenant-mfa.py")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)


def check(name, cond):
    assert cond, name
    print(f"OK: {name}")


def user(uid, username, tenant, mfa):
    attrs = { "tenant_id": [tenant] }
    if mfa is not None:
        attrs["mfa_required"] = [mfa]
    return {"id": uid, "username": username, "attributes": attrs}


# parse_bool_setting / load_settings
check("true JSONB vira True", _mod.parse_bool_setting("true") is True)
check("false JSONB vira False", _mod.parse_bool_setting("false") is False)
check("string nao e booleano", _mod.parse_bool_setting('"true"') is None)
check("numero nao e booleano", _mod.parse_bool_setting("1") is None)
check("texto quebrado nao e booleano", _mod.parse_bool_setting("tru") is None)
settings, invalid, warnings = _mod.load_settings(
    [("t1", "true"), ("t2", "banana"), ("t3", "false")])
check("load separa validos", settings == {"t1": True, "t3": False})
check("load isola invalidos", invalid == ["t2"] and len(warnings) == 1)

# needs_update / updated_attributes
check("sem atributo precisa atualizar para true",
      _mod.needs_update(user("u1", "ana", "t1", None), True))
check("atributo igual nao atualiza",
      not _mod.needs_update(user("u1", "ana", "t1", "true"), True))
check("atributo true precisa atualizar para false",
      _mod.needs_update(user("u1", "ana", "t1", "true"), False))
check("updated_attributes preserva tenant_id",
      _mod.updated_attributes(user("u1", "ana", "t1", "false"), True)
      == {"tenant_id": ["t1"], "mfa_required": ["true"]})

# plan_updates: end-to-end do plano
users = [
    user("u1", "ana", "t1", None),      # t1 exige -> true
    user("u2", "bruno", "t1", "true"),  # ja certo
    user("u3", "catarina", "t2", "true"),  # t2 sem parametro -> false (limpa)
    user("u4", "daniel", "t3", None),   # t3 invalido -> pulado
    user("u5", "elisa", "t9", None),    # tenant fora do catalogo -> ignorado
]
plan = _mod.plan_updates(["t1", "t2", "t3"], {"t1": True}, ["t3"], users)
check("plano tem 2 atualizacoes",
      [(p["user_id"], p["to"]) for p in plan] == [("u1", "true"), ("u3", "false")])
check("plano nao toca tenant invalido",
      all(p["tenant_id"] != "t3" for p in plan))

print("todos os casos OK")
