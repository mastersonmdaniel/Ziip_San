# ADR-0027 — BI híbrido com Apache Superset

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0005 (isolamento por tenant); ADR-0010 (exceção registrada); ADR-0011 (réplica de leitura no RDS);
  ADR-0016 (marca do produto); ADR-0023 (editais); ADR-0025 (RNF-71); `docs/arquitetura/ARQUITETURA-ALVO.md` §1; plano
  P4.3 (BI e prestação de contas em réplica)
- **Base:** `docs/levantamento/editais/18g-aderencia-operacao-qualidade-bi.md` §1.5, §3 (E-REL, E-BI, E-PAIN) e §4
  (D1, D5)

## Contexto

Relatórios e BI concentram a maior parte da área de operação dos editais: 172 linhas de relatórios e 222 de BI
(plataforma, indicadores e sete painéis padrão), quase todas não atendidas (`18g` §1.4). Nenhuma tarefa do P0 ao P3
entrega relatório operacional; a P4.3 ("BI e prestação de contas em réplica", tamanho M) só move relatórios para a
réplica no segundo semestre de 2027. O go-live do piloto aconteceria sem relatório de arrecadação, faturamento ou
leitura (`18g` §3, contradição 7).

Os itens de BI do SAMAE (2.29.18.x) descrevem uma ferramenta de mercado item a item (gerador ad hoc, cubos, OLE DB,
uso offline, simulação em área particular, apps móveis). Videira exige software de autoria própria e veda
subcontratação (ED-001, ED-024). Construir tudo passa de seis meses-pessoa; embarcar ferramenta comercial (Qlik OEM,
Power BI Embedded) custa por usuário e pode ser lido como revenda de terceiro.

## Decisão

1. **Relatórios e painéis padrão nativos**, no produto:
   - núcleo de relatórios declarativos sobre views, com filtros, agrupamento, exportação em PDF, XLSX e CSV, agendamento
     e permissão por papel;
   - **cerca de 20 relatórios operacionais já no P3**, antes do faturamento paralelo (arrecadação diária, pagamentos não
     identificados e em duplicidade, faturamento por categoria, leituras retidas, ligações por situação e afins);
   - catálogo restante e os sete painéis padrão (arrecadação, atendimento, cobrança, faturamento, medição, operacional,
     indicadores estratégicos) na trilha de editais do P4.
2. **Apache Superset (licença Apache 2.0) embarcado**, com a marca do produto (ADR-0016), para consultas livres,
   modelo semântico e cubos. Lê a **réplica de leitura** (ADR-0011), nunca o primário. Login pelo Keycloak (SSO), sem
   cadastro próprio de usuário.
3. **Isolamento por tenant no banco, pela identidade da conexão** (opção segura; ver seção seguinte): cada tenant
   tem uma role de login própria para o BI, e a restrição de linhas é feita pelo PostgreSQL com base nessa role. Os
   filtros de linha do próprio Superset e os datasets filtrados são segunda camada, nunca a única.
4. **"Autoria própria" do edital** é respondida assim: relatórios, painéis padrão, indicadores e a integração são do
   Ziip SAN; o Superset é componente de software integrado ao produto, sob licença aberta, operado pelo Ziip, e não
   serviço subcontratado. Itens sem equivalente (uso offline, OLE DB, simulação em área particular) viram pedido de
   esclarecimento (ADR-0023).
5. **Exceção ao ADR-0010:** o Superset é componente novo com caso concreto (requisitos de BI dos editais que o
   PostgreSQL e os relatórios nativos não atendem), registrado na seção "Exceções" daquele ADR. Roda em modo síncrono,
   com metadados no PostgreSQL; Redis e Celery (consultas assíncronas e cache distribuído) só entram por nova exceção.

## Isolamento por tenant: opções e a escolhida

A RLS das tabelas de tenant depende da GUC `app.current_tenant`, aplicada pela aplicação no início de cada transação.
Numa conexão direta de ferramenta de BI, quem escreve a consulta controla a sessão e pode executar
`SET app.current_tenant = '<outro tenant>'`. Por isso a GUC **não** serve de fronteira para o BI.

| Opção | Onde o filtro é aplicado | Avaliação |
|---|---|---|
| a) Filtro de linha do Superset (RLS da ferramenta, cláusula no guest token do painel embarcado) | Na aplicação Superset | Insuficiente sozinha: erro de configuração ou consulta livre (SQL Lab) atravessa tenants |
| b) Dataset ou view filtrada por parâmetro de sessão | No banco, mas pela GUC | Insegura: a sessão altera a GUC |
| **c) Role de login por tenant, com restrição no banco pela identidade (`current_user`)** | No PostgreSQL | **Escolhida:** a role não pode trocar de identidade; o filtro vale para qualquer consulta, inclusive livre |

