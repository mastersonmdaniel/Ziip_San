### 2.2 Decisões de 12/10/2026

| ADR | Decisão |
|---|---|
| [0001](arquitetura/adr/ADR-0001-roadmap-p0-p4.md) | P0–P4 com marco do piloto; piloto escolhido no P0; o marco passa a homologação local do piloto e entra a fase final de lançamento comercial depois do P4 (ajuste v2.7); lançamento a partir de outubro de 2028, pela opção A do §2.4 (ajuste v2.8) |
| [0002](arquitetura/adr/ADR-0002-decisoes-g-como-parametros-do-tenant.md) | Parâmetros do tenant com vigência, sugerido, limite e aceite; contrato, DPA e termos no P3 |
| [0003](arquitetura/adr/ADR-0003-motor-fiscal-portado-para-java.md) | Motor fiscal C# portado para Java; NFAg-e (modelo 75) e NFS-e avulsa sobre ele |
| [0004](arquitetura/adr/ADR-0004-sinisa-e-sinir-assistidos.md) | SINISA e SINIR assistidos: arquivo oficial, o cliente envia |
| [0005](arquitetura/adr/ADR-0005-isolamento-hibrido-por-porte.md) | Isolamento híbrido por porte; shard no catálogo; teste com 300 schemas |
| [0006](arquitetura/adr/ADR-0006-bff-com-cookie-httponly.md) | BFF com cookie HttpOnly, no processo `api`, sessão no PostgreSQL; vale para o navegador, e os apps seguem o ADR-0030 (ajuste v2.5) |
| [0007](arquitetura/adr/ADR-0007-mfa-configuravel-por-tenant.md) | MFA configurável por tenant; plataforma sempre com MFA. Prevalece sobre o 17:S4 (MFA obrigatório por papel) |
| [0008](arquitetura/adr/ADR-0008-mascara-de-documento-configuravel.md) | Máscara configurável com teto; busca por documento com papel, registro e cota |
| [0009](arquitetura/adr/ADR-0009-push-automatico-e-dev-fora-do-drive.md) | Push automático; desenvolvimento em disco local + GitHub |
| [0010](arquitetura/adr/ADR-0010-infra-nao-usada-congelada.md) | Infra não usada em perfis em 127.0.0.1; k8s e Terraform em `infra/_arquivado/`; exceções pgRouting e endpoint de banco do QGIS, sem servidor de mapas (ajuste v2.10); GeoServer para o WMS servidor da onda B, só leitura, com workspace e acesso ao banco por tenant, atrás da `api` (ajuste v2.12) |
| [0011](arquitetura/adr/ADR-0011-hospedagem-a-decidir.md) | Hospedagem: **aceito em 12/10/2026** (v2.4): AWS `sa-east-1` gerenciada (RDS PostgreSQL 17 Multi-AZ com PITR e réplica de leitura, KMS, Secrets Manager, S3 com Object Lock); nenhum dado, réplica ou backup fora da região; certificações exigidas em edital conferidas no AWS Artifact; código continua neutro; desbloqueia R1, backup, PITR e IaC (P0.26–P0.29); infraestrutura na AWS só no lançamento comercial, com cofre e backup locais até lá (ajuste v2.7, ADR-0033) |
| [0012](arquitetura/adr/ADR-0012-metas-nao-funcionais.md) | Metas não funcionais do §6 da revisão 17 |
| [0013](arquitetura/adr/ADR-0013-postgresql-17-no-p0.md) | PostgreSQL 17 + PostGIS 3.5 no P0, antes de dado real, em todo o ambiente local (ajuste v2.7) |
| [0014](arquitetura/adr/ADR-0014-outbox-no-postgresql.md) | Outbox no PostgreSQL com workers `SKIP LOCKED`, atrás de interface |
| [0015](arquitetura/adr/ADR-0015-limites-legais-dos-parametros.md) | Classes de parâmetro (a–d); multa padrão 2% com aceite reforçado acima; vedações de corte por inadimplência travadas; piso da tarifa social; pisos de retenção |
| [0016](arquitetura/adr/ADR-0016-rebrand-e-renomeacao-ziipsan.md) | Pacote de marca único; marca do tenant; renomeação `sigsan` → `ziipsan` no P0, com contexto HKDF congelado e códigos de problema da API em inglês (`urn:ziipsan:problem:`) |
| [0017](arquitetura/adr/ADR-0017-temas-claro-escuro-azul-e-verde-agua.md) | Temas claro, escuro e do sistema; padrões Azul Água e Verde Água; preferência no servidor; contraste no CI |
| [0018](arquitetura/adr/ADR-0018-central-ziip-propria.md) | Central Ziip própria com o console; papéis de plataforma com MFA; sem leitura de dado pessoal de tenant; API de métricas `v1` |
| [0019](arquitetura/adr/ADR-0019-cobranca-saas-por-contrato.md) | Cobrança do SaaS por contrato; nenhum bloqueio automático; restrição só manual |
| [0020](arquitetura/adr/ADR-0020-ajuda-e-suporte-com-protocolo.md) | Ajuda e suporte com protocolo e SLA por contrato; ClamAV; acesso assistido com consentimento no lugar da impersonação; acesso de emergência só em incidente de segurança declarado |
| [0021](arquitetura/adr/ADR-0021-sobre-versao-e-changelog.md) | SemVer gerado no build; changelog do cliente obrigatório para segurança, legislação e parâmetro regulatório; Sobre |
| [0022](arquitetura/adr/ADR-0022-portais-historico-e-documentos.md) | Portal do consumidor com 120 meses, conta paga, DANFAG e XML original; conta do consumidor no realm `ziipsan-portal`, com login por CPF/CNPJ ou e-mail e senha, sem SMS; vínculo por código impresso na conta e CPF/CNPJ; documentos anteriores ao go-live importados; área do tenant com faturas e NFS-e do contrato |
| [0023](arquitetura/adr/ADR-0023-estrategia-de-editais-e-prova-de-conceito.md) | Trilha de editais no P4, mirando licitações de 2028, sem mexer no go-live; POC passa a ser escopo, com prontidão de POC no aceite de fase a partir do P3, ambiente de demonstração permanente (≥ 10 mil unidades fictícias), roteiros internos VISAN e SAMAE e ensaio por fase; atestados pelo piloto público em SC (dispensa ou LC 182/2021, com cláusula de atestado) e por cliente privado; implantação e treinamento remotos no piloto; itens ilegais de edital: segue-se a lei, com pedido de esclarecimento ou impugnação; revoga "POC, editais" do diário §1; primeiro atestado só depois do lançamento comercial e da primeira operação real (ajuste v2.7); com a opção A do lançamento, primeiro atestado entre maio e julho de 2029 e licitações com POC a partir do 3º trimestre de 2029, no lugar de 2028 (ajuste v2.8); referências a equipe sem efeito (ajuste v2.12) |
| [0024](arquitetura/adr/ADR-0024-instancia-dedicada-e-clausula-de-reversao.md) | Padrão SaaS multi-tenant; instância dedicada gerenciada para quem exigir em contrato; execução na infraestrutura do órgão só como cláusula de reversão (pacote em contêiner, requisitos mínimos, runbook, desconto), em Linux ou em Windows Server 2022+ com Docker no WSL2, homologado uma vez (ajuste v2.5); SaaS em Linux na AWS; acesso administrativo do órgão só na instância dedicada; IaC e instância dedicada a partir do lançamento, pacote de reversão sobre o ambiente local (ajuste v2.7) |
| [0025](arquitetura/adr/ADR-0025-nivel-de-servico-setor-publico-contratavel.md) | Nível de serviço "Setor Público" contratável: 99,9%, cinco severidades com prazo de solução, aderência e multa, exportação em 10 dias úteis com dicionário, consulta somente leitura por até 120 dias após o contrato, relatórios e arquivos em 2 ou 5 min (RNF-71); padrão do produto inalterado, com as severidades S1–S5 também no padrão (ajuste v2.5); escala de plantão da S1 montada até 31/01/2028 (ED-05); contratável e acionado só a partir do lançamento (ajuste v2.7) |
| [0026](arquitetura/adr/ADR-0026-apps-hibridos-com-capacitor.md) | Apps híbridos com Capacitor: app de campo só Android, por APK (leitura com emissão simultânea, OS, impressão Zebra série ZQ com editor de leiaute ZPL/CPCL); app do cliente Android e iOS nas lojas com a marca do tenant; a P4.4 passa a app de campo Android; autenticação no ADR-0030 e emissão em campo no ADR-0031 (ajuste v2.5) |
| [0027](arquitetura/adr/ADR-0027-bi-hibrido-com-apache-superset.md) | Relatórios e painéis padrão nativos, com cerca de 20 relatórios operacionais já no P3; Apache Superset embarcado na trilha do P4, na réplica de leitura, com SSO e isolamento por role de banco por tenant; exceção registrada no ADR-0010 |
| [0028](arquitetura/adr/ADR-0028-escopo-funcional-ampliado-pelos-editais.md) | Escopo da trilha: qualidade da água no P4 com apoio à ISO 17025 e SISAGUA assistido; GIS web próprio, QGIS com role individual e DWG convertido pelo ODA File Converter (revoga o R9 de 2028 para esses itens e para manobras); omnichannel híbrido com chatbot por modelo de linguagem de terceiro, com conectores WhatsApp, Messenger, Instagram e Telegram (ajuste v2.5); dívida ativa (núcleo); eventos contábeis e e-Sfinge assistido; Pix Automático e FEBRABAN 150 legado, este no P3 se o piloto tiver optantes (ajuste v2.5); negativação e protesto por arquivo; cartão pela credenciadora do cliente; ERP no recorte "núcleo saneamento + integração": almoxarifado e frota construídos, integração com Betha, IPM e Fiorilli, compras, RH e orçamento em 2029 sob demanda (ajuste v2.5); telemetria por REST e arquivo (MQTT só sob demanda); CTI mínimo e totem em modo quiosque; fatura por e-mail com link seguro e PDF por opt-in; item b lido pelo ADR-0034 (ajuste v2.10): núcleo do GIS no P1–P3, QGIS por views que gravam no pacote, manobras sobre a topologia persistida; GeoServer descongelado só para o WMS servidor da onda B (ajuste GIS, v2.12) |
| [0029](arquitetura/adr/ADR-0029-capacidade-e-equipe-da-trilha-de-editais.md) | **Revogado em 12/10/2026 (v2.12):** gestão de equipe fora do escopo do plano, por decisão do dono; texto mantido como histórico. Número de pessoas, contratações, perfis, onboarding, gatilho de velocidade e análises de capacidade não valem mais; datas de fases e marcos ficam como metas de calendário |
| [0033](arquitetura/adr/ADR-0033-desenvolvimento-local-e-aws-no-lancamento.md) | Desenvolvimento local em Docker Compose (PostgreSQL 17, Keycloak, emulador de S3, cofre local) até concluir as fases; AWS `sa-east-1` só no lançamento comercial, depois do P4; código pronto para a AWS por interfaces (objetos, chaves, segredos, fila, e-mail) com teste de contrato; go-live do piloto vira homologação local com dados sintéticos ou anonimizados na origem; nenhum dado pessoal real na base local; produção real, emissão fiscal em produção e atestado só no lançamento; lançamento pela opção A, depois de todas as fases, a partir de outubro de 2028; certificado da homologação fiscal local: e-CNPJ da própria Ziip/TGP no cofre local, fora do banco, com acesso registrado, como exceção explícita ao item 5 (ajuste v2.8); XML assinados na homologação fiscal local na mesma exceção e no mesmo consentimento, em área separada da base local, apagados ao fim de cada ciclo de testes, com só os vetores de referência sem assinatura guardados (ajuste v2.9); rede do piloto na homologação local como regra, não como exceção: redes, peças e estruturas reais (ativos, não dado pessoal), com ligações, ramais e imóveis sintéticos ou anonimizados na origem e coordenada deslocada dentro da quadra ou agregada por quadra (ajuste v2.11) |
| [0030](arquitetura/adr/ADR-0030-autenticacao-nos-apps.md) | Autenticação nos apps: navegador do sistema com PKCE (RFC 8252), token no Android Keystore ou iOS Keychain, chamadas pela camada nativa, access token de 5 min, refresh rotativo, revogação pelo tenant e pela Central; app do cliente no realm `ziipsan-portal`, app de campo no realm `ziipsan` em dispositivo registrado, com bloqueio remoto; web continua no BFF (ADR-0006) |
| [0031](arquitetura/adr/ADR-0031-calculo-offline-e-nota-fiscal-na-emissao-em-campo.md) | Emissão em campo: pacote da rota com tarifas e versões de parâmetro; mesmo módulo Java de cálculo no servidor e no plugin Android, com vetores comuns no CI (JVM e emulador); conta com chave da NFAg-e em contingência se o leiaute permitir (a confirmar), sem certificado no aparelho; recálculo no servidor, fila de divergências com aprovação pela retificação (P2.20); idempotência pela chave da tarefa |
| [0032](arquitetura/adr/ADR-0032-material-da-os-com-alcada-e-baixa-automatica.md) | Material da OS: requisição com alçada por valor e por item (classe b), reserva na aprovação, entrega registrada, baixa automática da quantidade usada e devolução da sobra no encerramento, custo apropriado na OS; segregação de funções; saldo negativo permitido com alerta e regularização; no campo, baixa na sincronização por `task_id` e alçada a posteriori para itens controlados; ED-58 na onda A, junto da OS completa (ED-36), desde a v2.6 (ajuste v2.6) |
| [0034](arquitetura/adr/ADR-0034-gis-cadastro-tecnico.md) | GIS e cadastro técnico: água, esgoto, drenagem e resíduos no mesmo modelo, com papel topológico fixo e classes, tipos e catálogo brasileiro de fábrica extensíveis por tenant; edição por pacotes revisados por outra pessoa, ciclo de vida projetado → em obra → em operação → abandonado e "rede na data"; núcleo (modelo, catálogo, topologia, mapa web com tiles vetoriais, vínculo com imóvel, ligação e ramal, importador DWG/SHP, temáticos) antes da homologação local, no P1–P3; edição web completa, QGIS, manobras com OS em lote, drenagem, resíduos e campo na trilha; precisão por feição com meta por tenant (padrão ±0,10 m H e ±0,05 m V); simbologia padrão sobrescrevível; QGIS por role individual e views `INSTEAD OF` que gravam no pacote, com endpoint próprio; EPANET e SWMM por exportação e importação. Projeto em [GIS-CADASTRO-TECNICO.md](arquitetura/GIS-CADASTRO-TECNICO.md). Ajuste v2.11: base da ligação na P2.23 confirmada; fora do backoffice autenticado, imóveis e ligações só agregados por quadra ou setor com no mínimo 5 imóveis (classe c); normas ABNT compradas no P1 (P1.28); DGN v8 por conversão assistida na implantação; OGC API – Features e WMS autenticados por tenant na onda B. Ajuste v2.12: WMS servidor pelo GeoServer (exceção no ADR-0010), com workspace e acesso ao banco por tenant e simbologia por SLD gerado da fonte única; OGC API – Features e WFS na `api`; referências a equipe e capacidade sem efeito |
| [0035](arquitetura/adr/ADR-0035-planejamento-sem-gestao-de-equipe-e-geoserver.md) | Planejamento sem gestão de equipe; GeoServer para a publicação WMS | Aceito, 12/10/2026 |
| [0036](arquitetura/adr/ADR-0036-icones-e-simbologia-do-mapa.md) | Ícones de interface (Lucide e próprios) e símbolos do mapa em SVG de fonte única, com aceite do dono por lote | Aceito, 14/10/2026 |
| [0037](arquitetura/adr/ADR-0037-plataforma-com-pacotes-de-vertical.md) | Plataforma Ziip com pacotes de vertical; saneamento primeiro; fronteira testada; domínio do GIS por referência | Aceito, 14/10/2026 |
| [0038](arquitetura/adr/ADR-0038-bifurcacao-normativa-vira-parametro.md) | Toda bifurcação normativa vira parâmetro do tenant, com padrão mais protetivo ao usuário; lei federal que proíbe continua travada | Aceito, 14/10/2026 |
| [0039](arquitetura/adr/ADR-0039-unidade-consumidora-raiz-e-alteracao-por-os.md) | Unidade consumidora como raiz do domínio; toda solicitação ou alteração de cliente ou unidade gera OS, de campo ou interna, com efeito no encerramento | Aceito, 14/10/2026 |
| [0040](arquitetura/adr/ADR-0040-permissoes-escopo-e-senha-por-tenant.md) | Catálogo de permissões `recurso:ação` no P0 (P0.13), unidade organizacional e escopo por área no P3 (P3.30) e política de senha por tenant por autenticador próprio no realm único (ED-06), testado a cada atualização do Keycloak | Aceito, 14/10/2026 |
| [0041](arquitetura/adr/ADR-0041-modulo-certificacoes-de-qualidade.md) | Módulo Certificações de qualidade: catálogo único de normas, voluntárias ativáveis e obrigatórias não desativáveis; o módulo não certifica | Aceito, 21/10/2026 |
| [0042](arquitetura/adr/ADR-0042-politica-de-ia-e-decisoes-automatizadas.md) | Política de IA e decisões automatizadas: nenhum modelo sem humano no circuito, auditoria, mascaramento e desligamento; IA não decide corte, tarifa social, negativação nem acesso a serviço essencial | Aceito, 22/10/2026 |

