# ADR-0024 — Instância dedicada e cláusula de reversão

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0005 (isolamento por porte); ADR-0010 (infraestrutura congelada); ADR-0011 (hospedagem);
  ADR-0019 (contrato); ADR-0023 (editais); ADR-0025 (nível de serviço); `docs/arquitetura/ARQUITETURA-ALVO.md` §3 e §7
- **Base:** `docs/levantamento/editais/18h-aderencia-nao-funcionais-servicos-habilitacao.md` §3.4, §5 (E4) e §6
  (decisão 3)
- **Ajuste de 12/10/2026 (editais, v2.5), antes de qualquer implementação:** item 3 passa a prever que o pacote de
  reversão, em contêineres Linux, roda também em Windows Server 2022 ou superior, sobre Docker numa distribuição Linux
  do WSL2 (com virtualização aninhada habilitada quando o servidor for máquina virtual; forma exata do runtime
  conferida na homologação), com requisitos mínimos publicados para Linux e para Windows Server; a combinação Windows é
  homologada uma vez, na construção do pacote (ED-04), e volta a ser homologada só se um cliente a adotar na reversão;
  o SaaS continua em Linux na AWS (ADR-0011); a URA segue a integração mínima do ADR-0028 (item k), com API de consulta
  para URA de terceiros; a consequência negativa sobre Windows fica tratada por este ajuste; decisão do dono do
  produto.
- **Ajuste de 12/10/2026 (v2.7), antes de qualquer implementação:** o SaaS do item 1 roda na AWS só a partir do
  lançamento comercial (ADR-0033); a IaC parametrizada (tarefa derivada do P0) e a instância dedicada do item 2 passam
  à fase de lançamento ou depois dela (plano LC.2, ED-04); o pacote de reversão do item 3 pode ser construído antes, na
  onda B, porque o ambiente local do ADR-0033 já reúne PostgreSQL 17, Keycloak, armazenamento compatível com S3 e cofre
  próprio em contêineres; o item 4 lê-se com as exceções do ADR-0010 ajustadas na v2.7 (emulador de S3 e cofre local
  no ambiente de desenvolvimento); decisão do dono do produto.

## Contexto

Os dois editais analisados têm cláusulas que o SaaS puro não responde (`18h` §3.4):

- Videira, TR 4.4.12 (TR-067, ED-067): a qualquer tempo, por desempenho insatisfatório, a VISAN pode assumir a
  infraestrutura; a contratada detalha os requisitos, prepara os equipamentos e migra, e a proposta traz o percentual de
  dedução da mensalidade. TR 4.15 (TR-104) pede acesso administrativo da TI ao ambiente de nuvem, "sempre que
  possível".
- SAMAE, 2.2.3.4 (SG-020): licenças instaláveis em qualquer servidor físico ou virtual, sem variação de custo; 2.1.3 e
  2.2.3.3 (SG-004, SG-019): requisitos de hardware na proposta, Windows Server 2022+ e Linux com kernel 5+.

O ADR-0005 já prevê três modos de isolamento (schema compartilhado, schema próprio, banco dedicado) registrados no
catálogo, e a arquitetura-alvo usa uma imagem única de contêiner e componentes neutros quanto a provedor (ADR-0011,
item 4). Instalar fora da nuvem do Ziip tira das mãos do Ziip a atualização contínua e a medição de disponibilidade.

## Decisão

1. **Padrão: SaaS multi-tenant** na hospedagem do ADR-0011, com o isolamento por porte do ADR-0005.
2. **Instância dedicada gerenciada pelo Ziip** para o cliente que exigir em contrato: pilha própria (`api`, `worker`,
   Keycloak e banco) numa conta ou ambiente separado na mesma hospedagem, criada pela mesma IaC, com a mesma imagem e o
   mesmo pipeline de atualização. O modelo híbrido do ADR-0005 comporta esse caso como extensão do modo "banco
   dedicado". O acesso administrativo da TI do órgão (TR-104) é concedido só na instância dedicada, somente leitura
   sobre configuração e monitoramento, com MFA e auditoria; nunca em infraestrutura compartilhada.