Forma da opção c:

- uma role de login por tenant (por exemplo, `ziipsan_bi_<tenant>`), sem BYPASSRLS, sem `CREATE`, sem pertencer a outra
  role de tenant, com acesso só de leitura a views de relatório;
- as views (ou políticas RLS próprias para essas roles) filtram por `tenant_id` a partir de uma tabela de mapeamento
  role → tenant que só o `migrate` escreve; em schema próprio por tenant, a role só recebe `USAGE` no schema do próprio
  tenant;
- no Superset, uma conexão de banco por tenant, usando a role do tenant; o usuário do tenant só enxerga a conexão do
  próprio tenant, e o filtro de linha do Superset fica como segunda camada;
- `statement_timeout`, limite de conexões por role e consultas só na réplica, para que um tenant não degrade os demais
  nem atrase a replicação;
- roles e views são criadas por migração no primário e chegam à réplica pela replicação física;
- teste de isolamento no CI: a role de um tenant, com consulta livre e tentativa de `SET` da GUC, não lê linha de
  outro tenant, em schema próprio e em schema compartilhado (ADR-0005).

## Consequências

### Positivas

- O piloto opera com relatórios desde o go-live, e o que a POC avalia (relatórios e painéis padrão) fica sob controle
  do produto.
- O ad hoc e os cubos chegam sem meses de desenvolvimento e sem custo de licença por usuário.
- O isolamento do BI não depende de configuração correta da ferramenta.

### Negativas

- Segunda pilha (Python) a operar, atualizar e incluir na varredura de vulnerabilidades e no pentest.
- Uma role e uma conexão por tenant no Superset: provisionamento e rotação de credenciais por tenant (Secrets Manager,
  ADR-0011).
- O modo síncrono limita consultas longas; o `statement_timeout` corta o que passar do limite.
- A leitura de "autoria própria" com componente aberto pode ser contestada por concorrente ou pela comissão.
- Relatórios nativos no P3 acrescentam trabalho a uma fase sem folga; o núcleo precisa ficar no tamanho mínimo.

### O que muda no código

- Módulo de relatórios declarativos com exportação PDF, XLSX e CSV e agendamento pelo `worker` (outbox, ADR-0014).
- Views de relatório por domínio; roles de BI por tenant, tabela de mapeamento e políticas; teste de isolamento.
- Contêiner do Superset com tema da marca, SSO pelo Keycloak e painéis embarcados no frontend.
- Provisionamento do tenant (`ARQUITETURA-ALVO.md` §2, item 7) cria a role e a conexão de BI.

### O que muda no roadmap

- P3: núcleo de relatórios e cerca de 20 relatórios operacionais, na réplica de leitura da P0.28 quando disponível; sem
  ela, no primário com `statement_timeout`.
- P4: a P4.3 deixa de ser M e passa a incluir Superset, catálogo restante de relatórios e os sete painéis (trilha de
  editais).

## Alternativas consideradas

- **BI inteiramente próprio.** Rejeitada: mais de seis meses-pessoa para OLAP, ETL e mobile, em competição com o
  go-live.
- **Ferramenta comercial embarcada (Qlik OEM, Power BI Embedded).** Rejeitada: custo por usuário ou capacidade,
  contrato de OEM e risco de ser lida como revenda de terceiro (ED-001).
- **Isolamento só pelo filtro de linha do Superset.** Rejeitada: a fronteira entre tenants ficaria na configuração da
  ferramenta, e a consulta livre a atravessaria.
- **Usuário de banco compartilhado com GUC por sessão.** Rejeitada: a sessão controla a GUC.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Núcleo de relatórios declarativos, exportação e agendamento | P3 |
| Cerca de 20 relatórios operacionais, medidos contra o RNF-71 | P3, antes do faturamento paralelo |
| Roles de BI por tenant, mapeamento role → tenant, views de relatório e teste de isolamento no CI | P4 |
| Superset com marca do produto, SSO pelo Keycloak, conexão por tenant na réplica, painéis embarcados | P4 (amplia a P4.3) |
| Catálogo restante de relatórios e sete painéis padrão | P4 (trilha de editais) |
| Pedido de esclarecimento-modelo para itens de BI sem equivalente (offline, OLE DB, simulação) | P4, com os modelos do ADR-0023 |
