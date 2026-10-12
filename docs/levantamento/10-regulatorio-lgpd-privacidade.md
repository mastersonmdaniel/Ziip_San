# Levantamento 10 — Regulatório: LGPD, privacidade e proteção de dados (2015–2026)

Data da pesquisa: 12/10/2026. Escopo: o que a lei e a regulação exigem de um SaaS de saneamento que trata dados pessoais de usuários, e o que o SIGSAN já tem para atender.

> **Aviso.** Isto **não é parecer jurídico**. Reúne normas lidas em fonte primária e a leitura do código. Toda conclusão de enquadramento (quem é controlador, qual base legal, se um dado é sensível, se um prazo é em dias corridos) exige revisão por advogado de proteção de dados e por quem conhece o regulador de cada cliente antes de virar regra do produto. Onde não consegui confirmar, está escrito **NÃO CONFIRMADO**.

**Método.** Os textos de lei foram baixados do Planalto (versão compilada) e as resoluções da ANPD do DOU/gov.br, e lidos no texto. Artigos citados entre aspas ou parafraseados vêm desses textos. Onde só achei notícia, blog ou página-resumo, marquei **fonte secundária** ou confiança média/baixa. Código verificado em `G:\Meu Drive\PROJETOS\Ziip SAN\sigsan`.

**Legenda de prioridade:** P0 = obrigatório e, sem ele, não se deve operar com dado real; P1 = obrigatório ou de alto risco, necessário antes do primeiro cliente em produção; P2 = depois.

---

## 0. Resumo executivo

1. **A LGPD mudou em 2026.** Última alteração: **Lei 15.352, de 25/02/2026**, que transformou a ANPD em *Agência* Nacional de Proteção de Dados (agência reguladora, Lei 13.848/2019) e mexeu em art. 5º VIII, art. 55-A e art. 55-C. Nenhuma obrigação material do controlador mudou. O texto compilado do Planalto lista 4 leis que alteraram a LGPD: 13.853/2019, 14.010/2020, 14.460/2022 e 15.352/2026.
2. **Prazos que o software precisa carregar:** resposta ao titular de **15 dias** (art. 19 II; o código cita "art. 18" por engano), **comunicação de incidente em 3 dias úteis** à ANPD e ao titular (Res. CD/ANPD 15/2024, arts. 6º e 9º), registro de incidente por **5 anos** (art. 10).
3. **O SIGSAN tem estrutura LGPD mas quase nenhum controle real.** Existem 4 tabelas e 4 engines que o próprio código declara honestos (não apagam, não coletam dado de outros módulos). Não há API REST, não há teste (cobertura zero, diário §11.1), não há registro de operações (art. 37), não há fluxo de incidente.
4. **A criptografia AES-256-GCM por tenant não existe em código.** É uma decisão do diário (§3). `shared/sigsan-security` e `shared/sigsan-events` são **diretórios vazios** e fora do reactor; o Vault do compose é modo dev com token fixo. O cadastro comercial planejado (CPF, RG, nome da mãe, renda, NIS) **não pode entrar em produção** antes disso.
5. **`audit_log` é um vazamento estrutural de dado pessoal.** O trigger de auditoria serializa a linha inteira (`to_jsonb(NEW)`) em `old_values/new_values`; `audit_log` é só-inserção para a aplicação. Resultado: qualquer CPF, e-mail, IP e `report_pdf` entram em claro num lugar que o motor de eliminação/anonimização não alcança.
6. **Tarifa social virou dever legal com prazo: Lei 14.898/2024 + NR ANA 13/2025; adequação até 11/12/2026** (derivado: vigência 180 dias após 14/06/2024, mais 24 meses). O fluxo de dados do CadÚnico passa pela **entidade reguladora infranacional (ERI)**, não vai direto ao prestador privado. Isso muda o desenho do módulo e da base legal.
7. **Negativação é o ponto de maior risco jurídico** na cobrança: notificação prévia é do órgão mantenedor do cadastro (Súmula 359), mas o prestador entrega o endereço (Súmula 404) e responde pela baixa em **5 dias úteis** após o pagamento (Súmula 548). Em 2026 o STJ (Tema 1.315) validou a notificação eletrônica **com prova de entrega**.
8. **Operador x controlador:** o Ziip SAN é **operador** dos dados dos usuários da concessionária (art. 5º VII e art. 39), mas vira **controlador** (art. 42 §1º I) se usar esses dados para fins próprios, como treinar modelos do módulo `intelligence` ou fazer benchmark entre tenants. O contrato precisa travar isso.

---

## (a) Quadro de normas

Colunas: Norma | Esfera | Data / última alteração | Vigente? | Fonte | Confiança.

### A. LGPD e alterações

| Norma | Esfera | Data / última alteração | Vigente? | Fonte (URL) | Confiança |
|---|---|---|---|---|---|
| Lei 13.709/2018 (LGPD) | Federal | 14/08/2018. Última alteração: Lei 15.352/2026. Sanções (arts. 52-54) em vigor desde **01/08/2021** (art. 65, I-A). Vigência geral: o texto compilado diz "24 meses após a publicação"; o marco **18/09/2020** é o corrente, mas **NÃO CONFIRMADO em fonte primária** (a Lei 14.058/2020, que se costuma citar, não menciona a LGPD) | Sim | https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/L13709compilado.htm | Alta (texto); média (data de vigência geral) |
| Lei 13.853/2019 (conversão da MP 869/2018) | Federal | 08/07/2019. Criou a ANPD (arts. 55-A a 55-L, 58-A, 58-B), art. 7º VIII, art. 18 V e §6º, art. 20, art. 23 III, art. 26 §1º IV-V, art. 27 p.ú. | Sim | https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2019/lei/L13853.htm | Alta |
| Lei 14.010/2020, art. 20 | Federal | 10/06/2020. Inseriu o art. 65, I-A (sanções só em 01/08/2021) | Sim (efeito exaurido) | https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2020/lei/L14010.htm | Alta |
| Lei 14.460/2022 (conversão da MP 1.124/2022) | Federal | 25/10/2022. ANPD vira autarquia de natureza especial; revogou o art. 55-B | Sim | https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2022/lei/L14460.htm | Alta |
| **Lei 15.352/2026** (conversão da MP 1.317/2025) | Federal | **25/02/2026**. ANPD = Agência Nacional de Proteção de Dados (agência reguladora). Altera art. 5º VIII e XIX, art. 55-A, art. 55-C; cria carreira de regulação; ajusta a vigência da Lei 15.211/2025 | Sim | https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2026/lei/L15352.htm | Alta |
| Decreto 12.881/2026 | Federal | 18/03/2026. Estrutura regimental da ANPD; **revoga o Decreto 10.474/2020**. Vigor 21 dias após a publicação (≈08/04/2026, cálculo meu) | Sim | https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2026/decreto/d12881.htm | Alta |
| Lei 15.211/2025 (ECA Digital) | Federal | 17/09/2025. Vigência fixada em **17/03/2026** pela Lei 15.352/2026 (art. 41-A). ANPD fiscaliza. Aplica-se a produto ou serviço de TI "direcionado a crianças e adolescentes" ou de "acesso provável" por eles (art. 1º) | Sim. **Aplicabilidade ao SIGSAN: provavelmente nenhuma** (portal de conta de água não é direcionado nem de acesso provável por menores; critérios do art. 1º p.ú. são cumulativos). Confirmar com o jurídico | https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2025/lei/L15211.htm | Alta (texto); média (aplicabilidade) |

### B. ANPD (regulação e orientação)

Lista oficial consultada: https://www.gov.br/anpd/pt-br/acesso-a-informacao/institucional/atos-normativos/regulamentacoes_anpd (a página lista atos até a Res. 32/2026; **não confirmei nenhum ato posterior**).

