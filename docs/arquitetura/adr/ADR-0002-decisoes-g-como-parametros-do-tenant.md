# ADR-0002 — Decisões G1, G2, G4, G5, G6 e G9 como parâmetros do tenant

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` §2.2 (G1–G9); revisão 17, achado X5 e §8.4
- **Detalhamento dos parâmetros:** `docs/arquitetura/PARAMETROS-DO-TENANT.md`
- **Ajuste de 14/10/2026 (plano v2.14):** caminhos alternativos admitidos por norma ou prática viram opções do mesmo parâmetro, com padrão de fábrica mais protetivo ao usuário (ADR-0038).

## Contexto

O plano de execução (§2.2) trata G1–G9 como decisões de negócio que bloqueiam fases do roadmap e atribui a maioria a um
jurídico ou regulatório que não existe: nenhum documento nomeia responsável (`00` Q5), e a revisão 17 (X5) recomendava
contratar jurídico e tributário até 31/10/2026.

A maior parte dessas decisões não é do produto. Agência reguladora, critérios de tarifa social, convênios bancários,
multa e juros, prazo de retenção e regras de corte variam por prestador, por agência e por norma local, e mudam no
tempo. Quem responde por elas é o prestador (tenant), que é também o controlador dos dados (LGPD). Se o produto fixar
esses valores, assume uma responsabilidade que não é dele e precisa de um código diferente por cliente.

O plano já previa a estrutura: `V1_5` com `regulatory_profiles`, `regulatory_parameters` (com vigência) e `holidays`
(§3), e a regra de que parâmetro sem valor gera erro explícito (§2.2, último parágrafo). Hoje o código só tem
configuração chave/valor sem vigência nem aprovação (`public.tenant_settings`, `shared/sigsan-core/src/main/resources/db/migration/core/V1__create_core_schema.sql:107`;
`tenant_metadata.settings`, `platform/sigsan-platform/src/main/resources/db/migration/tenant/V1_1__create_tenant_schema.sql:17`).

## Decisão

1. G1, G2, G4, G5, G6 e G9 deixam de ser decisões do produto e passam a ser **parâmetros do tenant**, preenchidos na
   implantação:

   | Decisão | Parâmetros do tenant (exemplos) |
   |---|---|
   | G1 | Agência reguladora; norma de referência (número, data, vigência); adesão às NR 11 e 13 da ANA; perfil regulatório |
   | G2 | Critérios da tarifa social (renda per capita em fração do salário mínimo, validade do CadÚnico, BPC); desconto por faixa; incidência na parte fixa e no esgoto; carência de perda; formato da base recebida; base legal do tratamento de NIS e renda |
   | G4 | Bancos e convênios; layout de arrecadação e de cobrança; Pix habilitado; PSP; Pix Automático |
   | G5 | Percentual de multa; juros (pro rata die ou mensal) e taxa; índice de correção |
   | G6 | Prazo de retenção por categoria de dado e de documento; marco inicial; prazo de prescrição por esfera |
   | G9 | Janela de horário do corte; dias vedados; motivos de corte além da inadimplência; tratamento de débito pretérito; prazos do SAC |

2. Cada parâmetro tem:
   - **definição mantida pelo produto**: tipo, unidade, valor sugerido, obrigatoriedade e norma-fonte do valor sugerido;
   - **limites legais validados pelo sistema**, num catálogo versionado com a norma de origem (exemplo: teto de 2% para
     multa moratória, CDC art. 52 §1º, conforme `14-VALIDACAO-REGULATORIA-CONSOLIDADA.md` item 2). Valor fora do limite
     é recusado; valor dentro do limite e diferente do sugerido exige justificativa;
   - **vigência** (`valid_from`, `valid_to`), sem sobreposição garantida no banco (EXCLUDE, como já se faz nas tarifas);
     alteração gera nova versão, nunca edita a anterior;
   - **aceite formal registrado**: quem aprovou (usuário do tenant com papel próprio, nome e cargo), quando, qual versão
     do conjunto de parâmetros (hash do conteúdo canônico) e, opcionalmente, o ato que embasa (portaria, resolução, ata).

3. Sem valor aceito e vigente, a operação que depende do parâmetro falha com erro explícito, e o tenant não passa a
   produção sem o conjunto obrigatório aceito.
4. O produto não depende de jurídico próprio para avançar. G3, G7 e G8 continuam decisões do produto (ADR-0003 e
   ADR-0004).
5. Contrato SaaS, DPA e termos de uso são tarefa anterior ao primeiro contrato, e não do P0. No DPA, o Ziip SAN é
   **operador**; o cliente é o **controlador** e nomeia seu encarregado. O contrato aloca ao cliente a responsabilidade
   pelos valores aceitos.

## Consequências

### Positivas

- As fases deixam de esperar decisões jurídicas que o produto não controla.
- Um mesmo código atende agências e normas diferentes; mudança de norma vira nova vigência, não nova versão do sistema.
- Toda conta calculada fica rastreável até a versão do parâmetro e o aceite que a autorizou.
- Coerente com o papel de operador na LGPD: retenção e base legal são decisões do controlador.

### Negativas

- O catálogo de limites legais é mantido pelo produto. Um limite errado (permissivo demais) deixa passar valor ilegal
  sob aparência de validação. Mitigação: cada limite cita a norma, tem teste próprio e é revisado quando a norma muda;
  o contrato deixa claro que o limite é trava mínima, não parecer jurídico.
- A implantação fica mais pesada: o cliente precisa preencher e aceitar dezenas de parâmetros. Exige assistente de
  implantação e valores sugeridos bem documentados.
- Parâmetros com vigência entram em todos os cálculos (tarifa, multa, corte, retenção), o que aumenta o custo de teste.

### O que muda no código

- `V1_5` sai do R4 e entra no P0, ampliado com `parameter_definitions`, `parameter_legal_limits`, versões com vigência e
  `parameter_acceptances`.
- `tenant_settings` e `tenant_metadata.settings` ficam restritos a configuração técnica; parâmetro regulatório não vai
  para JSONB livre.
- Motores de cálculo (billing, collection, futuros R7 e R8) recebem o parâmetro vigente na data de referência e gravam
  a versão usada no resultado.
- Papel novo para aprovar parâmetros, separado de quem cadastra (quem propõe ≠ quem aprova), nos moldes da aprovação
  dupla do R5. Usuário de suporte da plataforma em impersonação não aprova.

### O que muda no roadmap

- G1, G2, G4, G5, G6 e G9 deixam de bloquear R3, R4, R5, R7 e R8. O bloqueio passa a ser a existência do parâmetro
  modelado e testado.
- A contratação de jurídico até 31/10 (X5) é retirada do P0. Contrato SaaS, DPA e termos ficam no P3, antes do
  primeiro contrato.

## Alternativas consideradas

- **Contratar jurídico e tributário e fixar os valores no produto.** Rejeitada: custo e prazo fora do controle do
  produto; os valores continuariam variando por cliente e por agência, e o produto assumiria responsabilidade do
  controlador.
- **Configuração livre sem limites nem aceite.** Rejeitada: permite valor ilegal sem trava e não deixa prova de quem
  decidiu, o que expõe o operador em fiscalização ou litígio.
- **Esperar cada decisão G antes de iniciar a fase correspondente (modelo atual).** Rejeitada: congela o roadmap sem
  data previsível.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| `V1_5` com definições, limites legais, versões com vigência e aceite; teste de recusa fora do limite e de erro sem valor | P0 |
| Catálogo inicial de limites legais com norma-fonte (G5, G9) e valores sugeridos documentados | P0 |
| Parâmetros de G1 e G2 modelados para o R3 | P1 |
| Assistente de implantação no console da plataforma v0, com bloqueio de produção sem conjunto obrigatório aceito | P1 |
| Parâmetros de G5 e G6 consumidos pelo ciclo de faturamento, com versão gravada na conta | P2 |
| Parâmetros de G4 (R5a/R5b) e G9 (R7 mínimo) | P3 |
| Contrato SaaS, DPA (Ziip SAN operador; cliente controlador com encarregado próprio) e termos de uso | P3, antes do primeiro contrato |

## Refinamento (12/10/2026)

O [ADR-0015](ADR-0015-limites-legais-dos-parametros.md) refina o item 2 da decisão. Os parâmetros passam a ter quatro
classes (travado pelo sistema; configurável com padrão de fábrica e aceite formal fora do padrão; só ampliável; livre).
A multa moratória, citada acima como exemplo de limite com recusa, passa a ter padrão de fábrica de 2%, e valor acima
dele exige norma citada, aprovação dupla, aceite formal registrado e alerta de risco. O ADR-0015 também fixa as regras
de corte por motivo, o piso travado da tarifa social e os pisos de retenção definidos pelo Ziip SAN.
