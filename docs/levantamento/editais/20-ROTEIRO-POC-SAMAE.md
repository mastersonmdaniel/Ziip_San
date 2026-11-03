# 20 — Roteiro interno de POC: SAMAE São Ludgero/SC (PE 2/2026, "SIGCOS")

Roteiro interno da P1.25 (ED-01), a partir de [18c §3](18c-sigcos-pe-2-2026.md)
(Edital 7 e TR 13) e da tabela de requisitos de
[18c §4](18c-sigcos-pe-2-2026.md). Não é o roteiro oficial da comissão: os 80
itens avaliados são escolhidos pela comissão e divulgados junto com a
convocação (18c §3, Edital 7.6.4), e todos os requisitos técnicos do TR são
passíveis de avaliação (7.7.2–7.7.4).

## Regras (18c §3)

- POC **exclusivamente eliminatória**, só da licitante provisoriamente em 1º
  lugar; reprovada, convoca-se a seguinte (7.1–7.2, 7.10).
- Convocação com **7 dias úteis** de antecedência (retificado; era 2); POC
  **presencial na sede** do SAMAE, podendo ocupar mais de um dia (7.2.6, 7.6.6).
- Sistema **completo em nuvem**, em plenas condições operacionais (7.2.2, 7.3);
  outro ambiente = desclassificação.
- Base de **pelo menos 10.000 unidades consumidoras**, somente dados fictícios
  ou anonimizados; descumprir a LGPD = desclassificação imediata (7.4).
- Infraestrutura (equipamentos, dispositivos móveis, internet 4G/5G) por conta
  da licitante; SAMAE fornece apenas o espaço (7.2.7). Equipe: 1 a 2
  especialistas (7.5.3). Sessão gravada em áudio e vídeo pela Administração
  (7.12–7.13).
- **80 itens binários** (Atende / Não Atende, sem parcial); atendimento =
  demonstração prática (inclusão de registros, cálculos, consultas, relatórios,
  integrações, logs) (7.7.5, 7.7.11).
- **90% dos essenciais**; essenciais não demonstrados continuam obrigatórios e
  devem ser implementados na implantação sem custo (7.7.6, 7.7.6.1).
- **Itens críticos (Edital 7.7.9), sempre com subitens, são eliminatórios por
  si sós**: falhar em qualquer um = desclassificação imediata, sem
  implementação posterior (7.7.7–7.7.8, 7.7.10). Devem obrigatoriamente constar
  entre os itens avaliados.
- Não são exigidos testes que dependam de integração com sistemas atuais do
  SAMAE ou de desenvolvimento ligado à legislação municipal (7.2.5).

## Lista crítica (Edital 7.7.9), com subitens

Cada seção abaixo lista a faixa de SG da tabela de 18c §4 (texto fiel de cada
subitem está lá; as faixas são compactas e os intervalos são contínuos, salvo
onde indicado). "ED / fase" vem do consolidado 18 §3.2. "Ensaio" = PENDENTE
até o primeiro ensaio, ao fim do P1 (formato de registro igual ao do roteiro
VISAN, [19](19-ROTEIRO-POC-VISAN.md); o registro no diário é do coordenador).

