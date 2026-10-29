## 5. Caminho crítico e marcos

Caminho crítico até a homologação local do piloto (v2.7, ADR-0033): ambiente local completo, interfaces, PITR local e R1
sobre o cofre local (P0.27, P0.45, P0.28) → importador com dado anonimizado na origem (P1.2) → outbox (P1.9) → lote
(P2.1) → ciclo (P2.5) → NFAg-e (P2.6) → retificação de conta (P2.20) → arrecadação (P3.1, P3.2) → relatórios
operacionais (P3.22) → paralelo local (GL.2) → aceite da homologação local (GL.4). Ramo igualmente crítico: piloto
(P0.30) → discovery (P1.1) → importadores → carga anonimizada (GL.1). Não há folga: atraso no piloto ou no ambiente
local desloca a homologação local na mesma medida. A hospedagem, decidida em 12/10/2026 (ADR-0011), saiu do caminho até
o lançamento.

Caminho até a primeira produção e o primeiro atestado (opção A do §2.4, v2.8): fim de todas as fases, inclusive a
onda C da trilha de editais, até 30/09/2028 → ensaio de implantação na AWS (LC.1) → IaC, RDS, KMS, S3, Keycloak e
operação (LC.2–LC.7) → pentest externo (LC.8) → contrato e DPA (LC.9) → carga real (LC.10) → paralelo real (LC.11) →
restauração (LC.12) → corte (LC.13) → atestado (LC.14) → primeira licitação com POC (M13). Do início do lançamento ao
atestado são de 7 a 9 meses; a primeira licitação com POC fica a partir do 3º trimestre de 2029.

A versão 2.4 põe no caminho a P2.20 e a P3.22, que o paralelo exige (contas divergentes precisam ser retificadas e
conferidas por relatório), e acrescenta ao P0–P3 cerca de 13 semanas-pessoa de esforço (consolidado 18, §5.2). Se o P2
ou o P3 atrasar, escorregam primeiro a meta de ensaio da P3.23 (o ensaio continua, sem
meta no aceite), os itens do nível Setor Público na P3.15 e na P3.16 e as tarefas fora do caminho já previstas no §6;
P2.20 e P3.22 ficam. A trilha de editais começa depois da homologação local do piloto e não está no caminho dele; o
caminho dela é início da trilha (M11) → onda A → ensaio com meta de 120 pontos → onda B → prontidão de POC (M12) → primeira
licitação (M13), com o atestado do piloto (P0.30 → LC.14, desde a v2.7) e o kit de habilitação (P0.44) em paralelo.

A versão 2.5 só toca o caminho do piloto num caso: se a P0.30 encontrar optantes de débito automático, a P3.24
(≈ 1,5 semana) entra no P3 e o aceite da homologação local (GL.4) e o corte no lançamento (LC.13, desde a v2.7)
passam a depender dela. Nesse caso escorregam primeiro os mesmos itens
listados acima.

A versão 2.6 não toca o caminho do piloto. No caminho da trilha, a onda A passa a incluir o almoxarifado (ED-58),
executado logo depois da OS completa (ED-36), com a baixa de campo depois da ED-20: são +7,5 semanas-pessoa de esforço
antes do ensaio de 120 pontos, compensadas pela onda C, que cai para ≈ 15,4 semanas-pessoa.

A versão 2.7 tira do caminho do piloto a infraestrutura na AWS: P0.27 e P0.28 passam a versões locais, sem dependência
da P0.26, e entra a P0.45; o caminho até a homologação local fica igual ou menor. O caminho até o atestado passa a
incluir a fase LC, e a primeira licitação com POC dependia da escolha do §2.4: na opção B, o lançamento correria em
paralelo com a trilha. A escolha foi a opção A (v2.8).