Os ADRs 0010, 0011, 0012, 0015, 0020 e 0022 foram ajustados em consequência das decisões da v2.4 (exceções de
infraestrutura, hospedagem, metas do nível Setor Público, consulta pós-contrato, severidades e fatura por e-mail); na
v2.5, os ADRs 0001, 0006, 0020, 0023, 0024, 0025, 0026 e 0028 receberam linha "Ajuste de 12/10/2026 (editais, v2.5)";
na v2.6, os ADRs 0029 e 0032 receberam linha "Ajuste de 12/10/2026 (editais, v2.6)"; na v2.7, foi criado o ADR-0033
e os ADRs 0001, 0010, 0011, 0012, 0013, 0023, 0024, 0025 e 0029 receberam linha "Ajuste de 12/10/2026 (v2.7)"; na v2.8,
os ADRs 0001, 0023, 0029 e 0033 receberam linha "Ajuste de 12/10/2026 (v2.8)"; na v2.9, os ADRs 0029 e 0033
receberam linha "Ajuste de 12/10/2026 (v2.9)"; na v2.10, foi criado o ADR-0034 e os ADRs 0010 e 0028 receberam linha
"Ajuste de 12/10/2026 (GIS, v2.10)"; na v2.11, os ADRs 0029, 0033 e 0034 receberam linha "Ajuste de 12/10/2026 (GIS,
v2.11)"; na v2.12, o ADR-0029 foi revogado, os ADRs 0001, 0020, 0023, 0025, 0028, 0032, 0033 e 0034 receberam linha
"Ajuste de 12/10/2026 (v2.12)" sobre equipe e capacidade e os ADRs 0010, 0028 e 0034, linha "Ajuste de 12/10/2026 (GIS,
v2.12)" sobre o GeoServer. Os ADRs
0016, 0018, 0022, 0027 e 0028 citam o go-live e leem-se pela regra do ADR-0033 (item 7). Em divergência com este
plano, vale o ADR.

