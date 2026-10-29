# Histórico de versões do plano de execução do Ziip SAN

Registro das mudanças do plano canônico desde a v1, com o texto da seção "O que mudou em relação à v1" do cabeçalho da v2.21 transcrito sem alteração e a entrada da v3.0 ao fim. Os links conservam o caminho original, relativo à pasta `docs/`.

## O que mudou em relação à v1

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
  [18](../levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md), em ondas A, B e C; qualidade da água, GIS web,
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
- Versão 2.6: decisão do dono do produto de 12/10/2026 (cartão): a ED-58 (almoxarifado com reserva, alçada e baixa
  automática, ADR-0032) sai da onda C para a onda A da trilha de editais, junto da ED-36 (OS completa), para que a
  requisição de material da OS afete o estoque desde a primeira onda; compras continua em 2029. Onda A ≈ 116,4
  semanas-pessoa (antes ≈ 108,9), onda C ≈ 15,4 (antes ≈ 22,9), trilha ≈ 206,3 (igual); prontidão de POC ≈ 199 (antes
  ≈ 192); ≈ 4,6 pessoas-equivalentes para junho de 2028 (antes ≈ 4,5), dentro da contratação de 4 a 5 do ADR-0029.
  Ajustados ED-20, ED-36, ED-58 e ED-62, a pendência da onda do almoxarifado (§2.4, agora resolvida), M12, caminho da
  trilha, riscos e resumo; ADRs 0029 e 0032 com linha de ajuste v2.6.
- Versão 2.7: decisões do dono do produto de 12/10/2026 (cartões) sobre ambiente e lançamento (ADR-0033). Todo o
  desenvolvimento passa a ser local, em Docker Compose com PostgreSQL 17, Keycloak, emulador de S3 e cofre local, até
  concluir as fases e o produto estar pronto para venda; a AWS `sa-east-1` (ADR-0011, provedor mantido) só sobe no
  **lançamento comercial**, nova fase depois do P4 (LC.1 a LC.14: ensaio de implantação, IaC, RDS Multi-AZ com PITR,
  KMS e Secrets Manager, S3 com Object Lock, CDN e WAF, observabilidade gerenciada, Keycloak de produção, pentest,
  contrato, carga e paralelo reais, corte e primeiro atestado). P0.26 a P0.29 passam a versões locais, e a nova P0.45
  cria as interfaces de infraestrutura (objetos, chaves, segredos, fila, e-mail) com implementação local e teste de
  contrato. O go-live do piloto vira **homologação local do piloto** (GL.1 a GL.4), com dados sintéticos ou anonimizados
  na origem e nenhum dado pessoal real na base local. Ajustadas P1.2, P1.5, P1.8, P2.6, P2.18, P2.19, P3.3, P3.7, P3.8,
  P3.9, P3.11, P3.13, P3.15, P3.18, P3.22, P3.23, P3.24, P4.7, ED-04, ED-05 e ED-33; caminho crítico, marcos (M4, M5,
  M8 a M10, M13 e os novos M14 a M16), riscos, resumo, §2.1, §2.2, §2.4 e §8 atualizados. O primeiro atestado passa a
  depender do lançamento: duas pendências novas no §2.4 (momento do lançamento, com efeito na meta de licitações de
  2028, e certificado da homologação fiscal local). ADRs 0001, 0010, 0011, 0012, 0013, 0023, 0024, 0025 e 0029 com
  linha de ajuste v2.7.
- Versão 2.8: as duas pendências da v2.7 resolvidas pelo dono do produto em 12/10/2026 (§2.4). **Momento do
  lançamento: opção A**, lançamento comercial depois de todas as fases, inclusive a trilha de editais: início em
  outubro de 2028, primeira produção por volta de fevereiro de 2029, primeiro atestado entre maio e julho de 2029 e
  licitações com POC a partir do 3º trimestre de 2029; a meta de disputar no 2º semestre de 2028 deixa de valer. A
  contratação do ADR-0029 não muda; a análise de capacidade mostra que a prontidão de POC em 30/06/2028 continua
  coerente, por ser anterior ao lançamento, mas o limite passa a ser a trilha completa (≈ 215 semanas-pessoa) até
  30/09/2028, que pede ≈ 3,8 pessoas-equivalentes (a prontidão em junho pedia ≈ 4,6); a folga abre pendência nova sobre
  fixar a equipe em 4 ou adiar a entrada (§2.4). **Certificado da homologação fiscal local:** e-CNPJ da própria
  Ziip/TGP, no cofre local, fora do banco, com acesso registrado e consentimento do responsável, como exceção explícita
  à regra "nenhum dado pessoal real na base local" (ADR-0033). Ajustados P0.26, P0.30, P0.44, P1.8, P2.6, P3.7, fase LC
  (cabeçalho e LC.1), caminho crítico, marcos M3 e M12 a M16, riscos, resumo, §2.2 e §2.4. ADRs 0001, 0023, 0029 e
  0033 com linha de ajuste v2.8.