| Norma | Esfera | Data / última alteração | Vigente? | Fonte (URL) | Confiança |
|---|---|---|---|---|---|
| Res. CD/ANPD 1/2021 (fiscalização e processo sancionador) | Agência | 28/10/2021; retificada pela Res. 4/2023. **Em revisão: Consulta Pública ANPD 1/2026**, contribuições de 09/09 a 26/10/2026 | Sim | https://www.gov.br/anpd/pt-br/acesso-a-informacao/institucional/atos-normativos/regulamentacoes_anpd/resolucao-cd-anpd-no1-2021 | Alta (ato); média (consulta, fonte: nota da ANPD) |
| Res. CD/ANPD 2/2022 (agentes de tratamento de pequeno porte, ATPP) | Agência | 27/01/2022; art. 14 II alterado pela Res. 15/2024. Dispensa encarregado (art. 11), prazo em dobro, registro simplificado. **Não vale** para quem faz tratamento de alto risco (art. 3º I, art. 4º) nem para pessoa jurídica de direito público (art. 2º I só lista entes privados) | Sim | https://www.gov.br/anpd/pt-br/acesso-a-informacao/institucional/atos-normativos/regulamentacoes_anpd/resolucao-cd-anpd-no-2-de-27-de-janeiro-de-2022 | Alta |
| Res. CD/ANPD 4/2023 (dosimetria e sanções) | Agência | 24/02/2023. Infração leve, média ou grave (art. 8º); critérios do art. 7º | Sim | https://www.in.gov.br/en/web/dou/-/resolucao-cd/anpd-n-4-de-24-de-fevereiro-de-2023-466146077 | Alta |
| **Res. CD/ANPD 15/2024 (comunicação de incidente)** | Agência | 24/04/2024. Prazo **3 dias úteis** (arts. 6º e 9º), registro por **5 anos** (art. 10) | Sim | https://www.in.gov.br/en/web/dou/-/resolucao-cd/anpd-n-15-de-24-de-abril-de-2024-556243024 | Alta |
| Res. CD/ANPD 18/2024 (encarregado) | Agência | 16/07/2024 | Sim | https://www.in.gov.br/en/web/dou/-/resolucao-cd/anpd-n-18-de-16-de-julho-de-2024-572632074 | Alta |
| Res. CD/ANPD 19/2024 (transferência internacional e cláusulas-padrão) | Agência | 23/08/2024; retificação de 18/08/2025 segundo a página da ANPD. Prazo de 12 meses para incorporar as cláusulas-padrão (art. 2º p.ú.) | Sim | https://www.gov.br/anpd/pt-br/acesso-a-informacao/institucional/atos-normativos/regulamentacoes_anpd/resolucao-cd-anpd-no-19-de-23-de-agosto-de-2024 | Alta |
| Res. CD/ANPD 32/2026 (adequação da União Europeia) | Agência | 26/01/2026 (DOU 27/01/2026). Reconhece UE e países do EEE como adequados (art. 33 I) | Sim | https://www.in.gov.br/web/dou/-/resolucao-n-32-de-26-de-janeiro-de-2026-683334547 | Alta |
| Enunciado CD/ANPD 1/2023 (crianças e adolescentes) | Agência | 22/05/2023. Dados de crianças e adolescentes podem usar hipóteses do art. 7º ou do art. 11, desde que prevaleça o melhor interesse (art. 14) | Sim | https://www.in.gov.br/en/web/dou/-/enunciado-cd/anpd-n-1-de-22-de-maio-de-2023-485306934 | Alta |
| Res. 23/2024, 30/2025, 31/2025 (agenda regulatória 2025-2026; mapa de temas 2026-2027) | Agência | 09/12/2024; 23/12/2025; 22/12/2025 | Sim | página de regulamentações acima | Alta (existência); o conteúdo da agenda **não li** |
| Guia: Agentes de tratamento e encarregado (v. 2.0, 2022, retificada) | Agência (orientação, não vinculante) | Abr/2022 | Sim | https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes/Segunda_Versao_do_Guia_de_Agentes_de_Tratamento_retificada.pdf | Média (li só resumo) |
| Guia: Tratamento de dados pelo Poder Público (v. 2.0, jun/2023) | Agência (orientação) | Jun/2023 | Sim | https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes/guia-poder-publico-anpd-versao-final.pdf | Média (li só resumo) |
| **Regulamento de direitos dos titulares** (arts. 9, 18, 19, 20) | Agência | **Não editado**. Na agenda; fontes secundárias dizem que aguardava deliberação para consulta pública | **Não existe** | (secundária) https://www.dataprivacybr.org/ja-ajustou-sua-agenda-com-a-da-anpd/ | Baixa (status) |
| **Regulamento de RIPD** (art. 38) | Agência | **Não editado**. Em elaboração. Em set/2026 o Conselho Diretor não aprovou o Guia de Alto Risco (fonte secundária) | **Não existe** | (secundária) mesma fonte | Baixa (status) |

### C. Internet, logs e conexão

| Norma | Esfera | Data / última alteração | Vigente? | Fonte (URL) | Confiança |
|---|---|---|---|---|---|
| Lei 12.965/2014 (Marco Civil da Internet) | Federal | 23/04/2014. Art. 13: registros de conexão, **1 ano**; art. 15: registros de acesso a aplicações, **6 meses**, para provedor de aplicações constituído como pessoa jurídica que exerça a atividade "de forma organizada, profissionalmente e com fins econômicos"; art. 14: provedor de conexão não guarda registros de aplicação; art. 16 II ajustado pela LGPD | Sim | https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2014/lei/l12965.htm | Alta |
| Decreto 8.771/2016 | Federal | 11/05/2016. **Alterado pelo Decreto 12.975/2026** (20/05/2026, DOU 21/05/2026, vigor 60 dias depois, ≈20/07/2026). Art. 13: controle estrito de acesso, autenticação forte, **inventário de acessos aos registros**, cifra; §2º: reter o mínimo e excluir quando cumprida a finalidade ou o prazo legal. **Novo art. 15-A:** guarda também da **porta lógica de origem** | Sim. Há projetos de decreto legislativo para sustar o Decreto 12.975 (fonte secundária; sem andamento confirmado) | https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2016/decreto/d8771.htm e https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2026/decreto/d12975.htm | Alta (texto); baixa (PDLs) |

### D. Crédito, cobrança e negativação

| Norma | Esfera | Data / última alteração | Vigente? | Fonte (URL) | Confiança |
|---|---|---|---|---|---|
| Lei 12.414/2011 (cadastro positivo) | Federal | 09/06/2011; alterada pela **LC 166/2019**. Art. 2º IV: "fonte" inclui expressamente "prestadores de serviços continuados de água, esgoto, eletricidade, gás, telecomunicações". Art. 14: informação de adimplemento por no máximo **15 anos** | Sim | https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2011/lei/l12414.htm | Alta |
| LC 166/2019 | Federal | 08/04/2019. Cadastro passa a ser aberto sem autorização prévia (opt-out); comunicação ao cadastrado em até 30 dias (art. 4º §4º); correção em até 10 dias (art. 5º III) | Sim | https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp166.htm | Alta |
| Decreto 9.936/2019 | Federal | 24/07/2019. Regulamenta a Lei 12.414: conjunto de dados financeiros e de pagamentos (art. 4º), leiaute de envio entre fonte e gestor (art. 17) | Sim | https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2019/decreto/D9936.htm | Alta |
| CDC (Lei 8.078/1990), arts. 42, 42-A e 43 | Federal | Art. 43 §1º: informação negativa por **no máximo 5 anos**; §2º: abertura de cadastro comunicada por escrito; §3º: correção; §5º: prescrita a dívida, não se informa | Sim | https://www.planalto.gov.br/ccivil_03/leis/l8078compilado.htm | Alta |
| Súmula STJ 359 | Judicial | Cabe ao órgão mantenedor do cadastro notificar o devedor antes da inscrição | Sim | https://processo.stj.jus.br/SCON/sumstj/toc.jsp?sumula=359.num. | Média (li via resumo de busca) |
| Súmula STJ 404 | Judicial | Dispensável o aviso de recebimento (AR); basta prova da postagem ao endereço fornecido pelo credor | Sim | https://processo.stj.jus.br/SCON/sumstj/toc.jsp?sumula=404.num. | Média |
| Súmula STJ 548 | Judicial | Credor exclui o registro em **5 dias úteis** após o pagamento integral e efetivo | Sim | https://scon.stj.jus.br/SCON/sumulas/doc.jsp?livre=@num='548' | Média |
| Súmula STJ 323 | Judicial | Inscrição mantida até **5 anos**, independentemente da prescrição da execução | Sim | https://www.stj.jus.br/docs_internet/revista/eletronica/stj-revista-sumulas-2011_26_capSumula323.pdf | Média |
| STJ, Tema 1.315 (Segunda Seção, repetitivo, publicação 11/03/2026) | Judicial | Comunicação eletrônica de **abertura não solicitada de cadastro** (art. 43 §2º CDC) é válida se houver prova de **envio e de entrega efetiva** ao e-mail ou telefone fornecidos pelo consumidor; não exige prova de leitura. **Não trata da Súmula 359**; o texto formal da tese não consta da página | Sim | https://www.stj.jus.br/sites/portalp/Paginas/Comunicacao/Noticias/2026/11032026-Repetitivo-STJ-valida-notificacao-eletronica-a-consumidor-sobre-abertura-de-cadastro-nao-solicitado.aspx | Média |
| STJ, REsp 2.070.073 (3ª Turma, notícia de 22/09/2023) | Judicial | Notificação prévia à negativação **só por e-mail** não bastava. Tensão com o Tema 1.315 e com o Informativo 837/2024 (fonte secundária). **Verificar a jurisprudência vigente antes de fixar o canal** | Em disputa | https://www.stj.jus.br/sites/portalp/Paginas/Comunicacao/Noticias/2023/22092023-Notificacao-exclusiva-por-e-mail-nao-autoriza-inscricao-em-cadastro-de-inadimplentes.aspx | Média |

### E. Poder público, transparência e identificação

| Norma | Esfera | Data / última alteração | Vigente? | Fonte (URL) | Confiança |
|---|---|---|---|---|---|
| Lei 12.527/2011 (LAI) | Federal, aplica-se a União, Estados, DF e Municípios | 18/11/2011. Art. 1º p.ú. II: autarquias, empresas públicas, sociedades de economia mista e entidades controladas por qualquer ente. Art. 11: prazo de 20 dias + 10 de prorrogação. Art. 31: informação pessoal com acesso restrito por até 100 anos. Alterada por Lei 15.141/2025 (arts. 8-A e 8-B, sem efeito para o SIGSAN) | Sim | https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2011/lei/l12527.htm | Alta |
| Decreto 7.724/2012 | **Federal apenas** (Poder Executivo da União) | 16/05/2012; art. 58 alterado pelo Decreto 11.527/2023. Estados e municípios têm norma própria | Sim | https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2012/decreto/d7724.htm | Alta |
| Lei 13.460/2017 e Lei 14.534/2023 | Federal | 14.534/2023 (11/01/2023) deu nova redação ao art. 10-A §1º da Lei 13.460: cadastros e formulários de serviço público, **inclusive "serviços públicos delegados"** (caput), devem ter campo de **CPF obrigatório**, "suficiente para sua identificação, vedada a exigência de qualquer outro número" | Sim | https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2023/lei/L14534.htm e https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2017/lei/l13460.htm | Alta |
| Guia de Transparência e Proteção de Dados Pessoais (ANPD + CGU) | Orientação | Consulta pública aberta em jun/2026; **não é norma** | Em elaboração | https://www.gov.br/anpd/pt-br/assuntos/noticias/anpd-e-cgu-abrem-consulta-publica-para-o-guia-de-transparencia-e-protecao-de-dados-pessoais | Média |

