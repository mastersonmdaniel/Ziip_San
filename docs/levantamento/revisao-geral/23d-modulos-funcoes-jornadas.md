# 23d — Revisão do produto por módulos, funções e jornadas por perfil

Levantamento de 28/10/2026 sobre o plano v2.21 (`docs/PLANO-EXECUCAO-PDCA-R0-R9.md`). Não altera o plano nem outro
documento. As propostas são recomendações ao dono do produto; nenhuma decisão registrada foi modificada. Os 156 achados
dos levantamentos 22a a 22d, já incorporados ao plano, não são repetidos: quando um achado daqui toca um deles, o texto
diz o que falta além do que foi incorporado.

## 0. Leitura rápida

- O plano tem tarefa para quase todo módulo de um prestador. Dos 26 módulos de menu mapeados, quatro não têm tarefa
  dona: página inicial, caixa próprio, privacidade (código `sigsan-lgpd` sem tela) e resíduos sólidos (código `grs/*`).
- 120 jornadas de 16 perfis: a maioria fecha no lançamento; as quebras estão nas passagens entre módulos e nas funções
  transversais. 39 achados (JOR-01 a JOR-39): 5 altos, 26 médios, 8 baixos.
- Altos: perfis de fábrica limitados a quatro papéis genéricos (JOR-01); caixa de pendências só com aprovações (JOR-05);
  sem motor único de modelos de documento (JOR-06); fila fiscal do tenant sem tarefa (JOR-08); direitos do titular e
  registro de operações de tratamento presos ao pacote voluntário ISO/IEC 27701 (JOR-15).
- Padrões: obrigação legal amarrada a pacote voluntário ou a onda tardia (LGPD, lodo, efluente da ETE); funções
  transversais feitas módulo a módulo, sem regra única herdada por todos.
- Relatórios: o ED-46 cobre bem comercial, campo e laboratório; faltam operação de estações, almoxarifado analítico,
  privacidade, acessos e compensações pagas ao usuário (JOR-39).

## 1. Método

**Fontes lidas.** Plano v2.21 (todas as linhas de tarefa de P0 a P4, ondas A, B e C, GL e LC, por extração e leitura
das linhas completas das tarefas citadas); `ARQUITETURA-ALVO.md`; `PLATAFORMA-E-VERTICAIS.md`;
`CERTIFICACOES-DE-QUALIDADE.md`; `CENTRAL-ZIIP.md` (§2, §3, §11); `AJUDA-SUPORTE-SOBRE.md` (§2 a §4 e §12); ADR-0040;
`PARAMETROS-DO-TENANT.md` (busca dirigida); `frontend-cadastro.md` (§2, §3 e §9); `22-INCORPORACAO-NO-PLANO.md`;
`18g` §2.4 (lista de relatórios dos editais); `20b` (busca dirigida sobre leitura e retenção). Do código, só os nomes dos
módulos Maven e as classes do `sigsan-lgpd`.

**Catálogo de permissões.** O catálogo `recurso:ação` da P0.13 (ADR-0040) não está escrito em documento: os nomes
encontrados nos documentos são `protocol:create`, `protocol:transfer-any-unit`, `so:close`, `so:close-retroactive`,
`so:close-without-activities`, `connection:cut`, `cert:read|write|approve|audit`, `registry.reveal-document` e
`gis.view|view-personal|edit|review|import|export|qgis|catalog`. O mapa de módulos foi montado a partir do plano e dos
documentos de arquitetura, e não do catálogo (ver JOR-02).

**Como cada jornada foi verificada.** Cada passo foi procurado no texto de aceite e nos ajustes das tarefas. Marcas
usadas nas tabelas da seção 3:

| Marca | Significado |
|---|---|
| (P1.31) | passo coberto pela tarefa citada |
| **[sem tarefa: JOR-nn]** | passo sem tarefa no plano |
| **[integração indefinida: JOR-nn]** | os dois módulos têm tarefa, mas a passagem entre eles não está descrita |
| **[fora do sistema: JOR-nn]** | o usuário teria de sair do produto para concluir o passo |

A coluna "Cobertura" usa: **Coberta** (todos os passos têm tarefa), **Parcial** (algum passo com marca) e **Lacuna**
(o núcleo da jornada não tem tarefa). A avaliação considera o produto no lançamento comercial, depois de todas as fases
e ondas; quando a jornada só fecha numa onda, a tarefa citada já indica isso.

**Gravidade.** Alta: obrigação legal sem cobertura ou jornada central que não fecha no produto. Média: jornada que
fica manual, fragmentada ou inconsistente entre módulos. Baixa: conforto, consistência de documento ou caso raro.

**Limites.** Não foram lidos por inteiro `GIS-CADASTRO-TECNICO.md`, os levantamentos de editais nem os relatórios 22a a
22d; achados que dependem de detalhe desses textos estão marcados como inferência na seção 6.

## 2. Mapa de módulos e submódulos

### 2.1 Mapa

