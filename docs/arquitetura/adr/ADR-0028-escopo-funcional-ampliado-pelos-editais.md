# ADR-0028 — Escopo funcional ampliado pelos editais

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0001 (P4 e 2028: qualidade da água e R9); ADR-0002 (aceite formal); ADR-0004 (modo assistido);
  ADR-0010 (exceções: ODA File Converter e ingestão de telemetria); ADR-0011 (hospedagem); ADR-0015 (corte e tarifa
  social); ADR-0020 (item 5, e-mail sem conteúdo); ADR-0022 (portal; documentos por e-mail); ADR-0023 (trilha de
  editais); ADR-0026 (apps); ADR-0027 (relatórios e BI); revisão 17 §7 (congelamento de SCADA/MQTT); plano P3.5, P4.1,
  P4.2, P4.4, P4.5; RNF-44
- **Base:** `docs/levantamento/editais/18d-aderencia-cadastro-medicao-gis.md` §3 e §4; `18e-aderencia-faturamento-arrecadacao-cobranca.md`
  §3 e §4; `18f-aderencia-atendimento-os-portal.md` §3 e §4; `18g-aderencia-operacao-qualidade-bi.md` §3 e §4

## Contexto

As matrizes de aderência (`18d` a `18g`) mostraram que, além dos apps (ADR-0026) e do BI (ADR-0027), os editais VISAN
Videira PE 06/2026 e SAMAE São Ludgero PE 2/2026 cobram na POC, como itens críticos ou pontuados, módulos que o plano
deixa para 2028, sob demanda, ou não prevê: qualidade da água e laboratório (SAMAE 2.26, crítica), GIS e migração do
cadastro técnico em DWG (SAMAE 2.27 e implantação 3.11.10; Videira nos 90 dias de implantação), omnichannel com
chatbot (SAMAE 2.17 a 2.24; Videira alínea s), dívida ativa (Videira alínea k; SAMAE 2.15.3.19), contabilidade e
e-Sfinge (Videira alínea l e atestado ED-019; SAMAE 2.16), débito automático (Videira alínea h; SG-327), negativação e
protesto (SG-402 a SG-411), cartão no parcelamento (TR-374), indicadores de ERP (TR-1001 a TR-1007; SG-1179 a SG-1189),
telemetria e supervisório (TR-210; SG-810, crítico), telefonia, totem e filas (SAMAE 2.25; TR-836, SG-614 a SG-626) e
envio da fatura ao e-mail do usuário (TR-570, SG-546, POC-20).

O ADR-0023 abriu a trilha de editais no P4, mirando licitações de 2028, sem mexer no go-live do piloto. Este ADR fixa o
escopo e a forma de cada módulo dessa trilha.

## Decisão

Salvo indicação contrária, cada item entra na **trilha de editais do P4** (ADR-0023), sequenciada pelo plano conforme
a criticidade na POC.

### a) Qualidade da água

1. Antecipada de 2028 para o P4, aproveitando o modelo e os testes de `sigsan-quality` (amostra, resultado,
   instrumento, controle de qualidade).
2. Escopo: catálogo normativo versionado (Portaria GM/MS 888/2021 e alterações), plano de amostragem com mínimos, rotas
   e cronograma, cadeia de custódia, recepção por código de barras, resultados com versão, reanálise e recoleta, motor
   de conformidade e fechamento mensal, bloco de qualidade na conta (Decreto 5.440/2005), publicação no portal e
   comunicação de não conformidade.
3. **SISAGUA** em modo assistido, como o ADR-0004: o produto gera o arquivo no formato oficial vigente e o cliente envia.
4. **Apoio à ISO/IEC 17025** (laboratório com escopo acreditado, calibração com histórico e bloqueio, regras de
   Westgard, incerteza e regra de decisão, validação por segunda pessoa) entregue em até 6 meses da implantação do
   cliente que exigir (SG-822). O software apoia; a acreditação é do laboratório.
5. O catálogo normativo é revisado por responsável técnico antes do uso (o modelo atual diverge da Portaria em 16 de 28
   linhas, `18g` §1.5, item 3).

### b) GIS web, cadastro técnico e manobras