**Decisões do dono do produto (cartões de 12/10/2026)**, aplicadas nos ADRs 0018–0020, no ADR-0010 e nos documentos
de arquitetura:

- Suspensão de tenant sempre manual, com aviso prévio; backoffice em somente leitura; portal do consumidor, Pix,
  retornos bancários e 2ª via continuam (serviço essencial).
- Suspensão, bloqueio e eliminação de dados de tenant: uma pessoa com MFA basta, sempre com justificativa e auditoria;
  a eliminação tem carência de 30 dias e certificado de descarte, e contestação na carência ou ordem judicial a
  suspende (§2.4). Suspensão e bloqueio: `PLATFORM_OWNER` ou `PLATFORM_FINANCE`, este só por inadimplência
  contratual; eliminação: só `PLATFORM_OWNER` (§2.4). A aprovação dupla continua nos parâmetros do tenant (ADR-0015),
  no ajuste de conciliação e na liberação da emissão fiscal em produção.
- Chamados de suporte e anexos retidos pela vigência do contrato mais 5 anos, com minimização, acesso restrito e
  eliminação no fim do prazo.
- ClamAV junto do `worker`, com quarentena até a varredura, substituível pelo serviço da hospedagem: exceção do
  ADR-0010 (anexos de chamados e logos de tenant).