3. **Execução na infraestrutura do órgão como cláusula contratual de reversão**, e não como produto:
   - **pacote em contêiner** com a imagem da aplicação, PostgreSQL 17 com PostGIS 3.5, Keycloak e cofre próprio, além
     de armazenamento de objetos compatível com S3 (componentes da coluna "VPS ou servidor próprio" da
     `ARQUITETURA-ALVO.md` §7, mantida como referência);
   - **requisitos mínimos** de hardware e sistema operacional publicados, para a proposta;
   - **runbook de migração** da nuvem do Ziip para o órgão, com exportação, carga, conferência e corte;
   - **percentual de desconto** sobre a mensalidade definido na tabela comercial, aplicado a partir da reversão;
   - depois da reversão, a disponibilidade e a infraestrutura deixam de ser medidas pelo Ziip; o contrato passa a
     cobrir só software, atualização (entregue como nova versão do pacote) e suporte.
4. Os componentes do pacote que o ADR-0010 congela (armazenamento compatível com S3, cofre próprio) entram só no pacote
   de reversão, registrados como exceção no ADR-0010 quando o pacote for construído.

## Consequências

### Positivas

- Responde às cláusulas TR 4.4.12, TR 4.15 e SG 2.2.3.4 sem abrir mão da operação do SaaS no caso normal.
- A instância dedicada usa a mesma IaC e o mesmo pipeline: não há bifurcação de código nem de versão.
- A neutralidade de provedor do ADR-0011 (item 4) passa a ter uma finalidade concreta.

### Negativas

- Instância dedicada custa uma pilha inteira por cliente; o preço precisa cobrir isso (tabela de custo do ADR-0011).
- O pacote de reversão precisa ser testado a cada versão relevante, mesmo sem cliente que o use, ou envelhece.
- Depois da reversão, a qualidade da operação fica com o órgão, e incidentes de infraestrutura podem ser atribuídos ao
  software. O contrato precisa separar responsabilidades.
- O SAMAE pede Windows Server 2022+; o pacote em contêiner roda em Linux. Em Windows, só por virtualização, o que
  precisa constar dos requisitos mínimos e pode gerar pedido de esclarecimento.
- Recursos da hospedagem gerenciada (KMS, Object Lock, backup gerenciado) não existem no pacote; crypto-shredding,
  retenção imutável e PITR passam a depender do cofre e do backup do órgão.

### O que muda no código

- IaC parametrizada para criar uma instância dedicada como mais um ambiente.
- Implementações de `TenantKeyProvider` e de armazenamento de objetos que não dependam da AWS, já exigidas pela
  neutralidade do ADR-0011.
- Pacote de reversão (compose ou equivalente), com teste automatizado de subida e de migração de dados.

### O que muda no roadmap

- ADR-0001 colocava "instância dedicada" em 2028; passa à trilha de editais do P4 (ADR-0023), sob demanda de contrato.

## Alternativas consideradas

- **Só SaaS, sem cláusula de reversão.** Rejeitada: obriga a deixar de disputar ou a impugnar TR 4.4.12 e SG 2.2.3.4.
- **Produto instalável de prateleira.** Rejeitada: tira a atualização contínua e a medição de disponibilidade das mãos
  do Ziip, multiplica as versões em campo e exige suporte a ambientes heterogêneos para uma equipe pequena.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| IaC de homologação e produção escrita de forma que uma instância dedicada seja mais um ambiente | P0 (com a P0.27) |
| Instância dedicada gerenciada: procedimento de criação, acesso administrativo restrito do órgão, medição de SLA própria | P4 (trilha de editais) |
| Pacote de reversão em contêiner, requisitos mínimos de hardware e sistema operacional, runbook de migração para o órgão | P4 (trilha de editais) |
| Exceção no ADR-0010 para os componentes do pacote de reversão | P4, junto do pacote |
| Percentual de desconto da reversão e cláusula contratual com a divisão de responsabilidades | P4, com a tabela comercial |
