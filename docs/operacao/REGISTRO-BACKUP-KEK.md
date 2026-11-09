# LC.4 — Registo — forma de atender ao backup da KEK

Produtor: sessao `ca6d61bf-f7f3-42dd-a535-196ff96c1069` (papel: Produtor) — bloco de conclusao do ZiipSan, 09/11/2026.
Fonte unica do aceite: `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json` (registo integral da LC.4 lido antes da producao).
Regras do bloco: sem GitHub (parada dura HTTP 429), sem AWS (sem conta neste bloco), nenhum dado inventado, nenhum segredo literal; valores sem fonte = `[A DEFINIR]`/`[SEM FONTE]` + pendencia nomeada.

Exigencia do aceite: "forma de atender ao backup da KEK **registada**". Registo (o procedimento em si executa-se na janela AWS; aqui fica a forma definida, sem valores inventados):

1. **Chaves geradas pelo KMS (modo PLATFORM, padrao):** o material da KEK nunca sai do KMS; a "copia de seguranca" e a propria durabilidade/replicacao interna do KMS na regiao `sa-east-1`, somada a: (a) exportacao versionada da **politica da chave** e dos grants para o repositorio de IaC (sem material); (b) inventario de ARNs/aliases guardado fora da conta de producao; (c) procedimento de recriacao da chave por IaC + re-envelopamento das DEKs a partir dos dados cifrados sobreviventes, ensaiado na restauracao (LC.12). Perda da regiao inteira: ver risco residual da LC.3 — nao coberto.
2. **Modo IMPORTED:** o material de chave e do tenant; o backup do material e responsabilidade registada **do tenant** (custodia fora da AWS), condicao que consta do modelo de aceite IMPORTED. Sem esse backup do tenant, a perda do material importado torna o dado ilegivel — risco aceite por escrito.
3. **Modo EXTERNAL:** a KEK vive no armazenamento externo do tenant; backup/disponibilidade sao do provedor externo do tenant, conforme o modelo de aceite EXTERNAL.
4. **Registo de execucao:** responsavel `[A DEFINIR]`, data da ultima verificacao `[A DEFINIR]`, resultado `[A DEFINIR]` — preenchidos na janela AWS (prova LC4-T6).
