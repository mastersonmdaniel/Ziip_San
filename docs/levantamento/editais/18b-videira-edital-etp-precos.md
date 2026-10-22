# 18b — VISAN Videira/SC, Pregão Eletrônico 06/2026: edital, ETP e pesquisa de preços

Levantamento de requisitos e condições do Pregão Eletrônico nº 06/2026 do Serviço Autônomo de Água e Esgoto do
Município de Videira (VISAN, CNPJ 30.753.960/0001-93), Processo Administrativo nº 09/2026, Requisição nº 060/2026.
Elaborado em 12/10/2026 a partir dos documentos publicados no PNCP/AtendeNet.

## Fontes e cobertura

| Documento | Arquivo (pasta `editais/`) | Páginas | Leitura |
|---|---|---|---|
| Edital e anexos I a VI | `..._Edital_edital_9_2026_23158_documento.pdf` | 148 | Páginas 1–34 (edital e minuta de contrato) e 130–148 (prova de conceito e modelos) lidas integralmente. Anexo II (Termo de Referência embutido, p. 35–129) lido integralmente nas partes contratuais e não funcionais (itens 1–4.19, 13.1.15, 25–32); módulos funcionais (itens 5–24) apenas inspecionados por títulos e busca dirigida, porque o TR é objeto do levantamento paralelo. |
| Estudo Técnico Preliminar (ETP) | `..._PE_06.2026__VISAN__ESTUDO_TECNICO_PRELIMINAR__ETP.pdf` | 10 | Integral. |
| Formulário de pesquisa de preços | `..._PE_06.2026__VISAN__FORMULARIO_DE_PESQUISA_DE_PRECOS.pdf` | 48 | Integral. Páginas sem texto (8, 9, 15, 20, 30, 35, 44) conferidas pelas imagens embutidas: são capas e fundos decorativos da proposta Jtech; a p. 12 traz a lista de clientes da Jtech em logotipos. |

Método: a ferramenta Read não renderiza PDF neste ambiente (sem poppler); o texto foi extraído página a página com
`pypdf` e as imagens das páginas sem texto foram extraídas e vistas. Números de página citados são os do PDF.

Observação de calendário: a sessão pública foi marcada para 24/03/2026. O certame já ocorreu; o resultado (vencedor,
preço, eventual prova de conceito) não consta destes documentos e deve ser consultado no Portal de Compras Públicas ou
no PNCP.

---

## 0. Dados-chave

| Tema | Condição | Fonte |
|---|---|---|
| Modalidade | Pregão eletrônico, modo de disputa aberto, Portal de Compras Públicas | Edital, preâmbulo e 5.12 (p. 1, 10) |
| Critério | Menor preço por lote (lote único, 7 itens) | 4.4 e 12.1 (p. 6, 18) |
| Valor estimado | R$ 366.365,13 | 1.1 (p. 2); TR 30 (p. 126) |
| Natureza | Bem/serviço comum (art. 6º, XIII, Lei 14.133) | TR 1.3 (p. 36) |
| Vigência | 1 ano, prorrogável nos termos do art. 107 da Lei 14.133 | 16.1 (p. 22); cl. 6ª (p. 30) |
| Implantação | Até 90 dias da Ordem de Compra (levantamento, conversão, implantação, parametrização, treinamento) | 11.1 (p. 18); cl. 3.1 (p. 26) |
| Hospedagem | Nuvem, datacenter no Brasil, provedor com ISO 9001, ISO 27001, ISO 27017, SOC 1/ISAE 3402 e SOC 3; VISAN pode exigir migração para infraestrutura própria | TR 4.4 (p. 43–48) |
| ME/EPP | Sem cota exclusiva; empate ficto de 5% mantido | 3.2 (p. 3); 4.6.5 (p. 7) |
| Consórcio | Permitido, com somatório de quantitativos técnicos e de valores econômico-financeiros | 3.10 (p. 5) |
| Subcontratação | Não admitida | TR 27.4 (p. 122) |
| Atestados | Mínimo 2, em instituição prestadora de serviço de água, com e-Sfinge TCE/SC e ≥ 8.500 ligações | 7.2.4.1 (p. 14) |
| Prova de conceito | 81 itens, nota 2/1/0, aprovação com ≥ 90% da pontuação máxima; 5 dias úteis após a adjudicação | TR 3.2 (p. 41); Anexo III (p. 130–144) |
| Garantia contratual | Não exigida | — (ausência) |
| Reajuste | IPCA, após 1 ano da data do orçamento | Minuta cl. 2.2 (p. 25) |
| Porte inferido | ≈ 17.000 ligações de água (8.500 = 50% do total, segundo o próprio edital) | 7.2.4.1 c (p. 14) |

---

## 1. Edital (incluindo minuta de contrato, partes contratuais do TR e Anexo III)

### 1.1 Objeto

"Contratação de empresa para o fornecimento, em caráter não exclusivo, de cessão de direito de uso, por prazo
determinado, de software de autoria própria com interface única para gestão comercial e de serviço operacional em
ambiente 100% web, sistema de informações geográficas (SIG/GIS) e prestação dos serviços técnicos especializados de:
customização, treinamento, migração de dados, implantação, integrações Web API, manutenção e suporte técnico", cobrindo
cadastramento, atendimento ao público, serviços, micromedição, faturamento, arrecadação, cobrança, financeiro,
relatórios, analytics, fiscalização, jurídico, controle de acesso e auditoria, qualidade de água, BI e omnichannel
(webchat, WhatsApp, Facebook Messenger e totens) (1.1, p. 1).

### 1.2 Lote, itens e valor estimado (p. 2; TR 30, p. 126)

| Item | Descrição | Unid. | Qtd. | Unitário (R$) | Total (R$) | Código interno |
|---|---|---|---|---|---|---|
| 1 | Implantação, conversão, configuração e treinamento do sistema comercial e operacional (em nuvem) e SIG/GIS | serviço | 1 | 77.900,00 | 77.900,00 | 200048 |
| 2 | Locação, manutenção e suporte técnico mensal do sistema comercial/operacional e SIG/GIS | mês | 12 | 14.966,66 | 179.599,92 | 200049 |
| 3 | Implantação, customização, ativação e treinamento do omnichannel | serviço | 1 | 5.833,33 | 5.833,33 | 200050 |
| 4 | Cessão de uso e/ou manutenção mensal, hospedagem e suporte do omnichannel | mês | 12 | 2.233,33 | 26.799,96 | 200051 |
| 5 | Implantação, conversão, configuração e treinamento do BI | serviço | 1 | 6.900,00 | 6.900,00 | 200052 |
| 6 | Locação, manutenção e suporte técnico mensal do BI | mês | 12 | 1.666,66 | 19.999,92 | 200053 |
| 7 | Customização do sistema comercial e operacional | hora | 200 | 246,66 | 49.332,00 | 200054 |
| | **Total do lote 1** | | | | **366.365,13** | |

Composição: serviços únicos R$ 90.633,33; recorrente mensal R$ 18.866,65 (R$ 226.399,80 em 12 meses); banco de horas
R$ 49.332,00. Todos os itens devem ser cotados, sob pena de desclassificação no lote (Obs. 01, p. 2; 4.2 g, p. 6).

### 1.3 Critério de julgamento, disputa e proposta

- Menor preço por lote, desde que atendidas as especificações (12.1, p. 18).
- Modo aberto: 10 minutos, prorrogação automática de 2 minutos sempre que houver lance nos 2 minutos finais; reinício da
  disputa possível se a diferença para o segundo colocado for ≥ 5%; intervalo mínimo entre lances de R$ 3.000,00
  (5.12–5.12.5, p. 10).
- Proposta: validade ≥ 60 dias; preço por item; marca "PRÓPRIA"; duas casas decimais; todos os custos incluídos
  (4.2, p. 6). Proposta ajustada ao último lance em até 2 horas, prorrogável a pedido (5.13.4–5.13.5, p. 10–11).
- Negociação pelo pregoeiro; diligências de exequibilidade (5.9.1, 5.13, p. 10).
- Datas: propostas de 10/03/2026 (18h) a 24/03/2026 (13h59); impugnação até 19/03/2026; sessão em 24/03/2026, 14h
  (p. 1). Impugnações e esclarecimentos até 3 dias úteis antes, só pelo portal (8.2, p. 16).
- Recursos: intenção imediata (prazo ≥ 10 min), razões em 3 dias úteis, contrarrazões em 3 dias úteis (9.1–9.3,
  p. 16–17).

### 1.4 Regime de execução

O edital e o TR não nomeiam o regime (empreitada por preço unitário ou global). A minuta fala em "preço total e global"
com "valor unitário" (cl. 2.1, p. 25); a medição é por item (serviço único, mês e hora). Na prática, preço unitário com
pagamento mensal após recebimento definitivo (TR 29 e 32, p. 124–129). Ver ambiguidade A-03.

### 1.5 Hospedagem (TR 4.4 a 4.16, p. 43–49)

- Toda a infraestrutura (banco, arquivos e aplicações) em datacenter no território nacional; vedado armazenamento ou
  espelhamento fora do país; comprovação da localização (4.4).
- Aplicação, banco e softwares relacionados em nuvem, "em empresa especializada na prestação de serviços de computação
  em nuvem" (4.4.7, 4.4.13).
- Provedor certificado em ISO 9001, ABNT NBR ISO/IEC 27001:2013, ISO/IEC 27017:2015, SOC 1/ISAE 3402 e SOC 3;
  documentos em nome do provedor; "os serviços em nuvem que não possuírem esses certificados não serão considerados para
  contratação e, consequentemente, eliminados do certame" (4.4.14–4.4.19).
