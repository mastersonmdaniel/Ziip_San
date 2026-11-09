# LC.4 — Roteiro de provas na AWS — KMS e Secrets Manager

Produtor: sessao `ca6d61bf-f7f3-42dd-a535-196ff96c1069` (papel: Produtor) — bloco de conclusao do ZiipSan, 09/11/2026.
Fonte unica do aceite: `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json` (registo integral da LC.4 lido antes da producao).
Regras do bloco: sem GitHub (parada dura HTTP 429), sem AWS (sem conta neste bloco), nenhum dado inventado, nenhum segredo literal; valores sem fonte = `[A DEFINIR]`/`[SEM FONTE]` + pendencia nomeada.

TODA prova deste roteiro esta **nao executada — exige AWS**, item a item, sem excecao.

| ID | Exigencia do aceite | Procedimento executavel na AWS | Evidencia esperada | Estado |
|---|---|---|---|---|
| LC4-T1 | `TenantKeyProviderContract` verde sobre o KMS | Correr a suite de contrato da P0.28 apontada ao KMS real (endpoint/ARNs das chaves deste modulo) no ambiente AWS; guardar o relatorio da suite. | Suite 100% verde com log e ARNs das chaves usadas. | nao executado — exige AWS |
| LC4-T2 | DEK por tenant gerada pelo KMS + rotacao | Para um tenant PLATFORM: `GenerateDataKey` na sua chave, cifrar/decifrar dado de prova; conferir `enable_key_rotation=true` e, apos rotacao (automatica/agendada na janela), decifrar DEK antiga com sucesso. | Logs de GenerateDataKey/Decrypt + estado da chave antes/depois. | nao executado — exige AWS |
| LC4-T3 | Segredos de producao no Secrets Manager SEM reaproveitar segredo/chave do cofre local | Inventariar segredos criados; conferir por amostragem que nenhum valor/ARN/nome coincide com o inventario do cofre local (lista do cofre local fornecida pela operacao, [SEM FONTE] neste pacote); tentar localizar referencia cruzada no state/IaC. | Inventario comparado assinado; 0 coincidencias. | nao executado — exige AWS |
| LC4-T4 | Revogacao da chave do cliente -> dado do tenant ilegivel (crypto-shredding, RNF-43), sem afetar demais tenants | Num tenant de prova: cifrar dado com a sua DEK; `aws kms disable-key` (e agendar exclusao, `ScheduleKeyDeletion`) na chave DESSE tenant; tentar decifrar -> deve falhar; decifrar dado de OUTRO tenant -> deve funcionar. | Erro de decifragem do tenant revogado + sucesso nos demais, com request IDs. | nao executado — exige AWS |
| LC4-T5 | Modos IMPORTED/EXTERNAL (PLT-41) sob contrato, com aceite registado | Com um tenant de prova e o modelo de aceite assinado (entrega): criar chave IMPORTED, importar material (`GetParametersForImport`/`ImportKeyMaterial`), cifrar/decifrar; repetir o essencial no EXTERNAL via XKS; registar as limitacoes (sem rotacao automatica; EXTERNAL sem multirregiao nem material importado). | Aceite arquivado + logs; limitacoes conferidas no console/API. | nao executado — exige AWS |
| LC4-T6 | Forma de atender ao backup da KEK registada | Conferir na janela que o procedimento de `REGISTRO-BACKUP-KEK.md` foi executado/validado para a chave da plataforma e uma de tenant PLATFORM. | Registo datado com responsavel e resultado. | nao executado — exige AWS |
