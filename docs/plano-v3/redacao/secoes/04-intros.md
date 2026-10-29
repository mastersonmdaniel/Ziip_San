## 4. Fases

**Como ler as tabelas.** Cada fase lista as tarefas na ordem de execução. "Origem" traz a fonte da tarefa (v1, revisão
17, ADR, achados de que ela é dona); "Dep." traz as dependências declaradas, e dependência fora da coluna só vale quando
a própria tarefa a escreve por extenso; "Aceite" é o critério verificável, com os ajustes das versões 2.13 a 2.21 já
incorporados. Quando uma tarefa usa o que outra constrói, o aceite diz "usa X da tarefa Y"; quando um ponto pertence a
outra tarefa, remete a ela ("ver P1.36"). Nenhuma tarefa depende de outra de fase ou onda posterior, nem da seção de
pacotes pendentes.

**Datas.** A data de cada fase é data de referência (ADR-0044): indica sequência e ordem de grandeza, não compromisso. As
datas de referência são as da v2.21, mantidas sem recálculo; o P0 ainda não começou. O que libera cada fase é o seu
gate, abaixo de cada título.

**Tamanho.** **P** até 2 dias, **M** até 1 semana, **G** mais de 1 semana, para uma pessoa com agentes; entre
parênteses, semanas-pessoa quando estimadas. É indicação de esforço, sem conversão em equipe nem em calendário
(ADR-0035); tarefa nova ou sem estimativa fica "a estimar" (decisão do dono 5). Toda tarefa termina com testes verdes,
registro no diário e push.

**Nomes depois da renomeação.** A partir da P0.33, os caminhos, módulos, pacotes, propriedades, roles e realm citados
neste plano com `sigsan` (`commercial/sigsan-fiscal`, `sigsan_worker`, `sigsan.fiscal.*`, realm `sigsan`) leem-se com
`ziipsan`. A P0.33 só troca `sigsan` por `ziipsan`; os nomes definitivos de módulo (`platform/*`,
`packages/sanitation/*`) são aplicados na P0.47, pela tabela única de módulos de PLATAFORMA-E-VERTICAIS, e prevalecem
sobre os da P0.33. Tarefas novas já usam o nome final (`ziipsan_central`, `urn:ziipsan:problem:`, códigos de problema
em inglês da tabela da P0.33). Commits e diário anteriores valem até a tag `pre-rename`.

### P0 — Plataforma operável · data de referência: 13/10–27/11/2026

**Gate:** limpeza do código (P0.25) e P0.7 antes da renomeação (P0.33); demais tarefas de código depois da P0.33.

Objetivo: ambiente fora do Drive; código limpo do que não será usado e renomeado para `ziipsan`; artefato implantável
com versão gerada; marca e temas; altos e médios de segurança; catálogo de permissões `recurso:ação`; parâmetros do
tenant; contrato de pacote de vertical com teste de fronteira; ambiente local completo (homologação e demonstração
locais, cofre local e emulador de S3); interfaces de infraestrutura com teste de contrato; PITR local e R1 sobre o
cofre local (ADR-0033); kit de habilitação iniciado. A infraestrutura na AWS `sa-east-1` (ADR-0011) fica para o
lançamento comercial.

A ordem do início é P0.1 → P0.25 → P0.7 → P0.33. A limpeza (P0.25, 23c §4) roda logo depois da saída do Drive, para não
renomear o que será apagado: sai o `KeycloakAdminService` com a dependência do cliente de administração, o
`ModuleRegistry`, o `AuditListener` vazio, o SINIR e o SCADA por MQTT contrários aos ADRs, as cópias das regras de
consumo no billing, os diretórios de módulo vazios e as tabelas legadas da `V1_1`, que é reescrita. A P0.33 faz em
seguida a renomeação `sigsan` → `ziipsan` como tarefa única, e as demais tarefas de código do P0 dependem dela; a P0.47
fixa os nomes definitivos de módulo. Com o ADR-0033, a P0.26 fica reduzida à tabela de custo e às conferências
documentais, e a P0.27, a P0.28, a P0.29 e a P0.45 entregam o ambiente local. A regra pura do R3 pode começar no P0, em
paralelo.

