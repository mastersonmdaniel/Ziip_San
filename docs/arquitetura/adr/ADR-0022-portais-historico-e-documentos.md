# ADR-0022 — Histórico e documentos no portal do consumidor e na área do tenant

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0003 (NFAg-e e NFS-e), ADR-0008 (registro de acesso), ADR-0015 (retenção), ADR-0016 (marca do
  tenant), ADR-0018 e ADR-0019 (Central e cobrança por contrato); ADR-0006 e ADR-0007 (sessão e MFA); decisões D5/D6
  do plano (realm único); plano P2.8, P2.19, P3.3, P3.20; RNF-11, RNF-41, RNF-60; `docs/arquitetura/CENTRAL-ZIIP.md`
  §6.4
- **Ajuste de 12/10/2026 (plano v2.2), antes de qualquer implementação:** item 4 deixa de remeter ao desenho da P3.3 e
  registra a decisão do dono do produto sobre conta e vínculo do consumidor; item 7 incluído (documentos anteriores ao
  go-live); nomes técnicos em inglês (plano §8, Idioma).
- **Ajuste de 12/10/2026 (plano v2.3), antes de qualquer implementação:** item 4 passa a definir o login do
  consumidor por CPF/CNPJ ou e-mail, com senha, sem SMS nem login por celular no go-live, com os cuidados de LGPD do
  documento como identificador e o mapeamento de login por atributo no Keycloak como item técnico do P3; decisão do
  dono do produto.

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
4. **Acesso do consumidor** (decisão do dono do produto, 12/10/2026). Só consumidor autenticado e vinculado à
   matrícula ou ao imóvel vê o histórico e baixa documentos; nunca dados de outro titular da mesma ligação ou de
   ligação anterior ao vínculo. Cada download vai para o `personal_data_access_log` (ADR-0008), com limite de taxa por
   consumidor e por IP (429).
   - **Conta própria no Keycloak** (login redefinido pelo dono do produto, plano v2.3): o consumidor entra com o
     **CPF** (ou o **CNPJ**, se pessoa jurídica) **ou o e-mail** da conta, e a senha; MFA opcional (TOTP ou
     WebAuthn/passkey, ADR-0007, item 4). Não há SMS nem login por celular no go-live; celular e WhatsApp ficam só
     como canal de notificação futuro (P4). Uma conta pode reunir vários imóveis.
   - **Documento como identificador de login** (LGPD, coerente com o ADR-0008): o CPF/CNPJ da conta é guardado
     cifrado e com índice cego, como no cadastro, e a conta é localizada pelo índice cego do valor digitado, nunca pelo
     valor em claro. O documento nunca vai em URL (inclusive `login_hint`) nem em log, o que alcança os eventos de
     login do Keycloak (RNF-52). Mensagens de erro genéricas: documento ou e-mail inexistente e senha errada recebem a
     mesma resposta, no mesmo tempo, e a recuperação de senha responde igual exista a conta ou não. Tentativas
     limitadas por identificador e por IP, com bloqueio progressivo, 429 e alerta de volume anômalo na Central.
   - **Mapeamento de login por atributo** (item técnico do P3, plano P3.3): o Keycloak não traz login por atributo
     cifrado pronto; um autenticador próprio do realm `ziipsan-portal` recebe o documento, calcula o índice cego e
     localiza a conta. A chave desse índice cego é definida no mesmo item, porque a conta do portal não pertence a um
     tenant e não pode usar a chave de um tenant.
   - **Usuários do portal separados dos do backoffice, em realm próprio** (`ziipsan-portal`), um só para todos os
     tenants. O realm `ziipsan` continua único para backoffice e plataforma (D5/D6). Escolha e motivos: o realm do
     backoffice aplica MFA por política de tenant em fluxo condicional (ADR-0007), atribui papéis e o claim
     `tenant_id` pela gestão de usuários da P0.13 e atende algumas centenas de operadores; o portal precisa de
     cadastro aberto, recuperação de senha pelo próprio consumidor, MFA opcional e volume de centenas de milhares de
     contas. No mesmo realm, um erro de fluxo ou de papel daria a uma conta de consumidor acesso ao backoffice e o
     cadastro aberto ficaria ao lado das contas de operador. Keycloak Organizations, em avaliação para a marca do
     tenant no login (P0.38), separa tenants do backoffice, não tipos de usuário. Realm por tenant segue rejeitado
     (ADR-0007): o consumidor não pertence a um tenant só e haveria um realm a mais por cliente.
   - **Sessão e emissor:** o portal usa o BFF do ADR-0006 com client confidencial próprio no realm `ziipsan-portal`;
     a API aceita o emissor do portal só nas rotas do portal e recusa o do backoffice nelas, e vice-versa. O token do
     portal não traz `tenant_id`: o tenant vem do subdomínio resolvido pelo BFF (MARCA-E-TEMAS §3.3), e toda leitura
     exige vínculo ativo do `sub` do consumidor naquele tenant (tenant `V8_2`).
   - **Vínculo com o imóvel:** comprovado pelo **código de vinculação impresso na conta** mais o **CPF/CNPJ do
     titular** atual. O código é por imóvel, não deriva da matrícula, é guardado só como hash e é invalidado na troca de
     titularidade, que também revoga os vínculos existentes. O documento informado é comparado pelo índice cego, sem
     gravar o valor em claro.
   - **Limites na vinculação** (anti-enumeração, coerente com o ADR-0008): resposta igual para matrícula inexistente,
     código errado e documento errado; tentativas limitadas por conta, por IP e por tenant, com bloqueio progressivo e
     429; cada tentativa registrada no `personal_data_access_log`; alerta de volume anômalo por tenant na Central.
