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
| D7 | Keycloak 26, banco próprio | = modo de produção na P0.29 (local) e na LC.6 (AWS) |
| D8 | API na 8090 | = |
| D9 | Cifra de dev por HKDF; só dado sintético até o R1 | Δ `dev-env` só em lista fechada de perfis (17:M2); até o lançamento, só dado sintético ou anonimizado na origem, com R1 sobre o cofre local (ADR-0033) |
| D10 | Migrations por job separado | Δ a JVM da API não recebe credencial de migração (17:S2) |
| D11 | Rascunhos e `validate-step` no R2 | = (P1.3) |
| D12 | ADMIN, MANAGER, OPERATOR, VIEWER, `registry.reveal-document` | Δ novos: busca por documento, aprovação de parâmetros, plataforma |
| D13 | `oidc-client-ts`, token em memória | Δ **substituída pelo ADR-0006** (BFF); hoje o token está em `sessionStorage` |
| G10a | `outOfOrder(true)` + teste "tenant antigo alcança o atual" | Δ só até o fim do P3; depois, versões sempre crescentes (17:D1) |
| S1–S3 (v1) | `DocumentNumber` no core; CPF mascarado no PDF do MTR; `drivers` cifrado | = implementadas; máscara segue o ADR-0008 |

### 2.2 Decisões vigentes, por tema

Uma linha por tema, com a decisão em vigor e a fonte (ADR ou cartão do dono do produto). O texto completo está no ADR;
a evolução, no histórico de versões. Quando uma decisão anterior foi superada, a linha diz o que a substitui.

