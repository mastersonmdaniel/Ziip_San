# GL.2 — Roteiro de execução do paralelo local (no PC do dono)

Execução real do ensaio: dois ou três ciclos de faturamento no ambiente
local, comparados conta a conta com o sistema do piloto. **Nada neste
roteiro foi executado ainda** — ele é o caminho da primeira execução, e
o primeiro `registro-paralelo.md` real nasce dela.

## 0. Pré-requisitos (todos, antes do ciclo 1)

1. **P0.30 entregue:** piloto escolhido, com o **critério de divergência
   registrado** (tolerâncias por conta, total do ciclo e ausências).
   Sem isso, a config não pode ser montada honestamente — o validador
   recusa critério sem fonte `P0.30`, e inventar tolerância está fora
   deste roteiro.
2. **GL.1 concluída:** carga anonimizada na homologação local com
   registro **CONCLUIDO** em `docs/homologacao/cargas-homologacao/`
   (o pré-voo confere o arquivo indicado na config).
3. **Stack local completa no ar:** perfil `homolog` da P0.27
   (Docker/PostgreSQL/JDK), com `ZIIPSAN_FISCAL_PRODUCTION_ALLOWED`
   literalmente `false` no `docker-compose.yml`.
4. **Perfil regulatório da agência do piloto (P2.31)** carregado,
   **ativo** e com a vigência **conferida** — a data da conferência
   entra na config (`conferidoEm`) e tem de ser anterior ao 1º ciclo.
5. **Unidade organizacional e escopo por área (P3.30)** definidos para
   a operação do paralelo; os nomes entram na config.
6. **Fiação dos hooks** (integração): comandos reais dos serviços do
   produto nas variáveis `ZIIPSAN_GL2_CMD_FATURAMENTO` (motor da P2.5,
   gerando `contas-ziipsan.json` no caminho de `GL2_SAIDA_CONTAS` e os
   XML do ciclo em `GL2_FISCAL_DIR`, só `tpAmb=2`/simulador),
   `ZIIPSAN_GL2_CMD_CONTEUDO` (P2.32), `ZIIPSAN_GL2_CMD_RETIFICACAO`
   (P2.20), `ZIIPSAN_GL2_CMD_RELATORIOS` (P3.22),
   `ZIIPSAN_GL2_CMD_DEVOLUCOES` (P3.29) e, quando houver, 
   `ZIIPSAN_GL2_CMD_ESCOPO` (P3.30). Sem fiação, o orquestrador reprova
   na etapa correspondente — por desenho.

## 1. Preparar os insumos

1. Copiar `docs/paralelo/MODELO-CONFIG-PARALELO-GL2.json` para a config
   da execução e preencher com os valores **reais desta homologação**
   (tenant, nº de ciclos — 2 ou 3 —, datas, agência/perfil da P2.31,
   critério copiado do registro da P0.30, conteúdo da P2.32, unidade e
   área da P3.30, caminho do registro CONCLUIDO da GL.1).
2. Para cada ciclo `N`, montar `ciclo-N/` no diretório do piloto:
   - `contas-piloto.json` — contas do sistema do piloto **pelo código
     substituto**: `{"contas": [{"codigoSubstituto": ..., 
     "totalCentavos": ..., "consumoM3": ..., "vencimento": ...}]}`;
   - `explicacoes.json` (quando houver divergência conhecida) —
     `{"<codigoSubstituto>": "<motivo>"}`;
   - `ocorrencias.json` (quando houver) —
     `{"devolucoes": n, "pagamentosNaoIdentificados": n}`.
   Nenhum nome, documento ou matrícula real entra nesses arquivos — só
   código substituto e valores.
3. Conferir a config: `python3 scripts/paralelo/validar-config-paralelo.py <config>`.

## 2. Executar

```bash
scripts/paralelo/paralelo-local.sh \
  --config <config-paralelo.json> \
  --piloto-dir <dir-do-piloto>
```

- `--dry-run` imprime o plano sem executar nem gravar nada.
- `--preflight-only` roda só o pré-voo e grava registro PARCIAL.
- A primeira falha para tudo, com código de saída por etapa
  (2 pré-voo, 4 faturamento, 3 conteúdo, 5 comparação, 7
  retificação/relatórios/devoluções/escopo, 6 fiscal) e registro de
  FALHA gravado com etapa e causa. **Não se corrige o relatório à mão
  para o ensaio passar:** divergência é resultado e fica registrada.

## 3. Divergências durante a execução

- **Dentro do critério e explicadas:** o ciclo só fecha após a
  retificação pela P2.20 e a conferência pelos relatórios da P3.22
  (hooks da etapa 5). Registrar a causa de cada divergência no
  `explicacoes.json` do ciclo **antes** de repetir a execução.
- **Fora do critério ou sem explicação:** o ciclo reprova (saída 5) com
  o `relatorio-comparacao.json` gravado. Investigar a causa (medição,
  tarifa/perfil, cadastro, carga), corrigir na origem — nunca no
  relatório — e executar de novo.
- **Devoluções e pagamentos não identificados** surgidos no ciclo:
  declarar em `ocorrencias.json` e tratar pela P3.29 (hook) na mesma
  execução.

## 4. Fechamento e registro

1. Ao fim de cada ciclo, o orquestrador apaga os XML assinados da área
   separada do ciclo e grava `limpeza-fiscal.json` (quantidade e hash
   do lote) — conferir que nenhum XML restou no diretório do ensaio.
2. Ao fim de todos os ciclos, o `registro-paralelo.md` gravado é o
   **registro do ensaio de POC**: conferir contra o modelo em
   `docs/paralelo/paralelos/MODELO-REGISTRO-PARALELO-GL2.md`, anexar os
   relatórios de comparação e de limpeza de cada ciclo, e levar o
   conjunto à GL.4 (aceite da homologação local).
3. Commit do diretório do ensaio como evidência, junto do marco da
   camada no diário de bordo.

## 5. Fronteiras (não são deste ensaio)

- **Paralelo com dado real:** LC.11, na produção, depois da carga real
  (LC.10) e do aceite (GL.4). Este ensaio é local e anonimizado.
- **Emissão fiscal de produção:** só no corte (LC.13), com `tpAmb=1` e
  aprovação dupla. Aqui, `tpAmb=1` é recusado em dois portões.
- **Critério de divergência:** nasce na P0.30; este roteiro o consome,
  não o define.
