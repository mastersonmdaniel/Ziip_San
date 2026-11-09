# LC.3 — Artefato — Risco residual de desastre regional (para a LC.9 / contrato do tenant / nivel Setor Publico)

Produtor: sessao `ca6d61bf-f7f3-42dd-a535-196ff96c1069` (papel: Produtor) — bloco de conclusao do ZiipSan, 09/11/2026.
Fonte unica do aceite: `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json` (registo integral da LC.3 lido antes da producao).
Regras do bloco: sem GitHub (parada dura HTTP 429), sem AWS (sem conta neste bloco), nenhum dado inventado, nenhum segredo literal; valores sem fonte = `[A DEFINIR]`/`[SEM FONTE]` + pendencia nomeada.

> Texto produzido a partir do aceite da LC.3, sem termos alem dele. Destino: contrato do tenant e nivel Setor Publico (ADR-0025), consumido pela **LC.9**. Nao e minuta contratual completa — e a declaracao do risco residual que a LC.9 incorpora.

## Declaracao de risco residual — desastre regional

1. **O que a solucao cobre.** A protecao de dados do ZiipSan em producao assenta em: RDS PostgreSQL Multi-AZ (falha de **zona** de disponibilidade), backups e snapshots com copia para **cofre logico isolado do AWS Backup, em conta distinta da producao, na mesma regiao**, com **Vault Lock em modo de conformidade** e politica de expiracao (protecao contra **ransomware** e **erro humano**, incluindo tentativa de exclusao ou alteracao da copia com credencial administrativa da producao), e restauracao point-in-time (PITR) com RPO <= 15 minutos e RTO <= 4 horas medidos (RNF-14, RNF-15).

2. **O que a solucao NAO cobre — risco residual assumido.** A solucao **nao cobre a perda da regiao inteira** (`sa-east-1`). Nenhum backup nem replica existe fora de `sa-east-1`, porque a copia de dados fora do pais continua vedada (RNF-44). Em caso de perda total e prolongada da regiao, os dados e o servico **nao sao recuperaveis por esta arquitetura** dentro dos RPO/RTO acima.

3. **Alternativas expressamente fora do desenho.** Copia em segundo provedor de nuvem e ambiente pilot-light em outra regiao ficam **fora** da solucao, pela mesma decisao que fixou o desenho acima (decisao do dono de 22/10/2026, PLT-04).

4. **Ciente do tenant.** Ao contratar (incluido o nivel **Setor Publico**, ADR-0025), o tenant recebe esta declaracao por escrito e o contrato (LC.9) a declara expressamente.

Campos que a LC.9 preenche na contratacao (nao inventados aqui): identificacao do tenant `[A DEFINIR]`, nivel de servico escolhido `[A DEFINIR]`, data e assinaturas `[A DEFINIR]`.