- Serviços exigidos do provedor, descritos de forma genérica mas correspondentes ao catálogo de um hiperescalador:
  backup gerenciado com console, API e CLI, criptografia em trânsito e em repouso, logs aderentes a PCI DSS (4.4.20–4.4.24);
  autoescalabilidade por console, CLI ou SDK (4.4.25–4.4.26); serviço de logs; instâncias Linux/UNIX e Windows; cache em
  memória; banco relacional gerenciado com restauração em ponto no tempo; monitoramento contínuo de segurança; avaliação
  automatizada de vulnerabilidades (4.4.27–4.4.32, 4.11); discos SSD (4.6); inventário e histórico de configurações
  (4.7); painel de usuários conectados (4.8); imagem padrão protegida (4.9); painel de logs (4.10); alta disponibilidade
  24x7 com licenças e atualizações de SO, SGBD e segurança (4.12).
- Disponibilidade da nuvem ≥ 95% mensal; abaixo disso, desconto de 5% a 30% na mensalidade "conforme escala regressiva
  a ser definida no contrato"; abaixo de 60%, desconto de 100% (4.5, p. 47).
- A VISAN pode, "a qualquer tempo, diante de desempenho insatisfatório", transferir toda a infraestrutura para si; a
  contratada deve detalhar requisitos, preparar equipamentos e migrar o ambiente; "a proposta apresentada deverá
  contemplar um percentual correspondente de valor a ser deduzido das mensalidades" para esse caso (4.4.12, p. 45).
- Documento personalizado do provedor confirmando o atendimento e o vínculo com a proponente, antes do uso oficial e
  sempre que solicitado (4.14); acesso administrativo ao ambiente para a TI da VISAN, "sempre que possível" (4.15).
- Cópia de todos os dados a qualquer momento (4.13).

### 1.6 Condições de participação

- Empresa com ramo de atividade compatível, credenciada no Portal de Compras Públicas (3.1, p. 3).
- ME/EPP: não se aplicam os arts. 47 e 48 da LC 123/2006 (valor do lote acima de R$ 80.000 e objeto indivisível), mas
  vale o empate ficto de 5% e a regularização fiscal tardia em 5 dias úteis (3.2, 4.6.5, 4.6.10, p. 3–8). Comprovação de
  enquadramento emitida há menos de 120 dias, com declaração do Anexo IV (4.6.2, p. 7).
- Consórcio permitido: compromisso de constituição, empresa líder, somatório de quantitativos técnicos e de valores
  econômico-financeiros, responsabilidade solidária, registro antes do contrato (3.10, p. 5).
- Impedimentos usuais do art. 14 da Lei 14.133, cooperativas, OSCIP, doadores de campanha no município, empresas com
  sócios em comum disputando o mesmo item (3.3, 3.12, p. 3–5).
- Subcontratação: "Não será admitida subcontratação" (TR 27.4, p. 122). O ETP veda apenas a subcontratação das parcelas
  de maior relevância (ETP p. 4). Ver A-05.

### 1.7 Habilitação (7.2, p. 13–15; TR 27, p. 119–122)

Prazo: documentos anexados pelo vencedor em até 2 horas após o julgamento (7.1.1, p. 12). Documento sem validade
expressa vale 90 dias (7.1.3, 7.11).

**Jurídica:** ato constitutivo consolidado com ramo compatível; procuração se o representante não for sócio; Declaração
Conjunta do Anexo V (cumprimento dos requisitos, menor, trabalho degradante, cota PcD, ausência de sanção, custos
trabalhistas, não doação a campanha, impedimentos do art. 9º) (7.2.1, p. 13).

**Fiscal, social e trabalhista:** CNPJ; certidão conjunta federal/dívida ativa; certidões estadual e municipal do
domicílio; FGTS; CNDT; SICAF opcional (7.2.2, p. 13–14).

**Econômico-financeira:** certidão negativa de falência do distribuidor da sede; balanço patrimonial, DRE e demais
demonstrações dos 2 últimos exercícios (7.2.3, p. 14). **Não há índices** (liquidez, solvência), capital mínimo nem
patrimônio líquido mínimo. O ETP declara que a qualificação "será restrita" a esses documentos (ETP p. 3).

**Técnica (7.2.4.1, p. 14; TR 27.4, p. 121):**
- No mínimo **2 atestados** em nome da proponente, emitidos pela contratante titular (pessoa jurídica de direito
  público ou privado), comprovando a execução dos serviços do objeto "em uma instituição prestadora de serviço de água".
- Conteúdo obrigatório: (a) suporte técnico, manutenção, implantação e treinamento do sistema comercial e operacional;
  (b) "informações através de relatórios de envio de dados e-Sfinge do TCE/SC conforme item 13.1.15 do Termo de
  Referência"; (c) operacionalização de sistema de gestão comercial com no mínimo **8.500 ligações de água e/ou esgoto
  em seu somatório**, "cujo dado reflete a posição de 50% do número de ligações totais de água"; (d) tempo de serviço,
  local e natureza; (e) quantidade de ligações de água e esgoto, caracterização do bom desempenho e identificação de
  quem assina.
- **Não se exigem** certificações da empresa (ISO, CMMI, MPS.BR), registro em conselho, profissional com vínculo
  ou acervo técnico de pessoa física. As certificações exigidas são do provedor de nuvem (1.5).
- **Visita técnica facultativa**, agendada pelo telefone (49) 3090-2770 com no mínimo 3 dias úteis de antecedência;
  quem não visitar deve apresentar declaração formal de pleno conhecimento (TR 26.2, p. 119).
- Dados da empresa e bancários (Anexo VI) (7.2.5, p. 15).

### 1.8 Prova de conceito (TR 3.2, p. 41; Anexo III, p. 130–144)

- Feita pela "empresa vencedora", em data e hora fixadas por comissão, "após instalação dos sistemas no ambiente em
  Nuvem da PROPONENTE" (TR 3.2.1).
- Nota por item: atende integralmente 2; parcialmente 1; não atende 0. Aprovação com PA = PT × 100 / PM ≥ 90%
  (TR 3.2.2–3.2.7; Anexo III p. 143–144).
- Escopo: funcionalidades dos itens 5.1 a 23.1 do TR, e quantas mais a comissão julgar necessárias (TR 3.2.8–3.2.9).
  O SIG (item 24) não está no roteiro.
- Roteiro do Anexo III: 20 grupos, **81 itens pontuáveis** (pontuação máxima 162; aprovação exige ≥ 146 pontos, ou
  seja, perda máxima de 16 pontos):

| Grupo | Conteúdo | Itens |
|---|---|---|
| a | Arquitetura (4.2), interface (4.3), banco/hospedagem (4.4) | 3 |
| b | Controle de acesso: criar operador, bloqueio após 3 senhas erradas, login único, perfil só consulta | 4 |
| c | ≥ 10 cadastros: 2 usuários, 2 endereços, 6 unidades de consumo, economias mistas, tabelas (origem e ocorrência de leitura, setor, situações de água e esgoto, feriados, 3 grupos de faturamento, categorias, tarifas, 5 serviços, material) | 4 |
| d | Hidrômetros: cadastro, instalação/retirada com histórico, reinstalação | 3 |
| e | Leitura em dispositivo móvel (2 por ligação), impressão em campo, importação, crítica manual e automatizada, fatura por e-mail, comunicado de débito, arquivo para reimpressão em Zebra | 7 |
| f | Faturas no sistema, conferência com tabela tarifária, mensagens, tabela tarifária com % de esgoto | 4 |
| g | Revisão de consumo por vazamento (2 unidades) | 1 |
| h | Arrecadação: importação de arquivo, pagamento não identificado, débito automático FEBRABAN | 3 |
| i | Parcelamento de fatura e de serviço | 2 |
| j | Notificações de corte e consulta/reimpressão | 2 |
| k | Dívida ativa: inscrição, CDA, parcelamento | 3 |
| l | Contabilidade: contas, rubricas, parametrizações | 4 |
| m | Cadastro de 3 tipos de serviço com parâmetros de cobrança, layouts de OS, termo de ligação nova, materiais, equipes, veículos, setores | 1 |
| n | 3 OS: abertura, encerramento, monitoramento, roteirização, anexos | 5 |
| o | Atendimento: protocolo, histórico por matrícula, alteração e cancelamento de fatura com OS, cadastro de não usuário | 5 |
| p | App de OS: monitoramento de deslocamento, mapa (Google Maps), importação de cadastros | 3 |
| q | App do usuário em Android **e iOS** | 1 |
| r | 13 relatórios escolhidos pela comissão e autoatendimento no portal | 14 |
| s | Omnichannel: atendimento integrado ao protocolo, 2ª via, QR Code PIX, extrato, previsão de leitura, arquivos, **IA em linguagem natural** | 7 |
| t | BI: painéis de arrecadação, atendimento, cobrança, faturamento e indicadores estratégicos | 5 |

- Prazo: "Após a adjudicação a comissão de avaliação terá o prazo de 05 dias úteis para realização da Prova de Conceito
  e providências seguintes"; reprovada a vencedora, chama-se a segunda colocada (Anexo III, p. 144).
- Regra correlata: a solução deve atender **nativamente ≥ 90%** das funcionalidades dos itens 5.1 a 23.1; no máximo 10%
  por customização, entregue na implantação (TR 4.19, p. 49).

### 1.9 Prazos de execução

