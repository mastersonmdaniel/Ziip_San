# Base Fictícia de Testes — ZiipSan (seed)

> **DADOS 100% FICTÍCIOS.** Nenhuma pessoa, empresa, documento ou endereço real
> é usado nesta massa. Tenant, titulares, endereços e valores foram gerados
> por script a partir de uma semente fixa. **PROIBIDO carregar em produção**
> ou em qualquer banco com dados reais.

Decisão de origem: dono, nº 34 (09/11/2026) — a P0.30 não tem piloto real;
os testes rodam sobre esta base fictícia de **12.000 unidades consumidoras**
e a base é **limpa por completo** ao final.

**Complemento EMASA (09/11/2026, adendo ao item 34):** a prestadora fictícia
passa a espelhar **só o perfil estrutural** da EMASA — autarquia municipal,
**dois municípios atendidos**, água + esgoto próprios, porte de referência
30 mil ligações / 73 mil+ unidades — porque, nas palavras do dono, a EMASA
"é uma gestora de duas cidades" e a base deve testar se o sistema está
pronto para isso. **Nenhum dado real da EMASA foi copiado** (nome, CNPJ,
telefone, e-mail, endereço e lei de criação ficam de fora): nome da
prestadora, municípios, códigos e endereços são **inventados** e marcados
como fictícios. As 12.000 UCs (escala reduzida frente às 73 mil+ unidades
do perfil) distribuem-se entre os dois municípios fictícios.

## Tenant dedicado

| Campo | Valor |
|---|---|
| tenant_id (UUID fixo, inalterado no complemento) | `11111111-2222-4333-8444-555555555555` |
| Nome | Autarquia Ficticia Intermunicipal de Aguas — Porto das Gaivotas e Vale do Rio Claro (TESTE FICTICIO) |
| schema_name | `tenant_ficticio_teste` |

## Dois municípios fictícios

| Município fictício | Papel no perfil | Código IBGE fictício | UCs |
|---|---|---:|---:|
| Porto das Gaivotas | cidade costeira turística maior | `9990001` (inventado) | 9.000 (75%) |
| Vale do Rio Claro | cidade vizinha menor | `9990002` (inventado) | 3.000 (25%) |

O município existe na massa pelo mecanismo real do domínio (registry V3_1):
`localities` (uma linha por município, com `municipality_ibge` fictício) →
`commercial_sectors` → `blocks` → `consumer_units.block_id`, e também em
`addresses.city`/`addresses.municipality_ibge` via
`consumer_units.address_id`. Rotas de leitura são dedicadas por município
(2 por município; nenhuma rota mistura os dois). A repartição 75/25 é
**escolha de modelagem justificada** no `RELATORIO-COBERTURA.md` — a fonte
do perfil não traz repartição por cidade.

Todas as linhas da massa carregam este `tenant_id`. Titulares chamam-se
`Titular Ficticio NNNNN`; endereços ficam em Porto das Gaivotas ou em
Vale do Rio Claro, conforme o município da UC.
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
| `prova-carga-limpeza.sh` | Harness reproduzível: cluster PostgreSQL 16 descartável, sentinela, carga, 12 asserções (7 originais + 5 do complemento: UCs por município ×2 mecanismos, situação × município, separação de leituras/contas por município, rotas por município), limpeza, placar. |

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
bash prova-carga-limpeza.sh   # placar esperado: 12/12
python3 gerar-base-ficticia.py /tmp/regenerado   # determinismo: hashes idênticos
```
