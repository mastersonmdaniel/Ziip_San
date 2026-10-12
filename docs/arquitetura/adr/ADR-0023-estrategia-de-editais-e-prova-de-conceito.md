# ADR-0023 — Estratégia de editais e prova de conceito

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0001 (roadmap: P4 e 2028); ADR-0011 (hospedagem certificada); ADR-0015 (travas legais); ADR-0024 a
  ADR-0028; diário de bordo §1 ("Fora do escopo: POC, editais"), que este ADR revoga; plano P0.30 (escolha do piloto),
  P0.23 (carga sintética), P0.27 (homologação)
- **Base:** `docs/levantamento/editais/18a` a `18h`, em especial `18h` §3.5, §4 e §6 (decisões 1, 4 e 5), `18d` §4
  (decisão 5), `18e` §1.4 e `18g` §4 (D5)
- **Ajuste de 12/10/2026 (editais, v2.5), antes de qualquer implementação:** item 4 decidido pelo ADR-0029: contratação
  de 4 a 5 desenvolvedores a partir de agosto de 2027, prontidão de POC em 30/06/2028 e disputa de licitações no 2º
  semestre de 2028; a tarefa derivada "Decisão de equipe para editais" fica cumprida, e o parceiro de implantação e
  treinamento presencial (ED-11) e o custo da equipe seguem como pendências do plano (§2.4); decisão do dono do
  produto.

## Contexto

A análise de aderência a dois editais de saneamento de Santa Catarina, VISAN Videira PE 06/2026 e SAMAE São Ludgero
PE 2/2026 (`18a` a `18h`), mostrou que, com o plano atual, o Ziip SAN seria reprovado nas duas provas de conceito
(POC). Nos dois certames a POC é eliminatória e acontece depois dos lances: Videira cobra 81 itens do Anexo II e exige
146 de 162 pontos (cerca de 90%); o SAMAE avalia 80 itens e desclassifica na hora quem falha em item crítico, sem
chance de corrigir na implantação. O prazo entre a convocação e a POC é de 5 (Videira) ou 7 (SAMAE) dias úteis.

O diário de bordo (§1) mantinha "POC, editais" fora do escopo. Isso é coerente com o caminho crítico do piloto, mas
significa não disputar licitações deste perfil antes de 2028 e não medir, fase a fase, quanto falta.

A habilitação técnica é a barreira imediata (`18h` §4.1): Videira exige dois atestados de prestadora de água, com 8.500
ligações somadas e envio ao e-Sfinge; o SAMAE, um atestado sem quantitativo. O piloto do plano gera, no melhor caso, um
atestado no fim de 2027.

Alguns itens dos editais contrariam a lei (`18e` §1.4, item 5): perda da tarifa social por fatura em atraso (TR-286,
contra a Lei 14.898/2024), geração de ordens de corte em qualquer dia e horário (TR-415, SG-398, contra a Lei
14.015/2020) e transferência à contratada das multas do TCE/SC por atraso ou erro na remessa (TR-494).

## Decisão

1. **Calendário.** Abre-se uma **trilha de editais no P4** (a partir de agosto de 2027), mirando licitações de 2028. O
   go-live do piloto (junho e julho de 2027) e o caminho crítico do P0 ao P3 não mudam. Até 2028, a venda se faz por
   piloto, por dispensa e com clientes que não exijam POC.
2. **POC passa a ser escopo.** A partir do P3, a prontidão de POC é critério de aceite de fase:
   - **ambiente de demonstração permanente**, separado de homologação e produção, com tenant de demonstração de pelo
     menos 10 mil unidades fictícias geradas por massa sintética, sem dado pessoal real;
   - **roteiros internos** derivados da POC da VISAN (Anexo II, 81 itens) e da lista crítica do SAMAE, com cada item
     ligado ao módulo e à fase que o entrega;
   - **ensaio interno cronometrado ao fim de cada fase**, no prazo dos editais (5 e 7 dias úteis de preparação),
     com o resultado registrado; a partir do P3, o resultado entra no aceite da fase, com a meta de cada fase definida
     no plano. Meta final, ao fim da trilha do P4: roteiro da VISAN com pelo menos 146 de 162 pontos e todos os itens
     críticos do SAMAE demonstrados.
3. **Atestados.**
   - **Primeiro atestado:** piloto público em Santa Catarina, prestadora de água, contratado por dispensa (Lei
     14.133/2021, art. 75, II) ou por Contrato Público de Solução Inovadora (LC 182/2021), com cláusula de emissão do
     atestado depois dos ciclos em produção. O critério entra na escolha do piloto (P0.30). Uma autarquia catarinense
     também abre o caminho do atestado de e-Sfinge.
   - **Segundo atestado:** cliente privado (concessionária ou prestadora privada), aceito pelos dois editais.
   - **Consórcio:** só se surgir oportunidade concreta; o roadmap não depende dele.
4. **Equipe.** No piloto, implantação e treinamento são **remotos**. A equipe para editais (especialistas presenciais,
   técnico pós-implantação, instrutores e plantão para a severidade 1 em 2 horas, ADR-0025) é decidida no P4.
