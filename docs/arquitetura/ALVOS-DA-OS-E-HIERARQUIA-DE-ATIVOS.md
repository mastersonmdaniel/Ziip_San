# Alvos da OS e hierarquia de ativos

Versão revista em 22/10/2026 com a leitura do anexo do MCPSB da ANA (confirmado) e da vigência da NBR 12208. Base: achado OPE-15 do levantamento [22b](../levantamento/lacunas/22b-operacao-campo-rede-inteligencia.md), ADR-0039
(unidade consumidora como raiz), catálogo do GIS ([GIS-CADASTRO-TECNICO §4](GIS-CADASTRO-TECNICO.md)) e pesquisa em
fontes técnicas de 22/10/2026 (seção 7). Plano v2.19. Nomes técnicos em inglês; rótulos em português.

## 1. Princípio

A OS passa a ter um ou mais **alvos**. O alvo é uma referência tipada (`target_type`, `target_id`) a qualquer coisa
sobre a qual se faz um serviço: a unidade consumidora (o caso da P1.31), uma feição do GIS, uma instalação
operacional, um equipamento ou um componente dele. Há duas fontes de verdade, sem duplicação:

- **Feição do GIS** (trecho de rede, válvula, hidrante, macromedidor, poço de visita, reservatório, elevatória como
  estrutura): o cadastro técnico do ADR-0034 continua dono da geometria e do catálogo de tipos.
- **Ativo** (`asset`): o cadastro patrimonial e de manutenção, dono dos equipamentos, dos componentes e das
  edificações que não têm geometria própria de rede (bomba, motor, painel, soprador, comporta, analisador, veículo). Um
  ativo pode ser filho de uma instalação, que por sua vez é uma estrutura do GIS.

O teste de arquitetura da P1.31 não muda: nenhuma escrita em cliente ou unidade consumidora fora do efeito de uma OS.

## 2. Hierarquia proposta

Inspirada na taxonomia de nove níveis da ISO 14224:2016 (tabela 2 da norma, conferida na pré-visualização oficial; a
norma é do setor de petróleo, gás e petroquímica e serve aqui como analogia técnica, colapsada em seis níveis) e na
estrutura de identificação dos bens do Manual de Controle Patrimonial da ANA (MCPSB, versão 2026, instituído pela NR
16/2026), que a ARSESP aprovou em 2022 (NT.F-0064-2022, em vigor desde 01/01/2024):

| Nível | Nome | Exemplos | Correspondência |
|---|---|---|---|
| 1 | Município e localidade | município atendido, distrito, bairro | MCPSB `LOC` (código do IBGE, 7 dígitos) |
| 2 | Sistema | abastecimento de água, esgotamento sanitário, bens de uso geral e controle de qualidade; GRS e drenagem como sistemas opcionais | primeiro nível de `NTS` do MCPSB (água, esgoto, administração) |
| 3 | Natureza e subdivisão do serviço | os 19 códigos do MCPSB: água (manancial, captação, adução, tratamento, reservação, adutoras, redes e ramais, ligações prediais); esgoto (ligações prediais, coleta, transporte, tratamento, disposição final); administração da operação, de obras e central; gestão comercial; gestão ambiental; reserva estratégica | MCPSB `NTS-SDS` (tabela 2); ARSESP `TS-ES` (nove etapas, subconjunto) |
| 4 | Instalação (unidade operacional) | poço, captação, ETA, UTS, elevatória, booster, reservatório, ETE | ISO 14224 níveis 3 a 5 |
| 5 | Equipamento (unidade de equipamento) | conjunto motobomba, motor, painel de comando, soprador, comporta | ISO 14224 nível 6; MCPSB `UAR` |
| 6 | Componente (item mantenível), opcional | selo, mancal, rolamento, sensor, motor dentro de um conjunto | ISO 14224 nível 8; a subunidade (nível 7, como o sistema de lubrificação) entra só como agrupamento opcional |

A peça (ISO 14224, nível 9, opcional na própria norma) não é ativo: é material consumido pela OS (almoxarifado, ED-58). A fronteira de cada
equipamento (o que pertence à bomba: motor, acoplamento, base, partida, instrumentação) é definida por classe, com
base na cláusula 8.1 e nos diagramas de fronteira do Anexo A da ISO 14224, que exigem fronteiras sem sobreposição entre classes, para que a mesma pergunta tenha sempre a mesma resposta.

## 3. Classes de alvo

