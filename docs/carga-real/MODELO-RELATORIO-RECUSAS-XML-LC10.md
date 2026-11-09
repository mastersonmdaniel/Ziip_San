# MODELO — Relatório de recusas de XML autorizados (LC.10 / P2.19)

- **Sessão produtora do modelo:** `1fb44147-00cd-4ed1-a19a-ac6f72c78874` — papel: **Produtor**
- **Documento em branco.** Preenchido somente na execução real, a
  partir do relatório por lote do `LegacyXmlImportService`.
  **Nenhum valor aqui é resultado de importação alguma** — nenhum XML
  real foi importado.

---

## Identificação

- Cliente (piloto): `[A DEFINIR]`
- Tenant: `[A DEFINIR]`
- Ambiente: produção
- Lote(s) (sistema de origem + chave externa): `[A DEFINIR]`
- Emitente esperado: `[A DEFINIR]`
- Declaração de mapeamento (arquivo + versão): `[A DEFINIR]`
- Operador (ZiipSan): `[A DEFINIR]`

## 1. Totais do lote

| Medida | Quantidade |
|---|---|
| Arquivos recebidos | |
| Aceitos | |
| Recusados | |
| Aceitos gravados na retenção imutável (P2.18, origem `LEGACY_IMPORT`, hash conferido) | |

## 2. Recusas por motivo (vocabulário `LegacyXmlRejection` da P2.19)

| Motivo | Quantidade | Observação |
|---|---|---|
| `SIGNATURE_INVALID` — assinatura inválida | | |
| `MISSING_AUTHORIZATION_PROTOCOL` — sem protocolo de autorização | | |
| `ISSUER_MISMATCH` — outro emitente | | |
| `DUPLICATE` — repetido | | |
| `MODEL_NOT_ACCEPTED` — modelo fora da declaração | | |
| `ACCESS_KEY_INVALID` — chave inválida | | |
| `XML_INVALID` — XML inválido | | |

## 3. Recusas por arquivo

| Arquivo | Chave de acesso (ou trecho que identifique) | Motivo | Disposição combinada com o cliente |
|---|---|---|---|
| `[A DEFINIR]` | | | |

## 4. Contagem por competência conferida com o legado

| Competência (AAMM) | Aceitos | Contagem declarada pelo legado | Confere? |
|---|---|---|---|
| `[A DEFINIR]` | | | |

> Sem a contagem declarada pelo legado, a conferência é erro
> (regra da P2.19) — a coluna não pode ser deixada em branco
> presumindo conferência.

## 5. Aceite

**Relatório de recusas aceito pelo piloto** (item de aceite da LC.10):

- Nome do representante do cliente: `[A DEFINIR]`
- Assinatura: ______________________________
- Data: `[A DEFINIR]`
- Recusas NÃO aceitas (voltam como divergência para tratamento; nenhuma
  recusa é apagada ou reclassificada para fechar número): `[A DEFINIR]`