| # | Módulo (menu) | Submódulos | Tarefas | Dono e observação |
|---|---|---|---|---|
| M01 | Início | painel do usuário; minhas tarefas; notificações; agenda | P2.28 (aprovações), P3.36 (notificações), ED-07 (alertas) | sem tarefa para a página inicial nem para pendências que não sejam aprovação (JOR-04, JOR-05); agenda sem dono (JOR-30) |
| M02 | Atendimento | protocolos e fila por prazo; ficha da unidade (visão 360); ouvidoria; interrupções programadas; canais (balcão, telefone, WhatsApp, e-mail, chat, redes); senhas e totem; LAI; canais externos | P3.4, ED-35, ED-40, ED-42, ED-43, ED-44, ED-99 | dono ED-35; abas da ficha previstas no frontend sem ligação às tarefas de origem (JOR-03) |
| M03 | Cadastro comercial | clientes; unidades consumidoras e imóveis; endereços; hierarquia comercial (localidade, setor, rota, quadra); catálogos auxiliares; tarifa social e beneficiários; recadastramento; qualidade cadastral | P1.3, P1.4, ED-12, P1.5, ED-57, ED-16, P3.32 | dono P1.3; toda alteração por OS (ADR-0039) |
| M04 | Ligações e hidrometria | ligações de água e esgoto; ramais; hidrômetros (lote, instalação, retirada, aferição); soluções individuais; ligação obrigatória | ED-13, P2.23, ED-14, P3.28, ED-88 | dono ED-13 e ED-14 |
| M05 | Ordens de serviço | tipos de serviço e preços; OS (abrir, programar, executar, encerrar); OS de lote; empreiteiras; frota e equipes; campo | P1.31, ED-34, ED-36, ED-37, ED-59, ED-20 | dono P1.31 e ED-36; impressão da OS sem tarefa (JOR-07) |
| M06 | Medição | cronograma; roteiros; leitura terceirizada; app de leitura; autoleitura; telemetria; leituras retidas | P2.5, P2.7, ED-15, ED-18, ED-17 | dono P2.5; fila de análise das retidas sem tela própria (JOR-09) |
| M07 | Faturamento | ciclo; contas; retificação; situações especiais e centralizador; gestão tarifária; mensagens legais; quitação anual; gráfica; emissão simultânea; resíduos na conta | P2.5, P2.20, ED-22, P2.29, P2.24, P2.8, P2.17, ED-19, ED-25 | dono P2.5 e P2.20 |
| M08 | Fiscal | NFAg-e; NFS-e de serviços; CBS e IBS; contingência; erros explicados | P1.8, P1.16, P2.4, P2.6, P3.6, P3.7, P2.16 | fila do tenant sem tarefa (JOR-08) |
| M09 | Arrecadação | retorno e conciliação; Pix; débito automático e Pix Automático; não identificados e devolução; documento agrupador; cartão pela credenciadora; caixa próprio | P3.1, P3.2, P4.5, ED-27, P3.24, P3.29, ED-26, ED-28 | dono P3.1; caixa próprio sem tarefa (JOR-13) |
| M10 | Cobrança | seleção e aviso; corte e religação; parcelamento e programas; negativação e protesto; dívida ativa; cobrança terceirizada | P3.4, P3.5, P4.1, ED-29, ED-30, ED-31, ED-68 (pendente) | dono P4.1 |
| M11 | Contabilidade e integrações | eventos contábeis; e-Sfinge; ERP; API e webhooks; hub de integrações | ED-32, ED-33, ED-62, P2.26, P3.37 | dono ED-32 e P3.37 |
| M12 | Almoxarifado | estoque por local; requisição com alçada; reserva e baixa; inventário; químicos; EPI; compras | ED-58, ED-89, ED-71 | dono ED-58; compras fora por decisão (ED-60 e ED-61 para 2029); entrada por NF-e sem tarefa (JOR-32) |
| M13 | Operação de sistemas | estações (diário, ronda, turno); telemetria e supervisório; sala de situação; pressão; energia; reservatórios; crise hídrica | ED-87, ED-17, ED-93, ED-86, ED-90, ED-89, ED-91 | dono ED-87 e ED-17 |
| M14 | Perdas | DMC e vazão mínima noturna; balanço; pesquisa de vazamento; perdas aparentes; fraude; anomalias; plano da NR 15 | ED-85, ED-86, ED-78, ED-95, ED-24, ED-94 | dono ED-85; código `sigsan-losses` existe |
| M15 | Qualidade da água e laboratório | plano de amostragem; coleta e custódia; resultados e laudo; ISO/IEC 17025; SISAGUA; bloco na conta; relatório anual; efluentes e esgoto não doméstico | ED-50, ED-51, P2.24, ED-79, ED-80, ED-83 | dono ED-50; código `sigsan-quality` existe |
| M16 | Rede e ativos (GIS) | mapa; edição e pacotes; importação; topologia; traçado e manobra; ativos e manutenção; hidráulica; drenagem; resíduos no mapa; campo GIS; exportação e OGC; ocorrência de rede | P1.26, P1.27, P1.30, P2.21, P2.22, P2.23, P3.25, P3.26, P3.27, ED-53 a ED-56, ED-63 a ED-67, ED-72, ED-92 | dono ED-52 (núcleo) e ED-53; código `sigsan-gis`, `sigsan-assets`, `sigsan-maintenance` |
| M17 | Obras e empreendimentos | viabilidade; loteamento e condomínio; recebimento de rede; medição de obra; convênios | ED-84, ED-37, ED-102 (pendente) | dono ED-84 |
| M18 | Gestão e regulação | relatórios; BI; painéis; indicadores e metas; SINISA; pacote por agência; agenda de obrigações; contrato regulatório; transparência; projeção; ESG | P3.22, ED-46, ED-47, ED-49, ED-48, P4.2, ED-97, ED-98, ED-96, ED-99, ED-100, ED-90, ED-77 | indicadores com vários donos (JOR-19) |
| M19 | Certificações de qualidade | normas ligadas; documentos; ocorrências e ações; riscos; objetivos; auditoria interna; análise crítica; competência; fornecedores; pacotes de norma; evidência e auditor externo | P1.32, P2.25, P3.31, ED-69 a ED-82, ED-75 | dono P1.32; fornecedor com dependência invertida (JOR-18) |
| M20 | Normas e legislação | biblioteca, consulta, vigência | P1.34 | dono P1.34 |
| M21 | Privacidade (LGPD) | pedidos do titular; consentimentos; RIPD; retenção e anonimização; registro de operações; incidentes | P1.15, P3.10, ED-74 | código `sigsan-lgpd` com pedido do titular, RIPD, retenção e consentimento, sem tarefa de tela nem canal no portal (JOR-15) |
| M22 | Resíduos sólidos (GRS) | MTR; PGRS; destinação; coleta | trilha GRS sob demanda (sem ID), ED-25, ED-65, P0.25 | código `grs/*` existe; sem tarefa com ID (JOR-17) |
| M23 | Portal e app do consumidor | conta e vínculo; contas e pagamentos; documentos; serviços e protocolos; autoleitura; parcelamento; certidões; autenticidade; app | P3.3, P3.20, P3.21, P3.34, P3.35, ED-38, ED-39, ED-101 | dono P3.3 e ED-38 |
| M24 | Administração do tenant | usuários; perfis; unidades e escopo; parâmetros; marca, tema e domínio; catálogos; modelos; integrações; dispositivos; trilha; contrato com o Ziip | P0.13, ED-06, P3.30, P0.19, P1.19, P1.18, P2.27, ED-12, P2.26, P3.37, ED-18, P2.14 | modelos sem tarefa (JOR-06); tela de parâmetros pós-implantação não descrita (JOR-25) |
| M25 | Ajuda, suporte e sobre | artigos, tours, erros explicados; chamados; novidades; status; Sobre; Trust Center | P1.21 a P1.24, P2.15, P2.16, P3.18, P3.19, P4.8, P3.38 | dono P1.21 e P1.23 |
| M26 | Central Ziip e console (Ziip) | implantação; planos e cotas; suspensão; saída; abas da Central; contratos; suporte; acesso assistido; status; radar | P1.6, P1.17, P2.11, P2.12, P2.13, P2.15, P3.15 a P3.18, ED-04, ED-05, ED-09, ED-11 | aba Estoque descrita sem tarefa de aba (JOR-36) |

### 2.2 Módulos sem dono claro

| Tema | Onde aparece | Problema |
|---|---|---|
| Indicadores e metas | ED-48 (motor), ED-77 (fichas), P3.31 (objetivos), ED-49 (painéis), ED-93 (sala de situação), P1.31 (indicador por tipo de serviço) | seis tarefas criam ou leem indicadores sem uma ser declarada dona do registro (JOR-19) |
| Notificações e eventos | P3.5, P3.36, ED-07, ED-41, P2.26, Central (`alert_rule`) | cada tarefa define seus eventos; não há catálogo de eventos de negócio (JOR-20) |
| Modelos de documento e mensagem | P2.8, P2.17, P2.24, P3.4, ED-19 ("leiaute do editor"), ED-29, ED-30, ED-31, ED-41, P4.1 | cada módulo define o próprio leiaute (JOR-06) |
| Ficha da unidade consumidora | `frontend-cadastro.md` §9 (abas "Ligações e medição", "Faturamento", "Débitos", "Atendimentos", "Documentos" adiadas até existirem), ED-35 (visão 360) | não há tarefa que acrescente cada aba quando a fonte nasce (JOR-03) |
| Fornecedores | P3.31 (avaliação "sobre a ED-37"), ED-37 (empreiteiras), ED-58 (materiais) | cadastro de fornecedor só nasce na onda A e só para empreiteiras (JOR-18) |

### 2.3 Módulos citados sem tarefa

| Módulo | Onde é citado | Situação |
|---|---|---|
| Privacidade (LGPD) para tenant e consumidor | Central, aba LGPD (só contagens); ED-74 ("API do módulo LGPD"); código `sigsan-lgpd` | sem tarefa de tela, fila ou canal (JOR-15) |
| Resíduos sólidos (GRS) | plano, 2028 "sob demanda"; código `grs/*`; P0.25 (SINIR assistido) | sem IDs (JOR-17) |
| Aba Estoque da Central | CENTRAL-ZIIP §3 | P3.15 não a lista; a ED-58 só cita alerta "na Central" (JOR-36) |
| Compras e RH | ED-60 e ED-61, adiadas para 2029 por decisão | decisão mantida; a jornada do almoxarife sai do sistema na compra (seção 6) |

### 2.4 Itens de menu que faltariam para o produto parecer completo

| Item | Por quê | Achado |
|---|---|---|
| Início | todo perfil abre o sistema num painel com pendências, alertas e atalhos | JOR-04 |
| Minhas tarefas | trabalho com prazo vindo de vários módulos num lugar só | JOR-05 |
| Agenda | cronograma de faturamento, programação de OS, auditorias, calibrações, preventivas e obrigações | JOR-30 |
| Caixa | recebimento presencial em autarquia e SAAE | JOR-13 |
| Configurações > Modelos | cartas, avisos, termos, laudos, mensagens | JOR-06 |
| Configurações > Parâmetros | manutenção depois da implantação | JOR-25 |
| Privacidade | encarregado de dados e pedidos do titular | JOR-15 |
| Fornecedores | material, serviço, laboratório e empreiteira | JOR-18 |
| Importações e Arquivos gerados | histórico de cargas por planilha; relatórios agendados e exportações para baixar | JOR-26, JOR-29 |
| Documentos fiscais | rejeições, contingência, inutilização, reenvio | JOR-08 |