A versão 2.8 fixa a opção A e põe a trilha de editais inteira no caminho até o atestado: onda A → ensaio de 120 pontos
→ onda B → prontidão de POC (M12) → onda C → início do lançamento (M14) → fase LC → primeira produção (M15) → atestado
(M16) → primeira licitação (M13). A prontidão de POC continua coerente, por ser anterior ao lançamento, mas deixa de
ser o marco limitante: o limite é a trilha completa até 30/09/2028 para o M14; atraso da trilha além de setembro de
2028 desloca M14, M15, M16 e M13 na mesma medida. O caminho do piloto até a homologação local não muda.

A versão 2.9 não toca o caminho: a guarda dos XML da homologação fiscal local (P1.8, P3.7) não o altera.

A versão 2.10 (ADR-0034) põe o núcleo do GIS no P1–P3 (P1.26, P1.27, P2.21 a P2.23, P3.25 a P3.27, ≈ 22
semanas-pessoa de esforço), com cada parte no marco da sua fase: modelo e catálogo no M6, topologia, pacotes e vínculo
comercial no M7, mapa web, importador e temáticos no M8. Ele não está no caminho do faturamento paralelo (GL.2) nem
depende dele, e só a P3.26 depende de tarefa do caminho (P1.9, outbox e `worker`). Dentro do P3, a P3.26, que carrega
a rede real do piloto, vem antes da P3.27 e da parte não essencial da P3.25 (cliente WMS, medição); se o P3 atrasar,
estas escorregam primeiro, depois os itens já listados acima; P2.20 e P3.22 ficam. Na trilha, o esforço sobe ≈ 18,5
semanas-pessoa (trilha completa com as tarefas originais do P4 ≈ 233,2).

A versão 2.11 acrescenta 1 semana-pessoa à ED-67 (WMS servidor autenticado por tenant): trilha completa com as tarefas
originais do P4 ≈ 234,2 e prontidão de POC ≈ 217,8. A base da ligação na P2.23, a rede real do piloto na homologação
local e o piso de agregação não mudam o caminho.

A versão 2.12 retira do plano a gestão de equipe e capacidade (§2.4). As datas das fases e dos marcos são metas de
calendário: homologação local de 01/06 a 30/07/2027, prontidão de POC por volta de meados de agosto de 2028, trilha
completa até 30/09/2028 e lançamento a partir de outubro de 2028. O WMS da ED-67 passa ao GeoServer (ADR-0010,
Exceções), sem mudar a onda nem a estimativa.

As tarefas da versão 2.1 não mudam esse caminho, com três ressalvas: a renomeação (P0.33) vem logo depois da P0.1 e
antes de toda tarefa de código do P0; cobrança por contrato, NFS-e da venda e
área do tenant (P2.12–P2.14) precedem a assinatura do contrato do piloto (LC.9, desde a v2.7); e o histórico com
documentos no portal (P3.20) depende da NFAg-e (P2.6), da cópia imutável do XML (P2.18), que depende do armazenamento
de objetos local (P0.27, P0.45), e da importação dos documentos anteriores à primeira produção (P2.19). As decisões
das versões 2.2 e 2.3 não alteram o caminho; a troca dos códigos de problema cabe na P0.33, que já toca todos os pontos
que os emitem.