| Evento | Prazo | Fonte |
|---|---|---|
| Assinatura do contrato | 3 dias úteis da convocação; assinatura digital ICP-Brasil ou gov.br | 9.7, 10.4 (p. 17) |
| Implantação completa (requisitos, conversão, implantação, parametrização, adequações, treinamento) | até 90 dias da Ordem de Compra | 11.1 (p. 18); TR 3.1.1 (p. 38) cita "ordem de serviço" |
| Customizações (≤ 10% do escopo) | dentro da implantação | TR 4.19 (p. 49) |
| Integrações com outros sistemas da VISAN | dentro da implantação | TR 3.1.2.13 (p. 39) |
| Acompanhamento pós-implantação | ≥ 30 dias, ao menos 1 técnico | TR 3.1.2.5 (p. 39) |
| Treinamento | presencial, ≥ 40 horas | TR 3.1.3.1 (p. 40) |
| Recebimento provisório / definitivo | 5 / 30 dias úteis | cl. 3.5–3.6 (p. 26) |
| Devolução de dados no fim do contrato | 10 dias úteis | TR 3.1.2.14 (p. 40) |
| Consulta à base após o fim | 120 dias, 1 usuário | TR 3.1.2.14.1 e 26.1.9 (p. 40, 118–119) |

Descumprimento do prazo de implantação "ensejará a revogação do contrato e a aplicação das sanções" (11.3, p. 18).

### 1.10 Equipe e presença física

- Equipe de no mínimo dois especialistas na solução, presencial na sede durante levantamento, implantação,
  parametrização, validação e capacitação (TR 3.1.1 b, p. 37).
- Preposto aceito pela Administração "no local do serviço" (cl. 4.2, p. 27).
- Instrutores com despesas de hospedagem, transporte e alimentação por conta da contratada (TR 3.1.3.3, p. 40).
- Substituição de técnicos e instrutores a critério da autarquia (TR 3.1.2.6, 3.1.3.4).

### 1.11 Migração de dados e GIS

- Migração e conversão de todo e qualquer dado do sistema comercial atual; adaptações de base e fórmulas; digitação e
  refino de cadastros por conta da contratada; teste de migração com validação quantitativa homologada pela TI e pelo
  setor comercial (TR 3.1.1 c–f, 3.1.2.1–3.1.2.4, p. 38).
- Responsabilidade por perdas e adulterações sob sua guarda (3.1.2.7).
- Catálogo e dicionário de dados em XLSX, CSV ou PDF (3.1.2.9); governança de dados com versionamento e data lineage
  (3.1.2.12).
- GIS estruturado a partir do cadastro técnico em DWG usado hoje pela VISAN, migrado pela contratada para PostgreSQL com
  PostGIS "ou outro software similar"; acesso para consulta e edição pelo QGIS (TR 24.3.18–24.3.19, p. 114).

### 1.12 Integrações

- APIs REST com JSON, compatíveis com a e-PING (TR 3.1.2.13.1, p. 40); APIs de consulta e inserção respeitando regras
  internas (4.2.6).
- Contabilidade: lançamentos por evento, fechamento mensal, integração diária "automática e sem intermediários" com o
  sistema contábil existente (TR 13.1–13.1.7, p. 75–76).
- e-Sfinge do TCE/SC: módulo tributário (estorno, diário de arrecadação, crédito tributário e baixa, contas a receber),
  cadastros de contribuinte e imobiliário; regras de consistência (CONs) impeditivas e de alerta; a contratada responde
  pelas penalidades do Tribunal por atraso ou inconsistência (TR 13.1.15, p. 77–78).
- SINISA: indicadores pré-cadastrados na implantação e relatórios (TR 19.85, p. 96; 22.6.9–22.6.10, p. 107).
- Arrecadação FEBRABAN, débito automático, PIX/QR Code (TR 11; Anexo III h, s).
- LDAP (TR 4.19.1.7, p. 50).

### 1.13 Requisitos não funcionais

- 100% web, sem instalação nem plug-in; Edge 88+, Chrome 100+, Firefox 116+ (TR 4.1.1, 4.3, p. 41–43).
- Três camadas, sem regra de negócio na apresentação, ambientes de desenvolvimento, homologação e produção com
  ferramenta automatizada de promoção, base única para todos os módulos, linguagem orientada a objetos "interpretada,
  robusta, portável [...] multithread" (TR 4.2, p. 42).
- Desempenho: busca, cadastro, exclusão e edição em base de 50.000 usuários com 20 acessos simultâneos em até 10 s;
  processamento de arquivos e relatórios em até 2 min (TR 4.17, p. 49).
- Monitoramento de eventos de negócio e de lotes com alertas (TR 4.18).
- Controle de acesso e trilha de auditoria com versão, usuário, data/hora, campo, valor anterior e posterior para
  cadastro, consumo, fatura, arrecadação e OS (TR 4.19.1, p. 50–51).
- Apps Android e iOS (TR 18, p. 91); app de OS e de leitura em Android via APK (TR 16.2, p. 88).

### 1.14 Manutenção, suporte e níveis de serviço (TR 25, p. 114–118)

- Suporte em português, portal de chamados ou helpdesk, telefone no Brasil, chamados ilimitados, das 07h30 às 17h30,
  de segunda a sexta; paradas fora do expediente com agendamento (25, 25.2–25.7).
- Atualizações enviadas assim que disponíveis, sem prejuízo de desempenho; atualização do software de infraestrutura
  (servidor HTTP, aplicação, banco) (25.1.1–25.1.2).
- Alterações por exigência legal, judicial, do Tribunal de Contas ou do Ministério Público **sem custo adicional**
  (25.1.5); demais alterações mediante orçamento com horas e valor de homem-hora por perfil (25.1.6).
- Prazos de solução por severidade (horas úteis, 08h–17h): S1 2 h; S2 4 h; S3 6 h; S4 16 h; S5 32 h; severidade pela
  matriz impacto × criticidade, com o impacto definido pela autarquia (25.8–25.10).
- Multa redutora de 5% da fatura global mensal por severidade descumprida, cumulativa (25.11).
- Disponibilidade de 99% em horário comercial (07h–11h30 e 13h–17h30); 5% do faturamento mensal por faixa de até 10% de
  indisponibilidade abaixo de 99%, até 30% (25.12).

### 1.15 Pagamento e medição (13, p. 18–20; cl. 5; TR 32, p. 127–129)

- Até o 10º dia útil do mês seguinte à prestação, após recebimento definitivo e nota fiscal, com todas as certidões da
  habilitação válidas anexadas.
- Retenção na fonte de IR, INSS e ISS (Decreto municipal 20.412/23); sem retenção de PIS, Cofins e CSLL.
- NF em nome da VISAN; XML para nfe@videira.sc.gov.br; crédito em conta.
- Medição implícita: itens 1, 3 e 5 por entrega; 2, 4 e 6 por mês; 7 por hora consumida. O edital não define a partir
  de quando a mensalidade começa a contar (ver A-11).

### 1.16 Reajuste e reequilíbrio

IPCA, após 1 ano da data do orçamento (data em branco na minuta); reequilíbrio por requerimento no portal AtendeNet,
assinado com certificado ICP-Brasil/gov.br, decisão em até 30 dias (cl. 2.2–2.3, p. 25–26).

### 1.17 Garantias

- **Garantia de execução (art. 96 da Lei 14.133): não exigida.** Garantia de proposta: não exigida (o item 15.10 fala
  em "perda da garantia de proposta", mas nenhuma é pedida).
- Garantia técnica: correção de vícios às expensas da contratada mesmo após o recebimento definitivo (cl. 1.3, 4.6);
  responsabilidade pelo Código de Defesa do Consumidor, com desconto em pagamentos (cl. 4.7).

### 1.18 Sanções e multas

- Licitação: multa de 0,5% a 30% do valor do contrato; 0,5% a 15% para falta de documento, não manutenção da proposta e
  recusa em assinar; 30% para declaração falsa, fraude e atos lesivos; impedimento de até 3 anos; inidoneidade
  (15.2–15.9, p. 20–21).
- Contrato: advertência; multa de 10% na reincidência com dano; atraso superior a 5 dias úteis, 0,5% ao dia sobre a
  parcela inadimplida a partir do 6º dia, limitado a 30 dias; inexecução parcial, 10%; atraso superior a 30 dias, 15%;
  impedimento e inidoneidade (cl. 9.3, p. 31).
- Somam-se as multas redutoras dos níveis de serviço (1.14) e os descontos de disponibilidade da nuvem (1.5).

### 1.19 Confidencialidade e LGPD

- Sigilo absoluto sobre dados e informações, inclusive após o término (TR 3.1.2.8, 26.1.3).
- Execução conforme a LGPD, "com funcionalidades nativas de controle de consentimento, rastreamento de logs de acesso a
  dados pessoais sensíveis e mecanismos de anonimidade" (TR 26.1.4, p. 118).
- Soberania de dados: proibição de armazenamento ou espelhamento fora do Brasil, com base nos arts. 33 a 36 da LGPD
  (TR 4.4, p. 43).
- O edital não exige contrato de operador, relatório de impacto, encarregado ou plano de resposta a incidentes.

### 1.20 Propriedade intelectual, código-fonte e art. 93 da Lei 14.133

- O objeto é **cessão de direito de uso, não exclusiva e por prazo determinado, de software de autoria própria**; o TR
  e o ETP enquadram o modelo na Lei 9.609/1998 (licença temporária com manutenção) (TR 3, p. 37; ETP p. 6).
- **Não há** cláusula de cessão de direitos patrimoniais (art. 93), de entrega de código-fonte, de depósito em escrow
  nem de licença perpétua ao fim do contrato. O art. 93 não é citado em nenhum dos três documentos.