## 3. Jornadas por perfil

### 3.1 Atendente

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| A1 | Atendimento no balcão ou por telefone | senha com prioridade legal (ED-40) → identificar o usuário pela busca (P1.33; documento com cota, P0.11) ou pelo número chamador (ED-44) → abrir a ficha da unidade (P1.3; visão 360 na ED-35) **[integração indefinida: JOR-03]** → abrir protocolo (P3.4) → classificar o motivo (P3.22, ajuste v2.15) → resolver ou gerar OS (P1.31) → informar número e prazo ao usuário com comprovante **[sem tarefa: JOR-06]** → encerrar com resposta (ED-35) → pesquisa de satisfação (ED-35) | Parcial |
| A2 | Segunda via e pagamento | localizar (P1.33) → 2ª via (P2.20) → QR Pix (P3.2) → enviar por e-mail (ED-41) ou imprimir em PDF (P2.17) → receber em espécie ou cartão no balcão **[sem tarefa: JOR-13]** | Parcial |
| A3 | Ligação nova, do pedido à primeira fatura | protocolo (P3.4) ou portal (P3.3) → documentos exigidos (ED-34, CAD-85) → unidade `PROVISIONAL` (P1.3) → vistoria e viabilidade como OS (P1.31, ED-36; empreendimento na ED-84) → orçamento e taxa (ED-34) → pagamento (P3.1, P3.2) → OS de ligação programada (ED-36) → execução (ED-20; sem app **[sem tarefa: JOR-07]**) → material (ED-58) → hidrômetro com leitura de instalação (ED-14) → efeito ligação ativa (ED-13, P1.31) → ramal no GIS (P2.23, ED-53) → sequência na rota (ED-15) → entrega do contrato de adesão com ciência **[sem tarefa: JOR-12]** → primeira fatura de período parcial **[sem tarefa: JOR-11]** → NFS-e da taxa (P3.6) | Parcial |
| A4 | Troca de titularidade | protocolo (P3.4) → documentos (ED-34) → OS interna (P1.3, P1.31) → débito anterior fica com quem o contraiu (`unit.transfer_with_previous_debt`) → vínculo do portal revogado e código invalidado (P3.20) → novo contrato de adesão **[sem tarefa: JOR-12]** | Parcial |
| A5 | Reclamação de consumo alto até a retificação | protocolo (P3.4) → OS `BILL_REVIEW` com suspensão de cobrança e corte (P2.20) → explicação da variação (ED-35, GES-26) → vistoria e aferição (P3.28) → laudo (P3.28) → retificação com nova versão e NFAg-e substituída (P2.20) → devolução se pago (P3.29) → resposta no protocolo (ED-35) | Coberta |
| A6 | Parcelamento de débito | consultar débitos (P2.20, ED-35) → simular (P4.1; portal ED-38) → termo com assinatura (P4.1, P3.34) → prestações como débito a cobrar (P4.1) → suspensão do corte (P3.4) | Coberta |
| A7 | Religação após pagamento | pagamento baixado (P3.1, P3.2) → OS de religação automática (P3.4, `rec.auto_order_on_payment`) → execução no prazo (ED-20) → compensação por atraso (P3.4) | Coberta |
| A8 | Tarifa social a pedido | protocolo (P3.4) → elegibilidade pela fonte do CadÚnico por natureza jurídica (P1.5, ED-57) → OS de enquadramento (P1.31) → benefício na próxima conta (P2.5) | Coberta |

### 3.2 Supervisor comercial

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| S1 | Gestão da fila e dos prazos | fila por prazo (ED-35) → prazo por especificação e feriados (P3.4) → redistribuir protocolos entre atendentes **[sem tarefa: JOR-31]** → transferir entre unidades (P3.30) → acompanhar vencidos (P3.22, ED-46) | Parcial |
| S2 | Aprovações do dia | caixa de aprovações com delegação (P2.28) → retificação (P2.20), devolução (P3.29), transferência de pagamento (P3.1) | Coberta |
| S3 | Ouvidoria em segunda instância | recurso do usuário (ED-35) → prazo da Lei 13.460 com prorrogação (ED-35) → identidade preservada (ED-35) → resposta → relatório anual (ED-35) | Coberta |
| S4 | Interrupção programada | cadastrar área (ED-35) → área calculada pelo isolamento (ED-54) → aviso aos afetados e à agência (P3.5, ED-41) → protocolos agrupados (ED-35) → abastecimento emergencial a essenciais (ED-35) | Coberta |
| S5 | Relatório mensal de reclamações (NR 11, art. 54) | motivo obrigatório no encerramento (P3.22) → relatório (P3.22) → envio à agência (ED-97, ED-98) | Coberta |
| S6 | Qualidade cadastral | regras com pontuação (P3.32) → fila de correção por OS de lote (P3.32) → recadastramento (ED-16) | Coberta |
| S7 | Seleção para corte e régua | seleção mínima (P3.4) → aviso com prova (P3.5) → régua e promessa (P4.1) → OS de corte com cota diária (ED-29) | Coberta |
| S8 | Desempenho do atendimento | painel de atendimento (ED-49) → tempos por protocolo (P3.22) → satisfação por canal (ED-35, ED-46) → produtividade por atendente **[sem tarefa: JOR-39]** | Parcial |

### 3.3 Faturista

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| F1 | Ciclo mensal de leitura e faturamento | cronograma por grupo com precedência (P2.5) → arquivo de leitura ao fornecedor (P2.7) ou rota no app (ED-18) → retorno sem duplicar (P2.7, ED-18) → crítica e retenção (P2.5, `bill.hold_*`) → análise das retidas com histórico, foto e ocorrência **[sem tarefa: JOR-09]** → ocorrências de leitura que pedem OS **[integração indefinida: JOR-10]** → faturar grupo (P2.5) → NFAg-e (P2.6) → tratar rejeições e contingência **[sem tarefa: JOR-08]** → arquivo da gráfica (P2.8) → contas devolvidas **[sem tarefa: JOR-35]** → encerrar a referência (P2.5) → eventos contábeis (ED-32) → relatórios de fechamento (P3.22) | Parcial |
| F2 | Retificação e manutenção de conta | OS tipada (P2.20) → motivo e anotação (P2.20) → aprovação (P2.28) → nova versão com trilha por campo (P2.20) → NFAg-e cancelada ou substituída (P2.20) | Coberta |
| F3 | Reajuste tarifário | ato da agência (P2.29) → cópia da estrutura (P2.29) → simulação de impacto (P2.29) → aceite (`bill.tariff_readjust`) → vigência (P2.5) → mensagem na conta (P2.5, CAD-96) | Coberta |
| F4 | Situações especiais e órgão centralizador | OS `SPECIAL_SITUATION_SET` (ED-22) → vínculo ao centralizador (ED-22) → fatura agrupadora com quitação em cascata (ED-26) | Coberta |
| F5 | Conta fora do lote e conta final | emitir fora do lote (P2.20) → conta final com leitura antecipada (ED-22) | Coberta |
| F6 | Declaração anual de quitação e bloco de qualidade | geração na fatura de maio (P2.24) → bloco mensal por planilha do laboratório (P2.24) até a ED-50 | Coberta |
| F7 | Emissão em campo e divergência | conta calculada no aparelho (ED-19) → recálculo no servidor (ED-19) → fila de divergências com aprovação (ED-19, P2.20) | Coberta |
| F8 | Conferência do ciclo e do paralelo | relatórios por grupo e categoria (P3.22) → comparação conta a conta (GL.2, LC.11) | Coberta |

