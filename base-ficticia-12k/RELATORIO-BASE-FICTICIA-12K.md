# Relatório — Base Fictícia de Testes (12.000 UCs)

Produtor: sessão produtora do pacote base-ficticia-12k (09/11/2026)

- **Decisão de origem:** dono nº 34 (09/11/2026 00:19 −03) — P0.30 sem piloto
  real; base 100% fictícia com 12.000 UCs, limpeza posterior obrigatória.
- **HEAD da main observado no início:** `82bad7cee2d2ac24a43ae8fa2f2d58a0d5753a79`
  (via conector GitHub, `list_commits`, 1 chamada, repo
  `mastersonmdaniel/Ziip_San`) — **base a re-verificar pelo Portão no push**.
- **Papel deste pacote:** somente produção. Revisão e confirmação são de
  agentes distintos, despachados depois pelo coordenador. **Nenhum push foi
  feito.**

## O que foi feito, item a item

1. **Matriz de situações** (`MATRIZ-SITUACOES.md` + `matriz.csv`): 23
   situações coerentes combinando status da UC (ACTIVE/PROVISIONAL — CHECK
   V3_1) × categoria (catálogo V3_1) × situação de água (catálogo V3_1 com
   flags) × tarifa social (V4_2) × hidrômetro (V5_1) × leitura/consumo
   (V5_1) × conta (V4_1; "vencida" = NORMAL com due_date vencida, pois não
   há status de vencida no domínio) × débito (V4_1) × parcelamento (DDL
   pendente P4.1) × OS (V9_1). **Soma mecânica = 12.000** (assert no
   gerador + `awk` sobre `matriz.csv`).
2. **Gerador determinístico** (`gerar-base-ficticia.py`): Python 3, só
   stdlib, `random.Random(20261109)`, UUIDs determinísticos por md5.
   Determinismo provado: 2 gerações → **309 arquivos com SHA-256 idênticos**
   (log em `harness-logs/determinismo.log`).
3. **Carga** (`00-tenant-ficticio.sql` + 307 × `carga-NNNN.sql`): tenant
   fictício dedicado `11111111-2222-4333-8444-555555555555`
   ("Prestadora Fictícia de Testes — Município Fictício de Teste"), COPY
   FROM STDIN, aviso de seed fictício no topo de cada arquivo, cada arquivo
   ≤ 110 KB (maior: 99.964 bytes). Total da carga: 23.541.654 bytes —
   **acima da meta de ~10 MB**: desvio declarado (ver Pendências), porque as
   123.096 linhas em 32 tabelas carregam `tenant_id` + chaves UUID por
   linha; reduzir o total exigiria cortar situações/linhas, o que a tarefa
   proíbe. Nenhum arquivo está em `db/migration` nem usa padrão `V*`.
4. **Limpeza exata** (`limpeza-base-ficticia.sql`): DELETEs filtrados só
   pelo tenant fictício, em ordem segura para as FKs, + remoção da linha do
   tenant; cabeçalho declara que só toca no tenant fictício.
5. **Fixture de prova** (`trabalho/montar-fixture.py` → `trabalho/fixture.sql`):
   DDL derivada das migrations reais nomeadas, só das 33 tabelas tocadas
   (32 da massa + `tenant_catalog`), com fontes e 5 adaptações declaradas
   no cabeçalho do script (schema único, sem RLS/GRANTs, sem EXCLUDE gist,
   sem colunas GEOMETRY, sem tabelas-filhas não tocadas). Fixture é fixture:
   declarada como derivada, nunca migration.
6. **Provas em banco descartável** (`prova-carga-limpeza.sh`, PostgreSQL 16
   local, cluster em `/tmp/bf12k-pg`, porta 55441, derrubado ao fim):
   sentinela criada **antes** da carga; carga completa sem erro; limpeza
   executada. **Placar: 7/7** (log integral em `harness-logs/prova-2.log`;
   `prova-1.log` guarda a 1ª tentativa, que falhou só por permissão de
   leitura do usuário postgres — corrigida com staging legível no próprio
   harness, sem afrouxar nenhuma asserção):
   1. UCs do tenant fictício = 12.000 ✓
   2. Distribuição por situação = `matriz.csv` (23/23 situações) ✓
   3. Órfãos por FK = 0 ✓
   4. Sentinela intacta após a carga ✓
   5. 0 linhas do tenant fictício nas 32 tabelas tocadas após limpeza ✓
   6. Linha do tenant removida de `tenant_catalog` ✓
   7. Sentinela com contagens e valores idênticos após a limpeza ✓

## Totais da massa