- Ponto de atenção: o item 7 (200 horas de customização) e as customizações de até 10% previstas na implantação são
  desenvolvimento sob encomenda. O art. 93 da Lei 14.133 impõe, como regra, a cessão dos direitos patrimoniais de
  software desenvolvido em contratação de serviço técnico especializado. O edital é omisso; a Administração pode
  pleitear depois a titularidade dessas customizações (ver A-16).
- O ETP exige que não se ofereça software livre ou de código aberto e prevê "certificado de autoria intelectual com
  registro no Instituto Nacional de Propriedade Industrial – INPI de todos os módulos contratados" (ETP p. 2–3). Essa
  exigência **não foi transposta** para a habilitação do edital (ver A-15).
- As bases de dados são de propriedade exclusiva da autarquia, entregues imediatamente quando solicitadas (TR 26.1.1).

### 1.21 Transição ao fim do contrato

- Entrega integral dos dados em até 10 dias úteis, em formato aberto e estruturado (CSV, JSON ou XML), com dicionário de
  dados e estrutura relacional (TR 3.1.2.14, p. 40).
- Consulta à base por 120 dias após o término ou rescisão, com um usuário, sem suporte nem atualização (TR 3.1.2.14.1;
  26.1.9).
- Possibilidade, durante a vigência, de migrar a hospedagem para a VISAN, com dedução de percentual das mensalidades
  (TR 4.4.12).
- Não há plano de transição assistida, repasse de conhecimento ao sucessor nem prazo de convivência.

---

## 2. Estudo Técnico Preliminar

### 2.1 Necessidade

A VISAN atende abastecimento de água, esgotamento sanitário **e coleta de resíduos sólidos** em Videira (p. 1). O
sistema comercial atual "por ser um software público e livre, vem sofrendo com falta de atualizações", com melhorias
compradas em horas técnicas (p. 2). O documento não nomeia o sistema; o perfil (software público livre de gestão
comercial de saneamento) é compatível com o GSAN, já estudado nos levantamentos 01 a 04 e 15a — **inferência, não
confirmada**. Objetivos: usabilidade, eliminação de retrabalho, dados confiáveis para reguladores e fiscalizadores,
LGPD, LRF, BI e omnichannel (p. 1–2).

### 2.2 Requisitos da contratação (p. 2–4)

- Licença de uso por prazo determinado com implantação, conversão, treinamento, suporte, hospedagem e manutenção.
- Vedação a software livre ou de código aberto e exigência de registro no INPI de todos os módulos.
- Suporte por severidade, criticidade e impacto; treinamento presencial.
- Qualificação econômico-financeira restrita a balanço, DRE e demonstrações dos 2 últimos exercícios e certidão de
  falência.
- Atestado de capacidade técnica (justificativa idêntica à do edital).
- Vedada a subcontratação das parcelas de maior relevância.
- Sem cota exclusiva de ME/EPP (art. 49, III, LC 123/2006).

### 2.3 Levantamento de mercado (p. 5–6)

Três alternativas, sem nomear fornecedores nem produtos:

1. Desenvolvimento de sistema próprio — exigiria estrutura e ao menos dois servidores de TI.
2. Licença vitalícia (on-premise) — menor custo de implantação que o desenvolvimento, mas exige estrutura interna para
   atualizações e manutenção.
3. **Licença por prazo determinado com manutenção e suporte pela contratada — escolhida.**

Justificativa: a VISAN não tem profissionais de TI nem cargo de programador; criar cargos exigiria lei. O modelo SaaS não
é tratado como categoria própria; a hospedagem em nuvem aparece como serviço agregado. Os fornecedores efetivamente
consultados estão apenas no formulário de preços (seção 3).

### 2.4 Quantidades (p. 4–5)

Somente os 7 itens do lote, para 12 meses. **O ETP não informa** número de ligações, economias, usuários do sistema,
leituristas, coletores, equipes de campo, atendentes nem volume de atendimentos do omnichannel. O único dado de porte
nos documentos é o do edital (8.500 ligações = 50% do total, logo ≈ 17.000 ligações de água) e a referência de desempenho
de 50.000 usuários do TR 4.17.

### 2.5 Estimativa de valor

R$ 366.365,13, pela média das cotações do formulário de preços (p. 7–8).

### 2.6 Justificativas técnicas

Lote único por eficiência de gestão e fiscalização (p. 8–9); modelo de licença temporária amparado na Lei 9.609/1998
(p. 6).

### 2.7 Riscos

**Não há seção de riscos.** O ETP também declara não haver contratações correlatas (p. 10), embora o TR exija integração
com o sistema contábil existente.

### 2.8 Resultados pretendidos (p. 9)

Agilidade, confiabilidade dos dados, melhor atendimento, menos retrabalho e papel, economia financeira. Sem metas
mensuráveis.

### 2.9 Outros

Previsto no Plano de Contratações 2026, item 40, para novembro, antecipado mediante justificativa (p. 2). Sem impacto
ambiental (p. 10). Sem providências prévias de capacitação dos fiscais (p. 9–10). Assina Gabriel Gregolon, Diretor
Administrativo, que também é fiscal do contrato.

---

## 3. Formulário de pesquisa de preços

### 3.1 Metodologia (p. 1, 4–5)

- Período: 23/01/2026 a 24/02/2026. Fonte: inciso IV (pesquisa direta com fornecedores). Valor de referência: **média
  simples** das três cotações.
- Painéis de preços descartados porque os contratos encontrados "não correspondem à mesma quantidade de economias
  atendidas pela VISAN"; fornecedores escolhidos por pesquisa em sites especializados e consulta a municípios que usam
  sistemas semelhantes.
- Documentação comprobatória de 43 páginas anexada (p. 6–48).

### 3.2 Cotações por item

| Item | Strategos (23/01/2026) | Jtech (04/02/2026) | Waterfy (24/02/2026) | Referência (média) |
|---|---|---|---|---|
| 1 Implantação comercial/operacional + SIG | 25.000,00 | 127.700,00 | 81.000,00 | 77.900,00 |
| 2 Mensalidade comercial/operacional + SIG | 15.000,00 | 12.750,00 | 17.150,00 | 14.966,66 |
| 3 Implantação omnichannel | 2.000,00 | 4.500,00 | 11.000,00 | 5.833,33 |
| 4 Mensalidade omnichannel | 3.000,00 | 2.200,00 | 1.500,00 | 2.233,33 |
| 5 Implantação BI | 2.500,00 | 3.200,00 | 15.000,00 | 6.900,00 |
| 6 Mensalidade BI | 900,00 | 350,00 | 3.750,00 | 1.666,66 |
| 7 Hora de customização | 320,00 | 220,00 | 200,00 | 246,66 |
| **Total do lote (12 meses, 200 h)** | **320.300,00** | **363.000,00** (calculado) | **415.800,00** | **366.365,13** |

Conferência: as médias por item batem com a referência (truncadas no centavo). A média dos três totais é
R$ 366.366,67. A Jtech não apresenta total no formato do lote; o valor de R$ 363.000,00 foi calculado com os preços que
o formulário atribuiu a ela. Dispersão alta: implantação de R$ 25.000 a R$ 127.700 (5,1×); mensalidade de BI de R$ 350
a R$ 3.750 (10,7×).

### 3.3 Fornecedores consultados

**Strategos Engenharia, Informática e Consultoria Ltda.** — CNPJ 00.175.275/0001-66, Pinhais/PR. Orçamento simples com
os 7 itens, total R$ 320.300,00, validade 60 dias (p. 6–7). Não descreve produto, módulos nem clientes.

**Jtech Soluções em Informática Ltda.** — São José/SC, grupo Veolia, produto **Sansys** (p. 8–44):
- Apresenta-se com mais de 20 anos no setor, 3 milhões de contas, R$ 400 milhões de faturamento mensal sob gestão, 105
  clientes, 4.500 usuários, 150 cidades, 1.000 atendimentos de suporte por mês, 3 versões por ano (p. 19). Clientes em
  logotipo: Iguá, Águas de Joinville, Veolia, Verde Ambiental Alagoas, EMASA, SAMAE Jaraguá do Sul, Conasa Águas do
  Sertão, Interagua; países: Brasil, Equador, Peru e Nova Zelândia (p. 12, imagem).
- Módulos: medição, faturamento, cadastro, clientes, arrecadação, contabilidade (integração SAP, TOTVS, Oracle),
  cobrança, fraude com IA, operacional, gerencial (indicadores SNIS), autoatendimento (site, app, totem, WhatsApp), CTI
  (com Canal Telecom), administração, laboratório (myLIMS), workflow, crítica de leitura com IA, telemetria, BI (Qlik
  Sense, BigQuery), omnichannel (WhatsApp, Facebook, Instagram, Telegram, webchat), OS online, GIS (p. 21–29).
- Modelo de preço: implantação única e manutenção mensal **proporcional ao número de ligações de água** (p. 31–32).
- Proposta: base R$ 84.200,00 de implantação e R$ 8.500,00/mês, com usuários ilimitados, apps de leitura e OS e portal
  (p. 34). Adicionais: omnichannel WhatsApp receptivo com franquia de 3.000 atendimentos, R$ 4.500 + R$ 2.200/mês; BI
  R$ 3.200 + **R$ 350/mês por usuário ativo**; GIS R$ 43.500 + R$ 4.250/mês, com ressalva de variação conforme qualidade
  dos dados; laboratório myLIMS R$ 15.000 + R$ 2.800/mês (p. 36–37). Hora de customização R$ 220 (p. 38). Total com
  adicionais: R$ 150.400 de implantação e R$ 18.100/mês (p. 39). Excedentes do omnichannel: R$ 0,65 receptivo e R$ 0,85
  ativo (p. 40). Pagamento: 30% da implantação no ato e saldo em até 3 vezes (p. 41). Fora da proposta: Sansys Pay,
  Smart Meter, fraude e crítica com IA, Waste (p. 42).