### 3.4 Leiturista

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| L1 | Leitura da rota com o app | login e aparelho registrado (ED-18) → carga da rota (ED-18) → sequência e reordenação (ED-15) → identificar o hidrômetro por QR ou NFC (ED-18, OPE-39) → leitura com foto e reconhecimento óptico (ED-18, OPE-34) → ocorrência de leitura (ED-18) → sincronização idempotente (ED-18) | Coberta |
| L2 | Emissão simultânea | cálculo no aparelho (ED-19) → impressão térmica com QR Pix (ED-19) → comunicados de débito (ED-19) → retidas reimpressas (ED-19) | Coberta |
| L3 | Ocorrência que exige serviço | hidrômetro parado, violado ou inacessível (ED-18) → OS correspondente aberta **[integração indefinida: JOR-10]** | Parcial |
| L4 | Releitura e controle de qualidade da leitura | releitura amostral e faixa falsa (ED-15, OPE-23) → desvio de posição (ED-20) | Coberta |
| L5 | Condomínio e contingência | regra de condomínio (ED-15) → boletim de contingência (ED-15) | Coberta |
| L6 | Leitura no piloto antes do app | coletor do fornecedor (P2.7) **[fora do sistema: seção 6]** | Parcial |

### 3.5 Equipe de campo (OS)

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| C1 | Execução de OS com o app | programação por equipe (ED-36, ED-59) → pacote offline (ED-20) → "a caminho" e janela (ED-20, OPE-21) → análise de risco e permissão de trabalho (P1.31, OPE-14) → fotos, GPS e anexos (ED-20) → aceite do usuário (OPE-21) → material usado (ED-58) → encerramento com efeitos (P1.31) | Coberta |
| C2 | Corte com revalidação | pacote com débito, contestação, aviso e vedações (ED-20) → revalidação offline e na sincronização (ED-20, P3.4) → OS cancelada se falhar (ED-20) | Coberta |
| C3 | Reparo de vazamento em rede | ver jornada transversal J3 (seção 3.17) | Parcial |
| C4 | Instalação e troca de hidrômetro | retirada do estoque (ED-58) → instalação com leitura (ED-14) → retirado com destino (ED-14) | Coberta |
| C5 | Equipe sem app ou empreiteira | OS impressa ou roteiro → execução → baixa posterior pelo backoffice com data, hora, executor e foto **[sem tarefa: JOR-07]** → medição da empreiteira (ED-37) | Parcial |
| C6 | Coleta de amostra de água | OS de coleta em campo com cadeia de custódia **[integração indefinida: JOR-37]** → envio ao laboratório (ED-50) | Parcial |

### 3.6 Almoxarife

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| X1 | Entrada de material | pedido de compra **[fora do sistema: decisão de 2029]** → entrada pela NF-e do fornecedor **[sem tarefa: JOR-32]** → entrada manual por local (ED-58) → custo médio (ED-58) | Parcial |
| X2 | Atendimento de requisição da OS | requisição gerada pela OS (ED-58) → aprovação por alçada (ED-58) → reserva (ED-58) → entrega registrada (ED-58) → baixa no encerramento e devolução da sobra (ED-58) | Coberta |
| X3 | Inventário | contagem por local (ED-58) → ajuste com justificativa (ED-58) → relatório de divergência **[sem tarefa: JOR-39]** | Parcial |
| X4 | Transferência entre almoxarifados | saída de um local, trânsito e entrada no outro **[sem tarefa: JOR-32]** | Lacuna |
| X5 | Lote de hidrômetros | recebimento de lote e verificação (ED-14) → bancada de ensaio (ED-14, OPE-24) → número de série e lacre (ED-58, OPE-29) | Coberta |
| X6 | Produtos químicos e EPI | lote, validade e ficha de segurança (ED-89) → controle da Polícia Federal (ED-89) → EPI por tarefa (ED-71) | Coberta |
| X7 | Reposição | saldo negativo com alerta (ED-58) → relatório diário de negativos (ED-58) → aviso a compras **[fora do sistema: decisão de 2029]** | Parcial |

### 3.7 Operador de ETA e ETE

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| O1 | Turno e diário de operação | abertura do turno e ronda (ED-87) → registros horários (ED-87) → passagem de turno (ED-87) | Coberta |
| O2 | Controle de processo da ETA | turbidez por filtro a cada 2 h (ED-87) → cloro e pH na saída → dados de controle que alimentam a conformidade e o SISAGUA **[integração indefinida: JOR-21]** | Parcial |
| O3 | Dosagem e consumo de químicos | consumo por lote (ED-89) → baixa no estoque (ED-58) → consumo por m³ tratado **[sem tarefa: JOR-39]** | Parcial |
| O4 | Parada ou falha de equipamento | registro de parada (ED-17, ED-87) → ocorrência vira OS com alvo `OPERATIONAL_SITE` ou `EQUIPMENT` (ED-87, P1.31) → manutenção corretiva (ED-56) | Coberta |
| O5 | Manutenção preventiva | plano por tempo e uso (ED-56, OPE-20) → OS gerada (ED-36) → checklist (ED-56) | Coberta |
| O6 | Energia | kWh por m³, demanda e fator de potência (ED-90) | Coberta |
| O7 | Lodo de ETA e ETE | geração e destinação do lodo como resíduo do próprio tenant (ED-70, só com ISO 14001 ligada) → MTR do gerador **[integração indefinida: JOR-16, JOR-17]** | Parcial |
| O8 | Automonitoramento do efluente da ETE | coleta e análise (ED-80, onda C, em pacote) → eficiência de DBO e Declaração de Carga Poluidora (ED-80) **[amarração: JOR-16]** | Parcial |

### 3.8 Analista de perdas

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| P1 | Configurar setorização | zonas no GIS (`ZONE`, P1.26) → DMC (ED-85) → macromedidores e telemetria (ED-17, ED-48) | Coberta |
| P2 | Vazão mínima noturna | séries por DMC (ED-17, ED-85) → VMN (ED-85) → anomalia explicada (ED-94) | Coberta |
| P3 | Balanço hídrico | volume produzido (ED-48) → micromedido por DMC (ED-85) → usos operacionais e água exportada (ED-85) → ajuste do micromedido ao período do macromedido **[sem tarefa: JOR-34]** → balanço geral e plano (ED-78) | Parcial |
| P4 | Controle ativo de vazamento | pesquisa (ED-86) → OS de reparo (ED-36) → volume perdido e tempo de reparo por evento **[sem tarefa: JOR-33]** → ganho medido (ED-86) | Parcial |
| P5 | Perdas aparentes | fiscalização por critério (ED-24) → cruzamento com edificações (ED-95) → submedição pelo parque (ED-14) | Coberta |
| P6 | Gestão de pressão | zonas e pontos (ED-86) → ajuste de VRP (ED-86) | Coberta |

### 3.9 Laboratorista

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| Q1 | Planejar amostragem | plano da Portaria 888 (ED-50) → cronograma mensal (ED-50, ED-46) | Coberta |
| Q2 | Coleta e custódia | coleta em campo **[integração indefinida: JOR-37]** → cadeia de custódia (ED-50) → recebimento no laboratório (ED-50) | Parcial |
| Q3 | Resultado e laudo | registro (ED-50) → validação por segunda pessoa (ED-51) → laudo com emenda (ED-51) | Coberta |
| Q4 | Não conformidade | identificação (ED-50) → comunicação à autoridade de saúde e à população **[sem tarefa: JOR-22]** → recoleta (ED-46) → ocorrência e ação (P2.25) | Parcial |
| Q5 | Fechamento e prestação de contas | fechamento mensal (ED-50) → SISAGUA assistido (ED-50) → bloco na conta (P2.24, ED-50) → relatório anual (ED-79) | Coberta |
| Q6 | Reclamação de qualidade | protocolo gera coleta (ED-35, OPE-26) → resultado no protocolo (ED-50) | Coberta |
| Q7 | Equipamentos e métodos | calibração com bloqueio (ED-51) → validação de método e proficiência (ED-51) | Coberta |