**Códigos de problema na P0.33** (decisão do dono do produto, 12/10/2026). Lista dos códigos emitidos hoje, conferida em
`ApiException.java`, `GlobalExceptionHandler.java`, `ProblemResponses.java`, `JwtTenantFilter.java`, `SecurityConfig.java`
e nos serviços do cadastro, mais o código previsto na P0.43 e o `requisicao-invalida` apontado pelo 23c (COD-11). Os
códigos de campo e de aviso (`errors[].code`, `warnings[].code`, como `REQUIRED` e `DOCUMENT_IN_USE`) já estão em inglês
e não mudam. Código novo nasce em inglês, em minúsculas com hífen, e entra no catálogo de erros explicados (P1.22).

| Atual | Novo | Status |
|---|---|---|
| `nao-autenticado` | `unauthenticated` | 401 |
| `acesso-negado` | `access-denied` | 403 |
| `mfa_obrigatorio` (previsto) | `mfa-required` | 403 |
| `tenant-ausente`, `tenant-inativo`, `tenant-divergente` | `tenant-missing`, `tenant-inactive`, `tenant-mismatch` | 403 |
| `nao-encontrado` | `not-found` | 404 |
| `exclusao-indisponivel` | `deletion-unavailable` | 405 |
| `versao-desatualizada` | `stale-version` | 409 |
| `conflito-de-dados` | `data-conflict` | 409 |
| `registro-excluido`, `registro-ja-excluido` | `record-deleted`, `record-already-deleted` | 409 |
| `cliente-com-vinculo-vigente`, `cliente-sem-documento` | `customer-has-active-link`, `customer-without-document` | 409 |
| `requisicao-invalida` | `invalid-request` | 400 |
| `busca-na-url` | `search-in-url` | 400 |
| `filtro-indisponivel`, `filtro-invalido` | `filter-unavailable`, `invalid-filter` | 400 |
| `ordenacao-invalida`, `pagina-invalida`, `inscricao-invalida` | `invalid-sort`, `invalid-page`, `invalid-inscription` | 400 |
| `validacao` | `validation` | 422 |
| `confirmacao-necessaria` | `confirmation-required` | 422 |
| `erro-interno` | `internal-error` | 500 |

### P1 — Migração, cadastro e núcleo de OS · data de referência: 30/11/2026–29/01/2027

**Gate:** núcleo de OS (P1.31), protocolo de atendimento (P1.36) e cadastro mínimo de ativos (P1.35) antes das tarefas
que os consomem.

Objetivo: cadastro do piloto importado e conciliado; R2 (cadastro completo, com as situações do titular na P1.41 e a
carga pelo importador genérico de planilha da P1.40) e R3; núcleo de OS com a unidade consumidora como raiz (ADR-0039,
ADR-0045), com o protocolo de atendimento como origem de toda solicitação externa e o cadastro mínimo de ativos com a
hierarquia de seis níveis; perfis de acesso de fábrica por função, cada um com menu e página inicial; catálogo único de
eventos de negócio; CNPJ alfanumérico em todas as fronteiras; console e Central Ziip v0, outbox, marca do tenant, ajuda
e chamados de suporte v0; núcleo do módulo Certificações de qualidade e área "Normas e legislação"; roteiros internos de
POC e primeiro ensaio registrado; modelo de dados e catálogo do GIS (ADR-0034), com as normas ABNT compradas no início
da fase (P1.28). O núcleo do GIS (P1.26, P1.27, P2.21 a P2.23, P3.25 a P3.27) ocupa o P1 a P3, cada parte com aceite no
fim da sua fase.

### P2 — Ciclo de faturamento · data de referência: 01/02–26/03/2027

**Gate:** perfis regulatórios das agências de SC (P2.31) e conteúdo mínimo da fatura (P2.32) concluídos antes da GL.2.

Objetivo: ciclo completo em homologação local com NFAg-e, dentro das metas de carga, com retificação e manutenção de
conta por OS; perfis regulatórios das agências de SC carregados, simulados e testados; conteúdo mínimo da fatura por lei
e por agência, com o motor de mensagens legais; motor único de modelos de documento e mensagem; cashback de CBS e IBS na
fatura; fila dos documentos fiscais do tenant (rejeições, contingência, inutilização e reenvio); gestão tarifária com
simulação; "minhas tarefas" como caixa única de pendências e aprovações; API pública e webhooks de saída; domínio
próprio do tenant; cobrança do SaaS por contrato e área do tenant antes do primeiro contrato; topologia, pacotes de
edição e vínculo comercial do GIS (ADR-0034).

### P3 — Pronto para operar · data de referência: 29/03–28/05/2027