1. **GIS web próprio** sobre MapLibre (frontend) e PostGIS (ADR-0013): camadas, mapas base, pesquisas, seleção,
   simbologia, temáticos (consumo, situação, OS, hidrômetros, inadimplência), importação de shapefile e GeoPackage e OS
   no mapa. Tiles vetoriais gerados pelo PostGIS e servidos pela `api`, sem servidor de mapas novo; GeoServer e QGIS
   Server continuam congelados (ADR-0010).
2. **QGIS conectado direto ao banco como editor avançado**, com permissão por tenant:
   - role de login individual por usuário do QGIS, mapeada ao tenant por tabela que só o `migrate` escreve, com a
     restrição de linhas feita pela identidade da role, nunca pela GUC de sessão (mesmo fundamento do ADR-0027);
   - permissão de edição só nas tabelas e camadas autorizadas pelo administrador do tenant, com `WITH CHECK` nas
     políticas; leitura das demais;
   - auditoria das edições por gatilho no banco, já que a alteração não passa pela `api`;
   - acesso por VPN ou por endpoint restrito ao IP de origem do cliente, com TLS e verificação do certificado do
     servidor; o banco nunca fica aberto à internet; credencial de curta duração quando possível (autenticação IAM do
     RDS) ou senha rotacionada.
3. **Migração do cadastro técnico:** DWG convertido para DXF com o **ODA File Converter** (gratuito, licença
   proprietária) numa **etapa assistida da implantação**, fora da imagem de produção; o importador do produto lê só
   formatos abertos (DXF, SHP, GPKG, INP) com GDAL no `worker`, com diagnóstico topológico, reprojeção para SIRGAS 2000
   e casamento de unidades por matrícula ou endereço. O ODA File Converter é registrado no ADR-0010 como ferramenta de
   implantação; a licença de uso comercial é conferida antes do primeiro uso.
4. **Manobras** sobre o modelo de rede existente (`gis_network_element`, `NetworkTopologyBuilder`, escritor EPANET):
   isolamento de trecho pelas válvulas, consumidores afetados e OS em lote.
5. **Revoga "R9 em 2028" para estes itens** (GIS web, edição, migração do cadastro técnico e manobras), que passam ao P4.
   Os demais itens do R9 (perdas com entrada real e o que não está listado aqui) continuam em 2028.

### c) Omnichannel híbrido

1. No produto: **tela de atendimento, filas, protocolo** (ligado à P3.4 e ao R7) e **conectores oficiais**: WhatsApp
   Business Platform (API oficial da Meta), e-mail e chat do portal. Toda conversa fica gravada no protocolo.
2. **Chatbot com modelo de linguagem de terceiro por API**, com orquestração própria:
   - o modelo só chama **funções do portal** (2ª via, situação da conta, abertura de solicitação e afins), executadas
     com as permissões do consumidor autenticado; o bot nunca tem acesso maior que o do consumidor, e não há consulta
     livre ao banco;
   - **minimização de dado pessoal**: o texto enviado ao modelo não leva CPF, CNPJ, nome nem endereço quando não
     necessários; identificadores internos substituem os dados; o provedor não pode usar o conteúdo para treino
     (cláusula contratual);
   - transbordo para atendente humano a qualquer momento;
   - provedores (Meta, modelo de linguagem) como suboperadores no DPA. Provedor com processamento no Brasil é preferido;
     se o processamento for no exterior, a transferência internacional é registrada no RIPD e no DPA (LGPD, art. 33) e
     como exceção ao RNF-44.

### d) Dívida ativa

Núcleo no produto: notificação com entrega registrada, inscrição (individual e periódica), livro de dívida ativa,
**CDA**, parcelamento próprio da dívida ativa, saldo mensal congelado e relatórios (Lei 6.830/1980; Lei 4.320/1964,
art. 39). A **execução fiscal** sai como certidão, petição em modelo do órgão e arquivo para a procuradoria ou o
sistema tributário do município; o produto não conduz o processo judicial.

### e) Contabilidade e e-Sfinge

1. **Eventos contábeis gerados por fato** (faturamento, arrecadação, cancelamento, PDD, retenções, inscrição em dívida
   ativa), com plano de contas e regras evento → lançamento parametrizados por tenant, fechamento congelado e
   **exportação diária** ao sistema contábil do cliente. O produto não é o sistema contábil oficial do órgão.