- Os valores do formulário para a Jtech correspondem a: item 1 = base + GIS (84.200 + 43.500); item 2 = 8.500 + 4.250;
  item 6 = um único usuário de BI. O laboratório foi excluído.
- O material traz termo de confidencialidade da Jtech (p. 10), mas foi publicado como anexo do processo.

**Waterfy** — Limeira/SP, plataforma própria com quase 20 anos (origem na antiga Águas de Limeira, 2005), atuação em
Brasil, Espanha e México, "simultaneamente em 163 municípios" e 20 milhões de pessoas (p. 45). Clientes declarados:
Companhia Ituana de Saneamento (Itu/SP, ≈ 63.000 ligações), EMASA (Itabuna/BA, ≈ 52.000), SANEAR/GS Inima
(Araçatuba/SP, ≈ 73.000), GMAS (Veracruz/MX, ≈ 210.000); derivações em BRK Limeira (≈ 105.000), BRK Maceió (≈ 206.000) e
Rio+ Saneamento (≈ 488.000) (p. 46). Total R$ 415.800,00 (p. 47). Omnichannel com tarifa variável: R$ 0,07 por
atendimento recebido, R$ 0,11 ativo e R$ 0,52 de marketing; "valores meramente indicativos" (p. 48). Assina Maurício
Castilho Flores, diretor financeiro.

### 3.4 Observações sobre a pesquisa

- Nenhum dos três fornecedores tem cliente declarado do porte de Videira (≈ 17.000 ligações); o menor citado tem
  ≈ 52.000.
- Custos variáveis do omnichannel (Jtech e Waterfy) e o preço por usuário do BI (Jtech) não entraram na estimativa; o
  edital exige preço fixo.
- Só Jtech (Santa Catarina) aparece com histórico em autarquias catarinenses, condição relevante para o atestado de
  e-Sfinge.

---

## 4. Tabela de requisitos e condições

Legenda — Obrig.: O = obrigatório (eliminatório ou contratual); C = condicional ou facultativo; I = informativo.
Impacto para fornecedor SaaS novo: **A** alto (pode impedir participação ou contratação), **M** médio (custo ou esforço
relevante), **B** baixo.

### 4.1 Edital (ED)

