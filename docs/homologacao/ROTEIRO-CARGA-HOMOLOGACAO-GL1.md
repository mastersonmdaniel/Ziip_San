# GL.1 — Roteiro da carga na homologação local

> **Onde roda:** no PC do dono (Docker/Podman + ambiente local da P0.27),
> com a extração do piloto **já anonimizada na origem**. **Não roda nesta
> VM** (sem Docker/JDK) e nenhuma carga é dada como executada sem o
> Registro da Carga gerado pela execução. O comportamento do orquestrador
> é provado sem Docker pelo harness
> `scripts/test-provisionar-homologacao-local.sh` (stubs, padrão P4.7/GL.3).

Peças:

| Peça | Caminho |
|---|---|
| Plano da homologação | `docs/homologacao/PLANO-HOMOLOGACAO-LOCAL-GL1.md` |
| Orquestrador | `scripts/homologacao/provisionar-homologacao-local.sh` |
| Validador do manifesto | `scripts/homologacao/validar-manifesto.py` |
| Modelo de manifesto | `docs/homologacao/MODELO-MANIFESTO-CARGA-GL1.json` |
| Modelo de registro | `docs/homologacao/cargas-homologacao/MODELO-REGISTRO-CARGA-GL1.md` |
| Roteiro de anonimização (P0.45) | `docs/seguranca/roteiro-anonimizacao-piloto.md` (já no repo) |
| Importadores | P1.2 (cadastro), P2.10 (histórico 120 meses), P2.19 (XML anteriores), P3.26 (cadastro técnico/GIS-07) |

## 1. Antes da extração (no piloto)

1. Termo de cooperação vigente (P0.30/P1.1) autorizando a homologação local
   com anonimização na origem e a carga da **rede real** com acesso
   restrito (ADR-0033).
2. Extração executada **no sistema do piloto**, anonimizada lá pelo
   roteiro da P0.45: sem nome, documento, endereço nem contato; **código
   substituto no lugar da matrícula**; a tabela de correspondência fica
   **só no piloto**.
3. Cadastro técnico: redes/peças/estruturas reais; ligações, ramais,
   cavaletes e imóveis anonimizados com coordenada deslocada dentro da
   quadra ou agregada por quadra, **método registrado por camada**.
4. Documentos fiscais anteriores: preparar **lote sintético** assinado com
   **certificado de teste** (espelhando contagens/competências, sem XML
   real). XML real **não sai do piloto** nesta fase — só na LC.10.
5. O piloto fornece os **totais oficiais** da extração (unidades, saldo
   total, registros de histórico, XML que o lote sintético representa)
   para o manifesto — são a referência da conciliação, não um resultado.

## 2. Preparação local

1. Ambiente do zero ou íntegro: `scripts/local-env.sh homolog --reset`
   (se a base local tiver resíduo de outra massa) e **backup pré-carga**
   não se aplica a base virgem; com massa anterior, backup da P0.28 antes.
2. Preencher o manifesto a partir do modelo: `tenantId` do tenant de
   homologação (string em formato UUID — número é recusado),
   `codigoSubstituto: true`, `totaisPiloto`, `xmlOrigem`,
   `camadasGis` com tipo/método por camada (tipo fora do conjunto
   conhecido é recusado, em qualquer grafia) e `acessoRestrito` da rede
   real com grupo **e** responsáveis nomeados.
3. Fiar os comandos dos importadores (pendência de integração — ver o
   plano §3): `ZIIPSAN_GL1_CMD_CADASTRO`, `_HISTORICO`, `_XML`, `_GIS` e
   `ZIIPSAN_GL1_CMD_VARREDURA_POS` (suíte de varredura da P0.45).
4. Conferência a seco: `--dry-run` (plano) e `--preflight-only`
   (pré-voo + varredura de entrada, com registro PARCIAL).

## 3. Execução

```bash
scripts/homologacao/provisionar-homologacao-local.sh \
  --manifesto <manifesto-carga.json> \
  --entrada-dir <dir da extração anonimizada> \
  --totais-carregados <totais-carregados.json>
```

- `totais-carregados.json` é consolidado pelo operador **a partir dos
  relatórios dos importadores** (conciliação da P1.2, saldo da P2.10,
  contagem por competência da P2.19) após as cargas — na prática, rode as
  cargas assistidas, consolide o arquivo e rode o orquestrador completo
  (as cargas são idempotentes por lote: P1.2/P2.10/P2.19 recusam duplicar).
- **Abortar:** primeira falha para tudo, com saída distinta por etapa
  (2 pré-voo/provisionamento, 3 varredura de entrada, 4 carga, 5
  conciliação, 6 varredura pós-carga) e registro de FALHA gravado.
  **Erro de sintaxe de argumentos** (opção desconhecida, argumento
  inesperado, valor faltando) também sai 2 **com registro de FALHA
  gravado** — nunca silencioso (correção D5); única exceção: com
  `--dry-run` nada é gravado, por definição do modo.
  Não corrigir dado "na mão" na base: divergência volta à origem
  (extração/mapeamento) e a carga é refeita.
- **Interrupção (Ctrl-C/SIGTERM):** trap grava o registro como FALHA na
  etapa corrente e sai 130/143. O ambiente `homolog` **não** é derrubado
  pelo orquestrador (é do operador; `local-env.sh down` é manual).

## 4. Depois da carga

1. Registro CONCLUIDO conferido contra o modelo; desvios anotados por quem
   executou (passos manuais, decisão sobre camada GIS, lote refeito).
   **Amarrar a conciliação fina:** preencher no registro (campos do
   modelo) as referências aos relatórios dos importadores (P1.2, P2.10,
   P2.19) ou anexá-los ao diretório do registro — o portão de conciliação
   do orquestrador confere só os totais gerais (correção D4).
2. Acesso à rede real restrito ao grupo do manifesto — conferir os
   acessos concedidos no tenant de homologação antes de liberar a GL.2.
3. O ambiente pós-carga é a base da **GL.2** (paralelo conta a conta pelo
   código substituto) e do ensaio da **GL.3** sobre backup desta base.
4. Commit do registro (+ manifesto, sem nenhum dado da extração) como
   evidência, junto do marco da camada no diário de bordo.

## 5. Fronteiras

- **Dado real:** LC.10, só na produção, após LC.9 (contrato/DPA) e GL.4.
- **Paralelo:** GL.2. **Aceite da homologação:** GL.4 (decisão formal).
- **Emissão fiscal de produção (`tpAmb=1`):** LC.13. Na homologação local,
  NFAg-e só em simulador/homologação SEFAZ (`tpAmb=2`, P3.7).
