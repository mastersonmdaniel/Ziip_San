# GL.1 — Plano e ambiente de homologação local + carga anonimizada

> **Tarefa:** GL.1 "Carga na homologação local" (17:X5; ADR-0033), tamanho M,
> dependências formais P2.10 e P2.19. **Frente consumidora imediata:** GL.2
> (paralelo local). **Fronteira:** dado real é LC.10, só na produção.
>
> **Estado honesto desta entrega:** plano + ambiente executável + portões
> automatizados, prontos e provados nesta VM pelo harness sem Docker
> (37 verificações, após as correções D1–D7 da revisão cruzada — ver o
> adendo no relatório da frente). **Nenhuma carga real foi executada** — não há piloto,
> extração, Docker nem JDK nesta VM. A primeira carga é ato de homologação
> no PC do dono, seguindo o roteiro, e o primeiro registro real nasce dela.

## 1. O que é a homologação local (e o que ela não é)

Ambiente **local** (ADR-0033), sobre o perfil `homolog` da P0.27, onde o
sistema do piloto é carregado **anonimizado na origem** para o paralelo da
GL.2 — sem nenhum dado pessoal real, sem emissão fiscal de produção e sem
substituir o sistema do piloto, que continua oficial e emissor até o corte
(LC.13). Não é demonstração (massa sintética da P3.23) nem produção.

## 2. Ambiente (provisionamento)

| Peça | Fonte | Papel na GL.1 |
|---|---|---|
| Perfil `homolog` (PostgreSQL, Keycloak, PgBouncer, cofre, S3 local, `migrate` + `api`) | P0.27, `scripts/local-env.sh homolog` | a pilha onde a carga acontece; recriável do zero com `--reset` |
| `ZIIPSAN_FISCAL_PRODUCTION_ALLOWED=false` | **compose da P0.27 é a fonte autoritativa**: literal `"false"` no `environment:` de `api`/`migrate` (conferido no espelho real; a variável **não** consta do `.env.example`) | portão do orquestrador confere o compose: toda ocorrência tem de ser literalmente `false`; ausente, interpolada (`${...}`) ou diferente = **recusa** (saída 2, fail-closed — não dá para determinar, não carrega). O `.env` é conferido como defesa extra, com parsing tolerante a grafia/aspas (`TRUE` e `"true"` recusam) — homologação nunca emite `tpAmb=1` |
| Portas só em `127.0.0.1` | P0.27 (`check-local-profiles.sh`) | nenhum serviço da homologação exposto na rede |
| Backup/PITR local | P0.28 | rede de segurança antes/depois da carga (backup pré-carga no roteiro) |
| Ensaio de restauração | GL.3 | aparato já entregue; a GL.2/GL.4 reusam o ambiente pós-carga |
| Orquestrador da GL.1 | `scripts/homologacao/provisionar-homologacao-local.sh` | provisiona (via P0.27), aplica os portões §3 e grava o registro |
| Manifesto da carga | `docs/homologacao/MODELO-MANIFESTO-CARGA-GL1.json` | contrato da carga: tenant, código substituto, totais do piloto, origem dos XML, camadas GIS e acesso restrito |
| Validador do manifesto | `scripts/homologacao/validar-manifesto.py` | forma + regras ADR-0033 antes de qualquer carga |

Provisionar = `scripts/local-env.sh homolog` (o orquestrador o invoca;
não há compose novo nesta tarefa — duplicar a pilha da P0.27 seria divergir
dela). Tempo de provisionamento é o que o `local-env.sh` imprime; nenhum
tempo é afirmado antes de rodar.

## 3. Portões da carga (na ordem; a primeira falha para tudo)

