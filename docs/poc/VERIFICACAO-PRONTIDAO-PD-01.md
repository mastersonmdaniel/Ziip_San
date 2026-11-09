# ED-01 — Verificação de prontidão PD-01, item a item

**Sessão produtora (Produtor):** `002855fb-1e26-4fcb-86e7-4b568c15a6eb`
**Data:** 09/11/2026 · **Papel:** Produtor (revisão e confirmação são de
agentes distintos, depois) · **Sem acesso ao GitHub** (parada dura por
HTTP 429): toda a evidência abaixo é de **pacotes locais** em
`~/workspace/ziipsan-producao/`. O estado da **main NÃO foi verificado**
neste bloco e esta verificação **não** o afirma — reverificação por hash
no pouso pós-429 (ver `LANDING.md`).

## 1. O critério (PD-01, texto do aceite da ED-01)

Fonte única: `ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-onda-B.json`,
registro ED-01 (lido integralmente):

> "dependência textual (PD-01): onda A, grupo B1 da onda B e tarefas do
> P4 concluídos; o grupo B2 da onda B (ED-56, ED-69 a ED-79, ED-83 a
> ED-94, ED-96 a ED-99 e ED-105) não conta para a prontidão de POC e
> entra só no critério da LC.1; kit de campo mantido (Android, iPhone,
> impressora Zebra série ZQ, bobinas, 4G); massa do tenant de
> demonstração atualizada a cada versão; meta final: roteiro da VISAN
> com pelo menos 146 de 162 pontos e nenhuma falha em item crítico do
> SAMAE, em dois ensaios consecutivos feitos no tempo de preparo que os
> editais concedem (marco M12)"

Dependência declarada da ED-01: **P3.23** (ambiente de demonstração e
ensaio de POC do P3) — pacote local presente (`p3.23/`, RELATORIO +
REVISAO-CRUZADA com veredito "LIBERA COM CORREÇÕES APLICADAS"); o
**ensaio real** da P3.23 nunca foi executado (declarado no próprio
pacote: sem Docker/JDK/PostgreSQL nesta VM) — ver pendência P-ED01-02.

## 2. Recorte conferido e divergência D-ED01-01 (declarada, não escondida)

| Grupo | Fonte do recorte | Itens |
|---|---|---:|
| Onda A | ids de `tarefas-onda-A.json` | 34 |
| Grupo B1 | **tabelas do plano** (`PLANO-v3.0.md`, seção "Grupo B1") | 20 |
| P4 (no critério) | `tarefas-GL-P4-LC-PEND-C.json`, P4.* exceto P4.6 | 5 |
| **Total do critério** | | **59** |

**D-ED01-01 — divergência entre o aceite e as tabelas do plano.**
A string do aceite enumera o B2 por intervalos (ED-83 a ED-94), o que,
lido literalmente, coloca **ED-88 e ED-92 no B2** e reduz o B1 a 18.
As **tabelas do plano** listam o B1 com 20 entregas, **incluindo ED-88
e ED-92**, e a `CHECAGEM-MONTAGEM.md` registra a correção expressa:
"ED-88 e ED-92 movidas para o grupo B1 (SG-154 crítico na POC;
ED-64 depende da ED-92)". O §5 do plano também fecha a conta com
B1 = 20 e B2 = 27 (+ ED-01 no fim da onda B). **Decisão de execução
deste produtor (menor impacto, registrada):** conferir pelo recorte
das tabelas (B1 = 20), porque é a agrupação operacional do plano e a
única coerente com as dependências (ED-64 → ED-92); a divergência fica
nomeada para o coordenador/dono corrigirem a string do aceite ou as
tabelas. Com a leitura literal, o placar seria sobre 57 itens e
ED-88/ED-92 sairiam do critério — os dois pacotes existem localmente
com cadeia completa (ver tabela), logo a divergência **não muda o
resultado prático** desta verificação; muda a contagem formal.

