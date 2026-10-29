# ADR-0043 — Login gov.br e pagamento sem conta no portal do consumidor

- **Status:** Aceito, 28/10/2026
- **Decisor:** dono do produto (cartão de 28/10/2026)
- **Altera:** ADR-0022, item 4 (conta própria no realm `ziipsan-portal`) e a alternativa rejeitada "acesso só por
  matrícula e CPF, sem conta"
- **Afeta:** P3.3, P0.38, ED-38, P2.27 (domínio próprio do tenant), P3.8 (pentest), P3.35 (autenticidade da fatura);
  achados CAD-97 e CAD-101 (22a), PLT-06 (22c), GES-32 (22d) e COE-07 (23a)

## Contexto

O ADR-0022 definiu que o consumidor entra no portal com conta própria (CPF, CNPJ ou e-mail e senha) e rejeitou o
acesso sem conta por matrícula e CPF, porque esses dados circulam em papel e não provam posse. As tarefas P3.3, P0.38 e
ED-38 passaram a incluir o login gov.br e o pagamento sem conta, sem ADR que os autorizasse (23a, COE-07).

Fatos apurados em 22/10/2026 (22c, PLT-06): a integração com o Login Único gov.br é exclusiva de órgãos e entidades
públicos; o serviço deve estar em domínio GOV.BR; o município deve ter aderido à Rede Nacional de Governo Digital; cada
serviço exige solicitação própria; o nível de confiabilidade da conta (bronze, prata, ouro) é informado ao serviço.

## Decisão

1. **Login gov.br como opção do portal, só para tenant de direito público.** O realm `ziipsan-portal` aceita o gov.br
   como provedor de identidade (OIDC, intermediado pelo Keycloak, PKCE), além da conta própria do ADR-0022, que
   continua existindo. O tenant liga a opção (`portal.idp_govbr_enabled`, classe d, disponível só a tenant autarquia,
   órgão ou entidade pública que tenha a integração aprovada e domínio próprio conforme a P2.27).
2. **Nível mínimo por operação.** Operações de maior risco (troca de titularidade, tarifa social, alteração de dado de
   contato, negociação de débito) exigem nível prata ou ouro quando a entrada é pelo gov.br
   (`portal.min_assurance_by_operation`, classe b). Sem gov.br, valem as regras de vínculo do ADR-0022.
3. **Pagamento sem conta (convidado).** Quem só quer pagar informa o código de vinculação impresso na fatura (alta
   entropia, não a matrícula) e recebe apenas a fatura em aberto daquele código: valor, vencimento, mês de referência,
   linha digitável e Pix copia e cola. Nenhum dado pessoal é exibido (nome, endereço, documento, consumo detalhado).
   O tenant escolhe (`portal.guest_payment`, classe b): desligado; só código; código mais sufixo do documento do
   titular. Padrão de fábrica: só código.
4. **Proteções do caminho de convidado.** Limite de tentativas por origem e por código, resposta indistinta para código
   inexistente, sem sessão persistente, registro de cada consulta e defesa contra automação compatível com
   acessibilidade (22c, PLT-22). O pentest da P3.8 cobre enumeração de código.
5. **O que não muda no ADR-0022.** Histórico de 120 meses, documentos e vínculo por código impresso mais documento do
   titular exigem conta (própria ou gov.br). O documento nunca vai em URL nem em log.

## Consequências

### Positivas

- Reduz atrito de quem só quer pagar, coerente com a regra de que o prestador não recusa pagamento.
- Para tenant público, o gov.br reduz a custódia de senha e reforça a prova de posse do CPF.

### Negativas

- O gov.br depende de domínio GOV.BR e de solicitação por serviço: não vale para tenant privado nem para o domínio
  padrão do Ziip.
- O caminho de convidado amplia a superfície de enumeração; exige os controles do item 4 e teste específico.

## Alternativas consideradas

- Manter só a conta própria (ADR-0022 original): rejeitada pelo dono do produto em 28/10/2026.
- Convidado por matrícula e CPF: continua rejeitado (não prova posse; matrícula circula em papel).
- Gov.br para qualquer tenant: inviável; a integração é restrita a entes públicos.

## Tarefas derivadas

- P3.3: caminho de convidado e opção gov.br; P0.38: provedor de identidade por tenant no realm do portal; ED-38: uso
  no aplicativo; P3.8: teste de enumeração do código; parâmetros `portal.idp_govbr_enabled`,
  `portal.min_assurance_by_operation` e `portal.guest_payment` no catálogo do tenant.
