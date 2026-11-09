# LC.4 — Modelo de aceite do tenant — chave do cliente, modo IMPORTED (PLT-41)

Produtor: sessao `ca6d61bf-f7f3-42dd-a535-196ff96c1069` (papel: Produtor) — bloco de conclusao do ZiipSan, 09/11/2026.
Fonte unica do aceite: `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json` (registo integral da LC.4 lido antes da producao).
Regras do bloco: sem GitHub (parada dura HTTP 429), sem AWS (sem conta neste bloco), nenhum dado inventado, nenhum segredo literal; valores sem fonte = `[A DEFINIR]`/`[SEM FONTE]` + pendencia nomeada.

Formulario de registo — **documento generico, revisar e substituir pelos dados reais na contratacao**. Nenhum valor real neste modelo.

| Campo | Valor |
|---|---|
| Tenant (razao social) | [A DEFINIR] |
| CNPJ do tenant | [A DEFINIR] |
| Contrato SaaS n. | [A DEFINIR] |
| Parametro aplicado | `crypto.key_mode = IMPORTED` |
| Data do aceite | [A DEFINIR] |
| Representante do tenant (nome/cargo) | [A DEFINIR] |
| Representante ZiipSan (nome/cargo) | [A DEFINIR] |
| Assinaturas | [A DEFINIR] |

## Risco e limitacoes declarados ao tenant (do aceite da LC.4)

- O modo IMPORTED so existe **sob demanda de contrato**; o padrao da plataforma e `PLATFORM`.
- **Limitacoes do provedor registradas:** chave com material importado **nao tem rotacao automatica** no KMS; o material pode ter data de expiracao definida na importacao, e a reimportacao e ato do tenant.
- **Risco de indisponibilidade aceite pelo tenant:** se a chave do cliente ficar indisponivel (revogacao, expiracao/perda do material ou indisponibilidade do armazenamento externo, conforme o modo), **o dado daquele tenant fica ilegivel** — efeito equivalente ao crypto-shredding (RNF-43) — sem afetar os demais tenants.
- A revogacao da chave pelo tenant ou a seu pedido produz esse efeito de forma **irreversivel** apos a janela de exclusao do KMS.

Ciente e aceite do tenant: assinatura `[A DEFINIR]`.