| `target_type` | Fonte de verdade | Conteúdo |
|---|---|---|
| `CONSUMER_UNIT` | `consumer_units` | unidade consumidora; o alvo de hoje |
| `WATER_CONNECTION`, `SEWER_CONNECTION`, `METER` | ligação, GIS (ramal e cavalete) e hidrômetro | ligação de água e de esgoto, ramal, cavalete, hidrômetro |
| `GIS_LINEAR` | GIS (arco) | trechos de rede (seção 4.1) |
| `GIS_POINT` | GIS (nó) | válvulas, hidrantes, caixas, poços de visita, pontos de controle (seção 4.2) |
| `OPERATIONAL_SITE` | GIS (estrutura) | instalações operacionais (seção 4.3) |
| `EQUIPMENT` | `asset` | equipamentos e instrumentos (seção 4.4) |
| `COMPONENT` | `asset` | componente de um equipamento, opcional |
| `BUILDING` | `asset` | edificações e estruturas civis sem rede (seção 4.5) |
| `FLEET_ASSET` | `asset` | veículos e equipamentos móveis (seção 4.6) |
| `NETWORK_INCIDENT` | ocorrência de rede (ED-92) | rompimento, extravasamento, falta de água |
| `ZONE` | GIS (zona) | setor de abastecimento, DMC, bacia e sub-bacia de esgotamento |
| `LOCATION_ONLY` | endereço ou ponto | inspeção ou serviço sem ativo cadastrado |

Uma OS pode ter vários alvos (por exemplo, um reparo que fecha duas válvulas e repara um trecho). Cada alvo recebe
a sua marca de efeito aplicado.

## 4. Catálogo de alvos

Cada linha indica a fonte em colchetes: **G** = catálogo do GIS do projeto (GIS-CADASTRO-TECNICO §4); **A** =
unidade de adição e retirada (UAR) dos manuais de controle patrimonial da ANA (MCPSB) e da ARSESP, cujas listas de água e
esgoto conferem entre si; **S** = glossário do SINISA; **N** =
norma da ABNT (NBR); **I** = ISO 14224. Nomes de código são propostas; o catálogo é do tenant e extensível.

### 4.1 Rede linear (`GIS_LINEAR`)

Adutora de água bruta (`RAW_WATER_MAIN`) [A, S]; adutora de água tratada (`TREATED_WATER_MAIN`) [A, S]; subadutora
[G]; rede de distribuição (`DISTRIBUTION_MAIN`) [A, G]; linha de recalque de água e de esgoto (`FORCE_MAIN`) [A];
interligação (`INTERCONNECTION`) [A]; rede coletora de esgoto (`SEWER_MAIN`) [A, G]; coletor tronco (`TRUNK_SEWER`)
[A, S]; interceptor (`INTERCEPTOR`) [A, S]; emissário (`OUTFALL_MAIN`) [A, S]; lododuto de ETA (`SLUDGE_LINE`) [A];
extravasor e descarga (`OVERFLOW_LINE`, `DRAIN_LINE`) [A, G]; travessia aérea (`AERIAL_CROSSING`) [A]; canal, galeria
e túnel (`CHANNEL`, `GALLERY`, `TUNNEL`) [A]; rede de drenagem (`STORM_SEWER`) [A, G]; ramal predial de água e de
esgoto (`SERVICE_LINE`) [G]; ramal de hidrante [G]; sifão invertido (`INVERTED_SIPHON`) [G].

### 4.2 Pontos de rede (`GIS_POINT`)

- **Válvulas:** registro de gaveta, borboleta, esfera, retenção, redutora de pressão (VRP), sustentadora,
  redutora-sustentadora, controladora de vazão, quebra-pressão, ventosa, descarga, antigolpe ou de alívio, válvulas
  especiais [A, G]; registro de passeio [G]; dispositivo bipartido para bloqueio de
  tubulação [A].
- **Hidrantes:** de coluna, subterrâneo e com obturação própria [G].
- **Medição e controle:** macromedidor, ponto de controle de pressão, ponto de controle de qualidade, medidor de
  vazão, registrador de pressão e de vazão, pluviômetro, estação hidrometeorológica [G, A].
- **Esgoto:** poço de visita, tubo de inspeção e limpeza, terminal de limpeza, caixa de passagem, caixa de inspeção,
  caixa de ligação, caixa de descarga, câmara de sifão, ponto de lançamento [G].
- **Caixas e câmaras:** caixa de registro, abrigo e câmara de macromedidor, bloco de ancoragem, bloco de apoio, suporte
  de tubulação, proteção catódica [G].

### 4.3 Instalações operacionais (`OPERATIONAL_SITE`)

- **Produção de água:** captação superficial; captação flutuante; barragem [A]; poço tubular profundo; poço amazonas
  [A, G]; nascente [G]; ETA (convencional, compacta, filtração direta); unidade de tratamento simplificado, UTS [S]
  (o SINISA distingue ETA de UTS: esta só desinfeta e fluoreta, em geral com captação subterrânea).
