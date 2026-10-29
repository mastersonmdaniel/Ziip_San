### P4 · 02/08–17/12/2027 (tarefas originais, redefinidas pelos ADRs 0026 a 0028) e trilha de editais · a partir de 02/08/2027

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P4.1 | R8: cobrança; negativação e protesto na ED-30 | R8 (v1) | G | P3.4 | parcelamento com demonstrativo; contestação bloqueia corte **Ajuste v2.18:** levantamentos 22a a 22d: CAD-73 Programas de regularização e renegociação por ato do ente; CAD-75 Superendividamento e processo de repactuação; CAD-100 Régua de cobrança com promessa de pagamento e priorização; CAD-104 Planos de pagamento por capacidade (fatura nivelada e acordo por renda); PLT-07 Assinatura eletrônica dos documentos do consumidor e do tenant, por nível da Lei 14.063; GES-24 Previsão de inadimplência e régua de cobrança adaptativa. |
| P4.2 | R6: SINISA assistido | R6 (v1), ADR-0004 | M | P2.5 | arquivo oficial; valor manual com justificativa; casos da v1 (água não faturada 3.000 m³; ILI); base da ED-48 **Ajuste v2.18:** levantamentos 22a a 22d: GES-16 SINISA: ciclo completo, mapa de variáveis e certificado de adimplência. |
| P4.3 | BI e prestação de contas em réplica: catálogo restante de relatórios, Superset e painéis padrão (ADR-0027) | 17 §5, ADR-0027 | G (24, contado na trilha: ED-46, ED-47, ED-49) | P0.28, P3.22 | aceites da ED-46, da ED-47 e da ED-49; nenhum relatório nem consulta de BI no primário |
| P4.4 | App de campo Android em Capacitor: base, autenticação e leitura offline (ADR-0026, ADR-0030) | 17:P3, ADR-0026, ADR-0030, ED-18 | G (5, contado na trilha: ED-18) | P2.3, P2.7 | aceite da ED-18; emissão simultânea (ED-19, ADR-0031) e OS de campo (ED-20) em seguida |
| P4.5 | R5c: Pix Automático | R5 (v1), ADR-0028 | M | P3.2 | cobrança recorrente no PSP do piloto; FEBRABAN 150 legado na ED-27 **Ajuste v2.18:** levantamentos 22a a 22d: GES-15 Pix Automático e cobrança híbrida: regras operacionais do Banco Central. |
| P4.6 | Schema compartilhado e banco dedicado | ADR-0005 | G | P1.14, P2.2 | só com demanda; mesmo teste de isolamento |
| P4.7 | Restauração mensal automatizada | ADR-0012 | P | P0.28 | relatório mensal no ambiente local; na produção, a partir da LC.3 |
| P4.8 | Suporte: resposta por e-mail, deflexão, assinatura de status, notas do tenant | ADR-0020 | G | P3.17, P3.18 | resposta por e-mail entra no chamado certo só de remetente verificado e sem anexo não varrido; deflexão cita artigo e transfere para humano; assinatura de status por e-mail **Ajuste v2.18:** levantamentos 22a a 22d: PLT-42 Telemetria de produto com privacidade (adoção e funil de implantação). |

O R6 fica depois do alvo de 30/04/2027 da v1 porque, sem cliente em produção antes do lançamento comercial (ADR-0033),
o primeiro envio assistido com dado do Ziip SAN é o do primeiro ano-base completo depois do corte (LC.13); até lá o
piloto envia pelo processo atual.

**2028, sob demanda:** trilha GRS (D-MTR-1..7, DMR e SINIR assistidos, lista IBAMA, NBR 10004 em sua série 2024, partes 1 e 2; a edição de 2004 foi cancelada, com transição até 31/12/2026, e a série 2024 usa duas classes, Classe 1 perigoso e Classe 2 não perigoso, sem a divisão IIA e IIB; verificado em 22/10/2026, a conferir no texto da ABNT); R9 sem o GIS (incidente,
N5, N1, N7, perdas com entrada real); integração por API com birô de crédito e com a CENPROT; ISO 27001. Saíram desta
lista para a trilha de editais do P4 (v2.4): GIS web, edição, migração do cadastro técnico e manobras, negativação e
protesto por arquivo e qualidade da água (ADR-0028); instância dedicada (ADR-0024); desde a v2.10, o núcleo do GIS
está no P1–P3 e o restante do GIS na trilha (ADR-0034). **2029, sob demanda (v2.5):**
compras, RH e orçamento do ERP (ADR-0028, item i, ajuste v2.5).

