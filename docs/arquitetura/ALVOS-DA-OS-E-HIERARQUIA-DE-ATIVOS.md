# Alvos da OS e hierarquia de ativos

Base: achado OPE-15 do levantamento [22b](../levantamento/lacunas/22b-operacao-campo-rede-inteligencia.md), ADR-0039
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

Inspirada na taxonomia de nove níveis da ISO 14224 (que a própria norma diz não ser obrigatória em toda a
profundidade; a prática colapsa em quatro a seis níveis) e na estrutura de identificação dos bens da ARSESP (NT.F-0064-2022):

| Nível | Nome | Exemplos | Correspondência |
|---|---|---|---|
| 1 | Município e localidade | município atendido, distrito, bairro | ARSESP `MUN`; ANA, minuta de inventário: "município" |
| 2 | Sistema | abastecimento de água, esgotamento sanitário, bens de uso geral e controle de qualidade; GRS e drenagem como sistemas opcionais | minuta ANA: "sistema" |
| 3 | Etapa do serviço | água: manancial, captação, adução, tratamento, distribuição; esgoto: coleta, transporte, tratamento, disposição final | ARSESP `TS-ES` (nove etapas) |
| 4 | Instalação (unidade operacional) | poço, captação, ETA, UTS, elevatória, booster, reservatório, ETE | ISO 14224 níveis 3 a 5 |
| 5 | Equipamento (unidade de equipamento) | conjunto motobomba, motor, painel de comando, soprador, comporta | ISO 14224 nível 6; ARSESP `UAR` |
| 6 | Componente (item mantenível), opcional | selo, mancal, impelidor, rolamento, sensor | ISO 14224 nível 8; é onde a OS de manutenção costuma cair |

A peça (ISO 14224, nível 9) não é ativo: é material consumido pela OS (almoxarifado, ED-58). A fronteira de cada
equipamento (o que pertence à bomba: motor, acoplamento, base, partida, instrumentação) é definida por classe, com
base nos diagramas de fronteira da ISO 14224, para que a mesma pergunta tenha sempre a mesma resposta.

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
manual de controle patrimonial da ARSESP, unidade de adição e retirada (UAR); **S** = glossário do SINISA; **N** =
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

Classes agrupadas pela UP do manual da ARSESP, com a UAR citada como tipo.

| Classe | Tipos (UAR) |
|---|---|
| Bombeamento (UP 05, 29) | bomba centrífuga horizontal, vertical e radial; conjunto motobomba submersível, submersa e monobloco; bomba de deslocamento positivo, peristáltica, helicoidal, de pistão, hidráulica e pneumática; bomba de eixo flexível com motor a combustão; booster móvel; caixa e cabine para conjunto motobomba e booster; motor elétrico; redutor e variador de velocidade; bomba parafuso de Arquimedes; roda d'água |
| Elétrica (UP 06, 26) | cabine de alimentação primária; transformador; grupo gerador e turbogerador; nobreak; banco de baterias e carregador; inversor de frequência, soft starter e servomotor; centro de controle de motores; painéis de comando de motores, de válvulas, de estação, de serviços auxiliares, de instrumentação e de telemetria; banco de capacitores; disjuntor e chave seccionadora; protetor de surtos; painel e inversor fotovoltaicos; retificador para proteção catódica; rede elétrica em postes; proteção contra descargas atmosféricas |
| Telecontrole e automação (UP 03) | controlador lógico programável (CLP); unidade remota e estação remota de telemetria; transmissores de nível, pressão, vazão, velocidade e temperatura; indicador e controlador de nível; controlador de VRP; registradores de pressão e de vazão; conversor serial/ethernet; armário de instrumentação |
| Tratamento (UP 09) | dosador de produtos químicos; clorador e hidroclorador; preparador de polieletrólito; bomba dosadora; aeradores (superficial, submersível e por injeção de ar); soprador de ar; misturador; grade mecanizada; peneira estática e rotativa; removedor de areia; ponte removedora de lodo e raspadores; rosca transportadora; filtro prensa e centrífuga de lodo; secador; adufa e comporta com atuador; analisadores on-line de cloro, pH, oxigênio dissolvido, fluoreto e H2S; medidor de vazão e de nível; detector de gás; lavador de gases; osmose reversa |
| Controle de perdas e metrologia (UP 30, 10, 12) | hidrômetro; macromedidor; bancada de aferição de hidrômetro; data logger com telemetria; correlacionador de ruído de vazamento; geofone eletrônico e mecânico; detector e localizador de tubo; detector de fraude em ligação e cavalete; maleta de calibração; controlador inteligente |
| Laboratório (UP 12) | balança, pHmetro, turbidímetro, medidor de cloro e de oxigênio, jar-test, estufa, banho-maria, câmara incubadora, autoamostrador, analisador multiparâmetro, espectrofotômetro e cromatógrafo (ligados à ED-51 e à ISO/IEC 17025: calibração, padrão e incerteza) |
| Movimentação de carga (UP 14) | ponte rolante, talha elétrica, pórtico móvel, monovia, plataforma elevatória (a NBR 12214 exige sistema de movimentação de equipamentos na elevatória) |

