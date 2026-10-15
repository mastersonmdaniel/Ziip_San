# Levantamento 20a — GSAN: regras de negócio de CADASTRO E LIGAÇÃO, cruzadas com o Ziip SAN

Data: 14/10/2026. Fonte (somente leitura, clean-room): `G:\Meu Drive\PROJETOS\Diversos\FONTES OPEN\GSAN`, pasta `src/gcom` (arquivos ISO-8859-1, lidos com conversão em memória). Nenhum código, SQL, JSP, texto de tela ou mensagem foi copiado; o documento registra conceitos e regras em palavras próprias e cita o caminho do arquivo só como evidência. Licença do GSAN segue não confirmada (ver `01` §2).

Convenções: **[FATO]** = visto no arquivo citado; **[INFERÊNCIA]** = dedução a confirmar. Caminhos abreviados: `CAP` = `src/gcom/atendimentopublico/ControladorAtendimentoPublicoSEJB.java` (12.191 linhas); `CLA` = `src/gcom/atendimentopublico/ligacaoagua/ControladorLigacaoAguaSEJB.java`; `CLE` = `.../ligacaoesgoto/ControladorLigacaoEsgotoSEJB.java`; `CIM` = `src/gcom/cadastro/imovel/ControladorImovelSEJB.java` (16.234 linhas); `CCL` = `src/gcom/cadastro/cliente/ControladorClienteSEJB.java`; `CLO` = `src/gcom/cadastro/localidade/ControladorLocalidadeSEJB.java`; `CMI` = `src/gcom/micromedicao/ControladorMicromedicao.java`; `SP` = `src/gcom/cadastro/sistemaparametro/SistemaParametro.java` e `.hbm.xml`; `CPE` = `src/gcom/seguranca/ControladorPermissaoEspecialLocal.java`; `V3_1` = `commercial/sigsan-registry/src/main/resources/db/migration/tenant/V3_1__create_registry_schema.sql`; `V5_1` = `operations/sigsan-metering/.../V5_1__create_metering_schema.sql`; `PDT` = `docs/arquitetura/PARAMETROS-DO-TENANT.md`; `PLANO` = `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` (v2.13).

Este documento não repete o que `01-gsan-visao-geral-e-cadastro.md` e `07-gsan-telas-cadastro-vs-ziip.md` já trazem (modelo de entidades, telas campo a campo, matriz de lacunas de campos). Ele aprofunda o que aqueles declararam como limite: religação, restabelecimento, corte, supressão e corte administrativo; movimentação, instalação, retirada e substituição de hidrômetro; situação de cobrança; unicidade da inscrição; a lista completa de parâmetros do sistema; e as diferenças por empresa.

---

## 1. Resumo executivo

1. As operações de ligação de água (ligar, cortar, corte administrativo, religar, suprimir, restabelecer, alterar situação) e de esgoto (ligar, tamponar, desativar, restabelecer, reativar) são **efeitos da execução de uma Ordem de Serviço** cujo tipo de serviço está amarrado a uma operação do sistema; cada uma valida o estado de origem, a existência da ligação, o imóvel ativo e o bloqueio "imóvel em campo", e gera débito quando o tipo de serviço tem tipo de débito [FATO, `CAP` e `CLA`].
2. A máquina de estados é **dirigida por dados** (catálogo com flags: fatura, consumo mínimo, rede, ligação, abastecimento, dias de corte, tipo de consumo), mas as **transições estão em código**, por id fixo. O Ziip SAN já tem o catálogo com flags (`V3_1`) e o histórico de situação; falta a entidade de ligação (prevista como `service_connections` em `V3_7`, P2.23) e as transições por OS (ED-13, ED-29).
3. A **inscrição** é verificada como única no serviço de inclusão e alteração do imóvel (ignorando o próprio imóvel); não há evidência de restrição única no banco do GSAN. O Ziip SAN cobre no banco com índice único parcial (`V3_1`).
4. A **variação por empresa** no cadastro é quase inexistente: seis das sete subclasses `ControladorCadastro<EMPRESA>SEJB` estão vazias; só a CAERN tem um método (emissão de boleto de doação). As diferenças reais estão espalhadas em condicionais por código FEBRABAN ou sigla da empresa: desempate da categoria principal, ordenação de arquivos, obrigatoriedade da sequência de rota, OS sem localidade e remetente de e-mail. Todas viram parâmetros de classe d no Ziip SAN.
5. `SistemaParametro` tem **205 parâmetros** (206 campos com a chave; 18 são referências a outras entidades) numa única linha por instalação, misturando identidade da empresa, competência corrente, regras de faturamento, cobrança, segurança, infraestrutura e até credencial de serviço externo. A seção 4 lista todas, agrupadas, com classe a/b/c/d proposta; só 4 chaves de cadastro existem hoje no Ziip SAN (`RegistrySettingsService`).
6. **Supressão** tem regra de política relevante: ligada por parâmetro, a supressão a pedido do cliente exige imóvel sem débito e a supressão por outro motivo exige permissão especial; a supressão retira o hidrômetro sem gerar débito.
7. **Hidrômetro**: instalação só de equipamento DISPONÍVEL; substituição exige informar o destino (situação e local de armazenagem) do retirado; cadastro em lote por prefixo e faixa; movimentação entre locais com motivo e parecer. O Ziip SAN tem `meters` e `meter_installations` com exclusão temporal (`V5_1`), mas sem vínculo com OS, sem lacre na ligação, sem destino do retirado e sem movimentação.
8. O GSAN não conhece as **vedações legais de corte** (Lei 14.015/2020, aviso prévio de 30 dias, horário comercial): para ele são regras da cobrança, externas ao cadastro. O Ziip SAN já as trava em G9 (`PDT`) e na P3.4; a lacuna é ligar a trava ao evento de corte da ligação quando ela existir.
9. **Situação de cobrança do imóvel** e **situação especial de faturamento** são períodos com comando, histórico e bloqueios (parcelamento, inclusão, retirada, exigência de advogado). Pertencem à cobrança e à dívida ativa (P4.1, ED-31), não ao cadastro; o Ziip SAN acerta em mantê-las fora de `consumer_units`.
10. Lacunas mais graves, em ordem: (i) ligação como entidade com transições por OS; (ii) OS tipada com operação, efeito e débito; (iii) hidrômetro ligado à OS, com lacre, destino do retirado e lote; (iv) parâmetros do cadastro (13 chaves faltantes); (v) alteração de inscrição com autorização e bloqueio "em leitura".

---

## 2. Catálogo de regras

Natureza: **LEGAL** (lei ou regulador), **POLÍTICA** (escolha da prestadora; vira parâmetro do tenant), **GSAN** (particularidade do legado; não absorver), **PRÁTICA** (boa prática de modelagem). Situação: **COBERTA**, **PARCIAL**, **AUSENTE**, **N/A** (não cabe absorver). Fase: P1–P3 do `PLANO` ou onda A/B/C da trilha de editais.

### 2.1 Território, inscrição e imóvel

| Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|
| CAD-01 Inscrição (localidade, setor, quadra, lote, sublote) única; na inclusão bloqueia citando a matrícula existente, na alteração ignora o próprio imóvel [FATO]. Unicidade no banco do GSAN não vista (sem DDL). | `CIM` ~13163 (passo FS0013 do caso de uso) | PRÁTICA | COBERTA no banco: índice único parcial `idx_units_inscription` (`V3_1`), libera inscrição de unidade excluída | Manter; garantir no serviço a mensagem com a matrícula que já ocupa a inscrição (código `UNIT_INSCRIPTION_TAKEN`) antes do erro do índice. P1.3 |
| CAD-02 Face de quadra obrigatória quando o parâmetro de uso de face está ligado; com face, rede de água e esgoto, distrito e bacia vêm da face e não da quadra [FATO] | `CIM` ~13130; `CLO` validarQuadra 1305+; `QuadraFace.java` | POLÍTICA | PARCIAL: chave `registry.use_block_face` existe (`RegistrySettingsService`), mas não há tabela de faces | Criar `block_faces` (rede por face, dificuldade, risco, pressão, intermitência) só quando um tenant pedir; até lá a chave fica documentada como inoperante. P2.23 (junto do GIS) ou onda A (ED-13) |
| CAD-03 Quadra: rede de água e de esgoto obrigatórias (sem face); distrito operacional obrigatório se há rede de água (total ou parcial); sistema de esgoto e bacia obrigatórios se há rede de esgoto [FATO] | `CLO` validarQuadra | PRÁTICA | PARCIAL: `blocks.has_water_network/has_sewer_network` booleanos e `dma_id` opcional (`V3_1`); não há "rede parcial" nem sistema/bacia de esgoto | Trocar os booleanos por enum `NONE/FULL/PARTIAL`; exigir `dma_id` quando há rede de água; `sewer_system_id`/`basin_id` quando o GIS os tiver (P1.26). P1.3 e P2.23 |
| CAD-04 A rota da quadra tem de pertencer ao mesmo setor comercial da quadra [FATO] | `CLO` validarQuadra | PRÁTICA | AUSENTE: `reading_routes` não tem `sector_id` (`V3_1`) | Adicionar `sector_id` à rota e a checagem no serviço. P1.3 |
| CAD-05 Número de quadra único por setor; código de setor único por localidade; localidade por código [FATO] | `CLO` validarQuadra; `V3_1` | PRÁTICA | COBERTA (`idx_blocks_sector_code`, `idx_sectors_locality_code`, `idx_localities_tenant_code`) | Nada |
| CAD-06 Localidade precisa de localidade pólo ("elo"); quadra em localidade sem elo é recusada [FATO] | `CLO` validarQuadra | GSAN | N/A | Não absorver; hierarquia regional e unidade de negócio só se o tenant usar (`01` P1) |
| CAD-07 Localidade, setor e quadra bloqueados para inserção de imóvel; liberação só com permissão especial [FATO] | `Quadra.BLOQUEIO_INSERIR_IMOVEL_*`; `07` T1.1 | POLÍTICA | PARCIAL: `blocked` nas três tabelas (`V3_1`); sem permissão de exceção | Permissão `registry.override-blocked-area` auditada. P1.3 |
| CAD-08 Setor comercial "alternativo" ativo não aceita imóvel novo [FATO] | `SetorComercial.indicadorSetorAlternativo`; `07` T9 | GSAN | N/A | Não absorver; `blocked` cobre a necessidade |
| CAD-09 Quadra com incremento automático gera o lote (maior lote + 1, sublote 0) [FATO em `01` R2] | `CIM` inserirImovelRetorno | POLÍTICA (por quadra) | AUSENTE: `blocks` sem a flag | `blocks.auto_lot_increment` e geração no serviço. P1.3 |
| CAD-10 Sequência na rota obrigatória só para uma empresa, codificada no validador de formulário [FATO] | `gcom/WEB-INF/validator.xml` l.480–500 e 625–645 | POLÍTICA | COBERTA: `registry.require_reading_sequence` | Nada; exemplo canônico de regra por empresa que vira parâmetro |
| CAD-11 Alteração de inscrição é processo em bloco (origem → destino) com autorização, aplicada online ou em batch; guarda inscrição anterior e atual, usuário que alterou e que autorizou, e avisa quando a troca de quadra muda o grupo de faturamento; habilitada por parâmetro [FATO] | `ImovelInscricaoAlterada.java`; `CIM` 14942, 14999; `SP.indicadorAlteracaoInscricaoImovel` | PRÁTICA | AUSENTE (tabela `inscription_changes` só proposta em `01` §8) | Nova tarefa P1 (sugestão P1.3b): `inscription_changes` com estados PENDING/AUTHORIZED/APPLIED/REJECTED e aviso de mudança de rota ou grupo de faturamento. P1 |
| CAD-12 "Imóvel em campo": quando a rota tem impressão simultânea e o arquivo de leitura da competência já foi gerado, qualquer operação de ligação ou hidrômetro é recusada até o fim da leitura [FATO] | `CMI` 39865 validarImovelEmCampo e isImovelEmCampo; chamado em `CAP` (ligar, religar, suprimir, restabelecer, instalar, retirar, substituir) | PRÁTICA | AUSENTE | Estado "em leitura" por rota e competência no módulo de medição; operações de ligação e hidrômetro recusam ou entram em fila. P2.5 e P2.23 |
| CAD-13 Concorrência otimista por carimbo de tempo em imóvel, ligação, OS e instalação de hidrômetro [FATO] | `CIM` 5766; `CLA` verificarLigacaoAguaControleConcorrencia; `CAP` | PRÁTICA | COBERTA (`@Version`, problema `stale-version`) | Nada |
| CAD-14 Categoria principal do imóvel = categoria com mais economias; empate resolvido pelo menor id, exceto numa empresa (CAERN), que usa outra regra [FATO] | `CIM` (dois trechos condicionados ao código FEBRABAN da CAERN) | POLÍTICA | PARCIAL: `ConsumerUnitService.composition()` deriva a categoria principal; critério de desempate não parametrizado | Parâmetro `registry.main_category_tiebreak` (enum: menor código, ordem explícita, maior tarifa), classe d. P1.3 |
| CAD-15 Ordenação de listas e arquivos de imóveis: por inscrição numa empresa, por sequencial de rota nas demais [FATO] | `RepositorioImovelHBM.java` (condicional pela sigla da empresa) | POLÍTICA | N/A hoje | Opção de ordenação nos arquivos de leitura e relatórios. P2.5 |
| CAD-16 Categorias, subcategorias, perfis e situações têm ids fixos no código do GSAN [FATO] | `Subcategoria.java`, `ImovelPerfil.java`, `LigacaoAguaSituacao.java` | GSAN | COBERTA: `V3_1` não semeia catálogos; comportamento por flags | Manter; onde o código precisar de um "papel" (situação que representa "ligado", por exemplo), usar `system_role` como em `reading_anomalies` (`V5_1`) |
| CAD-17 Exclusão do imóvel é lógica e encerra todos os vínculos vigentes com motivo "exclusão do imóvel" e data do dia [FATO] | `CIM` 1984–1987; `ClienteImovelFimRelacaoMotivo.EXCLUSAO_IMOVEL` | PRÁTICA | PARCIAL: `deleted_at`/`deleted_reason` existem; se `ConsumerUnitService.delete` fecha os vínculos não foi confirmado | Garantir no `delete`: fecha `unit_customers` com `end_reason` de papel `UNIT_DELETED`, com teste. P1.3 |

### 2.2 Cliente e vínculo

| Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|
| CAD-18 Unicidade na inclusão: CPF; CNPJ; RG com órgão expedidor e UF. Na atualização só o CNPJ é rechecado (excluindo o próprio) [FATO] | `CCL` 61–303 | PRÁTICA | COBERTA para documento (`idx_customers_document` por hash, libera excluído); RG não é chave (decisão do `07`) | Rechecar documento também na atualização (o GSAN deixa passar CPF repetido na manutenção); teste em `CustomerDocumentServiceTest`. P1.3 |
| CAD-19 Atualizar cliente apaga e reinsere todos os telefones e endereços [FATO] | `CCL` atualizarCliente | GSAN | N/A (`customer_contacts` com atualização por linha) | Não absorver; perde histórico e auditoria por linha |
| CAD-20 Cliente nasce com "ação de cobrança ativa" e "não gera arquivo texto" [FATO] | `CCL` inserirCliente | GSAN | N/A | Flags de cobrança pertencem a `collection`, não ao cadastro |
| CAD-21 Parâmetros de cliente: documento obrigatório (inclusão e manutenção, separados), checagem de duplicidade, consulta à Receita, validação de documento, nome com menos de dez letras, nome genérico (lista), uso de nome fantasia na conta [FATO] | `SP` | POLÍTICA | PARCIAL: só `registry.require_user_document` | Adicionar `registry.require_document_on_update`, `registry.min_name_length`, `registry.generic_names` (lista), `registry.use_trade_name_on_bill`; classe d. P1.3 e P1.6 |
| CAD-22 Cliente responsável superior (hierarquia) e consulta de subordinados [FATO] | `ControladorCadastro.pesquisarClientesSubordinados`; `Cliente.cliente` | PRÁTICA | AUSENTE (`parent_customer_id` não está em `V3_1`) | Entra com "unidades por órgão pagador" da ED-12. Onda A |
| CAD-23 Quem era o usuário do imóvel numa data [FATO] | `CCL` 2550 obterUsuarioImovelPorData | PRÁTICA | COBERTA pelo modelo (`unit_customers` com vigência); consulta por data a confirmar | Endpoint `GET /units/{id}/customers?at=` com teste. P1.3 |
| CAD-24 Encerramento de vínculo exige data e motivo de catálogo; motivos têm papel de sistema (exclusão do imóvel, saída de programa especial, atualização cadastral) [FATO] | `ClienteImovelFimRelacaoMotivo` (ids 6, 8, 10); `07` T5 | PRÁTICA | PARCIAL: `end_reason` texto livre (`V3_1`) | Catálogo `relation_end_reasons` com `system_role`. P1.3 |
| CAD-25 Permissões especiais do domínio: alterar nome de cliente, inserir ou alterar imóvel com perfil corporativo, imóvel com município do logradouro diferente do setor (inserir e manter), alterar situação de ligação com débito, efetuar supressão, visualizar dia de vencimento do cliente, OS seletivas de hidrômetro, vencimento alternativo fora do período [FATO] | `CPE` (18 métodos `verificarPermissao*`) | PRÁTICA | PARCIAL: `RegistryRoles` com papéis; sem permissões finas | Permissões nomeadas (`registry.*`, `connection.*`, `meter.*`) atribuíveis a papéis, auditadas. P1.3 |

### 2.3 Ligação de água: operações por OS

