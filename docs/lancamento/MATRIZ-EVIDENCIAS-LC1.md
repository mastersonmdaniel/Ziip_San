# LC.1 — Matriz de evidências (aceite → evidência → onde → quem)

- **Pacote:** LC.1 — Ensaio de implantação na AWS com dado sintético
- **Produtor:** sessão `a1981a33-6f88-49c6-8f6f-f9d70266f27b` (papel: Produtor)
- **Uso:** é o índice do dossiê do ensaio. Na execução, cada linha é
  preenchida com a referência real do artefacto (ficheiro/registo) e a data;
  linha sem referência = item **não cumprido** (ver passo 5.2 do roteiro).
- **Quem produz:** papéis — Executor do ensaio (E), Conferente independente
  (C), Dono/projeto (D). Nomes concretos: **[A DEFINIR]** (P-LC1-06).
  Executor ≠ conferente em todas as linhas.

| Item | Aceite (resumo) | Evidência exigida | Onde se produz | Quem |
|---|---|---|---|---|
| A0 | Só dado sintético | Varredura de dado pessoal real verde antes e depois da carga (passo 1.3) + registo da massa com marca de sintético | Dossiê do ensaio, Fase 1 | E produz, C confere |
| A1 | Fases P0–P3/GL/P4 + ondas A, B1, **B2**, C concluídas (excl. P4.6, ED-68, ED-102, ED-103, ED-104) | Checklist de conclusão tarefa a tarefa com fonte por estado e exclusões explícitas (passo 0.1) | Dossiê, Fase 0 | E produz, C confere |
| A2 | Aceite GL.4 concluído | Decisão formal da GL.4 (documento datado) + relatório do paralelo GL.2 e ensaio GL.3 anexos (passo 0.2) | Processo da GL.4 → dossiê, Fase 0 | D decide; E anexa |
| A3 | Prontidão de POC (ED-01) concluída | Registo de prontidão PD-01 com veredito (passo 0.3) | Processo da ED-01 → dossiê, Fase 0 | E anexa, C confere |
| A4 | B2 conta na LC.1; LC.15 fora do caminho | Linhas do B2 **dentro** do checklist do passo 0.1 e declaração de exclusão da LC.15 no mesmo checklist | Dossiê, Fase 0 | C confere |
| A5 | Conta de ensaio em `sa-east-1` | Identificação da conta de ensaio, região, alerta de custo e orçamento ativos (passo 0.4) | Conta AWS de ensaio | D autoriza/cria; E regista |
| A6 | IaC aplicada do zero | Plano contra ambiente vazio (0.5) + log de aplicação + inventário = plano (1.1) + tempo medido | Dossiê, Fases 0–1 | E produz, C confere |
| A7a | Contrato de chaves verde contra KMS | Placar integral da suíte de chaves alvo AWS (passo 2.1) | Pipeline do ensaio, Fase 2 | E produz, C confere |
| A7b | Contrato de segredos verde contra Secrets Manager | Placar integral da suíte de segredos alvo AWS (passo 2.2) | Pipeline do ensaio, Fase 2 | E produz, C confere |
| A7c | Contrato de objetos verde contra S3 com Object Lock | Placar da suíte + registo das recusas de sobrescrita e de exclusão (passo 2.3) | Pipeline do ensaio, Fase 2 | E produz, C confere |
| A7d | Contrato de e-mail verde contra o serviço de e-mail | Placar da suíte + identificador da mensagem de ensaio no provedor (passo 2.4) | Pipeline do ensaio, Fase 2 | E produz, C confere |
| A8 | Fumaça + carga curta comparados com a linha de base P0.27 | Linha de base local arquivada (0.6) + placar de fumaça AWS (3.1) + métricas de carga AWS (3.2) + tabela comparativa métrica a métrica (3.3) | Dossiê, Fases 0 e 3 | E produz, C confere |
| A9 | Versões PostGIS, `pg_trgm`, `unaccent`, `pg_partman` conferidas no RDS | Saída integral das consultas de versão/extensões na instância de ensaio, com data/hora (passo 4.1) | Dossiê, Fase 4 | E produz, C confere |
| A10 | Diferenças local × AWS registadas, cada uma com correção ou tarefa | Registo de diferenças completo no modelo próprio; zero linha sem correção/tarefa (passo 5.1) | Dossiê, Fase 5 | E produz, C confere |
| A11 | Ambiente destruído, sem segredo nem dado remanescente | Log de destruição (6.1) + varredura independente pós-destruição (6.2) + janelas de recuperação listadas com data final, se houver | Conta AWS de ensaio + dossiê, Fase 6 | E destrói, **C varre** |
| — | Veredito e custo do ensaio | Termo de veredito A0–A11 (5.2) + custo total apurado da conta (6.3) | Dossiê, Fases 5–6 | E + C assinam |

## Regras de leitura da matriz

1. Evidência produzida pelo próprio executor sem conferência não fecha item.
2. Placar parcial (ex.: 3 de 4 suítes verdes) fecha só as linhas verdes; as
   demais constam **não cumpridas** — a matriz não tem linha "A7 parcial".
3. A varredura do A11 é feita pelo conferente, fora da IaC: quem destruiu
   não é quem prova que não restou nada.
