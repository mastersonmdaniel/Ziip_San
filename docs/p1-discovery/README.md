# Discovery do legado — templates da P1.1-prep

> **Status: PREPARAÇÃO — NÃO É A P1.1 PLENA.**
>
> A P1.1 plena (discovery do legado) depende da P0.30 (escolha do piloto,
> decisão do dono) e de aprovações do piloto. **Nenhuma dessas existe.**
> Este diretório contém SOMENTE a parte construível sem piloto: os
> templates em branco para o discovery. Nenhum template aqui está
> preenchido com dado de piloto, valor de legado ou aprovação.
>
> **Aprovação do piloto (todos os templates): ⏳ PENDENTE — aguarda a
> decisão P0.30.** Nenhum campo de aprovação foi preenchido, e nenhum
> pode ser preenchido antes da P0.30.
>
> Origem: tarefa P1.1, linha 627 de
> [PLANO-v3.0.md](../plano-v3/PLANO-v3.0.md) (dependência declarada:
> P0.30; origem 17:X5; ADR-0028). Decisões relacionadas: PD-06 (CNAE na
> unidade consumidora, P1.3) e PD-07 (guichê de caixa próprio como item
> condicional da ED-26, ligado por parâmetro).

## Templates

| # | Template | O que a P1.1 plena exige dele |
|---|---|---|
| 01 | [Dicionário de dados do legado](01-dicionario-de-dados.md) | Dicionário de dados do legado aprovado pelo piloto |
| 02 | [Mapeamento campo a campo](02-mapeamento-campo-a-campo.md) | Mapeamento campo a campo aprovado pelo piloto |
| 03 | [Levantamento de volumes](03-levantamento-de-volumes.md) | Volumes do legado aprovados pelo piloto |
| 04 | [Optantes de débito automático e leiaute bancário](04-debito-automatico-leiaute-bancario.md) | Optantes e leiaute conferidos no legado contra o levantamento da P0.30; lista das autorizações a migrar (base da P3.24, se houver optantes) |
| 05 | [Parcela bônus (COB-36)](05-parcela-bonus.md) | Verificar se o piloto usa parcela bônus; se usar, ela entra como opção de `inst.bonus_installments`, desligada por padrão, nunca como regra fixa |
| 06 | [Guichê de caixa próprio (PD-07)](06-guiche-caixa-proprio.md) | Verificar se o piloto mantém guichê de caixa próprio; a resposta decide se o item condicional de caixa da ED-26 é ligado por parâmetro (`pay.own_cashier_enabled`, desligado por padrão) |
| 07 | [Onde o legado registra a atividade econômica — CNAE (PD-06)](07-cnae-no-legado.md) | Verificar onde o legado registra a atividade econômica (CNAE), para o mapeamento à unidade consumidora definido na P1.3 |
| 08 | [Parcelamentos ativos e prestações a vencer](08-parcelamentos-ativos-prestacoes-a-vencer.md) | Lista dos parcelamentos ativos e das prestações a vencer, base da carga da P1.2 e da P2.10 |

## Regras de preenchimento (valem para todos os templates)

1. **Só dado sintético ou anonimizado na origem.** Nenhum template
   preenchido pode trazer nome, documento, endereço, telefone ou e-mail
   real, nem matrícula real do legado. No lugar da matrícula, usa-se o
   código substituto, cuja correspondência fica SÓ no piloto, fora do
   ambiente local. Ver o
   [roteiro de anonimização](../seguranca/roteiro-anonimizacao-piloto.md)
   (P0.45, ADR-0033). Todo exemplo nestes templates está marcado
   `SINTÉTICO` e é fictício.
2. **Campo em branco = dado ainda não levantado, não dado inexistente.**
   "Não usa" / "não existe" só pode ser marcado depois de verificado no
   legado e confirmado pelo piloto.
3. **Aprovação do piloto, em cada template, é um campo próprio e
   independente.** Um template só conta como concluído quando o seu
   campo de aprovação estiver preenchido com nome, data e evidência —
   o que hoje é impossível, porque o piloto ainda não foi escolhido.
4. **Nada aqui decide nada.** Em particular: a parcela bônus nunca
   vira regra fixa (template 05); o guichê de caixa nunca é ligado por
   estes templates (template 06, decisão PD-07); nenhum parâmetro de
   tenant é alterado por estes templates.
5. **Sem inventar decisão do dono.** Onde o plano exige dado do piloto,
   o template deixa marcado como pendente da decisão P0.30.

## O que ficou pendente da escolha do piloto (P0.30)

Sem a P0.30, a P1.1 NÃO pode ser fechada. Falta, no mínimo:

- A identidade do piloto (prestador público em SC, dispensa ou CPSI,
  com cláusula de atestado) e o termo de cooperação para a homologação
  local com anonimização na origem;
- O acesso ao legado (sistema, versão, forma de extração autorizada);
- O levantamento da P0.30 a ser conferido no legado: quantidade de
  optantes de débito automático e leiaute bancário em uso, e existência
  de guichê próprio de arrecadação;
- A aprovação do piloto sobre o dicionário de dados, o mapeamento
  campo a campo e os volumes;
- As respostas verificadas no legado: parcela bônus (template 05),
  guichê de caixa próprio (template 06) e local do CNAE (template 07);
- A lista de parcelamentos ativos e prestações a vencer (template 08),
  base da carga da P1.2 e da P2.10.

Ver a lista completa, item a item, em cada template (seção
"Pendências da P0.30" de cada arquivo).