Regra geral [FATO, `CAP` 367–470 e 1093–1440; `CLA` 392–606]: toda operação (a) exige OS cujo tipo de serviço está associado à operação correspondente (ou é o tipo fixo da operação); (b) valida a OS (prazo, aditivo); (c) exige o imóvel da OS ou do RA; (d) exige imóvel ativo (não excluído); (e) exige ligação de água existente (exceto ligar); (f) valida o estado de origem; (g) ao efetuar, registra operação com usuário, atualiza a OS quando não veio do encerramento e gera o débito do serviço quando o tipo de serviço tem tipo de débito e não há motivo de não cobrança (valor, parcelas, percentual de cobrança). As variantes "sem RA" recebem a matrícula direto.

| Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|
| CAD-26 Operações de campo são efeitos de OS tipada (tipo de serviço → operação → efeito na ligação → débito) [FATO] | `CAP` validar*Exibir; `ServicoTipo`/`Operacao` | PRÁTICA | AUSENTE: `service_orders` tem `service_code_id` sem catálogo e `status` livre (`V1_1`) | Catálogo `service_types` com `operation` (enum), `debit_type_id`, `requires_request`; serviço de domínio `ConnectionTransitions`. P2.23 (base) e P3.4 (OS) |
| CAD-27 Ligar água: estado de origem POTENCIAL, FACTÍVEL ou EM FISCALIZAÇÃO; quadra (ou face) com rede diferente de "sem rede"; imóvel ativo; não "em campo"; perfil da ligação com tarifa; consumo mínimo informado não menor que o calculado [FATO] | `CAP` 341–470, 570 | PRÁTICA; consumo mínimo é POLÍTICA (CAD-37) | AUSENTE | `ConnectionTransitions.connectWater` com as pré-condições e teste por estado. P2.23 |
| CAD-28 Efeito de ligar: situação de água → LIGADO; data de implantação = hoje; esgoto "ligado fora de uso" → LIGADO; cria ou atualiza a entidade ligação (1:1 com o imóvel) [FATO] | `CAP` 230–341 | PRÁTICA | AUSENTE (`service_connections` previsto em `V3_7`) | P2.23 |
| CAD-29 Corte: origem LIGADO ou LIGADO EM ANÁLISE; exige motivo e tipo de corte; registra leitura, selo e data; OS com aditivo de prazo validado [FATO]. Tipo de corte tem flags "administrativo" e "exibir formulário" | `CLA` 392–547; `CorteTipo.java` | LEGAL para inadimplência (aviso ≥ 30 dias, vedações de dia e horário, prova do aviso: G9 do `PDT`); PRÁTICA quanto ao registro | PARCIAL: G9 travado e P3.4 bloqueia a OS; não há evento de corte na ligação | Ligar a trava G9 ao `cut_reason` do catálogo `cut.reasons`; o efeito CORTADO só por OS liberada. P3.4 e ED-29 |
| CAD-30 Corte administrativo: mesma origem (LIGADO ou EM ANÁLISE), sem RA, data própria de corte administrativo [FATO] | `CAP` 1180; `CLA` 547; `LigacaoAgua.dataCorteAdministrativo` | POLÍTICA | AUSENTE | Motivo de corte "administrativo" no catálogo `cut.reasons`, sujeito às vedações da classe b. ED-29 |
| CAD-31 Religação: origem só CORTADO; efeito LIGADO; esgoto fora de uso → LIGADO; valida "em campo"; gera débito (taxa de religação) se o serviço tiver [FATO] | `CAP` 1278, 1947–2054; `CLA` 630 | LEGAL: sem aviso prévio comprovado a taxa não é devida (Lei 13.460 art. 6º p.u.; `PDT` G9) | PARCIAL: P3.4 prevê "sem prova, religação sem taxa"; sem evento na ligação | Evento `RECONNECTED` em `unit_status_history` com `service_order_id`; taxa condicionada à prova do aviso. P3.4 e ED-29 |
| CAD-32 Supressão: origem LIGADO, EM ANÁLISE, CORTADO, SUPRIMIDO PARCIAL A PEDIDO, EM FISCALIZAÇÃO ou EM CANCELAMENTO; tipo total ou parcial; motivo de catálogo. Com o parâmetro de supressão ligado: a pedido do cliente só sem débito (contas, guias, débitos a cobrar); outro motivo só com permissão especial. Retira o hidrômetro sem gerar débito; registra selo e leitura de supressão [FATO] | `CAP` 1335, 3159–3330; `SupressaoTipo`, `SupressaoMotivo` (`atendimentopublico/ordemservico`) | POLÍTICA | AUSENTE | Parâmetro `cut.suppression_requires_settlement` (bool) e permissão `connection.suppress`; níveis de corte (cavalete, ramal, supressão) da ED-29. Onda A |
| CAD-33 Restabelecimento: origem SUPRIMIDO ou SUPRIMIDO PARCIAL A PEDIDO; efeito LIGADO; data de restabelecimento = encerramento da OS; esgoto fora de uso → LIGADO [FATO] | `CAP` 1093, 3330–3480; `CLA` 606 | POLÍTICA | AUSENTE | ED-29 e ED-13 |
| CAD-34 Variantes combinadas: ligar, religar e restabelecer "com instalação de hidrômetro" numa só execução [FATO] | `CAP` 5719, 5937, 7626, 7972 | PRÁTICA | AUSENTE | A OS admite mais de um efeito (lista de operações); evita duas OS para um serviço de campo. ED-13 e ED-14 |
| CAD-35 Alterar situação de ligação por OS específica: recusada se o imóvel tem débito, salvo permissão especial; se o destino não é LIGADO nem FACTÍVEL e não há hidrômetro, a entidade ligação é removida; com hidrômetro instalado, recusa; encerra a OS com "conclusão do serviço" [FATO] | `CAP` 6112–6365 | POLÍTICA e PRÁTICA | AUSENTE | Transição manual auditada, com permissão `connection.force-status` e justificativa; nunca apagar a ligação: encerrar com `ended_at`. P2.23 |
| CAD-36 A situação de água carrega "dias de corte" e um mapeamento para tipo de consumo usado pelo faturamento [FATO] | `LigacaoAguaSituacao.numeroDiasCorte`; `LigacaoAguaSituacaoConsumoTipo`; `CLA` 779; `faturamento/ControladorFaturamentoFINAL.java` | PRÁTICA | PARCIAL: `water_statuses` tem `bills`, `requires_network`, `connected`, `supplies`, `minimum_consumption`; faltam `days_to_cut` e `consumption_type` | Adicionar as duas colunas quando o ciclo precisar. P2.5 |
| CAD-37 Consumo mínimo fixado na ligação não pode ser menor que o calculado pela categoria e economias (água e esgoto) [FATO] | `CLA` 321; `CAP` 570; `CLE` 486 | POLÍTICA; a NR 13 da ANA veda consumo mínimo em contratos novos (`PDT` G1 `reg.contract_pre_nr13`) | PARCIAL: só o parâmetro de contrato pré-NR 13 | Campo `minimum_consumption_m3` em `service_connections` habilitado só quando `reg.contract_pre_nr13 = true`. P2.23 |
| CAD-38 Religar em lote imóveis cortados com consumo real (processo batch) [FATO pelo nome da operação] | `LigacaoAgua.ATRIBUTOS_RELIGAR_IMOVEIS_CORTADOS_COM_CONSUMO_REAL` | POLÍTICA | AUSENTE | Alerta de "consumo em ligação cortada" no módulo de perdas, com OS de fiscalização; religar em lote só por decisão do tenant. Onda A (ED-29) |
| CAD-39 Situação do imóvel é derivada da combinação água × esgoto por uma matriz de dados [FATO] | `CIM` 3250 obterSituacaoImovel; `ImovelSituacao` | PRÁTICA | AUSENTE | `property_status_matrix` ou cálculo por flags (ativo = água ou esgoto fatura). P2.5 |

### 2.4 Ligação de esgoto

| Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|
| CAD-40 Ligar esgoto: cria ou atualiza a ligação 1:1; situação → LIGADO; valida "em campo"; débito da OS [FATO] | `CAP` 1689–1893 | PRÁTICA | AUSENTE | P2.23 |
| CAD-41 Mudança de situação de faturamento do esgoto por OS: tamponamento e desativação exigem esgoto LIGADO e água nem LIGADO nem CORTADO; tamponamento → TAMPONADO; desativação → FACTÍVEL se a face tem rede, senão POTENCIAL; restabelecimento exige TAMPONADO → LIGADO; reativação exige FORA DE USO → LIGADO [FATO] | `CAP` 2509–2672, 2862–2950 | POLÍTICA; a interrupção de esgoto por inadimplência é vedada (Lei 11.445 art. 40 V; `PDT` `cut.sewer_allowed`) | AUSENTE | Transições de esgoto só por motivo técnico ou a pedido; o motivo "inadimplência" não existe para esgoto. P2.23 e ED-29 |
| CAD-42 Esgoto "ligado fora de uso" volta a LIGADO sempre que a água é ligada, religada ou restabelecida [FATO]; como entra em "fora de uso" não foi visto [INFERÊNCIA: ao suprimir ou cortar a água] | `CAP` efetuarLigacaoAgua, efetuarReligacaoAgua, efetuarRestabelecimentoLigacaoAgua | PRÁTICA | AUSENTE | Regra acoplada no serviço de transição; pergunta Q6 |
| CAD-43 Percentual de esgoto derivado do perfil; percentual de coleta editável só com permissão; volume mínimo fixado ≥ calculado [FATO em `07` T7 e `CAP` validarVolumeMinimoFixadoEsgoto] | `CLE` 412–555; `LigacaoEsgotoPerfil` | POLÍTICA | AUSENTE no cadastro (percentual de esgoto é regra tarifária do billing) | `service_connections.sewer_percentage` lido do perfil; alteração com permissão. P2.5 |
| CAD-44 Esgotamento (condição) obrigatório quando a situação de esgoto fatura [FATO em `07` T1.6] | `CIM` validarImovelAbaCaracteristicas | PRÁTICA | AUSENTE | Catálogo `sewer_discharge_types` e regra condicional. P1.3 |

