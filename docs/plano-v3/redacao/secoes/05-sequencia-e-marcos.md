## 5. Sequência de dependências e marcos

A ordem das tarefas vem das dependências declaradas na coluna "Dep." e da precedência das fases: P0 < P1 < P2 < P3 <
homologação local < P4 = onda A < onda B < onda C < pacotes pendentes < lançamento. As datas abaixo são datas de
referência (ADR-0044): indicam sequência e ordem de grandeza e não geram cobrança por data. São as da v2.21, mantidas
sem recálculo; o P0 ainda não começou.

**Até a homologação local do piloto (GL.4).** O fecho de dependências do GL.4 tem 81 tarefas (29 do P0, 24 do P1, 13
do P2, 12 do P3 e 3 da homologação local). A cadeia mais longa, por número de tarefas, passa pelo cadastro e pelos
documentos fiscais antigos:

P0.1 → P0.7 → P0.3 → P0.5 → P1.9 (outbox) → P1.20 → P1.40 (importador genérico) → P1.3 (cadastro completo) → P1.41
(situações do titular) → P2.5 (ciclo) → P2.6 (NFAg-e) → P2.18 (XML imutável) → P2.19 (documentos anteriores à primeira
produção) → GL.1 (carga anonimizada) → GL.2 (paralelo local) → GL.4.

Entram também no fecho, por ramos de mesmo destino:

- **Piloto e legado:** P0.30 (piloto) → P1.1 (discovery) → P1.2 (importador do legado, que depende também da P1.31, da
  P1.39 e do ambiente local com R1, P0.27 → P0.28) → P2.10 → GL.1.
- **Núcleo de OS e protocolo:** P1.31 (núcleo de OS) → P1.36 (protocolo) → P2.20 (retificação de conta por OS) → GL.2.
- **Regulatório e fatura:** P1.32 → P1.34 (normas) → P2.31 (perfis das agências de SC) → P2.32 (conteúdo mínimo da
  fatura, com o motor de modelos da P2.30) → GL.2.
- **Arrecadação:** P3.1 → P3.2 → P3.29 (devolução e pagamentos não identificados) → GL.2.
- **Escopo por área e relatórios:** P2.28 (minhas tarefas) → P3.30 (escopo por área) → GL.2; P3.4 (corte e religação)
  → P3.22 (relatórios operacionais) → GL.2.
- **Restauração:** P0.28 → P3.33 (restauração seletiva) → GL.3 → GL.4.
- **Condicional:** P3.24 (FEBRABAN 150 legado) entra no GL.4 e na LC.13 só se o piloto tiver optantes e o parecer da
  RAS-06 mantiver o objeto (§2.4).

O núcleo do GIS (P1.26, P1.27, P2.21 a P2.23, P3.25 a P3.27) não está no fecho do GL.4: cada parte fecha com a sua fase,
e só a P3.26, que carrega a rede real do piloto, depende de tarefa do fecho (P1.9). Dentro do P3, a P3.26 vem antes da
P3.27 e da parte não essencial da P3.25 (cliente WMS, medição).

**Na trilha de editais.** Início da trilha (M11) → onda A, com o P4 em paralelo → ensaio de 120 pontos → grupo B1 da
onda B → ED-01 → prontidão de POC (M12) → grupo B2 e onda C → todas as fases e ondas concluídas, exceto os pacotes
pendentes → início do lançamento (M14). O atestado do piloto (P0.30 → LC.14) e o kit de habilitação (P0.44) correm em
paralelo.

**Até a primeira produção e o primeiro atestado.** GL.4 e ED-01, mais todas as ondas → LC.1 (ensaio de implantação
na AWS) → LC.2 (IaC e borda, com o domínio do tenant da P2.27) → LC.3 a LC.7 (RDS, KMS, S3, Keycloak, observabilidade)
→ LC.8 (pentest externo) → LC.9 (contrato e DPA) → LC.10 (carga real) → LC.11 (paralelo real) → LC.13 (corte, com a
LC.12, restauração da produção, antes) → LC.14 (primeiro atestado) → primeira licitação com POC (M13). A LC.15
(instância dedicada) depende da LC.2 e da ED-04 e fica fora desse caminho.

**Ressalvas que continuam valendo.** Cobrança por contrato, NFS-e da venda e área do tenant (P2.12 a P2.14) precedem a
assinatura do contrato do piloto (LC.9). O histórico com documentos no portal (P3.20) depende da NFAg-e (P2.6), da cópia
imutável do XML (P2.18), que depende do armazenamento de objetos local (P0.27, P0.45), e da importação dos documentos
anteriores à primeira produção (P2.19).

