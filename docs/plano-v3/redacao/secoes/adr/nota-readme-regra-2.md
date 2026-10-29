# Nova redação da regra 2 do índice de ADRs

Substitui a regra 2 de `docs/arquitetura/adr/README.md`, por decisão do dono do produto de 28/10/2026 (PD-03; 23a,
COE-22). As demais regras ficam como estão.

---

2. ADR não se apaga. Linha de ajuste datada (por exemplo, "**Ajuste de 28/10/2026 (plano v3.0):** ...") só registra
   esclarecimento que não muda a substância da decisão: precisão de texto, remissão a tarefa, nome ou número
   atualizado, leitura de um item à luz de ADR posterior, fato conferido em fonte. Mudança de substância (o que se
   decide, o escopo, a alternativa escolhida, uma regra ou um limite) exige ADR novo, que declara qual ADR e qual item
   altera ou substitui. O ADR alterado só em parte recebe linha de ajuste que aponta para o ADR novo; o substituído por
   inteiro passa a "Substituído por ADR-NNNN", e só a linha de status é editada. Quando a decisão sai do escopo sem
   substituta, o ADR passa a "Revogado", com data, versão do plano e motivo, e o texto fica como histórico. As linhas de
   ajuste gravadas até a versão 2.21 do plano continuam válidas como estão.

---

**Aplicação no índice** (23a, COE-22), na mesma revisão do README:

- ADR-0029: status "Substituído por ADR-0035" no lugar de "Revogado (v2.12)", porque o ADR-0035 é a decisão que o
  substitui.
- ADR-0022: linha de ajuste que aponta para o ADR-0043, que altera o item 4.
- ADR-0037: linha de ajuste que aponta para o ADR-0045 (motor de OS no núcleo).
- ADR-0035: linha de ajuste que aponta para o ADR-0044, que substitui o item 1 ("metas de calendário").
- ADR-0001 e ADR-0011: título do índice atualizado ("homologação local e lançamento comercial" no lugar de "go-live
  piloto"; "hospedagem decidida" no lugar de "a decidir"), com o nome do arquivo mantido por causa dos links.
- Índice: linhas do ADR-0045 (Aceito, 28/10/2026) e do ADR-0046 (Proposto, 28/10/2026).
- Referências: acrescentar `docs/levantamento/certificacoes/21a` e `21b` (base do ADR-0041), `docs/levantamento/lacunas/22a`
  a `22d` (base do ADR-0042 e dos ajustes da v2.18), `docs/levantamento/revisao-geral/23a` a `23e` (base dos ADRs 0043 a
  0046) e `docs/normas`.
