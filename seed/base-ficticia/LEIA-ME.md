# Base Fictícia de Testes — ZiipSan (seed)

> **DADOS 100% FICTÍCIOS.** Nenhuma pessoa, empresa, documento ou endereço real
> é usado nesta massa. Tenant, titulares, endereços e valores foram gerados
> por script a partir de uma semente fixa. **PROIBIDO carregar em produção**
> ou em qualquer banco com dados reais.

Decisão de origem: dono, nº 34 (09/11/2026) — a P0.30 não tem piloto real;
os testes rodam sobre esta base fictícia de **12.000 unidades consumidoras**
e a base é **limpa por completo** ao final.

## Tenant dedicado

| Campo | Valor |
|---|---|
| tenant_id (UUID fixo) | `11111111-2222-4333-8444-555555555555` |
| Nome | Prestadora Fictícia de Testes — Município Fictício de Teste |
| schema_name | `tenant_ficticio_teste` |

Todas as linhas da massa carregam este `tenant_id`. Titulares chamam-se
`Titular Ficticio NNNNN`; endereços ficam no Município Fictício de Teste.
Documentos (CPF/CNPJ) **não** foram gerados: o DDL permite documento nulo
(`customers.document_*`) e nenhum documento fictício foi inventado.

## O que há aqui

| Arquivo | Papel |
|---|---|
| `gerar-base-ficticia.py` | Gerador determinístico (Python 3, só stdlib, semente `20261109`). Regenera `carga-*.sql`, `matriz.csv` e `manifesto.json` byte a byte. |
| `00-tenant-ficticio.sql` | Cria o tenant fictício em `public.tenant_catalog`. Carregar **primeiro**. |
| `carga-NNNN.sql` | A massa (catálogos do tenant + 12.000 UCs e cadeias). Cada arquivo ≤ ~110 KB, com aviso de seed fictício no topo. |
| `matriz.csv` | Matriz de situações legível por máquina (23 situações; soma = 12.000). |
| `manifesto.json` | Contagens por tabela e SHA-256 de cada saída do gerador. |
| `limpeza-base-ficticia.sql` | Limpeza exata: DELETEs por `tenant_id` fictício, em ordem segura para as FKs, + remoção do tenant. **Só toca no tenant fictício.** |
| `RELATORIO-COBERTURA.md` | Situação × contagem × fonte no domínio. |
| `prova-carga-limpeza.sh` | Harness reproduzível: cluster PostgreSQL 16 descartável, sentinela, carga, 7 asserções, limpeza, placar. |

Este diretório vive em `seed/base-ficticia/` no repositório — **fora** de
qualquer `db/migration`. A massa **nunca** vira migration versionada e
nenhum arquivo segue o padrão `V*`.

## Como carregar

Pré-requisito: banco com o esquema do ZiipSan já migrado (tabelas das
migrations citadas no `RELATORIO-COBERTURA.md`).

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f 00-tenant-ficticio.sql
for f in carga-*.sql; do psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$f"; done
```

## Como limpar (obrigatório ao fim dos testes)

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f limpeza-base-ficticia.sql
```

A limpeza remove **todas** as linhas do tenant fictício nas 32 tabelas
tocadas e a linha do tenant em `tenant_catalog` — e nada de nenhum outro
tenant (provado pelo harness com um tenant sentinela).

## Como provar

```bash
bash prova-carga-limpeza.sh   # placar esperado: 7/7
python3 gerar-base-ficticia.py /tmp/regenerado   # determinismo: hashes idênticos
```
