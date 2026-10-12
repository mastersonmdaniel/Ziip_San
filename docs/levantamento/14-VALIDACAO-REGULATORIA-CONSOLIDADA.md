# Validação regulatória consolidada (2015 a 12/10/2026)

Síntese dos relatórios `08` a `13`, cada um produzido por um agente que pesquisou normas na web e conferiu o código do SIGSAN. Esta é a resposta curta à pergunta: **"o sistema vai atender as exigências legais e regulatórias?"**

> **Não é parecer jurídico.** Os relatórios de origem são levantamento técnico para orientar requisitos de software. Toda decisão sobre prazo, multa, juros, tarifa social, corte, negativação e tributos exige revisão por advogado, tributarista e regulatório. As fontes primárias nem sempre abriram no ambiente de pesquisa: o relatório `08` leu os textos legais na Câmara dos Deputados (o Planalto e o DOU não responderam); o `13` leu o Planalto por navegador; vários prazos de 2026 vêm de **fonte secundária**, marcados nos relatórios de origem.

> **Atualização (12/10/2026, fim do dia):** vários defeitos de código citados aqui já foram corrigidos (RLS e dono do banco, tenant pelo token, auditoria sem dado pessoal, cifra de CPF/CNPJ no cadastro e no MTR, JSON, MTR com testes, aplicação executável). O estado atual está no diário, seção 18; a ordem de prioridade, em `docs/PLANO-EXECUCAO-PDCA-R0-R9.md`.

---

## 1. Resposta direta

**Hoje, não.** E não por falha de qualidade: porque os módulos que carregam a obrigação ainda não existiam. Tarifa, fatura, corte, religação, protocolo, SINISA, retorno bancário e atendimento dependem de `commercial/*` e `operations/*`, que estavam vazios até esta rodada.

| Área (relatório) | Requisitos levantados | NÃO atende | Parcial | Atende | Observação |
|---|---|---|---|---|---|
| Saneamento, tarifas, SINISA (`08`) | 42 | ~36 | ~6 | 0 | Nada comercial existia |
| Qualidade da água (`09`) | ~46 | ~33 | ~13 | 0 | O seed de parâmetros divergia da norma e foi removido |
| LGPD e privacidade (`10`) | ~48 | ~38 | ~8 | 1 | Estrutura existe; a operação, quase nada |
| Cobrança, bancário, fiscal (`11`) | 49 | ~27 | ~23 | 1 | "Parcial" só onde o relatório 03 já previa algo |
| Resíduos, MTR, SINIR (`12`) | ~48 | ~33 | ~12 | 0 | MTR do SIGSAN não tem valor legal sem o SINIR |
| Atendimento, acessibilidade, assinatura (`13`) | ~35 | ~34 | 0 | 0 | Módulo de atendimento não existe |

Contagens aproximadas (contadas por varredura dos quadros de cada relatório). O que vale é a direção: **o cadastro e a base de isolamento estão sólidos; as obrigações regulatórias concretas ainda estão por construir**, e o plano abaixo as ordena por prazo.

---

## 2. O que mudou em 2025 e 2026 e que o plano anterior ignorava

| Fato | Origem | Efeito no SIGSAN |
|---|---|---|
| **Tarifa Social** (Lei 14.898/2024 + NR 13/2025 da ANA): implantação até **11/12/2026** | `08` | P0 mais urgente. Classificação por CadÚnico/BPC, desconto de 50% até 15 m³ (valores do relatório, a conferir no DOU), aviso de 3 meses antes de retirar o benefício, relatório anual |
| **NFAg-e (modelo 75) obrigatória em 01/12/2026** (Ato Conjunto RFB/CGIBS nº 4/2026) | `11` | Quatro fontes secundárias concordam na data; o DOU não foi lido e a SVRS ainda a lista como minuta. Confirmar antes de construir |
| **Reforma tributária**: água e esgoto ficam na alíquota padrão (sem redução na LC 214); PIS/Cofins terminam em 2027; fatura precisa prever *cashback* de CBS/IBS | `11` | Documento fiscal e cálculo da conta |
| **Débito automático mudou em 13/10/2025**: para recebedor PJ, autorização e cancelamento seguem o Pix Automático (Res. CMN 5.251 e BCB 505) | `11` | O relatório 03 copiava o FEBRABAN 150 e precisa ser revisto |
| **Boleto**: Res. BCB 443/2024 revogou as Circulares 3.598, 3.656 e 3.956 em 03/02/2025; fator de vencimento reiniciou em 1000 em 22/02/2025 (só no boleto de cobrança, não na conta de consumo, código 8) | `11` | Código de barras |
| **Layout FEBRABAN de arrecadação versão 08** vale desde 01/06/2026 (segmento 2 = Saneamento, CNPJ alfanumérico). CNAB 240 V11.0 (11/09/2026) é preliminar | `11` | Parser de retorno |
| **Corte**: aviso prévio mínimo de 30 dias (Lei 11.445, art. 40 §2º) e **proibido iniciar corte de sexta a domingo, em feriado ou véspera** (Lei 14.015/2020) | `08`, `11`, `13` | O sistema deve **impedir** a ordem de serviço, não só avisar |
| **16 Normas de Referência da ANA** (NR 1 a 16; a NR 16 saiu em 28/08/2026; a NR 2 foi revogada pela NR 8) | `08` | NR 11 (condições gerais), NR 13 (tarifa), NR 15 (perdas, balanço padronizado base SINISA), NR 9 (indicadores) |
| **Decretos 10.710/2021, 11.466/2023 e 11.467/2023 revogados**; valem o 11.598 e o 11.599/2023 (alterado pelo 12.805/2025) | `08` | Comprovação de capacidade econômico-financeira |
| **LGPD**: Lei 15.352/2026 (25/02/2026) transformou a ANPD em agência reguladora; nenhuma obrigação material mudou | `10` | Nada a mudar no código |
| **Decreto 12.975/2026** (em vigor desde 20/07/2026): guardar a **porta lógica de origem** junto do IP no registro de acesso | `13` | O SIGSAN não registra login nem leitura |
| **Portaria GM/MS 2.472/2021** substituiu o Anexo 13 da Portaria 888 (amostragem físico-química) e tirou o pH do art. 44 §3 | `09` | O catálogo de qualidade ignorava isso |
| **Portaria MMA 280/2020 (MTR) em revisão** (consulta pública jan/fev 2025); dúvida sobre base legal, porque ela regulamenta o Decreto 7.404, revogado | `12` | Risco regulatório do módulo MTR |
| **ANTT**: 5.232/2016 e 5.947/2021 revogadas; vale a 5.998/2022 (alterada pelas 6.016/2023 e 6.056/2024) | `12` | Transporte de resíduos perigosos |

