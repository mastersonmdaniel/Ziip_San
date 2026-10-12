# ADR-0007 — MFA configurável por tenant

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** revisão 17, achados S3 e S4; decisões D5/D6 (realm único `sigsan`)

## Contexto

O realm único `sigsan` não tem OTP nem WebAuthn configurados (S4). Todos os tenants compartilham o realm (D5/D6), com
o tenant no claim `tenant_id`; portanto, uma política de autenticação do realm vale para todos. O papel `ADMIN` é do
realm inteiro e o atributo `tenant_id` é editável por administrador (`infra/keycloak/realms/sigsan-realm.json:359`),
o que a revisão trata em S3 com a separação entre administração do tenant e administração da plataforma.

Os clientes têm realidades diferentes: autarquias com operadores em terminais compartilhados, prestadores com política
de segurança própria e exigência de MFA em edital. A revisão recomendava MFA obrigatório para `ADMIN`, `MANAGER` e
quem revela documento.

## Decisão

1. **MFA configurável por tenant**, definido na implantação: obrigatório para todos, obrigatório por papel, ou
   opcional. A configuração é um parâmetro do tenant com registro de quem aprovou e quando, no mesmo modelo de aceite
   do ADR-0002.
2. **Padrão de fábrica:** obrigatório para `ADMIN`, `MANAGER` e para quem tem `registry.reveal-document`. Afrouxar o
   padrão exige aceite registrado e fica visível no console da plataforma.
3. **Administração da plataforma sempre com MFA**, sem configuração. Vale para todo papel de plataforma (operação,
   suporte, impersonação auditada).
4. **Métodos:** TOTP e WebAuthn/passkey. Códigos de recuperação para perda do dispositivo; redefinição de MFA por
   administrador do tenant (ou da plataforma, para papéis de plataforma) sempre auditada.
5. A API não confia só no Keycloak: operações de plataforma, e as do tenant quando a política exigir, verificam no
   token que o login usou segundo fator (`acr`/`amr`). A revelação de documento pode exigir reautenticação recente
   (step-up).

## Consequências

### Positivas

- Atende clientes com exigência de MFA sem impor atrito a quem não a tem, mantendo um padrão seguro.
- A conta de maior impacto (plataforma, com acesso a vários tenants) fica sempre protegida.
- A verificação do fator na API evita que um erro de configuração do fluxo no Keycloak passe despercebido.

### Negativas

- Um tenant pode desligar MFA e expor dado pessoal a roubo de senha. O risco é do controlador, mas o incidente atinge
  o operador; por isso o padrão é ligado e o afrouxamento é registrado.
- No realm único, a política por tenant depende de fluxo condicional do Keycloak (condição por grupo, papel ou
  atributo) sincronizado com o catálogo. Erro de sincronização pode deixar um tenant sem MFA; exige teste de
  ponta a ponta por política.
- Suporte a perda de dispositivo passa a ser rotina de atendimento.

### O que muda no código

- Realm: fluxo de autenticação condicional com OTP e WebAuthn; códigos de recuperação; grupo ou atributo por tenant
  que indique a política. Sem segredos no `sigsan-realm.json`.
- Catálogo: política de MFA por tenant e o aceite correspondente; serviço que sincroniza a política com o Keycloak
  pela API de gestão de usuários prevista em S3.
- API: verificação de `acr`/`amr` nas rotas de plataforma e nas do tenant conforme a política; step-up opcional no
  `reveal-document`.
- Testes: um caso por política (obrigatório, por papel, opcional) e um de plataforma sem MFA recusado.

### O que muda no roadmap

- Entra no P0 com as correções de segurança (S4). A configuração por tenant aparece no console da plataforma v0 (P1).

## Alternativas consideradas

- **MFA obrigatório para todos os usuários, fixo no produto.** Rejeitada: atrito alto em autarquias com terminais
  compartilhados e baixa maturidade digital, com risco de contorno (dispositivo compartilhado).
- **MFA só para administradores, fixo no produto.** Rejeitada: não atende cliente com exigência maior em edital ou
  política interna.
- **Realm por tenant para ter política própria.** Rejeitada: contraria D5/D6 e reintroduz o código de realm por
  tenant que a revisão manda remover.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Fluxo condicional no realm com TOTP, WebAuthn/passkey e códigos de recuperação | P0 |
| MFA obrigatório e verificado na API para todo papel de plataforma | P0 |
| Política de MFA por tenant no catálogo, com aceite e sincronização com o Keycloak | P0–P1 |
| Configuração da política no console da plataforma v0 | P1 |
| Step-up na revelação de documento | P1 |
| Pentest incluindo contorno de MFA e redefinição de fator | P3 |