- **Bombeamento de água:** estação elevatória de água, EEA [N: NBR 12214, G, A]; **booster** [G].
  Pela NBR 12208:2020, quando a pressão a montante é superior à atmosférica a elevatória passa a se chamar booster
  (estação impulsionadora); o catálogo guarda essa condição como atributo (sucção em pressão) e permite booster móvel
  (`MOBILE_BOOSTER`) [A].
- **Reservação:** reservatório apoiado, elevado, enterrado e semienterrado [N: NBR 12217, G]; reservatório
  hidropneumático; tanque de alimentação unidirecional (TAU); chaminé de equilíbrio [A].
- **Esgoto:** estação elevatória de esgoto, EEE [N: NBR 12208, G]; ETE (lodos ativados, UASB, lagoas, filtro
  biológico, outras) [N: NBR 12209 e 17076, G, S]; estação de pré-condicionamento e destino final (hierarquia de bens da minuta de inventário da ANA).
- **Subunidades de ETA** (como estruturas filhas da ETA) [A, N: NBR 12216]: calha Parshall, misturador rápido,
  floculador, decantador (convencional, laminar, compacto), filtro, tanque de contato, câmara de desinfecção, casa de
  química, silo de cal, sala de dosagem.
- **Subunidades de ETE** [A, N: NBR 12209]: poço ou canal de gradeamento, peneira, desarenador, calha Parshall,
  reator anaeróbio de fluxo ascendente (UASB), filtro aerado submerso, biodisco, lagoa, decantador, digestor,
  adensador, leito de secagem, escada de aeração, câmara de desinfecção, queimador de biogás.
- **Outras:** central de resíduos e de compostagem [A]; estrutura de infiltração e desidratação de lodo (bag de
  geotêxtil) [A].

### 4.4 Equipamentos e instrumentos (`EQUIPMENT`)

Classes agrupadas pelos grupos de bens (unidades patrimoniais, UP) do MCPSB e do manual da ARSESP, com a UAR citada
como tipo. Os grupos são citados pelo nome: a numeração das UP do MCPSB difere da da ARSESP.

| Classe | Tipos (UAR) |
|---|---|
| Bombeamento (equipamentos de bombeamento; conjuntos motobomba monobloco) | bomba centrífuga horizontal, vertical e radial; conjunto motobomba submersível, submersa e monobloco; bomba de deslocamento positivo, peristáltica, helicoidal, de pistão, hidráulica e pneumática; bomba de eixo flexível com motor a combustão; booster móvel; caixa e cabine para conjunto motobomba e booster; motor elétrico; redutor e variador de velocidade; bomba parafuso de Arquimedes; roda d'água |
| Elétrica (instalações e equipamentos elétricos; redes elétricas; infraestrutura fotovoltaica) | cabine de alimentação primária; transformador; grupo gerador e turbogerador; nobreak; banco de baterias e carregador; inversor de frequência, soft starter e servomotor; centro de controle de motores; painéis de comando de motores, de válvulas, de estação, de serviços auxiliares, de instrumentação e de telemetria; banco de capacitores; disjuntor e chave seccionadora; protetor de surtos; painel e inversor fotovoltaicos; retificador para proteção catódica; rede elétrica em postes; proteção contra descargas atmosféricas |
| Telecontrole e automação | controlador lógico programável (CLP); unidade remota e estação remota de telemetria; transmissores de nível, pressão, vazão, velocidade e temperatura; indicador e controlador de nível; controlador de VRP; registradores de pressão e de vazão; conversor serial/ethernet; armário de instrumentação |
| Tratamento (filtros e outros equipamentos de tratamento) | dosador de produtos químicos; clorador e hidroclorador; preparador de polieletrólito; bomba dosadora; aeradores (superficial, submersível e por injeção de ar); soprador de ar; misturador; grade mecanizada; peneira estática e rotativa; removedor de areia; ponte removedora de lodo e raspadores; rosca transportadora; filtro prensa e centrífuga de lodo; secador; adufa e comporta com atuador; analisadores on-line de cloro, pH, oxigênio dissolvido, fluoreto e H2S; medidor de vazão e de nível; detector de gás; lavador de gases; osmose reversa |
| Controle de perdas e metrologia | hidrômetro; macromedidor; bancada de aferição de hidrômetro; data logger com telemetria; correlacionador de ruído de vazamento; geofone eletrônico e mecânico; detector e localizador de tubo; detector de fraude em ligação e cavalete; maleta de calibração; controlador inteligente |
| Laboratório (aferição, medição e testes laboratoriais) | balança, pHmetro, turbidímetro, medidor de cloro e de oxigênio, jar-test, estufa, banho-maria, câmara incubadora, autoamostrador, analisador multiparâmetro, espectrofotômetro e cromatógrafo (ligados à ED-51 e à ISO/IEC 17025: calibração, padrão e incerteza) |
| Movimentação de carga (serviço de campo e de oficina) | ponte rolante, talha elétrica, pórtico móvel, monovia, plataforma elevatória (a NBR 12214 exige sistema de movimentação de equipamentos na elevatória) |