### 3.10 Engenheiro de cadastro técnico (GIS)

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| G1 | Carga inicial da rede | DWG, DXF, SHP, GPKG (P3.26) → staging (P3.26) → topologia com snap (P2.21) → aprovação (P2.22) | Coberta |
| G2 | Edição por pacote | abrir pacote (P2.22) → editar na web (ED-53) ou no QGIS (ED-55) → revisão por outra pessoa (P2.22) → publicar tiles (P3.25) → revisar na caixa (P2.28, PLT-13) | Coberta |
| G3 | As-built de OS e obra | OS que altera rede abre pacote (ED-53) → recebimento de rede do empreendedor (ED-84) → campo GIS (ED-66) | Coberta |
| G4 | Qualidade do cadastro técnico | validação contínua (P2.21) → precisão (P3.27) → relatório de extensão (P3.27) | Coberta |
| G5 | Traçado e manobra | isolamento (ED-54) → plano de manobra com aviso e OS em lote (ED-54) | Coberta |
| G6 | Modelo hidráulico | `.inp` por zona e data (ED-63) → resultado | Coberta |
| G7 | Inventário de ativos | hierarquia e MCPSB (ED-56) → conciliação com patrimônio (ED-56) | Coberta |
| G8 | Entrega da base | exportação e OGC (ED-67) → perfis Corsan e Saneago (ED-67) | Coberta |

### 3.11 Gestor e diretor

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| D1 | Acompanhamento diário | página inicial com indicadores do papel **[sem tarefa: JOR-04]** → painéis (ED-49) → sala de situação (ED-93) → alertas (ED-07) | Parcial |
| D2 | Aprovações e alçadas | caixa (P2.28) → delegação temporária (P2.28) | Coberta |
| D3 | Metas e indicadores | indicadores com meta e semáforo (ED-48) → objetivos (P3.31) → fichas ISO 24510 e 24512 (ED-77) **[integração indefinida: JOR-19]** | Parcial |
| D4 | Análise crítica pela direção | entradas reunidas (P3.31) → atas e decisões (P3.31) → ações (P2.25) | Coberta |
| D5 | Tarifa e receita | simulação tarifária (P2.29) → projeção de receita e caixa (ED-100) → custo por m³ (ED-48, GES-09) | Coberta |
| D6 | Prestação de contas | SINISA (P4.2, ED-48) → pacote por agência (ED-97) → agenda de obrigações (ED-98) → transparência (ED-99) | Coberta |
| D7 | Fiscalização da agência | notificação recebida → prazo de resposta → plano de ação **[sem tarefa: JOR-24]** | Lacuna |

### 3.12 Auditor interno de qualidade

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| I1 | Programa de auditoria | programa anual (P3.31) → escopo pelas normas ligadas (P1.32) | Coberta |
| I2 | Execução da auditoria | lista de verificação pelo mapa de cláusulas (P1.32, P3.31) → evidência vinculada (P1.32) → relatório de auditoria em modelo **[sem tarefa: JOR-06]** | Parcial |
| I3 | Achado até ação corretiva | não conformidade (P2.25) → causa raiz (P2.25) → ação com responsável e prazo na lista de pendências do responsável **[sem tarefa: JOR-05]** → eficácia (P2.25) | Parcial |
| I4 | Análise crítica | entradas (P3.31) | Coberta |
| I5 | Auditoria externa | pacote de evidência sem dado pessoal (ED-75) → acesso do auditor externo com prazo (ED-75) | Coberta |
| I6 | Trilha do sistema | consulta por usuário (ED-06) → exportação verificável (ED-06, PLT-19) | Coberta |

### 3.13 Regulador (acesso externo)

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| R1 | Receber acesso | perfil externo somente leitura (ED-75, GES-22) com escopo de dado regulatório e não de evidência de certificação **[integração indefinida: JOR-23]** | Parcial |
| R2 | Consultar indicadores e relatórios | pacote por agência (ED-97) → indicadores (ED-48) → reclamações do art. 54 (P3.22) → relatório da ouvidoria (ED-35) | Coberta |
| R3 | Receber comunicações | interrupção programada (ED-35) → ocorrência de rede (ED-92) | Coberta |
| R4 | Receber remessas | SINISA (P4.2) → arquivo da agência (ED-97) | Coberta |
| R5 | Fiscalizar e notificar o prestador | termo de fiscalização, prazo, resposta e acompanhamento **[sem tarefa: JOR-24]** | Lacuna |
| R6 | Compensações pagas ao usuário | relatório por tipo de usuário e de compensação **[sem tarefa: JOR-39]** | Lacuna |

### 3.14 Consumidor (portal e app)

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| U1 | Criar conta e vincular imóvel | conta no realm do portal (P3.3) → vínculo pelo código da conta e documento (P3.20) → vários imóveis (P3.20) | Coberta |
| U2 | Ver e pagar a conta | 2ª via (P3.3) → Pix (P3.2) → sem conta, se o ADR que altera o ADR-0022 for aceito (CAD-97) → autenticidade da fatura (P3.35) | Coberta |
| U3 | Histórico e documentos | contas e pagamentos de 120 meses (P3.20) → DANFAG e XML (P3.20) → certidão de quitação (ED-38) | Coberta |
| U4 | Pedir serviço e acompanhar | pedido gera protocolo e OS (P3.3) → agendamento (ED-38, CAD-94) → "a caminho" (ED-38, OPE-21) | Coberta |
| U5 | Reclamar e recorrer à ouvidoria | protocolo pelo portal (P3.3) → ouvidoria (ED-35) → pesquisa (ED-35) | Coberta |
| U6 | Informar vazamento na rua ou falta d'água sem conta | relato com foto e localização, sem login, que gera ocorrência ou protocolo `LOCATION_ONLY` **[sem tarefa: JOR-14]** → consulta da situação por endereço (ED-38, CAD-95) | Lacuna |
| U7 | Autoleitura | envio com crítica (ED-15) | Coberta |
| U8 | Débito automático | Pix Automático (P4.5, ED-27) | Coberta |
| U9 | Parcelar pelo portal | simulação (ED-38) → aceite e assinatura (P4.1, P3.34) | Coberta |
| U10 | Exercer direitos de titular de dados | pedido de acesso, correção, eliminação ou portabilidade pelo portal, com protocolo e prazo **[sem tarefa: JOR-15]** | Lacuna |
| U11 | App e avisos | app nas lojas (ED-39) → preferências por canal e finalidade (ED-41, CAD-84) → alerta de consumo anormal (P3.5, CAD-99) | Coberta |

### 3.15 Administrador do tenant

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| T1 | Criar usuário e dar acesso | convite (P0.13) → perfil de fábrica por persona **[sem tarefa: JOR-01]** → perfil composto (ED-06) → unidade e escopo (P3.30) → MFA por papel (P0.15) | Parcial |
| T2 | Manter parâmetros depois da implantação | proposta, aprovação dupla e aceite (P0.19, PARAMETROS §1) em tela do tenant **[sem tarefa: JOR-25]** | Parcial |
| T3 | Marca, tema e domínio | marca (P1.19) → tema (P1.18) → domínio próprio (P2.27) | Coberta |
| T4 | Catálogos e tipos de serviço | 22 catálogos (ED-12) → tipos de serviço (P1.31) → preços (ED-34) | Coberta |
| T5 | Modelos de documento e mensagem | cartas, avisos, termos, comprovantes, e-mails e SMS com versão e aprovação **[sem tarefa: JOR-06]** | Lacuna |
| T6 | Integrações | credencial de API e webhooks (P2.26) → saúde dos conectores (P3.37) | Coberta |
| T7 | Revisar acessos | sessões e trilha (ED-06) → recertificação (ED-06, ED-73) | Coberta |
| T8 | Dispositivos de campo | registrar, bloquear e aposentar (ED-18) | Coberta |
| T9 | Contrato e suporte com o Ziip | faturas e NFS-e (P2.14) → chamado (P1.23) → consentimento de acesso assistido (P3.17) | Coberta |
| T10 | Carga de dados por planilha | modelo, validação, relatório de recusas e aplicação por OS de lote **[sem tarefa: JOR-26]** | Parcial |