### F. Saneamento × dados pessoais

| Norma | Esfera | Data / última alteração | Vigente? | Fonte (URL) | Confiança |
|---|---|---|---|---|---|
| **Lei 14.898/2024 (Tarifa Social de Água e Esgoto)** | Federal | 13/06/2024; vigor 180 dias após a publicação (≈11/12/2024, cálculo meu). Elegibilidade: CadÚnico com renda per capita até 1/2 salário mínimo, ou BPC (arts. 2º). **Classificação automática pelo prestador** com base no CadÚnico e nos bancos que ele já usa, "sem necessidade de prévia comunicação ao usuário" (art. 4º §4º). Relatório ao menos **anual** à ERI (art. 4º §1º). Perda do benefício só após aviso na fatura por **3 meses** (arts. 2º §2º e 3º p.ú.). Contratos sem categoria social se adaptam em até **24 meses** (art. 7º §2º) | Sim | https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2024/lei/L14898.htm | Alta |
| **NR ANA 13/2025 (Res. ANA 271, de 21/11/2025)** | Agência (ANA) | Estrutura tarifária e tarifa social. Arts. 30 a 40: a **ERI** obtém os dados do CadÚnico e do BPC, repassa ao prestador "o conjunto mínimo de informações" (art. 30 §4º), exige sigilo conforme a LGPD e um responsável pela guarda (art. 38 §2º), acesso restrito à concessão do benefício (art. 40); registros do CadÚnico com até 2 anos (art. 31); suspensão com contraditório (art. 34). Adequação dos prestadores até **11/12/2026** (guia da ANA) | Sim | https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2025/271 | Média: li por resumo automático de página truncada; **conferir os números dos artigos no DOU (seção 1, 24/11/2025 ou 02/12/2025)** |
| Decreto 11.016/2022 (CadÚnico) | Federal | 29/03/2022. Art. 13: dados de identificação das famílias são sigilosos, uso só para gestão de políticas públicas e pesquisa; "mínimo necessário"; cessão a terceiros "na forma estabelecida em ato do Ministro". **Texto compilado do Planalto pode estar desatualizado** (ministério e portarias mudaram) | Presumivelmente | https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2022/decreto/D11016.htm | Média |
| Informe nº 57/2024 do Cadastro Único e Portaria MC 810/2022 | Federal | **Não li.** Citados pela página de perguntas da ANA: o Cadastro Único não repassa dados pessoais direto a prestadoras privadas ou de direito privado; para prestador de direito público (autarquia) a cessão pode ser direta | **NÃO CONFIRMADO** | https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/tarifa-social-de-agua-e-esgoto/perguntas-frequentes | Baixa |
| Lei 11.445/2007, art. 53 (SINISA) | Federal | Informações do SINISA são "públicas, gratuitas" e publicadas "em formato de dados abertos" (§1º, redação da Lei 14.026/2020). Logo, **nada de dado pessoal** no que o prestador envia para publicação | Sim | https://www.planalto.gov.br/ccivil_03/_ato2007-2010/2007/lei/l11445.htm | Alta |
| STJ, REsp 1.532.514/SP (Súmula 412) | Judicial | Repetição de indébito de tarifa de água e esgoto: prazo do Código Civil, **10 anos** (CC/2002) ou 20 (CC/1916, com a transição do art. 2.028). Útil como motivo de **retenção** do histórico de cobrança | Sim | https://www.stj.jus.br/sites/portalp/Paginas/Comunicacao/Noticias-antigas/2017/2017-05-25_09-29_Definidos-prazos-prescricionais-para-restituicao-de-tarifas-de-agua-e-esgoto-cobradas-indevidamente.aspx | Média |

### G. Verificado e **sem efeito direto** (para não reabrir a dúvida)

| Item | Conclusão |
|---|---|
| Decreto 10.046/2019 (governança de compartilhamento na administração federal; ADI 6649) | Citado pelo Decreto 11.016, mas é federal e sob controle de constitucionalidade; não li a situação atual. Sem impacto para o SIGSAN hoje |
| Prazo em dobro de ATPP para direitos do titular (Res. 2/2022, art. 14 I) | Remete a "regulamentação específica" que **não existe**; não construir parâmetro em cima disso |
| Prazos fiscais e contábeis de guarda, prazos trabalhistas (FGTS etc.) | **NÃO CONFIRMADOS neste relatório** (fora da minha área; ver relatórios fiscal e trabalhista, se existirem) |

---

## 1. Papéis e contrato: operador x controlador no modelo SaaS

### 1.1 Enquadramento (a confirmar com o jurídico)

| Dado ou atividade | Controlador | Operador | Base |
|---|---|---|---|
| Cadastro de clientes, leitura, fatura, cobrança, tarifa social, OS | A concessionária, SAAE ou companhia (decide finalidades) | Ziip SAN | LGPD art. 5º VI e VII; art. 39 (operador segue instruções) |
| Usuários e funcionários do prestador (`users`, GPS do leiturista) | A concessionária (empregador) | Ziip SAN | idem |
| Conta de administrador do tenant, contatos comerciais, faturamento do SaaS, telemetria de segurança da plataforma | **Ziip SAN** | n/a | controlador para a própria relação comercial |
| Treino de modelos em `intelligence/*`, benchmark entre tenants, estatística de produto | **Ziip SAN passa a ser controlador** se usar dado pessoal do tenant para fim próprio | n/a | art. 42 §1º I: operador que descumpre instrução lícita "equipara-se ao controlador" |
| Dados que o prestador envia a bancos, PSP, SPC/Serasa, ERI, ANA | Concessionária (controlador) para destinatário, que é outro controlador | Ziip só transmite | art. 5º XVI (uso compartilhado) |

**Responsabilidade.** O operador responde solidariamente quando descumpre a lei ou as instruções lícitas (art. 42 §1º I); há direito de regresso (art. 42 §4º). O operador deve manter registro das operações (art. 37), adotar segurança (art. 46) e, se quiser, indicar encarregado (facultativo, **Res. 18/2024, art. 6º**: conta como boa prática de governança).

**Incidente.** A Res. 15/2024 põe a obrigação de comunicar no **controlador**; o prazo de 3 dias úteis conta "do conhecimento pelo controlador" (art. 6º §1º). Logo o contrato deve obrigar o operador a avisar o controlador **antes** disso, com conteúdo suficiente para ele preencher os itens do art. 6º §2º e do art. 9º.

### 1.2 Cláusulas que o contrato de operador precisa ter (checklist)

Origem: LGPD arts. 37, 39, 42, 46, 48; Res. 15/2024; Res. 19/2024; Guia ANPD de agentes de tratamento (resumo, não li o PDF inteiro). A LGPD **não exige contrato por escrito**, mas o guia o trata como prática necessária.

| # | Cláusula | Observação |
|---|---|---|
| 1 | Objeto, duração, natureza e finalidade do tratamento; tipos de dado e categorias de titular | Anexar o inventário por módulo (seção 3) |
| 2 | Tratar somente conforme instruções documentadas do controlador (art. 39); vedar uso para fim próprio, **inclusive treino de IA e benchmark**, salvo dado anonimizado e autorizado | Ponto crítico para `intelligence/*` |
| 3 | Confidencialidade de quem acessa; acesso do suporte do Ziip por privilégio mínimo, com trilha | |
| 4 | Medidas de segurança (art. 46) em anexo: cifra, isolamento por tenant, backup, controle de acesso, registro de acesso | Só prometer o que existe: hoje a cifra de campo **não existe** |
| 5 | Sub-operadores: lista pública, autorização geral ou específica, aviso prévio de troca, mesmas obrigações em cascata | Incluir Vault/Keycloak/hospedagem/e-mail/SMS/WhatsApp/observabilidade |
| 6 | Assistência ao controlador: pedidos de titular (prazo interno de encaminhamento), RIPD, resposta à ANPD | Prazo interno é sugestão minha, não da lei |
| 7 | Incidente: aviso ao controlador em prazo curto (sugestão: horas, não dias), conteúdo mínimo conforme Res. 15/2024 art. 6º §2º | O prazo legal do controlador é 3 dias úteis |
| 8 | Registro das operações de tratamento (art. 37) mantido pelo operador | |
| 9 | Transferência internacional: proibida sem autorização; se houver, cláusulas-padrão da Res. 19/2024 ou país adequado (UE: Res. 32/2026) | |
| 10 | Término: devolução (exportação em formato aberto) e **eliminação**, inclusive backups, com declaração; exceções do art. 16 | **Não há rotina de offboarding no código** |
| 11 | Auditoria e evidências; cooperação com a ANPD | |
| 12 | Responsabilidade solidária (art. 42 §1º) e regresso (§4º); limites de indenização a negociar | Matéria comercial |
| 13 | Para cliente **público**: o contrato administrativo (licitação) traz as cláusulas; comunicação à ANPD de contratos de compartilhamento com privados (art. 26 §2º) | Se contratar operador conta como "uso compartilhado" é pergunta aberta (seção e) |
| 14 | Encarregado do operador (facultativo) e canal de contato | Res. 18/2024, arts. 6º e 8º |

