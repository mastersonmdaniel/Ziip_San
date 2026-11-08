# Índice e rastreabilidade — pacote ED-05 (arcabouço do contrato-modelo)

Repo de destino (proposto, a confirmar na integração — **não existe** pasta
de contratos no repo no HEAD `0fbcd22`, conferido via MCP em 07/11/2026):
`docs/contratos/`. Nenhum push foi feito.

## Arquivos deste pacote

| Arquivo | Conteúdo | Depende da P3.9? |
|---|---|---|
| `CONTRATO-MODELO-SETOR-PUBLICO.md` | Estrutura/sumário do contrato-modelo, pontos de inserção | Sim — redação de todas as cláusulas |
| `ANEXO-I-SLA-SUPORTE.md` | Estrutura da política de SLA + regras de cálculo + disponibilidade 99,9% | Parcial — prazos/metas/% por severidade |
| `ANEXO-II-LGPD-DPA-ESTRUTURA.md` | Estrutura do DPA a partir do aceite da P3.9 + parâmetros da P3.10 | Sim — redação, teto de horas, lista de suboperadores |
| `ANEXO-III-SAIDA-REVERSIBILIDADE.md` | Tabela normativa de saída (P3.16) | Só a redação da remissão |
| `ANEXO-IV-CONTINGENCIA-PLANTAO.md` | Telefone/canal/plantão S1 (estrutura + roteiro de ensaio) | Dados concretos não existem nas fontes |

## Fontes de cada afirmação normativa

| Afirmação | Fonte |
|---|---|
| ED-05: título, deps P2.15/P3.15/P3.16, aceite integral | `tarefas-onda-A.json` (ED-05); PLANO-v3.0 linha da ED-05 |
| Nível Setor Público: 99,9%, 5 severidades, exportação 10 dias úteis, consulta RO até 120 dias, acionado no lançamento | PLANO-v3.0, decisões (ADR-0025) |
| SLA por contrato, `SUP-AAAA-NNNNNN`, S1–S5, chamados vigência+5 anos | PLANO-v3.0, decisões (ADR-0020) |
| Política em `support.sla_policy`, core `V10_2`, alerta 80%/estouro, FRT/MTTR/SLA/CSAT, casos Videira/SAMAE | Aceite da P2.15 (`tarefas-P2.json`) |
| Multa = soma por chamado estourado de mensalidade × %/100, sem teto nas fontes | Entrega local P3.15, `MonthlyContractReport.java` |
| Relatório mensal por contrato: disponibilidade + aderência + multa | Aceite da P3.15 |
| Papéis operador/controlador; teto contratual de `lgpd.operator_notice_hours`; cláusulas-padrão ANPD Res. 19/2024; retenção/exceção no DPA; assinatura só na LC.9 | Aceite da P3.9 (`tarefas-P3.json`) |
| Alerta Central 24h/48h; ANPD 3 dias úteis + complementação 20 dias úteis (Res. 15/2024); registro do incidente 5 anos | Aceite da P3.10 + entrega local P3.10 (`IncidentClock`, `AnpdDeadlines`, `LgpdIncidentParameters`) |
| Saída: 15 dias / 10 dias úteis, RO ≤120 dias, carência 30 dias, contestação 15 dias, certificado ≤30 dias e máx. 75/60 dias, crypto-shredding, portão P3.19 p/ tenant público | Aceite da P3.16 + roteiro de ensaio da entrega local P3.16 |
| Suspensão sempre manual c/ aviso prévio, papéis e MFA | PLANO-v3.0, decisões (ADR-0018/0019) |
| Risco residual regional declarado em contrato; nada fora de `sa-east-1` | Aceite da LC.3 (RNF-44, PLT-04) |
| Item 2.5 SAMAE via alertas da ED-07 | Aceites da ED-05 e da ED-07 |

## Migration / registro em sistema

**Não se aplica e nenhuma foi criada.** O aceite da ED-05 não pede schema
novo: ele consome a `V10_2` (P2.15, `support.sla_policy`) e a `V10_5`
(P3.16, consulta pós-contrato), ambas já reservadas no mapa §3 do plano às
suas tarefas donas. Criar migration aqui violaria o mapa de reservas.