5. **Marca e acessibilidade.** PDFs com a marca do tenant (ADR-0016) e o modelo de conta aprovado; texto real e
   estrutura marcada no PDF; páginas do portal em WCAG 2.1 AA e eMAG (RNF-60).
6. **Área do tenant na relação com o Ziip** (P2, antes do primeiro contrato): o prestador vê o histórico de faturas e
   pagamentos do contrato SaaS (ADR-0019) e baixa a fatura paga e a NFS-e da venda em PDF e XML. Acesso do
   administrador do tenant e de papel próprio de consulta do contrato; o tenant só vê o próprio contrato. O mesmo
   pipeline de integridade do item 3 vale para o XML da NFS-e.
7. **Documentos anteriores ao go-live** (decisão do dono do produto, 12/10/2026). Na migração, o importador de legado
   (plano P2.19) recebe os XML autorizados que o cliente tiver, do sistema antigo ou do emissor usado no intervalo
   entre a obrigatoriedade da NFAg-e e o corte. Cada XML é validado (assinatura, protocolo de autorização, emitente
   igual ao do tenant, duplicidade) e guardado sem alteração no mesmo armazenamento imutável, com hash e origem; o
   recusado vai para o relatório do lote. O PDF das contas antigas é gerado a partir dos dados importados (P2.10), com
   a indicação de que foi gerado do histórico importado. O portal mostra os 120 meses numa só lista e indica a origem
   de cada documento: emitido pelo Ziip SAN, importado do sistema anterior ou importado do emissor do intervalo.

## Consequências

### Positivas

- O morador obtém comprovante e documento fiscal sem ir ao atendimento, o que reduz demanda do R7.
- O XML original guardado de forma imutável serve de prova perante fisco, agência e consumidor.
- O tenant liquida a despesa do SaaS com documentos tirados do próprio produto.

### Negativas

- Mais superfície exposta à internet com dado pessoal; exige vínculo bem provado, limite de taxa e pentest (P3.8).
- Contas anteriores ao go-live dependem da qualidade do histórico importado; NFAg-e anterior ao go-live só aparece se
  o cliente entregar os XML autorizados, e o que faltar não é reconstruído.
- Um segundo realm a operar (tema, políticas de senha, backup e atualização junto do Keycloak da P0.29).
- O login por CPF/CNPJ exige o autenticador próprio do item 4, a manter a cada atualização do Keycloak, e torna o
  documento um identificador exposto à tentativa de enumeração; a mitigação é a do item 4 e o pentest (P3.8). Sem SMS
  no go-live, não há provedor de SMS como suboperador nessa fase, e a recuperação de senha depende do e-mail da conta.
- A conta do portal é uma só por pessoa e pode reunir imóveis de mais de um tenant; o papel do Ziip sobre os dados da
  conta (CPF/CNPJ, e-mail, credenciais) precisa constar do DPA (P3.9).
- O código de vinculação passa a ser impresso em toda conta (P2.8), o que exige cuidado com o arquivo da gráfica.
- O bloqueio de retenção do armazenamento convive com a eliminação no fim do contrato: o objeto é cifrado com a chave
  do tenant (RNF-32), e a destruição da DEK o torna ilegível (RNF-43); o certificado de descarte registra a data de
  expiração do bloqueio.

### O que muda no código

