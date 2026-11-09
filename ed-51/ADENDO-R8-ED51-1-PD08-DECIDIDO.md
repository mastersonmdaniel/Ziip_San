# ADENDO R8-ED51-1 — PD-08 DECIDIDO (item 40): documentos da ED-51 desatualizados

- **Data:** 09/11/2026.
- **Sessão produtora:** `510a8f95-7642-4808-bae2-9df7c5d6bdf1` (lote R8-A, adendo datado — os originais não são reescritos).
- **Artefactos adendados:** documentos da ED-51 (`ed-51/`).
- **Achado:** R8-ED51-1 (moderado, documentação, Rodada 8 da auditoria contínua), confirmado pela mesa.

## 1. O facto novo

**Decisão 40** (`DECISOES-DO-DONO.md` §16, dono, 09/11/2026 03:10 −03, cartão de decisão PD-08, sessão principal): **CONSTRUIR o PD-08** — o pacote de validação por versão (apoio à ISO/IEC 17025) da ED-51, pendência P-ED51-01, deliberadamente não construído até esta decisão.

**Execução da decisão:** pacote próprio `ed-51-pd08/`, construído sob a esteira sobre o software entregue da ED-51 — 3 classes + 3 tabelas (`lims_system_validation*`), JUnit **9/9**, revisão **APROVADO** com pendências (`REVISAO-ED-51-PD08.md` §"VEREDITO FORMAL"), confirmação **CONFIRMADO** (`CONFIRMACAO-ED-51-PD08.md:150`), portão registado (`PORTAO-REGISTRO-ED-51-PD08.md`). Pronto em espera para a fila pós-429, **imediatamente após a ED-51** (fila §5, #3).

## 2. O que os documentos da ED-51 ainda afirmam (verdadeiro à produção, desatualizado agora)

| Ficheiro | Linha(s) | Afirmação (verbatim, exceto o essencial) |
|---|---|---|
| `ed-51/RELATORIO-ED-51.md` | 17 | "PD-08 pendente." |
| `ed-51/RELATORIO-ED-51.md` | 108 | "Pacote de validação do sistema por versão pendente de decisão do dono (PD-08) — **NÃO construído**, por mandado expresso do aceite" |
| `ed-51/RELATORIO-ED-51.md` | 170–172 | "P-ED51-01 (PD-08, decisão do dono): pacote de validação do sistema por versão — **não construído** por mandado do aceite; **aguarda a decisão do dono (PD-08)**." |
| `ed-51/RELATORIO-ED-51.md` | 204–205 | "Nenhuma decisão nova foi tomada nesta frente. A PD-08 permanece pendente por mandado do aceite (P-ED51-01)." |
| `ed-51/entrega/docs/laboratorio/ED-51-LIMS-17025.md` | 32–33 | "O **pacote de validação do sistema por versão NÃO é construído** (PD-08, decisão do dono pendente) — pendência P-ED51-01." |
| `ed-51/entrega/docs/laboratorio/ED-51-LIMS-17025.md` | 70 | "Pacote de validação do sistema por versão = **PD-08** (decisão do [dono…])" |
| `ed-51/REVISAO-ED-51.md` | 70 | "PD-08 pendente (item 4 desta revisão) — **Confirmado: o pacote de validação por versão NÃO foi construído** … Construí-lo seria desvio do aceite" |
| `ed-51/CONFIRMACAO-ED-51.md` | 49–53 | "P-ED51-01 (PD-08 não construído — decisão do dono) consta da revisão … a minha amostragem por grep confirma que PD-08 só aparece na entrega como declaração de [pendência…]" |

Verificação executada: cada linha acima foi lida no ficheiro original antes de escrever este adendo. Todas as afirmações eram **verdadeiras à data da produção da ED-51**; estão **desatualizadas** desde a decisão 40 (03:10 −03).

## 3. Estado correto a partir deste adendo

- **P-ED51-01 RESOLVIDA no âmbito do pacote:** a decisão do dono foi tomada (item 40, CONSTRUIR) e o pacote `ed-51-pd08` foi construído, revisto e confirmado. Nenhum artefacto em `ed-51/` cita o item 40 — este adendo é a remissão.
- **P-ED51-02 permanece nomeada:** fiação/aplicação real — ver §4.3 do relatório do bloco de conclusão (`conclusao/RELATORIO-CONCLUSAO.md:110`).
- **Risco que este adendo elimina:** quem ler a ED-51 no pouso sem este adendo conclui que o PD-08 segue indeciso e pode bloquear o pouso do `ed-51-pd08` (que pousa DEPOIS da ED-51) ou re-perguntar ao dono uma decisão já tomada.
- Os originais da ED-51 **não foram reescritos**: as afirmações acima ficam como registo histórico do estado à produção; este adendo é a correção datada.