- Corte por inadimplência: nenhuma configuração do tenant afrouxa (ADR-0015, item 3).

**Decisões do dono do produto sobre editais e hospedagem (cartões de 12/10/2026, v2.4)**, aplicadas no ADR-0011 e nos
ADRs 0023–0028, a partir das matrizes 18d–18h e do consolidado 18:

- **Escopo.** POC e editais passam a ser escopo. Fica revogada a linha "Fora do escopo: POC, editais" do diário §1
  (ADR-0023, item 7); blockchain no MTR e escrita com ESRI continuam fora.
- **Calendário.** Trilha de editais no P4 (a partir de 02/08/2027), mirando licitações de 2028 (desde a v2.8, a partir
  do 3º trimestre de 2029, pela opção A do lançamento); o caminho crítico do P0
  ao go-live não muda. Do piloto entram antes do P4 só o que ele mesmo precisa e as matrizes mostraram faltar:
  retificação e manutenção de conta (P2.20) e cerca de 20 relatórios operacionais (P3.22); da prontidão de POC, os
  roteiros (P1.25) e o ambiente de demonstração (P3.23).
- **Prontidão de POC.** A partir do P1, ensaio interno ao fim de cada fase, com resultado registrado; a partir do P3, a
  meta da fase entra no aceite (§8). Meta final da trilha: roteiro da VISAN com pelo menos 146 de 162 pontos e nenhuma
  falha em item crítico do SAMAE, em dois ensaios seguidos.
