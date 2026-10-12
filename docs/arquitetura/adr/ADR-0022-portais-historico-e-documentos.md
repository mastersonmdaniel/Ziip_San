# ADR-0022 — Histórico e documentos no portal do consumidor e na área do tenant

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0003 (NFAg-e e NFS-e), ADR-0008 (registro de acesso), ADR-0015 (retenção), ADR-0016 (marca do
  tenant), ADR-0018 e ADR-0019 (Central e cobrança por contrato); plano P3.3; RNF-11, RNF-41, RNF-60;
  `docs/arquitetura/CENTRAL-ZIIP.md` §6.4

## Contexto

O portal do cliente final (P3.3) prevê 2ª via, Pix e protocolo. Não prevê histórico nem a entrega dos documentos já
quitados: a conta paga, que o morador usa como comprovante, e a NFAg-e (modelo 75), que passa a substituir a Nota
Fiscal/Conta de Água (ADR-0003). O histórico de leitura e faturamento tem piso de 120 meses (NR 11 art. 22,
ADR-0015), e o importador v1 (P2.10) traz esse período do legado.

Do lado do Ziip, o tenant paga o SaaS por contrato (ADR-0019) e precisa da fatura e da NFS-e da venda para a
liquidação da despesa. Hoje a Central só prevê o lado do financeiro do Ziip.

## Decisão

1. **Portal do consumidor final** (P3), além da 2ª via e do Pix:
   - histórico de contas e pagamentos de até 120 meses, o mesmo piso de `ret.billing_history_months`;
   - download da conta paga em PDF, com indicação de quitação (data, valor e canal do pagamento);
   - download da NFAg-e em PDF (documento auxiliar, DANFAG) e em XML autorizado.
2. **Documentos antigos sob demanda.** PDFs de conta e DANFAG podem ser gerados no pedido, a partir dos dados
   guardados (conta com as versões de parâmetro e tarifa gravadas; XML autorizado). O **XML autorizado é sempre o
   original**: nunca é regerado; fica no armazenamento de objetos com retenção imutável e é servido como foi gravado.
3. **Integridade do XML.** O SHA-256 gravado na autorização (`fiscal_documents_xml`, FISCAL-PORTE-JAVA §3.8) é
   conferido a cada download; divergência impede a entrega e abre alerta. A resposta leva o hash, `Content-Disposition:
   attachment` e `X-Content-Type-Options: nosniff`.
4. **Acesso do consumidor.** Só consumidor autenticado e vinculado à matrícula ou ao imóvel vê o histórico e baixa
   documentos; nunca dados de outro titular da mesma ligação ou de ligação anterior ao vínculo. O método de
   autenticação e de comprovação do vínculo fica definido no desenho do P3.3 (pendência do plano §2.4). Cada download
   vai para o `personal_data_access_log` (ADR-0008), com limite de taxa por consumidor e por IP (429).
5. **Marca e acessibilidade.** PDFs com a marca do tenant (ADR-0016) e o modelo de conta aprovado; texto real e
   estrutura marcada no PDF; páginas do portal em WCAG 2.1 AA e eMAG (RNF-60).
6. **Área do tenant na relação com o Ziip** (P2, antes do primeiro contrato): o prestador vê o histórico de faturas e
   pagamentos do contrato SaaS (ADR-0019) e baixa a fatura paga e a NFS-e da venda em PDF e XML. Acesso do
   administrador do tenant e de papel próprio de consulta do contrato; o tenant só vê o próprio contrato. O mesmo
   pipeline de integridade do item 3 vale para o XML da NFS-e.

## Consequências

### Positivas

- O morador obtém comprovante e documento fiscal sem ir ao atendimento, o que reduz demanda do R7.
- O XML original guardado de forma imutável serve de prova perante fisco, agência e consumidor.
- O tenant liquida a despesa do SaaS com documentos tirados do próprio produto.

### Negativas

- Mais superfície exposta à internet com dado pessoal; exige vínculo bem provado, limite de taxa e pentest (P3.8).
- Contas anteriores ao go-live dependem da qualidade do histórico importado; NFAg-e anterior ao go-live só existe se o
  piloto entregar os XML do emissor que usou no intervalo.
- O bloqueio de retenção do armazenamento convive com a eliminação no fim do contrato: o objeto é cifrado com a chave
  do tenant (RNF-32), e a destruição da DEK o torna ilegível (RNF-43); o certificado de descarte registra a data de
  expiração do bloqueio.

### O que muda no código

- Tenant `V8_2` (portal): vínculo da conta do consumidor com a matrícula, com método de comprovação, data e revogação.
- Serviço de documentos do portal: PDF de conta paga e DANFAG sob demanda, XML lido do armazenamento com conferência do
  hash; registro de acesso e limite de taxa.
- Core `V10_1`: faturas e pagamentos do contrato SaaS com referência aos documentos (PDF da fatura, XML e PDF da NFS-e).
- Rotas do tenant para a área do contrato, com filtro pelo `tenant_id` do token.

### O que muda no roadmap

- P2: área do tenant junto da cobrança por contrato (ADR-0019); cópia imutável do XML autorizado depois da NFAg-e.
- P3: histórico e documentos no portal do consumidor, junto da P3.3.

## Alternativas consideradas

- **Regerar o XML a partir dos dados.** Rejeitada: o XML sem a assinatura e o protocolo originais não é o documento
  autorizado.
- **Guardar o PDF de toda conta emitida.** Rejeitada como regra: volume alto para um documento derivável dos dados
  gravados; cache admitido.
- **Histórico só dos últimos 12 ou 24 meses.** Rejeitada: o piso legal de guarda é de 120 meses e o consumidor tem
  direito de acesso aos próprios dados.
- **Documentos só por e-mail, sem portal.** Rejeitada: sem controle de acesso nem registro, e e-mail não leva anexo
  com dado pessoal (ADR-0020, item 5).

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Cópia do XML autorizado em armazenamento com retenção imutável e hash na trilha | P2 (depois da NFAg-e e da hospedagem) |
| Área do tenant: histórico de faturas e pagamentos do contrato; fatura paga e NFS-e em PDF e XML | P2, antes do primeiro contrato |
| Desenho da autenticação e do vínculo do consumidor | P3 (com a P3.3) |
| Histórico de 120 meses, conta paga com quitação, DANFAG e XML no portal; registro de acesso e limite de taxa | P3 |
| Pentest do portal cobrindo vínculo, enumeração de matrícula e download | P3 |