2. **Remessa e-Sfinge (TCE/SC)** gerada e validada pelo produto, com as regras de consistência do leiaute, e **enviada
   pelo cliente** (modo assistido, como o SINISA no ADR-0004), com registro de hash, versão do leiaute e retorno.

### f) Débito em conta

**Pix Automático** para novas autorizações (Res. CMN 5.251/2025; plano P4.5) e **FEBRABAN 150** (registros B, C, E e
F) para as autorizações legadas migradas, com data de desligamento conforme a regra vigente do Banco Central (Res. CMN
5.299/2026, a conferir).

### g) Negativação e protesto

Antecipados de 2028 para o P4, **primeiro por arquivo**: remessa gerada pelo produto e enviada pelo cliente ao birô
(SPC, Serasa) e à central de protesto, com importação do retorno. Antes da inclusão, **carta de aviso prévio** ao
devedor (CDC, art. 43, §2º), com entrega registrada; baixa no prazo legal; teto de permanência já previsto em
`ret.negative_listing_max_years`. Integração por API com birô e com a central dos cartórios (CENPROT) vem depois, sob
demanda.

### h) Cartão no parcelamento

**Adaptador para a credenciadora contratada pelo cliente** (Cielo, Rede, Stone, PagSeguro), por link de pagamento ou
checkout hospedado pela credenciadora, configurado como parâmetro do tenant; **conciliação automática** pelo retorno e
pelo extrato da credenciadora. O produto **nunca recebe nem armazena dados de cartão**, o que mantém o escopo PCI DSS
mínimo.

### i) ERP: almoxarifado, compras, RH, frota e orçamento

1. **Construir no produto** os módulos de almoxarifado, compras, RH, frota e orçamento **no recorte ligado ao
   saneamento**: materiais e veículos consumidos na OS, estoque de hidrômetros e materiais de rede, equipes de campo,
   custos operacionais e indicadores do painel estratégico.
2. **Integrar** via API e/ou arquivo com os ERPs públicos **Betha, IPM e Fiorilli**, para o cliente que mantiver o ERP
   como sistema oficial.

### j) Telemetria e supervisório

1. O **congelamento de MQTT e SCADA continua até o go-live** (revisão 17 §7; ADR-0010).
2. Na trilha do P4: **telemetria por API REST e importação de arquivo** (grandes consumidores, AMR, supervisório), em
   tabela particionada sem auditoria por linha, com **alarmes** persistidos e **registro manual de paradas** da ETA. A
   ingestão é registrada no ADR-0010 para deixar explícito que não há broker.
3. **MQTT ou OPC** só com ADR de exceção e cliente pagante.

### k) Telefonia, totem e filas

1. **Integração mínima com o PABX do cliente:** CTI em que a ligação recebida abre a ficha do consumidor pelo número
   chamador, com registro de acesso; campanhas de telemarketing como **listas de discagem** geradas pelo produto e
   executadas na telefonia do cliente. Contact center próprio não entra.
2. **Totem** = portal em modo quiosque, no **hardware do cliente**. **Módulo de senhas** com prioridade legal (Lei
   10.048/2000), chamada na tela de atendimento e **painel de chamada** em navegador, no produto.

### l) Fatura por e-mail

1. **Padrão:** e-mail com aviso (vencimento, valor) e **link autenticado ao portal**; o documento é baixado no portal,
   com o controle de acesso e o registro do ADR-0022.
2. **Anexo PDF opcional** por **opt-in registrado do morador** (data, canal, versão do texto, revogação a qualquer
   momento), para e-mail verificado.
3. O **tenant pode tornar o anexo o padrão**, com aceite formal registrado (ADR-0002); nesse caso o morador pode
   recusar o anexo. O ajuste correspondente está registrado no ADR-0022.

## Consequências

### Positivas

- O produto passa a cobrir as seções críticas das duas POCs que o plano deixava para 2028 ou não previa.
- As formas escolhidas evitam componente novo onde o PostgreSQL e o `worker` bastam (tiles, telemetria) e evitam
  responsabilidade que não é do Ziip: envio ao TCE, ao SISAGUA, ao birô e ao cartório pelo cliente; execução fiscal
  pela procuradoria; dados de cartão na credenciadora; hardware de totem com o cliente.