- Versão 2.9: duas decisões do dono do produto de 12/10/2026 (cartões). **Equipe da trilha:** 4 contratações a partir
  de 02/08/2027 (antes, de 4 a 5, com o número no M8). Com o lançamento em outubro de 2028, a trilha completa (≈ 215
  semanas-pessoa) até 30/09/2028 tem folga de ≈ 15% (capacidade ≈ 247), dentro da incerteza de ±30%, e a prontidão de
  POC (M12) fica por volta do fim de julho de 2028 (faixa de junho a agosto). A quinta pessoa é contratada se a
  velocidade medida de 02/08 a 31/10/2027 ficar abaixo de 90% da planejada, com a regra reaplicada ao fim de cada onda;
  abaixo de 80%, M12, M13 e M14 a M16 são registrados de novo. A pendência do §2.4 sobre o tamanho e a entrada da
  equipe passa a resolvida. **XML assinados na homologação fiscal local:** levam o certificado público da Ziip/TGP, com
  o CPF do responsável, e entram na mesma exceção e no mesmo consentimento do ADR-0033; ficam em área separada da base
  local e são apagados ao fim de cada ciclo de testes, guardados só os vetores de referência sem assinatura. Ajustados
  P1.8, P2.6, P2.18, P3.7, GL.2, caminho da trilha, marcos M8, M11 e M12, riscos, resumo, §2.2 e §2.4. ADRs 0029 e 0033
  com linha de ajuste v2.9.
- Versão 2.10: oito decisões do dono do produto de 12/10/2026 (cartões) sobre o GIS e o cadastro técnico (ADR-0034;
  projeto em [arquitetura/GIS-CADASTRO-TECNICO.md](../arquitetura/GIS-CADASTRO-TECNICO.md)): água, esgoto, drenagem e
  resíduos no mesmo modelo; catálogo brasileiro de fábrica extensível por tenant; edição por pacotes revisados por
  outra pessoa, ciclo de vida e "rede na data"; **núcleo do GIS antes da homologação local** (P1.26, P1.27, P2.21 a
  P2.23, P3.25 a P3.27, entregas GIS-01 a GIS-08, ≈ 22 semanas-pessoa); precisão por feição com meta por tenant;
  simbologia padrão; QGIS por views com `INSTEAD OF` que gravam no pacote; EPANET e SWMM. Na trilha: ED-52 sai da onda
  B para o P1–P3; ED-53 passa à onda A; ED-54, ED-55 e ED-56 crescem; ED-13 cede a base da ligação à P2.23; novas ED-63
  a ED-67 na onda B (GIS-09 a GIS-19 no total). Esforço: antes do P4 ≈ 35,3 semanas-pessoa (antes ≈ 13,3); onda A ≈
  120,9 (antes ≈ 116,4), B ≈ 87,5 (antes ≈ 74,5), C ≈ 16,4 (antes ≈ 15,4); trilha ≈ 224,8 (antes ≈ 206,3); prontidão de
  POC ≈ 216,8 (antes ≈ 199); trilha completa com as tarefas originais do P4 ≈ 233,2 (antes ≈ 215). Com 4 contratados, a
  folga até 30/09/2028 cai de ≈ 15% para ≈ 6% e o M12 vai para meados de agosto de 2028; o núcleo cai sobre o dono
  sozinho no P1–P3, sem folga, o que abre duas pendências (§2.4): capacidade para o núcleo e revisão do gatilho da quinta
  contratação. Mapa Flyway com `V13_1` a `V13_8`, registry `V3_7`, core `V9_6` e `V12` e catálogo repetível; caminho,
  marcos M6 a M8, M12 e M14, riscos e resumo atualizados. ADRs 0010 e 0028 com linha de ajuste v2.10.