| ID | Cláusula / página | Texto fiel ou síntese | Categoria | Obrig. | Impacto para SaaS novo |
|---|---|---|---|---|---|
| ED-001 | 1.1, p. 1 | "cessão de direito de uso, por prazo determinado, de software de autoria própria com interface única [...] em ambiente 100% web" | Comercial/contratual | O | M — exige titularidade do software; revenda de terceiro não serve |
| ED-002 | Obs. 01, p. 2; 4.2 g | Cotar todos os itens do lote, sob pena de desclassificação | Comercial/contratual | O | M — exige omnichannel, BI e SIG próprios ou integrados |
| ED-003 | 4.4, 12.1, p. 6, 18 | Menor preço por lote | Comercial/contratual | O | B |
| ED-004 | p. 2 | Valor estimado R$ 366.365,13 | Comercial/contratual | I | M — teto de referência para 12 meses |
| ED-005 | 5.12.5, p. 10 | Intervalo mínimo entre lances de R$ 3.000,00 | Comercial/contratual | O | B |
| ED-006 | 4.2 a–d, p. 6 | Validade ≥ 60 dias; marca "PRÓPRIA"; preços com todos os custos | Comercial/contratual | O | B |
| ED-007 | 5.13.4, p. 10 | Proposta ajustada em até 2 horas após a negociação | Comercial/contratual | O | B |
| ED-008 | 3.2, p. 3; 4.6.5, p. 7 | Sem cota ME/EPP; empate ficto de 5% preservado | Habilitação | C | B — benefício se a empresa for ME/EPP |
| ED-009 | 3.10, p. 5 | Consórcio com "somatório dos quantitativos de cada consorciado" para habilitação técnica | Habilitação | C | A (positivo) — via de entrada para quem não tem atestado |
| ED-010 | 3.12, p. 5 | Empresas com sócios em comum não podem disputar o mesmo item | Habilitação | O | B |
| ED-011 | 7.1.1, p. 12 | Habilitação anexada em até 2 horas após o julgamento | Habilitação | O | B |
| ED-012 | 7.2.1.1, p. 13 | Contrato social com ramo de atividade compatível com o objeto | Habilitação | O | B — conferir CNAE |
| ED-013 | 7.2.1.5, p. 13 | Declaração Conjunta (Anexo V) | Habilitação | O | B |
| ED-014 | 7.2.2, p. 13–14 | CNPJ, CND federal, estadual, municipal, FGTS, CNDT | Habilitação | O | B |
| ED-015 | 7.2.3.1, p. 14 | Certidão negativa de falência da sede | Habilitação | O | B |
| ED-016 | 7.2.3.2, p. 14 | "balanço patrimonial, demonstração de resultado de exercício e demais demonstrações contábeis dos 2 (dois) últimos exercícios sociais" | Habilitação | O | M — empresa recém-criada só tem balanço de abertura (art. 65, §1º, Lei 14.133) |
| ED-017 | 7.2.4.1, p. 14 | "no mínimo 2 (dois) Atestados [...] comprovando que a proponente já tenha executado, em uma instituição prestadora de serviço de água, os serviços definidos no objeto" | Habilitação | O | **A** — produto novo não tem atestado |
| ED-018 | 7.2.4.1 a | Atestado deve abranger suporte, manutenção, implantação e treinamento do sistema comercial e operacional | Habilitação | O | **A** |
| ED-019 | 7.2.4.1 b | Atestado com "relatórios de envio de dados e-Sfinge do TCE/SC" | Habilitação | O | **A** — restringe, na prática, a quem atende entes de SC |
| ED-020 | 7.2.4.1 c | "no mínimo 8.500 (oito mil e quinhentos) ligações de água e/ou esgoto em seu somatório" | Habilitação | O | **A** |
| ED-021 | 7.2.4.1 d–e | Tempo de serviço, local, natureza, quantidade de ligações, bom desempenho, identificação do signatário | Habilitação | O | M |
| ED-022 | 7.2.5, p. 15 | Dados da empresa e bancários (Anexo VI) | Habilitação | O | B |
| ED-023 | TR 26.2, p. 119 | Visita técnica facultativa ou declaração formal de pleno conhecimento | Habilitação | C | B — custo de deslocamento a Videira/SC |
| ED-024 | TR 27.4, p. 122 | "Não será admitida subcontratação" | Comercial/contratual | O | M — omnichannel, BI e GIS precisam ser próprios ou licenciados de forma que não configure subcontratação |
| ED-025 | 9.7, 10.4, p. 17 | Assinatura em 3 dias úteis, com certificado ICP-Brasil ou gov.br | Comercial/contratual | O | B |
| ED-026 | 16.1, p. 22 | Vigência de 1 ano, prorrogável (art. 107) | Comercial/contratual | O | B |
| ED-027 | cl. 2.2, p. 25 | Reajuste pelo IPCA após 1 ano da data do orçamento | Comercial/contratual | O | B |
| ED-028 | cl. 2.3, p. 26 | Reequilíbrio por requerimento, decisão em 30 dias | Comercial/contratual | C | B |
| ED-029 | 11.1, p. 18 | Implantação completa em até 90 dias da Ordem de Compra | Serviço | O | **A** — inclui migração, GIS e customizações |
| ED-030 | 11.3, p. 18 | Atraso na implantação "ensejará a revogação do contrato" e sanções | Comercial/contratual | O | A |
| ED-031 | cl. 3.5–3.6, p. 26 | Recebimento provisório em 5 e definitivo em 30 dias úteis | Comercial/contratual | O | M — fluxo de caixa |
| ED-032 | 13.1, p. 18 | Pagamento até o 10º dia útil do mês seguinte, após recebimento definitivo e com certidões | Comercial/contratual | O | M — fluxo de caixa |
| ED-033 | 13.2, p. 19 | Retenção de IR, INSS e ISS | Comercial/contratual | O | B |
| ED-034 | 13.3–13.5, p. 19 | NF em nome da VISAN; XML para nfe@videira.sc.gov.br | Comercial/contratual | O | B |
| ED-035 | cl. 4.2, p. 27 | Preposto aceito pela Administração "no local do serviço" | Serviço | O | M |
| ED-036 | cl. 4.7, p. 27 | Responsabilidade por danos (CDC) com desconto em pagamentos | Comercial/contratual | O | M |
| ED-037 | cl. 4.19, p. 28 | Ônus por erro de dimensionamento da proposta | Comercial/contratual | O | M — custos variáveis de nuvem e omnichannel ficam com a contratada |
| ED-038 | 15.5, p. 21 | Multa de 0,5% a 30% do valor do contrato na fase de licitação | Comercial/contratual | O | M |
| ED-039 | cl. 9.3, p. 31 | Atraso: 0,5%/dia a partir do 6º dia útil, até 30 dias; inexecução parcial 10%; atraso > 30 dias 15% | Comercial/contratual | O | A |
| ED-040 | — | Garantia de execução não exigida | Comercial/contratual | I | B (positivo) |
| ED-041 | TR 1.3, p. 36 | Objeto de natureza comum | Comercial/contratual | I | B |
| ED-042 | TR 3, p. 37 | Envio de todas as atualizações assim que disponíveis, sem prejuízo de desempenho | Serviço | O | B — natural em SaaS |
| ED-043 | TR 3.1.1, p. 37 | Plano de implantação com fases, tarefas, ferramentas, testes e plano de treinamento | Serviço | O | M |
| ED-044 | TR 3.1.1 b, p. 37 | "equipe técnica formada por no mínimo dois especialistas na solução ofertada [...] presencialmente na sede da Autarquia" | Serviço | O | A — custo de equipe alocada em Videira/SC |
| ED-045 | TR 3.1.1 c–f; 3.1.2.1–3.1.2.4, p. 38 | Migração de todo dado do sistema atual, digitação e refino de cadastro pela contratada, teste com validação quantitativa | Serviço | O | A — depende de conhecer o legado |
| ED-046 | TR 3.1.2.5, p. 39 | Técnico por ≥ 30 dias após a implantação | Serviço | O | M |
| ED-047 | TR 3.1.2.7–3.1.2.8 | Responsabilidade por perda ou adulteração; sigilo absoluto, inclusive após o contrato | Não funcional | O | M |
| ED-048 | TR 3.1.2.9 | Catálogo e dicionário de dados em XLSX, CSV ou PDF | Não funcional | O | B |
| ED-049 | TR 3.1.2.12 | Governança de dados com versionamento e data lineage | Não funcional | O | M |
| ED-050 | TR 3.1.2.13, 3.1.2.13.1, p. 39–40 | Integração com os demais sistemas da VISAN na implantação; APIs REST/JSON, e-PING | Técnico-funcional | O | M |
| ED-051 | TR 3.1.2.14, p. 40 | Dados ao fim do contrato em 10 dias úteis, CSV/JSON/XML, com dicionário e estrutura relacional | Não funcional | O | B |
| ED-052 | TR 3.1.2.14.1; 26.1.9 | Consulta à base por 120 dias após o término, 1 usuário | Serviço | O | B — custo de manter o tenant ativo |
| ED-053 | TR 3.1.3.1–3.1.3.4, p. 40 | Treinamento presencial ≥ 40 h, material e instrutores por conta da contratada | Serviço | O | M |
| ED-054 | TR 3.1.3.5–3.1.3.7, p. 40–41 | Capacitação avançada de TI e comercial; plataforma web de material e dúvidas por prazo indeterminado; manual online em pt-BR | Serviço | O | M |
| ED-055 | TR 3.2, p. 41 | Prova de conceito no ambiente em nuvem da proponente, nota 2/1/0, aprovação ≥ 90% | Técnico-funcional | O | **A** |
| ED-056 | Anexo III, p. 130–144 | Roteiro de 81 itens em 20 grupos; 5 dias úteis após a adjudicação; reprovação chama o segundo | Técnico-funcional | O | **A** — exige produto completo e pronto |
| ED-057 | Anexo III q, p. 140 | App do usuário em Android e iOS | Técnico-funcional | O | A |
| ED-058 | Anexo III e, p. 133–134 | Leitura e emissão de fatura em dispositivo móvel com impressão térmica (Zebra) | Técnico-funcional | O | A |
| ED-059 | Anexo III s; TR 21.6, 21.25 | Omnichannel integrado ao protocolo, PIX, IA com linguagem natural | Técnico-funcional | O | A |
| ED-060 | Anexo III t; TR 22.6 | Painéis de BI e indicadores SINISA pré-configurados | Técnico-funcional | O | M |
| ED-061 | TR 4.1.1, 4.3.1–4.3.2, p. 41–43 | 100% web sem plug-in; Edge 88+, Chrome 100+, Firefox 116+ | Não funcional | O | B |
| ED-062 | TR 4.2, p. 42 | Três camadas; ambientes dev/homologação/produção com ferramenta automatizada; base única; linguagem OO multithread; APIs | Não funcional | O | M |
| ED-063 | TR 4.4, p. 43 | Datacenter no Brasil; vedado armazenamento ou espelhamento fora; comprovação | Não funcional | O | M — exige região brasileira |
| ED-064 | TR 4.4.13, p. 45 | Hospedagem em "empresa especializada na prestação de serviços de computação em nuvem" | Não funcional | O | M |
| ED-065 | TR 4.4.14–4.4.18, p. 45–46 | Provedor com ISO 9001, ISO/IEC 27001, 27017, SOC 1/ISAE 3402 e SOC 3; sem eles, eliminação | Não funcional | O | **A** — exclui VPS comum; só hiperescaladores atendem com folga |
| ED-066 | TR 4.4.20–4.12, p. 46–48 | Backup gerenciado com console/API/CLI, autoescala, logs, cache, banco gerenciado com PITR, monitoramento de segurança, varredura de vulnerabilidades, SSD, inventário, painel de usuários | Não funcional | O | A — redação moldada em serviços de hiperescalador |
| ED-067 | TR 4.4.12, p. 45 | VISAN pode exigir hospedagem local; proposta deve prever percentual de dedução das mensalidades | Comercial/contratual | O | **A** — SaaS puro precisa de versão instalável e de preço para esse cenário |
| ED-068 | TR 4.5, p. 47 | Disponibilidade da nuvem ≥ 95%; desconto de 5% a 30%; abaixo de 60%, 100% | Não funcional | O | M |
| ED-069 | TR 4.13; 26.1.1 | Cópia de todos os dados a qualquer momento; bases de propriedade exclusiva da autarquia | Não funcional | O | B |
| ED-070 | TR 4.14, p. 48 | Documento personalizado do provedor de nuvem confirmando atendimento e vínculo com a proponente | Habilitação | O | M — depende de carta do provedor |
| ED-071 | TR 4.15, p. 49 | Acesso administrativo ao ambiente de nuvem para a TI da VISAN, "sempre que possível" | Não funcional | C | M — conflita com isolamento multi-tenant |
| ED-072 | TR 4.17, p. 49 | Até 10 s com 50.000 usuários e 20 acessos simultâneos; arquivos e relatórios em até 2 min | Não funcional | O | M — precisa de teste de carga |
| ED-073 | TR 4.18, p. 49 | Alertas de eventos de negócio e de falhas de lote | Não funcional | O | M |
| ED-074 | TR 4.19, p. 49 | ≥ 90% nativo nos itens 5.1–23.1; ≤ 10% customizado, entregue na implantação | Técnico-funcional | O | **A** |
| ED-075 | TR 4.19.1, p. 50–51 | Controle de acesso com LDAP, senha forte, bloqueio, timeout, perfis; trilha com valor anterior e posterior | Não funcional | O | M |
| ED-076 | TR 5–24, p. 51–114 | Módulos: cadastro, hidrometria, leituras, leitura móvel, revisão, faturamento, arrecadação, cobrança e dívida ativa, contabilidade, serviços, atendimento, app de OS, GPS, apps, relatórios, totem/portal, omnichannel, BI, qualidade da água, SIG | Técnico-funcional | O | A — detalhamento no levantamento do TR |
| ED-077 | TR 13.1.15, p. 77–78 | e-Sfinge TCE/SC: remessas tributárias, CONs, responsabilidade por penalidades do Tribunal | Técnico-funcional | O | **A** — requisito estadual específico |
| ED-078 | TR 13.1.7, p. 76 | Integração diária automática com o sistema contábil existente | Técnico-funcional | O | M |
| ED-079 | TR 19.85, p. 96; 22.6.9–22.6.10, p. 107 | Indicadores e relatórios do SINISA nativos | Técnico-funcional | O | M |
| ED-080 | TR 24.3.18–24.3.19, p. 114 | GIS migrado do cadastro técnico em DWG para PostgreSQL/PostGIS; acesso via QGIS | Técnico-funcional | O | M — compatível com a pilha do Ziip SAN |
| ED-081 | TR 25–25.7, p. 114–115 | Suporte em português, portal de chamados, telefone no Brasil, ilimitado, 07h30–17h30 | Serviço | O | M |
| ED-082 | TR 25.1.5, p. 114–115 | Mudanças por exigência legal, judicial, do TCE ou do MP sem custo adicional | Comercial/contratual | O | M |
| ED-083 | TR 25.1.6, p. 115 | Demais mudanças por orçamento com horas e homem-hora por perfil | Comercial/contratual | O | B |
| ED-084 | TR 25.10, p. 116–117 | Solução: S1 2 h, S2 4 h, S3 6 h, S4 16 h, S5 32 h úteis | Serviço | O | A — exige plantão técnico dedicado |
| ED-085 | TR 25.11, p. 117 | 5% da fatura global mensal por severidade descumprida, cumulativo | Comercial/contratual | O | A |
| ED-086 | TR 25.12, p. 117–118 | 99% de disponibilidade em horário comercial; 5% por faixa de 10% abaixo, até 30% | Não funcional | O | M |
| ED-087 | TR 26.1.4, p. 118 | LGPD com consentimento, logs de acesso a dados sensíveis e anonimização nativos | Não funcional | O | M — o Ziip SAN já tem base de LGPD |
| ED-088 | TR 26.1.8 | Atualizações preferencialmente fora do expediente | Serviço | C | B |
| ED-089 | TR 29, p. 124 | Lote único, adjudicação a um só licitante | Comercial/contratual | O | M |
| ED-090 | — (omissão) | Sem cláusula de cessão de direitos (art. 93), código-fonte ou escrow | Comercial/contratual | I | M — risco sobre customizações (A-16) |
| ED-091 | cl. 8, p. 30 | Fiscais: Gabriel Gregolon, Vinicius Bado Pelissaro, Julia Antunes; gestora: Camila Strapazzon | Comercial/contratual | I | B |

### 4.2 ETP

