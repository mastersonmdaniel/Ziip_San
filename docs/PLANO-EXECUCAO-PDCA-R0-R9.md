# Plano de execução do Ziip SAN: fases P0–P4 e go-live piloto

**Versão 2.5, 12/10/2026.** Produto Ziip SAN; nome interno do código, SIGSAN, renomeado para `ziipsan` na P0.33. O
nome do arquivo foi mantido por causa dos links. Este é o plano canônico; em divergência com um ADR, vale o ADR mais
recente.

**O que mudou em relação à v1**

- Roadmap oficial P0–P4 com marco de go-live piloto; R1–R9 vira conteúdo das fases (ADR-0001).
- G1, G2, G4, G5, G6 e G9 viram parâmetros do tenant com aceite formal e deixam de bloquear fases (ADR-0002); G3, G7 e
  G8 foram decididas pelo produto (ADR-0003, ADR-0004).
- Entram a camada de serviço operado (artefato, ambientes, backup, lote, observabilidade, carga), a migração do legado
  e todos os achados críticos, altos e médios da revisão 17.
- `outOfOrder(true)` ganha prazo; depois dele, versões Flyway sempre crescentes (§3).
- NFAg-e sobre motor fiscal próprio, portado do Ziip Cloud, no lugar do integrador.
- BFF com cookie, MFA por tenant, máscara configurável, push automático, desenvolvimento fora do Google Drive.
- Versão 2.1: marca e renomeação `ziipsan`, temas, Central Ziip, cobrança por contrato, ajuda e suporte, Sobre e
  changelog, histórico e documentos nos portais (ADR-0016 a ADR-0022), com as decisões do dono do produto de
  12/10/2026 sobre suspensão, eliminação, retenção de chamados e antivírus (§2.2).
- Versão 2.2: sete pendências do §2.4 resolvidas pelo dono do produto em 12/10/2026 (carência de 30 dias, papéis
  que suspendem e eliminam, acesso de emergência, conta do consumidor no portal, documentos anteriores ao go-live,
  cores dos temas e idioma dos identificadores), com tarefas e critérios ajustados, a nova P2.19 e a convenção de
  idioma no §8.
- Versão 2.3: três decisões do dono do produto em 12/10/2026 (§2.4): contestação durante a carência suspende a
  eliminação de dados do tenant, com resposta do Ziip em até 15 dias e prazo máximo de 75 dias do fim do contrato;
  login do consumidor no portal por CPF/CNPJ ou e-mail, com senha, sem SMS nem login por celular no go-live; códigos
  de contrato da API em inglês (`urn:ziipsan:problem:`), aplicados na P0.33. Ajustados P0.33 (com a tabela de
  códigos), P0.43, P1.6, P1.22, P3.3, P3.8 e P3.16, os riscos e o §8, cuja única exceção de idioma passa a ser o
  protocolo `SUP-AAAA-NNNNNN`.
- Versão 2.4: decisões do dono do produto de 12/10/2026 sobre os editais VISAN Videira PE 06/2026 e SAMAE São Ludgero
  PE 2/2026 (ADR-0023 a ADR-0028) e hospedagem decidida (ADR-0011 aceito: AWS `sa-east-1` gerenciada). POC e editais
  entram no escopo, revogando a linha "Fora do escopo: POC, editais" do diário §1 (ADR-0023, item 7). Hospedagem sai
  das pendências e P0.26–P0.29 (IaC, backup, PITR, R1) ficam desbloqueadas; P0.27, P0.28 e P0.30 ajustadas; novas
  P0.44 (kit de habilitação), P1.25 (roteiros de POC), P2.20 (retificação de conta), P3.22 (relatórios operacionais) e
  P3.23 (ambiente de demonstração); P2.15, P3.15 e P3.16 ampliadas pelo nível Setor Público (ADR-0025); P4.3 e P4.4
  redefinidas (ADR-0027, ADR-0026); **trilha de editais** no P4 com as entregas ED-01 a ED-62 do consolidado
  [18](levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md), em ondas A, B e C; qualidade da água, GIS web,
  migração do cadastro técnico, manobras, negativação e protesto e instância dedicada saem de 2028 para o P4; mapa
  Flyway com faixas dos módulos novos; caminho crítico, riscos (capacidade quantificada), marcos ("prontidão de POC" e
  "primeira licitação 2028") e resumo atualizados.
- Versão 2.5: oito decisões do dono do produto de 12/10/2026 sobre a trilha de editais (§2.2). Equipe de 4 a 5
  desenvolvedores a partir de agosto de 2027, prontidão de POC em 30/06/2028 e licitações no 2º semestre de 2028
  (ADR-0029); autenticação nos apps (ADR-0030); cálculo offline e NFAg-e na emissão em campo (ADR-0031); material da OS
  com alçada, reserva e baixa automática (ADR-0032); ADR-0028 ajustado: ERP no recorte "núcleo saneamento +
  integração" (almoxarifado e frota construídos; compras, RH e orçamento em 2029, sob demanda), conectores Messenger,
  Instagram e Telegram no omnichannel e FEBRABAN 150 condicional ao piloto; pacote de reversão também em Windows
  Server (ADR-0024). Ajustados também os ADRs 0001, 0006, 0020, 0023, 0025 e 0026. Novas P3.24 (FEBRABAN 150
  condicional), `V6_4` e core `V11_1`; P0.30, P1.1, GL.4, P4.3, P4.4, ED-04, ED-05, ED-11, ED-18, ED-19, ED-20, ED-27, ED-36,
  ED-39, ED-42, ED-58, ED-59 e ED-62 ajustadas; ED-60 e ED-61 saem da trilha; esforço e capacidade recalculados
  (trilha ≈ 206 semanas-pessoa, igual à v2.4 por compensação; prontidão ≈ 192, antes ≈ 186; ≈ 4,5
  pessoas-equivalentes para junho de 2028, antes ≈ 4,3); §2.4, marcos M3, M8,
  M11 e M12, caminho da trilha, riscos e resumo atualizados. Limpeza de referências: severidades S1–S5 no suporte no lugar de P1–P4
  (ADR-0020), hospedagem e cofre no porte fiscal, P4.3 e P4.4 alinhadas aos ADRs 0027 e 0026.

**Referências.** Revisão 17: [levantamento/17-REVISAO-ESTRUTURA-SAAS-2026-10-12.md](levantamento/17-REVISAO-ESTRUTURA-SAAS-2026-10-12.md);
ADRs: [arquitetura/adr/README.md](arquitetura/adr/README.md); parâmetros: [arquitetura/PARAMETROS-DO-TENANT.md](arquitetura/PARAMETROS-DO-TENANT.md);
RNF: [arquitetura/REQUISITOS-NAO-FUNCIONAIS.md](arquitetura/REQUISITOS-NAO-FUNCIONAIS.md); arquitetura-alvo:
[arquitetura/ARQUITETURA-ALVO.md](arquitetura/ARQUITETURA-ALVO.md); porte fiscal: [arquitetura/FISCAL-PORTE-JAVA.md](arquitetura/FISCAL-PORTE-JAVA.md);
PDCA: [levantamento/16-PDCA-2026-10-12.md](levantamento/16-PDCA-2026-10-12.md); execução: [DIARIO-DE-BORDO.md](DIARIO-DE-BORDO.md) §18;
editais: [levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md](levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md)
(consolidado das extrações 18a–18c e das matrizes 18d–18h).

**Convenção.** Achados da revisão 17 levam o prefixo `17:` (`17:X1`, `17:S1`, `17:D1`), para não confundir com as
decisões D1–D13 e S1–S3 da v1. Os médios foram numerados aqui `17:M1`–`17:M15` e os baixos `17:B1`–`17:B11`, na ordem
do relatório.

---

## 1. Estado atual (12/10/2026)

- Fundações E0–E6 concluídas em 12/10/2026 (alvo era 27/11): 973 testes Java e 134 do frontend verdes.
- Isolamento por tenant provado no PostgreSQL real: roles separadas, função da RLS de `sigsan_owner`, RLS FORCE, GUC
  definida e limpa pela conexão, tenant só do JWT.
- Auditoria sem dado pessoal; CPF/CNPJ cifrados com índice cego no cadastro e no MTR.
- Aplicação executável (Spring Boot 4.1.1, Keycloak 26.8.0, PKCE, API do cadastro), aceita de ponta a ponta.
- Cadastro de cliente pela tela bloqueado até o R2 (tipo de cliente, perfil de imóvel, endereço).
- Faturamento, medição e arrecadação: motores puros, sem serviço, API nem lote.
- Chave só de desenvolvimento; nenhum CPF real antes do R1.
- Só ambiente local: sem Dockerfile, CI remoto, backup nem observabilidade.
- GitHub privado `mastersonmdaniel/Ziip_San` (push manual até `fb2b53d`); código ainda editado no Google Drive.
- Sem cliente piloto definido. Hospedagem decidida em 12/10/2026: AWS `sa-east-1` gerenciada (ADR-0011).
- Editais analisados (consolidado 18): dos 2.663 requisitos distintos da VISAN e do SAMAE, 3% atendidos, 21% em parte
  e 75% não atendidos; o produto hoje reprova nas duas provas de conceito.

**Histórico.** A v1 consolidou o PDCA de 12/10 (C1–C5) e o roadmap R0–R9 do `15c` em fundações E0–E6, com mapa Flyway e
portões de decisão. As etapas foram executadas no mesmo dia, um commit por etapa (diário §18). A revisão 17, sobre
`fb2b53d`, concluiu que o código está correto mas ainda não é serviço operável, o que motivou esta versão.

---

## 2. Decisões

### 2.1 Técnicas da v1

**=** mantida; **Δ** alterada.

| # | Decisão | Situação |
|---|---|---|
| D1 | `.git` fora do Drive (`--separate-git-dir`) | Δ todo o desenvolvimento sai do Drive no P0 (ADR-0009) |
| D2 | Push só com autorização | Δ **revogada pelo ADR-0009**: push automático com testes verdes |
| D3 | Autor: nome e e-mail do usuário | = |
| D4 | Spring Boot 4.1.1 (suporte OSS até 31/07/2027) | = linha seguinte antes de 31/07/2027 (P3.14) |
| D5/D6 | Realm único, claim `tenant_id` (UUID) | Δ mais gestão de usuários que fixa o `tenant_id` (17:S3); consumidores do portal em realm próprio, `ziipsan-portal` (ADR-0022) |
| D7 | Keycloak 26, banco próprio | = modo de produção na P0.29 |
| D8 | API na 8090 | = |
| D9 | Cifra de dev por HKDF; só dado sintético até o R1 | Δ `dev-env` só em lista fechada de perfis (17:M2) |
| D10 | Migrations por job separado | Δ a JVM da API não recebe credencial de migração (17:S2) |
| D11 | Rascunhos e `validate-step` no R2 | = (P1.3) |
| D12 | ADMIN, MANAGER, OPERATOR, VIEWER, `registry.reveal-document` | Δ novos: busca por documento, aprovação de parâmetros, plataforma |
| D13 | `oidc-client-ts`, token em memória | Δ **substituída pelo ADR-0006** (BFF); hoje o token está em `sessionStorage` |
| G10a | `outOfOrder(true)` + teste "tenant antigo alcança o atual" | Δ só até o fim do P3; depois, versões sempre crescentes (17:D1) |
| S1–S3 (v1) | `DocumentNumber` no core; CPF mascarado no PDF do MTR; `drivers` cifrado | = implementadas; máscara segue o ADR-0008 |

### 2.2 Decisões de 12/10/2026

| ADR | Decisão |
|---|---|
| [0001](arquitetura/adr/ADR-0001-roadmap-p0-p4.md) | P0–P4 com go-live piloto; piloto escolhido no P0 |
| [0002](arquitetura/adr/ADR-0002-decisoes-g-como-parametros-do-tenant.md) | Parâmetros do tenant com vigência, sugerido, limite e aceite; contrato, DPA e termos no P3 |
| [0003](arquitetura/adr/ADR-0003-motor-fiscal-portado-para-java.md) | Motor fiscal C# portado para Java; NFAg-e (modelo 75) e NFS-e avulsa sobre ele |
| [0004](arquitetura/adr/ADR-0004-sinisa-e-sinir-assistidos.md) | SINISA e SINIR assistidos: arquivo oficial, o cliente envia |
| [0005](arquitetura/adr/ADR-0005-isolamento-hibrido-por-porte.md) | Isolamento híbrido por porte; shard no catálogo; teste com 300 schemas |
| [0006](arquitetura/adr/ADR-0006-bff-com-cookie-httponly.md) | BFF com cookie HttpOnly, no processo `api`, sessão no PostgreSQL; vale para o navegador, e os apps seguem o ADR-0030 (ajuste v2.5) |
| [0007](arquitetura/adr/ADR-0007-mfa-configuravel-por-tenant.md) | MFA configurável por tenant; plataforma sempre com MFA. Prevalece sobre o 17:S4 (MFA obrigatório por papel) |
| [0008](arquitetura/adr/ADR-0008-mascara-de-documento-configuravel.md) | Máscara configurável com teto; busca por documento com papel, registro e cota |
| [0009](arquitetura/adr/ADR-0009-push-automatico-e-dev-fora-do-drive.md) | Push automático; desenvolvimento em disco local + GitHub |
| [0010](arquitetura/adr/ADR-0010-infra-nao-usada-congelada.md) | Infra não usada em perfis em 127.0.0.1; k8s e Terraform em `infra/_arquivado/` |
| [0011](arquitetura/adr/ADR-0011-hospedagem-a-decidir.md) | Hospedagem: **aceito em 12/10/2026** (v2.4): AWS `sa-east-1` gerenciada (RDS PostgreSQL 17 Multi-AZ com PITR e réplica de leitura, KMS, Secrets Manager, S3 com Object Lock); nenhum dado, réplica ou backup fora da região; certificações exigidas em edital conferidas no AWS Artifact; código continua neutro; desbloqueia R1, backup, PITR e IaC (P0.26–P0.29) |
| [0012](arquitetura/adr/ADR-0012-metas-nao-funcionais.md) | Metas não funcionais do §6 da revisão 17 |
| [0013](arquitetura/adr/ADR-0013-postgresql-17-no-p0.md) | PostgreSQL 17 + PostGIS 3.5 no P0, antes de dado real |
| [0014](arquitetura/adr/ADR-0014-outbox-no-postgresql.md) | Outbox no PostgreSQL com workers `SKIP LOCKED`, atrás de interface |
| [0015](arquitetura/adr/ADR-0015-limites-legais-dos-parametros.md) | Classes de parâmetro (a–d); multa padrão 2% com aceite reforçado acima; vedações de corte por inadimplência travadas; piso da tarifa social; pisos de retenção |
| [0016](arquitetura/adr/ADR-0016-rebrand-e-renomeacao-ziipsan.md) | Pacote de marca único; marca do tenant; renomeação `sigsan` → `ziipsan` no P0, com contexto HKDF congelado e códigos de problema da API em inglês (`urn:ziipsan:problem:`) |
| [0017](arquitetura/adr/ADR-0017-temas-claro-escuro-azul-e-verde-agua.md) | Temas claro, escuro e do sistema; padrões Azul Água e Verde Água; preferência no servidor; contraste no CI |
| [0018](arquitetura/adr/ADR-0018-central-ziip-propria.md) | Central Ziip própria com o console; papéis de plataforma com MFA; sem leitura de dado pessoal de tenant; API de métricas `v1` |
| [0019](arquitetura/adr/ADR-0019-cobranca-saas-por-contrato.md) | Cobrança do SaaS por contrato; nenhum bloqueio automático; restrição só manual |
| [0020](arquitetura/adr/ADR-0020-ajuda-e-suporte-com-protocolo.md) | Ajuda e suporte com protocolo e SLA por contrato; ClamAV; acesso assistido com consentimento no lugar da impersonação; acesso de emergência só em incidente de segurança declarado |
| [0021](arquitetura/adr/ADR-0021-sobre-versao-e-changelog.md) | SemVer gerado no build; changelog do cliente obrigatório para segurança, legislação e parâmetro regulatório; Sobre |
| [0022](arquitetura/adr/ADR-0022-portais-historico-e-documentos.md) | Portal do consumidor com 120 meses, conta paga, DANFAG e XML original; conta do consumidor no realm `ziipsan-portal`, com login por CPF/CNPJ ou e-mail e senha, sem SMS; vínculo por código impresso na conta e CPF/CNPJ; documentos anteriores ao go-live importados; área do tenant com faturas e NFS-e do contrato |
| [0023](arquitetura/adr/ADR-0023-estrategia-de-editais-e-prova-de-conceito.md) | Trilha de editais no P4, mirando licitações de 2028, sem mexer no go-live; POC passa a ser escopo, com prontidão de POC no aceite de fase a partir do P3, ambiente de demonstração permanente (≥ 10 mil unidades fictícias), roteiros internos VISAN e SAMAE e ensaio por fase; atestados pelo piloto público em SC (dispensa ou LC 182/2021, com cláusula de atestado) e por cliente privado; implantação e treinamento remotos no piloto, equipe para editais decidida no ADR-0029 (ajuste v2.5); itens ilegais de edital: segue-se a lei, com pedido de esclarecimento ou impugnação; revoga "POC, editais" do diário §1 |
| [0024](arquitetura/adr/ADR-0024-instancia-dedicada-e-clausula-de-reversao.md) | Padrão SaaS multi-tenant; instância dedicada gerenciada para quem exigir em contrato; execução na infraestrutura do órgão só como cláusula de reversão (pacote em contêiner, requisitos mínimos, runbook, desconto), em Linux ou em Windows Server 2022+ com Docker no WSL2, homologado uma vez (ajuste v2.5); SaaS em Linux na AWS; acesso administrativo do órgão só na instância dedicada |
| [0025](arquitetura/adr/ADR-0025-nivel-de-servico-setor-publico-contratavel.md) | Nível de serviço "Setor Público" contratável: 99,9%, cinco severidades com prazo de solução, aderência e multa, exportação em 10 dias úteis com dicionário, consulta somente leitura por até 120 dias após o contrato, relatórios e arquivos em 2 ou 5 min (RNF-71); padrão do produto inalterado, com as severidades S1–S5 também no padrão (ajuste v2.5); plantão da S1 com a equipe do ADR-0029, ativo até 31/01/2028 |
| [0026](arquitetura/adr/ADR-0026-apps-hibridos-com-capacitor.md) | Apps híbridos com Capacitor: app de campo só Android, por APK (leitura com emissão simultânea, OS, impressão Zebra série ZQ com editor de leiaute ZPL/CPCL); app do cliente Android e iOS nas lojas com a marca do tenant; a P4.4 passa a app de campo Android; autenticação no ADR-0030 e emissão em campo no ADR-0031 (ajuste v2.5) |
| [0027](arquitetura/adr/ADR-0027-bi-hibrido-com-apache-superset.md) | Relatórios e painéis padrão nativos, com cerca de 20 relatórios operacionais já no P3; Apache Superset embarcado na trilha do P4, na réplica de leitura, com SSO e isolamento por role de banco por tenant; exceção registrada no ADR-0010 |
| [0028](arquitetura/adr/ADR-0028-escopo-funcional-ampliado-pelos-editais.md) | Escopo da trilha: qualidade da água no P4 com apoio à ISO 17025 e SISAGUA assistido; GIS web próprio, QGIS com role individual e DWG convertido pelo ODA File Converter (revoga o R9 de 2028 para esses itens e para manobras); omnichannel híbrido com chatbot por modelo de linguagem de terceiro, com conectores WhatsApp, Messenger, Instagram e Telegram (ajuste v2.5); dívida ativa (núcleo); eventos contábeis e e-Sfinge assistido; Pix Automático e FEBRABAN 150 legado, este no P3 se o piloto tiver optantes (ajuste v2.5); negativação e protesto por arquivo; cartão pela credenciadora do cliente; ERP no recorte "núcleo saneamento + integração": almoxarifado e frota construídos, integração com Betha, IPM e Fiorilli, compras, RH e orçamento em 2029 sob demanda (ajuste v2.5); telemetria por REST e arquivo (MQTT só sob demanda); CTI mínimo e totem em modo quiosque; fatura por e-mail com link seguro e PDF por opt-in |
| [0029](arquitetura/adr/ADR-0029-capacidade-e-equipe-da-trilha-de-editais.md) | Equipe da trilha: 4 a 5 desenvolvedores a partir de 02/08/2027 (backend Java, frontend e mobile, GIS e dados, QA e POC), onboarding de 8 semanas, plantão da S1 até 31/01/2028; prontidão de POC em 30/06/2028 e licitações no 2º semestre de 2028; custo a estimar até o M8; sem contratação, prontidão por volta de 2031 |
| [0030](arquitetura/adr/ADR-0030-autenticacao-nos-apps.md) | Autenticação nos apps: navegador do sistema com PKCE (RFC 8252), token no Android Keystore ou iOS Keychain, chamadas pela camada nativa, access token de 5 min, refresh rotativo, revogação pelo tenant e pela Central; app do cliente no realm `ziipsan-portal`, app de campo no realm `ziipsan` em dispositivo registrado, com bloqueio remoto; web continua no BFF (ADR-0006) |
| [0031](arquitetura/adr/ADR-0031-calculo-offline-e-nota-fiscal-na-emissao-em-campo.md) | Emissão em campo: pacote da rota com tarifas e versões de parâmetro; mesmo módulo Java de cálculo no servidor e no plugin Android, com vetores comuns no CI (JVM e emulador); conta com chave da NFAg-e em contingência se o leiaute permitir (a confirmar), sem certificado no aparelho; recálculo no servidor, fila de divergências com aprovação pela retificação (P2.20); idempotência pela chave da tarefa |
| [0032](arquitetura/adr/ADR-0032-material-da-os-com-alcada-e-baixa-automatica.md) | Material da OS: requisição com alçada por valor e por item (classe b), reserva na aprovação, entrega registrada, baixa automática da quantidade usada e devolução da sobra no encerramento, custo apropriado na OS; segregação de funções; saldo negativo permitido com alerta e regularização; no campo, baixa na sincronização por `task_id` e alçada a posteriori para itens controlados |

