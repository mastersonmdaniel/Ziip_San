# LC.14 — Checklist do primeiro atestado

> **Produtor:** sessão `95ff17bd-dd7b-4269-a848-c235bad7f4be` (papel: Produtor), 09/11/2026.
> **O atestado é emitido pelo piloto/cliente — ato externo.** Este checklist
> prepara e regista as condições; ele não emite atestado nenhum, e nenhum
> atestado é simulado. Não há cliente hoje (DECISOES-DO-DONO.md, item 34).

## 0. Aceite que este checklist cumpre (fonte única, verbatim)

Fonte: `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json`,
registro LC.14 (título "Primeiro atestado", origem `ADR-0023; ED-02`, tamanho P,
dep LC.13):

> "atestado emitido pelo piloto depois dos ciclos em produção previstos no
> contrato, com ligações e, se ele for autarquia de SC, remessa e-Sfinge
> registradas (ED-33); kit de habilitação atualizado (M16)"

Marco: **M16 — primeiro atestado** (marcos do ADR-0033, conforme fontes locais).
Âncora contratual: a LC.9 exige "cláusula de emissão do atestado com prazo
contado do corte (ADR-0023)" no contrato assinado com o piloto — é esse
contrato (futuro) que fixa os ciclos e o prazo; nada disso existe hoje.

## 1. Condições do aceite — uma a uma

### C1. Corte consumado (dep LC.13)
- [ ] Termo de Decisão Formal de Corte da LC.13 completo e assinado, com o
      marco M15 registado (incl. declaração de desligamento do legado pelo
      cliente) — referência: ____
- Estado hoje: **PENDÊNCIA EXTERNA** — LC.13 é evento futuro sem cliente/produção.

### C2. Ciclos em produção previstos no contrato, cumpridos
- [ ] Número de ciclos exigido: **[A DEFINIR]** — é o contrato futuro (LC.9)
      que o fixa; nenhuma quantidade é presumida neste checklist.
- [ ] Ciclos efetivamente corridos em produção, um registo por ciclo
      (ciclo / período / referência do registo de produção): ____
- [ ] Prazo do atestado contado do corte (cláusula ADR-0023 da LC.9)
      conferido contra a data do corte registada na LC.13: ____

### C3. Ligações registadas
- [ ] Quantidade de ligações do piloto ao tempo do atestado, registada com a
      fonte do número (contagem do sistema em produção na data): ____
      (o número é facto do evento — não é estimado nem projetado aqui)
- [ ] Se o uso pretendido for habilitação em licitação (kit, M16), a forma do
      registo segue o que o edital/contrato de referência exigir [A DEFINIR
      com o contrato e o uso concreto].

### C4. Condicional: piloto é autarquia de SC → remessa e-Sfinge (ED-33) registada
Apurar primeiro o facto:
- [ ] O piloto é autarquia de Santa Catarina: SIM | NÃO (fonte do facto: ____)
  - **Se NÃO:** registar neste checklist "condicional e-Sfinge não se aplica
    — piloto não é autarquia de SC" e seguir para C5.
  - **Se SIM:** exigir o registo da remessa e-Sfinge operada a partir da
    primeira produção (LC.13), pela ED-33:
    - [ ] Relatório de envio da ED-33 (`EsfingeShipmentReport`) por
          competência, com hash SHA-256, versão do leiaute e retorno —
          só conta como enviada a remessa com retorno `ACCEPTED`: ____
    - [ ] **Estado real da ED-33 (declarado a partir do pacote local
          `ed-33/`, lido nesta produção):** motor entregue e aprovado como
          **parcial honesta** — geração/validação/ficheiro canónico com
          SHA-256, registo de hash/versão/retorno e relatório de envio
          utilizável no atestado estão implementados e testados (17/17);
          **mas o leiaute oficial vigente do TCE/SC não está nas fontes**:
          toda a remessa nasce com `layoutOfficial=false` e o marcador
          `PENDENTE_LEIAUTE_OFICIAL_TCE_SC`, e o relatório expõe a pendência.
    - [ ] **Pendência que atravessa para o atestado (DECISOES-DO-DONO.md,
          itens 29/30):** obter o leiaute vigente do e-Sfinge do TCE/SC e
          resolver a divergência de periodicidade das fontes locais (plano:
          "mensal desde 01/01/2022" × ED-98: "bimestral, IN TC-28/2021").
          Enquanto ela existir, o registo da remessa para o atestado tem de
          declarar expressamente que o leiaute usado **não é o oficial** —
          um atestado que a esconda fica falso por omissão.
    - [ ] Fiação da ED-33 (porta sobre ED-31/ED-32 reais, endpoints,
          agendamento) confirmada na produção — estado na entrega local:
          pendente de integração (ver `ed-33/RELATORIO-ED-33.md`).

### C5. Kit de habilitação atualizado (M16)
- [ ] Kit atualizado na forma do modelo deste pacote
      (`KIT-HABILITACAO-MODELO-LC14.md`), com o estado de cada peça
      reverificado à data do atestado — referência/versão do kit: ____

### C6. Emissão do atestado — ato do piloto/cliente
- [ ] Atestado emitido **pelo piloto/cliente** (documento dele, no papel e
      com a assinatura dele) — referência/registo do documento recebido: ____
- [ ] Atestado arquivado junto ao Termo da LC.13 e a este checklist; o marco
      M16 só se declara com o documento do cliente arquivado.
- Nota: **nenhum termo de atestado é produzido por nós como se emitido.**
  Se o cliente pedir um texto de referência, usa-se apenas o modelo em
  branco rotulado deste pacote (`MODELO-ATESTADO-EM-BRANCO-LC14.md`), que o
  cliente adapta, emite e assina como documento dele.