- **Habilitação.** Primeiro atestado pelo piloto público em SC, por dispensa ou CPSI, com cláusula de atestado (P0.30);
  segundo por cliente privado; kit de habilitação e INPI em paralelo (P0.44).
- **Hospedagem.** AWS `sa-east-1` gerenciada (ADR-0011); instância dedicada e cláusula de reversão (ADR-0024).
- **Nível de serviço.** "Setor Público" contratável (ADR-0025), sem mudar o padrão do produto.
- **Módulos.** Apps Capacitor (ADR-0026); BI híbrido (ADR-0027); escopo funcional da trilha (ADR-0028), inclusive o ERP
  construído no recorte do saneamento e integrado com Betha, IPM e Fiorilli.
- **Itens de edital contrários à lei.** O produto segue a lei e as travas do ADR-0015; pedido de esclarecimento ou
  impugnação (consolidado 18, §6).

**Decisões do dono do produto sobre a trilha de editais (cartões de 12/10/2026, v2.5)**, aplicadas nos ADRs 0030 a
0032 e nos ajustes dos ADRs 0001, 0006, 0020, 0023 a 0026 e 0028 (a decisão de equipe da mesma data, ADR-0029, foi
revogada na v2.12):

- **ERP.** Recorte "núcleo saneamento + integração": almoxarifado (materiais e estoque para a OS) e frota (veículos e
  equipes de campo) construídos; integração por API e/ou arquivo com Betha, IPM e Fiorilli; compras, RH e orçamento
  em 2029, sob demanda (ADR-0028, item i, ajuste v2.5).
