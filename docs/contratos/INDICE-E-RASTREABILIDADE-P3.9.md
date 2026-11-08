# Índice e rastreabilidade — pacote P3.9 (minutas)

> **MINUTA — NÃO ASSINAR. Aprovação do piloto pendente (piloto ainda não escolhido — P0.30). Assinatura só na LC.9, antes do primeiro dado real.**
>
> Estado do pacote: **PRODUZIDO COM APROVAÇÃO PENDENTE**. Pasta lógica de
> destino (a mesma do arcabouço da ED-05): `docs/contratos/`. **Nenhum
> push/commit foi feito.** Espelho do repo usado para as ADRs: HEAD
> `cb5d3216cff68ec26df5dd184ad74a3172bd2549` (arquivos em
> `~/workspace/ziipsan-producao/P3.9/fontes-repo/`).

## Arquivos deste pacote

| Arquivo | Conteúdo | Fontes principais |
|---|---|---|
| `MINUTA-CONTRATO-SAAS.md` | Objeto e licença controlada, aviso, restrição só manual, saída, risco residual | ADR-0019; ADR-0002; P3.16/ED-05 Anexo III |
| `MINUTA-DPA.md` | Papéis, incidente operador→controlador, retenção/exceção, acesso assistido e emergência, telemetria | ADR-0002; ADR-0020; P3.10; Res. CD/ANPD 15/2024 (via P3.10) |
| `MINUTA-TERMOS-DE-USO.md` | Licença por contrato, suporte por protocolo, papéis | ADR-0019; ADR-0020; ADR-0002 |
| `MINUTA-SLA-SUPORTE.md` | Severidades S1–S5, regra da multa, disponibilidade 99,9%, decisão pendente da P3.15 | ADR-0020; ADR-0025 (via ADR-0020/ED-05); P2.15; P3.15 |
| `MINUTA-SUBOPERADORES-TRANSFERENCIA.md` | Lista viva, aviso de mudança, cláusulas-padrão por remissão, trava, RIPD | Aceite da P3.9; ADR-0033; LGPD arts. 33–36; Res. CD/ANPD 19/2024 |
| `MINUTA-TERMO-HOMOLOGACAO-LOCAL.md` | Homologação local sem dado pessoal, anonimização na origem, geometria da rede | ADR-0033 (itens 4–5 e ajuste GIS v2.11); P0.30 |
| `INDICE-E-RASTREABILIDADE-P3.9.md` | Este índice | — |

Relação com a ED-05: este pacote fornece o **texto** que o arcabouço do
nível Setor Público da ED-05 (`CONTRATO-MODELO-SETOR-PUBLICO.md` e Anexos
I–IV) deixou como `[A DEFINIR NA P3.9]`; o placar das 22 ocorrências está no
`RELATORIO-P3.9.md`. Os Anexos III (saída) e IV (contingência/plantão) da
ED-05 continuam válidos e são incorporados por remissão.

## Rastreabilidade — afirmação normativa → fonte