Os ADRs 0010, 0011, 0012, 0015, 0020 e 0022 foram ajustados em consequência das decisões da v2.4 (exceções de
infraestrutura, hospedagem, metas do nível Setor Público, consulta pós-contrato, severidades e fatura por e-mail); na
v2.5, os ADRs 0001, 0006, 0020, 0023, 0024, 0025, 0026 e 0028 receberam linha "Ajuste de 12/10/2026 (editais, v2.5)".
Em divergência com este plano, vale o ADR.

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
- **Calendário.** Trilha de editais no P4 (a partir de 02/08/2027), mirando licitações de 2028; o caminho crítico do P0
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

**Decisões do dono do produto sobre a trilha de editais (cartões de 12/10/2026, v2.5)**, aplicadas nos ADRs 0029 a
0032 e nos ajustes dos ADRs 0001, 0006, 0020, 0023 a 0026 e 0028:

- **Capacidade.** Contratar de 4 a 5 desenvolvedores a partir de agosto de 2027, depois do go-live; prontidão de POC
  em 30/06/2028 e disputa de licitações no 2º semestre de 2028 (ADR-0029). Custo a estimar até o M8 (§2.4).
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

### 2.3 G1–G9 reclassificadas e padrões do produto

Padrões definidos pelo dono do produto em 12/10/2026 estão na última coluna.

| # | Tema | Classe | Fase | Padrão do produto |
|---|---|---|---|---|
| G1 | Agência, norma, NR 11/13 | parâmetro | P1 | — |
| G2 | Tarifa social | parâmetro | P1 | piso federal travado no sistema após conferência no DOU; o tenant só concede benefício maior |
| G3 | NFAg-e | produto | P1–P2 | construir sobre o motor portado (ADR-0003) |
| G4 | Bancos, convênios, Pix, PSP | parâmetro, dentro do escopo suportado (§2.4) | P3 | — |
| G5 | Multa e juros | parâmetro | P2 | multa padrão 2%; acima de 2% só com aceite formal registrado |
| G6 | Retenção e prescrição | parâmetro | P2 | pisos fixos pelo Ziip SAN (logs de acesso 6 meses, Marco Civil; histórico 120 meses, NR 11); o tenant só amplia; eliminação no fim do contrato por procedimento do Ziip com certificado de descarte |
| G7, G8 | SINISA, SINIR | produto | P4, 2028 | assistidos (ADR-0004) |
| G9 | Corte | parâmetro | P3 | vedações da Lei 14.015 (sexta, fim de semana, feriado, véspera) para todos os motivos; exceção só emergência técnica com justificativa e foto; outra regra só com aceite formal |

Sem valor aceito e vigente, a operação falha com erro explícito; o tenant não entra em produção sem o conjunto
obrigatório aceito.

### 2.4 Pendências de decisão

**Em aberto**