---

## 2. Dados do saneamento: classificação, base legal e proteção

A LGPD define dado sensível por **lista fechada** (art. 5º II): origem racial ou étnica, convicção religiosa, opinião política, filiação sindical ou a organização religiosa/filosófica/política, saúde, vida sexual, genético, biométrico. **Renda, NIS e CadÚnico não estão na lista.** Mas o art. 11 §1º estende o regime a "qualquer tratamento de dados pessoais que revele dados pessoais sensíveis e que possa causar dano ao titular". Pela Lei 14.898 (art. 2º II), a elegibilidade pode revelar **deficiência** (BPC) ou idade avançada, e o CadÚnico guarda composição familiar, o que pode incluir crianças. **NÃO CONFIRMADO** posicionamento da ANPD sobre renda como sensível. Recomendação de projeto: tratar o conjunto da tarifa social como **equivalente a sensível** (menor risco jurídico, custo baixo).

| Categoria / campo | Classificação | Base legal provável (revisar) | Proteção recomendada |
|---|---|---|---|
| Nome, endereço, e-mail, telefone do titular da ligação | Pessoal comum | art. 7º V (contrato), II (obrigação regulatória) | RLS (existe), RBAC por campo, máscara em tela e relatório; não cifrar coluna usada em busca, proteger por volume cifrado + backup cifrado |
| **CPF** (e CNPJ de MEI/empresário individual) | Pessoal; identificador | art. 7º II/V. **CPF é campo obrigatório** (Lei 13.460, art. 10-A §1º) | **Cifra de campo (AES-256-GCM) + índice cego (HMAC-SHA-256 com chave por tenant, nunca hash simples: o espaço do CPF é pequeno)**; máscara `***.***.123-45`; jamais em log nem URL |
| RG, órgão expedidor, data de emissão | Pessoal | sem finalidade clara; a lei **veda exigir outro número** além do CPF para identificar | **Não coletar por padrão.** Se um cliente exigir, opcional e cifrado |
| Data de nascimento | Pessoal | art. 7º V (homonímia, idoso, prioridade de atendimento) | Cifrar; derivar `is_elderly` quando só isso for necessário |
| **Nome da mãe**, sexo, profissão, ramo de atividade | Pessoal | Sem finalidade legal identificada; o GSAN usa para homonímia | **Decidir por finalidade; padrão: não coletar.** Se mantido para autenticação no atendimento, cifrar com índice cego |
| Perfil de consumo (leituras, volume, histórico) | Pessoal (revela hábitos e presença no imóvel) | art. 7º V | RBAC, retenção definida; **sem uso para IA própria do Ziip** |
| Dados financeiros (fatura, pagamentos, boleto, PIX) | Pessoal; Res. 15/2024 art. 5º III trata "dado financeiro" como critério de risco | art. 7º V e II | Não guardar PAN de cartão; cifrar chave Pix quando ligada a pessoa; segregar acesso |
| Geolocalização da ligação (`consumer_units.coordinates`) | **Pessoal quando ligada a pessoa** (Decreto 8.771, art. 14 I inclui "dados locacionais") | art. 7º V, II | Separar a camada GIS de rede (não pessoal) da camada comercial; a camada GIS **não deve trazer nome nem CPF**; atenção ao GeoServer/WMS |
| Fotos de campo (`documents`, `service_orders.photos`) | Pessoal se mostrarem pessoa, placa, fachada com identificação; **foto de rosto é biométrico só se usada para identificar** | art. 7º V, IX (com teste de legítimo interesse) | Remover EXIF/GPS no upload, retenção própria, acesso por OS |
| **Tarifa social: NIS, renda, BPC, composição familiar, cartão do programa** (relatório 01 §5.7 e §8.1) | Pessoal de alto risco; potencialmente sensível (art. 11 §1º) | art. 7º II e art. 11 II "a" (obrigação legal: **Lei 14.898, art. 4º**) | **Minimizar a "elegibilidade":** guardar `status`, `fonte` (CadÚnico/BPC/INSS), `valid_until`, `verified_at` e referência cifrada. **Não guardar lista de membros da família** nem valor de renda se a regra permitir. Cifra de campo + permissão própria `SOCIAL_TARIFF_VIEW` + evento de leitura auditado |
| Dados de funcionários e leituristas (`users`, `gps_track`, `assignee_id`) | Pessoal; rastreamento de localização | art. 7º V (contrato de trabalho), II; localização contínua exige teste de legítimo interesse (art. 7º IX) e aviso | Retenção curta para trilha de GPS; acesso só à chefia; aviso ao empregado |
| Crianças e adolescentes | Não há coleta prevista; `moradores` é contagem | Enunciado 1/2023 (art. 7º ou 11, melhor interesse) | Não criar campo identificando menores; na tarifa social, não guardar o núcleo familiar |
| Registros de acesso (IP, user agent, porta lógica) | Pessoal | MCI art. 15 (obrigação legal) | Guarda mínima obrigatória e depois eliminação; Decreto 8.771 art. 13 |

---

## 3. O que o código do SIGSAN realmente tem (compare com a seção (b))

Verificado em `compliance/sigsan-lgpd`, `shared/sigsan-core`, `platform/sigsan-platform`, `docker-compose.yml`, `frontend/src/services/api.ts`.

| Peça | Existe? | Observação |
|---|---|---|
| Tabelas `lgpd_consent`, `lgpd_data_subject_request`, `lgpd_dpia`, `lgpd_retention_schedule` | **Sim** (`V2_5__create_lgpd_schema.sql`, com RLS FORCE e auditoria via `register_tenant_table`) | Estrutura real, provisionada em todo tenant (FreshInstallTest) |
| `LgpdEngineService` (consentimento, RIPD, DSR) e 4 engines | Código existe, **sem nenhum teste** (diário §11.1) e **sem controller REST** (diário §11.2) | O frontend chama `/lgpd/consent` em `api.ts:220-224`, mas o endpoint não existe |
| `ConsentRecord` | Estrutura útil: texto, hash, IP, revogação | `LegalBasis` tem 7 valores; **faltam** art. 7º IV (pesquisa), VI (exercício de direitos em processo) e VIII (saúde). **Os comentários de artigo estão errados:** `CONTRACT` é art. 7º **V** (não II), `LEGAL_OBLIGATION` é **II** (não III), `VITAL_INTEREST` é **VII** (não IV), `PUBLIC_TASK` é **III** (não V) |
| `DataSubjectRequest` | Estrutura e intake reais | `RequestType` numera mal os incisos do art. 18: portabilidade é **V** (não VII), informação sobre compartilhamento é **VII** (não VIII), oposição é o **§2º** (não X); anonimização, bloqueio e eliminação são o **IV** (o código separa em 3 tipos, o que é útil, mas a citação está errada) |
| Prazo de 15 dias | **Fixo em `plusDays(15)`** (`LgpdEngineService:215`) para todo tipo de pedido, comentário "Art. 18" | O prazo é do **art. 19 II** e vale para confirmação/acesso em declaração completa; o art. 19 I exige resposta **imediata** em formato simplificado. Para titular de órgão público, o art. 23 §3º remete à LAI, Habeas Data e Lei 9.784 |
| `DataSubjectRightsEngine` | Intake e transição de estado | Honesto: **não verifica identidade, não coleta dado dos outros módulos, não conclui**. Não há como atender acesso ou eliminação de fato porque não existe tabela de cliente nem contrato de integração entre módulos |
| `AnonymizationEngine` | Só redige IP, user agent e geolocalização dos consentimentos revogados | Fila **em memória** (perde no restart). Não toca nenhum outro dado |
| `RetentionEngine` | Expira consentimentos e DSR vencidas; **nunca elimina** (`deletedRecords` sempre 0) | Honesto, mas significa que **não há eliminação por prazo** (art. 15-16) |
| `DpiaEngine` (RIPD) | Pontuação heurística por pesos fixos, sem metodologia oficial | O RIPD **só é obrigatório quando a ANPD determina** (art. 38); o regulamento ainda não existe. `report_pdf` nunca é gerado. A heurística lê strings (`contains("crianca")`) |
| Persistência de JSON | **Provável defeito:** `Map.toString()` e `List.toString()` gravados em colunas `jsonb` (`LgpdEngineService:64,86,87,149-155,180,213,216,261`). Produz `{lat=1.0}` e `[a, b]`, que **não são JSON válido**; o PostgreSQL rejeita | **Inferência, não executei**: zero teste cobre isso. O próprio `DpiaEngine` comenta que o formato armazenado "não é JSON estrito" |
| Cache de consentimento | `@Cacheable` com chave `#tenantId + ...`, mas `tenantId` não é parâmetro | Inferência: a chave não inclui tenant e o `@CacheEvict` do `grantConsent` usa outra chave. Hoje inócuo (cache desligado em `PlatformConfig`), mas revogação poderia ser servida velha |
| Criptografia AES-256-GCM por tenant | **Não existe em código.** Só a decisão (diário §3) | `shared/sigsan-security` está vazio, fora do `pom.xml` raiz; Vault em modo dev (`-dev-root-token-id=dev-root-token`). `spring-cloud-starter-vault-config` no POM da plataforma, sem uso de cifra |
| RLS | **Sim, testada** contra PostgreSQL real (diário §4 e §6; `TenantRlsIsolationTest`) | Isolamento entre tenants é o controle mais sólido do projeto |
| `audit_log` | Existe, **só-inserção** para a aplicação (`GRANT SELECT, INSERT`) | O trigger grava `to_jsonb(NEW)` inteiro (menos senha e token). **CPF, e-mail, IP e dados de tarifa social entrariam em claro; `lgpd_consent.ip_address` já entra.** Eliminação e anonimização não alcançam |
| Eliminação ao fim do contrato do tenant | **Não existe** | `TenantProvisioningService` só faz `DROP SCHEMA` no rollback de falha; as linhas de `public.audit_log` do tenant ficariam |
| Registro de operações de tratamento (art. 37) | **Não existe** | |
| Incidente de segurança | **Não existe** | Nem entidade, nem relógio de 3 dias úteis |
| Encarregado | **Não existe** campo nem página | `tenant_metadata` tem só endereço |
| Controle de acesso por campo e leitura auditada | `AuditAction.READ` existe, mas **nenhum trigger registra leitura** | O trigger só vê INSERT, UPDATE, DELETE |