### 2.5 Hidrômetro: instalação, retirada, substituição, remanejamento, lacre, lote e movimentação

| Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|
| CAD-45 Instalar: hidrômetro deve estar DISPONÍVEL; passa a INSTALADO; vincula-se à ligação de água (medição "ligação") ou ao imóvel (medição "poço", levando o tipo de poço); valida "em campo"; quando a OS não vem de documento de cobrança nem de RA, gera boletim de OS concluída com troca de proteção e de registro [FATO] | `CAP` 2146–2370 | PRÁTICA | PARCIAL: `meter_installations` com `kind WATER/WELL`, exclusão temporal e `status` (`V5_1`); sem `service_order_id`, sem troca de proteção/registro | `service_order_id`, `protection_replaced`, `valve_replaced` em `meter_installations`; estado do hidrômetro muda só pelo serviço. ED-14 (onda A), base na P2.5 |
| CAD-46 Substituir: novo deve estar DISPONÍVEL; a situação do retirado é obrigatória e não pode ser INSTALADO; local de armazenagem do retirado atualizado; histórico antigo fechado com usuário de retirada; histórico novo aberto; imóvel ativo [FATO] | `CAP` 2950–3160 | PRÁTICA | PARCIAL: `is_replacement`, `removed_on/removed_reading`; sem destino do retirado | Campos `removed_to_status`, `removed_to_location_id`; validação de DISPONÍVEL do novo. ED-14 |
| CAD-47 Retirar: data de retirada = hoje; limpa o vínculo na ligação ou no imóvel; situação e local de armazenagem do hidrômetro informados; débito da OS se houver [FATO] | `CAP` 2054–2146, 9897 (variante sem débito usada pela supressão) | PRÁTICA | PARCIAL | Idem CAD-46. ED-14 |
| CAD-48 Remanejar: muda local de instalação e proteção sem trocar o equipamento, por OS [FATO] | `CAP` 1893–1947, 859 | PRÁTICA | AUSENTE | Evento `RELOCATED` na instalação vigente. ED-14 |
| CAD-49 Cadastro em lote: prefixo fixo + faixa inicial e final; recusa se já existe número na faixa; número final = prefixo + sequência com zeros [FATO] | `CMI` 7237–7280 | PRÁTICA | AUSENTE (`meters` só unitário) | `POST /meters/batch` com prefixo e faixa; conflito devolve a quantidade já existente. ED-14; útil na P1.2 (importador) |
| CAD-50 Movimentação entre locais de armazenagem: data e hora, motivo, parecer, usuário, origem (local atual do hidrômetro) e destino; itens movimentados; faixa opcional [FATO] | `CMI` 7815+; `HidrometroMovimentacao`, `HidrometroMovimentado` | PRÁTICA | AUSENTE | Pertence ao almoxarifado da ED-58 (hidrômetro é item controlado em `inv.controlled_items`); `meter_movements` como movimentação de estoque. Onda A |
| CAD-51 Revisão por marca (intervalo em dias) e baixa com motivo [FATO em `01` 5.5] | `HidrometroMarca.intervaloDiasRevisao`; `HidrometroMotivoBaixa` | PRÁTICA; validade metrológica é LEGAL (Inmetro, ED-14) | PARCIAL: `last_revision_on`, `inmetro_approval` (`V5_1`); sem intervalo por marca | Catálogo `meter_brands.revision_interval_days` e alerta de vencidos. ED-14 |
| CAD-52 Lacre e selo: número de lacre na ligação (cavalete) e na instalação do hidrômetro; selos numerados de corte e de supressão [FATO] | `LigacaoAgua.numeroLacre/numeroSeloCorte/numeroSeloSupressao`; `HidrometroInstalacaoHistorico.numeroLacre/numeroSelo` | PRÁTICA | PARCIAL: `seal_number` em `meter_installations`; nada na ligação | `seal_number` e `tamper_seal_number` nos eventos de corte, supressão e instalação; estoque de lacres na ED-58. ED-14 |
| CAD-53 Situação do hidrômetro: instalado, em manutenção, disponível, roubado (com flag extraviado); baixado por motivo [FATO] | `HidrometroSituacao.java`; `01` 5.5 | PRÁTICA | COBERTA (`meters.status` com `WRITTEN_OFF`) | Nada |
| CAD-54 Hidrômetro de poço é segunda medição do imóvel, separada da ligação de água [FATO] | `MedicaoTipo` 1/2; `CAP` 2054+ | PRÁTICA | COBERTA (`kind WELL`) | Nada |

### 2.6 Cobrança e faturamento que tocam o cadastro

| Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|
| CAD-55 Situação de cobrança do imóvel: período (implantação, retirada, referências inicial e final), cliente alvo, motivo de revisão, escritório e advogado; a situação de catálogo diz se exige advogado, bloqueia parcelamento, inclusão ou retirada, se só se seleciona com permissão, se prescreve imóveis particulares e se exclui de cobrança por resultado [FATO] | `ImovelCobrancaSituacao.java`; `cobranca/CobrancaSituacao.java`, `CobrancaSituacaoTipo.java` | POLÍTICA | AUSENTE | Pertence à cobrança e à dívida ativa: `unit_collection_statuses` com vigência e flags. P4.1 e ED-31 |
| CAD-56 Situação especial de faturamento e de cobrança: entrada e retirada por comando, histórico por imóvel com ano/mês de retirada e observação, validação da competência do grupo [FATO] | `CIM` 2984–3120 | POLÍTICA | AUSENTE | `billing_exceptions` com vigência por competência e comando. P2.5 |
| CAD-57 Estado de faturamento e cobrança gravado dentro de `imovel` [FATO em `01`] | `Imovel.hbm.xml` | GSAN | COBERTA (separação por módulo) | Manter a separação |
| CAD-58 Débito gerado pela OS: tipo de débito do serviço, valor atual, parcelas, percentual de cobrança (100/70/50), motivo de não cobrança [FATO] | `CAP` e `CLA` (gerarDebitoOrdemServico) | POLÍTICA (preço de serviços: tabela da ERI) | AUSENTE | Tabela de preços de serviços por tenant com vigência; a OS gera `debits` e, quando couber, NFS-e (P3.6). ED-36 e P3.6 |

### 2.7 Variação por empresa e recadastramento

| Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|
| CAD-59 Das sete subclasses `ControladorCadastro<EMPRESA>SEJB`, seis estão vazias (20–21 linhas); só a CAERN tem um método: emissão de arquivo de boletos de doação a um hospital, por grupo de faturamento [FATO] | `src/gcom/cadastro/ControladorCadastro{CAEMA,CAERN,CAER,COMPESA,COSAMA,COSANPA,JUAZEIRO}SEJB.java` | GSAN | N/A | Confirma a tese "tenant + configuração"; doação a entidade (`ImovelDoacao`) é P2 do `01` |
| CAD-60 Variações reais do cadastro por empresa estão em condicionais por código FEBRABAN ou sigla: desempate da categoria principal (CAERN), ordenação de arquivo (COMPESA), sequência de rota obrigatória (CAERN), OS sem localidade (CAERN), remetente de e-mail do RA (COMPESA) [FATO] | `CIM`; `RepositorioImovelHBM`; `validator.xml`; `ordemservico/ControladorOrdemServicoSEJB.java`; `registroatendimento/ControladorRegistroAtendimentoSEJB.java` | POLÍTICA | PARCIAL (só a sequência de rota) | Cada uma vira chave de classe d (`registry.main_category_tiebreak`, `reading.file_order`, `registry.require_reading_sequence`, `so.allow_without_locality`, `notify.sender_name`). P1.3 e P1.6 |
| CAD-61 Códigos FEBRABAN da empresa fixos em constantes [FATO] | `SP` l.407–418; `Empresa.java` | POLÍTICA | COBERTA (`pay.febraban_company_code`, classe d) | Nada |
| CAD-62 O recadastramento pode cortar, suprimir e encerrar vínculo ao aplicar o retorno: há motivos de corte, supressão e fim de relação com papel "atualização cadastral" [FATO] | `MotivoCorte.ATUALIZACAO_CADASTRAL`; `SupressaoMotivo.ATUALIZACAO_CADASTRAL`; `ClienteImovelFimRelacaoMotivo.POR_ATU_CADASTRAL` | PRÁTICA | AUSENTE | Catálogos de motivo com `system_role = CADASTRAL_SURVEY`; a aplicação do retorno usa o mesmo serviço de transição (CAD-26). ED-16 |
| CAD-63 Imóvel em recadastramento (EM CAMPO) fica bloqueado para ligação e outras operações [FATO em `07` T13] | `SituacaoAtualizacaoCadastral`; `CAP` validarEncerramentoOsImovelEmCampo | PRÁTICA | AUSENTE | Mesma trava de CAD-12, com motivo distinto. ED-16 |
| CAD-64 Credencial de serviço externo (consulta a birô) guardada como parâmetro do sistema em texto [FATO] | `SP.loginSenhaCdl` | GSAN | N/A | Não absorver; credenciais só no cofre (ADR-0033) |

