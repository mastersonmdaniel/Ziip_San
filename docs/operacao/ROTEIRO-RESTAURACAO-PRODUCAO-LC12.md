# LC.12 — Roteiro de restauração da produção (PITR do RDS)

> **Produtor:** sessão `95ff17bd-dd7b-4269-a848-c235bad7f4be` (papel: Produtor).
> **Data de produção:** 09/11/2026. **Natureza:** roteiro para o evento real futuro.
> **Nenhuma restauração de produção foi executada.** Não existe produção AWS,
> não existe RDS e não existe cliente/piloto (DECISOES-DO-DONO.md, item 34:
> o produto será vendido em licitação após a conclusão). Nenhum tempo,
> contagem, checksum ou resultado é afirmado neste documento — todos nascem
> medidos na execução real e são gravados na Folha de Registo
> (`FOLHA-REGISTO-RESTAURACAO-LC12.md`, modelo em branco neste pacote).

## 0. Aceite que este roteiro cumpre (fonte única, verbatim)

Fonte: `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json`,
registro LC.12 (título "Restauração da produção", origem `17:X2; RNF-15; RNF-16`,
tamanho P, deps LC.3 e LC.10):

> "restauração da produção a partir do PITR do RDS (LC.3) dentro do RTO de
> 4 h (RNF-15), com contagem de registros, checksum e teste de RLS,
> concluída antes do corte (LC.13); é a meta que a GL.3 ensaiou"

## 1. Pré-condições — estado declarado (verificar uma a uma; qualquer NÃO = abortar)

| # | Pré-condição | Estado hoje (09/11/2026) | Evidência exigida no evento |
|---|---|---|---|
| PC-1 | LC.3 executada: RDS PostgreSQL com PITR ativo em `sa-east-1`, RPO ≤ 15 min e RTO ≤ 4 h como desenho (RNF-14/RNF-15), cofre isolado do AWS Backup | **PENDÊNCIA EXTERNA — não existe.** Nenhuma conta/ambiente AWS de produção existe; o pacote `lc.3/` deste bloco entrega configuração/roteiros, não um RDS vivo | Identificador da instância RDS, janela de PITR disponível e instante restaurável mais recente, registados na Folha |
| PC-2 | LC.10 executada: carga real do legado concluída e conciliada/assinada pelo piloto/cliente | **PENDÊNCIA EXTERNA — não existe.** Sem cliente não há dado real carregado | Referência ao registo de conciliação da LC.10 citado na Folha |
| PC-3 | Janela autorizada: restauração executada e concluída **antes** do corte LC.13, em janela combinada com o cliente | [A DEFINIR] no evento (data/hora e autorizador são do evento real) | Autorização registada na Folha (quem, quando) |
| PC-4 | Papéis nomeados (seção 2) presentes e contactáveis durante toda a janela | [A DEFINIR] no evento | Nomes/cargos registados na Folha — nenhum nome é presumido aqui |
| PC-5 | Instância de destino isolada definida (restauração PITR nasce numa instância nova; a produção corrente não é sobrescrita) | Desenho da LC.3; identificador concreto [A DEFINIR] no evento | Identificador da instância restaurada na Folha |
| PC-6 | Credenciais/segredos só via mecanismo da LC.4 (KMS/Secrets Manager) — nenhum segredo em ficheiro, log ou nesta Folha | Regra permanente | Declaração do executor na Folha |

**Consequência honesta:** hoje PC-1 e PC-2 falham por fator externo nomeado
(sem AWS, sem cliente — item 34). Este roteiro fica pronto em espera; ele
não simula a restauração nem declara a LC.12 executada.

## 2. Papéis (funções, não pessoas — nomes só na Folha, no evento)

| Papel | Responsabilidade |
|---|---|
| Executor da restauração | Executa os passos 1–6, opera o PITR do RDS, não aprova o próprio resultado |
| Verificador independente | Executa/confere os passos 7–9 (contagem, checksum, RLS) sobre a instância restaurada, sem ter executado a restauração |
| Cronometrista/registador | Marca início/fim de cada etapa com relógio único, preenche a Folha em tempo real |
| Responsável do cliente/piloto | Autoriza a janela (PC-3) e recebe o resultado; é quem decide, com o dono do produto, se a restauração serve de base ao corte LC.13 |
| Dono do produto | Aprova o encerramento da LC.12 e a passagem à LC.13 |

## 3. O que a GL.3 já ensaiou × o que só existe em produção

Ensaio de referência: pacote `gl.3/` — `docs/operacao/ROTEIRO-ENSAIO-RESTAURACAO-LOCAL-GL3.md`,
orquestrador `scripts/operacao/ensaio-restauracao-local.sh`, SQL em
`scripts/operacao/sql/` e harness com **65 verificações, 65 verdes** (stubs,
sem Docker/Postgres; o ensaio real local roda no PC do dono — ver
`RELATORIO-GL.3.md` §§2–3). A GL.3 regista tempos **sem meta**; a meta é aqui.