- Versão 2.11: oito decisões do dono do produto de 12/10/2026 (cartões) que resolvem as pendências do GIS da v2.10
  (§2.4). **Capacidade do núcleo:** o perfil 3 do ADR-0029 (GIS e dados) é antecipado para dez/2026–jan/2027 e executa
  o núcleo do GIS (GIS-01 a GIS-08) no P1–P3, sob revisão do dono, como uma das 4 contratações da trilha; homologação
  local e lançamento mantidos. Com entrada no início de janeiro de 2027 e onboarding de 8 semanas a 50%, ele entrega
  ≈ 16 semanas-pessoa até 28/05/2027 (≈ 20 semanas produtivas menos ≈ 4 de onboarding) contra ≈ 22 do núcleo: a conta
  não fecha, faltam ≈ 6 (≈ 2,5 com entrada em 07/12/2026), e o núcleo termina por volta do início de julho de 2027,
  durante a homologação local e antes do M10. M9 e M10 ficam nas datas originais; M6, M7 e M8 também, salvo a parte do
  GIS de cada um, que passa a cerca de 5 a 6 semanas depois do marco (nova pendência no §2.4). Na trilha, a capacidade
  até 30/09/2028 sobe de ≈ 247 para ≈ 251 semanas-pessoa, porque o perfil 3 chega a 02/08/2027 sem onboarding.
  **Gatilho da quinta contratação:** limiares de 95% (abre a vaga) e 85% (datas registradas de novo), medidos de 02/08 a
  31/10/2027 (≈ 44 semanas-pessoa planejadas). **Base da ligação** na P2.23 confirmada. **Rede do piloto** na
  homologação local: redes, peças e estruturas reais (ativos, não dado pessoal); ligações, ramais e imóveis sintéticos
  ou anonimizados, com coordenada deslocada ou agregada por quadra, como regra do ADR-0033. **LGPD no mapa:** fora do
  backoffice autenticado, imóveis e ligações só agregados por quadra ou setor com no mínimo 5 imóveis
  (`gis.public_aggregation_min_units`, classe c, só ampliável); rede e ativos podem ser publicados. **Normas ABNT**
  compradas no início do P1, antes do catálogo (nova P1.28, custo a estimar). **DGN v8** por conversão assistida na
  implantação; o importador aceita DGN v7, DWG, DXF, SHP, GPKG e INP do EPANET e do SWMM. **OGC** autenticado por
  tenant (OGC API – Features e WMS) na onda B, com o WMS servidor como escopo novo (+1 semana-pessoa na ED-67). Esforço:
  onda B ≈ 88,5 (antes ≈ 87,5), trilha ≈ 225,8 (antes ≈ 224,8), prontidão de POC ≈ 217,8 (antes ≈ 216,8), trilha
  completa com as tarefas originais do P4 ≈ 234,2 (antes ≈ 233,2); folga até 30/09/2028 de ≈ 7% (antes ≈ 6%),
  velocidade mínima de ≈ 93% sem a quinta pessoa e ≈ 84% com ela, cobertas pelos novos limiares; M12 continua por volta
  de meados de agosto de 2028 e M14 em outubro de 2028. Ajustados P0.30, P0.45, P1.26, P1.27, P2.23, P3.25 a P3.27,
  GL.1, ED-55 e ED-67, §2.2, §2.4 (pendências do GIS resolvidas), caminho, marcos M6 a M12 e M14, riscos e resumo. ADRs
  0029, 0033 e 0034 com linha de ajuste v2.11.