**Gate:** todas as tarefas da fase.

Objetivo: arrecadação com efeito da classificação, devolução e pagamentos não identificados; portal do consumidor com
histórico e documentos, login gov.br para tenant público e pagamento sem conta (ADR-0043); corte e religação com regras e
bloqueios; privacidade do titular para todo tenant (direitos, registro de operações de tratamento e canal no portal);
registro único de indicadores e metas; página inicial por perfil e agenda; unidades organizacionais e escopo por área;
revisão de segurança, minutas de contrato e operação no ambiente local; relatórios operacionais para o paralelo;
ambiente de demonstração e ensaio de POC com meta no aceite (pelo menos 50 de 162 pontos na VISAN; SAMAE 2.4 e 2.5 sem
falha); débito automático legado, se o piloto tiver optantes e o parecer da RAS-06 mantiver o objeto (P3.24, §2.4); mapa
web de consulta, importador do cadastro técnico e temáticos, que fecham o núcleo do GIS. Dentro do núcleo, a P3.26, que
carrega a rede real do piloto, vem antes da P3.27 e da parte não essencial da P3.25 (cliente WMS, medição). A P3.12
(fim do `outOfOrder`) fecha depois das tarefas que usam versão de faixa (§3).

### Homologação local do piloto · data de referência: 01/06–30/07/2027

**Gate:** GL.4, aceite formal do dono do produto e do piloto.

Tudo no ambiente local, com dados sintéticos ou anonimizados na origem e nenhum dado pessoal real na base (ADR-0033). O
paralelo (GL.2) compara conta a conta com o sistema do piloto, com o perfil regulatório da agência do piloto ativo
(P2.31) e o conteúdo mínimo da fatura (P2.32) conferidos. O sistema do piloto continua oficial até o corte, no
lançamento comercial (LC.13). Os códigos GL foram mantidos por causa das remissões em outros documentos.

### P4 — Tarefas originais · data de referência: 02/08–17/12/2027

**Gate:** corre junto da onda A da trilha de editais; as tarefas contam na prontidão de POC.

Ficam no P4 as tarefas originais redefinidas pelos ADRs 0026 a 0028: R8 (cobrança, P4.1), R6 (SINISA assistido, P4.2),
R5c (Pix Automático, P4.5), restauração mensal automatizada (P4.7) e suporte por e-mail com deflexão (P4.8). Saíram a
P4.3, absorvida pela ED-46, pela ED-47 e pela ED-49, que ficam com a regra "nenhum relatório nem consulta de BI no
primário", e a P4.4, duplicata da ED-18; a ED-16, a ED-19 e a ED-20 passam a depender da ED-18. A P4.6 (schema
compartilhado e banco dedicado) foi para a seção de pacotes pendentes e sob demanda.

O R6 fica depois do alvo de 30/04/2027 da v1 porque, sem cliente em produção antes do lançamento comercial (ADR-0033),
o primeiro envio assistido com dado do Ziip SAN é o do primeiro ano-base completo depois do corte (LC.13); até lá o
piloto envia pelo processo atual.

### Trilha de editais (ADR-0023) · data de referência: a partir de 02/08/2027

Executa as entregas ED-01 a ED-105 do [consolidado 18](levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md) §5 e dos
levantamentos posteriores, na ordem das ondas. Dentro de cada onda, primeiro o que é crítico no SAMAE e pontua na VISAN.
Já estão em fases anteriores ou no lançamento: ED-02 (P0.44), ED-03 (P0.26 e LC.2 a LC.5), ED-21 (P2.20), ED-45 (P3.22),
as partes de ED-01 (P1.25, P3.23) e de ED-05 (P2.15, P3.15, P3.16), a parte FEBRABAN 150 da ED-27 (P3.24, se houver
optantes), a ED-52 (P1.26, P1.27, P2.21, P2.22, P3.25, P3.27 e parte da P2.23), a parte de migração da ED-55 (P3.26), a
base da ligação da ED-13 (P2.23), o cadastro de ativo da ED-56 (P1.35), o núcleo de LGPD da ED-74 (P3.40), o registro de
indicadores da ED-48 (P3.41) e o motor de mensagens legais da ED-79 (P2.32). A parte de instância dedicada da ED-04 passou
à LC.15. As entregas de GIS citam entre parênteses o ID do projeto
([GIS-CADASTRO-TECNICO §13](arquitetura/GIS-CADASTRO-TECNICO.md)).