| Métrica | Valor |
|---|---:|
| Arquivos de carga | 307 (+ `00-tenant-ficticio.sql`) |
| Bytes da carga | 23.541.654 (maior arquivo: 99.964) |
| Linhas geradas (todas as tabelas) | 123.096 |
| UCs | 12.000 |
| Titulares / vínculos / subcategorias | 12.000 / 12.000 / 12.000 |
| Endereços | 2.000 (compartilhados, 1 a cada 6 UCs) |
| Hidrômetros / instalações | 10.450 / 10.450 |
| Leituras / consumos | 9.150 / 11.300 |
| Contas / itens | 11.300 / 11.300 |
| Débitos | 2.550 |
| Enquadramentos tarifa social | 1.250 |
| Parcelamentos / parcelas | 400 / 1.200 |
| Ordens de serviço / alvos | 1.850 / 1.850 |

## Guardas

- **Migrations: NÃO SE APLICA** — este pacote não toca nenhuma migration;
  declarado expressamente. A massa vive em `seed/base-ficticia/`, fora de
  `db/migration`, sem nenhum nome no padrão `V*`.
- **Calculador ED-19: NÃO SE APLICA** — nenhum código de cálculo foi tocado;
  os valores de conta do seed são massa de teste, não saída do calculador.

## Pendências nomeadas

- **P-BF-01** — Na integração com tenant totalmente migrado, a V3_12 renomeia
  `consumer_units.matricula/dv` para `registration_number/check_digit`
  (fixture/carga seguem a era V3_1 nomeada na tarefa): renomear as 2 colunas
  na carga ou gerar variante V3_12 do gerador.
- **P-BF-02** — `social_tariff_enrollments.service_order_id`: carga segue a
  V4_2 (anulável); a V4_13 o torna NOT NULL — na integração, vincular OS aos
  1.250 enquadramentos ou gerar a OS no seed.
- **P-BF-03** — Total da carga (23,5 MB) acima da meta de ~10 MB do brief,
  com o teto por arquivo (≤ ~110 KB) respeitado: aceitar o total ou reduzir
  linhas por UC em decisão posterior (não foi cortada nenhuma situação).
- **P-BF-04** — Parcelamento usa o DDL **pendente** da P4.1 (sem número de
  migration): se a integração der outra forma às tabelas, regenerar a fatia
  de parcelamento (400 acordos) a partir da fonte final.

## Declarações

- Nenhum dado real: nenhum nome de pessoa/empresa real, nenhum documento
  (CPF/CNPJ omitidos — o DDL permite nulo), nenhum endereço real.
- Nenhuma migration tocada ou criada; `DECISOES-DO-DONO.md` e
  `portao-deploy.py` não foram editados.
- Nenhuma asserção afrouxada: a única correção de prova foi operacional
  (staging legível para o usuário postgres), registrada em log.
- Nenhum push feito — pouso depende de revisão + confirmação independentes
  e do Portão (ver `LANDING.md`).

---

## ADENDO — 09/11/2026 (lote R7-B, correção do achado R7-I-m1, menor, confirmado)

Corretor do lote R7-B (sessão `b6cf0c91-9a27-46d5-a359-d4a9ddcaecb9`).
Nenhuma linha anterior deste relatório foi alterada; este adendo apenas
supersede os números pré-complemento abaixo. O complemento EMASA
(`RELATORIO-COMPLEMENTO-EMASA.md`) é a fonte dos valores corretos.

**Valores pré-complemento que ficam supersededos neste relatório:**

- "123.096 linhas" (linhas 34 e 67) e "23.541.654 bytes" / "maior arquivo:
  99.964 bytes" (linhas 32 e 66);
- nome de tenant "Prestadora Fictícia de Testes — Município Fictício de
  Teste" (linha 30).

**Valores corretos pós-complemento:**

- **123.120 linhas** (COPY 121.270 + INSERT 1.850);
- **23.540.541 bytes** de carga em 307 arquivos, maior arquivo **99.993
  bytes** (`carga-0129.sql`);
- tenant **"Autarquia Ficticia Intermunicipal de Aguas — Porto das
  Gaivotas e Vale do Rio Claro (TESTE FICTICIO)"**;
- `carga-0001.sql`: **66.285 bytes** (o LANDING, com 62.708 bytes, é o
  valor pré-complemento; ver adendo próprio no `LANDING.md`).

**Fontes:** revisor 3 da Rodada 7 (`03-REVISOR-INTEGRACAO.md`, R7-I-m1:
soma do manifesto = 123.120; contagem própria COPY 121.270 + INSERT
1.850 = 123.120; 23.540.541 bytes por soma mecânica) e conferência
mecânica própria do corretor sobre `entrega/seed/base-ficticia/`:
soma dos 307 `carga-*.sql` = 23.540.541 bytes; linhas de dados COPY
121.270 + INSERT 1.850 = 123.120; `carga-0001.sql` = 66.285 bytes;
maior arquivo `carga-0129.sql` = 99.993 bytes.