- Versão 2.12: duas decisões do dono do produto de 12/10/2026. **Gestão de equipe e capacidade sai do plano:** o plano
  trata do que será feito e de como (escopo, desenho, ordem, dependências e critérios de aceite), não de quem nem de
  quantas pessoas. Saem número de pessoas, contratações, perfis, onboarding, pessoas-equivalentes, gatilho da quinta
  contratação, folga de capacidade, custo e regime de equipe, reprogramação de datas por falta de gente e os riscos e as
  pendências do §2.4 sobre equipe, inclusive a da falta de capacidade no núcleo do GIS. O ADR-0029 fica revogado e
  mantido como histórico; o tamanho das tarefas (P, M, G e semanas-pessoa) continua como indicação de esforço, sem
  conversão em equipe; as datas das fases e dos marcos ficam como metas de calendário (homologação local em
  jun–jul/2027, lançamento a partir de outubro de 2028), sem cálculo de capacidade; o núcleo do GIS continua no P1–P3,
  com cada parte no marco da sua fase. As entradas acima das versões 2.5 a 2.11 sobre equipe e capacidade ficam só como
  registro. **WMS da onda B pelo GeoServer:** o WMS servidor da ED-67 passa a ser publicado pelo GeoServer, por exigência
  de edital, com simbologia gerada da mesma fonte do projeto QGIS (SLD), workspace e acesso ao banco por tenant e
  acesso só pela `api` (exceção no ADR-0010); OGC API – Features e WFS continuam na `api`. Ajustados §2.2, §2.4,
  objetivos do P1 ao P3, P1.26, P3.25, ED-05, ED-11, ED-67, introdução da trilha, §5 (caminho e marcos M6 a M12 e M14),
  §6, §7 e §8. ADR-0029 revogado; ADRs 0001, 0020, 0023, 0025, 0028, 0032, 0033 e 0034 com linha de ajuste v2.12;
  ADRs 0010, 0028 e 0034 com linha "GIS, v2.12".

- Versão 2.13: quatro decisões do dono do produto de 14/10/2026 (cartões) sobre ícones e verticais (ADR-0036 e
  ADR-0037; projeto em [arquitetura/ICONES-E-SIMBOLOGIA.md](../arquitetura/ICONES-E-SIMBOLOGIA.md) e
  [arquitetura/PLATAFORMA-E-VERTICAIS.md](../arquitetura/PLATAFORMA-E-VERTICAIS.md)). **Ícones de interface:** Lucide mais
  ícones próprios no mesmo estilo (grade 24 px, traço de 2 px, `currentColor`). **Símbolos do mapa:** conjunto SVG
  próprio, de fonte única, que gera o sprite do MapLibre, os símbolos do QGIS, o SLD do GeoServer e a legenda dos PDFs,
  com estados por forma e traço; tenant escolhe da biblioteca, sem enviar SVG. **Criação:** gerada no desenvolvimento
  por guia de estilo, com aceite do dono por lote. **Verticais:** plataforma genérica com pacotes por vertical;
  saneamento é o primeiro e o único com escopo; saúde e segurança pública só entram por decisão futura, com ADR e
  análise jurídica de dados. Novas P0.46 (guia e pacote de ícones), P0.47 (contrato de pacote de vertical e teste de
  fronteira), P1.29 (ícones de telas e itens) e P1.30 (símbolos de água e esgoto e pipeline); P1.26 passa a criar
  `gis_domain` no lugar da lista fixa de domínio; GIS-06 (P3.25), ED-64 e ED-65 recebem os símbolos; core `V9_7`. Nova
  pendência no §2.4: nome comercial das verticais. Sem efeito nas datas das fases nem nos marcos; o esforço das tarefas
  novas é indicativo. ADR-0036 e ADR-0037 novos; ADRs 0016, 0017 e 0034 com linha de ajuste v2.13.

- Versão 2.14: duas diretrizes do dono do produto de 14/10/2026, a partir da análise do GSAN e das normas (levantamentos
  [gsan/20a a 20g](../levantamento/gsan/)). **Bifurcação normativa vira parâmetro** (ADR-0038): onde lei, norma de agência ou
  prática legítima admitem caminhos alternativos, ou normas divergem, o produto implementa todos e o tenant escolhe;
  padrão de fábrica é o caminho mais protetivo ao usuário; onde lei federal proíbe expressamente (vedações de corte da
  Lei 14.015, piso da tarifa social da Lei 14.898), o caminho fica travado, confirmado por cartão. As perguntas dos
  levantamentos 20a e 20f que eram bifurcações viraram parâmetros (PARAMETROS-DO-TENANT, seção nova), entre eles juros
  por dia com teto da agência como padrão (SC: 0,033%/dia), supressão a pedido, esgoto "fora de uso", consumo mínimo
  por ligação, compensação por corte indevido, vazamento oculto, aferição, negativação e protesto por ente público.
  **Unidade consumidora como raiz e toda alteração por OS** (ADR-0039): toda solicitação ou alteração de cliente ou de
  unidade consumidora, inclusive cadastral e pelo portal, gera OS, de campo ou interna, com efeito aplicado no
  encerramento e sem gravação direta. O núcleo de OS sai do P3 para o P1 (nova P1.31, `V9_1` antecipada); P1.3 passa a
  depender dela; P1.2, P3.3, P3.4, P3.20, ED-13, ED-16 e ED-36 ajustadas. Obrigações legais sem tarefa antes do P4 entram
  no P2 e no P3: declaração anual de quitação e bloco de qualidade da água na conta (nova P2.24), revisão por vazamento
  oculto e aferição de hidrômetro (nova P3.28), compensação por corte indevido e aviso em 24 horas após o corte (P3.4).
  `V3_6` ganha pedidos de alteração e `service_order_id` nos históricos; `V9_2` recebe os bloqueios de corte. Datas das
  fases e marcos sem mudança. ADR-0038 e ADR-0039 novos; ADRs 0002, 0015 e 0034 com linha de ajuste v2.14.

