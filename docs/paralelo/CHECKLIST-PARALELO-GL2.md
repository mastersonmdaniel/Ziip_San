# GL.2 — Checklist do paralelo local (com evidência)

Marcar cada item **com a evidência ao lado** (arquivo/registro), não de
memória. Item sem evidência = item não feito.

## Acordos e critério

- [ ] P0.30 entregue: piloto escolhido e **critério de divergência
  registrado** — evidência: ______
- [ ] Critério copiado para a config **sem alteração de número** —
  evidência: config + registro da P0.30 lado a lado: ______

## Ambiente e carga

- [ ] Perfil `homolog` da P0.27 no ar; `ZIIPSAN_FISCAL_PRODUCTION_ALLOWED`
  literalmente `false` no `docker-compose.yml` — evidência: ______
- [ ] Carga da GL.1 com registro **CONCLUIDO** — evidência:
  `docs/homologacao/cargas-homologacao/<carimbo>-gl1/registro-carga.md`

## Configuração do paralelo

- [ ] Config validada (`validar-config-paralelo.py` → `CONFIG_OK`) —
  evidência: ______
- [ ] Perfil regulatório da agência do piloto (P2.31) **ativo**, vigência
  cobrindo o 1º ciclo e **conferida antes** dele (`conferidoEm`) —
  evidência: ______
- [ ] Conteúdo mínimo (P2.32) identificado na config — evidência: ______
- [ ] Unidade organizacional e escopo por área (P3.30) declarados —
  evidência: ______
- [ ] Hooks fiados: faturamento, conteúdo, retificação, relatórios,
  devoluções (e escopo, quando houver) — evidência: ______

## Insumos por ciclo

- [ ] `ciclo-N/contas-piloto.json` só com código substituto e valores
  (sem nome/documento/matrícula) — evidência: ______
- [ ] `explicacoes.json` cobrindo toda divergência conhecida do ciclo —
  evidência: ______
- [ ] `ocorrencias.json` declarando devoluções/pagamentos não
  identificados do ciclo (ou ausência declarada) — evidência: ______

## Execução e fechamento

- [ ] Orquestrador executado; registro gravado em
  `docs/paralelo/paralelos/<carimbo>-gl2/registro-paralelo.md` —
  resultado: ______
- [ ] Por ciclo: `relatorio-comparacao.json` dentro do critério da
  P0.30, toda divergência explicada — evidência: ______
- [ ] Divergências corrigidas pela retificação (P2.20) e conferidas
  pelos relatórios (P3.22) — evidência: logs ao lado do registro: ______
- [ ] `limpeza-fiscal.json` por ciclo: XML assinados apagados
  (quantidade + hash do lote), nenhum XML restante — evidência: ______
- [ ] Registro do ensaio de POC levado à GL.4 — evidência: ______
