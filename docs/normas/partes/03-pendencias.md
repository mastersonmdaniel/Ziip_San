# Pendências da biblioteca de normas técnicas (ABNT, ISO, IEC, OGC)

Complemento de `03-tecnicas-abnt-iso.json`. Reúne o que não foi localizado, o que apresenta divergência entre fontes, o que foi deixado fora do corte e as limitações de verificação. Nenhuma data deste documento é prazo de projeto.

## 1. Não localizadas

- ABNT NBR 12215-2 (projeto de adutora de água, parte 2): ausente das listagens do CB-177 em normas.com.br e no Busca Normas. A ficha da NBR 12215:1991 (cancelada) aponta apenas a NBR 12215-1 como sucessora. Sem item.
- Projeto ABNT NBR 17078 (poços de visita e de inspeção em material plástico): esteve em consulta nacional em 2022 e não consta como norma publicada no Busca Normas. Sem item; verificar no catálogo ABNT se foi publicada com outro número.
- Norma ABNT específica sobre lodo de esgoto ou de ETA: não existe NBR dedicada. O tema é tratado na NBR 12209 (fase sólida da ETE), na série NBR 10004 a 10007 (classificação, ensaios e amostragem) e em normas de aterro; o uso agrícola é regulado por resolução do CONAMA, fora do escopo ABNT. Foram registradas as normas internacionais do ISO/TC 275 como referência.
- ISO 46002: não existe no catálogo do ISO/TC 224. As buscas retornaram apenas a EN 46002:1996, de dispositivos médicos, sem relação com o tema.
- Adoção brasileira (NBR ISO) da ISO 46001, 24513, 24516 (partes 1 a 4), 24518, 24523, 24536, 24521, 24525, 31800 e das normas do ISO/TC 275: não localizada na listagem do comitê ABNT/CEE-166 nem no Busca Normas.
- Adoção brasileira das edições ISO de 2024 da série 24510, 24511 e 24512: figura em consulta nacional (fim de 01/2026) sem publicação confirmada. As NBR ISO de 2012 constam canceladas sem substituição desde 01/2026 e estão registradas como CANCELADA.
- Adoção ABNT da ISO/IEC 25010 e das demais partes do SQuaRE (25012, 25040): não localizada na lista do comitê ABNT/CB-021. Constam apenas 25030:2008 e 25062:2011 (vigentes) e 25000 e 25001 (canceladas).
- Adoção ABNT de ISO/IEC 27000, 27006-1, 27019, 27033-1, 27034-1, 27040, 27400 e 27402; de ISO 8601, 4217, 20022 e ISO/IEC 20922 (MQTT); e das IEC 62443 (partes 2-1, 2-4, 3-2, 3-3, 4-1, 4-2), IEC 62541 e IEC 60870-5-104: não localizadas. Existe apenas a ABNT IEC/TS 62443-1-1:2023.
- ABNT NBR ISO 19011:2026 (adoção da ISO 19011:2026): não localizada. O catálogo oficial ainda lista a NBR ISO 19011:2018; o item de 2018 permanece como VIGENTE com nota.
- ET-ADGV, ET-PCDG e ET-RDG (CONCAR/DSG): citadas apenas em resultados de busca e ausentes da página oficial da INDE. Foi incluída a ET-EDGV 3.0 e o Perfil MGB 2.0. Só foi encontrada a ET-ADGV/SPU 4.0, adaptação institucional da SPU, que não foi incluída.
- Resolução CONCAR de homologação do Perfil MGB 2.0 e a relação formal com o Perfil MGB 1.0 (2009): não localizadas.
- Números pedidos que existem com assunto diferente do suposto (não são normas de saneamento): NBR 13210 (reservatório de PRFV), NBR 16097, 16656, 16723, 16919, 15805, 13224 e 15099. Foram descartados; reavaliar se o escopo do produto incluir reservatórios.

## 2. Divergências entre fontes, a conferir no catálogo oficial da ABNT