| Marco | Data | Critério |
|---|---|---|
| M1 | 23/10/2026 | clientes-alvo comunicados |
| M2 | 12/10/2026 (previsto para 30/10) | hospedagem decidida: cumprido, ADR-0011 aceito |
| M3 | 13/11/2026 | piloto escolhido, com termo de cooperação para a homologação local (dados anonimizados na origem), prazo de produção no lançamento aceito, critério de atestado (ADR-0023) e optantes de débito automático levantados (P3.24 confirmada ou cancelada); prazo de produção da opção A comunicado no termo de intenção (lançamento a partir de outubro de 2028, corte por volta de fevereiro de 2029; momento do lançamento escolhido em 12/10/2026, v2.8) |
| M4 | 27/11/2026 | fim do P0: ambiente local completo (homologação e demonstração locais), interfaces com teste de contrato, PITR local ensaiado, R1 sobre o cofre local; kit de habilitação iniciado |
| M5 | 11/12/2026 | R3 em homologação local |
| M6 | 29/01/2027 | fim do P1: cadastro do piloto importado e conciliado; roteiros de POC e primeiro ensaio registrados; normas ABNT compradas (P1.28); modelo e catálogo do GIS (P1.26, P1.27) |
| M7 | 26/03/2027 | fim do P2: ciclo, NFAg-e e retificação de conta em homologação local; topologia, pacotes e vínculo comercial do GIS (P2.21 a P2.23) |
| M8 | 28/05/2027 | fim do P3: pronto para a homologação local do piloto, com relatórios operacionais (e P3.24, se houver optantes); ensaio de POC com meta (≥ 50 de 162 na VISAN; SAMAE 2.4 e 2.5 sem falha); núcleo do GIS concluído (P3.25 a P3.27, GIS-01 a GIS-08) |
| M9 | 01/06/2027 | início da homologação local do piloto (paralelo local) |
| M10 | 30/07/2027 | homologação local do piloto aceita (GL.4); legado continua oficial até o corte no lançamento (antes: go-live com legado desligado) |
| M11 | 02/08/2027 | início da trilha de editais |
| M12 | por volta de meados de agosto de 2028 (meta de calendário); na opção A (v2.8), anterior ao lançamento e não limitante: o limite é a trilha completa até 30/09/2028 (M14) | prontidão de POC: ondas A e B concluídas (≈ 217,8 semanas-pessoa de esforço com as tarefas originais do P4, inclusive o almoxarifado da ED-58 e a edição web do GIS na onda A e o GIS da trilha, com o WMS pelo GeoServer, na onda B); dois ensaios seguidos com pelo menos 146 de 162 pontos na VISAN e nenhuma falha crítica no SAMAE; plantão da S1 montado desde 31/01/2028 e acionado a partir da primeira produção (ajuste v2.7) |
| M13 | a partir do 3º trimestre de 2029 (opção A do §2.4, escolhida na v2.8; antes: 2º semestre de 2028) | primeira licitação com POC: atestado do piloto emitido (M16), ambiente de demonstração na nuvem (LC.2), kit de habilitação válido (balanços de 2027 e 2028), M12 cumprido e decisão de disputar registrada (consolidado 18, §7.3); primeiro alvo, edital do tipo SAMAE (um atestado) |
| M14 | outubro de 2028 (opção A do §2.4) | início do lançamento comercial: todas as fases concluídas, inclusive a onda C da trilha de editais (até 30/09/2028; ≈ 234,2 semanas-pessoa de esforço com o GIS do ADR-0034 e o WMS da v2.11); orçamento da AWS aprovado; ensaio de implantação (LC.1) iniciado. Atraso da trilha desloca este marco e os seguintes na mesma medida |
| M15 | por volta de fevereiro de 2029 | primeira produção: corte do legado do piloto (LC.13), emissão fiscal em produção |
| M16 | maio a julho de 2029 | primeiro atestado emitido (LC.14) |

**Prazos legais do prestador.** Até o corte, no lançamento comercial (LC.13, por volta de fevereiro de 2029), o piloto cumpre pelo sistema atual:
NFAg-e 01/12/2026, a confirmar (verificado em 22/10/2026: data confirmada pelo Ato Conjunto RFB/CGIBS 4/2026, de 30/07/2026, art. 1º, XVI, e pelo cronograma da Receita Federal; optantes do Simples Nacional têm data própria de 01/01/2027) (Ziip SAN no P2, emite em produção só após o corte); tarifa social 11/12/2026 (P1,
satélite); CBS/IBS 01/01/2027 (P2); boleto híbrido 01/02/2027, facultativo (P3); NR 11 em 20/05/2027 e Lei 14.015, em
vigor (P3); SINISA anual (P4); DMR trimestral (2028).

---