---

## (b) Requisitos acionáveis para o software

Formato: ID | Requisito | Origem | Módulo afetado | **Situação** | Lacuna | Recomendação | Prioridade.

### B1. Governança e direitos do titular

| ID | Requisito | Origem | Módulo | Situação | Lacuna | Recomendação | Prior. |
|---|---|---|---|---|---|---|---|
| PRV-01 | Registro das operações de tratamento por tenant (finalidade, categoria de dado, base legal, destinatários, retenção, medidas) | LGPD art. 37 (controlador e operador) | `compliance/lgpd` | **NÃO ATENDE** | Nenhuma entidade. `DpiaRecord` não é registro de operações | Criar `processing_activity` ligada a módulo e a campo do cadastro; popular a partir da seção 2. Exportável em PDF/CSV para a ANPD | P0 |
| PRV-02 | Base legal por finalidade, **sem usar consentimento onde há contrato ou obrigação** | LGPD arts. 7º, 8º, 11 | `compliance/lgpd`, cadastro | **PARCIAL** | Enum incompleto; comentários de artigo errados; sem vínculo entre finalidade e campo; consentimento deve ficar só em marketing, e-mail promocional, etc. | Corrigir enum e citações; catálogo de finalidades por tenant; teste que impede `legal_basis=CONSENT` para fatura | P1 |
| PRV-03 | Consentimento com prova, texto versionado, granularidade e revogação simples | LGPD art. 8º §§1º a 6º | `compliance/lgpd` | **PARCIAL** | Sem API e sem teste; `grantConsent` sobrescreve o registro existente (perde o histórico probatório); possível erro de JSON; hash cai para UUID aleatório em exceção | Nova linha a cada concessão; versão do texto; teste de integração; remover o fallback do hash | P1 |
| PRV-04 | Atender pedidos do titular: confirmação e acesso **imediato simplificado** ou declaração completa em até **15 dias**; correção; anonimização/bloqueio/eliminação; portabilidade; informação sobre compartilhamento; revogação; oposição | LGPD arts. 18 e 19 (não há regulamento de prazos da ANPD; art. 19 §4º permite prazos setoriais) | `compliance/lgpd` e todos os módulos com dado de pessoa | **PARCIAL** | Intake e estados existem. Falta: coletar dado real dos módulos (acesso/portabilidade), executar correção/eliminação, prazo diferenciado (imediato x 15 dias), identificação do titular (hoje `subject_id UUID` genérico, sem tabela de cliente), canal do titular, notificação ao titular | Definir um contrato `PersonalDataProvider` que cada módulo implementa (listar, exportar, corrigir, bloquear, eliminar); o motor orquestra. Prazo configurável por tenant e tipo | P0 |
| PRV-05 | Titular de **órgão público**: prazos e ritos da LAI, Habeas Data e Lei 9.784 (art. 23 §3º), com ocultação de dado pessoal de terceiros (LAI art. 31) | LGPD art. 23 §3º; LAI arts. 11 e 31 | `compliance/lgpd`, portal | **NÃO ATENDE** | Todo tenant recebe 15 dias | Atributo `tenant.legal_nature`; perfil de prazos por natureza e por norma local | P1 |
| PRV-06 | Portabilidade e cópia eletrônica integral em formato reutilizável, quando a base for consentimento ou contrato | LGPD art. 18 V e art. 19 §3º | todos | **NÃO ATENDE** | Não há exportador | Exportação JSON/CSV por titular, assinada, com expiração do link | P1 |
| PRV-07 | Eliminação, anonimização e bloqueio **efetivos**, com exceções do art. 16 (obrigação legal, etc.) e guarda mínima | LGPD arts. 15, 16, 18 IV e VI | todos, `audit_log` | **NÃO ATENDE** | `RetentionEngine` nunca elimina; `AnonymizationEngine` só toca consentimentos; `audit_log` replica PII e é só-inserção | Anonimização por substituição determinística (token), job privilegiado de purga em `audit_log`, `legal_hold` por regra. Ver PRV-15 | P0 |
| PRV-08 | Tabela de retenção por categoria, com prazo, âncora (data de início), motivo legal e ação (anonimizar, eliminar, arquivar); **sem prazo padrão inventado** | LGPD arts. 15 e 16; Decreto 8.771 art. 13 §2º | `compliance/lgpd` | **PARCIAL** | `RetentionSchedule` existe, sem semente (correto), sem âncora por registro, sem executor | Catálogo de categorias do SIGSAN com **prazo vazio até o jurídico preencher**; ver "motivos de retenção" na seção 6 | P1 |
| PRV-09 | Direito à revisão de **decisões automatizadas** e explicação dos critérios | LGPD art. 20 | tarifa social, cobrança, `intelligence/*` | **NÃO ATENDE** | Classificação automática da tarifa social (Lei 14.898 art. 4º) e critérios de negativação são decisões automatizadas | Guardar critérios aplicados e versão da regra em cada decisão; ação "pedir revisão" com atendimento humano | P1 |
| PRV-10 | Aviso de privacidade e canal do titular **por tenant**, com identidade e contato do **encarregado** divulgados | LGPD arts. 9º e 41 §1º; Res. 18/2024, arts. 8º e 9º; para público, art. 23 I | portal, `tenant_metadata` | **NÃO ATENDE** | Sem campos, sem página, sem versão do aviso | Campos de encarregado e ato de designação por tenant; página pública white-label; para ente público, link do Diário Oficial da designação (Res. 18, art. 5º §1º) | P1 |
| PRV-11 | RIPD **quando a ANPD determinar**, com descrição dos dados, metodologia de coleta e segurança, análise de riscos e salvaguardas | LGPD art. 38 (regulamento ainda inexistente); Res. 15/2024, art. 8º lista o RIPD entre o que a ANPD pode pedir após incidente | `compliance/lgpd` | **PARCIAL** | Entidade e heurística existem; não gera PDF; `DpiaEngine` cita art. 38 como se fosse sempre obrigatório | Manter como **modelo editável**, não como "certificação" de risco. Gerar PDF a partir do `processing_activity` | P2 |

### B2. Segurança e criptografia