- NBR 10160: normas.com.br (página individual) registra nova edição de 08/2026; Busca Normas e buscadorsinapi ainda exibem a edição de 09/2005, confirmada em 01/2022. O item adota a edição 2026 (VIGENTE, SECONDARY_SOURCE).
- NBR 7675: normas.com.br lista nova edição de 08/2026; o Busca Normas ainda mostra a de 2022. Item com edição 2026.
- NBR 7362: Busca Normas exibe o rótulo "Cancelada" para o número e normas.com.br lista a edição de 04/2025 como vigente. Adotada a segunda leitura; as partes antigas (7362-1 a 7362-4) constam como SUBSTITUIDA.
- NBR ISO 16739-1 (IFC): normas.com.br mostra edição 08/2026 vigente; Busca Normas mostra registro cancelado, publicação 12/2023. Item A_CONFIRMAR.
- NBR 12595: a ficha informa cancelamento com substituição em 08/2022 sem nomear a sucessora. A NBR 17015 está registrada como sucessora presumível, por coincidência de data e de tema com as NBR 12266 e 9822.
- NBR 15950 (edição 2011): última confirmação registrada em 12/2019; conferir se houve confirmação posterior, revisão ou cancelamento.
- NBR 13133: normas.com.br indica edição 08/2021 e o Busca Normas indica versão corrigida de 03/2025. Registrada a edição 2021 com a versão corrigida em notes.
- NBR ISO/IEC 27002 (ano da edição anterior): um trecho de PDF cita 2012 e o Busca Normas registra 11/2013. Provável erro de grafia; não resolvido.
- NBR ISO/IEC 20000-1:2020: o Busca Normas registra emenda de 06/2024 sem número; não foi possível ligá-la à ISO/IEC 20000-1:2018/Amd 1:2024.
- Anos-base das adoções ABNT de NBR ISO 10002, 10003, 10004, 22301, 37001, 37301 e das NBR ISO/IEC-IEEE 12207 e 29119-1: a correspondência com a edição ISO de origem foi inferida pelas datas. Conferir antes de citar.
- Títulos de edições antigas ABNT (NBR ISO/IEC 27001:2013, 27002:2013, 27005:2019, 27701:2019): o Busca Normas exibe apenas o título da edição vigente; os demais seguem o histórico conhecido da série.
- ABNT NBR ISO 9001:2026 e ISO 14001:2026: o catálogo oficial confirma título e existência; as datas de edição (13/10/2026 e 07/2026) vêm de fontes secundárias. A página oficial da NBR ISO 9001:2015 registra cancelamento com revisão em 13/10/2026.
- ABNT NBR ISO 37002: o Busca Normas atribui à norma um comitê incorreto; o comitê real não foi confirmado. A NBR ISO 37000 foi deixada fora do corte por relevância.
- OGC WMS 1.3.0 (2006), WMTS 1.0.0 (2010) e GML 3.3 (2012): a página do OGC não exibe a data de adoção; o ano vem do identificador do documento. Está sinalizado em notes.
- OIML R 49: a página da OIML lista a recomendação de 2024 como ativa; a divisão em partes vem de resultado de busca. Não foi criado item por parte.
- ISO 19650 (partes 1 a 3), ISO 19115-1, ISO 4185, 45001, 22301, 31000, 37120, 46001, 55002, 24513 e 24516-1/-2: no estágio 90.92 da ISO ("a ser revisada"). Foram registradas como EM_REVISAO, mas a edição publicada permanece em vigor até a nova edição.
- ISO 14224: o escopo original é petróleo, petroquímica e gás natural; o uso em saneamento é por analogia (taxonomia de falhas e de manutenção). A NBR ISO 14224:2024 pode ter base ISO mais recente que a edição 2016.
- Modbus Serial Line Protocol e Modbus Security Protocol: não incluídos por falta de versão confirmada em fonte aberta.
- Esquemas de certificação da série IEC 62443 (IECEE e ISASecure): só foi confirmado o programa para as partes 2-1, 2-4 e 4-1; para 3-2, 3-3 e 4-2 o campo certifiable ficou NA, sem verificação.

## 3. Regime metrológico do Inmetro

A Portaria Inmetro/ME 155/2022 (regulamento técnico metrológico de medidores de água potável, situação "Revisto" no portal do Inmetro) foi mencionada apenas em notes dos itens de hidrômetro. A biblioteca de legislação trata de portarias; não foi criado item de portaria nesta parte.

