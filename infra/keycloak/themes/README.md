# Tema Keycloak `ziipsan` (P0.35)

Arquivos **gerados** a partir do pacote de marca (`branding/ziipsan`) por
`scripts/brand/generate-keycloak-theme.mjs`. Não edite à mão: troque o
pacote e regenere. Trocar `ZIIPSAN_BRAND` muda login e e-mail sem alterar
código — provado por `scripts/brand/test-brand-swap.mjs`.

- `login/`: tema de login (pai `keycloak`, só tintura de marca em CSS +
  mensagens pt-BR). O `displayName` do realm vem de
  `infra/keycloak/realms/ziipsan-realm.json` (`loginTheme`/`emailTheme` =
  `ziipsan`).
- `email/`: tema de e-mail (pai `keycloak`) com modelos HTML e texto para
  redefinição de senha, confirmação de e-mail e ações pendentes, na cor e
  na assinatura do pacote.

## Pendências honestas desta frente

1. **Estes modelos não foram renderizados contra um Keycloak vivo nesta
   onda** (não há Keycloak de teste nesta VM): a validação de renderização
   cabe à integração / à frente da P0.29, que embarca o tema na imagem de
   produção.
2. **A imagem de produção da P0.29 é de outra frente**: ela deve copiar
   `infra/keycloak/themes/ziipsan` para a imagem. Nada aqui duplica esse
   trabalho — só os arquivos do tema e o gerador.
3. O logo do tema é a cópia de `logo-light.svg` do pacote: um rebrand
   completo troca o arquivo de arte no pacote (o gerador copia, não desenha).