- O modo assistido, já usado no ADR-0004, se estende sem guarda de credencial de terceiros.

### Negativas

- **Volume:** só a área de operação, qualidade e BI soma de 13 a 16 meses-pessoa (`18g` §3), sem GIS operacional;
  somadas as demais áreas, a trilha do P4 excede a capacidade de uma pessoa com agentes no período de agosto a dezembro
  de 2027. O plano precisa sequenciar pela criticidade na POC, e parte da trilha pode avançar sobre 2028.
- **ERP (item i):** a análise recomendava só integração, porque os editais analisados pedem integração e indicadores,
  não módulos de ERP (`18g` §4, D3). Construir os módulos amplia o escopo além do exigido; o recorte "ligado ao
  saneamento" precisa ser definido antes de começar. O módulo de RH trata dados de empregados do cliente, nova
  categoria a incluir no RIPD e no DPA; orçamento público segue a Lei 4.320/1964.
- **QGIS direto:** abre um caminho de escrita no banco que não passa pela `api`; auditoria por gatilho, roles
  individuais e acesso restrito por rede passam a ser obrigatórios, e o pentest precisa cobri-lo.
- **Chatbot:** provedor de modelo de linguagem como suboperador, possivelmente fora do Brasil; risco de resposta errada
  ao consumidor, mitigado pelas funções fechadas e pelo transbordo.
- **Fatura com anexo:** PDF com dado pessoal trafega por e-mail sem controle de acesso; endereço errado ou caixa
  comprometida expõem o consumidor. O opt-in desloca a escolha ao titular, e o padrão continua sendo o link.
- **ODA File Converter:** ferramenta proprietária na esteira de implantação; mudança de licença ou de disponibilidade
  obriga a rever a etapa.
- **Débito em conta:** se o cliente piloto tiver optantes de débito automático, o FEBRABAN 150 legado é necessário já
  no go-live, antes da trilha do P4; isso precisa ser verificado no discovery do legado (P1).
- Custos de terceiros por uso (mensagens do WhatsApp, modelo de linguagem, birô, protesto) precisam de regra de
  repasse ou de franquia no contrato.

### O que muda no código

- `sigsan-quality` exposto por API e telas; motor de conformidade; exportador SISAGUA versionado.
- API de `gis_network_element`, tiles vetoriais pelo PostGIS, importador DXF/SHP/GPKG/INP no `worker` com GDAL; roles
  de edição por usuário do QGIS e gatilhos de auditoria; manobras sobre a topologia existente.
- Módulo de atendimento omnichannel (filas, conversa no protocolo, conectores) e orquestrador do chatbot com funções do
  portal.
- Dívida ativa, eventos contábeis e exportação, exportador e-Sfinge, remessas de débito, negativação e protesto,
  adaptador de credenciadora, módulos de ERP no recorte definido e conectores Betha, IPM e Fiorilli.
- Endpoint e importador de telemetria, alarmes e registro de paradas.
- CTI por número chamador, listas de discagem, senhas, painel de chamada e modo quiosque do portal.
- Preferência de entrega da fatura por consumidor (link ou anexo), com registro do opt-in e parâmetro do tenant.

### O que muda no roadmap

- ADR-0001: qualidade da água sai de 2028 para o P4; GIS web, edição, migração do cadastro técnico e manobras saem do
  R9 de 2028 para o P4; SPC/Serasa sai de 2028 para o P4; o restante do R9 e a trilha GRS continuam em 2028.
- Débito em conta passa a ter duas entregas (Pix Automático da P4.5 e FEBRABAN 150 legado).

## Alternativas consideradas

- **Qualidade da água em 2028 ou LIMS de mercado.** Rejeitadas: a seção 2.26 do SAMAE é crítica; o LIMS custa por mês
  (R$ 2.800 na proposta concorrente, `18b` PP-015) e conflita com a autoria própria.
- **GIS em 2028 ou GIS de terceiro.** Rejeitadas: exclui editais com GIS; GIS de terceiro é vedado em Videira e quebra a
  base única exigida pelo SAMAE.