| Tema | Decisão vigente | Fonte |
|---|---|---|
| Roadmap | P0 a P4, homologação local do piloto (GL), trilha de editais em ondas A, B e C, pacotes pendentes e sob demanda e lançamento comercial (LC) depois de todas as fases e ondas, exceto os pacotes pendentes. Substituído: o "go-live piloto" da v1 passou a homologação local (ADR-0033) | ADR-0001; ADR-0033; cartões de 28/10/2026 (PD-01, PD-05) |
| Datas | O projeto não tem prazos: datas de fase, marco e lançamento são datas de referência; a ordem vem da dependência; datas de norma são fatos. Substituído: "metas de calendário" da v2.12 (ADR-0035, item 1) | ADR-0044 |
| Escopo do plano | O plano trata do que e de como; equipe, contratação e capacidade ficam fora; tamanho é indicação de esforço; sem reestimativa agora. Substituído: ADR-0029, revogado na v2.12 | ADR-0035; cartão de 28/10/2026 (decisão 5) |
| Ambiente e lançamento | Desenvolvimento local em Docker Compose (PostgreSQL 17, Keycloak, emulador de S3, cofre local) até o lançamento; código pronto para a AWS por interfaces com teste de contrato; nenhum dado pessoal real na base local; produção, emissão fiscal real e atestado só no lançamento, pela opção A (as opções B e C ficaram rejeitadas); exceções: e-CNPJ da Ziip/TGP no cofre local e XML assinados da homologação fiscal em área separada, apagados ao fim de cada ciclo; rede real do piloto com ligações, ramais e imóveis anonimizados na origem | ADR-0033 (ajustes v2.7 a v2.11) |
| Hospedagem | AWS `sa-east-1` gerenciada (RDS PostgreSQL 17 Multi-AZ com PITR e réplica de leitura, KMS, Secrets Manager, S3 com Object Lock); nenhum dado, réplica ou backup fora da região; desastre regional por Multi-AZ mais cofre isolado em conta separada na mesma região, com risco residual declarado no contrato; SOC 1 e SOC 3 são relatórios de asseguração, não certificações | ADR-0011; cartão de 22/10/2026 (PLT-04) |
| Infraestrutura congelada | Componente novo só por exceção registrada no ADR-0010: ClamAV, Superset, ODA File Converter, cofre local, emulador de S3, pgRouting, endpoint do QGIS, GeoServer (WMS da onda B) e ingestão de telemetria por REST e arquivo; ingestão de supervisório somente leitura por REST e arquivo, sem broker, em ADR proposto | ADR-0010; ADR-0046 (proposto) |
| Desenvolvimento e publicação | Desenvolvimento em disco local com GitHub; push automático com testes verdes. Substituído: D2 da v1 (push só com autorização) | ADR-0009 |
| Banco e assíncrono | PostgreSQL 17 com PostGIS 3.5 no P0; outbox no PostgreSQL com workers `SKIP LOCKED`; isolamento híbrido por porte, com shard no catálogo e teste com 300 schemas; schema compartilhado e banco dedicado só com demanda (P4.6, seção de pacotes pendentes) | ADR-0013; ADR-0014; ADR-0005; cartão de 28/10/2026 (PD-05) |
| Metas não funcionais | Metas do §6 da revisão 17 como critério de aceite | ADR-0012 |
| Parâmetros do tenant | Parâmetros com vigência, sugerido, limite e aceite; classes a a d; multa padrão 2%, com aceite reforçado acima; vedações de corte da Lei 14.015 travadas; piso federal da tarifa social; pisos de retenção; toda bifurcação normativa vira parâmetro com padrão de fábrica mais protetivo ao usuário, e o que lei federal proíbe fica travado. Padrões confirmados em 21/10/2026: `bill.field_unreturned_rule = DIVERGENCE_QUEUE`, `fiscal.central_billing_invoice_mode = PER_UNIT`, `sac.deadline_counting_rule = CALENDAR` (com `BUSINESS` opcional) | ADR-0002; ADR-0015; ADR-0038; cartão de 21/10/2026 |
| Unidade consumidora e OS | Unidade consumidora é a raiz do domínio; toda solicitação ou alteração de cliente ou de unidade gera OS, de campo ou interna, com efeito no encerramento; solicitação externa nasce sempre como protocolo de atendimento, que gera a OS; alteração interna do backoffice gera OS sem protocolo; a OS tem um ou mais alvos tipados e hierarquia de ativos de seis níveis | ADR-0039 (item 7 confirmado em 28/10/2026); ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS; cartão de 28/10/2026 (decisão 7) |
| OS na plataforma | Motor de OS no núcleo da plataforma; tipos de serviço e alvos de saneamento no pacote `sanitation`. Substituído: OS listada no pacote de vertical em PLATAFORMA-E-VERTICAIS §2 | ADR-0045 |
| Ativos | Cadastro mínimo de ativos e hierarquia de seis níveis no P1 (P1.35), junto do núcleo de OS; a ED-56 fica com inventário regulatório, manutenção e risco. Substituído: hierarquia nascendo na ED-56, onda C | cartão de 28/10/2026 (COE-05) |
| Cadastro | Fatura em nome do usuário titular (NR 11, art. 33, §2º), com opção do tenant de exibir também o proprietário; atividade econômica (CNAE) na unidade consumidora; usuário avulso como unidade consumidora de tipo próprio | ADR-0039, item 2; cartões de 28/10/2026 (decisão 6, PD-06); cartão de 14/10/2026 |
| Perfis de acesso | Catálogo `recurso:ação` no P0 (P0.13); perfis de fábrica por função, cada um com menu e página inicial (P1.37); unidade organizacional e escopo por área no P3 (P3.30); política de senha por tenant por autenticador próprio no realm único (ED-06), testada a cada atualização do Keycloak, lida com a nota de alinhamento ao NIST SP 800-63B-4 | ADR-0040; cartão de 28/10/2026 (JOR-01) |
| Autenticação | BFF com cookie HttpOnly no navegador; MFA configurável por tenant, sempre na plataforma (prevalece sobre o 17:S4); apps pelo navegador do sistema com PKCE, token no armazenamento seguro, dispositivo de campo registrado | ADR-0006; ADR-0007; ADR-0030 |
| Dado pessoal | Máscara de documento configurável com teto; busca por documento com papel, registro e cota; LGPD do titular (direitos, registro de operações de tratamento, canal no portal) é obrigação de todo tenant (P3.40). Substituído: núcleo de LGPD dentro do pacote voluntário ISO/IEC 27701 (ED-74) | ADR-0008; cartão de 28/10/2026 (JOR-15) |
| Portal do consumidor | Histórico de 120 meses, conta paga, DANFAG e XML original; conta própria no realm `ziipsan-portal`, por CPF, CNPJ ou e-mail com senha, sem SMS; vínculo por código impresso e documento do titular; login gov.br só para tenant de direito público com a integração aprovada; pagamento sem conta pelo código de vinculação, sem dado pessoal exibido. Substituído: o item 4 do ADR-0022 (só conta própria) | ADR-0022; ADR-0043 |
| Fiscal | Motor fiscal do Ziip Cloud portado para Java; NFAg-e (modelo 75) e NFS-e avulsa sobre ele; emissão em campo com o mesmo módulo de cálculo no servidor e no app, vetores comuns no CI e chave em contingência se o leiaute permitir. Substituído: integrador fiscal da v1 | ADR-0003; ADR-0031 |
| SINISA e SINIR | Assistidos: arquivo oficial, o cliente envia. SINIR e MTR na trilha GRS sob demanda (ED-104) | ADR-0004; RAS-17 |
| Marca e interface | Pacote de marca único, marca do tenant, renomeação `sigsan` → `ziipsan` no P0 com contexto HKDF congelado e códigos de problema em inglês; temas claro, escuro e do sistema, padrões Azul Água e Verde Água com os valores HSL de MARCA-E-TEMAS §4.2; ícones Lucide e próprios; símbolos do mapa em SVG de fonte única, gerados no desenvolvimento com aceite do dono por lote | ADR-0016; ADR-0017; ADR-0036 |
| Verticais | Plataforma com pacotes de vertical; saneamento é o primeiro e único com escopo; saúde e segurança pública só por ADR futuro com análise jurídica; domínio do GIS por referência (`gis_domain`) | ADR-0037 |
| Central Ziip e cobrança do SaaS | Central própria com o console; papéis de plataforma com MFA; sem leitura de dado pessoal de tenant; cobrança por contrato, sem bloqueio automático. Suspensão sempre manual, com aviso prévio, backoffice em somente leitura e portal, Pix, retornos e 2ª via ativos; suspensão e bloqueio por `PLATFORM_OWNER` ou `PLATFORM_FINANCE` (este só por inadimplência contratual), eliminação só por `PLATFORM_OWNER`, sempre uma pessoa com MFA, justificativa e auditoria; eliminação com carência de 30 dias e certificado de descarte, suspensa por contestação (resposta em até 15 dias, máximo de 75 dias do fim do contrato) ou por ordem judicial | ADR-0018; ADR-0019; ADR-0015, item 5; cartões de 12/10/2026 |
| Suporte e versão | Ajuda e suporte com chamado `SUP-AAAA-NNNNNN` e SLA por contrato, severidades S1 a S5; chamados retidos pela vigência mais 5 anos, com minimização; ClamAV junto do `worker`; acesso assistido com consentimento, e de emergência só em incidente de segurança declarado (somente leitura, `PLATFORM_OWNER`, MFA no momento, aviso ao tenant em 24 h); SemVer gerado no build e changelog do cliente obrigatório para segurança, legislação e parâmetro regulatório | ADR-0020; ADR-0021; cartões de 12/10/2026 |
| Editais e POC | Editais como suplemento de planejamento; POC é escopo, com ensaio interno ao fim de cada fase e meta no aceite a partir do P3; meta final: VISAN ≥ 146 de 162 e nenhuma falha crítica no SAMAE, em dois ensaios seguidos; primeiro atestado pelo piloto público em SC, segundo por cliente privado; itens de edital contrários à lei seguem a lei, com esclarecimento ou impugnação; primeira licitação com POC depois do primeiro atestado. Substituído: "licitações de 2028" (ajuste v2.8 do ADR-0023) e "Fora do escopo: POC, editais" do diário | ADR-0023; diretriz de 21/10/2026 |
| Instância dedicada e reversão | SaaS multi-tenant; instância dedicada para quem exigir em contrato (LC.15, depois da LC.2); pacote de reversão em contêiner, também em Windows Server 2022+ com WSL2, na onda B (ED-04) | ADR-0024; cartão de 28/10/2026 (COE-04) |
| Nível de serviço | Nível "Setor Público" contratável (99,9%, cinco severidades, exportação em 10 dias úteis, consulta somente leitura por até 120 dias após o contrato), acionado a partir do lançamento; padrão do produto inalterado | ADR-0025 |
| Apps | Capacitor: app de campo só Android por APK, com leitura e emissão simultânea, OS e impressão Zebra; app do cliente Android e iOS nas lojas, com a marca do tenant. Substituído: P4.4 (fundida na ED-18) | ADR-0026; ADR-0030 |
| BI | Relatórios e painéis padrão nativos (P3.22); Apache Superset embarcado na trilha, na réplica de leitura, com role de banco por tenant; nenhum relatório nem consulta de BI no primário (ED-46, ED-47). Substituído: P4.3 (fundida na ED-46, ED-47 e ED-49) | ADR-0027 |
| Escopo funcional da trilha | Qualidade da água com apoio à ISO/IEC 17025; GIS web próprio; omnichannel com WhatsApp, Messenger, Instagram e Telegram e chatbot por modelo de terceiro; dívida ativa; eventos contábeis e e-Sfinge assistido; Pix Automático e FEBRABAN 150 legado condicional ao piloto (P3.24 só com optantes); negativação e protesto por arquivo; cartão pela credenciadora do cliente; telemetria por REST e arquivo; ERP no recorte "núcleo saneamento + integração" (almoxarifado e frota construídos, integração com Betha, IPM e Fiorilli); compras, RH (ED-60) e orçamento (ED-61) em 2029, sob demanda | ADR-0028 (ajustes v2.5, v2.10, v2.12) |
| Material da OS | Requisição com alçada por valor e por item, reserva na aprovação, baixa automática e devolução no encerramento, saldo negativo com alerta e regularização; almoxarifado (ED-58) na onda A, junto da OS completa (ED-36) | ADR-0032 (ajuste v2.6) |
| GIS | Água, esgoto, drenagem e resíduos no mesmo modelo; catálogo brasileiro de fábrica extensível; edição por pacotes de edição revisados por outra pessoa, ciclo de vida e "rede na data"; núcleo no P1 a P3; precisão por feição (padrão ±0,10 m H e ±0,05 m V); QGIS por views `INSTEAD OF` que gravam no pacote de edição; EPANET e SWMM; normas ABNT compradas no início do P1 (P1.28); DGN v8 por conversão assistida; fora do backoffice autenticado, imóveis e ligações só agregados com no mínimo 5 imóveis por célula (`gis.public_aggregation_min_units`, classe c); OGC API – Features e WFS na `api`, WMS pelo GeoServer na onda B. Substituído: "WMS na `api`, sem servidor de mapas" (v2.11) | ADR-0034; ADR-0035 |
| Certificações de qualidade | Catálogo único de normas: voluntárias e de referência ativáveis, obrigatórias (inclusive NR-1, 18, 33 e 35 do MTE) não desativáveis; desligar não apaga registro; o módulo não certifica; área "Normas e legislação" carregada do catálogo de normas (P1.34); validação do sistema por versão para a ISO/IEC 17025 pendente até haver cliente que a exija | ADR-0041; cartões de 21/10 e 22/10/2026; cartão de 28/10/2026 (PD-08) |
| IA | Nenhum modelo sem humano no circuito, auditoria, mascaramento e desligamento; IA não decide corte, tarifa social, negativação nem acesso a serviço essencial | ADR-0042 |
| Arrecadação e cobrança | Efeito da classificação do pagamento na P3.1; devolução e pagamentos não identificados na P3.29, antes da homologação local; parcelamentos ativos do piloto migrados como prestações a vencer; seleção mínima do aviso e do documento de cobrança na P3.4; ação de cobrança completa na P4.1; ouvidoria e interrupção programada na ED-35; caixa próprio do prestador como item condicional da ED-26, por parâmetro; cobrança por empresa terceirizada (ED-68) parada como pacote pendente | cartões de 14/10/2026; cartão de 28/10/2026 (PD-07) |
| Aprimoramentos 22a a 23e | Todos os 156 achados dos levantamentos 22a a 22d e todas as recomendações dos relatórios 23a a 23e aprovadas, com tarefa dona por achado; API pública com prioridade alta (P2.26); limpeza do código antes da renomeação (P0.25) | cartões de 22/10/2026 e de 28/10/2026 (decisão 2) |
| Idioma | Identificadores, tabelas, rotas, permissões e códigos de problema (`urn:ziipsan:problem:`) em inglês; textos ao usuário e documentação em português; única exceção: o formato `SUP-AAAA-NNNNNN` (§8) | cartões de 12/10/2026 (v2.2 e v2.3) |
| Governança dos ADRs | Linha de ajuste só para esclarecimento; mudança de substância por ADR novo que substitui o anterior | README dos ADRs, regra 2; cartão de 28/10/2026 (PD-03) |