Equipamentos de oficina e de campo (UP 14: analisadores de vibração, megôhmetros, câmeras de inspeção de esgoto,
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
caçamba, contêiner, tanque e reservatório móvel [A]; drone [A, UP 13]; grupo gerador e motobomba portáteis.

## 5. Atributos do ativo (compatíveis com o inventário regulatório)

O cadastro de ativo guarda, no mínimo: identificador individual (plaqueta, tombamento), município, sistema, etapa do
serviço, instalação-pai, indicador de bem compartilhado entre sistemas, indicador de bem reversível, classe e tipo (UP
e UAR), ficha técnica (fabricante, modelo, número de série, ano, potência, capacidade, dimensões), data de entrada em
operação, estado de conservação, vida útil, localização georreferenciada, documentos (manual, projeto, nota de
aquisição) e custo histórico. São os campos pedidos pela minuta de inventário da ANA e pelo manual da ARSESP.

A NR 16/2026 da ANA (Resolução ANA 302/2026, em vigor desde 31/08/2026) institui o Manual de Contabilidade
Regulatória (MCRSB) e o Manual de Controle Patrimonial (MCPSB). As entidades reguladoras têm três anos para
incorporar as diretrizes e os prestadores, até 2032, e a comprovação de adoção exige o plano de contas, os modelos de
demonstração, o inventário conforme o MCPSB e as vidas úteis do MCPSB. O catálogo de ativos do Ziip SAN guarda, por
tipo, a correspondência com a classificação do MCPSB (pendente de leitura do anexo, seção 8) e a vida útil por perfil
de agência (ADR-0038). O indicador de envelhecimento da infraestrutura (IGA1) do manual da ARSESP usa os mesmos dados.

## 6. Regras de uso nas OS

- Cada tipo de serviço operacional declara quais classes de alvo aceita (`so.operational_types_enabled`): reparo de
  vazamento aceita trecho linear, válvula e ocorrência; manutenção de bomba aceita equipamento e componente.
- Serviço sobre ativo atualiza o histórico do ativo (horas, custo, material, falha) e, quando há plano preventivo,
  reprograma a próxima execução.
- Serviço sobre rede abre pacote de edição do GIS quando altera a rede (GIS-CADASTRO-TECNICO §11).
- Alvo `NETWORK_INCIDENT` liga a OS à ocorrência, que mede a intermitência e o extravasamento (NR 9 da ANA).
- Prazo por tipo de serviço: `so.deadline_hours_by_type`, nunca mais longo que o da agência.

## 7. Fontes consultadas em 22/10/2026

- ARSESP, NT.F-0064-2022, Manual de Controle Patrimonial do Setor de Saneamento Básico, versão 2022 (29 unidades de
  propriedade e unidades de adição e retirada), <http://www.arsesp.sp.gov.br/ConsultasPublicasBiblioteca/NT-F-0064-2022%20Saneamento.pdf>.
- ANA, Resolução 302/2026 (NR 16/2026), <https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2026/302>,
  e minuta de instrução normativa de inventário (hierarquia de ativos), em participacao-social.ana.gov.br.
- ISO 14224, taxonomia de nove níveis (resumos públicos; o texto da norma não foi lido).
- ABNT NBR 12214:2020 e 12208:2020 (elevatórias), por resumos do portal normas.com.br; definição de booster na NBR
  12208.
- Ministério das Cidades, glossário de termos técnicos do SINISA (água e gestão municipal).
- Catálogo do GIS do projeto (GIS-CADASTRO-TECNICO §4).

## 8. Pontos a confirmar

- O anexo do MCPSB da ANA (hierarquia, classes e vidas úteis) não foi lido: o arquivo publicado com a resolução traz só
  o texto da norma. A classificação da ARSESP é a mais completa lida; a do MCPSB pode diferir [INFERÊNCIA].
- A NBR 12208:2020 aparece no portal normas.com.br com a marca de cancelada; confirmar a edição vigente.
- Os textos da ISO 14224 e das NBR não foram lidos integralmente (pagos).
- Os códigos de tipo em inglês são propostas, sujeitas ao catálogo do GIS.
