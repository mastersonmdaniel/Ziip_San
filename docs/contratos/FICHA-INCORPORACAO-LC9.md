# FICHA DE INCORPORAÇÃO POR REFERÊNCIA — LC.9

> **Produtor:** sessão `d9b12bda-ac3a-4b63-b86d-a806e37d4f49` (papel: Produtor).
> Esta ficha **não redige cláusulas**: para cada elemento que o aceite da
> LC.9 exige, ela aponta onde o elemento já está nas minutas da P3.9 ou
> regista a lacuna como **[A DEFINIR]/[SEM FONTE]** com pendência nomeada.
> Cláusula, valor, prazo ou parte sem fonte **não** entra como fato.

## F-1 — Papéis: ZiipSan operadora, cliente controlador

- **Exigência do aceite:** "Ziip SAN como operador e piloto como
  controlador" (no modelo do item 34: cliente futuro = controlador).
- **Estado nas minutas: REDIGIDO.** DPA, Cláusula 1ª ("Ziip SAN é o
  OPERADOR"; "O cliente (tenant) é o CONTROLADOR e nomeia seu próprio
  encarregado" — ADR-0002, item 5); Contrato SaaS, Cláusula 8ª (remissão
  ao DPA nos mesmos papéis).
- **Aberto (dado, não redação):** identificação e contato do encarregado
  do controlador — [A DEFINIR] na DPA, Cl. 1ª, item 2 (é um dos
  marcadores substantivos da P3.9; fecha na contratação).

## F-2 — Cláusula de emissão do atestado com prazo contado do corte (ADR-0023)

- **Exigência do aceite:** o contrato deve conter a cláusula de emissão
  do atestado, com **prazo contado do corte**.
- **Estado nas minutas: LACUNA — não há cláusula de atestado nas minutas
  da P3.9.** Conferência executada neste pacote: nenhuma das 6 minutas
  contém cláusula de emissão de atestado (o Termo de Homologação registra
  o inverso: homologação local **não** gera atestado — ADR-0033). Nem o
  arcabouço da ED-05 a continha (não figura entre as 22 ocorrências da
  ED-05). **Esta é a divergência D-LC9-01** (RELATORIO-LC.9).
- **O que as fontes fixam (e só isso entra):**
  - a **âncora** do prazo é o **corte** (aceite da LC.9; corte = LC.13);
  - o atestado é emitido pelo cliente **depois dos ciclos em produção
    previstos no contrato** (aceite da LC.14, que remete ao contrato —
    ou seja, o contrato é quem deve prever os ciclos);
  - atestado só depois do lançamento e da primeira operação real
    (ADR-0033, seção "Atestados e licitações", sobre o ADR-0023).
- **O que NÃO tem fonte — [A DEFINIR] (pendência P-LC9-01):**
  - número de ciclos em produção exigidos: **[A DEFINIR — SEM FONTE]**;
  - prazo de emissão contado do corte: **[A DEFINIR — SEM FONTE]**.
  - Nota de honestidade: o ADR-0033 cita "atestado de 3 a 5 meses depois
    do corte" como **intervalo de planejamento já admitido no plano
    anterior** (estimativa de cronograma, com incerteza declarada) —
    **não** é prazo contratual fixado e este pacote não o promove a
    cláusula.
- **Fecho:** a cláusula só pode ser redigida na contratação, por decisão
  do contratante/dono com os dois valores acima. Até lá, o conjunto está
  pronto **com esta lacuna nomeada** e o checklist de assinatura a trata
  como item bloqueante do ato (CHECKLIST, item S-06).

## F-3 — Nível de serviço escolhido: padrão × Setor Público (ADR-0025)

- **Exigência do aceite:** "nível de serviço escolhido (padrão ou Setor
  Público, ADR-0025)".
- **Estado: ESCOLHA ABERTA — é decisão do contrato futuro** (pendência
  P-LC9-02). Nenhuma fonte escolhe pelo cliente futuro; o Termo de
  Assinatura traz o campo em branco.