- Tenant `V8_2` (portal): vínculo da conta do consumidor (`sub` do realm `ziipsan-portal`) com a matrícula, com
  método de comprovação, data e revogação; hash do código de vinculação por imóvel; tentativas de vinculação.
- Realm `ziipsan-portal`, client confidencial do BFF do portal e validação do emissor por grupo de rotas na API.
- Autenticador de login por CPF/CNPJ no realm `ziipsan-portal` (índice cego do valor digitado), documento cifrado na
  conta e filtro que retira o identificador digitado dos eventos de login do Keycloak.
- Serviço de documentos do portal: PDF de conta paga e DANFAG sob demanda, XML lido do armazenamento com conferência do
  hash; registro de acesso e limite de taxa; origem de cada documento.
- Tenant `V14_3` (importação de legado): lotes de XML anteriores ao go-live, resultado da validação e origem.
- Core `V10_1`: faturas e pagamentos do contrato SaaS com referência aos documentos (PDF da fatura, XML e PDF da NFS-e).
- Rotas do tenant para a área do contrato, com filtro pelo `tenant_id` do token.

### O que muda no roadmap

- P2: área do tenant junto da cobrança por contrato (ADR-0019); cópia imutável do XML autorizado depois da NFAg-e;
  código de vinculação na conta impressa (P2.8); importação dos documentos anteriores ao go-live (P2.19).
- P3: realm e conta do consumidor (P3.3); vínculo, histórico e documentos no portal (P3.20).

## Alternativas consideradas

- **Regerar o XML a partir dos dados.** Rejeitada: o XML sem a assinatura e o protocolo originais não é o documento
  autorizado.
- **Guardar o PDF de toda conta emitida.** Rejeitada como regra: volume alto para um documento derivável dos dados
  gravados; cache admitido.
- **Histórico só dos últimos 12 ou 24 meses.** Rejeitada: o piso legal de guarda é de 120 meses e o consumidor tem
  direito de acesso aos próprios dados.
- **Documentos só por e-mail, sem portal.** Rejeitada: sem controle de acesso nem registro, e e-mail não leva anexo
  com dado pessoal (ADR-0020, item 5).
- **Consumidores no realm do backoffice.** Rejeitada: mistura cadastro aberto com contas de operador e torna a
  separação dependente de fluxo e papel corretos (item 4).
- **Organização do Keycloak para consumidores no realm do backoffice.** Rejeitada: Organizations agrupa membros de uma
  organização; não isola políticas de senha, de MFA e de cadastro por tipo de usuário.
- **Acesso só por matrícula e CPF, sem conta.** Rejeitada: matrícula e CPF circulam em papel e em outros sistemas, não
  provam posse da conta, e não há sessão para limitar e registrar.
- **Login por celular com verificação por SMS no go-live** (versão anterior deste ADR). Rejeitada pelo dono do
  produto em 12/10/2026. Pesavam contra ela, já nas consequências desta decisão, a verificação por SMS que o Keycloak
  não traz pronta e o provedor de SMS como suboperador; o celular fica como canal de notificação futuro (P4).
- **Mostrar só documentos emitidos pelo Ziip SAN.** Rejeitada: o morador perderia o histórico anterior ao go-live,
  que o piso de 120 meses alcança.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Cópia do XML autorizado em armazenamento com retenção imutável e hash na trilha | P2 (depois da NFAg-e e da hospedagem) |
| Área do tenant: histórico de faturas e pagamentos do contrato; fatura paga e NFS-e em PDF e XML | P2, antes do primeiro contrato |
| Código de vinculação por imóvel impresso na conta e no arquivo da gráfica | P2 (P2.8) |
| Importação dos XML autorizados anteriores ao go-live e PDF das contas antigas a partir dos dados importados | P2 (P2.19), repetida na carga final (GL.1) |
| Realm `ziipsan-portal`, conta do consumidor com login por CPF/CNPJ ou e-mail e senha, e client do BFF do portal | P3 (P3.3) |
| Mapeamento de login por atributo (CPF/CNPJ) no Keycloak: autenticador com índice cego, chave própria do portal, documento fora de URL e de log, resposta genérica e limite de tentativas | P3 (P3.3, item técnico) |
| Vínculo por código e CPF/CNPJ com limites; histórico de 120 meses com a origem de cada documento, conta paga com quitação, DANFAG e XML; registro de acesso e limite de taxa | P3 (P3.20) |
| Pentest do portal cobrindo vínculo, enumeração de matrícula, de código e de CPF/CNPJ ou e-mail no login, troca de token entre realms e download | P3 |
