# ADR-0039 — Unidade consumidora como raiz do domínio; toda solicitação ou alteração gera OS

- **Status:** Aceito, 14/10/2026
- **Decisor:** dono do produto (diretriz de 14/10/2026)
- **Afeta:** ADR-0002, ADR-0022, ADR-0031, ADR-0032, ADR-0034, ADR-0038; plano v2.14 (nova P1.31; P1.2, P1.3, P2.20,
  P2.23, P3.3, P3.4, P3.20, ED-13, ED-16, ED-36); mapa Flyway (`V3_6`, `V9_1`, `V9_2`); levantamentos
  `docs/levantamento/gsan/20a` e `20d`

## Contexto

O código atual já tem `consumer_units` como tabela central do cadastro (`V1_1`, `V3_1`), com `unit_customers`,
`unit_subcategories` e `unit_status_history`, mas o resto do domínio não a trata como raiz de forma uniforme: a OS
(`service_orders`, `V1_1`) tem código de serviço sem chave estrangeira e situação livre, a ligação só vira entidade na
P2.23 e alterações cadastrais são gravadas direto nas tabelas.

No GSAN, toda mudança de situação da ligação é efeito de uma OS tipada, aplicada no encerramento com execução,
idempotente e sem reversão (20a, 20d). Alterações cadastrais, porém, são diretas, e a trilha depende de interceptor que
falha em silêncio em alguns casos (20d).

O dono do produto definiu: a unidade consumidora é o pilar de toda a solução; tudo começa com o cadastro de uma
unidade consumidora e do cliente vinculado a ela; e toda solicitação ou alteração de cliente ou de unidade consumidora,
inclusive alteração cadastral, gera uma ordem de serviço, mesmo que interna e sem ida ao cliente.

## Decisão

1. **Unidade consumidora como raiz.** `consumer_units` é a raiz do domínio comercial e operacional. Ligações de água e
   de esgoto, hidrômetros, leituras, contas, débitos e créditos, pagamentos, parcelamentos, tarifa social, situações
   especiais, protocolos de atendimento, OS, documentos, vínculo com o portal e feições do GIS ligadas a imóvel e ramal
   referenciam a unidade consumidora. Nos documentos, "unidade consumidora" equivale à "unidade usuária" da NR 11/2024
   (art. 3º, XIII) e ao "imóvel" do GSAN e dos textos anteriores do plano.
2. **Cliente sempre por vínculo.** O cliente se liga à unidade consumidora por vínculo com papel e vigência
   (`unit_customers`: proprietário, usuário titular da fatura, responsável, outros papéis do catálogo). Cliente pode
   existir com mais de uma unidade; a fatura sai em nome do usuário titular (NR 11, art. 33 §2º).
3. **Tudo começa por uma unidade consumidora.** Pedido de ligação nova, de viabilidade ou de qualquer serviço em local
   ainda sem cadastro cria a unidade consumidora em situação provisória (`PROVISIONAL`, já existente no `V3_1`), e o
   pedido segue como OS sobre ela.
4. **Toda solicitação ou alteração gera OS.** Qualquer solicitação ou alteração de dado do cliente ou da unidade
   consumidora gera uma OS tipada, seja pelo backoffice, pelo atendimento, pelo portal ou pelo app do consumidor, pelo
   app de campo, por importação ou por integração. A OS pode ser:
   - **de campo**, com programação e execução (ligação, corte, religação, troca de hidrômetro, vistoria);
   - **interna**, sem ida a campo e sem envio ao cliente, encerrada no backoffice (alteração cadastral, troca de
     titularidade, alteração de vencimento, inclusão em tarifa social, situação especial).
5. **Efeito aplicado pelo encerramento.** O efeito no cadastro, na ligação ou no faturamento é aplicado pelo
   encerramento com execução da OS, numa transação, com marca de efeito aplicado que impede aplicar duas vezes (padrão
   do GSAN, 20d, seção B.1). Não há reversão: corrigir é abrir outra OS com o efeito inverso, ligada à primeira.
6. **Gravação direta proibida.** Nenhum caminho do sistema altera cliente ou unidade consumidora fora do efeito de uma
   OS. Os históricos (`unit_status_history` e os novos de cadastro) passam a ter `service_order_id` obrigatório, e um
   teste de arquitetura recusa escrita nessas tabelas fora do aplicador de efeitos.
7. **Protocolo e OS.** Toda solicitação recebe protocolo (NR 11, art. 52 §1º) e gera ao menos uma OS; a alteração feita
   pelo próprio backoffice gera OS interna sem protocolo de atendimento. Confirmado pelo dono do produto em 28/10/2026: o protocolo vem sempre antes da OS em
   toda solicitação externa, por qualquer canal (portal, aplicativo, atendimento, telefone, integração).
8. **Aprovação da OS interna é parâmetro (ADR-0038).** Por tipo de serviço, o tenant escolhe se a OS interna é aplicada
   por quem a registra ou se exige aprovação por outra pessoa. Padrão de fábrica: aprovação por outra pessoa para
   alterações que mudam o faturamento (categoria, economias, titularidade, situação da ligação, tarifa social, consumo
   mínimo, vencimento) e aplicação direta para dados de contato.
9. **Carga e lote.** A importação do legado (P1.2) e os lotes (recadastramento, ED-16; OS seletiva) geram uma OS interna
   de lote, que referencia cada unidade alterada e aplica os efeitos com a mesma regra; não se gera uma OS por registro
   na carga inicial.
10. **Faturamento e cobrança não são alteração cadastral.** Emitir conta, classificar pagamento ou calcular encargos não
    gera OS; corte, religação, parcelamento pedido pelo usuário, transferência de débito, devolução e retificação de
    conta geram.

## Consequências

### Positivas

- Trilha completa e uniforme: cada mudança de cliente ou de unidade tem OS, autor, aprovador, data, motivo e efeito.
- A regra vale igual para todos os canais, inclusive portal e importação.
- Atende NR 11 (art. 22, cadastro e histórico; art. 52, protocolo) e LGPD (art. 18, correção com registro).
- A OS passa a ser o mecanismo único de mudança, o que simplifica a auditoria e o relatório regulatório.

### Negativas

- O núcleo de OS sai do P3 e vai para o P1, antes do cadastro completo (P1.3): mais trabalho no P1.
- Alteração simples (telefone, e-mail) passa por OS; a tela precisa tornar a OS interna transparente para o atendente.
- Volume maior de OS; relatórios e painéis precisam separar OS de campo de OS interna.

## Alternativas consideradas

- Alteração cadastral direta com auditoria por interceptor (como o GSAN): rejeitada pelo dono do produto.
- OS só para mudança de situação da ligação: rejeitada pelo dono do produto.
- Uma OS por registro na carga inicial: preterida pelo volume; a OS de lote preserva a regra.

## Tarefas derivadas

- Nova P1.31: núcleo de OS (tipos de serviço com operação, efeito e indicador interno; máquina de estados; aplicador de
  efeitos; OS de lote; aprovação por tipo), `V9_1` antecipada do P3 para o P1.
- P1.3: cadastro completo só por OS; `V3_6` com pedidos de alteração campo a campo e `service_order_id` nos históricos.
- P1.2: carga como OS interna de lote.
- P3.3 e P3.20: alterações pelo consumidor no portal geram OS interna a partir do protocolo.
- P3.4: corte e religação sobre o núcleo da P1.31 (`V9_2` com os bloqueios).
- ED-13, ED-16 e ED-36: ligação, recadastramento e OS completa sobre o mesmo núcleo.
