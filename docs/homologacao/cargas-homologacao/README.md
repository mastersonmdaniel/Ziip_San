# Registros das cargas da homologação local (GL.1)

Cada execução real do orquestrador
(`scripts/homologacao/provisionar-homologacao-local.sh`) grava aqui um
diretório `<carimbo>-gl1/` com:

- `registro-carga.md` — resultado, etapas e valores **daquela execução**
  (gerado pelo script; conferir contra o `MODELO-REGISTRO-CARGA-GL1.md`);
- `local-env.log` — saída do provisionamento do perfil `homolog` (P0.27),
  quando a execução passou pelo provisionamento.
- Os **relatórios finos de conciliação dos importadores** (P1.2:
  setor/rota/categoria; P2.10: saldo por código substituto; P2.19:
  contagem por competência) — anexados aqui **ou** referenciados no
  registro (caminho/identificador de lote, campos do modelo de registro).
  O portão de conciliação do orquestrador confere só os totais gerais;
  registro CONCLUIDO sem a evidência fina referenciada/anexada está
  incompleto (correção D4 da revisão cruzada).

Regras:

- Registro de **FALHA também é evidência**: não apagar nem reescrever para
  "melhorar" — a recarga gera um registro novo, e a sequência conta a
  história da carga.
- **Nunca** gravar aqui arquivos da extração, o manifesto com dados reais
  de totais além do necessário ao registro, nem — em hipótese alguma — a
  correspondência código substituto ↔ matrícula (fica só no piloto).
- O primeiro diretório real nasce da primeira carga no PC do dono; até lá,
  esta pasta contém só o modelo e este README.