- **Material da OS.** Requisição com alçada por valor e por item, reserva na aprovação, baixa automática da quantidade
  usada e devolução da sobra no encerramento, saldo negativo permitido com alerta (ADR-0032).
- **Omnichannel.** Messenger e Instagram (API oficial da Meta) e Telegram (Bot API) como conectores, no mesmo modelo do
  WhatsApp (ADR-0028, item c, ajuste v2.5).
- **Windows Server.** O pacote de reversão roda em Docker sobre Windows Server, com contêineres Linux no WSL2,
  homologado uma vez; o SaaS segue em Linux na AWS; a URA segue a integração mínima (ADR-0024, ajuste v2.5).
- **Login nos apps.** Navegador do sistema com PKCE, token no armazenamento seguro do aparelho, dispositivo registrado
  no app de campo (ADR-0030).
- **Emissão em campo.** Mesmo motor no servidor e no app, vetores comuns no CI, recálculo e fluxo de divergência; chave
  da NFAg-e em contingência se o leiaute permitir (ADR-0031).
- **FEBRABAN 150.** Condicional ao piloto: a P0.30 levanta os optantes; havendo, sobe para o P3 (P3.24) como requisito
  do go-live; senão, fica na onda A (ADR-0028, item f, ajuste v2.5).

**Decisão do dono do produto sobre o almoxarifado (cartão de 12/10/2026, v2.6)**, aplicada no ajuste v2.6 do ADR-0032:

- **Onda do almoxarifado.** A ED-58 (almoxarifado com reserva, alçada e baixa automática, ADR-0032) é antecipada da
  onda C para a onda A, junto da ED-36 (OS completa), para que a requisição de material da OS afete o estoque desde a
  primeira onda. Efeito: +7,5 semanas-pessoa de esforço na onda A e na prontidão de POC (≈ 199). Compras continua em
  2029, sob demanda (ADR-0028, item i, ajuste v2.5).

**Decisões do dono do produto sobre ambiente e lançamento (cartões de 12/10/2026, v2.7)**, aplicadas no ADR-0033 e nos
ajustes v2.7 dos ADRs 0001, 0010, 0011, 0012, 0013, 0023, 0024, 0025 e 0029:

- **Desenvolvimento local até o lançamento.** Todo o desenvolvimento roda em Docker Compose na máquina de
  desenvolvimento, com PostgreSQL 17, Keycloak, emulador de S3 e cofre local, até concluir todas as fases e o produto
  estar pronto para venda. A AWS `sa-east-1` (ADR-0011, provedor mantido) só sobe no lançamento comercial, quando não
  houver mais desenvolvimento de fase pendente. Até lá, o código fica pronto para a AWS por interfaces (armazenamento
  de objetos, chaves e KMS, segredos, fila, e-mail), com implementação local agora e AWS no lançamento (P0.45, fase LC).