- Versão 2.15: consolidação dos levantamentos [gsan/20b, 20c e 20d](../levantamento/gsan/) (medição e faturamento;
  arrecadação e cobrança; atendimento, OS e segurança) e oito decisões do dono do produto de 14/10/2026 (cartões).
  **Arrecadação e cobrança no P3:** o efeito da classificação (baixa da conta, débito ou crédito de ajuste, aviso de
  pagamento em duplicidade) entra na P3.1; devolução e fila de pagamentos não identificados, na nova P3.29; seleção
  mínima do aviso e do documento de cobrança, com motivo de não geração por unidade, na P3.4; parcelamentos ativos do
  piloto migram como prestações a vencer cobradas na conta (P1.2, P2.10), e o parcelamento completo segue na P4.1.
  **Cobrança por empresa terceirizada:** nova ED-68, onda C. **Permissões e senha** (ADR-0040): catálogo
  `recurso:ação` na P0.13; unidade organizacional e escopo por área na nova P3.30, antes da homologação local; senha
  por tenant por autenticador próprio no realm único, na ED-06. **Ouvidoria e interrupção programada** na ED-35 (onda
  A), sem mapa; área afetada pelo isolamento do GIS na ED-54 (onda B). Resolvidas pelas diretrizes, sem pergunta:
  núcleo de OS antes da P2.20, da P1.3 e da P3.4 pela P1.31, com a P2.20 dependente dela e toda retificação,
  cancelamento e revisão de conta como OS interna tipada; usuário avulso como unidade consumidora de tipo próprio;
  parcela bônus verificada na P1.1 e, se o piloto a usar, oferecida como opção de parâmetro; qualidade da água na
  conta já decidida (P2.24); NFAg-e por unidade ou por fatura agrupadora e conta de campo que não volta da rota como
  parâmetros de bifurcação; protocolo no módulo de atendimento e OS no módulo de campo, ligados pela referência
  protocolo → OS. PARAMETROS-DO-TENANT ganha 70 chaves de bifurcação, 9 linhas ajustadas e novas travas legais.
  Ajustadas: P0.13, P1.1, P1.2, P2.5, P2.10, P2.20, P3.1, P3.4, P3.22, ED-06, ED-20, ED-22, ED-24, ED-26, ED-30, ED-32,
  ED-35 e ED-54; GL.2 passa a depender da P3.29 e da P3.30, e a ED-06 da P3.30. Reservas `V1_8` e `V6_5`; dois riscos
  novos. Datas das fases e marcos sem mudança. ADR-0040 novo.
- Versão 2.16, 21/10/2026: confirmações do dono do produto. Os três padrões de fábrica propostos ficam como estão:
  `bill.field_unreturned_rule = DIVERGENCE_QUEUE`, `fiscal.central_billing_invoice_mode = PER_UNIT` e
  `sac.deadline_counting_rule = CALENDAR`, este com `BUSINESS` (dias úteis) disponível a cada tenant que o preferir
  (classe b, com aceite; norma da ERI que fixe a contagem prevalece). A **ED-68** (cobrança por empresa terceirizada)
  fica parada como **pacote pendente**: sem estimativa, fora dos totais de esforço da trilha e da prontidão de POC, até
  o dono liberá-la. Diretriz de 21/10/2026: a pesquisa de editais e licitações é suplemento de planejamento, não
  atendimento de edital; o projeto não está sujeito a prazos, e as datas das fases e dos marcos são só indicação de
  sequência, sem cobrança de pendência por data. Só documentação.