| # | Portão | Critério | Saída |
|---|---|---|---|
| a | Pré-voo | ambiente P0.27 presente, fiscal de produção desligado **na fonte que controla a variável (compose: `false` literal em todas as ocorrências; `.env` sem ligação, como defesa extra)**, manifesto válido (tipos GIS normalizados e em whitelist, `tenantId` UUID, rede real com grupo **e** responsáveis) | 2 |
| b | Provisionamento | `local-env.sh homolog` sai 0 | 2 |
| c | Varredura de entrada | nenhum CPF/CNPJ formatado ou e-mail em arquivo da extração — **todos** os arquivos, incl. `*.md` e binários lidos como texto (anonimização é **na origem**, P0.45). Limite declarado: padrões formatados apenas; dígitos puros e texto livre não são detectados aqui | 3 |
| d | Cargas pelos importadores | P1.2 (cadastro) → P2.10 (histórico 120 meses) → P2.19 (XML, só sintético c/ certificado de teste) → P3.26 (cadastro técnico); comando de cada importador fiado por variável `ZIIPSAN_GL1_CMD_*` — **sem fiação, a etapa reprova declarando a pendência** | 4 |
| e | Conciliação | totais carregados **iguais** aos totais do sistema do piloto (manifesto): unidades, saldo total, registros de histórico, XML aceitos. Este portão é **só dos totais gerais**; a conciliação **fina** é a dos relatórios dos importadores e fica amarrada como evidência ao registro (ver §4.4) | 5 |
| f | Varredura pós-carga (P0.45) | teste de varredura verde: nenhum dado pessoal real e nenhum documento sem marca de sintético na base local | 6 |
| g | Registro | `docs/homologacao/cargas-homologacao/<carimbo>-gl1/registro-carga.md` com os valores desta execução | — |

**Por que hooks e não chamadas diretas:** os importadores da P1.2/P2.10/
P2.19/P3.26 são serviços Java do produto e **não têm CLI neste repo**
(conferido nas entregas locais das três frentes). Inventar uma CLI seria
inventar superfície de produto. A fiação (runner/teste de integração/
endpoint interno que a integração expuser) é pendência declarada no
relatório da GL.1; até lá, o modo `--preflight-only` prova (a)+(c) e o
roteiro descreve a execução manual assistida de cada importador.

## 4. Regras de dado (ADR-0033 + roteiro da P0.45)

1. **Anonimização na origem, no piloto:** sem nome, documento, endereço nem
   contato; **código substituto no lugar da matrícula**, com a
   correspondência **só no piloto** (nunca viaja, nunca entra no manifesto
   nem no registro).
2. **Cadastro técnico:** redes, peças e estruturas **reais** do piloto,
   com **acesso restrito** declarado no manifesto (grupo/responsáveis);
   ligações, ramais, cavaletes e imóveis **só sintéticos ou anonimizados**,
   com coordenada **deslocada dentro da quadra** ou **agregada por quadra**,
   e o método registrado **por camada** — camada pessoal sem método é
   recusada pelo validador (e pela guarda da P3.26 na publicação).
3. **Documentos fiscais anteriores:** na homologação local, **só
   sintéticos assinados com certificado de teste** (P2.19). XML real traz
   dado pessoal do destinatário e entra **só na LC.10**, na produção.
   O manifesto com `xmlOrigem` fora de `SINTETICO_CERTIFICADO_TESTE`/
   `NENHUM` é recusado no pré-voo.
4. **Conciliação com o sistema do piloto:** os totais da carga vêm dos
   relatórios dos próprios importadores; divergência não se "ajusta" —
   registra-se, investiga-se a causa (extração, mapeamento ou anonimização)
   e a carga é refeita. O portão (e) do orquestrador confere **só os
   totais gerais**; a conciliação **fina** (setor/rota/categoria da P1.2,
   saldo por código substituto da P2.10, contagem por competência da
   P2.19) vive nos relatórios dos importadores, e esses relatórios — ou a
   referência a eles (caminho/identificador de lote, campos próprios no
   modelo de registro) — **acompanham o registro da carga**: registro
   CONCLUIDO sem a evidência fina ao lado está incompleto (correção D4
   da revisão cruzada).

## 5. Papéis e sequência até a GL.2

| Quem | Faz |
|---|---|
| Piloto | extrai e anonimiza na origem (roteiro da P0.45); fornece os totais oficiais; guarda a correspondência código ↔ matrícula |
| Operador da homologação | provisiona, roda o orquestrador, consolida `totais-carregados.json` dos relatórios dos importadores, assina o registro |
| Integração (projeto) | fia os comandos dos importadores (§3-d) e roda a CI total da camada |

Sequência: termo de cooperação do piloto (P0.30/P1.1) → extração
anonimizada → GL.1 (esta carga, com registro CONCLUIDO) → GL.2 (paralelo
local, usando o código substituto para comparar conta a conta).

## 6. Critérios de aceite da GL.1 → onde são provados

Ver a tabela item a item no `RELATORIO-GL.1.md` da frente. Resumo: os
portões (a), (c) e as recusas de (d)/(e)/(f) são provados **aqui** pelo
harness; a carga em si, a conciliação com totais **reais** do piloto e a
varredura sobre a base carregada são provadas **na execução** no PC do
dono — pendentes por natureza, declaradas, nunca afirmadas como feitas.