---

## 3. Calendário (o que já venceu e o que vem)

| Data | Marco | Fonte |
|---|---|---|
| 31/10/2026 | DMR do 3º trimestre (resíduos) | `12` |
| 03/11/2026 | SINISA: versão preliminar | `08` |
| 17/11/2026 | SINISA: validação | `08` |
| 01/12/2026 | NFAg-e obrigatória (**a confirmar no DOU**) | `11` |
| 11/12/2026 | Prazo de implantação da Tarifa Social | `08` |
| 31/12/2026 | SINISA: Atestado de Adimplência (condição para recursos federais) | `08` |
| 01/02/2027 | Cobrança híbrida Pix + boleto | `11` |
| 20/05/2027 | ANA passa a verificar a NR 11 (atendimento: 24x7, protocolo numerado, relatório de reclamações, histórico de 120 meses) | `08`, `13` |
| 01/07/2027 | Novo regime de débito automático | `11` |
| 31/12/2027 | Fim da dispensa dos incisos VII–IX do art. 50 para recursos federais (Decreto 12.805/2025) | `08` |
| 20/05/2028 | Verificação das NR 13 (tarifa) e NR 15 (perdas) | `08` |
| 31/12/2033 | Metas: 99% água / 90% esgoto, por município, verificadas anualmente | `08` |

Já venceram: prazos de esporos (05/2022) e dureza (05/2023) da Portaria 888; a revisão de 5 anos da Portaria 888 (05/2026) passou sem texto novo publicado.

---

## 4. Requisitos de arquitetura que saem dos relatórios

1. **Perfil regulatório por tenant, com vigência no tempo** (`08`, R-39). As agências divergem em prazo de corte (ADASA só corta com 60 dias de atraso e protege saúde, escolas e baixa renda por 12 meses; ARSESP veda cortar por débito de outro serviço), ciclos de consumo por média (de 3 a 6), e índice de correção do débito (Selic, INPC, IPCA ou o do reajuste). **Nenhuma regra dessas pode ser constante no código.**
2. **Defaults de multa e juros são travas legais parametrizáveis**, não valores (`11`): teto de 2% de multa (CDC art. 52 §1º); juros e correção vêm do contrato e da agência.
3. **Bloqueio, não aviso**: o sistema impede a OS de corte que viole a Lei 14.015 (`08`, `11`, `13`).
4. **Conformidade de qualidade da água não é um valor máximo** (`09`): coliformes totais variam por população, turbidez é percentil mensal, subprodutos são média móvel de 12 meses. O modelo precisa de **regra de avaliação**, de plano de amostragem anual (população atendida, tipo de manancial) e de comunicação imediata de não conformidade, que hoje é um `log.warn`.
5. **MTR/CDF só têm valor legal se emitidos no SINIR ou em sistema estadual** (`12`). O SIGSAN, hoje, produz um documento sem valor. SP, MG, RJ, RS, SC e ES têm sistemas próprios, com validade, DMR e CDF diferentes; SP e SC exigem MTR duplo no interestadual.
6. **O Ziip SAN é operador de dados (LGPD), mas vira controlador** se usar dados dos tenants para treinar IA ou benchmark (`10`). O contrato precisa travar isso.
7. **Cifra de campo precisa existir antes do primeiro dado real** (`10`): o AES-256-GCM por tenant era só decisão do diário; esta rodada implementou o primitivo (`sigsan-security`), mas **a integração com o Vault ainda não existe**, e o `audit_log` copia a linha inteira, inclusive dado pessoal em claro.