- Versão 2.17, 21/10/2026: **módulo Certificações de qualidade** (ADR-0041), por diretriz do dono do produto e quatro
  cartões. Catálogo único de normas que o cliente liga e desliga: as voluntárias (ISO 9001, 14001, 45001, 55001,
  ISO/IEC 27001 e 27701, ISO/IEC 17025, ISO 46001, ISO 24510, 24511 e 24512) e as de referência (NBR e medidores) são
  ativáveis; as obrigatórias (NR da ANA, Portaria GM/MS 888, CONAMA, leis, LGPD) aparecem na mesma lista, não
  desativáveis. O sistema atende às 17 capacidades comuns das normas de gestão desde o início e só as exigências
  específicas dependem de a norma estar ligada; desligar nunca apaga registro; o módulo não certifica, o mapa de
  cláusulas é autoavaliação. Estrutura no P1 a P3 (novas P1.32, P2.25, P3.31) e pacotes por norma na trilha (novas
  ED-69 a ED-82, tamanhos a estimar). Cartões: escopo declarado pelo tenant; perdas da NR 15 sobem por dependência;
  esgoto e água bruta como pacote próprio; textos pagos comprados só para curadoria, sem entrar no produto.
  Ajustadas: P0.19, P0.47, P1.7, P1.24, P1.28, P1.31, P3.10, P3.15, P3.22, ED-06, ED-14, ED-35, ED-48, ED-51, ED-56 e
  ED-58. Reservas `V9_8` (core) e `V21_x` (tenant). Levantamentos [21a e 21b](../levantamento/certificacoes/); desenho em
  [CERTIFICACOES-DE-QUALIDADE](../arquitetura/CERTIFICACOES-DE-QUALIDADE.md). Só documentação.
- Versão 2.18, 22/10/2026: **incorporação dos 156 achados** dos levantamentos [22a a 22d](../levantamento/lacunas/)
  (lacunas e inovações, por quatro frentes), por decisão do dono do produto ("coloca todas"). Doze tarefas novas nas
  fases (P1.33, P2.26 a P2.29, P3.32 a P3.38) e 21 novas na trilha (ED-83 a ED-103, tamanhos a estimar; ED-102 e ED-103
  como pacotes pendentes); 127 tarefas existentes ajustadas, com o ID de cada achado. Rastreabilidade de cada achado
  em [22-INCORPORACAO-NO-PLANO](../levantamento/lacunas/22-INCORPORACAO-NO-PLANO.md). Cartões de 22/10/2026: NR-1, 18, 33
  e 35 do MTE passam a obrigatórias no ADR-0041; o módulo de perdas sobe para a onda B (ED-85, antes da ED-78); ADR-0042
  fixa a política de IA antes de qualquer modelo; API pública com prioridade alta (P2.26); desastre regional por
  Multi-AZ mais cofre isolado na mesma região. Login gov.br e pagamento sem conta no portal dependem de ADR que altera o
  ADR-0022. Totais de esforço da trilha não recalculados. Só documentação.
- Versão 2.19, 22/10/2026: **catálogo de alvos da OS e hierarquia de ativos** ([ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS](../arquitetura/ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS.md)), por pedido do dono do
  produto de incluir elevatórias, boosters e demais itens com base em fontes técnicas. A OS passa a ter um ou mais
  alvos tipados (unidade consumidora, ligação, hidrômetro, trecho de rede, ponto de rede, instalação operacional,
  equipamento, componente, edificação, frota, ocorrência de rede, zona, local sem ativo). Hierarquia de seis níveis
  (município, sistema, etapa do serviço, instalação, equipamento, componente), inspirada na ISO 14224 e no manual de
  controle patrimonial da ARSESP (29 unidades de propriedade); catálogo com instalações (captação, poço, ETA, UTS,
  elevatória, booster, reservatório, EEE, ETE e subunidades), equipamentos, instrumentos, edificações e frota. Ajustadas:
  P1.27, P1.31, ED-14, ED-36 e ED-56. Revisado em 22/10/2026 após a leitura do anexo do MCPSB da ANA, que confirmou a estrutura de codificação, as 19 naturezas e subdivisões do serviço e as UAR, e a vigência da NBR 12208:2020. Só documentação.