| ID | Requisito | Origem | Módulo | Situação | Lacuna | Recomendação | Prior. |
|---|---|---|---|---|---|---|---|
| SEC-01 | Isolamento de dados entre tenants | LGPD art. 46; decisão arquitetural | `shared/sigsan-core` | **ATENDE** | Isolamento testado em PostgreSQL real | Manter o teste de RLS no CI | P0 (mantido) |
| SEC-02 | Cifra de campo por tenant para identificadores e dados de alto risco (CPF, NIS, renda, nascimento, nome da mãe) | LGPD art. 46 e 6º VII; art. 48 §3º (cifra reduz o juízo de gravidade do incidente) | `shared/sigsan-security` (vazio) | **NÃO ATENDE** | Só existe a decisão. Vault em modo dev | Implementar: DEK por tenant (AES-256-GCM, IV aleatório de 96 bits, **AAD = tenant_id + tabela + coluna**), KEK no Vault em modo de produção (TLS, unseal), rotação com versão da chave na coluna, `AttributeConverter` JPA. Teste: dado de tenant A não decifra com chave de B | P0 |
| SEC-03 | **Índice cego** para busca e unicidade do CPF/CNPJ (e e-mail, se cifrado) | LGPD art. 6º III e VII; Lei 13.460 art. 10-A §1º (CPF obrigatório) | cadastro (`customers`) | **NÃO ATENDE** | Planejado em `01 §8.1` (`document_hash`), sem implementação | HMAC-SHA-256 com chave **por tenant** separada da chave de cifra; só igualdade (sem busca parcial); normalizar CPF antes (11 dígitos). **Nunca SHA-256 puro** | P0 |
| SEC-04 | Controle de acesso por campo, mascaramento e permissão própria para dado de tarifa social | LGPD arts. 6º VII e 46; NR ANA 13 art. 40 (acesso restrito à concessão do benefício) | RBAC (relatório 04) | **NÃO ATENDE** | RBAC granular é só proposta | Perfil `SOCIAL_TARIFF_VIEW`; máscara por padrão; "ver completo" gera evento de leitura | P1 |
| SEC-05 | Trilha de **acesso** (leitura) a dado pessoal e inventário de acessos aos registros | Decreto 8.771 art. 13 III; LGPD art. 37 | `shared/sigsan-core` | **PARCIAL** | `AuditAction.READ` existe; o trigger só vê DML; `ip_address` e `user_agent` não são preenchidos pelo trigger | Auditar leitura na camada de serviço para entidades de PII; incluir IP, user, finalidade | P1 |
| SEC-06 | **`audit_log` sem PII em claro** e compatível com eliminação | LGPD arts. 6º III, 16 e 46 | `V1_1` (`audit_trigger_function`), `V1__create_core_schema.sql` | **NÃO ATENDE** | `to_jsonb(NEW)` copia a linha inteira, inclusive `report_pdf` (bytea) e IP do consentimento; a aplicação não pode apagar | Lista por tabela de colunas a **omitir ou substituir por hash/ciphertext**; `changed_fields` só com nomes; purga por job privilegiado com `legal_hold`; particionar por mês para expirar por partição | P0 |
| SEC-07 | **Registro de incidente** (5 anos), **comunicação à ANPD e aos titulares em 3 dias úteis**, conteúdo mínimo, e aviso do operador ao controlador | LGPD art. 48; Res. 15/2024, arts. 5º, 6º, 9º, 10 | `compliance/lgpd` (novo) | **NÃO ATENDE** | Nada | Entidade `security_incident` com data de conhecimento, categorias, nº de titulares (com crianças, adolescentes e idosos), riscos, medidas, comunicação à ANPD e ao titular; **relógio em dias úteis** (calendário de feriados por tenant); modelos de texto | P0 |
| SEC-08 | Nenhum dado pessoal em URL, query string, log de aplicação ou `System.out` | LGPD arts. 6º VII e 46; achado do GSAN (00 §5; 02 linha 644; 03 linha 749) | todos | **PARCIAL** | Sem código comercial ainda, só decisão registrada | Lint/teste de arquitetura que falha com CPF/nome em log; filtro de log com máscara | P1 |
| SEC-09 | Segredos e chaves fora do repositório; Vault de produção | LGPD art. 46 | infra | **NÃO ATENDE** | Vault dev, token fixo no compose e no Terraform | Resolver antes do primeiro dado real | P0 |
| SEC-10 | Dados de teste e carga inicial sem dado real; ETL do GSAN com dado real tratado como produção | LGPD art. 6º III; 00 §6 e §8 | ETL, QA | **N/A hoje** | Existe risco se houver importação | Política: staging só com dado sintético ou anonimizado | P1 |

### B3. Logs, retenção e transferência

| ID | Requisito | Origem | Módulo | Situação | Lacuna | Recomendação | Prior. |
|---|---|---|---|---|---|---|---|
| LOG-01 | **Registros de acesso a aplicações: guarda mínima de 6 meses**, sob sigilo e em ambiente controlado; incluir porta lógica de origem quando necessário | MCI art. 15; Decreto 8.771 arts. 13 e **15-A** (novo, 2026) | plataforma, API gateway | **NÃO ATENDE** | Não há camada HTTP ainda; `audit_log` tem `ip_address` mas não cobre todo acesso | Log de acesso estruturado (IP, porta, data/hora com fuso, usuário), retenção configurável com piso de 6 meses e teto por política, armazenado fora do `audit_log` de negócio. **Aplicabilidade ao Ziip e à concessionária é pergunta aberta** | P1 |
| LOG-02 | Excluir registro e dado quando cumprida a finalidade ou o prazo legal ("reter a menor quantidade possível") | Decreto 8.771 art. 13 §2º | plataforma | **NÃO ATENDE** | Sem expurgo | Job de expurgo por categoria | P1 |
| TIN-01 | Transferência internacional só por hipótese do art. 33 (adequação, cláusulas-padrão, etc.); **cláusulas-padrão da Res. 19/2024** | LGPD art. 33; Res. 19/2024 (prazo de 12 meses venceu ≈23/08/2025); Res. 32/2026 (UE adequada) | infra, sub-operadores, `intelligence/*` | **NÃO ATENDE** | `DpiaRecord.thirdCountryTransfers` é só campo. Sem política nem bloqueio. O diário prevê "GPU em nuvem spot" para treino | Política `data_residency = BR` por tenant; bloquear envio de PII para fora; lista de sub-operadores com país; treino de modelo **só com dado anonimizado** | P1 |
| TIN-02 | Lista de sub-operadores e destinatários por tenant | LGPD art. 9º V; art. 18 VII | contrato, portal | **NÃO ATENDE** | | Tabela `recipient` por tenant, alimentando o aviso e a resposta ao titular | P1 |

### B4. Cadastro comercial (relatório 01 §8)

| ID | Requisito | Origem | Módulo | Situação | Lacuna | Recomendação | Prior. |
|---|---|---|---|---|---|---|---|
| CAD-01 | **CPF obrigatório** para pessoa física usuária de serviço público delegado; **não exigir outro número** de identificação | Lei 13.460, art. 10-A (Lei 14.534/2023) | `customers` | **NÃO ATENDE** (módulo inexistente) | O modelo do relatório 01 prevê RG, nome da mãe etc. como campos de identificação | Tornar RG, nome da mãe, sexo e profissão **opcionais** e justificados (PRV-01) | P0 |
| CAD-02 | Cifra + índice cego em `document`, `rg`, `birth_date`, `mother_name`, NIS | ver SEC-02 e SEC-03 | `customers` | **NÃO ATENDE** | `document_encrypted/document_hash` só no desenho | ver SEC-02/03 | P0 |
| CAD-03 | `customer_emails.consent_id` e finalidade: e-mail de fatura é contrato; marketing é consentimento separado | LGPD arts. 7º V, 8º §4º (autorização genérica é nula) | `customers` | **NÃO ATENDE** | Desenho do 01 já prevê o vínculo | Manter e aplicar PRV-02 | P1 |
| CAD-04 | Vínculo cliente-imóvel temporal (proprietário, usuário, responsável) preserva histórico; eliminar/anonimizar o titular não pode quebrar a conta histórica | LGPD art. 16 I; art. 6º III | `unit_customers` | **NÃO ATENDE** | | Anonimização por pseudonimização com tabela de chaves separada; conta histórica guarda o necessário | P1 |
| CAD-05 | Cliente é entidade distinta de usuário do sistema | LGPD art. 5º (titular x operador do sistema) | `customers` x `users` | **NÃO ATENDE** | `consumer_units.owner_id/responsible_id` apontam para `users` (relatório 01 §7) | Corrigir antes de existir dado (já recomendado em 00 §4.1) | P0 |
| CAD-06 | Geolocalização e endereço da ligação como dado pessoal; camada GIS sem identidade | Decreto 8.771 art. 14 I; LGPD art. 6º III | GIS, cadastro | **NÃO ATENDE** | `consumer_units.coordinates` e `address JSONB` na mesma linha da conta | A camada GIS referencia `unit_id` opaco; consultas GIS não retornam nome nem CPF | P2 |
| CAD-07 | Fotos de campo: remover EXIF/GPS, retenção própria, acesso por OS | LGPD arts. 6º III e 46 | `documents`, OS | **NÃO ATENDE** | `documents.metadata` livre | Pipeline de upload com sanitização | P2 |
| CAD-08 | Dados de funcionários e rastreamento (`gps_track`): finalidade, aviso, retenção curta, acesso restrito | LGPD arts. 7º V e IX, 9º | OS, app de campo | **PARCIAL** | Tabela existe; sem política | Retenção configurável; teste de legítimo interesse documentado | P2 |

### B5. Tarifa social (parte de dados; a regra tarifária está em outra área)

| ID | Requisito | Origem | Módulo | Situação | Lacuna | Recomendação | Prior. |
|---|---|---|---|---|---|---|---|
| TS-01 | Classificação **automática** a partir do CadÚnico e de bases do prestador, **sem prévia comunicação ao usuário** (mas com transparência geral) | Lei 14.898, art. 4º; LGPD arts. 9º e 23 I | tarifa social | **NÃO ATENDE** | Módulo inexistente | Aviso de privacidade específico da tarifa social; log da decisão e da regra (PRV-09) | P0 |
| TS-02 | **Origem dos dados via ERI**, em conjunto mínimo; sigilo conforme a LGPD, responsável pela guarda, uso só para o benefício | NR ANA 13, arts. 30, 38 e 40 (conferir); Decreto 11.016 art. 13 | integração com ERI | **NÃO ATENDE** | Sem conector, sem modelo de lote recebido | Importador de lote da ERI com termo de responsabilidade registrado, hash do arquivo, expurgo do lote após a carga, `source_batch_id` por linha | P0 |
| TS-03 | Validade do registro: **2 anos** desde a inclusão ou revalidação; usar o registro mais recente | NR ANA 13, art. 31; Lei 14.898 art. 4º §3º; Decreto 11.016 art. 12 | tarifa social | **NÃO ATENDE** | | `valid_until` obrigatório; rotina de vencimento | P1 |
| TS-04 | Direito de permanência por **3 meses** após deixar a elegibilidade, com **aviso na fatura**; perda por irregularidade com aviso por 3 meses | Lei 14.898, arts. 2º §2º e 3º | faturamento | **NÃO ATENDE** | | Mensagem de fatura por unidade, com prova de impressão ou envio | P1 |
| TS-05 | Relatório **anual** à ERI dos usuários contemplados, conforme o que a ERI demandar | Lei 14.898, art. 4º §§1º e 2º | relatórios regulatórios | **NÃO ATENDE** | Conteúdo "a definir em regulamentação posterior" | Exportação parametrizável por tenant, com registro de quem enviou e quando | P1 |
| TS-06 | Não exigir documentos além dos da lei; meios físicos e virtuais de cadastramento; não classificar após apresentar os documentos é "cobrança indevida" | Lei 14.898, art. 5º | atendimento | **NÃO ATENDE** | | Checklist fixo de documentos; prova de recebimento | P1 |