- **Homologação local do piloto.** O marco de jun–jul/2027 deixa de ser produção real: passa a homologação local do
  piloto com dados sintéticos ou anonimizados do cliente (faturamento paralelo comparado com o sistema dele, ensaio de
  POC). Nenhum dado pessoal real (CPF, nome, endereço, contato) entra na base local. Produção real com cliente e emissão
  de atestado só acontecem no lançamento, junto com a AWS (GL.1 a GL.4, LC.10 a LC.14).

**Decisões do dono do produto sobre o lançamento e o certificado (cartões de 12/10/2026, v2.8)**, aplicadas nos
ajustes v2.8 dos ADRs 0001, 0023 e 0033:

- **Momento do lançamento: opção A.** O lançamento comercial começa depois de todas as fases, inclusive a trilha de
  editais (ondas A, B e C): início em outubro de 2028 (M14), primeira produção do piloto por volta de fevereiro de 2029
  (M15), primeiro atestado entre maio e julho de 2029 (M16) e licitações com POC a partir do 3º trimestre de 2029
  (M13). A meta de disputar licitações no 2º semestre de 2028 deixa de valer.
- **Certificado da homologação fiscal local.** A produção restrita da NFS-e Nacional e a homologação da SEFAZ
  (`tpAmb=2`), a partir do ambiente local e sem emissão real, usam o e-CNPJ da própria Ziip/TGP. O CPF contido nele é
  do responsável pela empresa, que consente, e não é dado de cliente. O certificado fica no cofre local, fora do banco,
  com cada acesso registrado. É exceção explícita à regra "nenhum dado pessoal real na base local" (ADR-0033, item 5,
  ajuste v2.8); nenhum certificado de cliente entra no ambiente local.

**Decisão do dono do produto sobre os XML da homologação fiscal (cartão de 12/10/2026, v2.9)**, aplicada no ajuste
v2.9 do ADR-0033 (a decisão de equipe da mesma data foi retirada na v2.12):

- **XML assinados na homologação fiscal local.** Contêm o certificado público da Ziip/TGP, com o CPF do responsável, e
  entram na mesma exceção do ADR-0033 (item 5, ajustes v2.8 e v2.9) e no mesmo consentimento. Ficam em área separada da
  base local e são apagados ao fim de cada ciclo de testes; só os vetores de referência sem assinatura são guardados
  (P1.8, P3.7).

**Decisões do dono do produto sobre o GIS e o cadastro técnico (cartões de 12/10/2026, v2.10)**, aplicadas no ADR-0034,
nos ajustes v2.10 dos ADRs 0010 e 0028 e em [arquitetura/GIS-CADASTRO-TECNICO.md](arquitetura/GIS-CADASTRO-TECNICO.md):

- **Domínios.** Água, esgoto, drenagem e resíduos sólidos no mesmo modelo, catálogo e topologia; água e esgoto
  completos primeiro (núcleo), drenagem e resíduos em seguida (onda B).
- **Catálogo.** Catálogo brasileiro de fábrica (NBR, padrões Corsan/Aegea e Saneago) com materiais, DN e DN2, ângulos
  de curva, classes de pressão, juntas, peças e conexões; cada tenant ativa, desativa e acrescenta itens sem código;
  valor sem norma conferida marcado como valor de referência a validar.
- **Edição.** Por pacotes, com revisão por outra pessoa antes de entrar na rede em operação, na web, no QGIS e no app
  de campo; ciclo de vida projetado → em obra → em operação → abandonado; histórico temporal e consulta "rede na data".
- **Momento.** Núcleo do GIS antes da homologação local do piloto, no P1–P3 (P1.26, P1.27, P2.21 a P2.23, P3.25 a
  P3.27); edição web completa (onda A), QGIS, manobras com OS em lote, drenagem, resíduos e campo (onda B) na trilha.
- **Precisão.** Método de captura, exatidão estimada, data, responsável e origem por feição; meta por tenant e por
  classe, padrão ±0,10 m na horizontal e ±0,05 m na vertical; mapa e relatórios destacam o que está fora da meta e
  separam cadastro aproximado de levantado.
- **Simbologia.** Padrão Ziip por tipo e estado, igual no mapa web, no projeto QGIS gerado e nos PDFs, sobrescrevível
  por tenant.
- **QGIS.** Role individual sem privilégios, presa ao tenant; edição só por views com `INSTEAD OF` que gravam no pacote
  e passam pela validação; projeto QGIS gerado pelo produto; endpoint próprio com TLS e auditoria, fora do pooler.
