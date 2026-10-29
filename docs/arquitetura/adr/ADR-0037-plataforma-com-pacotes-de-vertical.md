# ADR-0037 — Plataforma Ziip com pacotes de vertical

- **Status:** Aceito, 14/10/2026
- **Decisor:** dono do produto (cartão de 14/10/2026)
- **Afeta:** ADR-0001 (fases); ADR-0002 (parâmetros por tenant); ADR-0016 e ADR-0017 (marca e temas); ADR-0018
  (Central Ziip); ADR-0020 e ADR-0021 (ajuda, suporte, Sobre); ADR-0034 (GIS: domínios e catálogo); ADR-0036 (ícones e
  símbolos); `docs/arquitetura/PLATAFORMA-E-VERTICAIS.md`; plano v2.13 (P0.47, P1.26, §2.4, §3)
- **Ajuste de 28/10/2026 (plano v3.0):** o motor de OS fica no núcleo e os tipos e alvos de saneamento no pacote
  `sanitation`, conforme o ADR-0045.

## Contexto

O dono do produto indicou que o GIS e a plataforma poderão atender outras verticais, como GIS para saúde e para
secretarias de segurança pública, além do saneamento. Hoje o código e o plano assumem saneamento em todo lugar: o
`domain` do GIS é uma lista fixa (`WATER`, `SEWER`, `STORM`, `WASTE`, `CONTEXT`), o menu e as telas são de
saneamento e os módulos de negócio (faturamento, medição, arrecadação) pressupõem consumo de água. Uma segunda vertical
construída depois, sem preparo, exigiria desmembrar o que se mistura.

Não há requisitos, normas, editais nem dado para saúde ou segurança pública. Este ADR trata só da **forma** que permite
a extensão, não do conteúdo dessas verticais.

## Decisão

1. **Uma plataforma, várias verticais.** O núcleo da plataforma (tenant, autenticação, papéis, parâmetros, marca e
   temas, Central Ziip, ajuda e suporte, Sobre, auditoria, lote, notificações, GIS com catálogo, camadas, topologia
   opcional e pacotes de edição) é genérico. Cada vertical é um **pacote** com o que é específico dela.
2. **Saneamento é o primeiro pacote** (`sanitation`) e é o único com escopo, fases e datas neste plano. Saúde e
   segurança pública não têm escopo, tarefa nem data; entram por decisão futura, com ADR próprio.
3. **Conteúdo de um pacote** (manifesto `vertical.json`): identificação e versão; versão mínima da plataforma;
   domínios do GIS e catálogos de fábrica; símbolos e ícones (ADR-0036); módulos de negócio habilitáveis e itens de
   menu; definições de parâmetros do tenant; papéis e permissões; conteúdo de ajuda e textos; relatórios e camadas
   regulatórias; migrações Flyway do próprio pacote (schemas e faixa de versões do pacote).
4. **Tenant ativa pacotes.** O tenant tem um ou mais pacotes ativos (`platform.tenant_vertical`); o menu, os parâmetros,
   as permissões e o catálogo do GIS aparecem conforme os pacotes ativos. Pacote ativado não se desativa se houver
   dados dele no tenant; só se arquiva.
5. **Fronteira verificada por teste.** O núcleo não depende de classe, tabela nem texto de um pacote; o teste de
   arquitetura (ArchUnit e verificação de dependência de módulos) falha o build se depender. Termo de domínio de
   saneamento no núcleo (por exemplo `hidrômetro`, `ligação de água`) é recusado pelo mesmo teste, com lista de exceções
   versionada.
6. **Domínio do GIS por referência, não por CHECK.** A tabela `gis_domain` (chave, pacote, rótulo, cor, ordem), criada
   na `V13_1`, substitui a lista fixa do `gis_feature_class.domain`; os cinco domínios atuais são os do pacote de
   saneamento. É o único ajuste de modelo exigido agora, porque a `V13_1` ainda não foi escrita.
7. **Marca e nome.** A marca resolvida pelo `BrandProvider` (ADR-0016) continua única; o pacote de vertical fornece o
   nome da vertical e os textos próprios. O nome comercial de cada vertical e a relação com "Ziip SAN" são decisão do
   dono, em aberto (plano §2.4).
8. **Dados de saúde e de segurança pública não entram sem análise própria.** Dado de saúde é dado pessoal sensível
   (LGPD, art. 11) e o tratamento para segurança pública segue regime legal diverso; cada um exige ADR, avaliação
   jurídica e classe de dados nova antes de qualquer requisito ou coluna.
9. **Sem refatoração antecipada.** Nada além dos itens 5 e 6 e da estrutura de pastas do manifesto é construído agora;
   módulos de saneamento já planejados continuam como estão, só rotulados como pertencentes ao pacote `sanitation`.

## Consequências

### Positivas

- A segunda vertical entra como pacote, sem reescrever o núcleo.
- A fronteira vira teste, não intenção.
- O GIS sai de uma lista fixa de domínios sem custo relevante, porque ainda não há migração escrita.

### Negativas

- Mais disciplina de módulos desde o P0: o teste de fronteira pode recusar atalhos.
- O núcleo precisa de nomes genéricos (por exemplo "ativo de rede" e "ponto de atendimento" no lugar de "hidrômetro").
- Risco de generalização prematura: por isso o item 9 limita o que se faz agora.

## Alternativas consideradas

- Produtos separados por vertical: duplica manutenção de tenant, autenticação, Central e GIS.
- Só saneamento agora, generalizar depois: retrabalho no modelo de dados do GIS e nos nomes do núcleo, que ainda estão
  por escrever; o custo de preparar agora é pequeno.
- Generalizar tudo já (faturamento, medição, arrecadação): sem demanda nem requisito, risco alto.

## Tarefas derivadas

- P0.47: contrato do manifesto, registro de pacotes, `platform.vertical` e `platform.tenant_vertical`, pacote
  `sanitation` mínimo e teste de fronteira.
- P1.26: `gis_domain` no lugar da lista fixa de domínio (ajuste do aceite da GIS-01).
- Pendência §2.4: nome comercial das verticais.