### B6. Cobrança, negativação e cadastro positivo

| ID | Requisito | Origem | Módulo | Situação | Lacuna | Recomendação | Prior. |
|---|---|---|---|---|---|---|---|
| NEG-01 | **Não negativar sem prova de notificação prévia** feita pelo órgão mantenedor; o prestador entrega o **endereço correto** e guarda o que enviou | CDC art. 43 §2º; Súmulas 359 e 404; Tema 1.315 (2026) | `dunning` (inexistente) | **NÃO ATENDE** | Módulo inexistente | Bloqueio por regra: sem registro de envio ao bureau com endereço e e-mail usados, sem inclusão. **Prazo de espera e canal: parâmetros do jurídico** (não achei prazo mínimo em lei) | P0 |
| NEG-02 | Exclusão do registro em **5 dias úteis** após pagamento integral e efetivo; prazo de contagem a partir da disponibilidade do valor | Súmula 548 | `dunning`, arrecadação | **NÃO ATENDE** | | Evento de pagamento dispara exclusão; SLA com alerta | P0 |
| NEG-03 | Informação negativa por no máximo **5 anos**; não informar dívida prescrita | CDC art. 43 §§1º e 5º; Súmula 323 | `dunning` | **NÃO ATENDE** | | Data-limite por dívida; expurgo | P0 |
| NEG-04 | Cadastro positivo: o prestador de água e esgoto **pode** ser "fonte" (art. 2º IV); se participar, dados corretos, correção em até 10 dias, cancelamento a pedido, sem dado sensível, 15 anos | Lei 12.414 arts. 2º, 3º §3º, 5º, 14; LC 166; Decreto 9.936 | integração com bureaux | **N/A hoje** | A lei **autoriza**; não encontrei obrigação (confirmar) | Item opcional por tenant; leiaute definido entre fonte e gestor | P2 |
| NEG-05 | Comunicação ao consumidor sobre abertura de cadastro (art. 43 §2º) com prova de entrega | CDC; Tema 1.315 | cobrança | **NÃO ATENDE** | | Guardar protocolo de envio e de entrega | P1 |
| NEG-06 | Negativação por **ente público** (SAAE): compartilhamento com bureau privado exige base e eventual consentimento (art. 27) e comunicação à ANPD de contratos (art. 26 §2º) | LGPD arts. 26 e 27 | `dunning` | **NÃO ATENDE** | Pergunta aberta (seção e) | Chave `allow_credit_bureau` por tenant, desligada por padrão para entes públicos até parecer | P1 |

### B7. Setor público e compartilhamento

| ID | Requisito | Origem | Módulo | Situação | Lacuna | Recomendação | Prior. |
|---|---|---|---|---|---|---|---|
| PUB-01 | Indicação de encarregado publicada em Diário Oficial; informação clara sobre as hipóteses de tratamento no sítio | LGPD art. 23 I e III; Res. 18/2024, art. 5º | portal por tenant | **NÃO ATENDE** | | Campos e página (PRV-10) | P1 |
| PUB-02 | Transparência ativa e passiva (LAI): pedidos de acesso, 20+10 dias, recurso | LAI arts. 8º, 11 | portal | **NÃO ATENDE** | Fora do núcleo do SIGSAN; decidir se entra | Módulo opcional para tenant público; padrão: integrar com o sistema de ouvidoria do município | P2 |
| PUB-03 | **Dado de saneamento enviado a SINISA/ANA/ERI/ARSAE** sem dado pessoal (SINISA é dado aberto); relatório de usuários da tarifa social vai só à ERI | Lei 11.445 art. 53 §1º; Lei 14.898 art. 4º §1º | relatórios regulatórios | **NÃO ATENDE** | Módulo inexistente | Duas famílias de exportação: **agregada pública** e **nominal restrita**, com registro de destinatário e base (PRV-01) | P1 |
| PUB-04 | Registro de compartilhamento com banco, PSP, bureau, ERI (destinatário, finalidade, base) para responder ao art. 18 VII | LGPD arts. 9º V, 18 VII | arrecadação, cobrança | **NÃO ATENDE** | | Tabela `data_sharing_log` | P1 |

### B8. Fora do escopo ou sem efeito

| ID | Item | Situação |
|---|---|---|
| N/A-01 | ECA Digital (Lei 15.211/2025): supervisão parental, verificação de idade, etc. | **N/A provável** (serviço não direcionado a menores nem de acesso provável). Reavaliar se houver app com funcionalidade que atraia adolescentes |
| N/A-02 | ATPP (Res. 2/2022): dispensas de encarregado e prazos em dobro | **N/A** para o SIGSAN e seus clientes públicos; clientes privados grandes não se enquadram (receita e risco alto). O Ziip, como startup, pode se enquadrar **se** não fizer tratamento de alto risco, o que o volume de dados de tarifa social torna improvável |

---

## (c) Prazos e marcos

### Já vencidos ou em vigor

| Data | Marco | Efeito para o SIGSAN | Fonte |
|---|---|---|---|
| 14/08/2018 | LGPD publicada | Base de tudo | Planalto |
| 28/12/2018 | Em vigor os artigos da ANPD (art. 65 I) | n/a | LGPD |
| 08/04/2019 | LC 166/2019 | Cadastro positivo opt-out; prestadores de água e esgoto são "fonte" | Planalto |
| 24/07/2019 | Decreto 9.936 | Leiaute fonte-gestor | Planalto |
| 18/09/2020 (NÃO CONFIRMADO em fonte primária) | Vigência geral da LGPD | | ver (a) |
| **01/08/2021** | Sanções administrativas da ANPD (art. 65 I-A) | Multa de até 2% do faturamento, **R$ 50 milhões por infração**, e demais sanções (art. 52). Entes públicos: só as sanções não pecuniárias do art. 52 §3º | Lei 14.010 art. 20 |
| 24/02/2023 | Res. 4/2023 | Dosimetria | DOU |
| 22/05/2023 | Enunciado 1/2023 | Crianças | DOU |
| 24/04/2024 | Res. 15/2024 | **3 dias úteis**; registro de 5 anos | DOU |
| 16/07/2024 | Res. 18/2024 | Encarregado | DOU |
| 13/06/2024; ≈11/12/2024 | Lei 14.898 publicada; em vigor após 180 dias | Tarifa social | Planalto |
| ≈23/08/2025 | Fim do prazo de 12 meses para incorporar as cláusulas-padrão da Res. 19/2024 (data exata depende da publicação no DOU; **conferir**) | Contratos com sub-operador no exterior | Res. 19 art. 2º p.ú. |
| 17/09/2025 | ECA Digital publicado | Provável N/A | Planalto |
| 26/01/2026 | Res. 32/2026: UE adequada | Transferência para UE por art. 33 I | DOU |
| 25/02/2026 | Lei 15.352/2026 | ANPD agência | Planalto |
| 17/03/2026 | ECA Digital em vigor | Provável N/A | Lei 15.211 art. 41-A |
| 11/03/2026 | STJ Tema 1.315 | Notificação eletrônica com prova de entrega | STJ |
| ≈08/04/2026 | Estrutura da ANPD (Decreto 12.881) | n/a | Planalto |
| ≈20/07/2026 | Decreto 12.975: porta lógica no registro de acesso (art. 15-A) | Logs | Planalto |

### Vencem ou se resolvem de agora em diante

| Data | Marco | Efeito | Confiança |
|---|---|---|---|
| **26/10/2026** | Fim da Consulta Pública ANPD 1/2026 (revisão do regulamento de fiscalização e sancionador, com multa diária de até R$ 50 milhões em cautelares, segundo a minuta) | Acompanhar; pode mudar o rito de incidente e fiscalização | Média (nota da ANPD) |
| **11/12/2026** | Prazo para o prestador implementar ou adequar a tarifa social à Lei 14.898 e à NR ANA 13/2025 (24 meses da vigência da lei; também citado pelo guia da ANA) | **P0 do módulo de tarifa social**: ver TS-01 a TS-06 | Alta (cálculo e guia) |
| Anual, a partir da adoção | Relatório à ERI dos usuários contemplados | TS-05 | Alta (lei) |
| A cada 2 anos | Revalidação do cadastro no CadÚnico; registros mais velhos não valem | TS-03 | Média (NR ANA 13 art. 31, Decreto 11.016 art. 12) |
| 2027 e depois | Regulamentos da ANPD sobre **direitos do titular**, **RIPD** e eventual guia de alto risco; Mapa de Temas 2026-2027 (Res. 30/2025) | Pode trazer **prazos de resposta e conteúdo de RIPD**; reabrir PRV-04 e PRV-11 | Baixa (agenda; sem data confirmada) |
| 2027 a 2030 | **Nenhum marco fixo da minha área foi confirmado.** Marcos do setor (universalização) estão em outro relatório | | |