| Seção crítica | Subitens (18c §4) | ED | Tarefa/fase (consolidado §3.2) | Ensaio |
|---|---|---|---|---|
| 1.1.2 (todo o TR, incl. SLA, segurança e LGPD) | sem SG próprio: abrange todos os 1.415 SG | todas | todas | PENDENTE |
| "Item 0" (todos os módulos em base única, nativos) | SG-013 (parágrafo após o TR 2.2.2.4) | ED-42, ED-47, ED-50, ED-52 | P1–P3 (GIS) e P4 onda B | PENDENTE |
| 2.4 Banco de dados | SG-027–SG-035 (9) | — | ensaio local (PostgreSQL 17); POC real só com o RDS do lançamento (ADR-0011, ADR-0033) | PENDENTE |
| 2.5 Desempenho (10 s; arquivos e relatórios em 5 min; alertas) | SG-036–SG-043 (8) | ED-07, ED-05 | P0.23, P2.9, P3.22 (parcial; faltam alertas ao cliente) | PENDENTE |
| 2.7 Controle de acesso (inclui Active Directory) | SG-047–SG-065 (19) | ED-06 | P4 onda A (sem AD nem perfis definidos pelo cliente) | PENDENTE |
| 2.8 Cadastro | SG-066–SG-156 (91) | ED-12, ED-13, ED-53 | P4 onda A; base da ligação na P2.23 | PENDENTE |
| 2.9 Hidrometria | SG-157–SG-172 (16) | ED-14, ED-17 | P4 onda A | PENDENTE |
| 2.10 Leituras e consumos | SG-173–SG-209 (37) | ED-15, ED-17 | P2.5, P2.7 (parcial) | PENDENTE |
| 2.11 Leitura e faturamento móvel | SG-210–SG-241 (32) | ED-18, ED-19, ED-16 | P4 onda A | PENDENTE |
| 2.12 Revisão de consumo, incl. 2.12.5 (em dispositivo móvel, sem papel) | SG-242–SG-263 (22) | ED-23, ED-20 | P4 onda A | PENDENTE |
| 2.13 Faturamento (inclui resíduos sólidos) | SG-264–SG-325 (62) | ED-22, ED-25, ED-24 | P4 onda A | PENDENTE |
| 2.14 Arrecadação | SG-326–SG-342 (17) | ED-26, ED-27 | P3.1, P3.2 (parcial) | PENDENTE |
| 2.15 Cobrança (negativação, protesto, dívida ativa) | SG-343–SG-428 (86) | P4.1, ED-29, ED-30, ED-31 | P4 onda A | PENDENTE |
| 2.16.18 Importação e exportação de lançamentos contábeis | SG-447 (1) | ED-32, ED-62 | P4 onda A | PENDENTE |
| 2.17 Gestão de serviços (inclui empreiteiras e estoque) | SG-448–SG-505 (58) | ED-34, ED-36, ED-37, ED-58, ED-62 | P4 onda A | PENDENTE |
| 2.18 OS móvel | SG-506–SG-519 (14) | ED-20 | P4 onda A | PENDENTE |
| 2.19 Atendimento | SG-520–SG-600 (81) | ED-35, ED-21 | P3.4 (parcial) | PENDENTE |
| 2.22 Omnichannel | SG-628–SG-669 (42) | ED-42, ED-43 | P4 onda B | PENDENTE |
| 2.24 App de autoatendimento nas lojas | SG-704–SG-782 (79) | ED-39 | P4 onda B | PENDENTE |
| 2.26 Qualidade da água | SG-789–SG-822 (34) | ED-50, ED-51 | P4 onda B | PENDENTE |
| 2.27 SIG/GIS (e implantação do GIS, 3.11.10) | SG-823–SG-889 (67) | ED-52, ED-53–ED-55, ED-63, ED-67 | P1–P3 (núcleo); restante P4 | PENDENTE |
| 2.28 Relatórios gerenciais | SG-890–SG-1062 (173) | ED-46 | P3.22 (parcial) | PENDENTE |
| Item 3 Manutenção e suporte (severidades, telefone, 99,9%) | SG-1278–SG-1365 (88) | ED-05 | P2.15 (cinco severidades) | PENDENTE |

### Reconciliação da contagem (não inventada: apurada sobre 18c §4)

