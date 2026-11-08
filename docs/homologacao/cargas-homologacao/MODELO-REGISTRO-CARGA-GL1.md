# MODELO — Registro de carga GL.1 (homologação local)

> **Isto é um MODELO, não é uma carga executada.** O registro real é
> gerado pelo orquestrador `scripts/homologacao/provisionar-homologacao-local.sh`
> em `docs/homologacao/cargas-homologacao/<carimbo>-gl1/registro-carga.md`,
> preenchido com os valores daquela execução. Campo deste modelo sem
> correspondente na execução real é completado por quem executou, no
> registro real ou em documento ao lado — nunca neste modelo.

- Carimbo: `<AAAA-MM-DD HH:MM (fuso do operador)>`
- Resultado: **CONCLUIDO | FALHA | PARCIAL (--preflight-only)**
- Etapa da falha / causa: `<etapa + causa, quando FALHA>`
- Duração total medida: `<segundos medidos pelo orquestrador>`
- Manifesto: `<caminho do manifesto usado>`
- Entrada: `<diretório da extração anonimizada — só o caminho, nunca o conteúdo>`

## Etapas (nesta execução)

- pré-voo: `<OK — ambiente P0.27 presente, fiscal de produção desligado, manifesto válido>`
- provisionamento: `<OK via local-env.sh homolog | NÃO EXECUTADO (--preflight-only)>`
- varredura-entrada: `<OK — nenhum CPF/CNPJ formatado ou e-mail na extração>`
- carga-cadastro-P1.2: `<OK pelo comando fiado | NÃO EXECUTADA + motivo>`
- carga-historico-P2.10: `<OK | NÃO EXECUTADA + motivo>`
- carga-xml-P2.19: `<OK — só lote sintético com certificado de teste | NÃO EXECUTADA + motivo>`
- carga-gis-P3.26: `<OK — rede real restrita; camadas pessoais com método | NÃO EXECUTADA + motivo>`
- conciliação: `<OK — unidades/saldo/histórico/XML carregados == totais do piloto | divergência registrada>`

## Conciliação fina — evidência dos importadores (preencher por quem executou)

> O portão de conciliação do orquestrador confere **só os totais gerais**
> acima. A conciliação fina é a dos relatórios dos próprios importadores;
> sem as referências abaixo preenchidas (ou os relatórios anexados ao
> diretório do registro), o registro está **incompleto** (correção D4).

- Relatório de conciliação da P1.2 (setor/rota/categoria): `<caminho do relatório ou identificador do lote>`
- Relatório de conciliação da P2.10 (saldo por código substituto): `<caminho do relatório ou identificador do lote>`
- Relatório de conciliação da P2.19 (contagem por competência): `<caminho do relatório ou identificador do lote>`
- Relatório/carga da P3.26 (cadastro técnico, se gerar relatório próprio): `<caminho ou identificador — ou "não se aplica" com o motivo>`
- varredura-pos-carga: `<OK — teste de varredura da P0.45 verde | NÃO EXECUTADA + motivo>`

## Desvios (completar por quem executou)

- `<passos manuais, recargas, decisões sobre camadas GIS, qualquer etapa
  assistida fora do orquestrador — ou "nenhum desvio">`

## Fronteira declarada

> Este registro prova a carga **anonimizada** na homologação local.
> XML real e dado real são da **LC.10** (produção). A correspondência
> código substituto ↔ matrícula **não** está neste registro: fica só no
> piloto (roteiro da P0.45).