**Esforço.** A coluna "Tam." traz a escala do plano e, entre parênteses, a estimativa em semanas-pessoa do consolidado 18
§5. Somadas só as entregas estimadas: onda A ≈ 120,9 (34 de 34 entregas); onda B ≈ 92,5 (grupo B1 ≈ 85,5, com 17 de 18
entregas estimadas, mais a ED-56 ≈ 4 no grupo B2 e a ED-01 ≈ 3); onda C ≈ 12,4 (6 de 12); total ≈ 225,8, igual ao da
v2.21, porque a ED-56 só mudou de onda e a estimativa da ED-04 inclui a parte que foi para a LC.15. Faltam estimar 28
entregas do grupo B2 e 6 da onda C, e 49 entregas estimadas mudaram de escopo desde a v2.14 sem nova estimativa: os
totais são indicação parcial, com esforço a recalcular (COE-12), e não se reestima agora (decisão do dono 5).

**Ensaios de POC.** Ao fim de cada onda, com meta no aceite: **onda A**, pelo menos 120 de 162 pontos na VISAN e nenhuma
falha no SAMAE nas seções 2.4 a 2.19, 2.28 e Item 3; **fim do grupo B1**, a meta final da ED-01 (VISAN ≥ 146 de 162 e
nenhuma falha crítica no SAMAE, em dois ensaios seguidos).

#### Onda A

**Gate:** homologação local aceita (GL.4); começa junto do P4.

Itens que pontuam nas alíneas b a p e r da VISAN ou são críticos no SAMAE nas áreas comercial, de campo e de suporte:
controle de acesso de edital, atendimento completo com ouvidoria e interrupção programada, OS completa no backoffice
com almoxarifado, alçada e baixa automática (ED-36, ED-58, ADR-0032), hidrometria, app de campo com autenticação,
leitura, emissão simultânea e OS offline (ED-18 a ED-20), arrecadação e débito em conta, dívida ativa, negativação e
protesto por arquivo, contabilidade e e-Sfinge para a habilitação, portal ampliado com login gov.br no aplicativo,
notificações por SMS, WhatsApp e push, telemetria por REST e arquivo, edição web do GIS (ED-53), integração com o ERP do
cliente e o catálogo restante de relatórios (ED-46). A ED-17 consome o contrato de ingestão de supervisório do ADR-0046,
que precisa estar aceito antes do início da onda (§2.4).

#### Onda B

**Gate:** dependências declaradas; entregas da onda B podem depender da onda A e do P4, nunca o contrário. O ensaio da
meta final (fim do grupo B1) pressupõe a onda A concluída, com o ensaio de 120 pontos registrado.

**Grupo B1: canais digitais, BI, qualidade e GIS.** Entregas que atendem às alíneas q, s e t da VISAN e às seções 2.22,
2.24, 2.26, 2.27 e "Item 0" do SAMAE: pacote de reversão (ED-04, sem a instância dedicada), cartão no parcelamento, app
do cliente nas lojas, totem, omnichannel com Messenger, Instagram e Telegram, chatbot por modelo de terceiro (ADR-0042),
BI embarcado com Superset, indicadores SINISA sobre o registro da P3.41, painéis padrão, qualidade da água operacional e
apoio à ISO/IEC 17025, e o GIS da trilha (traçados, isolamento e manobras, QGIS, hidráulica, drenagem, resíduos, campo,
exportação e OGC autenticado por tenant, com WMS pelo GeoServer).

**Grupo B2: certificações, perdas, operação e regulatório.** As 27 entregas de título "onda B" das versões 2.17 e 2.18
que estavam na tabela da onda C (COE-02), mais a ED-56, que subiu da C com o que restou depois da P1.35 (inventário MCPSB,
manutenção e risco), e a ED-105 (efluente da ETE e lodo de ETA e ETE, obrigação legal de todo tenant com estação):
pacotes ISO 9001, 14001, 45001, 55001, 27001 e 27701, evidência e auditor externo, curadoria das normas, fluxo do
empreendedor e obras, operação de estações sem supervisório, esgoto não doméstico, soluções individuais, insumos
químicos, sistemas e Plano de Segurança da Água, crise hídrica, ocorrência de rede, fichas de indicador, módulo de
perdas (ED-85, antes da ED-78), gestão de pressão, energia e ESG, sala de situação, anomalia em séries e o pacote
regulatório (ED-96 a ED-99). Quase todas estão "a estimar". O grupo B2 não entra na prontidão de POC; entra no critério
do lançamento (PD-01).