Equipamentos de oficina e de campo (grupo de serviço de campo e de oficina: analisadores de vibração, megôhmetros, câmeras de inspeção de esgoto,
bicos de desobstrução, máquinas de furar tubos sob pressão, compactadores, perfuradores de solo) entram como ativos
de apoio, usados pelas equipes; só viram alvo quando recebem manutenção ou calibração.

### 4.5 Edificações e estruturas (`BUILDING`)

Casa operacional, casa de bombas, casa de química, abrigo operacional e não operacional, cabine primária e de força,
subestação, laboratório, almoxarifado, prédio administrativo, base e bloco de concreto, muro e dique de contenção,
urbanização e benfeitorias, estufa agrícola, central de resíduos [A, G].

### 4.6 Frota e equipamentos móveis (`FLEET_ASSET`)

Caminhão, picape, utilitário, motocicleta, bicicleta de serviço [A]; equipamentos automotivos (retroescavadeira,
minirretroescavadeira, guindaste, empilhadeira, pá carregadeira, plataforma) [A]; equipamentos acoplados (**caminhão
limpa-fossa**, combinado vácuo-jato, sewer jet, vac-all, mini-jet, tanque de água, guincho, guindauto, oficina móvel,
poliguindaste, valetadeira, cesto aéreo) [A]; reboques e semirreboques; embarcação, balsa, lancha, draga [A];
caçamba, contêiner, tanque e reservatório móvel [A]; drone [A, telecomunicação]; grupo gerador e motobomba portáteis.

## 5. Atributos do ativo (compatíveis com o inventário regulatório)

O MCPSB da ANA (versão 2026) fixa a estrutura de codificação de cada bem, e o cadastro de ativo do Ziip SAN a adota:

| Campo | Sigla | Regra do MCPSB | Quem define |
|---|---|---|---|
| Código individual do bem | `CIB` | 25 dígitos; só para bem individual, não para bem controlado em massa | prestador |
| Localização (município) | `LOC` | 7 dígitos, código do IBGE | IBGE |
| Área constituída | `AC` | 6 dígitos; cadastros imobiliários adjacentes ligados a um endereço principal | prestador |
| Bem compartilhado | `BCP` | `S` ou `N`; usado em conjunto com outros municípios | prestador |
| Bem reversível | `BRV` | `S` ou `N` | prestador |
| Natureza e subdivisão do serviço | `NTS-SDS` | 19 códigos (seção 2, nível 3) | entidade reguladora |
| Unidade patrimonial | `UP` | 29 grupos de bens; no código de exemplo do manual aparece com 2 dígitos na tabela 4 e 3 dígitos na figura 2 | entidade reguladora |
| Unidade de adição e retirada | `UAR` | 7 dígitos; atribuídos pela entidade reguladora; espécie não prevista é demandada ao regulador | entidade reguladora |
| Atributos técnicos | `AT1` a `AT5` | 2 dígitos cada; `00` quando não se aplica | entidade reguladora |

O manual também define o controle por massa ou individual e a unidade de medida de cada UAR, e uma **tabela única nacional de vida útil e taxa de depreciação regulatória** por UAR, de uso obrigatório, com possibilidade de exceção tecnicamente comprovada aceita pela entidade reguladora (Voto 14/2026 da ANA). Os valores que li na tabela, por grupo de bens, foram: estruturas de saneamento 50 anos, canais, galerias e túneis 60, equipamentos de bombeamento 20, poços 20, ligações domiciliares 50, instalações e equipamentos elétricos 15, telecontrole e automação 8, filtros e outros equipamentos de tratamento 10 e hidrômetros 7. A leitura foi automática e esses números devem ser conferidos na tabela original antes de virarem dado do produto.

Além do código, o cadastro guarda a ficha técnica (fabricante, modelo, número de série, ano de fabricação, potência, capacidade, dimensões), a data de entrada em operação, o estado de conservação, a localização georreferenciada, a instalação-pai, os documentos (manual, projeto, nota de aquisição) e o custo histórico, que são os campos pedidos pela minuta de inventário da ANA e pelo manual da ARSESP.

