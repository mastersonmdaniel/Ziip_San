# Modelo de registro do ensaio de restauração local — GL.3

> Este é o **modelo** dos campos do registro. O registro de um ensaio real é **gerado pelo orquestrador** (`scripts/operacao/ensaio-restauracao-local.sh`) em `docs/operacao/ensaios-restauracao/<carimbo>-gl3/registro-ensaio.md`, preenchido com os valores **medidos na execução** — nenhum valor deste modelo é resultado de ensaio.
>
> **Resultado registrado como ensaio, sem meta; a meta vale na LC.12.** A referência de RTO de 4 h (ADR-0012) entra como comparação registrada, nunca como aprovação/reprovação do ensaio.

```
Registro do ensaio de restauracao local - GL.3
Data/hora da execucao: ____
Ambiente: ____ (maquina, runtime de container e versao, imagem do PostgreSQL)
Executor: ____

ORIGEM
  Backup usado (diretorio da P0.28): ____
  Dump: ziipsan.dump (____ bytes)  SHA-256: ____
  LSN do WAL no backup (wal-lsn.txt): ____
  Lateral: segunda restauracao do mesmo dump | PITR preparado a parte (roteiro P0.28) ____

TEMPOS MEDIDOS (inicio -> fim, duracao) x REFERENCIA
  pre-voo: ____
  restauracao-completa: ____
  verificacao-contagem-checksum: ____
  teste-rls: ____
  restauracao-seletiva-tenant: ____
  desfazer-lote-os-inversa: ____
  TOTAL: ____ s
  Referencia de RTO: 14400 s (4 h) - comparacao registrada: ____ (sem meta; meta na LC.12)

RESTAURACAO COMPLETA (base isolada descartavel)
  Resultado do pg_restore --exit-on-error: ____
  Tabelas verificadas: ____
  Registros (soma por tabela): ____
  Checksum global (SHA-256 do TSV por tabela): ____
  Artefato: contagens-checksum.tsv

TESTE DE RLS (como ziipsan_app, no banco restaurado)
  Sem contexto = 0 / contexto proprio = so as linhas dele / contexto alheio = 0: OK | FALHOU | INCONCLUSIVO
  Artefato: teste-rls.tsv

RESTAURACAO SELETIVA (P3.33)
  Tenant restaurado (id/schema): ____
  Instante-alvo: ____  Fonte: lateral do ensaio
  Consentimento (id/arquivo): ____  Modo: REPLACE
  Extrair/comparar/aplicar: OK | FALHOU (etapa/causa)
  Decisao sobre tabelas ONLY_IN_CURRENT: ____ (quem aprovou: ____)
  Tenant-testemunha (id/schema): ____
  Prova de nao-afetacao dos demais: fotografia P3.33 conferir OK | FALHOU;
    checksum da testemunha antes == depois: SIM | NAO

DESFAZER DE LOTE (P3.33)
  Lote (run_id / diretorio): ____
  Caminho: remocao por execucao | OS inversa (efeito por OS, ADR-0039)
  Se OS inversa: OS de origem ____ ; descritor InverseOrderRequest conferido: SIM | NAO;
    OS inversa encerrada (CLOSED_EXECUTED, reverses_order_id = origem): ____ ;
    efeitos aplicados em service_order_effects: ____
  Se remocao por execucao: checksum do schema do lote antes != depois: SIM | NAO;
    reversao em public.audit_log: ____
  Prova do efeito: ____

DESVIOS E OBSERVACOES
  ____
```

Campos que o orquestrador **não** preenche sozinho e quem executa completa à mão no registro gerado: executor, detalhe do ambiente, decisão sobre `ONLY_IN_CURRENT`, desvios. Todo o resto nasce medido da execução — se um campo estiver `-` no registro gerado, a etapa correspondente não foi medida (o ensaio falhou antes ou foi abortado), e isso é o resultado honesto do ensaio.