| Afirmação nas minutas | Fonte |
|---|---|
| Ziip SAN operador; cliente controlador com encarregado próprio; contrato aloca ao cliente os valores aceitos | ADR-0002, item 5 |
| Decisões G como parâmetros do tenant: definição, limites, vigência, aceite formal; sem aceite, operação falha | ADR-0002, itens 1–3 |
| Contrato na Central: número, processo, CNPJ, vigência, aditivos, índice/data-base, empenhos, contatos, política de SLA (`support.sla_policy`) | ADR-0019, item 1 |
| Licença controlada: plano, módulos e cotas derivam do contrato | ADR-0019, item 2 |
| Faturas por competência/medição; NFS-e pelo motor fiscal; pagamentos informados e conciliados; sem gateway | ADR-0019, itens 3–4 |
| Sem bloqueio automático; restrição só manual: aviso prévio por escrito, pessoa com MFA/step-up, `PLATFORM_FINANCE` ou `PLATFORM_OWNER`, justificativa e auditoria, ref. à fatura e ao aviso; efeito = backoffice somente leitura, portal/Pix/retornos/2ª via continuam; Lei 14.133/2021 | ADR-0019, item 5 (+ `CENTRAL-ZIIP.md` §6.2 citado nele) |
| Reajuste só após apostilamento/aditivo | ADR-0019, item 6 |
| Alertas 180/90/30 dias; empenho < 2 faturas; cota 80%/100%; fatura vencida; pagamento sem fatura | ADR-0019, item 7 |
| Protocolo `SUP-AAAA-NNNNNN`; conversa, estados com pausa, escalonamento, CSAT | ADR-0020, itens 1 e 3 |
| Severidades S1–S5 (impacto × criticidade); S1 solução em 2h úteis **como referência**; padrão sem multa, Setor Público com multa | ADR-0025 via ajustes do ADR-0020, item 3 |
| Anexos em quarentena até varredura (ClamAV); e-mail sem conteúdo/anexos | ADR-0020, itens 4–5 |
| Acesso assistido: identidade própria c/ MFA, consentimento do admin vinculado a chamado, máx. 72h, revogação, auditoria | ADR-0020, item 6 |
| Acesso de emergência: incidente declarado, só `PLATFORM_OWNER` c/ step-up, só leitura, aviso ≤24h, registro completo; DPA precisa prever | ADR-0020, seção "Exceção: acesso de emergência" |
| Retenção de chamados/anexos = vigência + 5 anos; exceção registrada no certificado de descarte; DPA prevê | ADR-0020, Consequências (decisão 12/10/2026); aceite da P3.9 |
| Página pública de status separada | ADR-0020, item 7 |
| Aviso operador→controlador sem demora injustificada e no prazo contratual; Res. 15/2024 não fixa número; ANPD recomenda fixar em contrato | Aceite da P3.9; P3.10 (procedimento §3) |
| `lgpd.operator_notice_hours` classe c, só reduz, teto contratual obrigatório — sem teto, validação recusa | P3.10, `LgpdIncidentParameters` |
| Relógio no conhecimento; alertas na Central 24h/48h | P3.10, `IncidentClock` |
| Controlador: ANPD/titulares em 3 dias úteis; complementação 20 dias úteis | Res. CD/ANPD 15/2024, via P3.10 (`AnpdDeadlines`) |
| Registro do incidente por 5 anos (`ret.incident_record_years` fixo) | P3.10 (spec e `LgpdIncidentParameters`) |
| Incidente/RIPD ligados à ocorrência da P2.25; RIPD por módulo; vínculo P3.40 | P3.10 (`MODELO-RIPD.md`) |
| Telemetria agregada: sem identificador, sem gravação de sessão, sem conteúdo digitado | Aceite da P3.9 via Anexo II §5 da ED-05 (P4.8/LC.9) |
| Cláusulas-padrão ANPD p/ todo provedor fora do país (modelo de linguagem, Meta, Telegram e demais); transferência no RIPD; trava de aprovação | Aceite da P3.9; Res. CD/ANPD 19/2024; LGPD arts. 33–36 |
| Homologação local; dado do piloto só anonimizado na origem; código substituto; varredura da base local | ADR-0033, itens 4–5 |
| Rede do piloto real como geometria c/ autorização no termo de cooperação (P0.30); ligações/ramais/cavaletes/imóveis anonimizados | ADR-0033, ajuste GIS v2.11 |
| AWS `sa-east-1` só no lançamento; sem dado real antes; atestado só após lançamento | ADR-0033, itens 1–2 e 5 |
| Saída: exportação 10 dias úteis (SP) / 15 dias (padrão); RO ≤120 dias; carência 30 dias; contestação 15 dias; certificado ≤30 dias e máx. 75/60 dias da âncora da carência; crypto-shredding; portão P3.19 | P3.16 via Anexo III da ED-05 |
| Multa de SLA = soma por chamado estourado de mensalidade × `penalty_pct`/100, HALF_UP por chamado; sem teto nas fontes | P3.15 (`MonthlyContractReport`) via Anexo I da ED-05 |
| Disponibilidade 99,9% só Setor Público, do lançamento; relatório mensal por contrato; padrão sem meta | ADR-0025/P3.15 via Anexo I da ED-05 |
| Premissa provisória da P3.15 (denominador exclui manutenção planejada) = decisão expressa pendente | P3.15 via Anexo I §4 da ED-05 (registro D5) |
| Risco residual regional declarado; nada fora de `sa-east-1` | LC.3/RNF-44 via Contrato-modelo §2.9 da ED-05 |
| Assinatura do contrato/DPA só na LC.9, antes do primeiro dado real | Aceite da P3.9 |

## Nota de conferência — B2-9 (Rodada 2, 08/11/2026)

A citação de `support.sla_policy` neste índice (e nas minutas) foi
**confirmada contra a P2.15**: a migration `core V10_2`
(`shared/ziipsan-core/src/main/resources/db/migration/core/V10_2__support_sla_csat.sql`,
blob na main `df29a1f0aeea2fcafd1d13ca4b1953b9c7fe986c`, cabeçalho
"P2.15 — SLA de suporte por contrato e CSAT") cria a tabela
`support.sla_policy` (política de SLA por tenant, linha 53, com
`UNIQUE (tenant_id, policy_key)`) e a tabela distinta
`support.sla_policy_target` (os cinco alvos S1–S5 de cada política,
linha 85, `policy_id` com FK para `support.sla_policy (id)`).

Não há conflito de nomes: a P3.9 cita a política (`support.sla_policy`,
ADR-0019 item 1 — o contrato referencia a política do próprio tenant) e
a P3.15 lê a tabela-filha (`support.sla_policy_target` —
`MonthlyContractReport.java:82`). A suspeita B2-9 fecha como
**confirmado, sem correção no texto das minutas**.
