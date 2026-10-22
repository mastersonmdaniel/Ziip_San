# 22 — Incorporação dos achados 22a a 22d no plano (v2.18)

Decisão do dono do produto em 22/10/2026 ("coloca todas"): os 156 achados dos levantamentos 22a (cadastro, comercial e relacionamento), 22b (operação, campo, rede e inteligência), 22c (plataforma, segurança, integração e experiência) e 22d (gestão, regulatório e inovação de mercado) entram no plano. Esta tabela mostra, para cada achado, onde ele foi incorporado. "Nova" é tarefa criada na v2.18; "Ajusta" é tarefa existente que recebeu uma linha de ajuste v2.18 com o ID do achado. Itens marcados como parâmetro (Sim) criam ou ampliam parâmetros do tenant na implementação (ADR-0038); as chaves são definidas na tarefa, com a regra e o padrão mais protetivo.

Tamanhos das tarefas novas: "a estimar"; os totais da trilha não foram recalculados. Nenhuma data é prazo.

| ID | Tipo | Prio. | Esf. | Achado | Destino no plano |
|---|---|---|---|---|---|
| CAD-65 | A | A | M | Titular falecido e sucessão da conta | Ajusta: P1.3, P1.31, ED-30 |
| CAD-66 | A | M | M | Representação legal e terceiro autorizado | Ajusta: P1.3, P1.31, P3.3 |
| CAD-67 | A | B | P | Nome social e nome civil | Ajusta: P1.3, P2.17 |
| CAD-68 | A | A | M | Unidade sem usuário vigente (desocupada) | Ajusta: P1.3, P1.2, P2.5 |
| CAD-69 | A | A | M | Fonte alternativa de água no cadastro e na cobrança de esgoto | Ajusta: P1.3, P2.5, ED-13, ED-24 |
| CAD-70 | A | M | G | Esgoto não doméstico e carga poluidora do usuário | Nova: ED-83 |
| CAD-71 | A | M | G | Fluxo do empreendedor (loteamento, condomínio, transferência de redes) | Nova: ED-84 |
| CAD-72 | A | B | M | Ligação provisória, hidrante e carro-pipa | Nova: ED-91; Ajusta: ED-13, ED-34 |
| CAD-73 | A | M | M | Programas de regularização e renegociação por ato do ente | Ajusta: P4.1 |
| CAD-74 | A | M | M | Medida excepcional por área e mecanismo tarifário de contingência | Nova: ED-91; Ajusta: P2.5, ED-22, P3.4 |
| CAD-75 | A | M | M | Superendividamento e processo de repactuação | Ajusta: P4.1, ED-30 |
| CAD-76 | A | M | P | Canais externos de reclamação (Consumidor.gov.br, Procon, agência) | Ajusta: ED-35 |
| CAD-77 | A | M | P | Pedidos de acesso à informação (LAI) | Nova: ED-99; Ajusta: ED-35 |
| CAD-78 | A | M | M | Fusão e separação de cadastros duplicados | Ajusta: P1.3, P1.31 |
| CAD-79 | A | M | P | Destino da devolução (chave Pix) | Ajusta: P3.29 |
| CAD-80 | A | A | M | Usuários essenciais e vulneráveis (institucionais e pessoais) | Ajusta: P1.3, P3.4, ED-35 |
| CAD-81 | B | M | M | Perfil de atendimento prioritário e de acessibilidade | Ajusta: P1.3, ED-35, ED-40 |
| CAD-82 | B | M | M | Validação cadastral externa (CPF, CNPJ, biometria) | Ajusta: P1.3, P1.2, P1.31 |
| CAD-83 | B | M | M | Endereço normalizado e geocodificação do imóvel | Ajusta: P1.3, P2.23 |
| CAD-84 | B | M | M | Contatos verificados e preferências por finalidade | Ajusta: P1.3, ED-41, P3.5 |
| CAD-85 | B | M | M | Documentos exigidos por serviço e documentos emitidos verificáveis | Ajusta: ED-34, ED-38 |
| CAD-86 | B | A | G | Fluxo encadeado da ligação nova | Ajusta: ED-34, ED-35, ED-36 |
| CAD-87 | B | A | G | Ligação obrigatória e cobrança por disponibilidade | Nova: ED-88; Ajusta: P2.23, P2.5, P3.22 |
| CAD-88 | B | M | M | Categorias opcionais da NR 13 e benefícios com validade | Ajusta: P2.5, P1.5, ED-22 |
| CAD-89 | B | B | P | Sazonalidade da unidade | Ajusta: P1.3, P2.5 |
| CAD-90 | B | M | M | Cofaturamento (NR 13, arts. 46 a 56) | Ajusta: ED-25, ED-32 |
| CAD-91 | B | A | G | Identificação automática da tarifa social e fonte do CadÚnico por natureza jurídica | Ajusta: P1.5, ED-57 |
| CAD-92 | B | M | M | Índice de Tarifa Social, lista positiva da ANA e relatório anual | Nova: ED-97; Ajusta: P3.22, ED-48 |
| CAD-93 | B | M | M | Contratos especiais de grandes usuários e venda por atacado | Ajusta: ED-22, ED-17 |
| CAD-94 | B | M | M | Agendamento pelo usuário | Ajusta: ED-38, ED-36 |
| CAD-95 | B | M | P | Situação do abastecimento por endereço (consulta pública) | Ajusta: ED-35, ED-38 |
| CAD-96 | B | A | M | Motor de mensagens legais da fatura antes da ED-79 | Ajusta: P2.5, P2.17, P2.24 |
| CAD-97 | B | M | M | Segunda via e pagamento sem conta no portal | Ajusta: P3.3; Depende de ADR que altera o ADR-0022 (pagamento sem conta) |
| CAD-98 | C | A | G | Qualidade cadastral contínua | Nova: P3.32 |
| CAD-99 | C | A | M | Alerta preventivo de consumo anormal | Ajusta: P3.5, P2.5, ED-17 |
| CAD-100 | C | M | G | Régua de cobrança com promessa de pagamento e priorização | Ajusta: P4.1 |
| CAD-101 | C | M | M | Login gov.br como provedor federado do portal | Ajusta: P3.3, ED-38; Depende de ADR que altera o ADR-0022 (login gov.br) |
| CAD-102 | C | M | M | Fatura que explica a variação e compara com domicílios semelhantes | Ajusta: P2.17, ED-38, ED-43 |
| CAD-103 | C | M | M | Cruzamento com o cadastro imobiliário municipal | Nova: ED-95; Ajusta: ED-62, ED-16, ED-24 |
| CAD-104 | C | B | M | Planos de pagamento por capacidade (fatura nivelada e acordo por renda) | Ajusta: P4.1 |
| OPE-01 | A | alta | G | Módulo de perdas operacional sem tarefa dona (DMC, vazão mínima noturna, balanço por DMC) | Nova: ED-85; Ajusta: ED-78, ED-48; Onda B aprovada em 22/10/2026; ED-78 depende da ED-85 |
| OPE-02 | A | alta | G | Operação de ETA, ETE, elevatórias e reservatórios sem supervisório: diário, ronda e turno | Nova: ED-87; Ajusta: ED-17, ED-50, P1.31 |
| OPE-03 | A | alta | G | Soluções individuais, limpa-fossa e ligação obrigatória à rede | Nova: ED-88; Ajusta: ED-13, ED-22, ED-34 |
| OPE-04 | A | média | M | Gestão de pressão: zonas, pontos de controle e ajuste de válvulas redutoras | Nova: ED-86; Ajusta: ED-17 |
| OPE-05 | A | média | P | Usos operacionais e emergenciais e água exportada no balanço hídrico | Nova: ED-85; Ajusta: ED-81 |
| OPE-06 | A | média | M | Insumos químicos de tratamento: consumo, lote, ficha de segurança e controle da Polícia Federal | Nova: ED-89; Ajusta: ED-50, ED-58 |
| OPE-07 | A | média | P | Limpeza e desinfecção de reservatórios com registro público | Nova: ED-89; Ajusta: ED-50, ED-79 |
| OPE-08 | A | média | M | Eficiência energética das unidades (kWh por m³, demanda, fator de potência) | Nova: ED-90; Ajusta: ED-77, ED-48 |
| OPE-09 | A | média | M | Programa de exercício de válvulas, de hidrantes e de descarga de rede | Ajusta: ED-56, ED-54 |
| OPE-10 | A | média | M | Crise hídrica: rodízio, racionamento, bandeira de contingência e carro-pipa | Nova: ED-91; Ajusta: ED-35, ED-54 |
| OPE-11 | A | média | P | Privacidade do rastreamento do executor e das fotos de campo | Ajusta: ED-20, ED-66, P3.10 |
| OPE-12 | A | baixa | M | Inspeção de esgoto por vídeo (CCTV) e desobstrução programada | Ajusta: ED-56, ED-64 |
| OPE-13 | A | baixa | M | Efluentes não domésticos: cadastro, pré-tratamento e amostragem pelo prestador | Nova: ED-83; Ajusta: ED-50 |
| OPE-14 | B | alta | M | Segurança do trabalho na OS: habilitação, PET, análise de risco, espaço confinado e trabalho isolado | Ajusta: P1.31, P3.31, ED-59, ED-71; ADR-0041 ajustado (NR-1, 18, 33 e 35 do MTE como REGULATORY); ajusta P1.31, P3.31, ED-59, ED-71 |
| OPE-15 | B | alta | M | Alvo da OS além da unidade consumidora (rede, ativo, unidade operacional, ocorrência) | Ajusta: P1.31, ED-36 |
| OPE-16 | B | alta | M | Ocorrência de rede (incidente) como entidade, ligada às NR 9 Nível I-04 e I-05 | Nova: ED-92; Ajusta: ED-35, ED-54, ED-77 |
| OPE-17 | B | alta | M | Controle ativo de vazamento: pesquisa, reparo e ganho medido | Nova: ED-86 |
| OPE-18 | B | alta | G | Medição inteligente e telemetria: dispositivo, alarme que abre OS, validação e estimativa | Ajusta: ED-17, ED-15 |
| OPE-19 | B | alta | M | Dimensionamento, retenção e agregação de séries temporais | Ajusta: ED-17, P2.3, P0.23 |
| OPE-20 | B | alta | G | Manutenção preventiva de ativos não lineares (plano por tempo e por uso, rotas, checklist) | Ajusta: ED-56, ED-72 |
| OPE-21 | B | média | M | Janela de atendimento, "a caminho" e aceite do usuário na OS | Ajusta: ED-20, ED-41, ED-38 |
| OPE-22 | B | média | M | Sala de situação operacional em tempo quase real, fora do BI | Nova: ED-93; Ajusta: ED-49 |
| OPE-23 | B | média | M | Qualidade do trabalho de leitura: releitura amostral, faixa falsa, desvio de posição | Ajusta: ED-15, ED-20 |
| OPE-24 | B | média | M | Ciclo de vida econômico do parque de hidrômetros e bancada de ensaio | Ajusta: ED-14 |
| OPE-25 | B | média | M | Lodo de ETA e de ETE como resíduo do próprio tenant (gerador) | Ajusta: ED-70 |
| OPE-26 | B | média | P | Reclamação de qualidade que gera coleta de amostra e resposta no protocolo | Ajusta: ED-35, ED-50 |
| OPE-27 | B | média | G | Obras e projetos: viabilidade, projeto de loteamento, recebimento de rede, medição e recomposição | Nova: ED-84; Ajusta: ED-53, ED-37 |
| OPE-28 | B | média | M | Frota: checklist, documentos do motorista e equipamentos especiais como recurso | Ajusta: ED-59 |
| OPE-29 | B | média | P | Almoxarifado: número de série, lote, validade e kit por tipo de OS | Ajusta: ED-58 |
| OPE-30 | B | média | P | Contrato de ingestão de supervisório (somente leitura) e rascunho do ADR de exceção | Ajusta: ED-17 |
| OPE-31 | B | baixa | G | Resíduos sólidos operacionais: pesagem, roteiro, coleta seletiva e plano operacional | Mantida na trilha de resíduos sólidos (GRS, sob demanda); o relatório 22b serve de detalhamento quando a trilha for aberta |
| OPE-32 | B | baixa | M | Drenagem: plano de operação e manutenção e monitoramento pluviométrico | Ajusta: ED-64 |
| OPE-33 | C | alta | G | Otimização de programação e roteirização com habilidades, janelas e SLA | Ajusta: ED-36, ED-59 |
| OPE-34 | C | média | M | Leitura por foto com reconhecimento óptico e revisão por exceção | Ajusta: ED-18, ED-19 |
| OPE-35 | C | baixa | G | Modelo hidráulico calibrado com telemetria e cenários de falha | Ajusta: ED-63 |
| OPE-36 | C | média | M | Detecção de anomalia explicável em séries (vazão noturna, pressão, consumo) | Nova: ED-94 |
| OPE-37 | C | média | M | Cruzamento de edificações com unidades consumidoras para perdas aparentes | Nova: ED-95; Ajusta: P1.2, ED-57 |
| OPE-38 | C | média | M | Manutenção por condição e criticidade de peça sobressalente | Ajusta: ED-56, ED-58 |
| OPE-39 | C | baixa | P | Identificação de hidrômetro, lacre e ativo por QR, código de barras ou NFC | Ajusta: ED-18, ED-20 |
| OPE-40 | C | baixa | M | Assistência por voz e rascunho de parecer da OS, com os limites do ADR-0028 (c) | Ajusta: ED-43, ED-20 |
| PLT-01 | A | alta | G | API pública de integração: credencial por tenant, escopos, cota, ambiente de teste e ciclo de vida | Nova: P2.26; Ajusta: P1.11, ED-62, ED-17; Prioridade alta aprovada em 22/10/2026 |
| PLT-02 | A | média | M | Eventos e webhooks de saída para o tenant, com entrega confiável e assinatura | Nova: P2.26; Ajusta: P1.9, ED-07 |
| PLT-03 | A | alta | G | Restauração seletiva de um tenant e desfazer de operação em massa | Nova: P3.33; Ajusta: P0.28, P3.16, GL.3, LC.3 |
| PLT-04 | A | média | M | Desastre regional em território nacional: decisão, cofre isolado e cópia em segundo provedor | Ajusta: LC.3; Decidido em 22/10/2026: Multi-AZ mais cofre isolado na mesma região (§2.2 e LC.3) |
| PLT-05 | A | média | M | Domínio próprio do tenant (CNAME, certificado automático, proteção contra tomada de subdomínio) | Nova: P2.27; Ajusta: P1.19, LC.2 |
| PLT-06 | A | baixa | M | Login gov.br no portal, com as restrições reais de elegibilidade (ver GES-32) | Ajusta: P3.3, P0.38; Depende de ADR que altera o ADR-0022 (login gov.br) |
| PLT-07 | A | média | G | Assinatura eletrônica dos documentos do consumidor e do tenant, por nível da Lei 14.063 | Nova: P3.34; Ajusta: P3.20, P4.1, ED-19, ED-23, ED-38 |
| PLT-08 | A | alta | P | Baseline de segurança de produto: ASVS 5.0, API Top 10 e LLM Top 10 como critério de aceite | Ajusta: P3.8, LC.8, ED-73 |
| PLT-09 | A | média | P | Divulgação responsável de vulnerabilidade e Trust Center público | Nova: P3.38; Ajusta: P0.44, P3.18 |
| PLT-10 | A | alta | M | Entregabilidade e mensageria por tenant: e-mail, SMS e WhatsApp (ver GES-19) | Ajusta: ED-41, ED-42, P3.5, LC.2 |
| PLT-11 | A | alta | M | Proteção contra cobrança falsa: verificação pública de autenticidade e canais oficiais | Nova: P3.35; Ajusta: P3.20, P2.8, P3.2 |
| PLT-12 | A | média | M | Central de notificações do usuário e preferências | Nova: P3.36; Ajusta: ED-07, P1.9, P1.18 |
| PLT-13 | A | média | G | Caixa unificada de pendências e aprovações, com delegação temporária | Nova: P2.28; Ajusta: P3.30, P3.1, P2.20, P1.27, P2.22 |
| PLT-14 | A | média | P | Fuso horário e calendário operacional do tenant | Ajusta: P0.19, P2.1, P3.4 |
| PLT-15 | A | média | M | Busca global, atalhos de teclado, visões salvas e densidade | Nova: P1.33; Ajusta: P1.12, P1.18 |
| PLT-16 | A | alta | G | Framework declarativo de migração por legado | Ajusta: P1.2, P2.10, P2.19, ED-09 |
| PLT-17 | A | média | M | Medição de uso e custo por tenant, e base comprovável de ligações ativas | Ajusta: P2.12, ED-10 |
| PLT-18 | A | alta | G | Plataforma de IA: operação de modelos, segurança e aceite da ED-43 (ver GES-08) | Ajusta: ED-43; ADR-0042; ajusta ED-43 |
| PLT-19 | A | média | M | Exportação e integridade verificável da trilha de auditoria do tenant | Ajusta: P1.7, ED-06, ED-73 |
| PLT-20 | A | alta | G | Hub de integrações: saúde, credenciais, reprocessamento e teste de contrato | Nova: P3.37; Ajusta: P0.45, P3.15 |
| PLT-21 | A | média | M | Jornada de implantação visível ao cliente e gates de prontidão | Ajusta: P1.6, ED-09, ED-11 |
| PLT-22 | A | média | M | Defesa contra automação e abuso no portal, compatível com WCAG 3.3.8 | Ajusta: P3.3, P3.8 |
| PLT-23 | A | baixa | P | Prática de resiliência: exercícios de falha, alerta ligado a runbook, revisão pós-incidente | Ajusta: P3.11, LC.7 |
| PLT-24 | A | baixa | M | Validação cadastral e detecção de óbito do titular | Ajusta: P1.3, P1.31 |
| PLT-25 | A | baixa | P | Internacionalização mínima e idioma de preferência | Ajusta: P0.34, P1.18 |
| PLT-26 | B | alta | P | Webhook Pix: mTLS e confirmação, no lugar de "assinatura do PSP" | Ajusta: P3.2 |
| PLT-27 | B | alta | P | Calendário regulatório do débito automático e da cobrança híbrida (ver GES-15) | Ajusta: P0.30, P3.24, ED-27, P3.2 |
| PLT-28 | B | média | P | Capacidade do Keycloak e do realm do portal | Ajusta: P0.23, P2.9 |
| PLT-29 | B | média | M | SSO corporativo (OIDC e SAML) e SCIM por tenant, além de LDAP/AD | Ajusta: ED-06, P0.38 |
| PLT-30 | B | baixa | P | Política de senha e MFA alinhada ao NIST 800-63B-4; passkey no portal | Ajusta: ED-06, P3.3 |
| PLT-31 | B | média | P | Acessibilidade: WCAG 2.2 AA, declaração por tenant e Libras | Ajusta: P0.36, P3.3 |
| PLT-32 | B | média | P | Cardinalidade e custo das métricas por tenant | Ajusta: P0.8 |
| PLT-33 | B | média | P | Plataformas móveis: prazos de loja, contas de organização e atualização remota | Ajusta: ED-18, ED-39, P3.14 |
| PLT-34 | B | média | P | Incidente de segurança e contrato de operador (Res. ANPD 15/2024) | Ajusta: P3.9, P3.10 |
| PLT-35 | B | média | P | Esquema por tenant: testes de upgrade, catálogo e instrução preparada | Ajusta: P0.21, P2.2, P0.9 |
| PLT-36 | B | média | P | Tarifa social: origem da lista conforme a natureza jurídica do prestador | Ajusta: ED-57, P1.5 |
| PLT-37 | B | baixa | P | Cadeia de suprimentos: proveniência, fixação de versões e endurecimento do CI | Ajusta: P0.3 |
| PLT-38 | C | média | M | Detecção de anomalia no acesso a dados pessoais (comportamento do usuário) | Ajusta a Central de Segurança, o ADR-0008 e o RNF-41 (sem tarefa com ID; detalhar na implementação do acesso a dado pessoal) |
| PLT-39 | C | média | M | Clone mascarado do tenant para homologação e treinamento | Ajusta: ED-09, ED-11, P0.45 |
| PLT-40 | C | média | M | Segunda via e Pix em modo de contingência estático quando a API cai | Ajusta: P3.3, P2.18 |
| PLT-41 | C | baixa | M | Chave do cliente (BYOK e HYOK) opcional | Ajusta: LC.4 |
| PLT-42 | C | baixa | M | Telemetria de produto com privacidade (adoção e funil de implantação) | Ajusta: P0.8, P4.8 |
| GES-01 | A | Alta | G | Gestão tarifária: reajuste, revisão, cópia de versão e simulação de impacto | Nova: P2.29; Ajusta: P2.5, P1.6 |
| GES-02 | A | Média | M | Instrumento contratual regulatório (contrato de programa, concessão, PPP), aditivos e matriz de riscos | Nova: ED-96; Ajusta: ED-56 |
| GES-03 | A | Alta | M | Metas de universalização e indicadores das NR 8 e NR 9 por município | Ajusta: ED-48, ED-77 |
| GES-04 | A | Alta | G | Pacote regulatório de saída por agência, com subsídios e índice de tarifa social | Nova: ED-97; Ajusta: P3.22 |
| GES-05 | A | Média | M | Agenda de obrigações regulatórias e de controle do tenant | Nova: ED-98; Ajusta: P3.15, ED-07 |
| GES-06 | A | Média | M | Transparência ativa, LAI e dados abertos | Nova: ED-99 |
| GES-07 | A | Média | M | Contingência hídrica e tarifa sazonal | Nova: ED-91; Ajusta: P2.5 |
| GES-08 | A | Alta | P | Política de IA e registro de decisões automatizadas | ADR-0042 (política de IA e decisões automatizadas) |
| GES-09 | A | Média | M | Custo por m³ e por ligação, margem por categoria e custo total da OS | Ajusta: ED-32, ED-36, ED-48 |
| GES-10 | A | Média | M | Projeção de receita, arrecadação e caixa por cenário | Nova: ED-100; Ajusta: ED-32, ED-47 |
| GES-11 | A | Média | M | API pública, webhooks de saída e catálogo de conectores de parceiros | Nova: P2.26; Ajusta: P1.6, P0.10, ED-62 |
| GES-12 | A | Baixa | G | Obras e convênios de investimento (Transferegov e afins) | Nova: ED-102; Pacote pendente (aguarda decisão do dono sobre o recorte) |
| GES-13 | B | Alta | M | Reforma tributária além da alíquota: cashback, split payment, fato gerador, reequilíbrio | Ajusta: P2.4, P2.5 |
| GES-14 | B | Média | G | Contabilidade regulatória da NR 16 (MCRSB) e de-para com o plano do cliente | Ajusta: ED-32, ED-56 |
| GES-15 | B | Alta | M | Pix Automático e cobrança híbrida: regras operacionais do Banco Central | Ajusta: P3.1, P3.2, P4.5 |
| GES-16 | B | Média | P | SINISA: ciclo completo, mapa de variáveis e certificado de adimplência | Ajusta: P4.2, ED-48 |
| GES-17 | B | Média | M | Receitas alternativas, complementares e acessórias, com compartilhamento | Ajusta: ED-34 |
| GES-18 | B | Baixa | P | Dívida ativa: ajuizamento, alçada e prescrição | Ajusta: ED-31 |
| GES-19 | B | Média | P | Mensageria: custo por categoria de template e uso medido do SaaS | Ajusta: ED-10, ED-41, ED-42 |
| GES-20 | B | Baixa | M | Risco e continuidade do prestador: incidente operacional, resiliência climática | Ajusta: P3.31, ED-79 |
| GES-21 | B | Baixa | M | ESG e energia: kWh por m³, inventário simplificado de emissões | Nova: ED-90; Ajusta: ED-77 |
| GES-22 | B | Média | M | Perfil de acesso externo do regulador e do titular, somente leitura | Ajusta: ED-75, ED-04 |
| GES-23 | B | Baixa | P | Ganchos de integração para compras, RH e orçamento (2029) | Ajusta: P3.30, ED-58, ED-37 |
| GES-24 | C | Média | G | Previsão de inadimplência e régua de cobrança adaptativa | Ajusta: P4.1, ED-29 |
| GES-25 | C | Alta | M a G | Analítica de fraude, ligação clandestina e perdas aparentes | Nova: ED-95; Ajusta: ED-24, ED-14, ED-17 |
| GES-26 | C | Alta | P a M | Explicação automática da variação da conta e copiloto do atendente | Ajusta: ED-35, ED-43 |
| GES-27 | C | Média | G | Consumo quase em tempo real e alertas ao consumidor (AMI) | Ajusta: ED-17, ED-39, ED-41 |
| GES-28 | C | Baixa | M | Metas de consumo, comparação com pares e gamificação | Nova: ED-101 |
| GES-29 | C | Média | M | Benchmark com o SINISA aberto e, depois, entre tenants | Ajusta: ED-48, ED-49 |
| GES-30 | C | Média | M | Regulação por incentivo: apuração auditável e simulador de fatores Q, P e U | Ajusta: ED-77, ED-48 |
| GES-31 | C | Baixa | P | Gêmeo digital: integração, não construção | Ajusta: ED-63 |
| GES-32 | C | Baixa | M | Login gov.br como provedor de identidade opcional no portal | Ajusta: P3.3; Depende de ADR que altera o ADR-0022 (login gov.br) |
| GES-33 | C | Baixa | P | Open Finance (iniciação) e Pix por aproximação via PSP | Ajusta: P3.2 |
| GES-34 | C | Baixa | M | Antecipação de recebíveis e conta vinculada | Nova: ED-103; Pacote pendente (o relatório 22d não a recomenda agora) |
