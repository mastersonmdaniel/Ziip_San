# NOTA DE INTEGRAÇÃO — NUMERAÇÃO DE MIGRATIONS (AUTORITATIVA)

**Lote 2 de correções da revisão geral do ZiipSan, 08/11/2026 —
item M6. Esta nota é a instrução ÚNICA e AUTORITATIVA de numeração
de migrations para a integração. Onde ela divergir de relatório de
pacote, vale ela.**

## 1. Regra única: sequência global da P3.12

A numeração de todos os SQL hoje aguardando numeração (os arquivos
em `db/pendente/` dos pacotes — 43 no inventário da revisão geral,
incluído o arquivo equivalente da P2.24 em `db/migration-pendente/`)
segue **SÓ** a sequência global única instituída pela P3.12
(`V100__global_sequence_start.sql`):

- toda migration nova de tenant recebe versão **maior que a maior
  versão publicada**, atribuída **em ordem**, na sequência aberta
  pela V100 (política "V100+");
- **nenhuma** numeração volta a usar faixa de módulo (V4_x, V15_x,
  V21_x, V23_x ou qualquer outra faixa antiga): as faixas foram
  encerradas pela P3.12, que retirou o `outOfOrder` do
  `TenantMigrator`;
- nenhum número é reservado, reutilizado ou deixado como placeholder
  (`V15_X`, `VSEQ`, `V-pendente` etc.) no caminho ativo de migrations.

Âmbito: a sequência global governa as migrations de **tenant**. Os
poucos SQL pendentes de schema core/platform (ex.: ED-09, ED-11 e a
parte core da ED-06, conforme o próprio desenho de cada pacote) são
numerados na história do seu próprio schema — nunca numa faixa de
tenant e nunca no lugar de uma migration de tenant.

## 2. A lista de publicadas anda junto, sempre

**Cada** numeração feita sob esta nota atualiza, no mesmo ato, o
arquivo `migration-policy/published-tenant-migrations.txt`
(111 linhas no corte da P3.12), acrescentando a migration recém
numerada. Numeração sem atualização da lista está incompleta: é a
lista que alimenta a guarda de CI do item 4.

## 3. Instruções superadas

Ficam **SUPERADAS** por esta nota as instruções de numeração por
faixa constantes dos relatórios dos pacotes abaixo (o restante de
cada relatório permanece válido; a superação é só da instrução de
numeração, e cada relatório recebeu o adendo correspondente):

| Pacote | Instrução superada | Destino que NÃO deve mais ser usado |
|---|---|---|
| ED-34 | "próximo slot livre da história tenant do billing (pacotes locais vão até V4_17)" | faixa V4_x |
| ED-69 | "próximo número livre da faixa" V21_x | `V21_<n>__certification_quality_iso9001.sql` |
| ED-74 | "próximo número livre da faixa" V23_x | `V23_<n>__lgpd_privacy_iso27701.sql` |
| ED-46 | "próximo slot livre da faixa" V15_x | faixa V15_x |

No caso da ED-46, o relatório menciona `V100+` como alternativa
("decisão do dono da integração"): esta nota encerra a alternativa —
vale só a sequência global, sem decisão caso a caso.

## 4. Rede de proteção

A guarda da P3.12 — `TenantMigrationGlobalSequenceCiTest` /
`TenantMigrationVersionPolicy`, sobre a lista
`migration-policy/published-tenant-migrations.txt` — é a rede de
proteção que **recusa** qualquer numeração fora da sequência global.
Ela não substitui esta nota: quem numera seguindo relatório antigo
bate na recusa da guarda; quem numera por esta nota passa por ela.

## 5. Os arquivos `V15_X__*.sql` (coordenação com o G1 / Lote 1)

Os dois arquivos `V15_X__*.sql` (ED-46 e ED-48), objeto do item G1
tratado pelo Lote 1, seguem esta nota **nos dois desfechos
possíveis**, sem exceção:

- se forem **numerados** na integração, a numeração é a da sequência
  global (item 1) e eles entram em
  `migration-policy/published-tenant-migrations.txt` como qualquer
  migration numerada (item 2);
- se forem **movidos para `db/pendente/`**, permanecem sem número e
  fora da lista de publicadas até serem numerados — e, quando forem,
  será também pela sequência global (item 1), com a atualização da
  lista no mesmo ato (item 2).

Nenhuma instrução desta nota depende de qual dos dois desfechos o
Lote 1 adotar.