- **Hidráulica.** EPANET (água) e SWMM (esgoto e drenagem) juntos, por exportação e importação a partir da topologia.

**Decisões do dono do produto sobre as pendências do GIS (cartões de 12/10/2026, v2.11)**, aplicadas nos ajustes v2.11
dos ADRs 0033 e 0034, em [arquitetura/GIS-CADASTRO-TECNICO.md](arquitetura/GIS-CADASTRO-TECNICO.md) e em
[arquitetura/PARAMETROS-DO-TENANT.md](arquitetura/PARAMETROS-DO-TENANT.md) (as decisões de equipe da mesma data foram
retiradas na v2.12):

- **Base da ligação.** `service_connections` antecipada da ED-13 para a P2.23 (0,5 semana): confirmada.
- **Rede do piloto na homologação local.** Entram redes, peças e estruturas reais do piloto, que são ativos e não dado
  pessoal; ligações, ramais e imóveis vêm sintéticos ou anonimizados na origem, com coordenada deslocada dentro da
  quadra ou agregada por quadra. É regra do ADR-0033 (ajuste v2.11), não exceção de dado pessoal.
- **LGPD no mapa.** Fora do backoffice autenticado (portal, WMS e OGC públicos ou entregues a terceiros, relatórios
  externos), dados de imóveis e ligações só agregados por quadra ou setor, com no mínimo 5 imóveis por célula; rede e
  ativos podem ser publicados. Parâmetro `gis.public_aggregation_min_units`, classe c (só ampliável), com piso 5.
- **Normas ABNT.** Compra das principais no início do P1, antes do catálogo (P1.28, custo a estimar): NBR 12586, 12587,
  12218, 9649, 5647, 7665, 7675 e 15561 e as demais apontadas em GIS-CADASTRO-TECNICO §4.
- **DGN v8.** Conversão assistida na implantação, para DWG ou DXF, com ferramenta do cliente ou do Ziip; o importador
  aceita DGN v7, DWG, DXF, SHP, GPKG e INP do EPANET e do SWMM.
- **Publicação OGC.** OGC API – Features e WMS, autenticados por tenant, na onda B, junto do GIS avançado (ED-67); o
  servidor do WMS foi definido na v2.12 (abaixo).

**Decisões do dono do produto sobre o escopo do plano e o WMS (12/10/2026, v2.12)**, aplicadas na revogação do
ADR-0029, nos ajustes v2.12 dos ADRs 0001, 0020, 0023, 0025, 0028, 0032, 0033 e 0034, nos ajustes "GIS, v2.12" dos
ADRs 0010, 0028 e 0034, em [arquitetura/GIS-CADASTRO-TECNICO.md](arquitetura/GIS-CADASTRO-TECNICO.md) e no consolidado 18:

- **Escopo do plano.** O plano trata do que será feito e de como: escopo, desenho, ordem, dependências e critérios de
  aceite. Gestão de equipe e capacidade fica fora dele (número de pessoas, contratações, perfis, onboarding,
  pessoas-equivalentes, gatilhos de contratação, folga, custo e regime de equipe, reprogramação por falta de gente). O
  tamanho das tarefas (P, M, G e semanas-pessoa) fica como indicação de esforço; as datas das fases e dos marcos, como
  metas de calendário. O ADR-0029 fica revogado e mantido como histórico.
- **WMS da onda B pelo GeoServer.** O WMS 1.3.0 da ED-67 (GIS-17) é publicado pelo GeoServer, componente da onda B por
  exigência de edital (exceção no ADR-0010). Só leitura (WMS; WFS-T e edição desligados), sem porta publicada e
  acessível só pela `api`, que autentica, confere a permissão por camada e encaminha ao workspace do tenant da
  credencial. Isolamento: um workspace por tenant; o acesso ao banco de cada workspace usa role de banco própria do
  tenant, somente leitura, sem privilégio, que só alcança o schema dele (schema próprio) ou views filtradas pelo tenant
  da role (schema compartilhado, ADR-0005), como no Superset e no QGIS; nenhuma credencial lê outro tenant (teste).
  Simbologia em SLD gerado da mesma fonte única que gera o estilo do mapa web e o projeto QGIS, sem estilo editado à mão
  no GeoServer. Configuração (workspaces, stores, camadas, estilos) gerada pelo produto pela API REST do GeoServer. OGC
  API – Features e WFS 2.0 com `Transaction` no pacote continuam na `api`.