---

## 3. Máquina de estados da situação da ligação

### 3.1 Água (`LigacaoAguaSituacao`, nove estados) [FATO nos ids e flags; transições conforme a evidência indicada]

```
                 ligar (OS; quadra/face com rede; não em campo)
 POTENCIAL ──┐
 FACTÍVEL  ──┼───────────────────────────────► LIGADO ◄──────────────┐
 EM FISCALIZAÇÃO (4, "ligado em análise") ─┘      │                  │
                                                  │ corte / corte    │ religação (só de CORTADO)
                                                  │ administrativo   │ restabelecimento (de SUPRIMIDO
                                                  ▼                  │   ou SUPR. PARCIAL A PEDIDO)
                                               CORTADO ──────────────┘
      supressão (total → SUPRIMIDO; parcial → SUPR. PARCIAL / SUPR. PARCIAL A PEDIDO)
      origem: LIGADO, EM ANÁLISE, CORTADO, SUPR. PARCIAL A PEDIDO, EM FISCALIZAÇÃO, EM CANCELAMENTO
                                                  │
                      SUPRIMIDO (6) · SUPR. PARCIAL (7) · SUPR. PARCIAL A PEDIDO (8)
      EM CANCELAMENTO (9): estado de passagem antes da supressão/exclusão [INFERÊNCIA]
      Qualquer → qualquer: "alterar situação" por OS própria, com permissão se há débito (CAD-35)
```

| Transição | Pré-condições [FATO] | Efeitos [FATO salvo indicação] |
|---|---|---|
| Ligar | origem 1, 2 ou 4; rede na quadra/face ≠ sem rede; imóvel ativo; não em campo; consumo mínimo válido | LIGADO; `dataImplantacao` = hoje; cria/atualiza `LigacaoAgua`; esgoto FORA DE USO → LIGADO; débito da OS |
| Corte | origem 3 ou 4; ligação existe; motivo e tipo | CORTADO [INFERÊNCIA: efeito gravado pelo repositório, não lido]; data, selo e leitura de corte; débito da OS |
| Corte administrativo | origem 3 ou 4; sem RA | idem, em `dataCorteAdministrativo` |
| Religação | origem 5; não em campo | LIGADO; `dataReligacao`; esgoto FORA DE USO → LIGADO; débito (taxa de religação) |
| Supressão | origem 3, 4, 5, 8, 4, 9; se parâmetro ligado: a pedido exige sem débito; outro motivo exige permissão | SUPRIMIDO ou parcial (7/8) conforme tipo [INFERÊNCIA]; `dataSupressao`, selo, leitura; retira o hidrômetro sem débito |
| Restabelecimento | origem 6 ou 8; não em campo | LIGADO; `dataRestabelecimentoAgua` = encerramento da OS; esgoto FORA DE USO → LIGADO; débito |
| Alterar situação | OS do tipo próprio; sem débito ou permissão; sem hidrômetro instalado para sair de LIGADO/FACTÍVEL | nova situação; remove a entidade ligação quando destino não é 3 nem 2; OS encerrada por conclusão |

Efeitos no faturamento e no abastecimento vêm das flags da linha do catálogo e não do id [FATO nas flags; semântica por INFERÊNCIA]: `fatura` (gera conta), `consumo mínimo de faturamento` (volume cobrado sem medição), `existe rede`, `existe ligação`, `abastecimento` (recebe água), `água ativa` / `água desligada` / `água cadastrada` / `análise de água` (contagens gerenciais: `gerencial/cadastro/UnResumoConsumoAgua.java`, histogramas de `faturamento/`), `consumo real` (usa leitura), `dias de corte` (prazo após o qual o corte vira supressão ou gera fiscalização, usado em `CMI`) e o mapeamento situação → tipo de consumo lido pelo faturamento (`ControladorFaturamentoFINAL`). Leitura típica dos dados de uma concessionária [INFERÊNCIA]: LIGADO fatura com consumo real; CORTADO fatura sem abastecimento (mínimo ou zero, conforme flags); SUPRIMIDO e POTENCIAL/FACTÍVEL não faturam água; FACTÍVEL e POTENCIAL diferem só pela existência de rede.

### 3.2 Esgoto (`LigacaoEsgotoSituacao`, seis estados) [FATO]

```
 POTENCIAL (1) / FACTÍVEL (2) / EM FISCALIZAÇÃO (4) ──ligar esgoto──► LIGADO (3)
 LIGADO ──tamponamento (água ≠ LIGADO e ≠ CORTADO)──► TAMPONADO (6) ──restabelecimento──► LIGADO
 LIGADO ──desativação (idem)──► FACTÍVEL se a face tem rede, senão POTENCIAL
 LIGADO ──[INFERÊNCIA: supressão ou corte da água]──► LIGADO FORA DE USO (5)
 LIGADO FORA DE USO ──reativação por OS, ou automaticamente ao ligar/religar/restabelecer a água──► LIGADO
```

Flags da linha: `fatura`, `volume mínimo de faturamento`, `existe rede`, `existe ligação`. O percentual de esgoto (parcela do consumo de água faturada como esgoto) vem do perfil da ligação, não da situação. A regra "tamponamento e desativação só quando a água não está ligada nem cortada" protege contra desligar esgoto de imóvel abastecido, coerente com a vedação legal de interromper esgoto por inadimplência.

### 3.3 Proposta para o Ziip SAN

Manter o catálogo com flags (`water_statuses`, `sewer_statuses`) e acrescentar: `system_role` (CONNECTED, CUT, SUPPRESSED, POTENTIAL, FEASIBLE, OUT_OF_USE, CAPPED) para o código referir papel e não id; `days_to_cut`; `consumption_type`. Transições num serviço puro `ConnectionTransitions` (origens permitidas por operação, pré-condições, efeitos), acionado só por `service_orders` com `service_types.operation`, gravando `unit_status_history` com `service_order_id` e os `version_id` dos parâmetros G9 consultados (como pede `PDT` §1). Nunca apagar `service_connections`: encerrar com `ended_at` e motivo.

---

## 4. Parâmetros do sistema do GSAN (`SistemaParametro`) e proposta de classe

Fonte: `SP` (`.java` com 2.585 linhas: 206 campos privados, dos quais 1 é a chave e 18 são referências a outras entidades, isto é, 205 parâmetros; `.hbm.xml` com 209 elementos mapeados) [FATO]. Classe proposta segue o ADR-0015 (`PDT` §2): **a** travado, **b** padrão de fábrica com aceite, **c** só ampliável, **d** livre. Coluna `PDT` indica se já existe chave equivalente em `PARAMETROS-DO-TENANT.md` ou no código (`tenant_settings`, `RegistrySettingsService`). "Não param." = no Ziip SAN não é parâmetro (vira dado de cadastro, infraestrutura ou descarte). Nomes das propriedades conforme o GSAN, sem tradução, para rastreabilidade.

### 4.1 Identidade, endereço e marca da prestadora

