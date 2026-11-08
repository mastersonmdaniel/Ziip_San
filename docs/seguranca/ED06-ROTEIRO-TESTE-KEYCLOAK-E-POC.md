# ED-06 — Roteiro de ensaio: VISAN (alínea b), SAMAE 2.7 e regressão do Keycloak

Este roteiro é o ensaio HUMANO/CI sobre o núcleo entregue (as regras já têm
teste unitário). Nada aqui substitui o roteiro oficial dos editais: os
identificadores POC-04 a POC-07 e o item 2.7 são os do aceite da ED-06.

## 1. VISAN, alínea b (POC-04 a POC-07)

Pré-condição: tenant de ensaio com `sec.lockout_attempts = 3` (valor do
ensaio, não do código), um usuário comum, um usuário com perfil só de
consulta (ex.: VIEWER) e o ADMIN do tenant.

| Passo | Esperado |
|---|---|
| POC-04: login válido do usuário comum | Entra; a sessão anterior do mesmo usuário (se houver) é encerrada (login único) |
| POC-05: 3 senhas erradas seguidas | Na 3ª, a conta bloqueia; a senha CERTA durante o bloqueio recebe a mesma mensagem genérica |
| POC-05b: usuário inexistente e senha errada | Código e mensagem IDÊNTICOS entre si e ao caso bloqueado (resposta indistinta) |
| POC-06: usuário só de consulta tenta uma escrita | Escrita recusada; leituras funcionam |
| POC-07: ADMIN consulta sessões ativas e a trilha do usuário | Sessões listadas; trilha por usuário (TR-128, SG-062) exportável, com integridade verificável (P1.7) |

## 2. SAMAE, item 2.7 (controle de acesso, inclusive Active Directory)

- Federação LDAP/AD por organização ligada no tenant de ensaio contra o
  servidor de teste; usuário do AD entra; usuário fora da organização, não.
- SSO OIDC e SAML: dois tenants no mesmo realm, provedores distintos; o
  tenant com `sec.sso_enforced = true` recusa login local.
- SCIM: provisionar usuário no tenant A com o token de A funciona; o MESMO
  token lendo/alterando usuário do tenant B recebe 404; papel acima do teto
  do ADMIN de A é recusado.

## 3. Regressão a CADA atualização do Keycloak (condição do aceite)

Repetir, no CI ou no ensaio da atualização, ANTES de promover a versão:

1. `LoginGuardAndAuthenticatorTest.twoTenantsDifferentPolicies` (unitário)
   verde — dois tenants, limites diferentes, mesmo núcleo/realm.
2. Ensaio 1 deste roteiro (bloqueio em 3 no tenant de ensaio).
3. Ensaio 2 SCIM (isolamento entre tenants).
4. Troca de senha: mínimo do tenant, lista de vazadas, histórico; expiração
   e composição só com aceite registrado (sem aceite, ficam inertes).

Se qualquer item falhar, a atualização do Keycloak NÃO é promovida: é o
risco registrado no plano (autenticador próprio quebrado/ignorado após
atualização deixaria um tenant sem bloqueio ou com a política de outro).

## 4. Pendências de ensaio (não executáveis nesta entrega)

- O adaptador SPI do Keycloak (provider/factory sobre o núcleo
  `TenantPolicyAuthenticator`) e o servidor LDAP/AD de teste são trabalho de
  implantação: o núcleo e as portas estão entregues e testados; o ensaio
  ponta a ponta roda no ambiente com Keycloak do PC do dono/CI.
- A POC oficial é conduzida no roteiro VISAN completo; este documento cobre
  só a alínea b, que é o escopo da ED-06.