### 3.16 Operador da Ziip (Central)

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| Z1 | Implantar tenant | assistente com parâmetros e aceite (P1.6) → jornada visível ao cliente (P1.6, PLT-21) → metodologia e treinamento (ED-09, ED-11) → ativar vertical (P0.47) | Coberta |
| Z2 | Monitorar | abas da Central (P1.17, P2.11, P3.15) → aba Estoque **[sem tarefa: JOR-36]** → alertas (P1.17) | Parcial |
| Z3 | Atender chamado | fila com SLA (P1.23, P2.15) → acesso assistido com consentimento (P3.17) → resposta por e-mail e deflexão (P4.8) | Coberta |
| Z4 | Publicar versão | changelog validado (P0.41) → novidades por tenant (P2.16) → migração em ondas (P1.14) | Coberta |
| Z5 | Cobrar o contrato | contrato, empenho e fatura (P2.12) → NFS-e (P2.13) → restrição manual (P2.12) | Coberta |
| Z6 | Suspender e encerrar | suspensão com aviso (P1.6) → exportação e eliminação com carência (P1.6, P3.16) | Coberta |
| Z7 | Incidente | resposta e RIPD (P3.10) → status público (P3.18) → acesso de emergência (P3.17) | Coberta |
| Z8 | Acompanhar normas | radar (P3.15) → biblioteca (P1.34) | Coberta |

### 3.17 Jornadas transversais pedidas

| # | Jornada | Passos e tarefas | Cobertura |
|---|---|---|---|
| J1 | Ligação nova do pedido à primeira fatura | ver A3 | Parcial (JOR-07, JOR-11, JOR-12) |
| J2 | Corte e religação | seleção (P3.4) → aviso com prova (P3.5) → espera do prazo e vedações (P3.4) → OS de corte por nível (ED-29) → revalidação (ED-20) → execução registrada → pagamento ou parcelamento (P3.1, P4.1) → religação automática (P3.4) → compensação por atraso (P3.4) → relatório de cortadas não religadas (ED-46) | Coberta; sem app depende da JOR-07 |
| J3 | Vazamento até reparo e indicador | comunicação pelo atendimento (ED-35) ou sem login **[sem tarefa: JOR-14]** → ocorrência de rede (ED-92) → localização no mapa (P3.25) → OS com alvo de rede (P1.31) → isolamento e aviso (ED-54, ED-41) → execução e material (ED-20, ED-58) → recomposição como OS filha (ED-36) → histórico no trecho (ED-56) → volume perdido e tempo de reparo **[sem tarefa: JOR-33]** → indicadores I-04 e I-05 (ED-92, ED-77) → balanço (ED-85) | Parcial |
| J4 | Reclamação até resposta e ouvidoria | ver A1, S3 e U5 | Parcial (JOR-06) |
| J5 | Auditoria interna até ação corretiva | ver I1 a I4 | Parcial (JOR-05, JOR-06) |

Total: 120 jornadas por perfil (A1 a Z8), mais as cinco transversais que as recombinam.

## 4. Quadro de achados