| ID | Seção / página | Texto fiel ou síntese | Categoria | Obrig. | Impacto para SaaS novo |
|---|---|---|---|---|---|
| ETP-001 | 1, p. 1 | VISAN atende água, esgoto e coleta de resíduos sólidos | Técnico-funcional | I | B — resíduos fora do objeto |
| ETP-002 | 1, p. 2 | Sistema atual "software público e livre", sem atualizações, com melhorias por horas técnicas | Serviço | I | M (positivo) — se for o GSAN, o Ziip SAN já tem o mapeamento de migração |
| ETP-003 | 1, p. 2 | Atender LGPD, LRF (Lei Complementar 101/2000), relatórios confiáveis, BI e omnichannel | Não funcional | O | M |
| ETP-004 | 3, p. 2 | Licença de uso por prazo determinado com implantação, conversão, treinamento, suporte, hospedagem e manutenção | Comercial/contratual | O | B — aderente a SaaS |
| ETP-005 | 3, p. 2–3 | "Não poderá ser ofertado Software de Licença Livre ou de Código Aberto [...] certificado de autoria intelectual com registro no [...] INPI de todos os módulos" | Habilitação | C | M — não está no edital; prudente registrar o programa no INPI |
| ETP-006 | 3, p. 3 | Suporte por severidade, criticidade e impacto; treinamento presencial | Serviço | O | M |
| ETP-007 | 3, p. 3 | Qualificação econômico-financeira "restrita" a balanço, DRE e demonstrações de 2 exercícios e certidão de falência | Habilitação | O | M |
| ETP-008 | 3, p. 3–4 | Exigência de atestado de capacidade técnica | Habilitação | O | **A** |
| ETP-009 | 3, p. 4 | "Não é permitida a subcontratação de parcelas de maior relevância" | Comercial/contratual | O | M — mais brando que o TR |
| ETP-010 | 3, p. 4 | Sem cota exclusiva ME/EPP (art. 49, III, LC 123) | Habilitação | I | B |
| ETP-011 | 4, p. 4–5 | 7 itens, 12 meses; sem quantitativos físicos (ligações, usuários, leituristas) | Comercial/contratual | I | M — dificulta dimensionar preço |
| ETP-012 | 5, p. 5–6 | Alternativas: desenvolvimento próprio, licença vitalícia, licença temporária (escolhida) | Comercial/contratual | I | B |
| ETP-013 | 5, p. 6 | VISAN sem profissionais de TI e sem cargo de programador | Serviço | I | M — suporte e operação recaem integralmente na contratada |
| ETP-014 | 6, p. 7–8 | Estimativa R$ 366.365,13 pelo formulário de preços | Comercial/contratual | I | B |
| ETP-015 | 7, p. 8 | Manutenção corretiva, suporte e entrega de atualizações sem prejuízo de desempenho | Serviço | O | B |
| ETP-016 | 8, p. 8–9 | Não parcelamento; lote único | Comercial/contratual | O | M |
| ETP-017 | 9, p. 9 | Resultados: agilidade, confiabilidade, atendimento, menos retrabalho e papel | Não funcional | I | B |
| ETP-018 | 10, p. 9–10 | Sem providências prévias de capacitação para fiscalização | Comercial/contratual | I | B |
| ETP-019 | 11, p. 10 | "Não há contratações correlatas" | Comercial/contratual | I | B — contradiz a integração contábil exigida |
| ETP-020 | 12, p. 10 | Sem impacto ambiental | Não funcional | I | B |
| ETP-021 | 2, p. 2 | PCA 2026, item 40, previsto para novembro, antecipado | Comercial/contratual | I | B |
| ETP-022 | — (omissão) | Sem análise de riscos e sem metas mensuráveis | Comercial/contratual | I | B |

### 4.3 Pesquisa de preços (PP)

| ID | Página | Texto fiel ou síntese | Categoria | Obrig. | Impacto para SaaS novo |
|---|---|---|---|---|---|
| PP-001 | p. 1 | Período 23/01–24/02/2026; média; pesquisa direta com fornecedores (inciso IV) | Comercial/contratual | I | B |
| PP-002 | p. 4 | Painéis de preços descartados por não corresponderem à quantidade de economias da VISAN | Comercial/contratual | I | B |
| PP-003 | p. 2, 5 | Item 1: 25.000 / 127.700 / 81.000; referência 77.900,00 | Comercial/contratual | I | M — implantação é o item de maior dispersão |
| PP-004 | p. 2, 5 | Item 2: 15.000 / 12.750 / 17.150; referência 14.966,66/mês | Comercial/contratual | I | M |
| PP-005 | p. 2, 5 | Item 3: 2.000 / 4.500 / 11.000; referência 5.833,33 | Comercial/contratual | I | B |
| PP-006 | p. 2–3, 5 | Item 4: 3.000 / 2.200 / 1.500; referência 2.233,33/mês | Comercial/contratual | I | M — sem custo variável de mensagens |
| PP-007 | p. 3, 5 | Item 5: 2.500 / 3.200 / 15.000; referência 6.900,00 | Comercial/contratual | I | B |
| PP-008 | p. 3, 5 | Item 6: 900 / 350 / 3.750; referência 1.666,66/mês | Comercial/contratual | I | B |
| PP-009 | p. 3–5 | Item 7: 320 / 220 / 200; referência 246,66/h | Comercial/contratual | I | B |
| PP-010 | p. 6–7 | Strategos (CNPJ 00.175.275/0001-66, Pinhais/PR): total R$ 320.300,00, validade 60 dias | Comercial/contratual | I | B |
| PP-011 | p. 11–19 | Jtech/Sansys, grupo Veolia, > 20 anos, 105 clientes, 150 cidades, 3 milhões de contas | Comercial/contratual | I | M — concorrente catarinense de grande porte |
| PP-012 | p. 12 (imagem) | Clientes Jtech: Iguá, Águas de Joinville, Veolia, Verde Ambiental Alagoas, EMASA, SAMAE Jaraguá do Sul, Conasa, Interagua | Comercial/contratual | I | M |
| PP-013 | p. 31–32 | Jtech: mensalidade proporcional ao número de ligações de água | Comercial/contratual | I | M — referência de modelo de preço |
| PP-014 | p. 34 | Jtech base: R$ 84.200 + R$ 8.500/mês, usuários ilimitados, apps de leitura e OS, portal | Comercial/contratual | I | M |
| PP-015 | p. 36–37 | Jtech adicionais: omnichannel R$ 4.500 + R$ 2.200/mês (3.000 atendimentos); BI R$ 3.200 + R$ 350/usuário/mês; GIS R$ 43.500 + R$ 4.250/mês; laboratório R$ 15.000 + R$ 2.800/mês | Comercial/contratual | I | M |
| PP-016 | p. 38 | Jtech: customização R$ 220/hora | Comercial/contratual | I | B |
| PP-017 | p. 39–40 | Jtech total: R$ 150.400 + R$ 18.100/mês; excedentes R$ 0,65 e R$ 0,85 por atendimento | Comercial/contratual | I | B |
| PP-018 | p. 41 | Jtech: 30% da implantação na contratação, saldo em até 3 vezes | Comercial/contratual | I | B — incompatível com o pagamento do edital |
| PP-019 | p. 42 | Jtech exclui Sansys Pay, Smart Meter, fraude e crítica com IA, Waste | Técnico-funcional | I | B — a PoC pede crítica automatizada e IA no omnichannel |
| PP-020 | p. 45–46 | Waterfy (Limeira/SP): ~20 anos, 163 municípios; clientes Itu, Itabuna, Araçatuba, Veracruz; derivações BRK Limeira, BRK Maceió, Rio+ | Comercial/contratual | I | M |
| PP-021 | p. 47 | Waterfy: total R$ 415.800,00 | Comercial/contratual | I | B |
| PP-022 | p. 48 | Waterfy: omnichannel variável R$ 0,07 / 0,11 / 0,52 por atendimento; valores "meramente indicativos" | Comercial/contratual | I | B |
| PP-023 | p. 10 | Termo de confidencialidade da Jtech, publicado mesmo assim no processo | Comercial/contratual | I | B |
| PP-024 | p. 5 | Referência final por item igual à do edital; total R$ 366.365,13 | Comercial/contratual | I | B |

Totais: ED 91 itens; ETP 22 itens; PP 24 itens.

---

## 5. Barreiras de entrada para o Ziip SAN

Premissas: produto novo, sem contratos e portanto sem atestados; modelo SaaS multi-tenant; pilha Java + PostgreSQL/
PostGIS; segundo a revisão 17 (12/10/2026), sem artefato implantável nem ambiente de produção, motores de faturamento,
medição e arrecadação sem API, e módulos de portal, cobrança, contabilidade, campo e leitura ainda sem implementação.