---

## 5. Defeitos de código que os relatórios regulatórios apontaram

| Defeito | Onde | Origem |
|---|---|---|
| `IN_TRANSIT` inalcançável: `receiveMtr` falha sempre | `grs/sigsan-mtr` | `12` |
| Hash do MTR cai para UUID aleatório sem avisar; status do SINIR nunca é gravado | `grs/sigsan-mtr` | `12` |
| `MtrService` não tem nenhum teste | `grs/sigsan-mtr` | `12` |
| `LabService` só grava a conformidade que o chamador informa | `intelligence/sigsan-quality` | `09` |
| Comunicação de não conformidade é só `log.warn` | `intelligence/sigsan-quality` | `09` |
| Código de LGPD cita "art. 18" para o prazo de resposta, que é o art. 19 II | `compliance/sigsan-lgpd` | `10` |
| `GovBrSignatureService` confunde assinatura gov.br (avançada) com ICP-Brasil (qualificada) | `grs/sigsan-mtr` | `13` |
| `tenant_settings.tenant_id UNIQUE` (uma configuração por tenant) | `shared/sigsan-core` | `13` (**corrigido**) |
| Seed de parâmetros de qualidade com 16 de 28 linhas divergentes da Portaria | `intelligence/sigsan-quality` | `09` (**removido**) |
| Frontend quase sem atributos de acessibilidade (Lei Brasileira de Inclusão, art. 63) | `frontend` | `13` |

---

## 6. Plano de atendimento, ordenado pelo prazo mais próximo

> **Superado em 12/10/2026.** A ordem de prioridade canônica é a de `docs/PLANO-EXECUCAO-PDCA-R0-R9.md`. Esta seção fica como registro do raciocínio da época.


| Prioridade | Entrega | Prazo que a motiva | Estado |
|---|---|---|---|
| 1 | Tarifa social parametrizável (classificação, desconto, aviso, relatório) | 11/12/2026 | Regra de cálculo em construção no módulo `billing`; classificação por CadÚnico depende de integração e de base legal |
| 2 | Confirmar a NFAg-e e a base de cálculo da fatura com o tributarista | 01/12/2026 | Pendente de decisão jurídica |
| 3 | Integração SINISA (módulo `compliance/sigsan-integration` está vazio) | 03/11, 17/11, 31/12/2026 | Não iniciado |
| 4 | Bloqueio da OS de corte (Lei 14.015) + aviso prévio de 30 dias | Lei em vigor; NR 11 em 20/05/2027 | Depende do módulo de OS e do de cobrança |
| 5 | Cifra de campo ligada ao Vault + `audit_log` sem dado pessoal em claro | Antes do primeiro dado real | Primitivo pronto; integração e correção do `audit_log` pendentes |
| 6 | Atendimento: protocolo numerado, 24x7, histórico de 120 meses | 20/05/2027 | Módulo não existe |
| 7 | Cobrança híbrida Pix + boleto; Pix Automático | 01/02/2027, 01/07/2027 | Módulo `collection` em construção (boleto, acréscimos, classificação) |
| 8 | MTR: integração real com o SINIR e catálogo da lista IBAMA | Antes de qualquer uso do MTR | Bloqueado: o manual do Web Service do SINIR não foi encontrado |
| 9 | Catálogo de qualidade da água a partir do texto da norma, com regra de avaliação | Antes de qualquer laudo | Seed removido; modelo a redesenhar |

---

## 7. Perguntas em aberto (as que travam decisão)

1. A NFAg-e entra em 01/12/2026 para o prestador de água? Quais contas de água/esgoto exigem documento fiscal, por esfera?
2. Renda e NIS (tarifa social) são dado sensível na LGPD? O `10` lista 15 perguntas ao jurídico.
3. Qual é o prazo de prescrição aplicável à cobrança de água e esgoto? (O `13` cita o repetitivo REsp 1.117.903/RS como decenal, com o número do Tema **não confirmado**.)
4. O Web Service do SINIR existe para sistemas de terceiros? Como se credencia?
5. O Decreto do SAC (11.034/2022) vale para o prestador regulado por agência estadual/municipal? (O `13` conclui que não vale diretamente.)
6. Quais agências reguladoras os primeiros clientes têm? Isso define o perfil regulatório inicial. Agências **não levantadas**: ARPE, AGEMS, AGER-MT, ARSAE-AL, ARSEP.
7. Os valores da Portaria MCID 788/2024 sobre perdas não foram confirmados em fonte oficial.
8. Os relatórios `11` e `10` listam 22 e 15 perguntas adicionais.