- **Licenciar o SDK da ODA ou exigir DXF do cliente.** Rejeitadas: custo anual e dependência binária na imagem; exigir
  DXF contraria o texto dos editais.
- **Omnichannel inteiramente próprio, inclusive IA, ou plataforma de mercado.** Rejeitadas: prazo longo, no primeiro
  caso; conversa fora do produto e risco de subcontratação em Videira, no segundo.
- **Módulo completo de dívida ativa com execução fiscal, ou dívida ativa fora do produto.** Rejeitadas: assumir o
  processo judicial não é necessário para as POCs; deixar de fora reprova nas duas.
- **Contabilidade pública completa com envio direto ao TCE.** Rejeitada: esforço muito maior e responsabilidade pela
  declaração.
- **Só Pix Automático ou só FEBRABAN 150.** Rejeitadas: o primeiro reprova na POC de Videira; o segundo contraria a
  regra vigente para autorizações novas.
- **Negativação em 2028.** Rejeitada: o SAMAE a trata como crítica.
- **Cartão por maquininha/TEF no balcão, com conciliação por arquivo.** Não adotada: o TR-374 pede integração on-line
  com a operadora, que o adaptador do item h atende.
- **ERP só por integração (recomendação de `18g` D3) ou por parceria.** Rejeitadas pelo dono do produto em favor de
  construir no recorte do saneamento e integrar.
- **Reativar MQTT agora.** Rejeitada: infraestrutura e plantão antes de haver cliente.
- **Contact center próprio ou sistema de filas de terceiros.** Rejeitadas: fora do foco e caro de operar; filas de
  terceiros põem outra tela para o atendente (contraria SG-626).
- **Fatura sempre com anexo, ou nunca por e-mail.** Rejeitadas: a primeira expõe dado pessoal sem escolha do titular;
  a segunda contraria TR-570, SG-546 e POC-20.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Verificar no discovery do legado se o piloto tem optantes de débito automático (FEBRABAN 150 antes do go-live) | P1 |
| Conferir a licença de uso comercial do ODA File Converter | P1 |
| Definir o recorte "ligado ao saneamento" dos módulos de ERP (item i) | P3 |
| Qualidade da água operacional e exportador SISAGUA assistido; revisão do catálogo por responsável técnico | P4 (trilha de editais) |
| Apoio à ISO/IEC 17025 | Até 6 meses após a implantação do cliente que exigir |
| GIS web com tiles pelo PostGIS; acesso QGIS com roles individuais, auditoria por gatilho e acesso restrito por rede | P4 (trilha de editais) |
| Etapa assistida DWG → DXF e importador de formatos abertos no `worker` | P4 (trilha de editais) |
| Manobras sobre o modelo de rede existente | P4 (trilha de editais) |
| Omnichannel (tela, filas, protocolo, WhatsApp oficial, e-mail, chat do portal) | P4 (trilha de editais) |
| Chatbot com modelo de linguagem por API, funções do portal, minimização e registro no RIPD e no DPA | P4 (trilha de editais) |
| Dívida ativa (núcleo, CDA, parcelamento, saldo mensal, arquivo para a procuradoria) | P4 (trilha de editais) |
| Eventos contábeis e exportação diária; remessa e-Sfinge assistida | P4 (trilha de editais) |
| FEBRABAN 150 para autorizações legadas, junto do Pix Automático (P4.5) | P4 (antes, se o piloto exigir) |
| Negativação e protesto por arquivo, com carta de aviso prévio | P4 (trilha de editais) |
| Integração por API com birô e com a CENPROT | 2028, sob demanda |
| Adaptador de credenciadora com conciliação automática, sem dado de cartão | P4 (trilha de editais) |
| Módulos de ERP no recorte definido e conectores Betha, IPM e Fiorilli | P4 (trilha de editais) |
| Telemetria por REST e arquivo, alarmes e registro manual de paradas | P4 (trilha de editais) |
| CTI mínimo e listas de discagem; totem em modo quiosque, senhas e painel de chamada | P4 (trilha de editais) |
| Fatura por e-mail com link autenticado; anexo PDF por opt-in; parâmetro do tenant com aceite | P4 (trilha de editais) |