Os ADRs 0016, 0018, 0022, 0027 e 0028 citam o go-live e leem-se pela regra do ADR-0033 (item 7). As linhas de ajuste
gravadas nos ADRs até a v2.21 continuam válidas como estão; daqui em diante vale a regra 2 do README dos ADRs.

### 2.3 G1–G9 reclassificadas e padrões do produto

Padrões definidos pelo dono do produto em 12/10/2026 estão na última coluna.

| # | Tema | Classe | Fase | Padrão do produto |
|---|---|---|---|---|
| G1 | Agência, norma, NR 11/13 | parâmetro | P1; perfis das agências de SC carregados e testados na P2.31 | — |
| G2 | Tarifa social | parâmetro | P1 | piso federal travado no sistema após conferência no DOU; o tenant só concede benefício maior |
| G3 | NFAg-e | produto | P1–P2 | construir sobre o motor portado (ADR-0003) |
| G4 | Bancos, convênios, Pix, PSP | parâmetro, dentro do escopo suportado (§2.4) | P3 | — |
| G5 | Multa e juros | parâmetro | P2 | multa padrão 2%; acima de 2% só com aceite formal registrado |
| G6 | Retenção e prescrição | parâmetro | P2 | pisos fixos pelo Ziip SAN (logs de acesso 6 meses, Marco Civil; histórico 120 meses, NR 11); o tenant só amplia; eliminação no fim do contrato por procedimento do Ziip com certificado de descarte |
| G7, G8 | SINISA, SINIR | produto | SINISA no P4 (P4.2); SINIR na trilha GRS sob demanda (ED-104) | assistidos (ADR-0004) |
| G9 | Corte | parâmetro | P3 | vedações da Lei 14.015 (sexta, fim de semana, feriado, véspera) para todos os motivos; exceção só emergência técnica com justificativa e foto; outra regra só com aceite formal |

