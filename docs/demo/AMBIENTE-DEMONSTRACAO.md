# Ambiente local de demonstração (P3.23)

## O que é

O ambiente local de demonstração é o **perfil `demo`** do ambiente local
da P0.27 (`scripts/local-env.sh demo`), ocupado por um **tenant de
demonstração permanente** com massa sintética própria. Ele existe para a
POC ser ensaiável a qualquer momento — ao fim de cada fase (a partir do
P3, com a meta no aceite, ADR-0023) e antes da ED-01 — sem depender de
preparar um ambiente na véspera.

Ele **não** é a homologação local: homologação exercita o piloto com
dado anonimizado na origem (ADR-0033); demonstração demonstra o produto
com dado **fictício gerado**. Misturar os dois destruiria as duas
garantias (a de origem do dado de homologação e a de ausência de dado
real na demonstração).

## Separação da homologação (como é garantida)

| Dimensão | Homologação local | Demonstração local |
|---|---|---|
| Perfil (P0.27) | `homolog` | `demo` |
| Tenant | tenant do piloto (homologação) | tenant próprio da demonstração (`DEMO_TENANT_ID`) |
| Schema | schema do tenant de homologação | `DEMO_TENANT_SCHEMA`, diferente |
| Dado | anonimizado na origem, com termo | sintético gerado por script, marcado |
| Fiscal | `ziipsan.fiscal.production-allowed=false` | idem (guarda executável) |

As guardas são executáveis, não convencionais: `recriar-demo.sh` e
`DemoTenantGuard` recusam tenant/schema iguais aos de
desenvolvimento/homologação, recusam fiscal liberado e recusam massa
abaixo do piso ou com documento sem marca. A pilha (PostgreSQL,
Keycloak, cofre, S3, PgBouncer, mesma imagem da P0.5, job `migrate`) é
a mesma da P0.27 — compartilhar a pilha é o desenho da P0.27; separar
tenant, schema e dado é o que esta tarefa acrescenta.

## O tenant de demonstração

- **≥ 10.000 unidades fictícias** (padrão do pacote: 12.000, com folga
  declarada sobre o piso), 80% RES / 20% COM, com hidrômetro, leitura e
  consumo por unidade — as mesmas fórmulas da massa de carga da P2.9,
  para demo e carga falarem dos mesmos ids.
- **Sem dado pessoal real**: titular é `TITULAR SINTETICO NNNNN`,
  endereço é `RUA SINTETICA ... - MUNICIPIO SINTETICO`, e o documento
  (CPF do RES, CNPJ do COM) é derivado da semente com dígito verificador
  calculado. **Todo documento é marcado como sintético** — a marca
  (`documento_sintetico=SIM`, `marca=SINTETICO`) viaja na mesma linha
  do CSV canônico, e o teste do gerador confere 100% das linhas.
- **Recriação do zero por script, com tempo medido**:
  `scripts/demo/recriar-demo.sh` apaga e recria o perfil, regenera a
  massa, semeia e confere a contagem medida no banco, gravando
  `recriacao.json` com o tempo de cada etapa e o total **desta**
  execução. Tempo de recriação não é estimado em documento: nasce do
  registro de cada execução.
- Tarifa da demonstração: a fixture fictícia do produto
  (`FictitiousTariffs`, declarada fictícia), sob o código `DEMO-P323` —
  valor de demonstração, não política tarifária de ninguém.

## Ensaio de POC (resumo; o detalhe está em MATRIZ-VIVA-E-ENSAIO.md)

O ensaio é cronometrado contra o tempo de preparo que os editais de
referência concedem — **5 e 7 dias úteis** — e avaliado pelo critério
do P3: **≥ 50 de 162 pontos no roteiro da VISAN** e **seções 2.4 e 2.5
do SAMAE sem falha**. O resultado é registrado **por item** e alimenta
a matriz viva; o relatório de cobertura (requisito, tarefa, teste, item
de roteiro) é gerado do mesmo registro e anexado ao aceite. A meta
final da ED-01 (≥ 146/162, dois ensaios consecutivos) **não** é o
critério desta tarefa e não é usada em nenhum veredito aqui.

## Fronteira com a LC.2

A **versão na nuvem** deste ambiente (IaC na AWS `sa-east-1`, criada
antes da primeira POC de licitação, que exige o sistema em nuvem) é da
**LC.2**. Esta tarefa entrega o ambiente local, a massa, o ensaio e os
registros; quando a LC.2 subir a demonstração na nuvem, ela reaproveita
esta mesma massa sintética e este mesmo aparato de ensaio — nenhum
recurso de nuvem é criado ou prometido aqui.

## Pendências declaradas

1. **Trecho `[CONFERIR-V3_1]`** do `sql/10-seed-demo.sql`: a DDL do
   registry/titulares não estava verificada nos espelhos (mesma
   pendência da P2.9); unidades/titulares entram pelo esqueleto marcado
   depois da conferência no banco real, e o orquestrador para sem a
   contagem medida.
2. **Recriação e ensaio reais** rodam no PC do dono: nenhum tempo de
   recriação, pontuação de ensaio ou contagem de banco é afirmado neste
   documento — eles nascem dos registros (`recriacao.json`,
   `resultado-ensaio.json`) das execuções.