| ID | Perfil ou módulo | Tipo | Grav. | Proposta | Tarefa onde encaixa |
|---|---|---|---|---|---|
| JOR-01 | Administração; todos os perfis | Lacuna de função | Alta | Perfis de fábrica por persona, compostos do catálogo `recurso:ação`, cada um com menu e página inicial próprios: atendente, supervisor comercial, faturista, analista de arrecadação, leiturista, programador de OS, executor de campo, almoxarife, operador de estação, analista de perdas, laboratorista, responsável técnico da qualidade, editor e revisor do cadastro técnico, gestor, auditor interno, ouvidor, encarregado de dados, auditor externo e regulador. Hoje só existem ADMIN, OPERATOR, MANAGER e VIEWER (P0.13), e a ED-06 deixa a composição ao administrador do tenant | P0.13 (catálogo e perfis) e ED-06 (composição) |
| JOR-02 | Administração; plataforma | Consistência | Média | Documento do catálogo de permissões gerado do código e conferido no CI, com módulo, recurso, ação, perfil de fábrica e tarefa de origem; uniformizar o formato `recurso:ação` (hoje `gis.edit` e `registry.reveal-document` convivem com `cert:write` e `so:close`) | P0.13 |
| JOR-03 | Navegação; ficha da unidade | Sem dono | Média | Documento de mapa do produto (módulos, itens de menu, rotas, ícone, permissão, tarefa) derivado do `vertical.json`, e regra de que cada tarefa que cria uma fonte da ficha da unidade (contas, débitos, OS, atendimentos, documentos, qualidade) acrescenta a aba correspondente | P0.47 (manifesto) e P1.12; abas pela tarefa de origem |
| JOR-04 | Início; todos | Item de menu ausente | Média | Página inicial por perfil com pendências (JOR-05), alertas (ED-07), avisos (P3.36), atalhos e até quatro indicadores do papel, lendo o registro único de indicadores (JOR-19) | P3.36 (com ED-07) |
| JOR-05 | Início; todos | Lacuna de função | Alta | Ampliar a caixa da P2.28 de "aprovações" para "minhas tarefas": todo item com responsável e prazo, de qualquer módulo (protocolo a vencer, OS atrasada, conta retida, rejeição fiscal, pacote GIS a revisar, ação corretiva, calibração vencendo, pedido de titular, obrigação regulatória, divergência de conciliação), com filtro por prazo e delegação já prevista | P2.28 |
| JOR-06 | Transversal; atendimento, cobrança, qualidade, administração | Lacuna de função | Alta | Motor único de modelos de documento e mensagem por tenant, com variáveis tipadas, versão, vigência, aprovação, marca (P2.17) e prévia: comprovante de protocolo, resposta ao usuário, aviso de corte, cartas de cobrança, termo de parcelamento, auto de infração, certidões, CDA, OS impressa, laudo, relatório de auditoria, e-mail, SMS e WhatsApp. O motor de mensagens legais da fatura (CAD-96) e o leiaute térmico da ED-19 passam a usá-lo | P2.17 (base); consumido por P3.4, P3.5, ED-19, ED-29, ED-31, ED-41, ED-51, P4.1, P3.31 |
| JOR-07 | Ordens de serviço | Passo sem tarefa | Média | Impressão da OS e do roteiro da equipe (com QR que abre a OS) e baixa posterior pelo backoffice com data e hora de execução, executor, materiais e foto, para equipe sem app e empreiteira; prazos de religação e compensação dependem da hora registrada | ED-36 (com o modelo da JOR-06) |
| JOR-08 | Fiscal; faturista | Passo sem tarefa | Alta | Tela "Documentos fiscais" do tenant: autorizadas, rejeitadas com a explicação da P2.16, em contingência, canceladas e substituídas; reenvio, inutilização de numeração e cancelamento pendente; alerta ao responsável. A ARQUITETURA-ALVO §2.3 cita a "fila de pendências do operador", mas nenhuma tarefa a constrói (a P2.11 é a visão da Ziip) | P2.6 (com P2.16) |
| JOR-09 | Medição; faturista | Passo sem tarefa | Média | Fila de análise das leituras e contas retidas: comparação com a média e os últimos meses, foto e ocorrência da leitura, ação em lote (liberar, manter, abrir `BILL_REVIEW`, pedir releitura), como visão do trabalho e não só relatório (P3.22 lista "leituras retidas") | P2.5 e P2.20 |
| JOR-10 | Medição; ordens de serviço | Integração indefinida | Média | Tabela de ocorrências de leitura com o efeito de cada uma: tipo de OS gerada, consumo a faturar (média, mínimo, sem faturar), mudança de situação e perda de benefício; teste por ocorrência | P2.5 (catálogo) e P1.31 (tipo de OS); ED-18 usa |
| JOR-11 | Faturamento | Passo sem tarefa | Média | Regra da primeira conta, da conta final e do ciclo com dias fora da faixa: proporcionalizar ou não a tarifa mínima ou fixa e o consumo pelos dias, como parâmetro de bifurcação com o padrão mais protetivo (proporcional) e chave a definir (exemplo `bill.partial_period_rule`) | P2.5 e PARAMETROS-DO-TENANT (ADR-0038) |
| JOR-12 | Atendimento; cadastro | Passo sem tarefa | Média | Entrega do contrato de adesão vigente na ligação nova e na troca de titularidade, com versão e ciência registradas (a P3.20 só publica e versiona o contrato) | P3.20 e ED-34 |
| JOR-13 | Arrecadação | Item de menu ausente | Média | Caixa próprio do prestador: abertura e fechamento de caixa, recebimento em espécie e cartão com comprovante, sangria, conferência e baixa pela mesma classificação da P3.1. Confirmar com o piloto e com os editais se há guichê (o GSAN tem a forma de arrecadação "guichê/caixa") | ED-26 |
| JOR-14 | Portal; atendimento; rede | Passo sem tarefa | Média | Relato sem login, com foto, localização e contato opcional, que cria ocorrência de rede (ED-92) ou protocolo com alvo `LOCATION_ONLY` (P1.31) e agrupa relatos do mesmo evento; proteção contra abuso já prevista para o portal (PLT-22) | ED-38 (canal) e ED-92 (ocorrência) |
| JOR-15 | Privacidade; consumidor; administração | Amarração e sem tarefa | Alta | A LGPD está na classe `REGULATORY` do catálogo de normas, mas o registro de operações de tratamento e o atendimento dos direitos do titular só aparecem na ED-74 (pacote voluntário ISO/IEC 27701, onda B). O código `sigsan-lgpd` já tem pedido do titular, RIPD, retenção e consentimento, sem tela, fila nem canal. Proposta: tarefa de privacidade para todo tenant (canal no portal e no atendimento, protocolo e prazo, fila do encarregado, registro de operações, relatório) e a ED-74 só acrescenta o que a 27701 pede além da lei | nova tarefa no P3 ou ajuste da P3.3 e da P3.10; ED-74 reduzida ao pacote |
| JOR-16 | Operação de estações; qualidade | Amarração | Média | Separar a obrigação legal do pacote voluntário: geração e destinação do lodo de ETA e ETE (hoje na ED-70, ISO 14001) e automonitoramento do efluente da ETE (hoje na ED-80, onda C) entram na operação e na qualidade para todo tenant que tenha ETA ou ETE, já que CONAMA 430 e 498 estão na classe `REGULATORY` | ED-87 e ED-50; ED-70 e ED-80 mantêm só o que é da norma |
| JOR-17 | Resíduos sólidos | Sem tarefa | Média | Dar IDs à trilha GRS ou declará-la congelada como a infraestrutura parada (ADR-0010), e ligar o lodo do próprio tenant (JOR-16) ao MTR como gerador, que é o uso mais provável do módulo por um prestador só de água e esgoto | trilha GRS; decisão do dono |
| JOR-18 | Certificações; almoxarifado; laboratório | Amarração | Média | A P3.31 constrói avaliação de fornecedor "sobre a ED-37", que é da onda A e só cobre empreiteiras. Proposta: cadastro mínimo de fornecedor (material, serviço, laboratório externo, empreiteira) na P3.31, estendido pela ED-37 e lido pela ED-58 e pela ED-51 | P3.31 e ED-37 |
| JOR-19 | Gestão e regulação | Sem dono | Média | Declarar a ED-48 dona do registro de indicadores (definição, fórmula, fonte, meta, periodicidade, grau de confiança); P3.31 (objetivos), ED-77 (fichas), ED-49 (painéis), ED-93 (sala) e a página inicial (JOR-04) apenas leem | ED-48 |
| JOR-20 | Transversal | Sem dono | Média | Catálogo único de eventos de negócio (nome, carga sem dado pessoal, permissão de quem recebe), usado pela central de avisos (P3.36), pelos alertas do tenant (ED-07), pelos webhooks (P2.26), pelas notificações ao consumidor (P3.5, ED-41) e pela caixa (JOR-05) | P1.9 (outbox) e P2.26 |
| JOR-21 | Operação de estações; qualidade | Integração indefinida | Média | Dados de controle de processo do diário (turbidez por filtro, cloro, pH) alimentam a conformidade mensal e o arquivo do SISAGUA sem redigitação | ED-87 e ED-50 |
| JOR-22 | Qualidade; laboratorista | Passo sem tarefa | Média | Passo de comunicação de não conformidade com risco à autoridade de saúde pública e à população, com modelo (JOR-06), registro de envio e prazo; conferir artigos da Portaria GM/MS 888/2021 com o responsável técnico | ED-50 |
| JOR-23 | Regulador | Integração indefinida | Média | Perfil externo do regulador distinto do auditor externo de certificação: escopo pelos dados e relatórios regulatórios da agência (ED-97, ED-48, art. 54, ouvidoria, interrupções), sem pacote de evidência ISO e sem dado pessoal, com prazo e registro de cada leitura como na ED-75 | ED-75 (mecanismo) e ED-97 (escopo) |
| JOR-24 | Gestão; regulador | Passo sem tarefa | Média | Fiscalização recebida da agência, do Tribunal de Contas ou do órgão ambiental como origem de ocorrência (P2.25): termo, constatações, prazo de resposta contado pelo motor de prazos, plano de ação e evidência de atendimento, com lembrete pela agenda de obrigações (ED-98) | P2.25 (origem) e ED-98 |
| JOR-25 | Administração | Passo sem tarefa | Média | Tela do tenant para propor, aprovar e aceitar mudança de parâmetro depois da implantação, com comparação de versões, vigência futura e alerta de risco; hoje o fluxo descrito é o assistente do console da plataforma (P1.6). Verificar se a intenção já é essa e só não está escrita | P0.19 ou P1.6 |
| JOR-26 | Transversal; administração | Lacuna de função | Média | Importador genérico de planilha: modelo para baixar, área de preparação, validação, relatório de recusas, aplicação por OS de lote e histórico. Hoje cada tarefa tem sua carga (P1.3 CSV, ED-57, P2.24, ED-20, ED-12) | P1.3 (base) |
| JOR-27 | Transversal | Lacuna de função | Baixa | Observações internas com menção a usuário em protocolo, OS, unidade, conta e ocorrência, com visibilidade (interna ou ao usuário) e sem dado pessoal sensível em texto livre (alerta de campo) | P1.31 |
| JOR-28 | Transversal | Consistência | Média | Aba "Histórico" padronizada em todo registro, lendo as OS aplicadas (ADR-0039), as versões (conta, parâmetro, tarifa, pacote GIS) e a trilha, com quem, quando, antes e depois | P1.31 e P1.12 |
| JOR-29 | Transversal | Consistência | Média | Regra única de exportação de listas: permissão `*:export` por recurso, máscara pelo teto (ADR-0008), registro no `personal_data_access_log`, limite de linhas e geração assíncrona em "Arquivos gerados", onde também ficam os relatórios agendados (P3.22) | P1.12 e P3.22 |
| JOR-30 | Início | Item de menu ausente | Baixa | Agenda que só lê: cronograma de faturamento (P2.5), programação de OS (ED-36), auditorias (P3.31), calibrações (ED-51), preventivas (ED-56), obrigações (ED-98), interrupções programadas (ED-35) | P2.28 |
| JOR-31 | Atendimento; OS | Passo sem tarefa | Baixa | Atribuir e redistribuir protocolos e OS entre pessoas, além do trâmite entre unidades da P3.30, com motivo e registro | ED-35 e ED-36 |
| JOR-32 | Almoxarifado | Passo sem tarefa | Média | Entrada de material pela NF-e do fornecedor (leitura do XML, conferência de item e quantidade) e transferência entre locais com trânsito; não altera a decisão de deixar compras para 2029 | ED-58 |
| JOR-33 | Perdas; rede | Passo sem tarefa | Média | Volume perdido estimado e tempo de reparo por ocorrência de rede, gravados no encerramento da OS, como entrada do balanço (perdas reais) e dos indicadores | ED-92 e ED-85 |
| JOR-34 | Perdas | Passo sem tarefa | Baixa | Ajuste do volume micromedido ao período do macromedido (leituras de datas diferentes por rota), com método declarado | ED-85 |
| JOR-35 | Faturamento; cadastro | Passo sem tarefa | Baixa | Registro de conta não entregue (devolução, endereço não localizado), que alimenta a qualidade cadastral (P3.32) e o contato verificado | P2.8 e P3.32 |
| JOR-36 | Central Ziip | Consistência | Baixa | Incluir a aba Estoque (CENTRAL-ZIIP §3) no aceite de uma tarefa | P3.15 ou ED-58 |
| JOR-37 | Qualidade; campo | Integração indefinida | Baixa | Coleta de amostra como OS de campo no app, com etiqueta e cadeia de custódia, entregando a amostra ao laboratório | ED-50 e ED-20 |
| JOR-38 | Portal | Lacuna de função | Baixa | Visão do órgão centralizador e do grande cliente com muitas unidades: lista, exportação e fatura agrupadora | P3.20 e ED-22 |
| JOR-39 | Relatórios | Lacuna de função | Média | Relatórios ausentes da seção 5.2: operação de estações, almoxarifado analítico, privacidade, acessos, compensações pagas ao usuário, produtividade por atendente | ED-46 (catálogo) e tarefas de origem |

