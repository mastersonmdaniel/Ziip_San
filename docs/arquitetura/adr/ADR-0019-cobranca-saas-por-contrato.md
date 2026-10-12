# ADR-0019 — Cobrança do SaaS por contrato e licença controlada

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0003; ADR-0018; ADR-0020 (política de SLA); ADR-0022 (área do tenant); P3.9;
  `docs/arquitetura/CENTRAL-ZIIP.md` §3 e §6.2
- **Ajuste de 12/10/2026:** item 5 alinhado à decisão do dono do produto (restrição por uma pessoa com MFA); item 1
  passa a citar a política de SLA de suporte, antes de qualquer implementação.
- **Segundo ajuste de 12/10/2026 (plano v2.2), antes de qualquer implementação:** item 5 passa a dizer quem executa a
  restrição (financeiro ou dono), por decisão do dono do produto; nome da tabela de SLA em inglês (plano §8, Idioma).

## Contexto

Os clientes são autarquias, SAAEs e prestadores, em geral públicos. Contratam por licitação ou dispensa, com empenho,
vigência, aditivos e reajuste anual por índice, e pagam depois da liquidação. O modelo do Ziip Cloud (trial, Asaas,
cartão, reembolso, bloqueio automático quando a licença expira) não se aplica. O serviço do cliente é essencial:
bloquear o tenant interrompe faturamento e atendimento à população.

## Decisão

1. **Contrato na Central:** número, processo licitatório e modalidade, contratante (CNPJ), vigência, aditivos (prazo,
   valor, objeto), índice e data-base do reajuste, empenhos com saldo, contatos institucionais do contratante e a
   política de SLA de suporte com o calendário (referência a `support.sla_policy`, ADR-0020).
2. **Licença controlada:** plano, módulos e cotas (ligações ativas, usuários, armazenamento) derivam do contrato
   vigente e alimentam `tenant_subscription` e as flags de plano.
3. **Faturas** por competência ou medição prevista no contrato, emitidas pelo financeiro. A NFS-e da venda sai pelo
   motor fiscal portado (ADR-0003), com o Ziip como prestador num contexto fiscal próprio da plataforma.
4. **Pagamentos informados** (data, valor, ordem bancária) e conciliados contra as faturas. Sem gateway de cartão.
5. **Nenhum bloqueio automático.** Contrato vencido, cota excedida ou fatura em atraso geram alerta e aviso ao tenant.
   Restrição por inadimplência só manual: aviso prévio por escrito no prazo do contrato, execução por uma pessoa de
   plataforma com MFA, step-up, justificativa e auditoria, que pode ser o financeiro (`PLATFORM_FINANCE`) ou o dono
   (`PLATFORM_OWNER`), com referência à fatura vencida e ao aviso registrados; efeito limitado ao do `CENTRAL-ZIIP.md` §6.2 (backoffice em somente
   leitura; portal do consumidor, Pix, retornos bancários e 2ª via continuam) e avaliação jurídica (Lei 14.133/2021).
   Nenhuma configuração do tenant ou do contrato afrouxa as vedações de corte por inadimplência do consumidor
   (ADR-0015, item 3); a restrição do SaaS não alcança o serviço essencial.
6. **Reajuste** calculado pela Central como proposta; vale só depois de registrado o apostilamento ou aditivo.
7. **Alertas:** contrato a vencer em 180, 90 e 30 dias; saldo de empenho abaixo de duas faturas; cota em 80% e 100%;
   fatura vencida; pagamento sem fatura correspondente.

## Consequências

### Positivas

- Reflete como o cliente público compra e paga, e dá ao financeiro a prova de cada etapa.
- A continuidade do serviço essencial fica garantida por desenho, não por boa vontade.
- Reusa o motor fiscal em vez de contratar emissor ou ERP para a venda.

### Negativas

- Inadimplência pública pode durar meses sem recurso automático; o risco é financeiro e fica com o Ziip.
- Lançamento manual de pagamento exige rotina e conferência.
- O Ziip precisa de certificado e cadastro fiscal próprios dentro do produto.

## Alternativas consideradas

- **Assinatura com gateway (modelo Ziip Cloud).** Rejeitada: ente público não paga por cartão nem boleto avulso
  fora do empenho.
- **Bloqueio automático após tolerância.** Rejeitada: interrompe serviço essencial e expõe o Ziip a sanção contratual.
- **Faturar fora do produto (ERP).** Rejeitada agora; exportação contábil admitida depois.
- **Cota medida e cobrada automaticamente.** Admitida só como forma de medição, quando o contrato prever.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Tabelas de contrato, aditivo, empenho, reajuste, fatura e pagamento no schema `platform` | P2 |
| Contexto fiscal da plataforma e NFS-e da venda pelo motor portado (depende de P1.8) | P2 |
| Conciliação fatura × pagamento e alertas do item 7 | P2 |
| Teste que prova a ausência de bloqueio automático; fluxo de restrição manual por uma pessoa com MFA e justificativa (financeiro ou dono), recusado sem fatura vencida e aviso registrados | P2 |
| Área do tenant com faturas, pagamentos, fatura paga e NFS-e em PDF e XML (ADR-0022) | P2, antes do primeiro contrato |
| Modelo de contrato com cláusulas de aviso, restrição, saída e política de SLA de suporte (com P3.9) | P3 |