5. **Itens de edital que contrariam a lei.** O produto segue a lei, com as travas do ADR-0015 (corte por
   inadimplência e piso da tarifa social, classe a ou c), e não oferece configuração que as afrouxe para atender a um
   edital. O Ziip mantém modelos de **pedido de esclarecimento** e de **impugnação** (Lei 14.133/2021, art. 164) para
   esses itens. Na POC, demonstra a regra legal configurada e cita a norma. A remessa ao e-Sfinge é gerada e validada
   pelo produto e enviada pelo cliente (ADR-0028, item e), o que limita a responsabilidade do Ziip ao conteúdo da
   remessa; a transferência de multas do TCE/SC é objeto de pedido de esclarecimento.
6. **Kit de habilitação** em paralelo, fora do caminho crítico: empresa licitante definida, certidões, balanço,
   declarações, e-CNPJ, registro do programa no INPI, relatório de certificações do provedor (ADR-0011) e cláusula-padrão
   de licença das customizações (`18h` §5, E1).
7. **Revogação.** Fica revogado o item "POC, editais" da linha "Fora do escopo" do diário de bordo (§1). Os demais itens
   daquela linha (blockchain no MTR, integração de escrita com ESRI) não são alterados.

## Consequências

### Positivas

- O piloto continua prioritário, e cada fase passa a medir quanto dos roteiros já passaria; a distância até a
  primeira licitação deixa de ser estimativa.
- O ambiente de demonstração permanente elimina a corrida de montar a POC nos 5 ou 7 dias úteis.
- A estratégia de atestado começa no P0, com custo baixo (critério de escolha do piloto e cláusula contratual).
- Travas legais preservadas: o produto não corre o risco de, para pontuar, executar corte vedado ou retirar benefício
  de tarifa social.

### Negativas

- O P4 fica muito maior: além de R8, R6, BI, Pix Automático e app (ADR-0001), recebe a trilha de editais
  (ADR-0026 a ADR-0028). As matrizes estimam, só para operação, qualidade e BI, de 13 a 16 meses-pessoa (`18g` §3). O
  plano precisa sequenciar a trilha pela criticidade dos itens na POC, e parte dela pode escorregar para 2028.
- Ambiente de demonstração permanente custa hospedagem e manutenção da massa sintética a cada fase.
- Nenhuma licitação com POC do perfil analisado é disputável antes de 2028.
- Um só atestado no fim de 2027 não basta para editais como o de Videira (dois atestados, 8.500 ligações e e-Sfinge);
  o segundo depende de cliente privado ou de segundo piloto em 2028.
- Pedido de esclarecimento ou impugnação não garante que o item seja retirado; a POC pode ser avaliada pelo texto do
  edital.

### O que muda no código

- Gerador de massa sintética para o tenant de demonstração (reaproveita a carga sintética da P0.23 e do RNF-70).
- Nenhuma configuração nova para afrouxar travas do ADR-0015.

### O que muda no roadmap

- ADR-0001: o P4 ganha a trilha de editais; o "2028" passa a ser o ano-alvo das primeiras licitações com POC. Os itens
  que saem de 2028 para o P4 estão no ADR-0028; a instância dedicada, no ADR-0024.
- O critério de aceite das fases P3 e P4 passa a incluir o resultado do ensaio interno de POC.

## Alternativas consideradas

- **Manter POC e editais fora do escopo até o go-live (diário §1).** Rejeitada: o produto não mediria a distância até
  as licitações e chegaria a 2028 sem ambiente nem roteiro.
- **Reordenar o roadmap para disputar editais em 2027.** Rejeitada: desloca o go-live do piloto em vários meses e
  aumenta o risco do primeiro cliente (`18d` §4, decisão 5, opção c).
- **Consórcio com detentor de atestado como caminho principal.** Rejeitada como plano: o parceiro costuma ser
  concorrente com produto próprio, e Videira veda subcontratação e exige software de autoria própria.
- **Configuração que permita atender ao texto do edital nos itens ilegais.** Rejeitada: contraria as travas do
  ADR-0015 e expõe o cliente e o Ziip a responsabilização.
- **Incorporar empresa com acervo técnico.** Rejeitada: custo e risco societário fora do perfil do projeto.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Incluir "Santa Catarina, prestadora de água, contratação por dispensa ou CPSI e cláusula de atestado" nos critérios de escolha do piloto | P0 (P0.30) |
| Kit de habilitação e registro do programa no INPI | P0, em paralelo |
| Roteiros internos de POC (VISAN e SAMAE), com item, módulo, fase prevista e situação | P1 |
| Modelos de pedido de esclarecimento e de impugnação para os itens que contrariam a lei (TR-286, TR-415, SG-398, TR-494) | P1 |
| Ensaio interno de POC ao fim de cada fase, com resultado registrado | P1 em diante |
| Ambiente de demonstração permanente com tenant de pelo menos 10 mil unidades fictícias | P3 (antes do primeiro aceite com critério de POC) |
| Meta de prontidão de POC por fase no critério de aceite do P3 e do P4 | P3 |
| Decisão de equipe para editais (presencial, plantão, parceiro) | P4 |
| Trilha de editais sequenciada pela criticidade na POC (ADR-0026 a ADR-0028) | P4 |