| # | Barreira | Fonte | Como os fornecedores estabelecidos atendem | Caminhos para o Ziip SAN |
|---|---|---|---|---|
| B1 | Dois atestados em autarquia ou empresa de água, com ≥ 8.500 ligações somadas e implantação, suporte e treinamento | ED-017 a ED-021 | Carteira própria (Jtech, Waterfy citam dezenas de clientes) | Consórcio com empresa que tenha atestado (3.10 admite somatório); primeiro contrato em autarquia menor, por dispensa ou licitação sem essa exigência, para gerar atestado; incorporação de empresa com acervo; impugnação da exigência de número mínimo de atestados sem justificativa técnica |
| B2 | Atestado com relatórios de envio ao e-Sfinge do TCE/SC | ED-019, ED-077 | Fornecedores com clientes catarinenses (Jtech em São José/SC; SAMAE Jaraguá do Sul, Águas de Joinville) | Desenvolver o conector e-Sfinge tributário e obter um cliente em SC; impugnar por restringir a competição a quem já atua no estado |
| B3 | Prova de conceito de 81 itens em 5 dias úteis, ≥ 90%, com apps Android e iOS, impressão Zebra, FEBRABAN, PIX, dívida ativa, contabilidade, omnichannel com IA e BI | ED-055 a ED-060, ED-074 | Produtos maduros com ambiente de demonstração permanente | Montar um roteiro interno de PoC a partir do Anexo III e usá-lo como critério de prontidão do produto; manter um tenant de demonstração com massa de dados |
| B4 | Provedor de nuvem com ISO 9001, 27001, 27017, SOC 1 e SOC 3 e serviços gerenciados (backup, autoescala, banco com PITR, monitoramento de segurança) | ED-063 a ED-066, ED-070 | Hospedagem em AWS, Azure, GCP ou OCI em região brasileira, com carta do provedor ou documentação pública | Definir hospedagem de produção em hiperescalador com região São Paulo; uma VPS comum não comprova SOC 1/SOC 3 e seria eliminada (verificado em 22/10/2026: SOC 1/ISAE 3402 e SOC 3 são relatórios de asseguração emitidos por auditor independente, e não certificações; o SOC 3 pertence à família SOC 2, de critérios de serviços de confiança; vale confirmar com o órgão contratante o que a exigência abrange) |
| B5 | VISAN pode exigir hospedagem própria, com percentual de desconto na mensalidade previsto na proposta | ED-067 | Produtos com versão instalável (muitos nasceram on-premise) | Ter distribuição implantável fora da nuvem (contêineres e scripts) e um percentual pronto para declarar; o Dockerfile e o pacote de implantação da revisão 17 servem também a esse fim |
| B6 | Implantação, migração (inclusive GIS em DWG) e treinamento em 90 dias, com dois especialistas presenciais e técnico por 30 dias pós-implantação | ED-029, ED-044 a ED-046, ED-053, ED-080 | Equipes de implantação regionais e ferramentas de conversão já usadas em clientes anteriores | Ferramenta de migração do GSAN (se o legado for confirmado) e importador DWG → PostGIS; parceiro de implantação em SC |
| B7 | Balanço dos 2 últimos exercícios | ED-016 | Empresas com histórico contábil | Se a empresa for criada no exercício da licitação, balanço de abertura (art. 65, §1º, Lei 14.133); caso contrário, os 2 balanços precisam existir |
| B8 | SLA com solução em 2 h para severidade 1 e multa de 5% da fatura global por severidade descumprida, cumulativa | ED-084 a ED-086 | Centrais de suporte com centenas de chamados por mês (Jtech declara 1.000) | Plantão de suporte e monitoramento proativo antes do primeiro contrato público; precificar o risco das multas |
| B9 | Omnichannel (WhatsApp, Messenger, webchat, IA), BI e SIG no mesmo lote, sem subcontratação | ED-002, ED-024, ED-059, ED-060 | Módulos próprios ou de empresas do mesmo grupo; BI sobre Qlik ou BigQuery (Jtech) | Integrar provedores como componentes licenciados do produto (API do WhatsApp, motor de BI), não como serviço subcontratado; esclarecer o ponto antes da sessão |
| B10 | Preço: referência de R$ 14.966,66/mês para o sistema principal e R$ 77.900 de implantação | ED-004, PP-003 a PP-009 | Cotam entre R$ 12.750 e R$ 17.150 por mês | O preço não é barreira de entrada em si, mas o custo de equipe presencial, hiperescalador e plantão precisa caber nesses valores |
| B11 | Registro no INPI e vedação a software de código aberto (ETP) | ETP-005 | Fornecedores tradicionais têm registro | Registrar o programa de computador no INPI; o produto é proprietário, e o uso de bibliotecas livres não o torna "software de licença livre" |

Leitura geral: para o Ziip SAN, este edital é inacessível sozinho no curto prazo pelas barreiras B1, B2, B3 e B4.
Consórcio com detentor de atestado é a única via imediata prevista no próprio edital; as demais dependem de amadurecer o
produto e conquistar o primeiro cliente catarinense.

---

## 6. Pontos ambíguos

| ID | Ponto | Fonte | Por que importa |
|---|---|---|---|
| A-01 | Balanço de 2 exercícios sem índices ou critério de avaliação | 7.2.3.2 | Não se sabe o que reprova o licitante; leitura literal é só apresentar os documentos |
| A-02 | "8.500 ligações [...] em seu somatório": soma entre os 2 atestados ou dentro de cada um? "água e/ou esgoto" somados? | 7.2.4.1 c | Define se dois clientes pequenos bastam |
| A-03 | Regime de execução não declarado; minuta fala em "preço total e global" e "valor unitário" | cl. 2.1 | Afeta medição e aditivos |
| A-04 | O atestado deve conter relatórios do e-Sfinge; clientes fora de SC não teriam como cumprir | 7.2.4.1 b | Possível restrição indevida à competição |
| A-05 | Subcontratação: TR veda toda; ETP só as parcelas relevantes; edital silencia. A própria nuvem de terceiro é exigida | TR 27.4; ETP p. 4 | Omnichannel, BI e hospedagem de terceiros podem ser questionados |
| A-06 | Prazo de implantação contado da "Ordem de Compra" (edital) ou da "ordem de serviço" (TR) | 11.1; TR 3.1.1 | Marco inicial dos 90 dias |
| A-07 | "Revogação do contrato" por atraso | 11.3 | Termo impróprio; a Lei 14.133 trata de extinção |
| A-08 | PoC "após a adjudicação", feita pela "empresa vencedora", em 5 dias úteis, de forma a ser definida pela comissão | TR 3.2.1; Anexo III p. 144 | Prazo de preparo do ambiente e forma de demonstração indefinidos |
| A-09 | PoC cita painéis do BI nos itens 22.5.12 a 22.5.18; no TR eles estão em 22.6.12 a 22.6.18 | Anexo III t; TR 22.6 | Referência cruzada errada |
| A-10 | Dois indicadores de disponibilidade com escalas diferentes: 95% mensal na nuvem (4.5) e 99% em horário comercial (25.12); a escala do 4.5 seria "definida no contrato", mas a minuta não a define | TR 4.5; 25.12 | Risco de dupla penalização |
| A-11 | Início da cobrança mensal: desde a Ordem de Compra ou após a implantação? Vigência de 12 meses com 90 dias de implantação | 11.1; itens 2, 4, 6 | Receita efetiva pode ser de 9 meses |
| A-12 | Prazos de severidade em horas úteis das 08h às 17h; suporte das 07h30 às 17h30 | TR 25.7; 25.10 | Base de contagem |
| A-13 | Item 15.10 menciona perda de "garantia de proposta", que não é exigida | 15.10 | Cláusula de modelo sem efeito |
| A-14 | TR 32 fala em "ata de registro de preços"; o certame não é SRP | TR 32 | Texto de modelo |
| A-15 | ETP exige registro no INPI e veda código aberto; o edital não traz essas exigências | ETP p. 2–3 | Pode ser cobrado em diligência; prevalece o edital (17.7) |
| A-16 | Titularidade das customizações (item 7 e os 10% da implantação) diante do art. 93 da Lei 14.133; edital omisso sobre cessão, código-fonte e escrow | 1.1; TR 4.19; item 7 | Risco de a Administração reivindicar direitos sobre partes do produto |
| A-17 | Percentual de desconto para hospedagem local: onde e como declarar na proposta do portal, que é por item | TR 4.4.12 | Pode gerar desclassificação por omissão |
| A-18 | Certificações do provedor: momento de apresentação ("na data de assinatura do contrato", 4.4.16) versus eliminação "do certame" (4.4.18); itens 4.4.14.1.3 e 4.4.14.1.4 misturam ISO 27017 com SOC 1 e SOC 3, que são relatórios de asseguração de auditor independente, e não certificações (verificado em 22/10/2026) | TR 4.4.14–4.4.18 | Não se sabe se é requisito de habilitação, de PoC ou de contrato |
| A-19 | Volume do omnichannel e número de usuários de BI não informados; cotações têm custo variável e preço por usuário | itens 4 e 6; PP-015, PP-022 | Preço fixo com custo variável fica com a contratada (cl. 4.19) |
| A-20 | Quantitativos físicos ausentes (ligações, economias, usuários, leituristas, coletores, impressoras) | ETP 4 | Dimensionamento só por inferência (≈ 17.000 ligações) |
| A-21 | Quem fornece coletores, smartphones, impressoras térmicas e totens não está dito | TR 8, 16, 17, 20 | Custo de hardware pode recair na contratada |
| A-22 | Acesso administrativo da TI da VISAN ao ambiente de nuvem "sempre que possível" | TR 4.15 | Incompatível com ambiente multi-tenant compartilhado |
| A-23 | Declaração de quem não fez visita técnica: quando e onde apresentar (não consta da lista do item 7) | TR 26.2 | Risco de inabilitação por falta de documento |
| A-24 | TR 27.1 remete a Declaração Conjunta ao "Anexo III"; no edital ela é o Anexo V | TR 27.1 | Remissão errada |
| A-25 | SIG integra o objeto e o item 1, mas fica fora do escopo da PoC (5.1 a 23.1) | TR 3.2.8 | SIG não é testado antes da contratação |
| A-26 | ETP cita coleta de resíduos sólidos como atividade da VISAN; o objeto não inclui resíduos | ETP p. 1 | Possível demanda futura fora do contrato |
| A-27 | Identidade do sistema atual ("software público e livre") não informada | ETP p. 2 | Esforço de migração não estimável sem visita técnica |