**NR 16/2026 da ANA** (Resolução ANA 302/2026, em vigor desde 31/08/2026): institui o Manual de Contabilidade Regulatória (MCRSB) e o MCPSB. As entidades reguladoras têm até três anos para incorporar as diretrizes, os prestadores até 2032, e a comprovação de adoção exige plano de contas, modelos de demonstração, inventário conforme o MCPSB e as vidas úteis do MCPSB. O catálogo de ativos do Ziip SAN guarda a correspondência de cada tipo com o par UP e UAR do MCPSB e a vida útil por perfil de agência (ADR-0038). O indicador de envelhecimento da infraestrutura (IGA1) do manual da ARSESP usa os mesmos dados.

## 6. Regras de uso nas OS

- Cada tipo de serviço operacional declara quais classes de alvo aceita (`so.operational_types_enabled`): reparo de
  vazamento aceita trecho linear, válvula e ocorrência; manutenção de bomba aceita equipamento e componente.
- Serviço sobre ativo atualiza o histórico do ativo (horas, custo, material, falha) e, quando há plano preventivo,
  reprograma a próxima execução.
- Serviço sobre rede abre pacote de edição do GIS quando altera a rede (GIS-CADASTRO-TECNICO §11).
- Alvo `NETWORK_INCIDENT` liga a OS à ocorrência, que mede a intermitência e o extravasamento (NR 9 da ANA).
- Prazo por tipo de serviço: `so.deadline_hours_by_type`, nunca mais longo que o da agência.
## 7. Fontes consultadas em 22/10/2026

- ANA, Manual de Controle Patrimonial Aplicado ao Setor de Saneamento Básico (MCPSB), versão 2026, anexo da NR 16/2026 (363 páginas, lido), <https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Normativos-publicados-pela-ANA/mcpsb____pos_pfe_ana.pdf>.
- ANA, Resolução 302/2026 (NR 16/2026), <https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2026/302>; Voto 14/2026 (tabela única de vidas úteis), <https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Normativos-publicados-pela-ANA/sei_0233547_voto_cb__dlb__14.pdf>; minuta de instrução normativa de inventário (hierarquia de ativos), em participacao-social.ana.gov.br.
- ARSESP, NT.F-0064-2022, Manual de Controle Patrimonial do Setor de Saneamento Básico, versão 2022 (29 unidades patrimoniais, 385 páginas, lido), <http://www.arsesp.sp.gov.br/ConsultasPublicasBiblioteca/NT-F-0064-2022%20Saneamento.pdf>; a ARSESP aprovou a versão 2 (2025), com classe e tipo de aquisição (Deliberação 1.636/2025), não lida.
- ISO 14224:2016, pré-visualização oficial (tabela 2, taxonomia de nove níveis; cláusula 8.1, fronteira; Anexo A, diagramas), <https://www.iso.org/standard/64076.html>.
- ABNT NBR 12208:2020 e NBR 12214:2020 (elevatórias): situação em buscanormas.com.br, normas.com.br e DIN Media (vigentes, confirmadas em 01/2025); definição de booster em trecho da NBR 12208.
- Ministério das Cidades, glossário de termos técnicos do SINISA (água e gestão municipal).
- Catálogo do GIS do projeto (GIS-CADASTRO-TECNICO §4).

## 8. Situação das conferências

- **Anexo do MCPSB da ANA: lido e confirmado.** A estrutura de codificação, as 19 naturezas e subdivisões do serviço e as listas de UAR de água e esgoto coincidem com as do manual da ARSESP; muda a numeração das UP e o MCPSB acrescenta os grupos de infraestrutura fotovoltaica, poços e módulos de armazenamento de líquidos, sólidos e gás. Os números de vida útil da seção 5 vieram de leitura automática e pedem conferência na tabela.
- **NBR 12208:2020: vigente.** Confirmação publicada em 01/2025 (buscanormas.com.br e normas.com.br) e situação corrente na DIN Media. O aviso de "cancelada" que aparece numa página do normas.com.br contradiz o histórico da própria página e provavelmente trata da edição de 1992 [INFERÊNCIA].
- **Textos pagos:** a ISO 14224 foi conferida na pré-visualização oficial, que cobre a taxonomia e a fronteira; as NBR 12214 e 12208 foram lidas apenas em resumos e trechos públicos, e o texto integral depende da compra prevista na curadoria (ED-76). A ISO 14224 não é norma de saneamento; a analogia é decisão de projeto.
- Os códigos de tipo em inglês são propostas, sujeitas ao catálogo do GIS.