| Elemento | GL.3 (local, já ensaiado no desenho) | LC.12 (produção, este roteiro) |
|---|---|---|
| Origem | Backup local da P0.28 (`ziipsan.dump`) | **PITR do RDS da LC.3** — só existe em produção |
| Mecanismo | `pg_restore` em base isolada descartável | Restore point-in-time do RDS para instância nova isolada |
| Contagem + checksum | SQL `verificacao-contagem-checksum.sql` (SHA-256 canónico por tabela) — reutilizar o mesmo método/queries adaptados à instância RDS | Mesmo método; baseline = contagem/checksum da produção de origem no instante-alvo, obtidos no passo 6 |
| Teste de RLS | SQL `teste-rls-restaurado.sql` como `ziipsan_app` (sem contexto = 0; próprio = só as suas linhas; alheio = 0) | Mesmo teste, na instância restaurada, com a role de aplicação real da produção |
| Restauração seletiva de tenant + desfazer de lote (P3.33/PLT-03) | Ensaiados na GL.3 | **Fora do aceite da LC.12** — pertencem ao aceite da LC.3 sobre o PITR; não repetir aqui salvo decisão registada na Folha |
| Cronometragem | Registada contra a referência de 4 h, **sem meta** | **Com meta: RTO de 4 h (RNF-15)** — procedimento na seção 5 |
| Resultado | Registo de ensaio | Folha de Registo assinada; portão para a LC.13 |

## 4. Sequência (ordem obrigatória; abortar na primeira falha e registar)

1. **Abertura da janela.** Cronometrista regista o instante de abertura e
   confirma PC-1…PC-6. Sem todas as pré-condições, não há passo 2.
2. **Fixar o instante-alvo do PITR.** Registar o instante escolhido e o
   fundamento (ex.: último instante íntegro conhecido) — o instante é facto
   do evento, [A DEFINIR] aqui.
3. **Fotografia da origem.** Na produção de origem, correr a contagem por
   tabela e o checksum canónico (método da GL.3) referidos ao instante-alvo
   quando tecnicamente possível; guardar o artefacto (TSV) como baseline.
   Se a fotografia no instante exato não for possível, registar o desvio e a
   fotografia substituta usada — nunca inventar a baseline.
4. **Disparar o PITR.** Executor restaura o RDS para o instante-alvo, numa
   instância nova isolada (PC-5). **O relógio do RTO arranca aqui** (seção 5).
5. **Subida e sanidade.** Instância restaurada acessível; versão/extensões
   conferidas contra o esperado da LC.3 (PostGIS, `pg_trgm`, `unaccent`,
   `pg_partman` — lista do aceite da LC.1/LC.3); aplicação ainda **não**
   apontada para a instância restaurada.
6. **Contagem de registros (verificação do aceite).** Verificador corre a
   contagem por tabela na restaurada e compara com a baseline do passo 3.
   Evidência: TSV das duas contagens + divergências explicadas uma a uma.
   Divergência não explicada = restauração **não** concluída.
7. **Checksum (verificação do aceite).** Verificador corre o checksum
   canónico por tabela (método GL.3) na restaurada e compara com a baseline.
   Evidência: TSV + hash global de cada lado. Divergência não explicada =
   restauração **não** concluída.
8. **Teste de RLS (verificação do aceite).** Verificador corre o teste de
   RLS como role de aplicação (nunca superutilizador/`BYPASSRLS`): sem
   contexto = 0 linhas; contexto de um tenant = só as linhas dele; contexto
   alheio = 0. Resultado só pode ser OK para concluir; `INCONCLUSIVO`
   (ex.: sem massa) reprova, como na GL.3. Evidência: artefacto do teste.
9. **Fecho do relógio e veredito.** Cronometrista fecha o tempo total
   (seção 5). Verificador e executor assinam a Folha; dono do produto e
   responsável do cliente registam o veredito: restauração concluída dentro
   ou fora do RTO, apta ou não como base da LC.13.
10. **Destino da instância restaurada.** Registar o destino decidido
    (promovida, mantida para inspeção ou eliminada) — decisão do evento,
    [A DEFINIR] aqui. Nada é eliminado antes do veredito do passo 9.

**Volta/abortamento:** falhando qualquer passo 4–8, a produção corrente não
foi tocada (a restauração nasce isolada) — abortar é registar a falha na
Folha, preservar os artefactos e não avançar para a LC.13. Não há "volta"
sobre a produção porque nenhuma escrita nela acontece neste roteiro.

## 5. Cronometragem contra o RTO de 4 h (RNF-15) — procedimento de medição

- **Arranque (T0):** instante do disparo do PITR (passo 4), registado pelo
  cronometrista com relógio único e fuso declarado na Folha.
- **Paragens intermédias:** fim da subida (passo 5), fim de cada verificação
  (passos 6, 7, 8) — duração por etapa e acumulada.
- **Fecho (T1):** instante do veredito do passo 9. **Tempo do RTO = T1 − T0.**
- **Meta:** T1 − T0 ≤ 4 h (14 400 s). O resultado é o tempo **medido** no
  evento; este roteiro não afirma tempo nenhum.
- Fora do RTO não se apaga nem se reescreve: regista-se o tempo real, a
  etapa que consumiu o excesso e o veredito passa a ser decisão expressa do
  dono do produto e do cliente antes de qualquer LC.13.

## 6. Evidências que têm de existir ao fechar

Folha de Registo preenchida e assinada; TSV de contagem (origem × restaurada);
TSV de checksum (origem × restaurada) com hashes globais; artefacto do teste
de RLS; registo de tempos por etapa e total × RTO. Sem qualquer uma, a LC.12
não se declara concluída.