| Decisão | Dono | Até | Bloqueia |
|---|---|---|---|
| Cliente piloto (público, em SC, contratado por dispensa ou CPSI, com cláusula de atestado: ADR-0023), acesso ao legado, acordo de tratamento de dados mínimo; existência e quantidade de optantes de débito automático e leiaute bancário em uso (ADR-0028, item f, ajuste v2.5) | Daniel | 13/11/2026 | P1.1, P1.2, go-live; primeiro atestado; P3.24 |
| G4 no produto: leiautes (CNAB 240 e, se o piloto tiver optantes, FEBRABAN 150 de débito automático) e PSPs suportados no go-live | Daniel | 29/01/2027 | P3.1, P3.2, P3.24 |
| NFAg-e: obrigatoriedade e data para o perfil do piloto, no DOU; manual e XSD oficiais, inclusive o regime de contingência (chave gerada fora do autorizador, assinatura no momento da emissão, prazo de transmissão), de que depende a forma da emissão em campo (ADR-0031, item 4) | Daniel | 27/11/2026 (contingência: antes da ED-19) | P2.6; ED-19 |
| Arredondamento (half-even no C#, `HALF_DOWN` no billing) e tributo no CHECK de total de `bills` (hoje tratado como retenção) | técnico | 29/01/2027 | P2.4, P2.5; vetores comuns do ADR-0031 |
| Custo e forma da contratação da equipe (ADR-0029, item 6): orçamento, regime (CLT ou PJ), número final (4 ou 5) com a velocidade medida | Daniel | 28/05/2027 (M8) | recrutamento; M11; M12 |
| Parceiro de implantação e treinamento presencial para editais (especialistas presenciais, técnico pós-implantação, instrutores), fora do ADR-0029 | Daniel | 28/05/2027 (M8) | ED-11; propostas com implantação presencial |
| Onda do almoxarifado (ED-58): fica na onda C, porque a OS da onda A (ED-36) registra os materiais sem efeito no estoque; antecipar para a onda A se o roteiro da P1.25 mostrar item crítico do SAMAE 2.17 que dependa da baixa de estoque, com +7,5 semanas na prontidão | Daniel | 29/01/2027 (fim do P1, com a P1.25) | ED-58; capacidade de junho de 2028 |

**Resolvidas pelo dono do produto em 12/10/2026 (cartões)**

| Pendência | Decisão | Onde foi aplicada |
|---|---|---|
| Equipe da trilha de editais (v2.5) | Contratar 4 a 5 desenvolvedores a partir de agosto de 2027, depois do go-live; prontidão de POC em 30/06/2028; licitações no 2º semestre de 2028; plantão da S1 entre os contratados até 31/01/2028. Antes prevista para 02/08/2027 | ADR-0029; ADR-0023 e ADR-0025 (ajustes); M8, M11, M12; ED-05, ED-11; riscos (§6) |
| Recorte do ERP (v2.5) | "Núcleo saneamento + integração": almoxarifado (materiais e estoque para a OS) e frota (veículos e equipes de campo) construídos; integração com Betha, IPM e Fiorilli; compras, RH e orçamento em 2029, sob demanda. Material da OS com alçada e baixa automática (ADR-0032). Antes prevista para 28/05/2027 | ADR-0028 item i (ajuste); ADR-0032; ED-58, ED-59, ED-62; ED-60 e ED-61 fora da trilha; faixa `V20_x` |
| Conectores Messenger, Instagram e Telegram (v2.5) | Incluídos na ED-42 como conectores oficiais no mesmo modelo do WhatsApp (API da Meta e Bot API do Telegram). Antes prevista para 02/08/2027 | ADR-0028 item c (ajuste); ED-42; faixa `V18_x` |
| Windows Server no SAMAE (v2.5) | Pacote de reversão em Docker sobre Windows Server, com contêineres Linux no WSL2, homologado uma vez; SaaS em Linux na AWS; URA pela integração mínima | ADR-0024 (ajuste); ED-04 |
| Autenticação nos apps (v2.5) | Navegador do sistema com PKCE, token no armazenamento seguro, revogação pelo tenant e pela Central; app do cliente no realm `ziipsan-portal`, app de campo no realm `ziipsan` com dispositivo registrado e bloqueio remoto | ADR-0030; ADR-0006 e ADR-0026 (ajustes); RNF-39; core `V11_1`; ED-18, ED-39 |
| Emissão em campo (v2.5) | Mesmo motor Java no servidor e no app, vetores comuns no CI, recálculo no servidor e fluxo de divergência; chave da NFAg-e em contingência se o leiaute permitir | ADR-0031; ADR-0026 (ajuste); FISCAL-PORTE-JAVA §3.4, §3.7 e §6; ED-19 |
| FEBRABAN 150 (v2.5) | Condicional ao piloto: levantamento de optantes na P0.30; com optantes, P3.24 como requisito do go-live; sem, onda A (ED-27) | ADR-0028 item f (ajuste); P0.30, P1.1, P3.24, ED-27 |
| Hospedagem (v2.4) | AWS `sa-east-1` gerenciada, com certificações do provedor exigidas em edital (ISO 9001, 27001, 27017, SOC 1, SOC 3); nenhum dado, réplica ou backup fora da região. Antes prevista para 30/10/2026; desbloqueia P0.27–P0.29 e o P1.2 com dado real | ADR-0011 (aceito); P0.26–P0.28; M2 |
| POC e editais (v2.4) | Passam a ser escopo, com trilha no P4 e licitações em 2028; revogada a linha "Fora do escopo: POC, editais" do diário §1 | ADR-0023 a ADR-0028; §2.2; P0.30, P0.44, P1.25, P2.20, P3.22, P3.23; trilha de editais no P4 |
| Valores HSL de Azul Água e Verde Água | Proposta de MARCA-E-TEMAS §4.2 aprovada, nos modos claro e escuro; ajustes finos permitidos depois de ver as telas, mantendo o teste de contraste | ADR-0017; MARCA-E-TEMAS §4.2; P0.37 |
| Duração da carência antes da eliminação de dados de tenant | 30 dias contados do fim do contrato, para o cliente baixar a exportação e contestar. O RNF-42 passa a exigir eliminação concluída e certificado de descarte em até 30 dias após o fim da carência, ou seja, até 60 dias do fim do contrato. Complementada na v2.3 pela regra da contestação (abaixo) | RNF-42; ADR-0015; ADR-0018; CENTRAL-ZIIP §6.3; PARAMETROS G6; P1.6, P3.16 |
| Quem executa suspensão, bloqueio e eliminação | Suspensão e bloqueio: `PLATFORM_OWNER` ou `PLATFORM_FINANCE`, este só por inadimplência contratual; eliminação: só `PLATFORM_OWNER`. Sempre uma pessoa com MFA, step-up, justificativa e auditoria; sempre manual, com aviso prévio; suspensão deixa o backoffice só leitura e mantém portal, Pix, retornos e 2ª via | ADR-0018; ADR-0019; CENTRAL-ZIIP §2 e §6.2; P0.43, P1.6, P2.12 |
| Exceção de emergência ao consentimento do acesso assistido | Admitida só em incidente de segurança declarado: somente leitura, só `PLATFORM_OWNER`, reautenticação com MFA no momento, aviso ao tenant em até 24 h, registro completo na auditoria e no registro do incidente | ADR-0020 (seção Exceção); CENTRAL-ZIIP §7; AJUDA-SUPORTE-SOBRE §5; P3.17 |
| Autenticação do consumidor no portal e vínculo com a matrícula | Conta própria no Keycloak, no realm `ziipsan-portal`, separado do realm do backoffice (MFA opcional; o identificador de login foi redefinido na v2.3, abaixo); vínculo comprovado pelo código de vinculação impresso na conta mais o CPF/CNPJ do titular; uma conta com vários imóveis; limite de taxa e resposta indistinta na vinculação | ADR-0022; ARQUITETURA-ALVO §1; P2.8, P3.3, P3.8, P3.20; tenant `V8_2` |
| Contas e NFAg-e anteriores ao go-live no portal | Na migração, importar os XML autorizados que o cliente tiver (sistema antigo ou emissor usado no intervalo) e gerar o PDF das contas antigas a partir dos dados importados; o portal mostra os 120 meses num lugar só, com a origem de cada documento | ADR-0022; FISCAL-PORTE-JAVA §3.8; nova P2.19 (`V14_3`); GL.1; P3.20 |
| Idioma dos identificadores | Tabelas, colunas, schemas, rotas e identificadores de código em inglês, como no código atual; textos ao usuário e documentação em português. Nomes renomeados em todos os documentos (`suporte` → `support`, `chamado` → `ticket`, `aceite_documento` → `document_acceptance`, `implantacao` → `deployment`, `/ajuda` → `/help`, `/suporte` → `/support`, `/sobre` → `/about`). A exceção para URN e códigos de problema foi retirada na v2.3 (abaixo) | §8 (Idioma); §3; ADR-0014, ADR-0020 a ADR-0022; AJUDA-SUPORTE-SOBRE; CENTRAL-ZIIP; FISCAL-PORTE-JAVA; ARQUITETURA-ALVO |
| Contestação durante a carência da eliminação de dados de tenant (v2.3) | Contestação registrada durante a carência de 30 dias **suspende** a eliminação; o Ziip responde em até 15 dias e, respondida a última contestação, a eliminação segue. O prazo de 30 dias para concluir a eliminação e emitir o certificado de descarte conta do fim da carência ou da resposta à última contestação, o que ocorrer depois. Prazo máximo total: **75 dias do fim do contrato** (30 de carência, até 15 de resposta e até 30 para concluir e certificar); sem contestação, continua em até 60 dias. Ordem judicial sempre suspende a eliminação; enquanto vigorar, o prazo de 75 dias não corre | RNF-42; ADR-0015 item 5; ADR-0018 item 2; CENTRAL-ZIIP §4, §6.3 e §11; PARAMETROS G6; P1.6, P3.16 |
| Login do consumidor final no portal (v2.3) | Identificador **CPF** (ou **CNPJ**, para pessoa jurídica) **ou e-mail**, com senha; MFA opcional mantido. Sem SMS e sem login por celular no go-live; celular e WhatsApp só como canal de notificação futuro (P4). Mantidos o realm `ziipsan-portal` e o vínculo com o imóvel pelo código impresso na conta mais o documento do titular. Documento como identificador, com cuidado LGPD: guardado cifrado e com índice cego, como no cadastro; nunca em URL nem em log; mensagens de erro genéricas e tentativas limitadas (anti-enumeração, coerente com o ADR-0008). Item técnico do P3: mapeamento de login por atributo (CPF/CNPJ) no Keycloak | ADR-0022 item 4; ARQUITETURA-ALVO §1; P3.3, P3.8; riscos (§6) |
| Idioma dos códigos de contrato da API (v2.3) | Prefixo `urn:ziipsan:problem:` e códigos de problema em inglês, em minúsculas com hífen (`versao-desatualizada` → `stale-version`, `mfa_obrigatorio` → `mfa-required`, `nao-encontrado` → `not-found`, `validacao` → `validation`, `confirmacao-necessaria` → `confirmation-required`; lista completa na P0.33), aplicados na renomeação do P0. Mensagens ao usuário continuam em português. O protocolo `SUP-AAAA-NNNNNN` permanece, por ser visível ao cliente | §8 (Idioma); §4 (nomes depois da renomeação); P0.33, P0.43, P1.22; ADR-0016, ADR-0018, ADR-0020; MARCA-E-TEMAS §5; AJUDA-SUPORTE-SOBRE; CENTRAL-ZIIP |

---

## 3. Mapa de versões Flyway

Duas histórias: **core** (`public`, uma vez por banco) e **tenant** (uma por schema). Existentes, conferidas em
`**/db/migration/**` em 12/10/2026: core `V1`, `V2`; tenant `V1_1`–`V1_4`, `V2_1`–`V2_10`, `V3_1`–`V3_3`, `V4_1`, `V5_1`,
`V5_2`, `V6_1`.

**Regra de ordem.**
- Core nunca usa `outOfOrder`: o número sai na ordem do merge; a tabela mostra a ordem esperada e é corrigida se ela mudar.
- Tenant: faixa por módulo com `outOfOrder(true)` até o fim do P3; até lá todo tenant é recriável (massa sintética ou
  reimportação do legado, que segue como fonte da verdade). Na P3.12, antes do paralelo, `outOfOrder(false)`; daí em
  diante a versão vem de uma sequência global a partir de `V100`, com o módulo no nome (`V100__dunning_actions.sql`), e o
  CI recusa versão menor ou igual à maior publicada. Reservas não usadas até lá são renumeradas aqui.
- Expand/contract: coluna só sai na release seguinte à que deixou de usá-la.
- Subversões do core: para não renumerar reservas, tabelas novas do core entram como subversão da reserva da mesma
  fase (core `V5_1`, `V9_1`…), que o Flyway ordena entre as inteiras. O prefixo "core" as distingue das versões de
  tenant de mesmo número (por exemplo, tenant `V9_1`, field), que ficam em outra tabela de histórico.
- A coluna "Módulo" com `platform` designa o módulo Maven `platform/*-platform` (história de tenant). Os schemas
  `platform` e `support` do banco do core (ADR-0018, ADR-0020) entram pelas versões do core. Só o banco principal, o
  do catálogo, os usa; nos clusters adicionais (ADR-0005) ficam vazios, salvo decisão diferente na P1.14.
- Renomeação (P0.33): muda o checksum das migrations existentes, porque os `GRANT` citam as roles; aceito porque não
  há banco com dado real, e banco e Keycloak de desenvolvimento são recriados. Depois dela, as migrations voltam a ser
  imutáveis.

**Mapa da v1, corrigido pelo que foi aplicado**

| Versão | Módulo | Conteúdo | Situação |
|---|---|---|---|
| core `V1`, `V2`; `V1_1`–`V1_4` | core, platform | catálogo e `audit_log`; redação; schema do tenant; auditoria endurecida | existentes |
| `V1_5` | platform | parâmetros do tenant; `holidays` sai do `V6_1` | reservada, P0 (era R4) |
| `V2_1`–`V2_10` | quality, losses, gis, mtr, lgpd | schemas; redação; cifra do MTR em `V2_8` (a v1 previa `V2_6`) | existentes |
| `V3_1`–`V3_3` | registry | schema; redação (a v1 reservava `V3_3` para o log de acesso, que vai para `V1_6`) | existentes |
| `V4_1`; `V5_1`, `V5_2`; `V6_1` | billing, metering, collection | schemas; `V5_2` é redação (tarefas de leitura vão para `V5_3`) | existentes |
| `V4_2` / `V4_3`, `V4_4` | billing | tarifa social / ciclo, modelos de conta | reservadas, P1 / P2 |
| `V4_5` | `sigsan-fiscal` | tributos com vigência (CBS/IBS) e motor fiscal: certificados, séries, documentos, eventos, lotes | reservada, P1 |
| `V6_2`; `V8_1`, `V9_1` | collection; portal, field | conciliação, Pix; protocolo; OS e bloqueios | reservadas, P3 |
| `V7_x`, `V10_x`, `V12_x`, `V13_x` | integration, dunning, GRS, GIS | SINISA, e-Sfinge e conectores de ERP (ED-33, ED-62); cobrança, negativação e protesto (P4.1, ED-30); DMR; GIS web, edição, manobras e acesso QGIS (ED-52 a ED-55) e incidente | P4 (trilha de editais) e 2028, sequência global |
| `V11_x` | events | outbox por tenant | substituída por core `V8` (ADR-0014: uma outbox por banco) |

**Reservas novas**

| Versão | Módulo | Conteúdo | Tarefa |
|---|---|---|---|
| core `V3` | core | `parameter_definitions`, `parameter_legal_limits` (catálogo global do produto, só leitura para a aplicação) | P0.19 |
| core `V4` | core | `tenant_catalog.cluster_id`, `isolation_mode`; revisão do `schema_name UNIQUE` (`core/V1:21`); `tenant_schema_version`; `sigsan_app` só com SELECT | P0.17, P0.21, P1.14 |
| core `V5` | core | sessões do BFF | P0.14 |
| core `V5_1` | core | schema `platform`; `platform.deployment` (registro de implantação), gravada pelo `migrate` (ADR-0021) | P0.42 |
| core `V6` | core | `tenant_key` (DEK por tenant). Era "core `V3`" no R1 da v1: o R1 depende da hospedagem e o core não aceita fora de ordem | P0.28 |
| core `V7` | core | `audit_log` particionado por mês, PK `(created_at, id)` com UUIDv7, retenção | P1.7 |
| core `V8` | core | `outbox` com `tenant_id`, RLS e partição diária | P1.9 |
| core `V9` | core | console v0 no schema `platform`: `plan`, `tenant_subscription`, `feature_flag`, `onboarding_step`, `tenant_lifecycle`, `audit_event`. A impersonação sai: o acesso assistido do ADR-0020 vai para o core `V10_3` | P1.6 |
| core `V9_1` | core | Central v0: `platform.metric_definition`, `metric_point` (partição diária), `metric_daily`, `alert_rule`, `alert_event`, `config_item`; role `ziipsan_central`; funções `platform_stats.*` | P1.17 |
| core `V9_2` | core | `public.tenant_assets` (logo do tenant, com estado da varredura); função `SECURITY DEFINER` da marca pré-login, restrita às chaves `branding.*` de `tenant_settings` | P1.19 |
| core `V9_3` | core | schema `support`; conteúdo de ajuda: `article`, `article_version`, `article_feedback`, `tour_completion`, `error_explanation` | P1.21 |
| core `V9_4` | core | `support.ticket`, `ticket_message`, `ticket_attachment`, `ticket_event`; sequência do protocolo `SUP-AAAA-NNNNNN` | P1.23 |
| core `V9_5` | core | `platform.document_acceptance`; `support.release_note_view` | P1.24 |
| core `V10` | core | `job_runs` e travas por (tenant, ciclo) | P2.1 |
| core `V10_1` | core | contrato SaaS no schema `platform`: contrato (com a política de SLA), aditivo, empenho, reajuste, fatura, pagamento, referências aos documentos da fatura e da NFS-e | P2.12–P2.14 |
| core `V10_2` | core | `support.sla_policy` (com as cinco severidades do nível Setor Público, ADR-0025: prazo de solução, aderência e multa; matriz impacto × criticidade), `csat_response`; prazos e pausa no chamado | P2.15 |
| core `V10_3` | core | `support.access_consent`, `assisted_access_session` (tipo `CONSENTED` ou `EMERGENCY`; na emergência, referência ao incidente no lugar do consentimento) | P3.17 |
| core `V10_4` | core | `support.status_component`, `status_incident`, `status_update`, `maintenance_window` | P3.18 |
| core `V10_5` | core | período de consulta somente leitura pós-contrato no ciclo de vida do tenant (início, fim, usuários), antes da carência (ADR-0025) | P3.16 |
| core `V11` | core | instância dedicada no catálogo (modo de isolamento e ambiente, ADR-0024); configuração do app do cliente por tenant (identificador do app, contas de loja, versão publicada, ADR-0026) | ED-04, ED-39 |
| core `V11_1` | core | `platform.app_device`: dispositivos do app de campo por tenant (estado `ACTIVE`, `BLOCKED` ou `RETIRED`, aprovação, atestação, último contato), sem dado pessoal além do `sub` (ADR-0030) | ED-18 |
| `V1_5` | platform | `parameter_versions` (vigência com EXCLUDE, quem propõe ≠ quem aprova), `parameter_acceptances`, `regulatory_profiles`, `holidays` | P0.19 |
| `V1_6` | platform | `personal_data_access_log`; cota de busca por documento | P0.11 |
| `V1_7` | platform | `user_preferences` (modo e padrão de tema do usuário) | P1.18 |
| `V3_4` | registry | GIN trigram sobre `unaccent(lower(name))` | P0.22 |
| `V3_5` | registry | sequência de matrícula por tenant | P1.4 |
| `V3_6` | registry | R2: `customer_types`, `property_profiles`, endereço, `block_faces`, rascunhos | P1.3 |
| `V4_6` | billing | partição de `bills`/`bill_items`; auditoria por lote; CHECK de total com tributo | P2.3, P2.5 |
| `V4_7` | `sigsan-fiscal` | NFAg-e e NFS-e avulsa: leiaute, contingência, inutilização | P2.6, P3.6 |
| `V4_8` | billing | ajustes de conta: versão da conta retificada, operação, motivo (catálogo), anotação, aprovação, vínculo com o protocolo quando houver | P2.20 |
| `V15_1` | reporting | definições, execuções e agendamentos de relatório; views de relatório por domínio | P3.22 |
| `V5_3` / `V5_4` | metering | tarefas e importação de leitura / partição de `meter_readings`, chave de idempotência | P2.7 / P2.3 |
| `V6_3` | collection | partição de `payments`; `pix_inbox` | P3.2 |
| `V6_4` | collection | débito automático FEBRABAN 150 legado: autorizações migradas, remessas e retornos (só se houver optantes) | P3.24 |
| `V8_2` | portal | vínculo da conta do consumidor (`sub` do realm `ziipsan-portal`) com a matrícula: método de comprovação, data, revogação; hash do código de vinculação por imóvel; tentativas de vinculação | P3.20 |
| `V14_1`, `V14_2` | importação de legado | lotes, staging, conciliação; histórico de 120 meses | P1.2, P2.10 |
| `V14_3` | importação de legado | lotes de XML autorizados anteriores ao go-live, resultado da validação (assinatura, protocolo, emitente, duplicidade) e origem do documento | P2.19 |

**Faixas dos módulos novos (v2.4).** A trilha de editais começa depois da P3.12; suas migrations de tenant usam a
sequência global a partir de `V100`, com o módulo no nome. A faixa abaixo identifica o módulo (prefixo do nome do
arquivo, como `V1nn__accounting_chart_of_accounts.sql`, e catálogo de módulos) e só vale como número de versão se
alguma tabela precisar entrar antes da P3.12. `V4_8` e `V15_1` entram como faixa se a P2.20 e a P3.22 forem
concluídas antes da P3.12; senão, pela sequência global. Os metadados do Superset ficam em banco próprio, fora do
Flyway do produto; as roles e views de BI por tenant (ADR-0027) entram pela faixa `reporting`.

| Faixa | Módulo | Conteúdo | Entregas |
|---|---|---|---|
| `V15_x` | `reporting` (relatórios e BI) | definições e agendamentos de relatório, views por domínio, roles de BI por tenant e mapeamento role → tenant, catálogo de indicadores, metas e fechamentos | P3.22; ED-46 a ED-49 |
| `V16_x` | `accounting` (contabilidade) | plano de contas, rubricas, regras evento → lançamento, lançamentos, fechamento congelado, exportação diária | ED-32 (rubricas orçamentárias da ED-61 só em 2029) |
| `V17_x` | `active_debt` (dívida ativa) | notificação, inscrição, livro, CDA, parcelamento da dívida ativa, saldo mensal | ED-31 |
| `V18_x` | `omnichannel` | canais (WhatsApp, Messenger, Instagram, Telegram, e-mail, chat do portal), conversas, mensagens, filas, transbordo, sessões do bot, consentimentos por canal, senhas e painel de chamada | ED-40 a ED-43 |
| `V19_x` | `quality` (qualidade e laboratório; amplia `V2_1`, `V2_9`) | catálogo normativo versionado, plano de amostragem, cadeia de custódia, boletins, conformidade, laboratório e calibração (17025), exportação SISAGUA | ED-50, ED-51 |
| `V20_x` | `erp` (`inventory`, `fleet`) | almoxarifado e material da OS (`approval_rule`, `material_requisition`, `requisition_item`, `stock_reservation`, `stock_movement`, `approval_decision`, ADR-0032); veículos, abastecimento, manutenção e equipes de campo. `purchasing`, `hr` e `budget` só em 2029, sob demanda (ADR-0028, item i, ajuste v2.5) | ED-58, ED-59 |

`current_tenant_id()` como SQL STABLE (17:M10) não é migration: a função é de `sigsan_owner` e nasce no init de
segurança (`infra/postgres/init/sql/security.sql`); o migrador não pode redefini-la, por desenho. Nos bancos existentes,
script de operação como `postgres`.

---

## 4. Fases

Tamanho: **P** até 2 dias, **M** até 1 semana, **G** mais de 1 semana, para uma pessoa com agentes. Toda tarefa termina
com testes verdes, registro no diário e push.

**Nomes depois da renomeação.** A partir da P0.33, os caminhos, módulos, pacotes, propriedades, roles e realm citados
neste plano com `sigsan` (`commercial/sigsan-fiscal`, `sigsan_worker`, `sigsan.fiscal.*`, realm `sigsan`) leem-se com
`ziipsan`. O plano não é reescrito por isso; tarefas novas já usam o nome final (`ziipsan_central`,
`urn:ziipsan:problem:`, códigos de problema em inglês da tabela da P0.33). Commits e diário anteriores valem até a tag
`pre-rename`.

### P0 — Plataforma operável · 13/10–27/11/2026

Objetivo: ambiente fora do Drive, código renomeado para `ziipsan`, artefato implantável com versão gerada, marca e
temas, altos e médios de segurança, parâmetros do tenant e, com a hospedagem decidida (ADR-0011, AWS `sa-east-1`),
homologação e produção com PITR e R1; kit de habilitação iniciado.

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P0.1 | Desenvolvimento em disco local; GitHub como fonte da verdade | ADR-0009, 17:M15 | M | — | build e `ci.sh` verdes num clone fora do Drive, sem robocopy nem PowerShell; `.env` fora do Drive |
| P0.2 | Push automático ao fim de etapa | ADR-0009 | P | P0.1 | com teste vermelho nada é publicado |
| P0.33 | Renomeação `sigsan` → `ziipsan` como tarefa única, logo após a P0.1 e antes das demais tarefas de código do P0, na ordem de MARCA-E-TEMAS §5.2; inclui os códigos de contrato da API em inglês: prefixo `urn:sigsan:problema:` → `urn:ziipsan:problem:` e códigos da tabela abaixo | ADR-0016 | M | P0.1 | tag `pre-rename`, sem branch aberto; `mvn clean verify`, testes do frontend, `ci.sh` e `dev-up.sh` do zero verdes; login ponta a ponta no realm `ziipsan`; teste de vetor fixo de `ziipsan/enc/v1` e `ziipsan/bidx/v1`, congelados daí em diante; todo problem+json com `type` `urn:ziipsan:problem:<código>` e código da tabela abaixo (teste de contrato); `title`, `detail` e mensagens de campo seguem em português; backend, `frontend/src/services/registry/errors.ts` e testes (Java e frontend) atualizados no mesmo commit; busca residual sem `sigsan` fora da lista permitida e sem `problema:` nem código antigo da tabela |
| P0.3 | CI no GitHub Actions | 17:X1, 17:S6 | M | P0.1 | Java com Postgres de serviço e frontend; `npm audit` alto e gitleaks bloqueiam; `main` protegida |
| P0.4 | PostgreSQL 17 + PostGIS 3.5 | ADR-0013, 17:X2 | M | — | dev, integração e CI na mesma versão; suíte verde |
| P0.5 | Dockerfile multi-stage não root; uma imagem para `api`, `worker`, `migrate`; `config.json` do frontend em execução | 17:X1, 17:B7 | M | P0.3 | UID ≠ 0, sem segredo, SBOM, tag fixa; trocar o emissor OIDC não exige novo build |
| P0.6 | Migração só no job `migrate` | 17:S2, D10 | P | P0.5 | perfil `prod` sem `migratorDataSource` (teste de contexto) |
| P0.7 | Congelar infra parada | ADR-0010, 17:M13, 17:O2, 17:B8 | P | — | `docker compose up` sobe só PostgreSQL e Keycloak; demais em `profiles:` e 127.0.0.1; k8s e Terraform arquivados |
| P0.8 | Observabilidade mínima | 17:O1 | M | P0.5 | métricas HTTP e Hikari, traces com `tenant_id`, `pg_stat_statements`, logs JSON com `X-Request-ID` |
| P0.9 | Pools e timeouts | 17:D3 | P | — | Hikari dimensionado; `statement_timeout` e `idle_in_transaction_session_timeout` na role; consulta longa cancelada (teste) |
| P0.10 | API versionada | 17:M12 | P | — | `/api/v1`; OpenAPI gerado e conferido no CI |
| P0.11 | Máscara com teto; busca por documento com papel, registro e cota; leituras registradas | 17:S1, 17:M6, ADR-0008 | M | P0.19 | teto acima recusado; sem papel → 403; buscas e leituras em `V1_6`; cota excedida → 429 e alerta |
| P0.12 | Recalcular em lote as máscaras já gravadas | ADR-0008 | P | P0.11 | job decifra e regrava `*_masked` (`CustomerDocumentService.java:72`, `MtrDocumentProtection.java:74,99`); nenhuma máscara acima do teto |
| P0.13 | Gestão de usuários por tenant | 17:S3, 17:M5, 17:B3 | G | — | ADMIN de A não altera usuário de B; `tenant_id` só pela API própria; papel de plataforma auditado; realm por tenant e hash duplicado em `users` removidos |
| P0.14 | BFF com cookie HttpOnly no processo `api`, sessão no PostgreSQL | 17:S5, ADR-0006 | G | P0.5 | core `V5`; nenhum token no navegador (teste E2E); cookie `__Host-` HttpOnly, Secure, SameSite; CSRF; refresh com rotação; réplicas sem afinidade |
| P0.15 | MFA | ADR-0007 (prevalece sobre 17:S4) | M | P0.13 | plataforma sempre com segundo fator; política por tenant aplicada no login |
| P0.16 | Superfície web | 17:S5 | M | P0.14 | CSP estrita; HSTS atrás de proxy; rate limit por IP, usuário e tenant (429); `@Size` (400); limite de corpo (413) |
| P0.17 | Médios de segurança | 17:M1–M4, 17:B4, 17:S6 | P | P0.3 | um teste por item: cache com tenant do contexto; `dev-env` só em perfis listados; `sigsan_app` sem escrita no catálogo; schema existente recusado; `/api/me` sem schema; `maplibre-gl` 6 e `react-router` 7 |
| P0.18 | `current_tenant_id()` como SQL STABLE | 17:M10 | P | P0.4 | `DatabaseHardeningTest` verde; sem subtransação por linha |
| P0.19 | Parâmetros do tenant | ADR-0002 | G | P0.4 | core `V3` + `V1_5`; fora do limite recusado; vigência sobreposta recusada pelo banco; sem valor aceito → erro explícito |
| P0.20 | Limites, sugeridos e pisos do §2.3 | ADR-0002 | M | P0.19 | cada limite com norma e teste; multa > 2% só com aceite (ajustar CHECK de `V6_1` e `LegalLimits`); piso de retenção não reduzível |
| P0.21 | Shard no catálogo; teste com 300 schemas | ADR-0005, 17:D2 | G | P0.4 | core `V4`; tempo de migração, catálogo, vacuum e latência medidos; teto por cluster registrado |
| P0.22 | Busca e listas | 17:D4, 17:D5 | M | — | `V3_4`; busca "contém" usa o índice; listas com número constante de consultas; keyset nas grandes |
| P0.23 | Carga inicial | 17:O4, ADR-0012 | M | P0.8 | k6 ou Gatling noturno, 20 mil ligações sintéticas; p95 contra o RNF; regressão > 15% no p95 falha (RNF-70) |
| P0.24 | Modelagem de ameaças (STRIDE) | 17 §7 | P | P0.14 | API, BFF, worker, webhook e importador com controle ou tarefa |
| P0.25 | Dívidas da execução; SINIR em modo assistido | diário §18, ADR-0004 | P | — | sem `ObjectMapper` do Jackson 2 em collection; `SinirIntegrationService` removido ou desligado sem caminho de chamada; `grantConsent` sem versão manual |
| P0.26 | Decisão de hospedagem e tabela de custo | ADR-0011, ED-03 | P | — | decisão cumprida em 12/10/2026 (ADR-0011 aceito: AWS `sa-east-1` gerenciada); falta: tabela de custo mensal para a capacidade da ARQUITETURA-ALVO §6, para o piloto e para o dimensionamento mínimo dos primeiros tenants; versões de PostGIS, `pg_trgm`, `unaccent` e `pg_partman` conferidas no RDS for PostgreSQL 17; relatórios de certificação (ISO 9001, 27001, 27017, SOC 1, SOC 3) com escopo em `sa-east-1` baixados do AWS Artifact para o kit (P0.44) |
| P0.27 | Homologação e produção com IaC na AWS `sa-east-1` | 17:X1, 17:O2, ADR-0011, ADR-0024, ED-03, ED-04 | G | P0.26 | sem segredo no state; deploy pelo pipeline; produção com aprovação manual; rollback exercitado; IaC parametrizada de modo que uma instância dedicada ou o ambiente de demonstração (P3.23) sejam mais um ambiente; detecção de ameaças, inspeção de vulnerabilidades e registro de configuração do provedor ativos (TR-091, TR-092, TR-096) |
| P0.28 | HA, backup e PITR; R1 (KMS e chaves) | 17:X2, R1, ADR-0011 | G | P0.26 | RDS PostgreSQL 17 Multi-AZ com réplica de leitura; PITR com RPO ≤ 15 min e RTO ≤ 4 h medidos; cópia de snapshots em outra conta na mesma região, com política de expiração; nenhum backup nem réplica fora de `sa-east-1`; core `V6`; `TenantKeyProviderContract` verde sobre o KMS; rotação; forma de atender ao backup da KEK registrada; contexto de tenant local à transação e PgBouncer em modo transaction |
| P0.29 | Keycloak de produção | 17:O3 | M | P0.27 | `start --optimized`, TLS, `sslRequired=all`, 2 réplicas |
| P0.30 | Escolha do piloto | ADR-0001, ADR-0023 | P | — | termo de intenção, acesso ao legado, acordo de tratamento de dados e critério de divergência do go-live; critério de editais (ADR-0023, item 3): prestadora de água pública em Santa Catarina, contratação por dispensa (Lei 14.133, art. 75, II) ou CPSI (LC 182/2021) e cláusula de emissão de atestado após os ciclos em produção, com ligações e e-Sfinge registrados; escolha fora desse critério só com justificativa no diário; levantamento de optantes de débito automático (quantidade e leiaute bancário em uso) registrado: com optantes, a parte FEBRABAN 150 da ED-27 entra no P3 (P3.24) como requisito do go-live; sem optantes, fica na onda A (ADR-0028, item f, ajuste v2.5) |
| P0.31 | Comunicação aos clientes-alvo | G0 (v1) | P | — | até 23/10/2026: o que entra em cada fase e o que segue no legado |
| P0.32 | Esqueleto fiscal | ADR-0003 | M | — | `commercial/sigsan-fiscal`; vetores do C# passam no Java; sem `production-allowed` não há `tpAmb=1` |
| P0.34 | Pacote de marca | ADR-0016 | M | P0.33, P0.5 | `branding/ziipsan` validado por schema no CI (SVG com `script` recusado); `BrandProvider`; plugin do Vite; `GET /api/v1/public/brand`; `check-brand-literals.sh` no `ci.sh` falha com literal de marca fora do pacote |
| P0.35 | Tema de login e de e-mail do Keycloak gerado do pacote | ADR-0016 | P | P0.34 | realm com `loginTheme`, `emailTheme` e `displayName` do pacote; trocar `ZIIPSAN_BRAND` muda login e e-mail sem alterar código; o tema entra na imagem de produção da P0.29 |
| P0.36 | Temas claro, escuro e do sistema | ADR-0017 | M | P0.16 | tokens semânticos; registro com Azul Água e Verde Água; lint recusa cor literal (`Badge.tsx`, `Alert.tsx` migrados); script inline aplica o tema antes da primeira pintura, com hash na CSP conferido pelo CI; contraste WCAG 2.1 AA por padrão × modo (Vitest e Playwright com axe) bloqueia o merge |
| P0.37 | Valores dos padrões aplicados e ajuste fino | ADR-0017 | P | P0.36 | valores de MARCA-E-TEMAS §4.2, aprovados pelo dono em 12/10/2026, no registro de padrões; teste de contraste verde com eles; ajuste fino feito depois de o dono ver as telas só entra com o teste verde e o valor novo registrado no §4.2 |
| P0.38 | Keycloak Organizations para a marca no login | ADR-0016 | P | P0.13 | decisão registrada em MARCA-E-TEMAS §3.3 (subdomínio, e-mail primeiro ou caminho), com prova de conceito |
| P0.39 | Versão gerada pela tag | ADR-0021 | M | P0.3, P0.5 | `-Drevision` no Maven; mesma versão, commit e data no Vite; `build-info`, `git.properties`, `version.json`; nenhuma versão escrita à mão; dois builds do mesmo commit dão a mesma versão; `Ziip-Version` e `Ziip-Min-Client-Version` em toda resposta; SPA antigo mostra aviso de recarga (teste E2E) |
| P0.40 | Licenças de terceiros pelo SBOM | ADR-0021, RNF-35 | P | P0.5 | `third-party-licenses.json` gerado do CycloneDX (Maven e npm); licença fora da lista permitida bloqueia o CI |
| P0.41 | Changelog do cliente | ADR-0021 | P | P0.3 | esquema de `content/release-notes/<version>.yaml` validado; commit marcado como segurança, legislação ou parâmetro regulatório sem item bloqueia a release (teste do CI) |
| P0.42 | Registro de implantação e Sobre mínimo | ADR-0021 | P | P0.6, P0.39 | core `V5_1`; o `migrate` grava `platform.deployment`; `GET /api/v1/about` devolve versão, commit, ambiente e data gravada |
| P0.43 | Papéis de plataforma | ADR-0018, ADR-0007 | P | P0.13, P0.15 | `PLATFORM_OWNER`, `PLATFORM_OPS`, `PLATFORM_SUPPORT` e `PLATFORM_FINANCE` no realm; matriz de permissões de CENTRAL-ZIIP §2 com teste por papel; rota `/api/v1/platform/*` sem `amr` → 403 `mfa-required`; token com `tenant_id` recusado em rota de plataforma |
| P0.44 | Kit de habilitação, INPI e escrituração, fora do caminho crítico | ADR-0023 item 6, ED-02 | P | P0.26 | empresa licitante definida, com CNAE compatível; contador contratado e escrituração dos exercícios de 2026 e 2027 em curso (balanço de dois exercícios exigido nas licitações de 2028); pedido de registro do programa no INPI protocolado; e-CNPJ; lista de certidões do 18h §2.2 com data de validade e rotina de renovação; relatório de certificações do provedor arquivado; cláusula-padrão de licença das customizações (sem cessão de direitos) redigida |

P0.33 vem logo depois da P0.1: as demais tarefas de código do P0 dependem dela (dependência implícita, não repetida
na coluna). Com o ADR-0011 aceito em 12/10/2026, a P0.26 fica reduzida à tabela de custo e às conferências, que
podem correr junto da P0.27; P0.27–P0.29 não esperam mais decisão. A regra pura do R3 pode começar no P0, em paralelo.

**Códigos de problema na P0.33** (decisão do dono do produto, 12/10/2026, §2.4). Lista dos códigos emitidos hoje,
conferida em `ApiException.java`, `GlobalExceptionHandler.java`, `ProblemResponses.java`, `JwtTenantFilter.java`,
`SecurityConfig.java` e nos serviços do cadastro, mais o código previsto na P0.43. Os códigos de campo e de aviso
(`errors[].code`, `warnings[].code`, como `REQUIRED` e `DOCUMENT_IN_USE`) já estão em inglês e não mudam. Código novo
nasce em inglês, em minúsculas com hífen, e entra no catálogo de erros explicados (P1.22).

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
| `busca-na-url` | `search-in-url` | 400 |
| `filtro-indisponivel`, `filtro-invalido` | `filter-unavailable`, `invalid-filter` | 400 |
| `ordenacao-invalida`, `pagina-invalida`, `inscricao-invalida` | `invalid-sort`, `invalid-page`, `invalid-inscription` | 400 |
| `validacao` | `validation` | 422 |
| `confirmacao-necessaria` | `confirmation-required` | 422 |
| `erro-interno` | `internal-error` | 500 |

### P1 — Migração e satélite · 30/11/2026–29/01/2027

Objetivo: cadastro do piloto importado e conciliado; R2, R3, console e Central Ziip v0, outbox, marca do tenant,
ajuda e chamados v0; roteiros internos de POC e primeiro ensaio registrado.

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P1.1 | Discovery do legado | 17:X5, ADR-0028 | M | P0.30 | dicionário, mapeamento campo a campo e volumes aprovados pelo piloto; optantes de débito automático e leiaute bancário conferidos no legado contra o levantamento da P0.30, com as autorizações a migrar listadas (base da P3.24, se houver optantes) |
| P1.2 | Importador v0 do cadastro com conciliação | 17:X5 | G | P1.1, P1.4, P0.28 | `V14_1`; reimportação não duplica; totais por setor e rota iguais ao legado; dado real só em produção |
| P1.3 | R2: cadastro completo | R2 (v1) | G | — | `V3_6`; cliente cadastrado pela tela; rascunho e `validate-step`; CEP; CSV |
| P1.4 | Matrícula por sequência | 17:M9 | P | — | `V3_5`; dois lotes paralelos sem espera, sem lacuna nem duplicata |
| P1.5 | R3: tarifa social satélite + N4 | R3 (v1), G1, G2 | M | P0.20 | `V4_2`; casos da v1 (T-03 → 102,00 e 87,00; CadÚnico de 25 meses inelegível; `GRACE_3M`); benefício abaixo do piso federal recusado; em homologação até 11/12/2026 |
| P1.6 | Console da plataforma v0 | 17:P1, ADR-0002, ADR-0018 | G | P0.13, P0.19, P0.43, P1.9 | core `V9`; assistente de implantação; produção bloqueada sem parâmetros aceitos; planos, cotas, flags; suspensão manual com aviso prévio, por uma pessoa com MFA, step-up e justificativa (`PLATFORM_OWNER`, ou `PLATFORM_FINANCE` só com motivo inadimplência contratual), deixando o backoffice só leitura e o portal, o Pix, os retornos e a 2ª via funcionando (teste); `PLATFORM_FINANCE` com outro motivo ou pedindo eliminação → 403 (teste); exportação e eliminação com carência de 30 dias e certificado de descarte, eliminação só por `PLATFORM_OWNER`; contestação registrada na carência e ordem judicial suspendem a eliminação (estado e datas em `tenant_lifecycle`); toda ação auditada em `platform.audit_event`. A impersonação sai: acesso assistido na P3.17 (ADR-0020) |
| P1.7 | Partição do `audit_log` | 17:X3, 17:M7 | G | P0.4 | core `V7`; UUIDv7; retenção com piso; consulta por período poda partições |
| P1.8 | Porte fiscal: NFS-e Nacional, cofre de certificado, numeração | ADR-0003 | G | P0.32, P0.28 | `V4_5`; emissão, cancelamento e substituição na produção restrita; sem número repetido ou perdido |
| P1.9 | Outbox e `worker` básico | ADR-0014, 17:X4 | G | P0.5 | core `V8`; mensagem na transação do negócio; `SKIP LOCKED` com prazo de posse; role `sigsan_worker`; usado no provisionamento e no console |
| P1.10 | Corpus de XML de referência nos dois CIs | ADR-0003 | M | P0.32 | o mesmo corpus roda no Ziip Cloud e no Ziip SAN |
| P1.11 | `Idempotency-Key` e ETag/If-Match | 17:M12 | M | P0.10 | POST repetido devolve a mesma resposta; sem `If-Match` → 428; versão velha → 412 |
| P1.12 | Frontend robusto | 17:F1, 17:M14, 17:B9–B11 | M | P0.14 | Error Boundary; code splitting; ações por papel; filtro preservado; tabelas no celular; menu modal (axe limpo); retry por tipo; Web Vitals |
| P1.13 | Fronteiras de módulos | 17:M11 | M | — | ArchUnit ou Spring Modulith no CI; duplicatas billing/metering removidas |
| P1.14 | Rollout de migrations por tenant | 17:D1, ADR-0005 | M | P0.6, P0.21 | status por tenant; falha em quarentena sem parar os demais; paralelismo limitado; canário |
| P1.15 | Baixos de dado pessoal | 17:B5, 17:B6 | P | — | VIEWER não vê excluídos; `consentId` conferido contra o titular |
| P1.16 | Arredondamento e tributo na conta | FISCAL-PORTE-JAVA | P | P0.32 | regra única registrada em ADR; vetores C# e Java iguais; CHECK de `bills` definido para `V4_6` |
| P1.17 | Central Ziip v0, junto do console v0 | ADR-0018 | G | P1.6, P1.9, P0.8 | core `V9_1`; coletor de 5 min com trava e watchdog; abas Visão geral, Plataforma, Segurança e Alertas; alerta abre e fecha sem duplicar; `SELECT` de `ziipsan_central` em tabela de tenant falha no teste; cada função `platform_stats.*` com teste de ausência de coluna pessoal; API de métricas `v1` com catálogo e teste de contrato |
| P1.18 | Preferência de tema no servidor; padrão e restrição do tenant | ADR-0017 | M | P0.36, P1.6 | tenant `V1_7`; `GET/PUT /api/v1/me/preferences`; precedência usuário → tenant → Azul Água no modo do sistema (teste); padrão retirado da lista do tenant cai no padrão dele sem apagar a escolha |
| P1.19 | Marca do tenant | ADR-0016 | M | P0.34, P1.6, P1.20 | core `V9_2`; chaves `branding.*`; logo PNG/WebP reencodado e varrido (EICAR e arquivo poliglota recusados); `GET /api/v1/public/tenant-brand` pelo subdomínio, com slug desconhecido devolvendo a marca do produto e o mesmo status; regra de contraste da cor aplicada ao salvar |
| P1.20 | Pipeline de arquivos enviados com ClamAV | ADR-0010 (Exceções), ADR-0020 | M | P1.9 | tipo pelo conteúdo; arquivo em quarentena até a varredura; EICAR bloqueado e com alerta; PDF limpo liberado; idade das assinaturas monitorada; varredor atrás de interface substituível pelo serviço da hospedagem |
| P1.21 | Ajuda: conteúdo, publicação e ajuda contextual | ADR-0020 | M | P0.3, P0.6 | core `V9_3`; `content/help` validado no CI (cabeçalho, links, rotas, alvos `data-tour` existentes); publicação pelo `migrate` sem nova imagem; busca em português; `useContextualHelp` e `<Hint>` |
| P1.22 | Catálogo de erros explicados | ADR-0020 | P | P1.21 | teste falha se um código `urn:ziipsan:problem:*` não tiver explicação |
| P1.23 | Chamados v0 e fila na Central | ADR-0020 | G | P1.9, P1.17, P1.20 | core `V9_4`; protocolo `SUP-AAAA-NNNNNN`; conversa, estados e notificações pela outbox; usuário de outro tenant recebe 404; e-mail sem conteúdo nem anexo; formulário orienta a não anexar dado pessoal e marca `contains_personal_data` |
| P1.24 | Sobre completo e novidades por usuário | ADR-0021 | M | P0.34, P0.41, P0.42 | core `V9_5`; Sobre com marca, versão, documentos com versão vigente e aceita (`platform.document_acceptance`) e licenças; novidades uma vez por versão por usuário, com estado no servidor |
| P1.25 | Roteiros internos de POC, matriz viva e modelos jurídicos | ADR-0023, ADR-0028, ED-01 | P | — | roteiro da VISAN (81 itens, forma de demonstração de cada um) e do SAMAE (lista crítica com subitens e itens sorteáveis) versionados no repositório, cada item ligado a requisito, ED, tarefa e fase; matriz viva criada a partir de 18d–18h no formato do consolidado 18 §7, conferida no CI; modelos de pedido de esclarecimento e de impugnação para os itens do consolidado 18 §6 (no mínimo TR-286, TR-415, SG-398 e TR-494); licença de uso comercial do ODA File Converter conferida; primeiro ensaio (fim do P1) registrado no diário, sem meta |

### P2 — Ciclo de faturamento · 01/02–26/03/2027

Objetivo: ciclo completo em homologação com NFAg-e, dentro das metas de carga, com retificação e manutenção de conta;
cobrança por contrato e área do tenant prontas antes do primeiro contrato.

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P2.1 | Fundação de lote: Spring Batch por tenant e rota, `job_runs`, trava (tenant, ciclo), fila justa | 17:X4, ADR-0014 | G | P1.9 | core `V10`; reinício continua só rotas pendentes; disparo duplo não corre; tenant grande não atrasa o pequeno além do RNF |
| P2.2 | GUC por transação; pools por papel | 17:D3, ADR-0005 | M | P2.1 | `set_config(..., true)`; isolamento com PgBouncer em modo transaction; pools API, lote e ingestão; base para schema compartilhado |
| P2.3 | Tabelas de fato particionadas; auditoria por lote | 17:M8, 17:X3, 17:B1, 17:B2 | G | P1.7 | `V4_6`, `V5_4`; 100 mil contas auditadas por `run_id`; leitura reenviada não duplica |
| P2.4 | R4a: CBS/IBS com vigência | R4a (v1) | P | P1.8, P1.16 | alíquota ausente → erro; vigências sem sobreposição |
| P2.5 | R4: medição, ciclo, conta, N6 | R4 (v1), G5, G6 | G | P2.1, P0.20 | `V4_3`, `V4_4`; casos da v1 (1000→1027 com E-01 → 201,60; ciclo repetido sem segunda conta); conta grava as versões de parâmetro |
| P2.6 | NFAg-e (modelo 75) sobre o motor portado | ADR-0003, 17:P4 | G | P1.8, P2.5 | `V4_7`; autorizada na homologação da SEFAZ do piloto; ciclo reexecutado sem segunda NFAg-e |
| P2.7 | Leitura terceirizada | 17:P3 | M | P2.5 | `V5_3`; arquivo do fornecedor do piloto; reenvio não duplica |
| P2.8 | Arquivo para gráfica | 17:P4 | M | P2.5 | leiaute aprovado com amostra impressa; conta traz o código de vinculação ao portal do imóvel (ADR-0022), que não aparece em log nem em arquivo intermediário fora do da gráfica |
| P2.9 | Carga com 300 mil ligações | 17:O4, ADR-0012 | M | P2.5 | 100 mil contas em < 2 h sem estourar o p95 da API |
| P2.10 | Importador v1 com 120 meses | 17:X5 | G | P1.2 | `V14_2`; saldo por matrícula igual ao legado |
| P2.11 | Central: abas Faturamento e Fiscal | ADR-0018 | M | P1.17, P2.1, P2.6 | ciclo atrasado e fila fiscal acima do RNF-08 disparam alerta em homologação |
| P2.12 | Cobrança do SaaS por contrato, antes do primeiro contrato | ADR-0019 | G | P1.6, P1.17 | core `V10_1`; contrato com aditivos, empenhos, reajuste (só proposta até o apostilamento), faturas e pagamentos conciliados; alertas do item 7; restrição manual por uma pessoa com MFA, step-up e justificativa, executada por `PLATFORM_FINANCE` ou `PLATFORM_OWNER`, com motivo inadimplência contratual ligado a fatura vencida e ao aviso prévio registrados (sem eles, recusada); teste prova que nenhum caminho de código bloqueia tenant sozinho |
| P2.13 | NFS-e da venda pelo motor portado | ADR-0019, ADR-0003 | M | P1.8, P2.12 | contexto fiscal da plataforma definido; fatura gera NFS-e na produção restrita |
| P2.14 | Área do tenant: contrato com o Ziip | ADR-0022 | M | P2.12, P2.13 | tenant vê faturas e pagamentos do próprio contrato e baixa a fatura paga e a NFS-e em PDF e XML; outro tenant → 404; XML idêntico ao autorizado (hash conferido); acesso auditado |
| P2.15 | SLA de suporte por contrato e CSAT; severidades do nível Setor Público | ADR-0020, ADR-0025, ED-05 | M | P1.23, P2.12 | core `V10_2`; política referenciada no contrato; prazo bate com casos de feriado municipal e pausa; alerta a 80% e no estouro; relatório mensal de FRT, MTTR, SLA e CSAT na Central; política com cinco severidades (S1–S5) por impacto × criticidade, cada uma com prazo de primeira resposta, prazo de solução, meta de aderência e multa do contrato (casos de Videira, 2/4/6/16/32 h úteis, e do SAMAE, 2/4/16/72/160 h, testados) |
| P2.16 | Rejeições fiscais no catálogo de erros; novidades filtradas por tenant | ADR-0020, ADR-0021 | P | P1.22, P1.24, P2.6 | todo código de rejeição da NFAg-e (leiaute vigente) e da NFS-e com explicação (teste); novidade cujo `affects` não inclui o tenant não aparece |
| P2.17 | Marca do tenant e metadados nos PDFs | ADR-0016 | P | P1.19, P2.5 | conta e documentos com nome, logo e rodapé do tenant e metadados do `BrandProvider`; regras de contraste de MARCA-E-TEMAS §3.4 |
| P2.18 | XML autorizado em armazenamento com retenção imutável | ADR-0003, ADR-0022 | P | P2.6, P0.27 | cópia com bloqueio de retenção e hash na `HashChain`; apagar ou sobrescrever é recusado; leitura confere o SHA-256 |
| P2.19 | Importador: documentos anteriores ao go-live | ADR-0022, 17:X5 | M | P2.10, P2.18 | `V14_3`; importa os XML autorizados que o piloto entregar (sistema antigo ou emissor usado no intervalo), recusando os de assinatura inválida, sem protocolo de autorização, de outro emitente ou repetidos, com relatório por lote; aceitos vão para o armazenamento imutável da P2.18 com o hash e a origem (`LEGACY_IMPORT`), sem alteração do arquivo; o PDF das contas antigas sai sob demanda dos dados importados pela P2.10, com a indicação de que foi gerado a partir do histórico importado; contagem por competência conferida com o legado |
| P2.20 | Retificação e manutenção de conta pelo backoffice | ED-21 (18e-E1, 18f-ENT-15) | G | P2.5, P2.6, P1.11 | `V4_8`; operações: cancelar, alterar vencimento, alterar leitura, consumo e serviços, recalcular por mudança de cadastro, retirar água ou esgoto, crédito avulso, "pagamento a confirmar", emitir fora do lote, 2ª via e espelho; motivo de catálogo e anotação obrigatórios, aprovação conforme parâmetro; cada operação gera nova versão da conta sem apagar a anterior, com trilha por campo (valor anterior e posterior, usuário, data); NFAg-e cancelada ou substituída conforme o leiaute, testado na homologação da SEFAZ; repetição com a mesma `Idempotency-Key` não duplica; casos POC-49 e POC-50 da VISAN passam no ensaio (registro pelo protocolo quando a P3.4 existir); portal e relatórios mostram a versão vigente |

### P3 — Pronto para operar · 29/03–28/05/2027

Objetivo: arrecadação, portal, R7 mínimo, pentest, contrato e operação; relatórios operacionais para o paralelo;
ambiente de demonstração e primeiro ensaio de POC com meta no aceite; débito automático legado, se o piloto tiver
optantes (P3.24); plano de contratação da equipe da trilha (ADR-0029) no M8.

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P3.1 | R5a: retorno e conciliação N3 | R5 (v1), G4 | G | P2.5 | `V6_2`; retorno idempotente (NSA + hash); aprovação dupla |
| P3.2 | R5b: Pix Cobrança e boleto híbrido | R5 (v1), 17:P4 | G | P3.1 | `V6_3`; webhook assinado; tenant do convênio; 200 em < 2 s após gravar na `pix_inbox` |
| P3.3 | Portal do cliente: 2ª via, Pix, protocolo | 17:P2, ADR-0022 | G | P3.2, P0.14 | realm `ziipsan-portal` com conta própria do consumidor (login por CPF, CNPJ ou e-mail, com senha; MFA opcional; sem SMS nem login por celular) e client confidencial do BFF; item técnico: mapeamento de login por atributo no Keycloak, em que um autenticador próprio calcula o índice cego do documento digitado e localiza a conta, sem gravar nem consultar o valor em claro; CPF/CNPJ da conta cifrado e com índice cego; documento ausente de URL (inclusive `login_hint`), de logs da API e dos eventos de login do Keycloak (teste de log, RNF-52); resposta e tempo iguais para documento ou e-mail inexistente e senha errada, inclusive na recuperação de senha; tentativas limitadas por identificador e por IP, com bloqueio progressivo, 429 e alerta na Central; token do realm do portal recusado nas rotas do backoffice e token do backoffice recusado nas do portal (teste); sem acesso ao backoffice; disponibilidade medida contra 99,9%; histórico, documentos e vínculo na P3.20 |
| P3.4 | R7 mínimo: protocolo NR 11, OS com bloqueio de corte | R7 (v1), G9 | G | P2.5 | `V8_1`, `V9_1`; casos da v1 (aviso em 15/10 → 16/11; 19/11 e 20/11 bloqueados; ADASA; sem prova, religação sem taxa); vedações para todos os motivos; emergência exige justificativa e foto |
| P3.5 | Notificações com prova de entrega | 17:P4 | M | P1.9 | aviso de corte sem prova bloqueia a OS |
| P3.6 | NFS-e de serviços avulsos | ADR-0003 | M | P1.8 | serviço da OS gera NFS-e na produção restrita |
| P3.7 | Homologação fiscal com certificado do piloto | ADR-0003 | M | P2.6 | contingência e restauração do XML autorizado testadas |
| P3.8 | Pentest: isolamento, cofre, portal do consumidor, acesso assistido e de emergência | 17 §7, ADR-0020, ADR-0022 | M | P0.27 | nenhum achado crítico ou alto aberto; vínculo, enumeração de matrícula, de código de vinculação e de CPF/CNPJ ou e-mail no login e na recuperação de senha, troca de token entre realms e download do portal cobertos |
| P3.9 | Contrato SaaS, DPA, termos, SLA, suboperadores | ADR-0002, ADR-0019, ADR-0020 | M | P2.12, P2.14, P2.15 | assinados; Ziip SAN operador, cliente controlador; cláusulas de aviso, restrição só manual e saída; política de SLA de suporte; retenção de chamados pela vigência mais 5 anos e a exceção no certificado de descarte previstas no DPA |
| P3.10 | Resposta a incidente; modelo de RIPD | 17 §7 | P | — | prazos ao controlador e à ANPD; simulação de mesa feita |
| P3.11 | Operação: runbook, plantão, status, error tracking, burn rate | 17:O1 | M | P0.8, P0.27 | falha provocada dispara alerta; runbook exercitado |
| P3.12 | Fim do `outOfOrder` | 17:D1, G10a | P | P1.14 | sequência global a partir de `V100`; CI recusa versão ≤ maior publicada |
| P3.13 | Plano de paralelo e de corte | ADR-0001 | P | P0.30 | critérios e plano de volta aprovados pelo piloto |
| P3.14 | Spring Boot na linha com suporte | D4 | M | — | atualizado antes de 31/07/2027 |
| P3.15 | Central Ziip completa | ADR-0018, ADR-0025 | G | P2.11, P3.1, P3.2, P3.11 | abas Arrecadação, Pix, LGPD, Regulatório, Infraestrutura com SLO, Radar e Suporte; falha provocada alerta; kit de evidências com hash na trilha; relatório mensal por contrato de disponibilidade (meta de 99,9% no nível Setor Público) e de aderência por severidade, com o cálculo da multa |
| P3.16 | Ensaio de saída de tenant sintético | ADR-0018, ADR-0015, RNF-42, ADR-0025 | M | P1.6, P0.28 | exportação em até 15 dias (10 dias úteis no nível Setor Público), em CSV, JSON ou XML, com manifesto, hash e dicionário de dados gerado do schema; no nível Setor Público, período de consulta somente leitura pós-contrato (core `V10_5`) pelo prazo do contrato, até 120 dias, antes da carência, com escrita recusada (teste) e a carência contada do fim da consulta; carência de 30 dias contada do fim do contrato, com download da exportação e contestação registradas; eliminação agendada só por `PLATFORM_OWNER` com MFA e cancelável durante a carência; execução antes do fim da carência recusada (teste); contestação registrada na carência suspende a execução, e a resposta do Ziip sai em até 15 dias, com alerta na Central antes do vencimento (teste com relógio); ordem judicial registrada suspende a execução enquanto vigorar (teste); crypto-shredding; eliminação concluída e certificado emitido em até 30 dias após o fim da carência ou da resposta à última contestação, o que ocorrer depois, no máximo 75 dias do fim do contrato (60 sem contestação); certificado com hashes, expiração de backups e de cópias imutáveis e a exceção dos chamados |
| P3.17 | Acesso assistido com consentimento e acesso de emergência | ADR-0020 | M | P1.23, P0.43, P3.10 | core `V10_3`; sem consentimento → negado e auditado; revogação encerra a sessão em até 1 min; revelar documento e exportar bloqueados; cada requisição no `audit_log` do tenant com o id do consentimento. Emergência: sem incidente de segurança declarado, sem `PLATFORM_OWNER` ou sem reautenticação com MFA no momento → negado e auditado; qualquer escrita → 403; aviso ao tenant enviado pela outbox em até 24 h (teste com relógio); cada requisição no `audit_log` do tenant e em `audit_event` com o id do incidente, e a sessão no registro do incidente |
| P3.18 | Página pública de status | ADR-0020, RNF-54 | M | P0.27, P3.11 | core `V10_4`; hospedagem separada da produção; status muda sozinho com a sonda fora; janela de manutenção publicada com 72 h |
| P3.19 | Retenção, expurgo e exportação de chamados | ADR-0020 | P | P1.23, P2.12 | `purge_at` = fim da vigência do contrato mais 5 anos; ensaio de expurgo com contagem; chamados na exportação de fim de contrato; RIPD do módulo atualizado; manual de atendimento |
| P3.20 | Portal do consumidor: vínculo, histórico de 120 meses e documentos | ADR-0022 | G | P3.3, P2.6, P2.8, P2.10, P2.18, P2.19 | tenant `V8_2`; vínculo pelo código de vinculação da conta mais o CPF/CNPJ do titular, comparado pelo índice cego; resposta igual para matrícula inexistente, código errado e documento errado; tentativas limitadas por conta, IP e tenant, com bloqueio progressivo, registro e alerta de volume (ADR-0008); uma conta com vários imóveis; troca de titularidade revoga o vínculo e invalida o código; contas e pagamentos de 120 meses numa só lista, com a origem de cada documento (emitido pelo Ziip SAN, importado do sistema anterior ou do emissor do intervalo); conta paga com quitação, DANFAG em PDF e XML original; consumidor só vê matrículas vinculadas (teste com dois titulares e troca de titularidade); XML com hash igual ao gravado; todo download no `personal_data_access_log`; excesso → 429; PDF com marca do tenant e estrutura marcada; axe sem violação `serious` ou `critical` |
| P3.21 | Marca do tenant no portal e nos e-mails | ADR-0016 | P | P1.19, P3.3, P3.5 | portal e e-mails com nome, logo e cor do tenant; atribuição "operado com" conforme o pacote |
| P3.22 | Relatórios operacionais nativos: núcleo e cerca de 20 relatórios | ADR-0027, ADR-0025 (RNF-71), ED-45 | G | P0.28, P2.1, P2.5, P2.7, P2.20, P3.1, P3.4 | `V15_1`; núcleo declarativo sobre views, com filtros, ordenação, agrupamento, exportação PDF, XLSX e CSV, agendamento pelo `worker` e permissão por papel; leitura na réplica do RDS (sem ela, no primário com `statement_timeout`); relatórios: arrecadação diária (POC-58), pagamentos não identificados, em duplicidade (POC-59) e por data de remessa, pagamento, crédito e banco (POC-60), situação da importação do retorno, faturamento sucinto e detalhado por categoria e faixa, fechamento do ciclo por grupo, contas emitidas, canceladas e retificadas, leituras retidas e ocorrências de leitura, histograma de consumo por faixa (POC-56), ligações e economias por situação e categoria, valores a receber por tipo de unidade (POC-57), pendências por usuário (POC-65), aptos ao corte e notificações emitidas, serviços por protocolo com tempos (POC-64), tarifa social e isenções, NFAg-e autorizadas, rejeitadas e em contingência; cada relatório com teste de conteúdo sobre massa sintética; gerado em até 2 min na massa da P2.9 (RNF-71); usados no paralelo (GL.2) |
| P3.23 | Ambiente de demonstração permanente e ensaio de POC do P3 | ADR-0023, ED-01 | M | P0.23, P0.27, P1.25, P2.9 | ambiente separado de homologação e produção, criado pela IaC da P0.27; tenant de demonstração com pelo menos 10 mil unidades fictícias geradas pela massa sintética, sem dado pessoal real (documentos gerados marcados como sintéticos; teste no gerador); recriação do zero por script, com tempo medido; ensaio cronometrado com o prazo de preparo dos editais (5 e 7 dias úteis), resultado por item registrado na matriz viva; **meta do P3:** pelo menos 50 de 162 pontos no roteiro da VISAN e seções 2.4 e 2.5 do SAMAE sem falha; relatório de cobertura (requisito → tarefa → teste → item de roteiro) anexado ao aceite |
| P3.24 | Débito automático FEBRABAN 150 para autorizações legadas, **só se a P0.30 encontrar optantes** | ADR-0028 (f), ED-27 | M (1,5) | P3.1, P1.1, P1.2 | `V6_4` (ou sequência global, se depois da P3.12); autorizações legadas importadas do legado e conferidas por matrícula; remessa com registros B, C, E e F gerada e retorno processado com baixa idempotente; reenvio e cancelamento de optante; autorização nova recusada (só Pix Automático, na P4.5); data de desligamento parametrizada; conferido no paralelo (GL.2) e exigido no corte (GL.4). Sem optantes, a tarefa é cancelada com registro no diário e a parte FEBRABAN 150 fica na ED-27 |

### Go-live piloto · 01/06–30/07/2027

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| GL.1 | Carga final do legado | 17:X5 | M | P2.10, P2.19 | conciliação assinada pelo piloto; XML autorizados até o corte importados pela P2.19, com o relatório de recusas aceito pelo piloto |
| GL.2 | Dois ou três ciclos de faturamento paralelo, conta a conta | ADR-0001 | G | GL.1, P2.20, P3.22 | divergência dentro do critério da P0.30, toda explicada; sem emissão fiscal de produção; divergências corrigidas pela P2.20 e conferidas pelos relatórios da P3.22 |
| GL.3 | Restauração da produção | 17:X2 | P | P0.28 | dentro do RTO, antes do corte |
| GL.4 | Corte do legado | ADR-0001 | P | GL.2, GL.3; P3.24 se houver optantes | decisão formal; emissão fiscal ligada com aprovação dupla; com optantes, débito automático em produção pela P3.24 |

### P4 · 02/08–17/12/2027 (tarefas originais, redefinidas pelos ADRs 0026 a 0028) e trilha de editais · a partir de 02/08/2027

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P4.1 | R8: cobrança; negativação e protesto na ED-30 | R8 (v1) | G | P3.4 | parcelamento com demonstrativo; contestação bloqueia corte |
| P4.2 | R6: SINISA assistido | R6 (v1), ADR-0004 | M | P2.5 | arquivo oficial; valor manual com justificativa; casos da v1 (água não faturada 3.000 m³; ILI); base da ED-48 |
| P4.3 | BI e prestação de contas em réplica: catálogo restante de relatórios, Superset e painéis padrão (ADR-0027) | 17 §5, ADR-0027 | G (24, contado na trilha: ED-46, ED-47, ED-49) | P0.28, P3.22 | aceites da ED-46, da ED-47 e da ED-49; nenhum relatório nem consulta de BI no primário |
| P4.4 | App de campo Android em Capacitor: base, autenticação e leitura offline (ADR-0026, ADR-0030) | 17:P3, ADR-0026, ADR-0030, ED-18 | G (5, contado na trilha: ED-18) | P2.3, P2.7 | aceite da ED-18; emissão simultânea (ED-19, ADR-0031) e OS de campo (ED-20) em seguida |
| P4.5 | R5c: Pix Automático | R5 (v1), ADR-0028 | M | P3.2 | cobrança recorrente no PSP do piloto; FEBRABAN 150 legado na ED-27 |
| P4.6 | Schema compartilhado e banco dedicado | ADR-0005 | G | P1.14, P2.2 | só com demanda; mesmo teste de isolamento |
| P4.7 | Restauração mensal automatizada | ADR-0012 | P | P0.28 | relatório mensal |
| P4.8 | Suporte: resposta por e-mail, deflexão, assinatura de status, notas do tenant | ADR-0020 | G | P3.17, P3.18 | resposta por e-mail entra no chamado certo só de remetente verificado e sem anexo não varrido; deflexão cita artigo e transfere para humano; assinatura de status por e-mail |

O R6 fica depois do alvo de 30/04/2027 da v1 porque, sem cliente em produção antes de junho de 2027, o primeiro envio
assistido com dado do Ziip SAN é o do ano-base 2027; até lá o piloto envia pelo processo atual.

**2028, sob demanda:** trilha GRS (D-MTR-1..7, DMR e SINIR assistidos, lista IBAMA, NBR 10004); R9 sem o GIS (incidente,
N5, N1, N7, perdas com entrada real); integração por API com birô de crédito e com a CENPROT; ISO 27001. Saíram desta
lista para a trilha de editais do P4 (v2.4): GIS web, edição, migração do cadastro técnico e manobras, negativação e
protesto por arquivo e qualidade da água (ADR-0028); instância dedicada (ADR-0024). **2029, sob demanda (v2.5):**
compras, RH e orçamento do ERP (ADR-0028, item i, ajuste v2.5).

### Trilha de editais (ADR-0023) · a partir de 02/08/2027

Executa as entregas ED-01 a ED-62 do [consolidado 18](levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md) §5,
na ordem das ondas: **A** (itens que pontuam nas alíneas b a p e r da VISAN ou são críticos no SAMAE nas áreas
comercial, de campo e de suporte; e-Sfinge para a habilitação), **B** (canais digitais, BI, qualidade e GIS: VISAN q, s
e t; SAMAE 2.22, 2.24, 2.26, 2.27 e "Item 0") e **C** (aderência total fora das POCs e serviços de implantação). Dentro
de cada onda, primeiro o que é crítico no SAMAE e pontua na VISAN. Já estão em fases anteriores: ED-02 (P0.44), ED-03
(P0.26–P0.28), ED-21 (P2.20), ED-45 (P3.22), as partes de ED-01 (P1.25, P3.23) e de ED-05 (P2.15, P3.15, P3.16) e,
se a P0.30 encontrar optantes, a parte FEBRABAN 150 da ED-27 (P3.24). A coluna "Tam." traz a escala do plano e, entre
parênteses, a estimativa em semanas-pessoa do consolidado 18 §5.

Esforço da trilha, recalculado na v2.5 (consolidado 18, §5.2): onda A ≈ 109 semanas-pessoa; onda B ≈ 74,5; onda C ≈ 23;
total ≈ 206 (≈ 48 meses-pessoa), igual ao da v2.4 por compensação. Em relação à v2.4: +1 na ED-18 (ADR-0030), +2 na
ED-19 (ADR-0031), +2 na ED-42 (conectores), +0,5 na ED-04 (Windows Server) e −5,5 no ERP (ED-58 −0,5: −3 de compras
e +2,5 do ADR-0032; ED-59 +1 pelas equipes de campo; ED-60 e ED-61, −6, para 2029). As ondas A e B crescem 5,5
semanas e a C diminui o mesmo. A prontidão de POC (ondas A e B mais as tarefas originais do
P4) pede ≈ 192 semanas-pessoa, executadas pela equipe do ADR-0029 a partir de 02/08/2027. Ensaio de POC ao fim de cada
onda, com meta no aceite: **onda A**, pelo menos 120 de 162 pontos na VISAN e nenhuma falha no SAMAE nas seções 2.4 a
2.19, 2.28 e Item 3; **onda B**, a meta final da ED-01.

**Onda A**

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| ED-05 | Nível Setor Público: telefone, canal de contingência e plantão | 18h-E7, E9; ADR-0025 | P (0,5) | P2.15, P3.15, P3.16; equipe do ADR-0029 | telefone no Brasil e canal de contingência publicados e testados com a ferramenta de chamados fora do ar; escala de plantão para S1 entre os contratados, ativa até 31/01/2028; contrato-modelo do nível com as metas do ADR-0025; Item 3 do SAMAE sem falha no ensaio |
| ED-06 | Controle de acesso de edital | 18h-E5 | G (3) | P0.13, P0.15, P0.43, P1.7 | bloqueio por tentativas e expiração de senha por tenant (POC-05 com 3 tentativas); federação LDAP/AD por organização no Keycloak, testada contra servidor de teste; perfis compostos de permissões criados pelo administrador do tenant (POC-04, POC-07); sessões ativas e trilha visíveis ao ADMIN do tenant; consulta da trilha por usuário (TR-128, SG-062); SAMAE 2.7 sem falha |
| ED-07 | Monitoramento e alertas para o cliente | 18h-E6 | M (1) | P1.9, P1.17, P2.1, P3.5 | evento de negócio configurado pelo tenant gera alerta ao usuário (teste); painel de uso e desempenho do tenant; falha de lote notifica o responsável do tenant |
| ED-08 | Compatibilidade técnica de edital | 18h-E12 | P (0,4) | P0.22, P1.12 | matriz E2E com Edge 88, Chrome 100 e Firefox 116; seletor de tamanho de página e "ir para página" sobre keyset (teste); nota técnica sobre gatilhos e linguagem no kit |
| ED-12 | Cadastro comercial ampliado e catálogos auxiliares | 18d-E1, E2 | M (2) | P1.3, P1.20 | POC-08 a POC-11 passam; RG e filiação cifrados, com finalidade registrada; 22 catálogos da SAMAE 2.8.10 com CRUD genérico; endereço alternativo |
| ED-13 | Ligações de água e esgoto | 18d-E3 | G (3) | P1.3, P3.4 | ligações 1:1 com o imóvel, com catálogos técnicos; consumo mínimo e situação lidos da ligação no ciclo (teste); alteração por OS no histórico; SAMAE 2.8 sem falha |
| ED-14 | Hidrometria | 18d-E4 | G (3) | P2.5, P3.4, P1.20 | POC-12 a POC-14 passam (cadastro com lote, modelo, classe e lacre; instalação; retirada com destino disponível e sucata; reinstalação com histórico); alerta de validade gera OS em lote; SAMAE 2.9 sem falha |
| ED-15 | Ciclo de leitura: complementos e autoleitura | 18d-E5 | M (2) | P2.5, P2.7, P3.3 | roteiro com reordenação; boletim de contingência; regra de condomínio configurável (SAMAE 2.10.10, nas duas modalidades); autoleitura pelo portal com crítica |
| ED-16 | Recadastramento | 18d-E11 | G (3) | P4.4, P1.3 | lote, coleta no app de campo, aprovação campo a campo e aplicação, com os cinco relatórios |
| ED-17 | Telemetria e supervisório por REST e arquivo | 18d-E10, 18g-E-AMR, E-SUP; ADR-0028 (j) | M (2) | P1.9, P2.3, P2.7 | API REST e importação de arquivo de leituras AMR (origem `TELEMETRY`) em tabela particionada sem auditoria por linha; alarmes persistidos de grandes consumidores; registro manual de paradas da ETA; nenhum broker (registro no ADR-0010) |
| ED-18 | App de campo Android, base, autenticação e leitura offline (é a P4.4) | 18d-E6; ADR-0026, ADR-0030 | G (5) | P2.3, P2.7 | projeto Capacitor com build e assinatura Android no CI; APK instalável (TR-621); login pelo navegador do sistema com PKCE no realm `ziipsan` e retorno por App Link; refresh rotativo só no Android Keystore, inacessível ao JavaScript (teste instrumentado); core `V11_1`; aparelho não registrado ou bloqueado não sincroniza (teste); `RETIRE` envia pendências e apaga, `BLOCK` recusa e apaga; revogação pela área do tenant e pela Central efetiva em até 5 min; banco local cifrado com chave no Keystore; leitura offline de uma rota com sincronização idempotente por `task_id` (reenvio não duplica) e fotos por ocorrência; RNF-39 verde |
| ED-19 | Emissão simultânea e impressão térmica | 18d-E6, 18e-E6; ADR-0026, ADR-0031 | G (8) | P4.4, P2.5, P2.6, P3.2, P2.20; leiaute de contingência da NFAg-e (§2.4) | módulo `ziipsan-billing-rules` puro, usado pelo servidor e pelo plugin Android como o mesmo artefato (hash conferido); vetores de `billing-contract-fixtures` iguais no JVM e no emulador Android em todo build, com divergência bloqueando o merge, e teste diferencial noturno com 10 mil ligações; pacote da rota com `task_id`, tarifas e versões de parâmetro; conta de campo única por `task_id` (reenvio não duplica conta nem NFAg-e), recalculada no servidor; divergência provocada vai à fila, é aprovada por quem tem a permissão da P2.20 (dupla acima do limite) e corrigida por nova versão, sem alterar a conta impressa; alerta de divergência na Central; certificado ausente do aparelho (teste); forma fiscal do ADR-0031 item 4 confirmada contra o manual e testada na homologação da SEFAZ; impressão em Zebra série ZQ por Bluetooth com leiaute do editor (ZPL e CPCL), código de barras e QR Pix; comunicados de débito e de excesso; retidas reenviadas e reimpressas; teste de cabeçote e bateria; envio a cada N imóveis configurável; contingência da NFAg-e definida; POC-15, POC-16 e POC-21 passam; SAMAE 2.11 sem falha |
| ED-20 | OS em campo e acompanhamento de campo | 18f-ENT-05, 18d-E7; ADR-0026 | G (5) | P4.4, ED-36, ED-52 | app de OS offline com fotos, GPS e anexos; trilha GPS do leiturista e da equipe; painel com deslocamento, tempo e paradas no mapa; importação de materiais, funcionários e cadastros de consistência; material usado registrado por tarefa e enviado na sincronização com `task_id` (baixa no estoque quando a ED-58 existir, ADR-0032); POC-52 a POC-54 passam; SAMAE 2.18 sem falha |
| ED-22 | Situações especiais de faturamento, usuário avulso e órgão centralizador | 18e-E2, 18f-ENT-16 | G (4) | P2.5, P1.3, ED-26 | POC-51 passa (não usuário regular com matrícula e fatura); faturas agrupadas e centralizadora com código único; isenção, contrato especial e conta final testados |
| ED-23 | Revisão de consumo e desconto por vazamento | 18d-E9, 18e-E3 | G (4) | P3.4, P2.20, ED-20, P3.5 | POC-26 passa (duas revisões completas e duas faturas alteradas por vazamento); protocolo com fases, vistoria e laudo no celular, sem papel; SAMAE 2.12 e 2.12.5 sem falha |
| ED-24 | Controle de fraudes e fiscalização | 18e-E4 | M (1) | ED-36, P2.20 | OS de fiscalização gerada por critério; fraude registrada com tipo, fotos e boletim; relatório de unidades com fraude |
| ED-25 | Resíduos sólidos na conta | 18e-E15, 18g-E-RES | G (3) | P2.5, ED-22 | componente de resíduos no cálculo, com isenção, fatura desvinculada e carnê mensal ou anual; SG-324 e SG-325 no ensaio; SAMAE 2.13 sem falha |
| ED-26 | Arrecadação ampliada | 18e-E7 | M (1) | P3.1, P2.1 | documento agrupador com quitação em cascata; fechamento mensal congelado; depósitos identificados e não identificados; VAN configurável |
| ED-27 | Débito em conta: Pix Automático e FEBRABAN 150 legado | 18e-E8; ADR-0028 (f) | M (2) | P3.1, P4.5 | POC-29 passa: arquivo FEBRABAN 150 (registros B, C, E, F) para autorizações legadas e retorno processado; autorizações novas só por Pix Automático; data de desligamento do legado parametrizada conforme a regra vigente do Banco Central; relatórios de débito em conta. Se a P0.30 encontrar optantes no piloto, a parte FEBRABAN 150 já foi feita na P3.24 e esta entrega fica com o Pix Automático integrado e os relatórios |
| ED-29 | Cobrança: complementos, corte e religação por nível | 18e-E9, 18f-ENT-04 | G (4) | P3.4, P4.1, ED-36 | POC-32 e POC-33 passam, com níveis de corte (cavalete, ramal, supressão) e religação de água e esgoto; geração de OS de corte fora da janela da Lei 14.015 recusada (teste); prioridade e cota diária; cartas de cobrança e de liberação de esgoto |
| ED-30 | Negativação e protesto por arquivo | 18e-E10; ADR-0028 (g) | G (3) | P4.1, ED-31 | remessa ao birô e à central de protesto gerada e retorno importado; inclusão sem carta de aviso prévio com entrega registrada é recusada; baixa no prazo legal após a quitação (teste com relógio); relatório de inclusões e exclusões; SAMAE 2.15 sem falha |
| ED-31 | Dívida ativa (núcleo) | 18e-E11; ADR-0028 (d) | G (4) | P2.20, ED-26, ED-29, ED-32 | POC-34 a POC-36 passam: inscrição individual e periódica, livro, CDA impressa, parcelamento de dívida ativa; saldo mensal congelado; arquivo e petição-modelo para a procuradoria |
| ED-32 | Eventos contábeis e integração contábil | 18e-E12; ADR-0028 (e) | G (4) | P2.5, P3.1, P1.9, P2.1 | POC-37 a POC-40 e POC-66 a POC-68 passam; lançamentos gerados por fato, com fechamento mensal congelado; exportação diária ao sistema contábil por arquivo e pela ED-62; SAMAE 2.16.18 sem falha |
| ED-33 | Remessa e-Sfinge assistida | 18e-E13; ADR-0028 (e) | G (3) | ED-31, ED-32 | remessa tributária gerada com as regras de consistência do leiaute vigente do TCE/SC, validada e baixada pelo cliente, com hash, versão do leiaute e retorno registrados; relatório de envio utilizável no atestado; em operação no piloto se ele for autarquia de SC |
| ED-34 | Catálogo de serviços e serviços faturáveis | 18f-ENT-01, 18e-E5 | G (4) | P0.19, P3.4, P3.6, ED-32 | POC-41 passa (três tipos de serviço com todos os parâmetros) e POC-31 (parcelamento de serviço); tabelas de preço de serviços e materiais com vigência; cobrança lançada no encerramento da OS |
| ED-35 | Atendimento completo | 18f-ENT-02 | G (3) | P3.4, P3.5, P1.20 | POC-47 e POC-48 passam: protocolo único por canal, visão 360 com faturas, consumos, serviços e análise de água; fila por prazo; SAMAE 2.19 sem falha |
| ED-36 | OS completa no backoffice | 18f-ENT-03 | G (4) | ED-34, P3.4, P1.9, P2.1 | POC-42 a POC-46 passam (abrir, encerrar com executores e materiais, monitorar, programar e desprogramar, anexos); OS em lote, desdobramento e retrabalho; materiais da OS com quantidade prevista e usada, prontos para a requisição com alçada da ED-58 (ADR-0032); SAMAE 2.17 sem falha |
| ED-37 | Empreiteiras terceirizadas | 18f-ENT-06 | M (1) | ED-36 | cadastro de empreiteiras, contratos, penalidades e medição (SG-492 a SG-496) |
| ED-38 | Portal e autoatendimento ampliados | 18f-ENT-08, 18e-E17 | G (4) | P3.3, P3.20, P1.5, ED-34 | POC-69 passa (faturas pendentes e quitadas, 2ª via, simulação de fatura e de parcelamento, certidão de quitação anual); SAMAE 2.20 com pelo menos 90% no ensaio |
| ED-41 | Notificações multicanal e fatura por e-mail | 18f-ENT-11; ADR-0028 (l) | G (3) | P1.9, P3.5, P3.9 | POC-20 passa: e-mail com aviso e link autenticado ao portal; PDF anexo só com opt-in registrado do morador, ou por padrão do tenant com aceite formal; SMS, WhatsApp e push com opt-in por canal e finalidade; provedores no DPA |
| ED-46 | Catálogo completo de relatórios (P4.3) | 18g-E-REL, 18d-E8, 18e-E16; ADR-0027 | G (12) | P3.22 e a fonte de cada relatório | catálogo das seções 19 (VISAN) e 2.28 (SAMAE), com teste por relatório; POC-56 a POC-68 passam; RNF-71 medido; SAMAE 2.28 sem falha |
| ED-62 | Integração com ERP do cliente (Betha, IPM, Fiorilli) | 18g-E-ERPIND, 18f-ENT-07; ADR-0028 (i) | G (7) | ED-32, ED-36 | conectores por API e/ou arquivo para contabilidade, estoque e RH, testados contra leiaute publicado ou ambiente do fornecedor; previsão de materiais da OS enviada ao estoque do cliente (SG-476, TR-521); movimentos de material da OS exportados quando o estoque oficial for o do ERP (ADR-0032); indicadores de compras, RH e orçamento lidos do ERP do cliente, que não são construídos até 2029 (ADR-0028, item i, ajuste v2.5), disponíveis para o painel estratégico |

**Onda B**

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| ED-04 | Instância dedicada e pacote de reversão | 18h-E4; ADR-0024 | G (3,5) | P0.5, P0.27, P4.6 | core `V11`; instância dedicada criada pela mesma IaC, imagem e pipeline, em conta separada; acesso administrativo do órgão somente leitura, com MFA e auditoria; pacote em contêineres Linux sobe em servidor Linux limpo e, uma vez, em Windows Server 2022 ou superior com Docker no WSL2, com os requisitos mínimos publicados para os dois, carrega a exportação de um tenant sintético e passa nos testes de fumaça; runbook de migração exercitado; exceção no ADR-0010 registrada |
| ED-28 | Cartão no parcelamento pela credenciadora do cliente | 18e-E14; ADR-0028 (h) | M (1) | P3.1, P4.1 | parcelamento pago por link ou checkout da credenciadora do tenant, com baixa e conciliação automáticas pelo retorno e pelo extrato; nenhum dado de cartão trafega nem é gravado no produto (teste) |
| ED-39 | App do cliente Android e iOS nas lojas | 18f-ENT-09; ADR-0026, ADR-0030 | G (4) | P4.4, ED-38, P3.21 | core `V11`; POC-55 passa em Android e iOS; login pelo navegador do sistema com PKCE no realm `ziipsan-portal`, client público por app criado pelo pipeline e Universal Link ou App Link; refresh só no Keychain ou no Keystore (RNF-39); app gerado por pipeline com nome, ícone e cores do tenant, publicado nas lojas na conta do prestador e ligado à base do tenant; push sem dado pessoal; acessibilidade do aparelho (SG-782); SAMAE 2.24 sem falha |
| ED-40 | Totem em modo quiosque e senhas | 18f-ENT-10; ADR-0028 (k) | M (1) | ED-38, ED-35 | portal em modo quiosque no hardware do cliente; senha com prioridade legal, chamada na tela de atendimento e painel em navegador (SG-625, SG-626) |
| ED-42 | Omnichannel híbrido | 18f-ENT-12; ADR-0028 (c) | G (7) | ED-35, ED-41 | POC-70 e POC-75 passam: WhatsApp oficial, e-mail e chat do portal atendidos na tela do Ziip SAN, com a conversa no protocolo da matrícula; Messenger e Instagram pela API oficial da Meta e Telegram pela Bot API, no mesmo modelo (conversa no protocolo, opt-in por canal, credenciais no cofre, webhook com assinatura ou segredo conferido, provedor no DPA), cada um com teste de ponta a ponta em conta de teste; TR 21.1 da VISAN e item 4 do lote do SAMAE atendidos; logs com integridade (SG-655 a SG-661); mensagens medidas por tenant; SAMAE 2.22 sem falha |
| ED-43 | Chatbot e IA por modelo de linguagem de terceiro | 18f-ENT-13; ADR-0028 (c) | G (5) | ED-42, ED-38 | POC-71 a POC-74 e POC-76 passam; o modelo só chama funções do portal, com as permissões do consumidor (teste de recusa de função fora da lista); texto enviado ao modelo sem CPF, CNPJ, nome nem endereço desnecessários (teste); transbordo humano; provedor no DPA e no RIPD, com transferência internacional registrada se houver |
| ED-47 | BI embarcado com Apache Superset (P4.3) | 18g-E-BI; ADR-0027 | G (5) | P0.28, P3.22 | Superset com marca do produto e SSO do Keycloak, lendo só a réplica; role de banco por tenant; teste de isolamento no CI (consulta livre e `SET` da GUC não leem outro tenant, em schema próprio e compartilhado); auditoria de acesso (SG-1252); exceção no ADR-0010 |
| ED-48 | Indicadores gerenciais, SINISA e macromedição | 18g-E-IND, E-MACRO | G (7) | P4.2, ED-47 | catálogo SINISA vigente pré-configurado, com painel exportável por período, setor, categoria e tipo de ligação; indicadores parametrizáveis com metas, fechamento e semáforo; macromedição e balanço por setor |
| ED-49 | Sete painéis padrão (P4.3) | 18g-E-PAIN; ADR-0027 | 7 × M (7) | ED-47, ED-48, ED-62 | POC-77 a POC-81 passam: painéis de arrecadação, atendimento, cobrança, faturamento, medição, operacional e indicadores estratégicos |
| ED-50 | Qualidade da água operacional | 18g-E-QUAL; ADR-0028 (a) | G (9) | P2.5, P2.8, P3.3, P3.22 | catálogo da Portaria GM/MS 888/2021 revisado por responsável técnico; plano de amostragem, cadeia de custódia, conformidade e fechamento mensal; bloco na conta (Decreto 5.440/2005) e no portal; arquivo SISAGUA assistido; SAMAE 2.26 e relatórios 2.28.117 a 2.28.126 sem falha |
| ED-51 | Apoio à ISO/IEC 17025 | 18g-E-LIMS17025; ADR-0028 (a) | G (5) | ED-50 | laboratório com escopo, calibração com histórico e bloqueio, Westgard, incerteza e validação por segunda pessoa demonstrados no ensaio; em produção em até 6 meses da implantação de quem exigir |
| ED-52 | GIS web | 18d-E12; ADR-0028 (b) | G (4) | P0.4, P2.5, P3.4 | visualizador MapLibre com tiles do PostGIS servidos pela `api`; camadas, WMS, pesquisas por coordenada UTM, atributo e endereço; temáticos; importação SHP e GeoPackage; OS no mapa |
| ED-53 | GIS: edição e integração com o cadastro | 18d-E13; ADR-0028 (b) | G (5) | ED-52, ED-13, ED-36 | editor de feições; ligação desenhada vira ligação no cadastro (teste); permissões por camada; camadas de resíduos e drenagem |
| ED-54 | Rede lógica, traçado e manobras | 18d-E14; ADR-0028 (b) | G (4) | ED-53, ED-36 | isolamento de trecho pelas válvulas sobre a topologia existente, com consumidores afetados e OS em lote; SAMAE 2.27 sem falha |
| ED-55 | Migração do cadastro técnico (DWG) e acesso QGIS | 18d-E15, 18h-E8; ADR-0028 (b) | G (4) | P1.9, P0.4, ED-52, P1.25 | DWG 2007+ convertido para DXF pelo ODA File Converter na esteira de implantação e importado com GDAL no `worker`, com diagnóstico topológico e SIRGAS 2000/UTM 22S; QGIS por role individual mapeada ao tenant, `WITH CHECK`, auditoria por gatilho e acesso por rede restrita; teste prova que a role de um tenant não lê nem grava outro; migração de amostra real em até 60 dias; coberto por pentest |
| ED-01 | Prontidão de POC: kit de campo e ensaio final | 18h-E3; ADR-0023 | M–G (3) | P3.23; ondas A e B | kit de campo (Android, iPhone, Zebra série ZQ, bobinas, 4G) mantido; massa do tenant de demonstração atualizada a cada versão; **meta final:** roteiro da VISAN com pelo menos 146 de 162 pontos e nenhuma falha em item crítico do SAMAE, em dois ensaios consecutivos com o prazo de preparo dos editais (marco M12) |

**Onda C**

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| ED-09 | Serviços de implantação padronizados | 18h-E8 | G (4) | P1.2, P2.10, P2.19 | metodologia e plano-modelo de 60 e 90 dias; importador do legado do primeiro cliente de edital (GSAN, se confirmado) com conciliação; exportação e importação de parâmetros aceitos entre homologação e produção; dicionário de dados gerado do schema no CI (XLSX, CSV ou PDF); linhagem da migração; implantação simulada com tenant sintético em até 60 dias |
| ED-10 | Política comercial e preço por porte | 18h-E10 | P (0,4) | ED-04, P0.26 | planilha de custo e preço por porte comparada com os tetos de referência (VISAN R$ 18.866,65/mês; SAMAE R$ 7.636,74/mês); perfis e homem-hora; garantia de 12 meses e correção de vícios em 48 h; desconto da reversão (ADR-0024); franquia ou repasse de mensagens e do modelo de linguagem |
| ED-11 | Rede de implantação e treinamento | 18h-E11; ADR-0023, ADR-0029 | G (3) | equipe do ADR-0029; parceiro de implantação (§2.4); ED-09 | equipe ou parceiro definido; plano de treinamento de 40 h por módulo; material online; tenant de pré-treinamento com massa sintética; capacitação de TI; documentação de customizações |
| ED-44 | Telefonia mínima | 18f-ENT-14; ADR-0028 (k) | M (1) | ED-35, ED-43 | chamada recebida abre a ficha pelo número chamador, com registro de acesso; API de consulta para URA de terceiros; listas de discagem geradas pelo produto |
| ED-56 | Ativos e manutenção de rede | 18g-E-ATIVOS | G (3) | ED-53, ED-32 | cadastro técnico de ativos com vínculo patrimonial exportado à contabilidade; manutenção preventiva e corretiva ligada ao GIS |
| ED-57 | Beneficiários de programas sociais | 18d-E16 | M (1) | P1.5 | importação de lista de beneficiários com base legal registrada (sem API pública), com conferência e vigência |
| ED-58 | Almoxarifado: materiais e estoque para a OS, com alçada e baixa automática | ADR-0028 (i), ADR-0032; 18g-E-ALMOX | G (7,5) | ED-36, ED-20, ED-12, ED-14 | estoque por local (hidrômetros e materiais de rede), entradas, ajustes com justificativa, custo médio, inventário e relatórios de materiais e insumos; parâmetros do grupo de almoxarifado com aceite; seleção de material na OS gera requisição; aprovação pela faixa de valor e pelo papel configurados, item controlado sempre aprovado, abaixo do limite aprovação automática registrada; **reserva na aprovação reduz o saldo disponível e mantém o físico** (teste); entrega registrada; **encerramento baixa a quantidade usada e devolve a sobra** (teste), com custo apropriado na OS; OS cancelada libera a reserva; **requisitante não aprova a própria requisição** (teste, recusa 403 e auditoria); **aprovação sem saldo deixa saldo negativo e gera alerta de reposição** no painel e na Central (teste); relatório diário de negativos e alerta além do prazo de regularização; baixa do material de campo na sincronização, idempotente por `task_id` (reenvio não baixa duas vezes), com alçada a posteriori para item controlado; toda operação na auditoria |
| ED-59 | Frota e equipes de campo | ADR-0028 (i) | G (3) | ED-36 | veículos, abastecimento, manutenção e custo por veículo, com consumo na OS; equipes de campo (composição, veículo, escala) usadas na programação e no custo da OS |

**Fora da trilha (2029, sob demanda; ADR-0028, item i, ajuste v2.5):** compras (requisição de compra, cotação e
pedido, antes na ED-58, ≈ 3 semanas), ED-60 (RH: lotação, competências e indicadores, ≈ 3) e ED-61 (orçamento:
dotações, previsto × realizado, ≈ 3). Até lá, os indicadores de compras, RH e orçamento do painel estratégico vêm do
ERP do cliente pela ED-62. Entram só com cliente que os contrate, por nova estimativa e aceite de escopo.

**Melhorias contínuas.** Os baixos 17:B1–B11 já estão distribuídos (P0.5, P0.7, P0.13, P0.17, P1.12, P1.15, P2.3);
baixos de revisões futuras entram nesta lista.

---

## 5. Caminho crítico e marcos

Caminho crítico: ambientes, PITR e R1 na AWS (P0.27, P0.28) → importador com dado real (P1.2) → outbox (P1.9) → lote
(P2.1) → ciclo (P2.5) → NFAg-e (P2.6) → retificação de conta (P2.20) → arrecadação (P3.1, P3.2) → relatórios
operacionais (P3.22) → paralelo (GL.2) → corte (GL.4). Ramo igualmente crítico: piloto (P0.30) → discovery (P1.1) →
importadores → carga final (GL.1). Não há folga: atraso no piloto ou nos ambientes desloca o go-live na mesma medida.
A hospedagem, decidida em 12/10/2026 (ADR-0011), saiu do caminho.

A versão 2.4 põe no caminho a P2.20 e a P3.22, que o paralelo exige (contas divergentes precisam ser retificadas e
conferidas por relatório), e acrescenta ao P0–P3 cerca de 13 semanas-pessoa (consolidado 18, §5.2), sobre fases que
já não tinham folga. Se o P2 ou o P3 atrasar, escorregam primeiro a meta de ensaio da P3.23 (o ensaio continua, sem
meta no aceite), os itens do nível Setor Público na P3.15 e na P3.16 e as tarefas fora do caminho já previstas no §6;
P2.20 e P3.22 ficam. A trilha de editais começa depois do go-live e não está no caminho do piloto; o caminho dela é
plano de contratação e orçamento (M8, ADR-0029) → recrutamento (junho e julho de 2027) → entrada e onboarding da
equipe (M11) → onda A → ensaio com meta de 120 pontos → onda B → prontidão de POC (M12) → primeira licitação (M13),
com o atestado do piloto (P0.30 → GL.4) e o kit de habilitação (P0.44) em paralelo.

A versão 2.5 só toca o caminho do piloto num caso: se a P0.30 encontrar optantes de débito automático, a P3.24
(≈ 1,5 semana) entra no P3 e o corte (GL.4) passa a depender dela. Nesse caso escorregam primeiro os mesmos itens
listados acima.

As tarefas da versão 2.1 não mudam esse caminho, com três ressalvas: a renomeação (P0.33) vem logo depois da P0.1 e
antes de toda tarefa de código do P0; cobrança por contrato, NFS-e da venda e
área do tenant (P2.12–P2.14) precedem a assinatura do contrato do piloto (P3.9); e o histórico com documentos no
portal (P3.20) depende da NFAg-e (P2.6), da cópia imutável do XML (P2.18), que depende da hospedagem (P0.27), e da
importação dos documentos anteriores ao go-live (P2.19). As decisões das versões 2.2 e 2.3 não alteram o caminho; a
troca dos códigos de problema cabe na P0.33, que já toca todos os pontos que os emitem.

| Marco | Data | Critério |
|---|---|---|
| M1 | 23/10/2026 | clientes-alvo comunicados |
| M2 | 12/10/2026 (previsto para 30/10) | hospedagem decidida: cumprido, ADR-0011 aceito |
| M3 | 13/11/2026 | piloto escolhido, com acordo de tratamento de dados, critério de atestado (ADR-0023) e optantes de débito automático levantados (P3.24 confirmada ou cancelada) |
| M4 | 27/11/2026 | fim do P0: ambientes no ar, PITR testado, R1; kit de habilitação iniciado |
| M5 | 11/12/2026 | R3 em homologação |
| M6 | 29/01/2027 | fim do P1: cadastro do piloto importado e conciliado; roteiros de POC e primeiro ensaio registrados |
| M7 | 26/03/2027 | fim do P2: ciclo, NFAg-e e retificação de conta em homologação |
| M8 | 28/05/2027 | fim do P3: pronto para operar, com relatórios operacionais (e P3.24, se houver optantes); ensaio de POC com meta (≥ 50 de 162 na VISAN; SAMAE 2.4 e 2.5 sem falha); plano de contratação da trilha (perfis, ordem, regime, custo estimado) e orçamento aprovados, número de contratações (4 ou 5) confirmado pela velocidade medida e recrutamento aberto (ADR-0029) |
| M9 | 01/06/2027 | início do paralelo |
| M10 | 30/07/2027 | go-live: legado desligado |
| M11 | 02/08/2027 | início da trilha de editais e entrada da equipe do ADR-0029, toda ela até 31/08/2027, com onboarding de 8 semanas |
| M12 | 30/06/2028, condicionado à entrada de 4 a 5 desenvolvedores até 31/08/2027 (ADR-0029); com uma pessoa, por volta de 2031 | prontidão de POC: ondas A e B concluídas; dois ensaios seguidos com pelo menos 146 de 162 pontos na VISAN e nenhuma falha crítica no SAMAE; plantão da S1 ativo desde 31/01/2028 |
| M13 | segundo semestre de 2028 | primeira licitação com POC: atestado do piloto emitido, kit de habilitação válido (balanços de 2026 e 2027), M12 cumprido e decisão de disputar registrada (consolidado 18, §7.3); primeiro alvo, edital do tipo SAMAE (um atestado) |

**Prazos legais do prestador.** Até o corte, o piloto cumpre pelo sistema atual: NFAg-e 01/12/2026, a confirmar (Ziip SAN
no P2, emite após o corte); tarifa social 11/12/2026 (P1, satélite); CBS/IBS 01/01/2027 (P2); boleto híbrido 01/02/2027,
facultativo (P3); NR 11 em 20/05/2027 e Lei 14.015, em vigor (P3); SINISA anual (P4); DMR trimestral (2028).

---

## 6. Riscos

| Risco | Dono | Mitigação |
|---|---|---|
| Capacidade: uma pessoa com IA | Daniel | uma fase aberta por vez; infra congelada; tarefa bloqueada não libera antecipação de funcionalidade; revisão de escopo ao fim de cada fase; P4 cede antes do go-live |
| Capacidade frente à trilha de editais (recalculada na v2.5): ≈ 220 semanas-pessoa nas entregas da trilha até 2028 (≈ 51 meses-pessoa), das quais ≈ 13 no P0–P3 e ≈ 206 no P4; a prontidão de POC (ondas A e B) pede ≈ 183, mais ≈ 8 das tarefas originais do P4, ≈ 192 no total. Para 30/06/2028 são precisos ≈ 4,5 pessoas-equivalentes produtivas desde agosto de 2027 (≈ 2,8 para dezembro de 2028). Com o dono a ≈ 0,7 e a rampa de 8 semanas a 50% (ADR-0029), 5 contratações cobrem com folga de ≈ 17% e 4 ficam ≈ 6 semanas-pessoa abaixo (prontidão em meados de julho de 2028); sem contratação, por volta de 2031 (consolidado 18, §8) | Daniel | equipe decidida (ADR-0029); plano de contratação e orçamento no M8 com a velocidade medida do P0 ao P3; trilha em ondas A → B → C; onda C (implantação, ERP no recorte, ativos, telefonia, ≈ 23 semanas) depois da prontidão; compras, RH e orçamento em 2029; primeiro alvo em editais do tipo SAMAE; estimativas recalibradas a cada onda, com M12 e M13 registrados de novo se a equipe entrar depois de 31/08/2027 ou for menor |
| Contratação da equipe da trilha (ADR-0029): seleção lenta, mercado de Java sênior e mobile concorrido, rotatividade, custo fixo antes de receita de editais, dono do produto como revisor único | Daniel | recrutamento aberto no M8 e seleção em junho e julho de 2027; período de experiência; onboarding com critério de saída; revisão por par entre contratados; contingência por empresa contratada por entrega, com código no repositório do Ziip e cessão de direitos; cada mês de atraso na entrada adia M12 em cerca de um mês |
| Acréscimos da v2.4 ao P0–P3 (P0.44, P1.25, P2.20, P2.15, P3.15, P3.16, P3.22, P3.23: ≈ 13 semanas) sobre fases sem folga | Daniel | P2.20 e P3.22 são necessidade do piloto e ficam; escorregam primeiro a meta da P3.23 (o ensaio continua) e os itens do nível Setor Público da P3.15 e da P3.16; revisão ao fim do P1 com a velocidade medida |
| Duplicação do motor fiscal C# × Java; o C# não é referência validada | Daniel | corpus comum nos dois CIs (P1.10) conferido contra o XSD oficial, não só contra o C#; tabela de versões de NT; monitor regulatório único; reavaliar em 2028 qual motor permanece |
| NFAg-e sem manual e XSD oficiais; obrigatória em 01/12/2026 (a confirmar) e go-live só em jun/2027 | Daniel | o prestador precisa de outro emissor nesse intervalo, o que consta da comunicação de 23/10; builder gerado do XSD, vetores por versão |
| Arredondamento divergente (half-even × `HALF_DOWN`) e CHECK de `bills` com tributo como retenção | técnico | P1.16 antes do P2 |
| Custo da AWS (≈ US$ 2 mil por mês no dimensionamento inicial) acima do teto de editais pequenos (SAMAE: R$ 7.636,74 por mês) e do preço do piloto | Daniel | tabela de custo na P0.26; dimensionamento mínimo nos primeiros tenants; vários tenants por cluster; preço por porte (ED-10) antes de qualquer proposta |
| Piloto indefinido ou fora do critério de atestado (público, em SC, dispensa ou CPSI, cláusula de atestado) | Daniel | data-limite 13/11; critérios do ADR-0001 e do ADR-0023 na P0.30; importador sobre leiaute genérico e massa sintética; sem piloto no critério, segundo caminho por cliente privado e nenhuma licitação com POC antes do atestado |
| Balanço de dois exercícios exigido nas licitações de 2028 | Daniel | P0.44: empresa licitante e contador desde 2026 |
| Escopo do ERP construído (ADR-0028, item i) além do que os editais pedem (integração e indicadores) | Daniel | recorte decidido na v2.5: só almoxarifado e frota construídos (ED-58, ED-59, onda C), integração pela ED-62; compras, RH e orçamento em 2029, sob demanda |
| Saldo negativo no almoxarifado (ADR-0032) distorce custo médio e inventário e pode esconder perda ou desvio | Daniel | alerta de reposição e relatório diário de negativos; regularização obrigatória por entrada ou ajuste com justificativa no prazo do parâmetro; inventário periódico; alerta na Central para negativo vencido; custo recalculado na regularização |
| Divergência entre a conta impressa em campo e o recálculo do servidor (ADR-0031) | técnico | mesmo artefato de cálculo no servidor e no app; vetores comuns no JVM e no emulador em todo build; teste diferencial noturno; fila de divergências com aprovação e correção por retificação; alerta na Central e novo vetor a cada caso |
| Leiaute de contingência da NFAg-e incompatível com a emissão em campo (assinatura exigida no momento da emissão) | Daniel | certificado nunca no aparelho; conferência do manual antes da ED-19 (§2.4); alternativa de conta sem chave fiscal com emissão após a sincronização, se o leiaute admitir; senão, novo ADR |
| Aparelho do app de campo perdido ou furtado com dados de consumidores | técnico | dispositivo registrado, banco local cifrado com chave no Keystore, bloqueio `BLOCK` com recusa imediata de sincronização e apagamento no contato seguinte, revogação pelo tenant e pela Central (ADR-0030) |
| Pacote de reversão em Windows Server (ADR-0024) depende de WSL2 e de virtualização aninhada no servidor do órgão | técnico | requisitos mínimos publicados; homologação única na ED-04; nova homologação só se um cliente adotar Windows na reversão |
| Severidade 1 em 2 h e monitoramento 24/7 do nível Setor Público sem equipe | Daniel | equipe do ADR-0029; nenhum contrato no nível Setor Público antes da escala de plantão ativa (até 31/01/2028, ED-05) |
| Editais de 2028 diferentes dos de 2026 | Daniel | matriz viva com requisito canônico (P1.25); extração e matriz por área em até 3 dias úteis da publicação; decisão de disputar registrada (consolidado 18, §7.3) |
| Item de edital contrário à lei cobrado na POC | Daniel | o produto segue a lei (ADR-0015, ADR-0023); pedido de esclarecimento ou impugnação protocolado antes da sessão com os modelos da P1.25; na POC, regra legal demonstrada e norma citada |
| Terceiros novos da trilha: modelo de linguagem (resposta errada, transferência internacional), lojas de aplicativo (revisão e recusa), conectores da Meta e do Telegram (revisão de permissões da Meta, mudança de API, processamento fora do país), ODA File Converter (licença), Superset (segunda pilha) e QGIS com escrita direta no banco | técnico | funções fechadas, minimização e transbordo (ED-43); conectores atrás da mesma interface, com teste por canal e provedores no DPA e no RIPD (ED-42); conta do prestador e publicação antecipada (ED-39); licença conferida na P1.25; role por tenant e teste de isolamento (ED-47, ED-55); pentest dos caminhos novos |
| FEBRABAN 150 necessário no go-live, se o piloto tiver optantes de débito automático | Daniel | levantamento na P0.30, conferido na P1.1; nesse caso a P3.24 (≈ 1,5 semana) entra no P3 e o corte depende dela |
| Prazos legais do prestador antes do go-live | Daniel | contrato explicita que as obrigações seguem no legado até o corte; nenhuma data regulatória prometida antes do go-live |
| Multa acima de 2% aceita pelo tenant (CDC art. 52 §1º) | Daniel | aceite formal com responsável e justificativa; contrato aloca a responsabilidade ao controlador |
| Limite legal errado no catálogo | Daniel | norma citada e teste por limite; revisão quando a norma muda |
| Schema por tenant no limite | técnico | 300 schemas (P0.21); teto por cluster; shard |
| Dado real antes do R1 ou do acordo de tratamento | técnico | `dev-env` recusado em `prod`; importador exige cofre ativo e acordo registrado |
| Homologação em terceiros (SEFAZ, PSP, banco, gráfica) | Daniel | credenciamento a partir do P1; simuladores |
| Fim de suporte (Spring Boot 4.1 em 31/07/2027; PostgreSQL 15 em nov/2027) | técnico | P3.14; P0.4 |
| Escopo da versão 2.1 (marca, temas, Central, cobrança, suporte, portais) sobre a mesma capacidade | Daniel | Central e suporte crescem por fase (v0 no P1); P4 cede antes do go-live; tarefas fora do caminho crítico escorregam primeiro |
| Renomeação em massa (P0.33) quebra build, cifra ou contrato da API | técnico | tag `pre-rename`; nenhum branch aberto; commit só de `git mv` separado do de conteúdo; teste de vetor fixo da cifra; teste de contrato dos códigos de problema, com backend, frontend e testes no mesmo commit; busca residual no CI |
| Retenção longa de chamados com dado pessoal (vigência mais 5 anos) | Daniel | minimização no formulário, acesso restrito e registrado, expurgo com contagem, previsão no DPA |
| XML do intervalo antes do go-live não entregues ou incompletos | Daniel | pedido formal ao piloto na P1.1; relatório de recusas da P2.19; o portal mostra a origem de cada documento e não inventa o que falta |
| CPF/CNPJ como identificador de login do portal: enumeração de titulares ou vazamento do documento | técnico | documento cifrado e com índice cego, nunca em URL nem em log (inclusive eventos do Keycloak); resposta e tempo iguais para conta inexistente e senha errada; limite de tentativas com bloqueio progressivo; pentest (P3.8) |
| Mapeamento de login por atributo sem suporte pronto no Keycloak | técnico | autenticador próprio coberto por teste na P3.3; revisão a cada atualização do Keycloak; o e-mail continua como identificador alternativo |
| Contestação sem resposta em 15 dias estoura o prazo de 75 dias da eliminação | Daniel | alerta na Central antes do vencimento da resposta; ensaio de saída com contestação (P3.16) |
| Acesso de emergência usado fora de incidente real | Daniel | só `PLATFORM_OWNER`, incidente declarado e registrado, reautenticação, somente leitura, aviso ao tenant em 24 h e revisão de cada uso na simulação de incidente (P3.10) |

---

## 7. Resumo

| Fase | Período | Entregas principais | Bloqueios |
|---|---|---|---|
| P0 | 13/10–27/11/2026 | dev fora do Drive, renomeação `ziipsan`, CI, Dockerfile, versão pela tag e changelog, PG 17, segurança, BFF, MFA, papéis de plataforma, pacote de marca, temas, parâmetros, 300 schemas, carga inicial; ambientes na AWS `sa-east-1`, PITR, R1; kit de habilitação | piloto (com critério de atestado) |
| P1 | 30/11/2026–29/01/2027 | importador v0, R2, R3, console e Central v0, marca do tenant, preferência de tema, ClamAV, ajuda e chamados v0, Sobre, partição do `audit_log`, outbox, porte fiscal; roteiros de POC, matriz viva e modelos jurídicos | piloto; R1 |
| P2 | 01/02–26/03/2027 | lote, R4, R4a, NFAg-e e XML imutável, retificação e manutenção de conta, leitura terceirizada, gráfica, carga, importador v1 e documentos anteriores ao go-live; cobrança por contrato, NFS-e da venda e área do tenant; SLA de suporte com cinco severidades; Central de faturamento e fiscal | P1.9, P1.16; SEFAZ |
| P3 | 29/03–28/05/2027 | R5a, R5b, portal com histórico e documentos, R7 mínimo, NFS-e avulsa, pentest, contrato, operação; Central completa, acesso assistido e de emergência, página de status, ensaio de saída; relatórios operacionais; ambiente de demonstração e ensaio de POC com meta; FEBRABAN 150 legado se houver optantes (P3.24); plano de contratação da trilha | escopo de G4; PSP; optantes do piloto |
| Go-live | 01/06–30/07/2027 | paralelo e corte | aceite do piloto |
| P4 | 02/08–17/12/2027 | R8, R6, BI ampliado, app de campo Android, R5c, isolamento por porte, suporte por e-mail | demanda |
| Trilha de editais | a partir de 02/08/2027, com a equipe do ADR-0029; prontidão de POC em 30/06/2028 | ED-01 a ED-62 (menos ED-60 e ED-61) em ondas A (comercial, campo com autenticação e emissão offline, suporte), B (canais com Messenger, Instagram e Telegram, BI, qualidade, GIS, instância dedicada) e C (implantação, almoxarifado com alçada e frota, aderência total) | contratação e custo da equipe; leiaute de contingência da NFAg-e; parceiro de implantação |
| 2028 | — | primeira licitação com POC (2º semestre); onda C concluída no 3º trimestre; GRS, R9 sem o GIS, ISO 27001 | atestado; prontidão de POC; demanda |
| 2029 | — | compras, RH (ED-60) e orçamento (ED-61), sob demanda | cliente que os contrate |

---

## 8. Governança

- **Release.** `main` protegida; PR com CI verde; versão semântica gerada da tag (ADR-0021), com notas de versão e item
  obrigatório no changelog do cliente para segurança, legislação e parâmetro regulatório; homologação antes de
  produção, com aprovação manual; `migrate` antes do rollout; rollback da imagem testado; janela de manutenção avisada
  com a antecedência do SLA.
- **Ambientes.** Local (sintético), CI (banco efêmero), homologação (sintético ou anonimizado) e produção, a única com
  dado real e com cofre obrigatório. Segredos só no cofre.
- **API.** `/api/v1`; OpenAPI no repositório, conferido no CI; mudança aditiva na mesma versão; incompatível vai para
  `/api/v2`, com convivência mínima de seis meses.
- **Feature flags por tenant.** No console (core `V9`); módulo contratado é flag de plano; flag de rollout tem dono e
  data de remoção; mudança vai para a auditoria.
- **ADRs.** Regras em `arquitetura/adr/README.md`; decisão nova só por ADR, que este plano cita sem repetir.
- **Idioma (decisões do dono do produto, 12/10/2026).** Tabelas, colunas, schemas, valores de enumeração gravados,
  rotas da API e do frontend, cabeçalhos HTTP, propriedades, permissões, chaves de configuração e de arquivos de
  conteúdo, pastas e identificadores de código em inglês, como o código atual (`consumer_units`, `'ACTIVE'`,
  `/api/registry/consumer-units`); textos exibidos ao usuário, conteúdo de ajuda, mensagens e documentação em português. Rotas
  novas: `/help`, `/support`, `/about`; `/api/v1/public/status` se mantém. Os códigos de contrato da API também ficam
  em inglês: prefixo `urn:ziipsan:problem:` e códigos de problema em minúsculas com hífen (`stale-version`,
  `mfa-required`), aplicados na P0.33 (tabela no P0) e congelados depois dela (MARCA-E-TEMAS §5.5); `title` e
  `detail` do problem+json continuam em português. Única exceção: o formato do protocolo exibido ao usuário
  (`SUP-AAAA-NNNNNN`). Nomes definidos fora do produto, como campos de leiautes oficiais (`tpAmb`, `cStat`) e código
  de terceiros citado (C# do Ziip Cloud), ficam como na fonte. Em revisão de PR e de documento, nome novo em
  português é corrigido antes do merge.
- **Diário.** `docs/DIARIO-DE-BORDO.md` ao fim de cada tarefa: o que foi feito, testes, pendências, commit.
- **Push automático (ADR-0009).** Fim de etapa com suíte verde → commit e push; vermelho não publica.
- **Aceite de fase.** Todos os critérios cumpridos e registrados; tarefa que escorregar recebe nova data aqui e no diário.
  A partir do P1, todo fim de fase tem ensaio interno de POC com resultado registrado; a partir do P3, a meta de ensaio
  da fase entra no aceite (P3: P3.23; P4: fim das ondas A e B da trilha de editais), conforme o ADR-0023.
- **Matriz de editais.** A matriz viva (P1.25; consolidado 18, §7) é atualizada ao fim de cada tarefa que toca
  requisito de edital, a cada ensaio e a cada edital novo; teste que prova requisito leva o ID do requisito. Edital
  novo segue a regra do consolidado 18, §7.3 (extração, equivalência, matriz por área, conflitos legais, decisão de
  disputar).
