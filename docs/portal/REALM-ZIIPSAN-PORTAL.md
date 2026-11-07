# Realm `ziipsan-portal` — especificação de configuração (P3.3)

Documento de configuração para a integração (não é migration nem código):
o realm é criado no Keycloak da implantação; esta entrega entrega as
**regras** em código (`platform/portal`) e este contrato de configuração.

## Realm
- Nome: `ziipsan-portal` (distinto do realm do backoffice).
- Conta própria do consumidor: login por CPF, CNPJ ou e-mail + senha.
- MFA opcional (TOTP/WebAuthn conforme política do tenant); **sem SMS**
  e sem login por celular.
- Senha conforme o alinhamento NIST SP 800-63B do ADR-0040 (ver ED-06):
  comprimento mínimo, lista de senhas vazadas conferida no cadastro
  (`BotDefensePolicy.validateNewPassword`), colar e gerenciador de senha
  permitidos.

## Client do BFF
- Client confidencial `ziipsan-portal-bff`, fluxo authorization code +
  PKCE S256, segredo só no cofre; o navegador nunca recebe token do
  Keycloak (sessão do BFF, core V5 da P0.14).

## Autenticador próprio (SPI)
- O SPI de autenticação delega a `BlindDocumentLogin`: normaliza o
  identificador (`DocumentNumber`), calcula o **índice cego**
  (`FieldProtector`, contextos `portal.account.document` /
  `portal.account.email`) e localiza a conta **somente** pelo índice.
- O SPI **não** pode gravar nem consultar documento em claro, **não**
  pode pôr documento em `login_hint`, URL, log ou evento de login
  (RNF-52; trava testável em `PortalLogSafety`).
- Falha uniforme: inexistente e senha errada produzem o mesmo resultado
  e o mesmo custo (`PortalAuthenticationFlow`, conta fictícia fixa).

## Isolamento de realms
- `PortalRealmPolicy`: token com emissor `.../realms/ziipsan-portal` é
  recusado nas rotas do backoffice e vice-versa (teste dedicado).

## gov.br (ADR-0043)
- IdP gov.br no realm **somente** para tenant de direito público
  (`GovBrPolicy.assertGovBrConfigurationAllowed`); a habilitação do
  provedor, a solicitação do serviço e o domínio GOV.BR são da ED-38.
- Nível do token (bronze/prata/ouro) lido do claim de garantia e exigido
  por operação via `portal.min_assurance_by_operation` no BFF
  (`GovBrPolicy.requireAssurance`).