Sem valor aceito e vigente, a operação falha com erro explícito; o tenant não entra em produção sem o conjunto
obrigatório aceito.

### 2.4 Pendências de decisão

Pendências descritas pelo que falta decidir, sem data (ADR-0044). A coluna "Antes de" indica a tarefa ou o marco que
precisa da decisão para começar; enquanto ela não vem, a tarefa não começa, ou começa só na parte que não depende dela.

**Em aberto**

| Decisão | Dono | Antes de | Afeta |
|---|---|---|---|
| Cliente piloto: público, em SC, contratado por dispensa ou CPSI, com cláusula de atestado (ADR-0023); acesso ao legado; termo de cooperação para a homologação local com dados anonimizados na origem (o acordo de tratamento de dados passa à LC.9, ADR-0033); aceite de que a produção só começa no lançamento (opção A); existência e quantidade de optantes de débito automático e leiaute bancário em uso; existência de guichê próprio de arrecadação (caixa do prestador, ED-26, PD-07) | Daniel | P1.1 e P1.2 (M3) | P1.1, P1.2, P3.24, ED-26, GL.1, LC.14 |
| G4 no produto e parceiros de homologação: leiautes (CNAB 240 e, se o piloto tiver optantes, FEBRABAN 150) e PSPs e bancos suportados na homologação local e no lançamento | Daniel | P3.1 e P3.2 | P3.1, P3.2, P3.24 |
| NFAg-e: manual e XSD oficiais (verificado em 22/10/2026: o portal da SVRS ainda indica o manual em forma de minuta, e o Anexo II, DANFAG, v1.02, é de 25/08/2026), inclusive o regime de contingência (chave gerada fora do autorizador, assinatura no momento da emissão, prazo de transmissão), de que depende a emissão em campo (ADR-0031, item 4); se a tarefa "confirmar no DOU a obrigatoriedade e a data" ainda procede, já que a data de 01/12/2026 consta do Ato Conjunto RFB/CGIBS 4/2026 (decisoes-a-rever, lote 2, item 1); aceitação do CNPJ da Ziip/TGP como emitente de teste do modelo 75 na homologação da UF do piloto (se não for aceito, a forma da P2.6 volta ao dono do produto); se o leiaute admite NFAg-e única por fatura agrupadora do órgão centralizador (`fiscal.central_billing_invoice_mode`) | Daniel | P2.6 (manual e XSD); ED-19 (contingência) | P2.6, ED-19 |
| Arredondamento (half-even no C#, `HALF_DOWN` no billing) e tributo no CHECK de total de `bills` (hoje tratado como retenção); a NT 007/2026 não é a base normativa do arredondamento de IBS e CBS, e a regra fica sem norma citada até a leitura dos anexos da NT 004, das NTs 008 e 009 e do manual da NFS-e | técnico | P1.16 (e P2.4, P2.5) | P2.4, P2.5; vetores comuns do ADR-0031 |
| Disponibilidade de `pgrouting`, `pg_jsonschema`, `pgaudit` e do SRID 5396 no RDS (ADR-0034) | técnico | P0.26 (documental); LC.1 (prática) | P2.21; GIS-10; lançamento |
| Aceite do ADR-0046 (ingestão de supervisório somente leitura por REST e arquivo, sem broker), registrado como proposto (PD-04) | Daniel | início da onda A (ED-17) | ED-17, ED-87, ED-93 |
| Liberação dos pacotes pendentes: ED-68 (cobrança por empresa terceirizada), recorte da ED-102 (obras e convênios de investimento), ED-103 (antecipação de recebíveis), ED-104 (trilha GRS) e P4.6 (schema compartilhado e banco dedicado, só com demanda); nenhuma tarefa fora da seção depende deles (PD-05, PD-09) | Daniel | entrada de cada pacote | seção de pacotes pendentes |
| Pacote de validação do sistema por versão para a ISO/IEC 17025: pendente até haver cliente com laboratório acreditado que o exija (PD-08) | Daniel | contrato que o exija | ED-51 |
| Nome comercial das verticais e relação com "Ziip SAN" (ADR-0037, item 7) | Daniel | primeiro material externo que cite outra vertical | nada no código; textos de marca e do Sobre |

**Pareceres jurídicos, na ordem do PD-10.** O roteiro das perguntas está em
[normas/questoes-para-parecer-juridico.md](normas/questoes-para-parecer-juridico.md). Nenhum desses pontos vira regra do
produto antes do parecer.

| Ordem | Questão | Antes de | Efeito no plano |
|---|---|---|---|
| 1 | RAS-06: se a cobrança de água e esgoto é "convênio de prestação de serviço público" para o prazo de adequação do débito em conta (01/01/2027, Res. CMN 5.278/2025 e Res. BCB 544/2025) e para o novo regime geral (01/07/2027, Res. BCB 565/2026); inclui a premissa de os bancos do piloto aceitarem o legado só em caso intrabanco | P3.24 e ED-27 (e P4.5) | se enquadrar, o FEBRABAN 150 legado perde objeto no corte e o trabalho vira conversão para Pix Automático com consentimento guardado |
| 2 | RAS-11: adesão do prestador ou do ente à MP 1.393/2026 (Desenrola Brasil 3.0) e plano de repactuação por superendividamento | P4.1 | cessão a programa federal e suspensão da cobrança durante a repactuação |
| 3 | RAS-27: alcance da retenção na fonte por órgão público (IN RFB 1.234/2012) quando o prestador é empresa estatal (Solução de Consulta Cosit 108) | P3.1 e ED-26 | baixa "com retenção", com o valor retido como crédito do tenant |
| 4 | RAS-28: obrigação do valor aproximado dos tributos na conta (Lei 12.741/2012) | P2.32 (e P2.17) | campo na fatura com fonte de alíquotas, se aplicável |
| 5 | Protesto de CDA de créditos de água e esgoto (ADI 5135): depende de o prestador poder inscrevê-los em dívida ativa | ED-30 e ED-31 | protesto de CDA como opção do tenant ou fora do produto |

**Decisões a rever depois da correção das citações normativas.** Premissas que o dono do produto, o jurídico ou o
regulatório do cliente devem reavaliar, conforme [normas/decisoes-a-rever.md](normas/decisoes-a-rever.md). Os itens
de débito automático, MP 1.393, retenção por estatal e protesto de CDA estão nos pareceres acima.

| Premissa a rever | Antes de |
|---|---|
| Manual da ARSESP: usar a versão 2 de 2025 (Deliberação ARSESP 1.636/2025) como referência de codificação e na comparação com o MCPSB | P1.27 e P1.35 |
| Vida útil por perfil de agência citando a NR ANA 16/2026 (Resolução ANA 302/2026) e o MCPSB, e não o Voto 14/2026 | ED-56 |
| Classes de resíduo da série NBR 10004:2024 (Classe 1 e Classe 2, com transição até 31/12/2026) no domínio do GIS e no modelo `waste_class`, a conferir no texto da ABNT | ED-65 e ED-104 |
| Fonte do bueiro (`CULVERT`) no catálogo de drenagem: DNIT 023/2006-ES e 025/2004-ES no lugar da 030/2004-ES | ED-64 |
| Revisão 04 da IN da Saneago (11/03/2024) no perfil de importação | P3.26 |
| Classificação "O (técnico)" do OGC Simple Features, MUDDI como referência só conceitual e formato dos tiles vetoriais (a ISO 19177-1 define a API, não a codificação) | P3.25 |
| Taxa legal do art. 406 do Código Civil pela taxa divulgada pelo BCB (Res. CMN 5.171/2024) | P2.5 e P3.1 |
| Mínimo existencial: estudos anuais do CMN, sem revisão anual obrigatória do valor | P4.1 |
| Prescrição: separar a pretensão de cobrança pelo prestador (Temas 251 a 254) da repetição de indébito pelo usuário (Súmula 412, Temas 154, 155 e 932) no prazo padrão e na retenção de 120 meses | P0.20 e P2.20 |
| Devolução em dobro em serviço público, sem marco temporal da modulação; Tema 929 com acórdão a publicar | P3.29 |
| CNAB 444 não é padrão FEBRABAN | P3.1 |
| Etapa da NFS-e Padrão Nacional de cada serviço avulso (01/10/2026, 01/12/2026, 01/01/2027) | P3.6 |
| PNQS sem perfil de exportação definido no regulamento: classe `VOLUNTARY` e não `EXPORT_PROFILE` | ED-82 |
| Data de desligamento do FEBRABAN 150: a Res. CMN 4.790/2020 segue em vigor até 30/06/2027 e o regime operacional vem da Res. BCB 565/2026 | P3.24 e ED-27 |
| O que o edital pede ao exigir SOC 1 e SOC 3 (em geral SOC 2 Tipo 2) | primeira proposta com POC |
| Classes protegidas no corte no DF (Lei Distrital 5.990/2017 declarada inconstitucional; Res. ADASA 14/2011, art. 122) | ED-97 (perfil fora de SC) |
| Verificação inicial de medidores: a Portaria Inmetro 657/2025 está suspensa; nada se constrói sobre declaração de conformidade | ED-14 |
| Canal da notificação prévia à negativação (Tema 1.315, notificação eletrônica com prova de entrega) | ED-30 |
| Retenção de registro de incidente por ente público pelo art. 10, §2º, da Res. CD/ANPD 15/2024 | P3.10 |
| Limites de fluoreto e radioatividade (Anexo XXI da Portaria 888; Anexo L da NN CNEN 3.01) | ED-50 |
| Fatores Q, P e U da CP 03/2026 da ANA sem base nas fontes lidas | ED-77 (GES-30) |
| Contingência hídrica: ADASA 20/2016 revogada; isenção pela ADASA 17/2016 a verificar | ED-91 (GES-07) |
| IN BCB 746/2026: revogação do teto fixo de R$ 500, sem novo valor | P3.2 (GES-33) |
| e-Sfinge: remessa mensal desde 01/01/2022 | ED-33 |
| CVM Res. 244/2026 retira a obrigatoriedade prevista na Res. CVM 193/2023 | ED-90 |
| Assinatura eletrônica: regime dos PSC no DOC-ICP-17 e 17.01; "AD-RB ou superior" a conferir; assinatura gerada em sistema do município só vale como avançada após ratificação (Decreto 29/2023 de São Ludgero) | P3.34 |
| Acessibilidade: WCAG 2.2 AA (P0.36, PLT-31) frente ao RNF-60 e ao ADR-0022 em 2.1 AA; alvo mínimo da NBR 17225:2025 a conferir no texto | P0.36 |
| OGC API – Features: Parte 5 aprovada em 30/04/2026, Parte 4 em minuta | ED-67 |
| Perfil da ARIS: conferir se a RN ARIS 41/2024 alterou os prazos de resposta e de ouvidoria | P2.31 |
| Paraná: Lei 22.130/2024 para prestadores de água que não a Sanepar | ED-97 (perfil fora de SC) |
| Justificativa do escore de crédito no PL 2.338/2023 (acesso a serviço essencial, art. 14, IV) | P4.1 (GES-24) |
| Limites de efluente: nenhum limite da revisão da CONAMA 430 codificado antes da publicação | ED-105 |

**Resolvidas pelo dono do produto em 28/10/2026 (cartões)**

| Decisão | Resultado | Onde foi aplicada |
|---|---|---|
| Plano v3.0 consolidado | Tarefas reescritas com os ajustes no aceite, uma tarefa dona por achado, ondas corrigidas, sobreposições resolvidas por fronteira, pacotes pendentes fora das ondas, histórico em anexo | todo o plano; histórico de versões |
| Recomendações dos relatórios 23a a 23e | Todas aprovadas, em especial perfis de fábrica por função (JOR-01), LGPD do titular para todo tenant (JOR-15), limpeza antes da renomeação (P0.25) e hierarquia de ativos no P1 (COE-05) | P1.37, P3.40, P0.25, P1.35; mapa de donos |
| Login gov.br e pagamento sem conta | ADR que altera o ADR-0022 | ADR-0043; P3.3, P0.38, ED-38 |
| Prazos | O projeto não tem prazos; datas são de referência | ADR-0044; §5, §8 |
| Estimativas | Não reestimar agora; tarefas sem tamanho seguem "a estimar" | §4, §5 |
| Nome na conta | Usuário titular, com opção do tenant de exibir o proprietário; padrão mais protetivo | P1.41, P2.32 |
| Protocolo e OS | Protocolo sempre antes da OS em solicitação externa; alteração interna gera OS sem protocolo | ADR-0039, item 7; P1.36, P1.31 |
| PD-01 | ED-01 depende da onda A, do grupo B1 e do P4; o grupo B2 entra só no critério da LC.1 | ED-01; M12; LC.1 |
| PD-02 | Motor de OS no núcleo; tipos e alvos de saneamento no pacote `sanitation` | ADR-0045; P0.47, P1.31, P1.32, P2.25 |
| PD-03 | Regra 2 do índice de ADRs: ajuste só para esclarecimento; substância por ADR novo que substitui | README dos ADRs |
| PD-04 | ADR proposto antes da onda A para ingestão de supervisório somente leitura por REST e arquivo, sem broker | ADR-0046 (proposto); ED-17 |
| PD-05 | P4.6 na seção de pacotes pendentes, fora do critério da LC.1 | P4.6; LC.1 |
| PD-06 | Atividade econômica (CNAE) na unidade consumidora | P1.3, P2.5 |
| PD-07 | Caixa próprio do prestador como item condicional da ED-26, ligado por parâmetro e confirmado na P1.1 | ED-26, P1.1 |
| PD-08 | Validação do sistema por versão para a ISO/IEC 17025 pendente até haver cliente que a exija | ED-51 |
| PD-09 | ED-68, ED-102 e ED-103 seguem como pacotes pendentes | seção de pacotes pendentes |
| PD-10 | Pareceres jurídicos na ordem acima, começando pela RAS-06 | P3.24, ED-27 e as tarefas da tabela de pareceres |

**Pendências abertas na redação da v3.0 (28/10/2026).** Cada uma traz a recomendação de quem redigiu; nenhuma foi
decidida:

| Pendência | Recomendação | Tarefa |
|---|---|---|
| Níveis do OWASP ASVS no aceite de segurança | nível 2 em geral e nível 3 em autenticação, pagamento e dados pessoais (22c, PLT-08) | P3.8 |
| Padrão de fábrica para unidade consumidora sem usuário vigente | sem titular, com fatura retida até a definição do responsável | P1.41 |
| Uso do pgRouting para o tempo de viagem das equipes | ADR próprio antes de usar (22b, OPE-33) | ED-36 |
| Leitura de "notas do tenant" na Central | avaliação de satisfação (CSAT e NPS) do suporte | P4.8 |
| Instância dedicada e banco dedicado | alinhar a LC.15 ao ADR-0024, que trata a instância dedicada como extensão do modo de banco dedicado da P4.6 | LC.15, P4.6 |
| Tamanho do código de unidade patrimonial (UP) do MCPSB | conferir no manual (a tabela 4 dá 2 dígitos e a figura 2 dá 3) antes da migration | P1.35 |
| Valores de fábrica dos perfis da AGR Tubarão e do CISAM-SUL | o tenant informa onde o texto da norma não foi confirmado | P2.31 |
| Autarquia como contribuinte de IBS e CBS e fato gerador da LC 227/2026 | parecer tributário antes de ligar o cashback | P2.34 |
| Valor aproximado dos tributos na conta (Lei 12.741/2012) | entra só depois do parecer jurídico | P2.32 |
| Nome do parâmetro de baixa com retenção na fonte | fixar depois do parecer da PD-10 | P3.1 |

As pendências resolvidas de 12/10 a 22/10/2026 estão consolidadas no §2.2, e o texto de cada resolução, com as datas
em que estavam previstas, está no histórico de versões e nos ADRs citados.

---