| Propriedade GSAN | Governa | Cl. | PDT / Ziip |
|---|---|---|---|
| nomeEstado, nomeEmpresa, nomeAbreviadoEmpresa, cnpjEmpresa, inscricaoEstadual, inscricaoMunicipal | identidade fiscal da prestadora, impressa em conta e arquivos | d | `tenant_catalog` (`V1:26`) e `reg.legal_nature`; inscrições: novo |
| numeroImovel, complementoEndereco, logradouro, bairro, enderecoReferencia, cep, logradouroBairro, logradouroCep | endereço da sede | d | `tenant_catalog` só endereço simples; normalizar com `addresses` |
| numeroTelefone, dddTelefone, numeroRamal, numeroFax, numero0800Empresa, descricaoEmail, dsEmailResponsavel, nomeSiteEmpresa | canais de contato na conta e nos avisos | d | novo (`contact.*`) |
| imagemLogomarca, imagemRelatorio, imagemConta, tituloPagina, mensagemSistema | marca e aviso de tela | d | `branding.*` (ADR-0016); `mensagemSistema` → aviso do console |
| urlhelp, urlAcessoInternet, url2ViaConta | links de ajuda, portal e 2ª via | d | ajuda própria (ADR-0020); portal (ADR-0022) |
| clientePresidente, clienteDiretorComercial, unidadeOrganizacionalIdPresidencia, resolucaoDiretoria | signatários e ato de diretoria citados em documentos | d | novo (`doc.signatories`), sem apontar para cliente |
| contaBancaria, codigoEmpresaFebraban, numeroLayoutFebraban, numeroContratoPrestacaoServico | conta de arrecadação e código no leiaute FEBRABAN | d | `pay.agreements`, `pay.febraban_company_code` |
| descricaoAplicativoAtendimento, telefoneAplicativoAtendimento | nome e telefone do aplicativo de atendimento impresso na conta | d | `branding.*` / portal |
| nomeControlador | sigla da empresa que escolhe a subclasse `Controlador*<EMPRESA>` | — | GSAN; não existe (tenant resolve) |
| dataImplantacaoSistema | data em que o sistema entrou em operação (limite de consultas históricas) | d | novo (`tenant.go_live_date`), gravado no corte (LC.13) |

### 4.2 Normas publicadas no autoatendimento

| Propriedade GSAN | Governa | Cl. | PDT / Ziip |
|---|---|---|---|
| descricaoDecreto/arquivoDecreto, descricaoLeiEstTarif/arquivoLeiEstTarif, descricaoLeiIndividualizacao/arquivoLeiIndividualizacao, descricaoNormaCO/arquivoNormaCO, descricaoNormaCM/arquivoNormaCM | texto e PDF (bytea) do decreto tarifário, lei de estrutura tarifária, lei de individualização, normas de corte e de medição exibidos na "loja virtual" | d | `reg.general_conditions_act` e `bill.tariff_readjust` guardam número, data e URL; anexos via `documents`, nunca bytea |

### 4.3 Competência, ciclo e conta

| Propriedade GSAN | Governa | Cl. | PDT / Ziip |
|---|---|---|---|
| anoMesFaturamento, anoMesArrecadacao | competência corrente de faturamento e de arrecadação (uma por instalação) | — | não param.: competência por grupo de faturamento em `billing_cycles` (P2.5) |
| indicadorFaturamentoAntecipado | permite faturar antes da leitura | b | novo (`bill.allow_advance_billing`) |
| indicadorAtualizacaoTarifaria, anoMesAtualizacaoTarifaria | reajuste tarifário em curso e competência | d | `bill.tariff_readjust` |
| indicadorTarifaCategoria | tarifa por categoria ou por subcategoria | d | `tariff_versions` já modela por categoria; novo se subcategoria |
| indicadorNaoMedidoTarifa, codigoTipoCalculoNaoMedido | regra de cálculo do não medido | d | `meter.after_cap_rule` cobre parte; novo |
| valorMinimoEmissaoConta | valor abaixo do qual a conta não é emitida (acumula) | d | novo (`bill.min_amount_to_issue`) |
| numeroMinimoDiasEmissaoVencimento, numeroDiasAdicionaisCorreios | antecedência entre emissão e vencimento, mais dias de correio | c | `bill.min_days_delivery_to_due` |
| numeroMesesValidadeConta | meses em que a conta pode ser paga sem reemissão | d | novo |
| indicadorCalculaVencimento, diasVencimentoAlternativo, ultimoDiaVencimentoAlternativo | cálculo do vencimento e lista de dias alternativos | c | `bill.due_date_options` |
| numeroMesesMinimoAlteracaoVencimento, numeroDiasAlteracaoVencimentoPosterior, indicadorLimiteAlteracaoVencimento, numeroLimiteAlteracaoVencimento | frequência e limite de troca do dia de vencimento | d | novo (`bill.due_day_change_*`) |
| numeroDiasVencimentoCobranca | prazo de vencimento de documento de cobrança | d | `collection_policies` |
| qtdeDiasEnvioEmailConta | antecedência do envio da conta por e-mail | d | novo |
| indicadorBloqueioContaMobile | impede emissão da conta no coletor | b | ADR-0031 (emissão em campo): novo |
| indicadorEmissaoExtratoNaConsulta, indicadorCobrarTaxaExtrato, numeroDiasValidadeExtrato, numeroDiasValidadeExtratoPermissaoEspecial, valorExtratoFichaComp | extrato de débitos: emissão, cobrança e validade | d | novo (`doc.statement_*`) |
| valorSegundaVia, indicadorImprimeUsuarioSegundaVia | preço e identificação na 2ª via | d | portal: 2ª via gratuita (ADR-0022); preço só presencial, novo |
| valorContaFichaComp, valorGuiaFichaComp, valorDemonstrativoParcelamentoFichaComp | tarifa da ficha de compensação por documento | d | `pay.document_kind_by_debt`; preço novo |
| indicadorGeracaoBoletoBB | gera boleto no leiaute de um banco | d | `pay.channels` |
| mensagemContaBraile | texto fixo da conta em braile | d | novo (`doc.braille_notice`) |
| descricaoAliquotaImposto, valorAliquotaImposto | alíquota de imposto impressa na conta | a | motor fiscal (ADR-0003); não param. do tenant |
| numeroMesesReinicioSitEspFaturamento | meses até reiniciar situação especial de faturamento | d | novo (CAD-56) |
| indicadorRetificacaoValorMenor, numeroMesesRetificarConta, indicadorNormaRetificacao, qtdMaxContasRetificadas, contaMotivoRetificacaoPagamentoAntecipado | regras de retificação de conta | d | novo (`bill.rectify_*`); retificação é a única correção (PDT §1.7) |
| indicadorExibeMensagemNaoReceberPagamento | mensagem "não receber" em conta substituída | d | novo |
| numeroVezesSuspendeLeitura, numeroMesesLeituraSuspensa | suspensão de leitura após N ocorrências por M meses | d | `meter.*`: novo |
| indicadorDocumentoValido | aceita documento vencido no pagamento | d | `pay.*`: novo |

### 4.4 Medição, consumo e rota

| Propriedade GSAN | Governa | Cl. | PDT / Ziip |
|---|---|---|---|
| mesesMediaConsumo, numeroMesesMaximoCalculoMedia, percentualMediaIndice, numeroDiasVariacaoConsumo | meses da média, teto, índice e variação admitida | d | `meter.average_months`, `meter.max_estimated_cycles` |
| indicadorFaixaFalsa, percentualFaixaFalsa, indicadorUsoFaixaFalsa | leitura esperada falsa (controle do leiturista) e percentual de imóveis | d | `ExpectedRangePolicy` (metering); percentual novo |
| indicadorUsoFiscalizadorLeitura, percentualFiscalizacaoLeitura, indicadorPercentualFiscalizacaoLeitura | fiscalização de leitura por amostra | d | novo (`reading.inspection_sample_pct`) |
| incrementoMaximoConsumoRateio, decrementoMaximoConsumoRateio, percentualToleranciaRateio | limites do rateio de condomínio | d | P2.2 do `01`: novo |
| hidrometroCapacidade | capacidade padrão sugerida no cadastro de hidrômetro | d | não param.: default de formulário |
| indicadorRoteiroEmpresa, indicadorUsaRota | leitura por roteiro de empresa terceirizada ou por rota | d | P2.7 (leitura terceirizada): novo |
| versaoCelular, numeroDiasBloqueioCelular | versão mínima do app de campo e dias para bloquear coletor sem transmissão | b | ADR-0030 (dispositivo registrado, bloqueio remoto) |
| menorConsumoGrandeUsuario, menorEconomiasGrandeUsuario, unidadeOrganizacionalTramiteGrandeConsumidor | limiares de "grande consumidor" e unidade que trata | d | perfil do imóvel (P1.4 do `01`): novo |
| indicadorSupressao | liga a regra de supressão a pedido sem débito/permissão (CAD-32) | b | novo (`cut.suppression_requires_settlement`) |
| numeroMesesPesquisaImoveisRamaisSuprimidos | janela de pesquisa de ramais suprimidos para fiscalização | d | novo |

### 4.5 Cadastro de imóvel e cliente