**Fim da onda: prontidão de POC (ED-01).** Kit de campo e ensaio final, depois da onda A, do grupo B1 e do P4 (PD-01).

#### Onda C

**Gate:** dependências declaradas; entregas da onda C podem depender das ondas A e B, nunca o contrário.

Aderência total fora das POCs e serviços de implantação: implantação padronizada, política comercial e preço por porte,
plano de implantação e treinamento, telefonia mínima, beneficiários de programas sociais, frota e equipes de campo
(ED-59, sobre a manutenção da ED-56), pacote de esgoto e água bruta (ED-80, sem o que passou à ED-105), uso próprio de
água (ISO 46001), perfis de exportação, perdas aparentes, projeção de receita e metas de consumo.

**Fora da trilha (2029, sob demanda; ADR-0028, item i):** compras (requisição de compra, cotação e pedido, antes na
ED-58, ≈ 3 semanas), ED-60 (RH: lotação, competências e indicadores, ≈ 3) e ED-61 (orçamento: dotações, previsto ×
realizado, ≈ 3). Até lá, os indicadores de compras, RH e orçamento do painel estratégico vêm do ERP do cliente pela
ED-62. Entram só com cliente que os contrate, por nova estimativa e aceite de escopo. Também ficam sob demanda, sem ID,
a integração por API com birô de crédito e com a CENPROT (a negativação e o protesto por arquivo estão na ED-30). Saíram
da antiga lista "2028, sob demanda" o que já está na trilha: incidente e ocorrência de rede (ED-92), perdas com entrada
real (ED-78, ED-85), ISO/IEC 27001 (ED-73) e a trilha GRS, que virou a ED-104.

### Pacotes pendentes e sob demanda · sem data de referência

**Gate:** cada pacote só entra por cartão do dono do produto ou por demanda registrada.

Fora das ondas, dos totais de esforço e do critério da LC.1 (COE-03, PD-05, PD-09); nenhuma tarefa fora desta seção
depende deles. P4.6 (schema compartilhado e banco dedicado, só com demanda); ED-68 (cobrança por empresa terceirizada,
com remuneração por resultado, parada desde 21/10/2026); ED-102 (obras e convênios de investimento, com recorte por
decisão do dono); ED-103 (antecipação de recebíveis e conta vinculada, não recomendada agora pelo 22d); ED-104 (trilha
GRS: MTR, CDF e DMR no sistema do IMA em SC e no SINIR, lista IBAMA, NBR 10004 série 2024, lodo do próprio tenant como
gerador e operação de resíduos sólidos). Até a liberação da ED-104, a obrigação de resíduos do tenant fica como item
manual da agenda da ED-98.

### Lançamento comercial (ADR-0033) · data de referência: a partir de outubro de 2028

**Gate:** a LC.1 começa depois de todas as fases e das ondas A, B e C, exceto a seção de pacotes pendentes e sob
demanda; a LC.15 fica fora do caminho até a LC.13.

Objetivo: infraestrutura de produção na AWS `sa-east-1` (ADR-0011), primeira produção com o piloto como cliente inicial
e primeiro atestado. Começa quando não houver desenvolvimento de fase pendente (opção A, escolhida pelo dono do produto
na v2.8). Datas de referência: início em outubro de 2028 (M14), primeira produção por volta de fevereiro de 2029 (M15)
e primeiro atestado entre maio e julho de 2029 (M16); do início da fase ao atestado, de 7 a 9 meses como ordem de
grandeza. Esforço estimado: ≈ 15 semanas-pessoa (±30%), quase todo transferido do P0 (P0.27 a P0.29 na versão AWS), do
P3 (P3.8, P3.9 e P3.18 na parte de produção) e da antiga entrada em produção do piloto (carga, paralelo, restauração e
corte com dado real), mais a LC.15, a estimar. Nenhum dado real entra antes da LC.8 e da LC.9. A instância dedicada
(LC.15) é criada pela mesma IaC, imagem e pipeline, em conta separada, quando um contrato a exigir.

**Melhorias contínuas.** Os baixos 17:B1–B11 já estão distribuídos (P0.5, P0.7, P0.13, P0.17, P1.12, P1.15, P2.3);
baixos de revisões futuras entram nesta lista.

---
