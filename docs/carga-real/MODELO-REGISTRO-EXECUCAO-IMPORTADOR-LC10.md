# MODELO — Registo de execução por importador (LC.10)

- **Sessão produtora do modelo:** `1fb44147-00cd-4ed1-a19a-ac6f72c78874` — papel: **Produtor**
- **Documento em branco.** Um registo por execução de importador
  (inclui reexecuções). Preenchido somente na execução real.
  **Nenhum valor aqui é resultado de execução alguma.**

---

- Importador: `[ ] P1.2 LegacyImportService` · `[ ] P2.10 V1ImportService` · `[ ] P2.19 LegacyXmlImportService`
- Cliente (piloto) / tenant: `[A DEFINIR]`
- Ambiente: produção (confirmar — dado real fora da produção é
  incidente, ver §5 do roteiro)
- Data/hora de início / fim: `[A DEFINIR]`
- Operador (ZiipSan): `[A DEFINIR]`
- Conferente (ZiipSan, distinto do operador): `[A DEFINIR]`

## Entrada

- Fonte (sistema de origem, meio, data/hora da extração, responsável
  no cliente): `[A DEFINIR]`
- Lote (sistema + chave externa): `[A DEFINIR]`
- Mapeamento/declaração (arquivo + versão): `[A DEFINIR]`
- Migration esperada no tenant (`V14_1` / `V14_2` / `V14_3`): `[A DEFINIR]`
- Marcador de PITR anterior à carga (LC.3): `[A DEFINIR]`

## Execução

- É reexecução? `[ ] não` · `[ ] sim — de qual execução (idempotência
  esperada: sem duplicidade)`
- Ensaio executado antes da aplicação (P1.2/P2.10)? `[ ] sim` · `[ ] não — justificar`
- Contagens: recebidos / encenados / aplicados / recusados: `[A DEFINIR]`
- Relatório gerado (caminho/identificação): `[A DEFINIR]`
- Divergências encontradas (referência ao relatório de conciliação ou
  de recusas): `[A DEFINIR]`

## Saída

- Resultado: `[ ] concluído` · `[ ] concluído com divergências explicadas` · `[ ] parado — motivo`
- Varredura da P0.45 aplicável a esta execução? (só homologação/
  demonstração — produção com dado real não usa a base varrida como
  prova) `[A DEFINIR]`
- Próximo passo do roteiro liberado: `[A DEFINIR]`
- Assinaturas (operador / conferente): `[A DEFINIR]`