| Marco | Data de referência | Critério |
|---|---|---|
| M1 | 23/10/2026 | clientes-alvo comunicados |
| M2 | 12/10/2026 | hospedagem decidida: cumprido, ADR-0011 aceito |
| M3 | 13/11/2026 | piloto escolhido, com termo de cooperação para a homologação local (dados anonimizados na origem), aceite de que a produção começa no lançamento (opção A: lançamento a partir de outubro de 2028, corte por volta de fevereiro de 2029), critério de atestado (ADR-0023) e optantes de débito automático levantados (P3.24 confirmada ou cancelada) |
| M4 | 27/11/2026 | fim do P0: todas as tarefas da fase, com código limpo e renomeado, ambiente local completo (homologação e demonstração locais), interfaces com teste de contrato, PITR local ensaiado, R1 sobre o cofre local e kit de habilitação iniciado |
| M5 | 11/12/2026 | R3 em homologação local |
| M6 | 29/01/2027 | fim do P1: todas as tarefas da fase, entre elas cadastro do piloto importado e conciliado, núcleo de OS, protocolo de atendimento, cadastro mínimo de ativos, perfis de fábrica, roteiros de POC e primeiro ensaio registrados, normas ABNT compradas (P1.28) e modelo e catálogo do GIS (P1.26, P1.27) |
| M7 | 26/03/2027 | fim do P2: todas as tarefas da fase, entre elas ciclo, NFAg-e e retificação de conta em homologação local, perfis regulatórios de SC, conteúdo mínimo da fatura, cashback de CBS e IBS e topologia, pacotes de edição e vínculo comercial do GIS (P2.21 a P2.23) |
| M8 | 28/05/2027 | fim do P3: todas as tarefas da fase, pronto para a homologação local do piloto, com relatórios operacionais (e P3.24, se houver optantes); ensaio de POC com meta (≥ 50 de 162 na VISAN; SAMAE 2.4 e 2.5 sem falha); núcleo do GIS concluído (P3.25 a P3.27, GIS-01 a GIS-08) |
| M9 | 01/06/2027 | início da homologação local do piloto (paralelo local) |
| M10 | 30/07/2027 | homologação local do piloto aceita (GL.4); o legado continua oficial até o corte no lançamento |
| M11 | 02/08/2027 | início do P4 e da trilha de editais |
| M12 | meados de agosto de 2028 | prontidão de POC: onda A, grupo B1 da onda B e P4 concluídos (PD-01), inclusive o almoxarifado da ED-58, a edição web do GIS na onda A e o GIS da trilha, com o WMS pelo GeoServer; dois ensaios seguidos com pelo menos 146 de 162 pontos na VISAN e nenhuma falha crítica no SAMAE; esforço a recalcular (COE-12); plantão da S1 montado (ED-05) e acionado a partir da primeira produção |
| M13 | a partir do 3º trimestre de 2029 | primeira licitação com POC: atestado do piloto emitido (M16), ambiente de demonstração na nuvem (LC.2), kit de habilitação válido (balanços de 2027 e 2028), M12 cumprido e decisão de disputar registrada (consolidado 18, §7.3); primeiro alvo, edital do tipo SAMAE (um atestado) |
| M14 | outubro de 2028 | início do lançamento comercial: todas as fases e as ondas A, B e C concluídas, exceto os pacotes pendentes e sob demanda; esforço a recalcular (COE-12); orçamento da AWS aprovado; ensaio de implantação (LC.1) iniciado |
| M15 | por volta de fevereiro de 2029 | primeira produção: corte do legado do piloto (LC.13), emissão fiscal em produção |
| M16 | maio a julho de 2029 | primeiro atestado emitido (LC.14) |

Os marcos M13 a M16 seguem a ordem do caminho acima: se o fim das ondas vier depois da data de referência do M14, os
marcos seguintes acompanham, sem que isso seja tratado como atraso.

**Datas de norma que afetam o prestador.** São fatos normativos, não datas do projeto. Até o corte, no lançamento
comercial (LC.13), o piloto cumpre pelo sistema atual; o produto precisa chegar à primeira produção com as regras
vigentes nela (23e, inferência 4).

- NFAg-e obrigatória em 01/12/2026 (Ato Conjunto RFB/CGIBS 4/2026, art. 1º, XVI; optantes do Simples Nacional em
  01/01/2027); a NT 004 da NFAg (RTC) entra em produção em 16/11/2026, com a soma de IBS e CBS no total a partir de 2027 (P2.6).
- NFS-e Padrão Nacional em três etapas por grupo de serviço: 01/10/2026, 01/12/2026 e 01/01/2027 (P3.6).
- CNPJ alfanumérico em novas inscrições desde julho de 2026 (IN RFB 2.229/2024; P1.39).
- Tarifa social: adequação até 11/12/2026 (Lei 14.898/2024; P1.5).
- CBS e IBS a partir de 01/01/2027; cashback de CBS sobre consumo desde 01/2027 e de IBS desde 01/2029 (LC 214/2025,
  Decreto 12.955/2026; P2.4, P2.34).
- Débito em conta para convênios de serviços públicos: adequação até 01/01/2027 (Res. CMN 5.278/2025, Res. BCB
  544/2025); novo regime geral do débito automático em 01/07/2027 (Res. BCB 565/2026); o enquadramento de água e esgoto
  depende do parecer da RAS-06 (P3.24, P4.5, ED-27).
- Cobrança híbrida em 01/02/2027, facultativa, e arts. 3º e 11-S da Res. BCB 587/2026 em 01/07/2027 (P3.2).
- NR 11 da ANA: verificação das condições gerais a partir de 20/05/2027; CISAM Meio-Oeste RN 2026-05 em 20/05/2027,
  revogando a RN 01/2017 (P2.31). Lei 14.015, em vigor (P3.4).
- NR-10 do MTE em 01/06/2027 (P1.31, catálogo de SST).
- PCASP do exercício de 2027 (Portaria STN/MF 2.520/2026; ED-32).
- SINISA anual (P4.2); DMR trimestral (ED-104, sob demanda).
- Fim de suporte: Spring Boot 4.1 em 31/07/2027 (P3.14); PostgreSQL 15 em novembro de 2027 (P0.4).

---
