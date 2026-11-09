# LC.5 — Roteiro de provas na AWS — S3 com Object Lock

Produtor: sessao `ca6d61bf-f7f3-42dd-a535-196ff96c1069` (papel: Produtor) — bloco de conclusao do ZiipSan, 09/11/2026.
Fonte unica do aceite: `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json` (registo integral da LC.5 lido antes da producao).
Regras do bloco: sem GitHub (parada dura HTTP 429), sem AWS (sem conta neste bloco), nenhum dado inventado, nenhum segredo literal; valores sem fonte = `[A DEFINIR]`/`[SEM FONTE]` + pendencia nomeada.

TODA prova deste roteiro esta **nao executada — exige AWS**, item a item, sem excecao.

| ID | Exigencia do aceite | Procedimento executavel na AWS | Evidencia esperada | Estado |
|---|---|---|---|---|
| LC5-T1 | Suite de contrato do armazenamento da P0.45 verde contra o S3 | Correr a suite de contrato da P0.45 apontada ao bucket deste modulo (endpoint S3 sa-east-1), cobrindo put/get/list por prefixo de tenant. | Suite 100% verde com log e bucket/regiao registados. | nao executado — exige AWS |
| LC5-T2 | Apagar ou sobrescrever XML retido recusado pelo Object Lock | Enviar um XML de prova para `tenant/<tenant>/...` com retencao ativa; tentar (a) `DeleteObject`, (b) `DeleteObjectVersion` com version ID, (c) sobrescrever a mesma chave — com credencial administrativa do bucket. | As tres tentativas RECUSADAS pelo Object Lock (erros S3 anexados); objeto intacto ao fim (hash confere). | nao executado — exige AWS |
| LC5-T3 | Prefixo e chave por tenant | Com a identidade/role do tenant A: escrever no prefixo A (ok) e tentar escrever/ler no prefixo do tenant B (recusado pela policy de prefixo). | Sucesso no proprio prefixo; AccessDenied no prefixo alheio, com request IDs. | nao executado — exige AWS |
