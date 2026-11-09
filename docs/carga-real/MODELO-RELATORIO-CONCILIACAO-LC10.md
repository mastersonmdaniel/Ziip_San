# MODELO — Relatório de conciliação da carga real (LC.10)

- **Sessão produtora do modelo:** `1fb44147-00cd-4ed1-a19a-ac6f72c78874` — papel: **Produtor**
- **Documento em branco.** Todos os campos estão vazios ou marcados
  `[A DEFINIR]` de propósito: este modelo só é preenchido na execução
  real da carga, com o dado real do cliente. **Nenhum valor aqui é
  resultado de carga alguma** — nenhuma carga real aconteceu.

---

## Identificação

- Cliente (piloto): `[A DEFINIR]`
- Tenant: `[A DEFINIR]`
- Ambiente: produção (único ambiente admitido para dado real)
- Janela de carga (início/fim): `[A DEFINIR]`
- Fonte do legado (sistema, extração, data/hora, responsável no cliente): `[A DEFINIR]`
- Mapeamento(s) utilizados (arquivo + versão): `[A DEFINIR]`
- Operador da carga (ZiipSan): `[A DEFINIR]`
- Conferente (ZiipSan, pessoa distinta do operador): `[A DEFINIR]`

## 1. Conciliação do cadastro (P1.2 — `LegacyImportService`)

Totais do legado × totais carregados, por recorte (aceite da P1.2:
totais por setor e rota iguais aos do legado):

| Recorte | Legado | Carregado | Diferença | Explicação |
|---|---|---|---|---|
| Setor `[A DEFINIR]` | | | | |
| Rota `[A DEFINIR]` | | | | |
| Categoria `[A DEFINIR]` | | | | |
| **Total de unidades** | | | | |

Perfil de qualidade do ensaio (completude, duplicidade, integridade
referencial, documento inválido):

| Dimensão | Ocorrências | Tratamento |
|---|---|---|
| Completude | | |
| Duplicidade | | |
| Integridade referencial | | |
| Documento inválido | | |

## 2. Conciliação do histórico e do saldo (P2.10 — `V1ImportService` / `V1BalanceConference`)

| Matrícula (código substituto) | Saldo legado | Saldo carregado (prestações incluídas) | Diferença | Código/explicação |
|---|---|---|---|---|
| `[A DEFINIR]` | | | | |

- Matrículas conferidas: `[A DEFINIR]`
- Matrículas divergentes (sem débito gerado, por regra da P2.10): `[A DEFINIR]`
- Códigos de divergência usados (`BALANCE_MISSING`, `BALANCE_NEGATIVE`,
  `INSTALLMENTS_EXCEED_BALANCE`, outros do relatório da carga): `[A DEFINIR]`
- Prestações a vencer migradas como débitos a cobrar: `[A DEFINIR]`
- Unidades sem usuário vigente na situação da P1.41 (CAD-68): `[A DEFINIR]`

## 3. Divergências e disposições

| # | Origem (P1.2/P2.10) | Descrição | Explicação | Disposição (corrigida na fonte / no mapeamento / aceita pelo cliente com justificativa) |
|---|---|---|---|---|
| | | | | |

> Divergência sem explicação ou sem disposição bloqueia a assinatura
> (§4.3 do roteiro). Nenhuma linha deste modelo está pré-preenchida.

## 4. Assinatura

**Conciliação assinada pelo piloto** (item de aceite da LC.10):

- Nome do representante do cliente: `[A DEFINIR]`
- Cargo/função: `[A DEFINIR]`
- Assinatura: ______________________________
- Data: `[A DEFINIR]`

- Conferente ZiipSan (nome e assinatura): `[A DEFINIR]`
- Data: `[A DEFINIR]`