| Propriedade GSAN | Governa | Cl. | PDT / Ziip |
|---|---|---|---|
| indicadorQuadraFace | usa face de quadra (CAD-02) | d | `registry.use_block_face` |
| numeroDigitosQuadra | largura do número da quadra na inscrição | d | novo (`registry.block_digits`); formato da inscrição em `Inscription.java` |
| indicadorAlteracaoInscricaoImovel | habilita a alteração de inscrição com autorização (CAD-11) | d | novo |
| numeroModuloDigitoVerificador | módulo do dígito verificador da matrícula | a | `Matricula.java` fixa módulo 11; manter travado |
| indicadorDuplicidadeCliente | checa duplicidade de documento ao incluir cliente | a | travado: unicidade por hash sempre (`V3_1`) |
| indicadorDocumentoObrigatorio, indicadorDocumentoObrigatorioManterCliente | documento obrigatório na inclusão e na manutenção | b | `registry.require_user_document`; manutenção: novo |
| indicadorConsultaDocumentoReceita | consulta o documento na Receita | d | novo (`registry.validate_document_online`), com base legal |
| indicadorNomeMenorDez, indicadorNomeClienteGenerico | bloqueia nome curto e nome genérico (lista) | d | novo (`registry.min_name_length`, `registry.generic_names`) |
| indicadorUsoNMCliReceitaFantasia | nome na conta: Receita ou fantasia | d | novo (`registry.use_trade_name_on_bill`) |
| indicadorVariaHierarquiaUnidade | unidade organizacional varia por hierarquia (RA/OS) | d | não param. (estrutura de unidades do tenant) |
| indicadorPopupAtualizacaoCadastral | mostra aviso de recadastramento pendente | d | não param. (UI) |
| clienteFicticioParaAssociarOsPagamentosNaoIdentificados | cliente "fictício" dos pagamentos não identificados | — | GSAN; não absorver (pagamento sem titular fica em `unidentified_payments`) |

### 4.6 Tarifa social e programas especiais

| Propriedade GSAN | Governa | Cl. | PDT / Ziip |
|---|---|---|---|
| areaMaximaTarifaSocial, consumoEnergiaMaximoTarifaSocial | critérios locais de elegibilidade (área, kWh) | d | `social.local_categories`; critérios adicionais: novo, só ampliam (Lei 14.898 é o piso) |
| percentualBonusSocial | desconto social | c | `social.variable_bands`, `social.fixed_part_discount_pct` |
| valorSalarioMinimo | salário mínimo de referência | a | referência nacional do produto (`PDT` §7.4) |
| clienteResponsavelProgramaEspecial, perfilProgramaEspecial | cliente pagador e perfil dos imóveis em programa especial | d | `special_programs` (P2.1 do `01`) |

### 4.7 Cobrança, parcelamento, negativação e certidões

| Propriedade GSAN | Governa | Cl. | PDT / Ziip |
|---|---|---|---|
| numeroMaximoParcelasFinanciamento, numeroMaximoParcelaCredito, percentualTaxaJurosFinanciamento, percentualMaximoAbatimento, percentualFinanciamentoEntradaMinima, numeroDiasVencimentoEntradaParcelamento, indicadorParcelamentoConfirmado, indicadorTabelaPrice, numeroMaximoParcelasContratosParcelamento, indicadorContaParcelada | regras de parcelamento | d | P4.1 (R8): novo (`inst.*`); juros do parcelamento ≠ `fee.*` |
| indicadorBloqueioContasContratoParcelDebitos, indicadorBloqueioContasContratoParcelManterConta, indicadorBloqueioDebitoACobrarContratoParcelDebito, indicadorBloqueioDebitoACobrarContratoParcelManterDebito, indicadorBloqueioGuiasOuAcresContratoParcelDebito, indicadorBloqueioGuiasOuAcresContratoParcelManterConta | bloqueios de edição de contas, débitos e guias que estão em contrato de parcelamento | b | novo (`inst.lock_*`) |
| numeroDiasCalculoAcrescimos, numeroMesesCalculoCorrecao, numeroMaximoMesesSancoes | carência de acréscimos, meses de correção, meses de sanção | d | `fee.grace_business_days`, `fee.correction_*`; sanções: novo |
| codigoPeriodicidadeNegativacao, numeroExecucaoResumoNegativacao, clienteResponsavelNegativacao | periodicidade e execução da negativação | d | ED-30: novo; `ret.negative_listing_max_years` |
| indicadorCobrancaJudical, indicadorDividaAtiva | módulos de cobrança judicial e dívida ativa ligados | d | ED-31: flag de produto, não param. |
| indicadorTransferenciaComDebito | permite transferir imóvel/titular com débito | d | novo (`registry.allow_transfer_with_debt`) |
| indicadorCertidaoNegativaEfeitoPositivo, indicadorDebitoACobrarValidoCertidaoNegativa, numeroDiasVencimentoDebitoGeracaoCertidaoNegativaDebitos, numeroAnoQuitacao, numeroMesesAnterioresParaDeclaracaoQuitacao | certidão negativa e declaração de quitação anual (Lei 12.007/2009) | c | novo (`doc.settlement_statement_*`); quitação anual é LEGAL |
| indicadorValorMovimentoArrecadador, dataHoraDadosDiariosArrecadacao, dataHoraResumoAcoesCobranca, cdDadosDiarios | controle de execução de resumos diários | — | não param.: estado de job (outbox, ADR-0014) |
| indicadorControlaAutoInfracao, numeroDiasPrazoRecursoAutoInfracao, numeroMaximoParcelasAutoInfracao, numeroMaximoMesesCalculoConsumoAutoInfracao | auto de infração: controle, prazo de recurso, parcelas, meses de consumo | d | novo (`infraction.*`); prazo de recurso: ERI |
| percentualConvergenciaRepavimentacao | percentual de convergência de repavimentação na OS | d | ED-36: novo |

### 4.8 Atendimento e ordem de serviço

| Propriedade GSAN | Governa | Cl. | PDT / Ziip |
|---|---|---|---|
| indicadorSugestaoTramite, indicadorControleTramitacaoRA, indicadorCalculoPrevisaoRADiasUteis, ultimoRAManual | tramitação do RA e previsão em dias úteis | d | `sac.response_days`; demais: novo (P3.4) |
| diasReativacao, diasMaximoAlterarOS, numeroDiasEncerramentoOrdemServico, numeroDiasEncerramentoOSSeletiva, indicadorValidarLocalizacaoEncerramentoOS | prazos de reativação, alteração e encerramento automático de OS; exige localização no encerramento | d | ED-36 (`so.*`): novo; `rec.sla_hours` cobre religação |
| qtdeDiasEncerraOSFiscalizacao, qtdeDiasValidadeOSFiscalizacao, qtdeDiasValidadeOSAnormalidadeFiscalizacao, numeroDiasEncerrarOsFiscalizacaoDecursoPrazo | validade e encerramento por decurso de prazo de OS de fiscalização | d | novo (`so.inspection_*`) |
| numeroDiasRevisaoComPermEspecial | dias de conta em revisão permitidos com permissão | d | novo |
| numeroMaximoFavorito, indicadorAutorizacaoRelatorio | favoritos do menu; autorização para relatórios | — | não param. (UI; permissão por papel) |

### 4.9 Segurança, agenda e infraestrutura

| Propriedade GSAN | Governa | Cl. | PDT / Ziip |
|---|---|---|---|
| numeroDiasExpiracaoAcesso, numeroDiasMensagemExpiracao, numeroMaximoLoginFalho, indicadorLoginUnico, indicadorControleExpiracaoSenhaPorGrupo, indicadorControleBloqueioSenhaAnterior, indicadorSenhaForte | política de senha e sessão | b | realm do Keycloak (`PDT` §4: não é parâmetro do tenant); `sec.mfa_roles` |
| ipServidorSmtp, ipServidorModuloGerencial, ipServidorModuloOperacional | endereços de serviços | — | não param. (infra; ADR-0033) |
| horaInicioProcesso, intervaloHorasProcesso | agenda do batch | — | não param. (worker/outbox) |
| loginSenhaCdl | credencial de birô de crédito em texto (CAD-64) | — | nunca; cofre |

Totais: 205 parâmetros, dos quais cerca de 54 já têm chave ou tabela equivalente no `PDT` ou no código, cerca de 106 são candidatos a chave nova (quase todas classe d, com 9 em b, 6 em c e 3 em a) e 45 não são parâmetro do tenant no Ziip SAN (estado de job, infraestrutura, UI, dado de cadastro ou particularidade do GSAN).

---

## 5. Melhores práticas

**Manter como conceito**

1. Catálogo de situação com flags de comportamento e histórico de transições; o faturamento lê flags, não ids (CAD-16, CAD-36). O Ziip SAN já faz isso em `V3_1` e `V5_1`.
2. Toda mudança de situação de ligação e de hidrômetro passa por OS tipada, com validação "exibir" (pode?) separada de "efetuar" (faz), e débito do serviço derivado do tipo de serviço (CAD-26, CAD-58). Dá rastreabilidade de quem, quando, por qual ordem.
3. Entidade ligação 1:1 com o imóvel por tipo, com datas por evento (implantação, ligação, corte, religação, supressão, restabelecimento), selos e lacre; esgoto acompanhando a água (CAD-28, CAD-42).
4. Histórico temporal de instalação de hidrômetro com leituras de instalação e retirada, usuário de cada ponta e destino do retirado (CAD-45 a CAD-47); exclusão temporal já presente em `V5_1`.
5. Aviso confirmável separado de erro bloqueante (economias acima do limite, sequência de rota ocupada); regras de domínio num serviço puro testável (`UnitConsistency` já segue isso).
6. Bloqueio "imóvel em campo" durante a leitura e durante o recadastramento (CAD-12, CAD-63): evita conflito entre o arquivo que está na rua e o cadastro.
7. Vínculo cliente-imóvel com vigência, papel e motivo de encerramento de catálogo; "quem era o usuário em X" (CAD-23, CAD-24).
8. Alteração de inscrição como processo autorizado e auditado, com aviso de impacto na rota e no grupo de faturamento (CAD-11).
9. Supressão a pedido condicionada a quitação e supressão de ofício condicionada a permissão (CAD-32): política explícita, não implícita.
10. Cadastro de hidrômetros em lote por faixa e movimentação com motivo e parecer (CAD-49, CAD-50).