- Por seção (lista acima): 1.036 SG.
- Pela coluna Obrigatoriedade de 18c §4 (`S:critical` na matriz viva): 1.037 SG.
- Diferença simétrica de 3 linhas, todas identificadas:
  - SG-013 ("Item 0", 2.2.2.4-A): crítico pela obrigatoriedade, fora das seções
    críticas — incluído acima como linha própria.
  - SG-446 (2.16.17): essencial pela obrigatoriedade, com texto idêntico ao
    2.16.18 crítico (duplicidade de numeração anotada em 18c §4) — tratar como
    crítico na preparação, por identidade de texto; a decisão formal é
    PENDENTE (comissão, na convocação).
  - SG-822 (2.26.24, ISO/IEC 17025): crítico pela seção, mas com prazo de "até
    6 meses após a implantação" na obrigatoriedade — incluído acima, com o
    prazo anotado; se a comissão o cobrar na POC, o prazo não socorre.
- Universo de preparação (união): **1.038 SG**. O consolidado 18 §3.2 diz
  "cerca de 1.037 das 1.415 linhas" — compatível, dentro do arredondamento e
  das 3 linhas de fronteira acima.

## Itens sorteáveis (os 80, escolhidos pela comissão)

Os 80 itens são escolhidos pela comissão e divulgados na convocação — **não é
possível listá-los antes**. O que este roteiro fixa é o universo de onde saem
os 90% e o que é apenas avaliável (18c §3):

| Grupo | Seções (faixas SG em 18c §4) | Papel na POC |
|---|---|---|
| Essenciais não críticas (contam nos 90%) | 2.1 (SG-002–SG-004), 2.3 (SG-021–SG-026), 2.6 (SG-044–SG-046), 2.16 exceto 2.16.18 (SG-429–SG-446), 2.20 portal (SG-601–SG-613), 2.21 totem (SG-614–SG-627), 2.29 BI (SG-1063–SG-1277) | 90% de aprovação, junto com os críticos já cobertos |
| Avaliáveis (não marcadas como essenciais) | 2.2 Arquitetura (SG-005–SG-020), 2.23 IA (SG-670–SG-703), 2.25 Telefonia (SG-783–SG-788) | passíveis de avaliação (7.7.2–7.7.4) |

Ressalva da matriz 18f §2.0, mantida aqui: no SIGCOS a comissão escolhe 80
itens e qualquer requisito do TR pode entrar. O preparo, portanto, não se
limita a esta tabela: cobre a matriz viva inteira, e a lista dos 80 sorteados
só se fecha na convocação — campo PENDENTE abaixo.

| Campo | Valor |
|---|---|
| 80 itens sorteados | PENDENTE (comissão, na convocação, 7 dias úteis antes) |
| Resultado por item (atende / não atende) | PENDENTE (primeiro ensaio, ao fim do P1) |
| Percentual de essenciais | PENDENTE (só calculável após o ensaio) |

## Kit de campo e ambiente (18 §3.3; 18h-E3)

- Tenant de demonstração permanente com pelo menos 10.000 unidades fictícias
  (massa sintética da P0.23), sem dado pessoal real, recriável a cada fase
  (P3.23). Até o lançamento comercial o ambiente é local (ADR-0033); a POC
  real exige nuvem (versão criada pela IaC do lançamento, LC.2, antes da
  primeira POC de licitação).
- Aparelhos Android, um iPhone para o app do cliente, impressora Zebra série ZQ
  com bobinas (ADR-0026), acesso 4G próprio — tudo por conta da licitante no
  SAMAE.
- Ensaio cronometrado ao fim de cada fase (a partir do P1), com o prazo de
  preparo do edital (7 dias úteis) e o resultado registrado na matriz viva
  (campo `rehearsal`); a partir do P3, meta por fase no aceite (P3.23).

## Fontes

- [18c §3](18c-sigcos-pe-2-2026.md) (regras), [18c §4](18c-sigcos-pe-2-2026.md)
  (subitens e obrigatoriedade), [18 §3.2](18-ADERENCIA-EDITAIS-CONSOLIDADO.md)
  (EDs e fases), [18 §3.3](18-ADERENCIA-EDITAIS-CONSOLIDADO.md) (ambiente e kit).
