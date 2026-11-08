# Ambiente de pré-treinamento — ZiipSan (ED-11)

O tenant de pré-treinamento é o ambiente onde o treinamento da ED-11
acontece. Ele é distinto dos outros dois ambientes de ensaio do
projeto, e a distinção é de origem do dado:

| Ambiente | Dono | Dado | Para quê |
|---|---|---|---|
| Homologação local | GL.1 / P3.13 | cadastro e histórico do piloto anonimizados na origem (ADR-0033) | provar a carga e o paralelo do piloto |
| Demonstração | P3.23 (perfil `demo` da P0.27) | massa sintética gerada, ≥ 10 mil unidades fictícias | demonstrar o produto e ensaiar a POC |
| **Pré-treinamento** | **ED-11** | **massa sintética OU clone mascarado do tenant (PLT-39)** | **treinar os usuários do tenant antes do go-live** |

## As duas origens permitidas (e só elas)

O aceite da ED-11: "tenant de pré-treinamento com massa sintética ou
com o clone mascarado da ED-09 (PLT-39)". A régua de prontidão está em
`PreTrainingTenant` (produto) e em `scripts/treinamento/validar-ambiente.py`
(fora da JVM), com os mesmos vereditos:

1. **Massa sintética** — tenant gerado sem nenhum dado do cliente,
   marcado como sintético, com a varredura da P0.45 limpa. É a origem
   padrão antes de existir produção para clonar (primeira implantação
   de um tenant novo, por exemplo).
2. **Clone mascarado (PLT-39)** — clone do tenant de produção gerado
   pela ED-09 com a finalidade `TRAINING`: nasce só da produção, com a
   avaliação de reidentificação do RIPD registrada antes e viável
   (`CloneGate` da ED-09), anonimizado pelo roteiro da P0.45 (sem nome,
   documento, endereço nem contato; código substituto no lugar da
   matrícula; coordenada deslocada ou no centroide, com o método
   registrado), marcado como sintético e com a varredura verde. Clone
   gerado para homologação **não** é reutilizado como treinamento: a
   finalidade é parte do registro do clone.

## Guardas do ambiente (executáveis, não convencionais)

- **Marca de sintético obrigatória** e **varredura da P0.45 em zero**:
  sem as duas, o tenant não recebe turma (`READY` no SQL pendente
  exige `sweep_violation_count = 0`).
- **Emissão fiscal de produção desligada**
  (`ziipsan.fiscal.production-allowed=false`), como em todos os perfis
  locais da P0.27: exercício fiscal do treinamento é o da homologação
  fiscal (P3.7), nunca emissão real a partir da turma.
- **Nunca o tenant de produção nem o de homologação**: treinar sobre
  eles destruiria a separação de origens acima e, no caso da
  homologação, a garantia do termo de cooperação do piloto.

## Ciclo de vida

1. **Provisionar** antes da primeira turma do escopo: gerar a massa ou
   pedir o clone (via ED-09), rodar a varredura e registrar o manifesto
   (`MANIFESTO-PRE-TREINAMENTO.template.json`) validado pelo script
   deste pacote.
2. **Usar** durante as turmas: as unidades guiadas e práticas de todos
   os módulos do escopo rodam aqui, inclusive as práticas `handsOn` da
   capacitação da TI.
3. **Repor entre turmas**, quando a turma seguinte precisa do estado
   inicial: a reposição é re-gerar a massa ou gerar clone novo — nunca
   "limpar à mão" o que uma turma fez, porque o estado inicial faz
   parte do roteiro.
4. **Descartar** ao fim da implantação: o tenant de pré-treinamento não
   vira produção nem homologação. Prazo e forma do descarte:
   **[A DEFINIR PELO DONO: prazo de retenção e forma de descarte do
   tenant de pré-treinamento após o go-live do tenant]**.

## O que este pacote não executa

Provisionar o tenant de verdade (criar schema, gerar a massa no banco,
executar o clone linha a linha e rodar a varredura sobre o banco) é da
integração, como nas frentes ED-09/P3.23: aqui estão a régua testável,
o manifesto, o validador e o SQL de governança
(`platform.pretraining_tenant`). Nenhum tenant de pré-treinamento foi
criado por esta entrega e nenhum resultado de varredura real é
afirmado — eles nascem das execuções no ambiente.