---

## (d) Variação regulatória: o que precisa ser parâmetro por tenant

### d.1 O que muda por natureza do cliente

| Eixo | Privado (concessionária ou PPP) | Autarquia, SAAE, empresa pública ou sociedade de economia mista |
|---|---|---|
| Regime da LGPD | Capítulo II (arts. 7º a 11) | Capítulo IV (arts. 23 a 30) mais o Capítulo II |
| Direito do titular: prazo | art. 19 (imediato / 15 dias) | art. 23 §3º remete a LAI (20 + 10 dias), Habeas Data e Lei 9.784 |
| Sanção da ANPD | Todas, multa até 2% do faturamento, máximo R$ 50 mi/infração | Apenas I, IV, V, VI, X, XI, XII (art. 52 §3º), sem multa |
| Encarregado | Obrigatório (art. 41); dispensa só para ATPP | Obrigatório (art. 23 III), com **publicação em Diário Oficial** (Res. 18, art. 5º §1º) |
| Registro de incidente | 5 anos | 5 anos **não se aplica** a entes do art. 23; vale a tabela de temporalidade ou o CONARQ (Res. 15, art. 10 §2º) |
| LAI | Só parcialmente (se receber recurso público) | Integral (LAI art. 1º p.ú. II) |
| Compartilhamento com bureau, banco, SaaS | Base do art. 7º | Arts. 26 e 27 (finalidade pública, comunicação à ANPD, consentimento, exceções) |
| Origem dos dados do CadÚnico | Via ERI (Informe 57/2024, **NÃO CONFIRMADO**) | Pode ser direta, conforme a mesma fonte |

### d.2 Estado e agência reguladora

Cada **ERI** regulamenta o fluxo de dados da tarifa social e o relatório anual (Lei 14.898 art. 4º §2º; NR 13 arts. 30 e 38). Não pesquisei regulador por regulador: **NÃO CONFIRMADO** o que cada agência estadual ou intermunicipal exige. Municípios têm **norma própria de LAI** (o Decreto 7.724 é federal).

### d.3 Parâmetros que devem ser configuráveis (`tenant_settings`)

| Grupo | Parâmetro |
|---|---|
| Natureza | `legal_nature` (PRIVADA, AUTARQUIA, EMPRESA_PUBLICA, SEM, OUTRA); `public_entity` booleano; esfera (municipal, estadual, federal) |
| Titulares | prazos de resposta por tipo e por natureza (dias corridos ou úteis), método de verificação de identidade, canais, texto do aviso e versão |
| Encarregado | nome, contato, ato de designação, link do Diário Oficial, substituto |
| Finalidades e bases | catálogo de finalidades, base legal, texto do consentimento versionado |
| Retenção | categoria, prazo, âncora, ação, `legal_hold`, **sem valor padrão** |
| Logs | piso de 6 meses (MCI art. 15) e teto, incluir porta lógica, local de guarda |
| Incidente | relógio em **dias úteis** com calendário de feriados do tenant, contatos, janela de aviso do operador, modelos de comunicação |
| Negativação | `allow_credit_bureau`, bureaux, prazo de espera após a notificação, canal (carta, e-mail com prova de entrega), endereço de origem, prazo de baixa (5 dias úteis, Súmula 548) |
| Cadastro positivo | `credit_positive_source` ligado ou não, gestor, leiaute |
| Tarifa social | fonte (`ERI` ou `DIRETA`), validade em meses (24), carência (3 meses), periodicidade do relatório à ERI, destinatário, documentos aceitos |
| Residência de dados | `data_residency` (BR), sub-operadores e país, política de transferência internacional |
| Transparência | LAI ativa (sim/não), link do portal de transparência |

---

## (e) Perguntas em aberto para o jurídico e o regulatório

1. **Enquadramento do Ziip:** operador em todas as atividades? Treino de modelos e benchmark entre tenants só com dado anonimizado e autorizado em contrato? O Ziip tem encarregado próprio (facultativo, Res. 18, art. 6º)?
2. **Ente público contratando SaaS:** contratar um operador privado é "uso compartilhado" que exige comunicação à ANPD (art. 26 §2º) e base do art. 27? O guia ANPD do poder público diz algo específico? (li só resumo)
3. **Renda, NIS e BPC são dado sensível** para fins de arts. 11 e Res. 15/2024 (art. 5º I)? Existe posicionamento da ANPD? Isso decide base legal e prazo de comunicação de incidente.
4. **Prazo de 15 dias do art. 19 II:** dias corridos ou úteis? E a **resposta imediata** do art. 19 I: qual formato "simplificado" é aceito? Existe regulamento do art. 19 §4º no horizonte?
5. **Negativação:** prazo mínimo entre a notificação e a inclusão (não achei na lei); canal aceito após o Tema 1.315 e o REsp 2.070.073; **ente público pode negativar** (natureza de tarifa x taxa, art. 27 da LGPD)?
6. **Cadastro positivo:** a lei autoriza o prestador de água e esgoto como fonte; algum contrato de concessão ou regulador **obriga** ou veda?
7. **Marco Civil, art. 15:** a concessionária, ao oferecer portal e app ao cliente, é "provedor de aplicações" sujeito aos 6 meses? E o Ziip, como provedor do SaaS? Impacto do **art. 15-A** (porta lógica) no desenho dos logs? Qual a situação dos PDLs que querem sustar o Decreto 12.975?
8. **Retenção:** prazos de guarda de contas, leituras e contratos por finalidade (repetição de indébito de 10 anos; cobrança; fiscal; contábil; trabalhista). Quem define a tabela inicial?
9. **Tarifa social:** confirmar os artigos da NR ANA 13 (li por resumo), o Informe 57/2024 e a Portaria MC 810/2022; confirmar quem é o controlador do lote da ERI e se o prestador privado pode guardar o dado depois da carga.
10. **Decreto 11.016/2022:** o texto compilado do Planalto está atual? Quem assina o termo de responsabilidade e sigilo do prestador?
11. **Decisão automatizada (art. 20):** a classificação automática da tarifa social e os critérios de negativação precisam de canal de revisão humana documentado? Qual o prazo?
12. **Foto de campo e rastreamento do leiturista:** qual a base legal, qual o aviso e qual o prazo de retenção aceitável?
13. **Contrato:** limite de responsabilidade do operador, cláusula de eliminação de backups, aceitação de auditoria pelo controlador e pela ANPD.
14. **Encarregado dos clientes privados:** o SIGSAN deve oferecer encarregado como serviço (conflito de interesse, Res. 18, art. 19)?
15. **ANPD:** confirmar se saiu algum ato **depois** da Res. 32/2026 (a página oficial que consultei lista até ela) e o resultado da Consulta Pública 1/2026 (encerra em 26/10/2026).

---

## Limites e itens NÃO CONFIRMADOS

- **Não li** o texto integral de: Guia ANPD de agentes de tratamento e Guia do poder público (só resumos), Informe 57/2024, Portaria MC 810/2022, Agenda Regulatória 2025-2026, Res. 4/2023 além das definições e da classificação de infração (valores-base de multa).
- **NR ANA 13/2025:** os números dos artigos vêm de leitura automática de uma página truncada (as últimas ~50 mil de ~150 mil caracteres ficaram de fora). Conferir no DOU.
- **Súmulas do STJ** (359, 404, 548, 323) e Tema 1.315: confirmadas por páginas do STJ e por resumo de busca; o texto literal da tese do Tema 1.315 não foi lido.
- **Status dos regulamentos de direitos do titular e de RIPD** e a "Consulta Pública 1/2026": confirmados só por nota da ANPD e por fontes secundárias (blogs e escritórios).
- **Vigência geral da LGPD em 18/09/2020:** NÃO CONFIRMADA em fonte primária.
- Nenhuma norma estadual ou de agência reguladora local foi pesquisada. Prazos fiscais, contábeis e trabalhistas de guarda: fora desta área.
- Os achados sobre o código vêm de leitura. **Não executei** testes. Os pontos marcados como inferência (JSON inválido em `jsonb`, chave de cache) precisam de teste de integração para virar fato.

---

## Fontes consultadas (resumo)

Planalto: Leis 13.709 (compilada), 13.853, 14.010, 14.460, 15.352, 15.211, 12.965, 12.414, 8.078 (compilada), 12.527, 13.460, 14.534, 14.898, 11.445; LC 166; Decretos 8.771, 12.975, 9.936, 7.724, 11.016, 12.881, 10.046. ANPD/DOU: Resoluções CD/ANPD 1/2021, 2/2022, 4/2023, 15/2024, 18/2024, 19/2024, 32/2026; Enunciado 1/2023; página de regulamentações; nota da Consulta Pública 1/2026. ANA: Res. 271/2025 (NR 13) e página de perguntas da tarifa social. STJ: súmulas 359, 404, 548, 323, 412; notícias do Tema 1.315, do REsp 2.070.073 e do REsp 1.532.514. Fontes secundárias, usadas só onde indicado: Data Privacy Brasil (agenda da ANPD), Mattos Filho e Atlas Público (Decreto 12.975 e PDLs).