## 5. Catálogo de relatórios

### 5.1 Por módulo

Fonte: P3.22 (núcleo e cerca de 20 relatórios do piloto), ED-46 (catálogo dos editais, seções 19 da VISAN e 2.28 do
SAMAE, resumido do 18g §2.4), ED-47 e ED-49 (BI e painéis) e as tarefas citadas.

| Módulo | Operacionais | Gerenciais | Regulatórios |
|---|---|---|---|
| Atendimento | atendimentos por período, por canal e por motivo (ED-46); serviços por protocolo com tempos (P3.22) | painel de atendimento (ED-49); satisfação por canal (ED-46) | reclamações do art. 54 da NR 11 (P3.22); relatório anual da ouvidoria (ED-35); LAI (ED-99) |
| Cadastro | ligações e economias por situação e categoria (P3.22, ED-46); duplicidades de matrícula, hidrômetro e logradouro (ED-46); recadastramento, cinco relatórios (ED-16) | qualidade cadastral por rota e setor (P3.32) | tarifa social e isenções (P3.22); índice de tarifa social e lista positiva (ED-97) |
| Medição | leituras retidas e ocorrências (P3.22); produtividade e tempos por leiturista; não retornadas; distribuição por coletor (ED-46) | painel de leitura por grupo e dia (ED-46, ED-49) | — |
| Faturamento | contas emitidas, canceladas e retificadas (P3.22); fechamento do ciclo por grupo (P3.22); segundas vias com motivo; faturas manuais (ED-46) | faturamento sucinto e detalhado por categoria e faixa (P3.22); histograma (P3.22); painel de faturamento (ED-49) | NFAg-e autorizadas, rejeitadas e em contingência (P3.22) |
| Arrecadação | arrecadação diária (P3.22); não identificados e duplicidades (P3.22, P3.29); retorno por convênio (P3.22); extrato e depósitos (ED-46) | painel de arrecadação (ED-49) | — |
| Cobrança | aptos ao corte e avisos emitidos (P3.22); motivos de não geração (P3.22); cortadas não religadas (ED-46); negativação (ED-30) | painel de cobrança (ED-49); desempenho da cobrança (ED-46) | dívida ativa e e-Sfinge (ED-31, ED-33) |
| Contabilidade | lançamentos por processo, razão, saldo por conta (ED-46) | contas a receber e provisão (ED-32); projeção (ED-100) | contabilidade regulatória da NR 16 (ED-32, ED-56) |
| OS e campo | tempos por estágio, equipe e prazo; não executadas; espelho (ED-46); OS no mapa (P3.27) | painel operacional (ED-49); custo total da OS (ED-36) | prazos de serviço da NR 11 (ED-46) |
| Almoxarifado | estoque, materiais por serviço e por equipe (ED-46, ED-58); negativos diários (ED-58) | — | — |
| Qualidade da água | antes e depois do fechamento; anual; cronograma de coleta; recoletas; fora de conformidade por ponto (ED-50, ED-46) | — | SISAGUA (ED-50); relatório anual ao consumidor (ED-79); bloco na conta (P2.24) |
| Perdas | — | balanço por DMC (ED-85); ganho de vazamento (ED-86) | plano e declaração da NR 15 (ED-78); SINISA (P4.2) |
| GIS | extensão por domínio, material, DN, década e setor; precisão (P3.27) | temáticos (P3.27) | inventário MCPSB (ED-56) |
| Gestão | relatórios agendados (P3.22) | indicadores com meta e semáforo (ED-48); sete painéis (ED-49); sala de situação (ED-93) | SINISA (P4.2, ED-48); pacote por agência (ED-97); NR 8 e NR 9 (ED-48, ED-77) |
| Certificações | mapa de cláusulas, pendências e vencimentos (P3.22, ajuste v2.17; P3.31) | análise crítica (P3.31) | pacote de evidência (ED-75) |
| Plataforma (Ziip) | relatório mensal de SLA, FRT, MTTR e CSAT (P2.15); disponibilidade por contrato (P3.15) | Health Score (P1.17) | kit de evidências (P3.15) |

### 5.2 O que falta por perfil

| Perfil | Relatório ausente | Achado |
|---|---|---|
| Supervisor comercial | produtividade por atendente (protocolos, tempo médio, reabertura) | JOR-39 |
| Regulador e supervisor | compensações pagas ao usuário por tipo (corte indevido, atraso de religação, interrupção, qualidade); o 18g marca o item TR-665/SG-891 como não planejado, e a P3.4 já calcula a compensação | JOR-39 |
| Operador de estação | boletim diário de operação; consumo de químicos por m³ tratado; horas de funcionamento e paradas por equipamento | JOR-39 |
| Almoxarife | curva ABC; giro; itens sem movimento; divergência de inventário; consumo por tipo de OS | JOR-39 |
| Analista de perdas | IVI e ILI por período no formato do SINISA, com o balanço da ED-78 (confirmar na ED-78) | seção 6 |
| Encarregado de dados | pedidos do titular por prazo e situação; acessos a dado pessoal por usuário (`personal_data_access_log`); incidentes | JOR-15, JOR-39 |
| Administrador do tenant | usuários inativos; perfis com permissão sensível; uso de cotas e de mensagens | JOR-39 (parte em ED-06 e ED-73) |
| Gestor | ocorrências de rede por causa, tempo de reparo e volume perdido | JOR-33 |
| Auditor interno | relatório de auditoria em modelo | JOR-06 |

## 6. Inferências

1. **Perfis fora da lista pedida.** Analista de arrecadação (conciliação, P3.1), ouvidor (ED-35), encarregado de dados
   (JOR-15), responsável técnico da qualidade (ED-50), procurador (dívida ativa, ED-31) e contador (ED-32) têm jornadas
   próprias; foram tratados dentro dos perfis vizinhos e entram na lista da JOR-01.
2. **Etapas antes do lançamento.** Na homologação local, a leitura vem do coletor do fornecedor (P2.7), a edição do GIS
   é só por importação até a ED-53 e a OS de campo não tem app (ED-20). Como o lançamento ocorre depois de todas as
   ondas, isso não foi contado como achado, salvo a impressão e a baixa da OS (JOR-07), que continuam necessárias para
   equipe sem app e empreiteira.
3. **Compras e RH.** A decisão de deixar compras e RH para 2029 (ED-60 e ED-61) foi mantida. A JOR-32 trata só da
   entrada de material, que acontece mesmo sem módulo de compras.
4. **Caixa próprio (JOR-13).** A necessidade depende do piloto e dos editais; a inferência vem do GSAN, que tem a forma
   de arrecadação "guichê/caixa", e da prática de autarquias e SAAE.
5. **Portaria GM/MS 888/2021 (JOR-22).** A obrigação de comunicar não conformidade com risco à autoridade de saúde e à
   população foi afirmada sem conferir os artigos nesta revisão.
6. **Parâmetros (JOR-25).** É possível que a manutenção de parâmetros pelo tenant esteja implícita no motor da P0.19;
   o achado pede que seja escrita, não que seja criada se já existir.
7. **IVI e ILI.** A P4.2 cita ILI; não foi conferido se a ED-78 entrega o relatório por período.
8. **Lodo e MTR (JOR-16, JOR-17).** A ligação entre o lodo do tenant e o módulo de MTR é proposta desta revisão; o
   plano não a menciona.
9. **Catálogo de permissões (JOR-02).** A lista de nomes da seção 1 saiu de busca nos documentos; o código pode já ter
   outros nomes.
10. **Contagem de jornadas.** As 120 jornadas são as das tabelas 3.1 a 3.16; as cinco da 3.17 recombinam passos já
    contados.