- **O que as minutas já sustentam para os dois lados da escolha**
  (redigido na P3.9, sem reescrita):
  - **Setor Público:** disponibilidade 99,9% a partir do lançamento;
    exportação de saída em 10 dias úteis; consulta somente leitura ≤ 120
    dias; SLA com prazo de solução, aderência e multa (SLA §§1.5, 4.1;
    Contrato, Cl. 9ª).
  - **Padrão:** sem meta de disponibilidade (calculada e exibida, sem
    veredito); exportação em 15 dias; SLA por contrato **sem multa**
    (SLA §§1.5, 4.2; Contrato, Cl. 9ª).
- **Ressalva declarada:** o Contrato SaaS da P3.9 declara encaixar-se no
  **arcabouço do nível Setor Público da ED-05**; se o contrato futuro
  escolher o nível **padrão**, as cláusulas que só valem no Setor Público
  (disponibilidade, RO, multa) já nascem condicionadas nas próprias
  minutas ("no nível Setor Público…"), mas a revisão jurídica da
  contratação deve confirmar o encaixe — registado como nota, não como
  defeito provado.

## F-4 — Declaração do risco residual de desastre regional (LC.3)

- **Exigência do aceite:** "contrato declara o risco residual de desastre
  regional (ver LC.3)".
- **Estado nas minutas: REDIGIDO.** Contrato SaaS, **Cláusula 10ª** —
  "a arquitetura cobre falha de zona, ransomware e erro humano, **não** a
  perda da região inteira; não há backup nem réplica fora de `sa-east-1`
  (LC.3; RNF-44)". Na tabela da ED-05/P3.9 este ponto (§2.9) é um dos
  **8 RESOLVIDOS**.
- **Fonte primária (aceite da LC.3, mesmo JSON-fonte):** desastre regional
  por Multi-AZ + cofre isolado **na mesma região** (PLT-04, decisão do
  dono de 22/10/2026); "a solução cobre falha de zona, ransomware e erro
  humano, não a perda da região inteira, e esse risco residual é entregue
  por escrito para o contrato do tenant e para o nível Setor Público
  (ADR-0025; ver LC.9)"; cópia em segundo provedor e pilot-light fora da
  solução, pela mesma decisão.
- **Dependência nomeada (P-LC9-04):** o pacote **LC.3 deste bloco**
  (produtor `ca6d61bf-f7f3-42dd-a535-196ff96c1069`) estava, na leitura
  deste produtor, **ainda em produção** — só o módulo IaC
  `rds-pitr` e um README mínimo, **sem** `RELATORIO-LC.3.md` nem texto
  autônomo da declaração escrita. A Cl. 10ª sustenta-se hoje no aceite da
  LC.3 + ED-05 §2.9; na integração, a declaração final da LC.3 deve ser
  conferida contra a Cl. 10ª (conferência pendente, não divergência
  provada).

## F-5 — Telemetria de produto agregada registrada no DPA (P4.8)

- **Exigência do aceite:** "DPA registra a telemetria de produto agregada
  (ver P4.8)".
- **Estado nas minutas: REDIGIDO.** DPA, **Cláusula 8ª** — telemetria
  **agregada**: "sem identificador de pessoa, sem gravação de sessão e
  sem conteúdo digitado". Na tabela da ED-05/P3.9, **RESOLVIDO** (#19).
- **Registro complementar por referência (P4.8, PLT-42):** a declaração
  da P4.8 (`p4.8/entrega/docs/telemetry/DPA-DECLARACAO-P4.8.md`), feita
  expressamente "para a LC.9 registrar", detalha o que a Cl. 8ª resume:
  métricas = adoção de funcionalidades, tempo até o primeiro ciclo,
  abandono de assistentes e tarefas mais repetidas, **em agregado por
  tenant e por papel**; parâmetro `telemetry.product_usage` (padrão
  `AGGREGATED`; `OFF` desliga); usos = aba Adoção da Central Ziip, painel
  de uso do tenant e priorização do changelog; eventos com identificador
  de pessoa ou conteúdo são **recusados** na ingestão (teste da P4.8).
  Esta ficha faz o registro por referência a essa declaração, sem
  reescrever a Cl. 8ª.