**Não replicar**

1. Transições por id fixo espalhadas em métodos de 100 a 400 linhas num controlador de 12 mil linhas; regra duplicada em "exibir" e "efetuar".
2. Regras por empresa em condicionais (`codigoEmpresaFebraban`, sigla) e no validador de formulário (CAD-10, CAD-60).
3. Parâmetro único de mais de 200 colunas sem vigência, sem aprovação e sem distinção entre política, infraestrutura e credencial (CAD-64); competência corrente como coluna global.
4. Remover a entidade ligação ao mudar a situação (CAD-35) e apagar e reinserir contatos na atualização de cliente (CAD-19): perda de histórico.
5. Flags 1/2, linhas sentinela (id 0, cliente fictício), coordenadas em texto, foto e PDF em bytea.
6. Concorrência otimista manual por carimbo de tempo reimplementada em cada método (CAD-13).
7. Débito da OS gerado dentro do método de cada operação, repetido dezenas de vezes; no Ziip SAN é um efeito único do encerramento da OS.
8. Cobrar taxa de religação sem verificar o aviso prévio (o GSAN não tem a regra): no Ziip SAN é trava legal (G9).

---

## 6. Lacunas do Ziip SAN em ordem de impacto

| # | Lacuna | Regras | Tarefa do plano afetada | Observação |
|---|---|---|---|---|
| L1 | Ligação de água e de esgoto como entidade com transições por OS (origens permitidas, efeitos, datas, selos, lacre, esgoto acompanhando a água) | CAD-26 a CAD-35, CAD-40 a CAD-43 | P2.23 (`V3_7`, só a base), ED-13 (onda A), ED-29 | Sem isso o ciclo da P2.5 não sabe desde quando o imóvel está cortado nem o que faturar; a P3.4 bloqueia uma OS que não produz efeito na ligação |
| L2 | OS tipada: catálogo de tipos de serviço com operação, efeito, débito e exigência de RA; `service_orders.status` como máquina de estados | CAD-26, CAD-58 | P3.4, P3.6, ED-36 | `service_orders` de `V1_1` tem `service_code_id` sem FK e `status` livre |
| L3 | Hidrômetro ligado à OS: instalação/substituição/retirada com destino do retirado, troca de proteção e registro, lacre, remanejamento; cadastro em lote; movimentação de estoque | CAD-45 a CAD-52 | P2.5 (instalação mínima), ED-14, ED-58 | `V5_1` tem a base temporal certa; faltam os campos de evento e o vínculo com a OS |
| L4 | Parâmetros do cadastro: 13 chaves de `registry.*` ausentes (dígitos da quadra, alteração de inscrição, documento na manutenção, nome mínimo, nomes genéricos, nome fantasia, desempate de categoria, transferência com débito, supressão a pedido, lote automático) | CAD-02, CAD-09, CAD-14, CAD-21, CAD-32 | P1.3, P1.6 | `RegistrySettingsService` lê 4 chaves de `tenant_settings`; o `PDT` §6 pede envelope com vigência para parâmetros regulatórios, mas estes são operacionais (classe d) |
| L5 | Alteração de inscrição com autorização e histórico; bloqueio "em leitura" por rota e competência | CAD-11, CAD-12 | P1.3 (nova P1.3b), P2.5 | Impacta migração (P1.2) e recadastramento (ED-16) |
| L6 | Catálogos de motivo com papel de sistema: fim de relação, corte, supressão, baixa de hidrômetro | CAD-24, CAD-62 | P1.3, ED-16 | `end_reason` hoje é texto livre |
| L7 | Território: rota sem setor; rede da quadra sem "parcial"; sem lote automático; sem face; sem sistema/bacia de esgoto | CAD-02 a CAD-04, CAD-09 | P1.3, P1.26, P2.23 | Baixo esforço; alto efeito na consistência da inscrição |
| L8 | Permissões finas do domínio (override de bloqueio, forçar situação, suprimir, revelar vencimento) | CAD-07, CAD-25, CAD-32, CAD-35 | P1.3, P0.13 | `RegistryRoles` só tem papéis |
| L9 | Situação derivada do imóvel (matriz água × esgoto) e situações especiais de faturamento e cobrança com vigência | CAD-39, CAD-55, CAD-56 | P2.5, P4.1, ED-31 | Fora do cadastro; registrar aqui para não voltar a `consumer_units` |
| L10 | Exclusão do imóvel encerrando vínculos; rechecagem de documento na atualização do cliente; consulta "usuário em data X" | CAD-17, CAD-18, CAD-23 | P1.3 | Testes a acrescentar em `ConsumerUnitService` e `CustomerDocumentServiceTest` |

---

## 7. Perguntas ao dono do produto (só as que travam decisão)

| # | Pergunta | Alternativas |
|---|---|---|
| Q1 | Consumo mínimo fixado por ligação (CAD-37), vedado pela NR 13 em contratos novos | (a) nunca existe no Ziip SAN; (b) só em tenant com `reg.contract_pre_nr13 = true`, com aceite; (c) sempre disponível, com aceite formal |
| Q2 | Supressão a pedido do cliente com débito em aberto (CAD-32) | (a) bloquear, como o GSAN com o parâmetro ligado; (b) permitir só com permissão e justificativa; (c) permitir sempre, mantendo o débito em cobrança |
| Q3 | Mudança manual de situação de ligação fora de OS (CAD-35) | (a) proibida: toda transição nasce de OS; (b) permitida com permissão `connection.force-status` e justificativa; (c) permitida só em cadastro provisório e na importação (P1.2) |
| Q4 | Face de quadra (CAD-02) | (a) implementar `block_faces` no P1.3; (b) adiar à P2.23, onde o GIS passa a ser a fonte da rede por trecho; (c) descartar e remover a chave `registry.use_block_face` |
| Q5 | Desempate da categoria principal (CAD-14) | (a) menor código da categoria; (b) ordem explícita configurável por tenant; (c) categoria de maior tarifa |
| Q6 | Esgoto "ligado fora de uso" (CAD-42) | (a) estado automático quando a água é cortada ou suprimida, revertido ao religar; (b) só por OS de desativação, nunca automático; (c) não existe no Ziip SAN: o faturamento de esgoto segue a flag da situação de água |
| Q7 | Cobrança de serviços da OS (ligação nova, religação, troca de hidrômetro) (CAD-58) | (a) tabela de preços por tenant e débito gerado pela OS na próxima conta, como o GSAN; (b) só NFS-e de serviço avulso (P3.6) paga à parte; (c) ambos, por tipo de serviço |
| Q8 | Nível de corte (cavalete, ramal, supressão) e seu reflexo na situação (CAD-29, CAD-32) | (a) três situações distintas no catálogo; (b) uma situação CORTADO com atributo `cut_level` no evento; (c) decidir na ED-29 |

---

## 8. Limites deste levantamento

1. Não li os repositórios HBM das operações (`RepositorioLigacaoAguaHBM`, `RepositorioImovelHBM.atualizarImovelExecucaoOrdemServico*`): o efeito "CORTADO" do corte e a situação final da supressão parcial são inferidos pelos nomes e pelos estados aceitos nas operações seguintes.
2. Não li as JSPs de religação, restabelecimento, corte administrativo e movimentação de hidrômetro; as regras vêm dos controladores, que são a fonte de verdade das validações.
3. Não li `ControladorFaturamentoFINAL` nem `ControladorCobranca*`: a semântica de cada flag da situação (água ativa, consumo real, dias de corte) é inferência a confirmar no levantamento de faturamento.
4. `ImovelCobrancaSituacao`: li a entidade e o catálogo; a lógica de inclusão (telas `gui/cadastro/imovel/*ImovelSituacaoCobranca*`) não foi lida.
5. Sem DDL (repositório `gsan-migracoes`): unicidades e índices reais do banco do GSAN continuam desconhecidos, inclusive para a inscrição.
6. A classificação a/b/c/d dos 205 parâmetros é proposta de analista; pisos e tetos legais exigem a conferência do `PDT` §7 antes de virar `parameter_legal_limits`.
7. Linhas citadas são aproximadas (arquivos lidos por intervalo); valem como ponto de partida para quem for confirmar.
8. Licença do GSAN não verificada; este documento só contém conceitos e regras em palavras próprias.