**Fora do critério (não contam como pendência da ED-01):**
- **P4.6** (Schema compartilhado e banco dedicado): parada por decisão
  do dono **PD-05** ("sob demanda, fora das ondas, dos totais e do
  critério da LC.1") e excluída expressamente pelo aceite da **LC.1**
  ("exceto a seção de pacotes pendentes e sob demanda (P4.6, ED-68,
  ED-102, ED-103 e ED-104), que não entra no critério nem bloqueia o
  início"). Fontes: registro P4.6 e registro LC.1 em
  `tarefas-GL-P4-LC-PEND-C.json`.
- **Grupo B2** (27 tarefas pelas tabelas: ED-56, ED-69 a ED-79 exceto
  as movidas, ED-83 a ED-87, ED-89 a ED-91, ED-93, ED-94, ED-96 a
  ED-99, ED-105, ED-75 a ED-78, ED-84, ED-85, ED-86): entra **só** no
  critério da LC.1 (PD-01). Não conferido aqui como requisito da
  prontidão; a lista completa está no script.

## 3. Método (mecânico, reproduzível)

Script entregue neste pacote: `conferir-prontidao-pd01.py` (somente
leitura, sem rede). Para cada tarefa do recorte ele localiza a pasta
local (`ed-XX/` ou `p4.x/`), os `RELATORIO*.md` (produção, em qualquer
nível da pasta) e os `REVISAO*/CONFIRMACAO*.md`.

**Método de veredito (corrigido na correção do estágio 3, 09/11/2026 —
defeitos D-REV-01/D-REV-02 da `REVISAO-ED-01.md`):** a versão original
do script procurava a palavra-chave aprovatória
(`APROVAD[OA]|CONFIRMAD[OA]|LIBERA`) em **qualquer ponto** do arquivo
de revisão e sobredeclarou a ED-04 e a ED-08, onde a palavra aparece em
texto corrido ("contrapartida confirmada", "teto 100 confirmado")
enquanto o veredito formal das respetivas revisões é **PARCIAL**. O
critério corrigido é mais estrito e mecânico:

- **linha de veredito** — linha que contém a palavra "veredito";
- **bloco de veredito** — a linha de veredito mais as 4 linhas
  seguintes, ignorando linhas de tabela Markdown no corpo (cobre
  cabeçalho seguido do corpo e vereditos partidos em duas linhas, sem
  varrer o texto corrido);
- só conta como aprovatório o bloco de veredito que contiver
  `APROVAD[OA]|CONFIRMAD[OA]|LIBERA` (negações expressas, "NÃO
  APROVADO"/"NÃO CONFIRMADO", removidas antes da busca). Assim,
  "PARCIAL" numa linha de veredito classifica PARCIAL mesmo que
  "confirmado" apareça noutro ponto do arquivo.

Classes:

- **COMPROVADO_LOCAL** — pasta + relatório + veredito aprovatório em
  bloco de veredito (ou exceção explícita abaixo);
- **PARCIAL_LOCAL** — pasta + relatório, sem veredito aprovatório em
  bloco de veredito;
- **SEM_EVIDENCIA_LOCAL** — sem pasta ou sem relatório.

Duas declarações do próprio script completam o método, sem ajuste
silencioso de saída: (1) **fonte externa declarada** — o fechamento da
P4.8 está fora da pasta, em
`correcoes-m3-p4.8/REVISAO-CONFIRMACAO-P4.8.md` ("CONFIRMADO — P4.8
APROVADA"), e o script lê esse artefato nomeado com o mesmo critério
de blocos; (2) **tabela de exceções explícita** — a ED-13, cujo
veredito final formal é a fórmula "PRONTA para integração, com as
pendências declaradas" (sem palavra aprovatória padrão), é forçada a
COMPROVADO_LOCAL pela exceção documentada no docstring do script,
com a justificação e a citação do veredito real (classificação aceite
na `REVISAO-ED-01` §§3.1/O-2).

Limites honestos do método: é leitura **mecânica** de pacotes locais —
não é re-revisão de código, não prova pouso na main e pode subestimar
cadeias fechadas fora da pasta do pacote (a única fonte externa lida é
a declarada acima, a da P4.8). As exceções foram lidas pelo produtor e
estão anotadas na seção 6.

## 4. Placar (execução real do script corrigido, 09/11/2026)

Execução original do produtor (script antigo, árvore do instante da
produção): **59 conferidos → 56 comprovados, 2 parciais, 1 sem
evidência** — placar **sobredeclarado em 2 itens** (ED-04 e ED-08),
como demonstrou a revisão independente (`REVISAO-ED-01`, defeitos
D-REV-01/D-REV-02). Reexecução com o script corrigido nesta correção
(duas execuções com saída idêntica; CSV regenerado coerente — detalhe
em `CORRECAO-ED-01.md` na raiz do pacote):

```
PLACAR PD-01 (evidência LOCAL de pacotes; main não verificada)
  ONDA_A: conferidos=34 comprovados=32 parciais=2 sem_evidencia=0
  B1_TABELAS_PLANO: conferidos=20 comprovados=19 parciais=1 sem_evidencia=0
  P4_SEM_P4.6: conferidos=5 comprovados=4 parciais=1 sem_evidencia=0
  TOTAL: conferidos=59 comprovados=55 parciais=4 sem_evidencia=0
  Itens não comprovados localmente: ['ED-08', 'ED-12', 'ED-04', 'P4.2']
```

Leitura do placar corrigido: o efeito isolado da correção do script
(mesma árvore) é só ED-04 e ED-08, de COMPROVADO para PARCIAL — os
comprovados caem de 57 (script antigo sobre a árvore atual) para 55.
Face ao placar entregue (56/2/1), há uma terceira diferença que **não**
é efeito da correção: a **ED-51**, sem evidência no instante da
produção e da revisão, completou entretanto o seu pacote local
(`RELATORIO-ED-51.md` + `REVISAO-ED-51.md` com veredito formal
APROVADO e `CONFIRMACAO-ED-51.md`, produção paralela do mesmo bloco)
e conta agora como
COMPROVADO_LOCAL — o caso de timing previsto na pendência P-ED01-04 e
na observação O-1 da revisão. No instante da revisão, o placar
corrigido era o calculado pelo revisor: **59 → 54 comprovados,
4 parciais, 1 sem evidência**.

Saída integral da execução original arquivada no pacote
(`trabalho/saida-conferencia-pd01.txt` na pasta do produtor); as
saídas da reexecução corrigida estão transcritas em
`CORRECAO-ED-01.md`. Detalhe por item em `verificacao-pd01.csv`
(mesma pasta deste documento no repositório).

## 5. Tabela item a item

<!-- TABELA-GERADA: linhas geradas por conferir-prontidao-pd01.py a partir de verificacao-pd01.csv; não editar à mão -->
| Grupo | Tarefa | Título | Pasta local | Relatório(s) | Revisão/Confirmação | Classe mecânica |
|---|---|---|---|---|---|---|
| ONDA_A | ED-05 | Nível Setor Público: telefone, canal de contingência e plantão | `ed-05/` | RELATORIO-ED-05.md | REVISAO-ED-05-PENDENTE.md;REVISAO-ED-05.md | COMPROVADO_LOCAL |
| ONDA_A | ED-06 | Controle de acesso de edital | `ed-06/` | RELATORIO-ED-06.md | REVISAO-CONFIRMACAO-ED-06.md;REVISAO-ED-06.md | COMPROVADO_LOCAL |
| ONDA_A | ED-07 | Monitoramento e alertas para o cliente | `ed-07/` | RELATORIO-ED-07.md | REVISAO-CONFIRMACAO-ED-07.md;REVISAO-ED-07.md | COMPROVADO_LOCAL |
| ONDA_A | ED-08 | Compatibilidade técnica de edital | `ed-08/` | RELATORIO-ED-08.md | REVISAO-ED-08.md | PARCIAL_LOCAL |
| ONDA_A | ED-12 | Cadastro comercial ampliado e catálogos auxiliares | `ed-12/` | RELATORIO-ED-12.md | REVISAO-ED-12.md | PARCIAL_LOCAL |
| ONDA_A | ED-13 | Ligações de água e esgoto | `ed-13/` | RELATORIO-ED-13.md | REVISAO-ED-13.md | COMPROVADO_LOCAL |
| ONDA_A | ED-14 | Hidrometria | `ed-14/` | RELATORIO-ED-14.md | REVISAO-CONFIRMACAO-ED-14.md;REVISAO-ED-14.md | COMPROVADO_LOCAL |
| ONDA_A | ED-17 | Telemetria e supervisório por REST e arquivo | `ed-17/` | RELATORIO-ED-17.md | REVISAO-ED-17.md | COMPROVADO_LOCAL |
| ONDA_A | ED-18 | App de campo Android, base, autenticação e leitura offline | `ed-18/` | RELATORIO-ED-18.md | REVISAO-CRUZADA-ED-18.md;REVISAO-ED-18.md | COMPROVADO_LOCAL |
| ONDA_A | ED-15 | Ciclo de leitura: complementos e autoleitura | `ed-15/` | RELATORIO-ED-15.md | REVISAO-CONFIRMACAO-ED-15.md;REVISAO-ED-15.md;REVISAO-INDEPENDENTE-ED-15.md | COMPROVADO_LOCAL |
| ONDA_A | ED-16 | Recadastramento | `ed-16/` | RELATORIO-ED-16.md | REVISAO-CONFIRMACAO-ED-16.md;REVISAO-ED-16.md | COMPROVADO_LOCAL |
| ONDA_A | ED-19 | Emissão simultânea e impressão térmica | `ed-19/` | RELATORIO-ED-19.md | REVISAO-ED-19.md | COMPROVADO_LOCAL |
| ONDA_A | ED-26 | Arrecadação ampliada | `ed-26/` | RELATORIO-ED-26.md | REVISAO-ED-26.md | COMPROVADO_LOCAL |
| ONDA_A | ED-22 | Situações especiais de faturamento, usuário avulso e órgão centralizador | `ed-22/` | RELATORIO-ED-22.md | REVISAO-CONFIRMACAO-ED-22.md;REVISAO-ED-22.md | COMPROVADO_LOCAL |
| ONDA_A | ED-27 | Débito em conta: Pix Automático e FEBRABAN 150 legado | `ed-27/` | RELATORIO-ED-27.md | REVISAO-CONFIRMACAO-ED-27.md;REVISAO-ED-27.md | COMPROVADO_LOCAL |
| ONDA_A | ED-32 | Eventos contábeis e integração contábil | `ed-32/` | RELATORIO-ED-32.md | REREVISAO-ED-32.md;REVISAO-ED-32.md | COMPROVADO_LOCAL |
| ONDA_A | ED-25 | Resíduos sólidos na conta | `ed-25/` | RELATORIO-ED-25.md | REVISAO-CONFIRMACAO-ED-25.md;REVISAO-ED-25.md | COMPROVADO_LOCAL |
| ONDA_A | ED-34 | Catálogo de serviços e serviços faturáveis | `ed-34/` | RELATORIO-ED-34.md | REVISAO-CONFIRMACAO-ED-34.md;REVISAO-CRUZADA-ED-34.md;REVISAO-ED-34.md | COMPROVADO_LOCAL |
| ONDA_A | ED-35 | Atendimento completo | `ed-35/` | RELATORIO-ED-35.md | REVISAO-CONFIRMACAO-ED-35.md;REVISAO-ED-35.md | COMPROVADO_LOCAL |
| ONDA_A | ED-36 | OS completa no backoffice | `ed-36/` | RELATORIO-ED-36.md | REVISAO-CONFIRMACAO-ED-36.md;REVISAO-ED-36.md | COMPROVADO_LOCAL |
| ONDA_A | ED-24 | Controle de fraudes e fiscalização | `ed-24/` | RELATORIO-ED-24.md | REVISAO-CONFIRMACAO-ED-24.md;REVISAO-ED-24.md | COMPROVADO_LOCAL |
| ONDA_A | ED-29 | Cobrança: complementos, corte e religação por nível | `ed-29/` | RELATORIO-ED-29.md | REVISAO-CONFIRMACAO-ED-29.md;REVISAO-ED-29.md | COMPROVADO_LOCAL |
| ONDA_A | ED-31 | Dívida ativa (núcleo) | `ed-31/` | RELATORIO-ED-31.md | REVISAO-ED-31.md | COMPROVADO_LOCAL |
| ONDA_A | ED-30 | Negativação e protesto por arquivo | `ed-30/` | RELATORIO-ED-30.md | REVISAO-ED-30.md | COMPROVADO_LOCAL |
| ONDA_A | ED-33 | Remessa e-Sfinge assistida | `ed-33/` | RELATORIO-ED-33.md | REVISAO-ED-33.md | COMPROVADO_LOCAL |
| ONDA_A | ED-37 | Empreiteiras terceirizadas | `ed-37/` | RELATORIO-ED-37.md | REVISAO-CONFIRMACAO-ED-37.md;REVISAO-ED-37.md | COMPROVADO_LOCAL |
| ONDA_A | ED-38 | Portal e autoatendimento ampliados | `ed-38/` | RELATORIO-ED-38.md | REVISAO-CONFIRMACAO-ED-38.md;REVISAO-ED-38.md | COMPROVADO_LOCAL |
| ONDA_A | ED-41 | Notificações multicanal e fatura por e-mail | `ed-41/` | RELATORIO-ED-41.md | REVISAO-CONFIRMACAO-ED-41.md;REVISAO-ED-41.md | COMPROVADO_LOCAL |
| ONDA_A | ED-20 | OS em campo e acompanhamento de campo | `ed-20/` | RELATORIO-ED-20.md | REVISAO-CONFIRMACAO-ED-20.md;REVISAO-ED-20.md | COMPROVADO_LOCAL |
| ONDA_A | ED-23 | Revisão de consumo e desconto por vazamento | `ed-23/` | RELATORIO-CORRECAO-ED-23.md;RELATORIO-ED-23.md | REVISAO-CONFIRMACAO-ED-23.md;REVISAO-ED-23.md | COMPROVADO_LOCAL |
| ONDA_A | ED-46 | Catálogo completo de relatórios | `ed-46/` | RELATORIO-ED-46.md | REVISAO-ED-46.md | COMPROVADO_LOCAL |
| ONDA_A | ED-53 | GIS: edição web completa e cadastro de unidades (GIS-09) | `ed-53/` | RELATORIO-ED-53.md | REVISAO-CONFIRMACAO-ED-53.md;REVISAO-CONFIRMACAO2-ED-53.md;REVISAO-ED-53.md | COMPROVADO_LOCAL |
| ONDA_A | ED-58 | Almoxarifado: materiais e estoque para a OS, com alçada e baixa automática | `ed-58/` | RELATORIO-CORRECAO-ED-58.md;RELATORIO-ED-58.md | REVISAO-CONFIRMACAO-ED-58.md;REVISAO-ED-58.md | COMPROVADO_LOCAL |
| ONDA_A | ED-62 | Integração com ERP do cliente (Betha, IPM, Fiorilli) | `ed-62/` | RELATORIO-CORRECAO-ED-62.md;RELATORIO-ED-62.md | REVISAO-CONFIRMACAO-ED-62.md;REVISAO-ED-62.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-04 | Pacote de reversão | `ed-04/` | RELATORIO-CORRECAO-ED-04.md;RELATORIO-ED-04.md | REVISAO-ED-04.md | PARCIAL_LOCAL |
| B1_TABELAS_PLANO | ED-28 | Cartão no parcelamento pela credenciadora do cliente | `ed-28/` | RELATORIO-ED-28.md | REVISAO-CONFIRMACAO-ED-28.md;REVISAO-ED-28.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-39 | App do cliente Android e iOS nas lojas | `ed-39/` | RELATORIO-ED-39.md | REVISAO-ED-39-REAPRECIACAO.md;REVISAO-ED-39.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-40 | Totem em modo quiosque e senhas | `ed-40/` | RELATORIO-ED-40.md | REVISAO-ED-40.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-42 | Omnichannel híbrido | `ed-42/` | RELATORIO-ED-42.md | REVISAO-CONFIRMACAO-ED-42.md;REVISAO-ED-42.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-43 | Chatbot e IA por modelo de linguagem de terceiro | `ed-43/` | RELATORIO-CORRECAO-ED-43.md;RELATORIO-ED-43.md | REVISAO-CONFIRMACAO-ED-43.md;REVISAO-ED-43.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-47 | BI embarcado com Apache Superset | `ed-47/` | RELATORIO-ED-47.md | REVISAO-ED-47.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-48 | Indicadores gerenciais e SINISA: catálogo, painel e fechamento | `ed-48/` | RELATORIO-ED-48.md | REVISAO-CONFIRMACAO-ED-48.md;REVISAO-CRUZADA-ED-48.md;REVISAO-ED-48.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-49 | Sete painéis padrão | `ed-49/` | RELATORIO-CORRECAO-ED-49.md;RELATORIO-ED-49.md | CONFIRMACAO-ED-49.md;REVISAO-ED-49.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-50 | Qualidade da água operacional | `ed-50/` | RELATORIO-CORRECAO-ED-50.md;RELATORIO-ED-50.md | REVISAO-CONFIRMACAO-ED-50.md;REVISAO-ED-50.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-51 | Apoio à ISO/IEC 17025 | `ed-51/` | RELATORIO-ED-51.md | CONFIRMACAO-ED-51.md;REVISAO-ED-51.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-54 | Traçados, isolamento e manobras com aviso e OS em lote (GIS-11) | `ed-54/` | RELATORIO-ED-54.md | REVISAO-CONFIRMACAO-ED-54.md;REVISAO-ED-54.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-55 | Acesso QGIS e importação ampliada (GIS-10, GIS-18) | `ed-55/` | RELATORIO-ED-55.md | REVISAO-CONFIRMACAO-2-ED-55.md;REVISAO-CONFIRMACAO-ED-55.md;REVISAO-CRUZADA-ED-55.md;REVISAO-ED-55.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-92 | Ocorrência de rede como entidade | `ed-92/` | RELATORIO-CORRECAO-ED-92.md;RELATORIO-ED-92.md | REVISAO-CONFIRMACAO-ED-92.md;REVISAO-ED-92.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-88 | Soluções individuais, limpa-fossa e ligação obrigatória à rede | `ed-88/` | RELATORIO-ED-88.md | REVISAO-ED-88.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-64 | Drenagem no GIS (GIS-13) | `ed-64/` | RELATORIO-ED-64.md | REVISAO-ED-64.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-63 | Hidráulica: EPANET e SWMM, exportação e importação (GIS-12) | `ed-63/` | RELATORIO-ED-63.md | REVISAO-ED-63.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-65 | Resíduos sólidos no GIS (GIS-14) | `ed-65/` | RELATORIO-ED-65.md | REVISAO-ED-65.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-66 | Campo GIS no app Capacitor (GIS-15) | `ed-66/` | RELATORIO-CORRECAO-ED-66.md;RELATORIO-ED-66.md | REVISAO-CONFIRMACAO-ED-66.md;REVISAO-ED-66.md | COMPROVADO_LOCAL |
| B1_TABELAS_PLANO | ED-67 | Exportação, devolução da base e OGC autenticado por tenant (GIS-16, GIS-17) | `ed-67/` | RELATORIO-ED-67.md | REVISAO-CONFIRMACAO-ED-67.md;REVISAO-ED-67.md;REVISAO-INDEPENDENTE-ED-67.md | COMPROVADO_LOCAL |
| P4_SEM_P4.6 | P4.1 | R8: cobrança; negativação e protesto na ED-30 | `p4.1/` | RELATORIO-P4.1.md | REVISAO-CONFIRMACAO-P4.1.md;REVISAO-P4.1.md | COMPROVADO_LOCAL |
| P4_SEM_P4.6 | P4.2 | R6: SINISA assistido | `p4.2/` | RELATORIO-P4.2.md | REVISAO-P4.2.md | PARCIAL_LOCAL |
| P4_SEM_P4.6 | P4.5 | R5c: Pix Automático | `p4.5/` | RELATORIO-P4.5.md | REVISAO-P4.5.md | COMPROVADO_LOCAL |
| P4_SEM_P4.6 | P4.7 | Restauração mensal automatizada | `p4.7/` | RELATORIO-P4.7.md | REVISAO-P4.7.md | COMPROVADO_LOCAL |
| P4_SEM_P4.6 | P4.8 | Suporte: resposta por e-mail, deflexão, assinatura de status, notas do tenant | `p4.8/` | RELATORIO-P4.8.md | REVISAO-P4.8.md;REVISAO-REGISTRO.md;correcoes-m3-p4.8/REVISAO-CONFIRMACAO-P4.8.md (fonte externa declarada) | COMPROVADO_LOCAL |

## 6. Leitura conferida das exceções (produtor, sobre os arquivos locais)

Na produção, esta leitura **não alterou** o placar mecânico então
vigente; ela registra o que a conferência manual encontrou nos itens
sinalizados. Na correção do estágio 3, o placar mecânico foi refeito
pelo script corrigido (seção 4) e esta seção foi atualizada onde a
correção ou a evolução da árvore o exigem (§§6.2 e 6.3).

### 6.1 Itens com primeiro veredito de REPROVAÇÃO e cadeia fechada localmente

| Tarefa | Cadeia local encontrada |
|---|---|
| ED-13 | `REVISAO-ED-13.md`: veredito inicial **REPROVADA** (bloqueante B1 — bifurcações inventadas); o mesmo arquivo traz a "Conferência das correções" (07/11/2026) com veredito final **"PRONTA para integração, com as pendências declaradas"** (14/15 defeitos corrigidos; L3 parcial declarado). **Ressalva viva:** o aceite integral da ED-13 continua formalmente condicionado ao **item 2.8 do SAMAE**, não verificável sem o roteiro ([SEM FONTE] local — ver pacote de ensaio). |
| ED-17 | `REVISAO-ED-17.md`: "Veredito inicial: REPROVADO"; no mesmo arquivo, "veredito final após as correções: **APROVADO COM AS CORREÇÕES APLICADAS**". |
| ED-31 | `REVISAO-ED-31.md`: "VEREDITO FORMAL: REPROVADO" (linha 118) seguido de "Reapreciação pós-correção" (08/11/2026) com "**VEREDITO FINAL DA REAPRECIAÇÃO: APROVADO**" (linha 230). |
| ED-40 | `REVISAO-ED-40.md`: "VEREDITO FORMAL: REPROVADO" seguido de "Confirmação pós-correção" (08/11/2026) com "**VEREDITO FINAL: APROVADO**" e suíte reexecutada 24/24. |
| P4.8 | `REVISAO-P4.8.md`: "Veredito formal: REPROVADO" (suíte vermelha, D1 grave). O fechamento está **fora da pasta do pacote**, em `correcoes-m3-p4.8/REVISAO-CONFIRMACAO-P4.8.md`: "**CONFIRMADO — P4.8 APROVADA**". O script não varre `correcoes-*/` — limitação declarada do método. |
| ED-25 | Sem flag mecânica, mas registrado: o pacote foi REPROVADO na revisão cruzada de 08/11/2026 e corrigido na seção "CORREÇÕES PÓS-REPROVAÇÃO" do próprio `RELATORIO-ED-25.md`, com `REVISAO-CONFIRMACAO-ED-25.md` na pasta. |

### 6.2 Itens PARCIAIS no mecânico — o que falta de verdade

| Tarefa | Estado local conferido | Falta |
|---|---|---|
| ED-04 | Pasta `ed-04/` com relatórios e `REVISAO-ED-04.md`, cujo veredito geral é "**PARCIAL COM PROBLEMAS ALTOS — não integrar/pushar como está**". O relatório de correção posterior fecha itens **sem linha de veredito formal APROVADO** e mantém 5 itens do aceite dependentes de ensaios reais não executados. **Correção do estágio 3 (D-REV-01):** o script antigo classificou COMPROVADO por colisão de palavra-chave — "confirmada/confirmado" em texto corrido, fora de qualquer linha de veredito; o script corrigido lê só blocos de veredito e classifica **PARCIAL_LOCAL**, como manda o veredito real da revisão. | Fechamento dos itens dependentes de ensaios reais e veredito formal aprovatório sobre o pacote corrigido. Até lá, a ED-04 **não** conta como comprovada para a prontidão. |
| ED-08 | Pasta `ed-08/` com relatório e `REVISAO-ED-08.md`, cujo veredito geral é "**PARCIAL, aprovável com pendências**", com o item 1 do aceite (matriz E2E verde no CI) **PENDENTE** — "o verde não está comprovado". **Correção do estágio 3 (D-REV-02):** o script antigo classificou COMPROVADO por colisão de palavra-chave — "confirmado" num parágrafo de conferência ("teto 100 confirmado no contrato…"), fora de linha de veredito; o script corrigido classifica **PARCIAL_LOCAL**, como manda o veredito real da revisão. | Matriz E2E verde comprovada no CI e veredito formal aprovatório. Até lá, a ED-08 **não** conta como comprovada para a prontidão. |
| ED-12 | Pasta `ed-12/` com `RELATORIO-ED-12.md` e `REVISAO-ED-12.md`. A revisão declara no próprio texto que **não foi feita por agente revisor separado** ("o coordenador não pôde acionar um agente revisor separado… a revisão foi uma segunda passagem completa do próprio coordenador") e **não traz veredito formal**. Achados A1–A3 marcados CORRIGIDO na própria passagem. | Revisão **independente** com veredito formal (regra da esteira: produção, revisão e confirmação nunca na mesma sessão). Até lá, a ED-12 **não** deve ser contada como comprovada para a prontidão. |
| P4.2 | Pasta `p4.2/` com relatório em `entrega/RELATORIO-P4.2.md` e `REVISAO-P4.2.md` (revisor independente, pós-implementação): 3 defeitos encontrados **e corrigidos na revisão** + lista de "conferências que passaram". O arquivo **não traz linha de veredito formal** nem existe CONFIRMACAO local. | Veredito formal de revisão/confirmação registrado em artefato. Conteúdo sugere aprovação com correções aplicadas, mas **sem o artefato de veredito a ED-01 não o afirma**. |

### 6.3 Item SEM EVIDÊNCIA local no instante da produção — entretanto resolvido na evidência local

| Tarefa | Estado local conferido |
|---|---|
| ED-51 (Apoio à ISO/IEC 17025, B1) | **No instante da conferência do produtor, nenhuma pasta `ed-51/` existia** em `ziipsan-producao/` (busca por nome em toda a árvore: só citações) — ausência de **timing do bloco**: o `conclusao/MAPA-COORDENACAO.md` registra a ED-51 como pacote despachado para produção em sessão paralela neste mesmo bloco. **Atualização da correção do estágio 3 (09/11/2026):** a produção paralela completou entretanto o pacote local — `ed-51/` com `RELATORIO-ED-51.md`, `REVISAO-ED-51.md` de veredito formal "**APROVADO**" e `CONFIRMACAO-ED-51.md` — e a reexecução do script corrigido classifica a ED-51 como **COMPROVADO_LOCAL** (é a terceira diferença face ao placar entregue; não é efeito da correção do script, que nela não toca: o script antigo, sobre a árvore atual, classifica-a igual). A pendência P-ED01-04 fica assim resolvida na evidência local; mantém-se a reverificação no pouso, porque a árvore deste bloco ainda evolui. A conferência desta correção é a mecânica (existência + linha de veredito formal); o mérito da cadeia da ED-51 é do portão do seu próprio pacote. |

## 7. Conclusão da verificação PD-01 (honesta)

- **Evidência local de pacotes (placar corrigido, estágio 3):**
  59 conferidos → **55 comprovados, 4 parciais (ED-04, ED-08, ED-12,
  P4.2), 0 sem evidência**. No instante da revisão independente o
  placar corrigido era **54 / 4 / 1**; a diferença é a ED-51, que
  completou a evidência local entretanto (§6.3). O placar entregue
  originalmente (56 / 2 / 1) estava **sobredeclarado** em ED-04 e
  ED-08 (D-REV-01/D-REV-02) e já não vigora.
- Com a leitura conferida da seção 6, os comprovados incluem cadeias
  que passaram por reprovação e correção — o histórico está
  registrado, não apagado.
- A prontidão PD-01 **não pode ser declarada fechada** enquanto
  ED-04 e ED-08 (vereditos PARCIAIS das respetivas revisões), ED-12
  (revisão independente) e P4.2 (veredito formal) estiverem abertos —
  são pendências **internas, nomeadas e pequenas**, distintas das
  pendências externas do ensaio (kit físico e ensaios cronometrados).
- Nada nesta verificação afirma estado da main, pouso ou CI: GitHub
  proibido neste bloco (429). A reverificação no pouso está no
  `LANDING.md`.
