# PENDÊNCIAS NOMEADAS — LC.9

> **Produtor:** sessão `d9b12bda-ac3a-4b63-b86d-a806e37d4f49` (papel: Produtor).
> Nenhuma pendência abaixo é presumida resolvida. Contagens conferidas
> por grep deste produtor sobre a entrega da P3.9 (ver RELATORIO-LC.9).

## 1. Os 14 valores abertos herdados da P3.9 (placar da ED-05: 8 resolvidos × 14 abertos)

Origem: as 22 ocorrências de `[A DEFINIR NA P3.9]` do arcabouço da ED-05;
a P3.9 resolveu a redação/mecanismo de todos e manteve o **valor** aberto
nestes 14 (numeração = tabela do `RELATORIO-P3.9.md`). Fecham por decisão
do dono/cliente futuro **na contratação**.

| # ED-05 | Ponto aberto | Onde está o marcador |
|---|---|---|
| 2 | Qualificação das partes (razão social, CNPJ, endereço, representante) | Contrato, Cl. 1ª |
| 3 | Janela/exclusões da disponibilidade 99,9% | SLA, §§4–5 |
| 4 | Prazo numérico do aviso prévio (cláusula de aviso) | Contrato, Cl. 4ª, item 3 |
| 6 | Vigência, rescisão e penalidades gerais | Contrato, Cl. 11ª |
| 7 | Foro e disposições gerais | Contrato, Cl. 12ª |
| 9 | Prazos de 1ª resposta/solução por severidade (S1–S5) | SLA, §2 (tabela) |
| 10 | Metas de aderência S1–S5 | SLA, §2 (tabela) |
| 11 | Percentuais de multa `penalty_pct` S1–S5 | SLA, §§2–3 |
| 12 | Teto de multa de SLA | SLA, §3.3 |
| 13 | Janela de medição/exclusões (no anexo de SLA) | SLA, §§4–5 |
| 15 | Teto de `lgpd.operator_notice_hours` (aviso de incidente operador→controlador) — sem ele, a validação da P3.10 recusa o parâmetro | DPA, Cl. 4ª, item 2 |
| 17 | Prazo do aviso de mudança de suboperador (a forma escrita é **proposta redacional a confirmar**, decisão declarada da P3.9) | Suboperadores, §3 |
| 18 | Lista nominal de suboperadores: razão social/país (AWS; modelo de linguagem; Meta; Telegram; demais) | Suboperadores, §1 |
| 22 | Decisão de criar plantão S2–S5 (a minuta não o promete) | SLA, §6 |

**Contagem completa dos marcadores nas minutas (conferida):** **59
ocorrências** de `[A DEFINIR` nas 6 minutas (grep deste produtor:
Contrato 9, DPA 3, SLA 27, Suboperadores 11, Termo de Homologação 4,
Termos de Uso 5). Pela decomposição do `RELATORIO-P3.9.md`: **52
marcadores substantivos** + 6 menções explicativas do próprio marcador +
1 `[A DEFINIR PELA OPERAÇÃO]` (plantão S1, dado operacional). Os 14 itens
acima são o recorte **por ponto de valor** da tabela da ED-05; os demais
marcadores substantivos são campos das próprias minutas (ex.: índice de
reajuste, encarregado do controlador, catálogo de papéis) — todos
listados por arquivo no `RELATORIO-P3.9.md`.

## 2. Pendências próprias da LC.9

| # | Pendência | Natureza |
|---|---|---|
| P-LC9-01 | **Cláusula do atestado (ADR-0023)**: número de ciclos em produção + prazo contado do corte — **[SEM FONTE]**; cláusula inexistente nas minutas (FICHA, F-2; divergência D-LC9-01) | Decisão na contratação (redação jurídica futura) |
| P-LC9-02 | **Escolha do nível de serviço** (padrão × Setor Público, ADR-0025) | Decisão do contrato futuro |
| P-LC9-03 | **Assinatura** do Contrato SaaS e do DPA com o cliente futuro (modelo de licitação, item 34) — ato externo; e **aprovação das minutas** por esse cliente, que substitui a "aprovação pelo piloto" | Ato externo futuro |
| P-LC9-04 | **Declaração final da LC.3** a conferir contra a Cl. 10ª do Contrato na integração (pacote LC.3 ainda em produção na leitura deste produtor) | Dependência nomeada (LC.3) |
| P-LC9-05 | **Portão da LC.8**: Termo de Resultado com veredito LIBERADO (zero crítico/alto aberto) — pré-condição do aceite; hoje o pentest nem pode começar (produção AWS inexistente, P-LC8-01) | Dependência nomeada (LC.8) |
| P-LC9-06 | **Anexação do texto oficial das cláusulas-padrão da ANPD** (Res. CD/ANPD 19/2024) no ato da contratação — a minuta as incorpora por remissão e a **trava** da P3.9 bloqueia a aprovação enquanto suboperador estrangeiro estiver sem a incorporação comprovada | Ato da contratação |
| P-LC9-07 | **Decisão expressa sobre a premissa provisória da P3.15** (denominador da disponibilidade exclui manutenção planejada): confirmar no SLA ou corrigir o código — registada como DECISÃO EXPRESSA PENDENTE no SLA, §5 | Decisão dono/cliente na aprovação |