## 4. Existem e foram deixadas fora do corte por baixa aderência ao produto

- Instalações e hidráulica predial periférica: NBR 15704-1, 15705, 15423, 15857, 14788, 15055, 15117 (registros, válvulas e descargas).
- Tubos e componentes: cerâmicos (NBR 5645, 14208), fibrocimento (NBR 8056, 8057), conexões de cobre (NBR 11720), anéis antigos (NBR 7672, 7673) e normas canceladas sem sucessora de aço, ferro dúctil e registros de ramal (NBR 9914, 9915, 10286, 10515, 10629, 11782, 11822, 12780, 13061).
- Ensaios individuais do CB-177 de ramais prediais e de estanqueidade (NBR 15182, 15183, 16153) e revestimentos de poço (NBR 13604 a 13609).
- Tratamento de esgoto e resíduos periféricos: NBR 11885, 13059, 13160 (grades), 13894 (landfarming), 10157, 13896, 8419, 15849 (aterros), 9896 (glossário), NBR 14283, 15495, 15515-2/-3, 16209, 16210, 16784-1, 16901 e 15492/15515-1/16434 (áreas contaminadas).
- Desenho técnico e documentação: NBR 16861, 17006, 17067, 17068, 8402, 8403, 10067, 13142, 8196, 10126, 8404, 12298, NBR 6492 e a série NBR 15965 (classificação da informação da construção), NBR 13752 e 16217, NBR 17082, 15309.
- Geoespacial e BIM: ISO 19105, 19110, 19131, 19148, 19162, 19125-2, 19152-3/-5, 19650-4/-5/-6, CityGML 3.0.
- Segurança da informação e TI periféricas: ISO/IEC 27003, 27004, 27007, 27014, 27031, 27033-1, 27034-1, 27036 (partes 1 a 3), 27037, 27400, 27402, 27555 a 27562, 29101; SQuaRE 25002, 25019 a 25024, 25030, 25041, 25051, 25052, 25059; ISO/IEC/IEEE 29119 partes 2 a 5, 15288, 29148, 32675, 16085, 90003; ISO/IEC 20000 partes 2, 3, 5, 6, 10, 11, 15; ISO/IEC 38501 a 38507 (exceto 38500); computação em nuvem (22123, 17788, 17789, 19086, 19941, 22624, 23613, 3445, 5140); IA periférica (23053, 42005, 42006, 5338, 5339, 5259, 24027, 24028, 24368, 8183); ISO 3166-1, ISO/IEC 30141, 20924, ISO 20022-2, ISO/IEC 17000; IEC 60870-5-7 e IEC 62541 partes 3, 5, 6 e 7.
- Gestão: ISO 14040, 14044, 26000, 37000, 10001, 10003, ISO/TS 18671, ISO 21500, 21502, 9004, 45002, 37004, 37005, 55012.
- Relevância revista caso o escopo mude: a retirada do corte não indica que a norma seja inaplicável, apenas que o produto não depende dela nesta fase.

## 5. Limitações de verificação

- O catálogo oficial da ABNT (abntcatalogo.com.br) não permite consulta direta (link expira e a busca exige postback). Itens ABNT estão como SECONDARY_SOURCE (normas.com.br e buscanormas.com.br), com exceção de algumas páginas de detalhe do catálogo que abriram (por exemplo NBR ISO/IEC 27002:2022 e NBR ISO 9001:2015) e de itens ISO.
- Páginas individuais do normas.com.br passaram a responder HTTP 403 durante a coleta; os históricos foram obtidos em buscanormas.com.br. As páginas do iso.org e do iec.ch foram lidas com cabeçalho de navegador.
- O bloco "Normas recomendadas" das páginas do normas.com.br traz texto padronizado de cancelamento que contradiz o rótulo de situação da própria norma; esse texto foi desconsiderado.
- A situação de cada item reflete as fontes consultadas no momento da coleta. Antes de uso contratual ou de certificação, conferir a edição vigente no catálogo da ABNT, da ISO ou da IEC.