- Versão 2.20, 22/10/2026: **biblioteca de normas e legislação** ([docs/normas](../normas/README.md)), por pedido do dono do produto
  de carregar todas as normas do setor numa área específica. Catálogo consolidado de 1.676 normas (leis, decretos, normas
  de referência da ANA, saúde, meio ambiente, metrologia, trabalho, dados, pagamentos e tributos, jurisprudência, normas
  de agências reguladoras de todos os estados, NBR, ISO e padrões técnicos), de quatro levantamentos e quatro lotes de
  verificação em fonte oficial: 1.298 conferidas em fonte oficial, 373 em fonte secundária (quase todas as NBR) e
  5 sem confirmação; 114 marcadas para revisão. Nova tarefa **P1.34** (área "Normas e legislação" do módulo
  Certificações de qualidade: carga do catálogo e consulta); ED-76 e P3.15 ajustadas. Achados com efeito no plano:
  vigências futuras (12), normas revogadas ou substituídas citadas pelo projeto e lista de correções a aplicar nos documentos em
  [correcoes-aos-documentos](../normas/correcoes-aos-documentos.md). O mapa de agências ([04-mapa-de-eris](../normas/partes/04-mapa-de-eris.md)) alimenta os perfis
  regulatórios do ADR-0038. Só documentação.
- Versão 2.21, 22/10/2026: **revisão da biblioteca de normas e correção das citações do projeto.** Reverificação dos 114
  itens a confirmar do catálogo: restam 13 (cada um com o próximo passo registrado), 6 foram reclassificados como não
  sendo norma em vigor (projetos de lei, relatório, minuta) e o catálogo ficou com 1.676 normas: 1.330 em fonte oficial,
  346 em fonte secundária e nenhuma sem confirmação ([docs/normas](../normas/README.md); os números da v2.20 estão superados).
  Cinco lotes de agentes corrigiram nos documentos as citações com número, ano, título, emissor, situação ou escopo
  errados (relatórios em [aplicacao-correcoes](../normas/aplicacao-correcoes/)); nenhuma decisão, tarefa, prazo ou parâmetro foi
  alterado, e as premissas que dependem da correção estão em [decisoes-a-rever](../normas/decisoes-a-rever.md). Datas
  confirmadas em fonte oficial: 01/01/2027, fim do prazo de adequação ao Pix Automático para convênios de serviços
  públicos (Res. CMN 5.278/2025), e 01/07/2027, vigência do novo regime geral do débito automático (Res. BCB 565/2026); a
  MP 1.393/2026 (Desenrola Brasil 3.0) alcança dívidas com prestadores de serviços públicos. Roteiro de 15 perguntas
  para parecer jurídico em [questoes-para-parecer-juridico](../normas/questoes-para-parecer-juridico.md). Só documentação.

- Versão 3.0, 28/10/2026: **plano consolidado**, por decisões do dono do produto de 28/10/2026 (cartões). Cada tarefa
  reescrita com os ajustes das versões 2.13 a 2.21 incorporados ao critério de aceite, sem etiquetas de versão; os 333
  achados dos levantamentos 22a a 22d e dos relatórios 23a a 23e com destino único (311 com tarefa dona, 22 com destino
  documental); ondas corrigidas (27 entregas da tabela da onda C passam ao grupo B2 da onda B, com a ED-56 e a ED-105;
  prontidão de POC pela onda A, pelo grupo B1 e pelo P4); seção de pacotes pendentes e sob demanda (P4.6, ED-68,
  ED-102, ED-103, ED-104) fora das ondas, dos totais e do critério da LC.1. Dezoito tarefas novas (P1.35 a P1.41,
  P2.30 a P2.34, P3.39 a P3.41, ED-104, ED-105, LC.15); P4.3 e P4.4 saem; P1.3, P3.4, ED-04, ED-48 e ED-80 divididas;
  limpeza do código antes da renomeação na P0.25; total de 286 tarefas. ADR-0043 (login gov.br e pagamento sem conta,
  altera o ADR-0022), ADR-0044 (sem prazos), ADR-0045 (OS no núcleo), ADR-0046 (supervisório somente leitura,
  proposto); nota NIST SP 800-63B-4 no ADR-0040; regra 2 do índice de ADRs. Seções 1, 2.2, 2.4, 3, 5, 6, 7 e 8
  reescritas; marcos como datas de referência; sem reestimativa. Só documentação.
