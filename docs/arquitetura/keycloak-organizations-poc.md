# Keycloak Organizations para a marca no login — PoC e decisão (P0.38)

Adendo a MARCA-E-TEMAS §3.3. **Decisão: PENDENTE do dono.** Nada neste documento é
decisão: a recomendação abaixo é da frente, e só vira decisão com a aprovação do dono
em cartão. A P0.13 avaliou Organizations; esta onda executa a prova de conceito
descrita aqui e regista o resultado.

## 1. As três opções (MARCA-E-TEMAS §3.3)

| Caminho | Como a marca aparece antes do login | Custo / risco principal |
|---|---|---|
| **Subdomínio** (`<slug>.<base>`) | O BFF resolve o tenant pelo host e mostra a marca antes de qualquer dado do usuário | O slug passa a ser público; exige DNS e certificado por tenant (ou curinga) |
| **E-mail primeiro** (Keycloak Organizations) | O usuário digita o e-mail; o Keycloak casa o domínio com a organização e o tema lê a marca da organização | Exige tema que leia a organização; a marca só aparece depois do e-mail; acopla tenant a domínio de e-mail (um tenant, vários domínios, exige configuração por domínio) |
| **Caminho** (`/t/<slug>`) | O slug vai na URL; o BFF resolve o tenant pelo caminho | Endereço menos memorizável; slug público do mesmo jeito |

## 2. O que o Keycloak 26 oferece (conferido na documentação do Keycloak 26)

- Organizations existe desde o Keycloak 25 e está no 26 (o compose do projeto usa
  `quay.io/keycloak/keycloak:26.8.0`); ativa-se por realm (Realm Settings → Organizations).
- Cada organização tem nome, alias único (URL-friendly), URL de redirecionamento e
  **domínios**: o Keycloak casa o domínio do e-mail digitado com a organização
  (identity-first login) e pode redirecionar ao IdP da organização.
- O token pode carregar a organização (escopo `organization`), o que permitiria ao
  BFF confirmar o tenant — mas o Ziip SAN já resolve o tenant pelo JWT + schema, e a
  P0.25 removeu o modelo realm-por-tenant: Organizations seria **uma organização por
  tenant dentro do realm único `ziipsan`**, não um realm por tenant.
- Limite que a PoC precisa provar (não assumido): o tema de login do Keycloak ler a
  marca da organização resolvida (logo, cor, nome) antes da senha, sem customização
  profunda do tema. A documentação descreve o roteamento por domínio e os passos de
  autenticação da organização, mas a marca por organização no tema é o ponto a provar.

## 3. Prova de conceito — roteiro e resultado

Roteiro executável: `scripts/keycloak-organizations-poc.sh` (contêiner) e
`scripts/keycloak-organizations-poc-local.sh` (Keycloak local, sem contêiner — a
variante executada, porque a VM não tinha Podman). Domínio `.invalid` de propósito:
nenhum domínio real, nenhum usuário real, nenhuma marca real.

**Resultado: PoC EXECUTADA em 2026-11-03 contra Keycloak 26.8.0 real** (distribuição
oficial, `start-dev` local na porta 18081, realm de PoC `poc`, dados H2 efêmeros):

1. Realm `poc` criado com `organizationsEnabled = true` (conferido via Admin API).
2. Organização `poc-saneamento` criada (id `9a4c7217-2040-4690-9980-ce984dd00991`,
   alias `poc-saneamento`, `enabled: true`) com domínio `poc.example.invalid`
   (`verified: false`, `autoRedirect: false`); listagem via Admin API a devolve.
3. Cliente público `poc-test` criado no realm; a página de login do tema padrão
   (`keycloak.v3`) abre com título "Sign in to poc" e campo de usuário — **zero
   menções a "organization" no HTML**.
4. Post do e-mail `usuario@poc.example.invalid` (domínio da organização, usuário
   inexistente): o fluxo segue para o campo de senha, sem mensagem de domínio, sem
   marca e sem qualquer elemento da organização. Ou seja: com o tema padrão, o
   e-mail primeiro **não mostra a marca da organização antes da senha** — a marca
   por organização exigiria tema customizado (não testado aqui) e/ou fluxo
   customizado, o que é trabalho adicional não estimado nesta onda.

Armadilhas encontradas na execução (registradas para a próxima PoC): o `start-dev`
não expõe `/health/ready` (404) — o probe correto é `/realms/master`; a primeira
subida leva ~5 min (augmentation do Quarkus) e **matar o Keycloak no meio do build
corrompe `lib/quarkus/`** (primeiro `quarkus-application.dat` truncado, depois
`build-system.properties` ausente) — a recuperação é reextrair `lib/quarkus/` do zip
oficial; e o cliente `account-console` exige PKCE (usar cliente público próprio no
teste de login).

## 4. Recomendação da frente (NÃO é decisão)

**Manter o subdomínio como caminho principal** (recomendação já registrada em
MARCA-E-TEMAS §3.3; `app.<domínio>` mostra só a marca do produto) e **não adotar
Organizations para a marca nesta fase**, pelos motivos:

1. O subdomínio já tem desenho implementável no BFF (`GET /api/v1/public/tenant-brand`
   resolvido pelo host, com função `SECURITY DEFINER` restrita às chaves
   `branding.*`, MARCA-E-TEMAS §3.3) e mostra a marca antes de qualquer dado.
2. Organizations resolve um problema que o Ziip SAN já resolve de outro jeito
   (multi-tenant por schema + JWT); usá-lo só para a marca traria o acoplamento
   tenant↔domínio de e-mail sem ganho de autenticação nesta fase.
3. **A PoC provou que o tema padrão não mostra marca por organização** (seção 3):
   adotar Organizations para a marca exigiria tema customizado, sem estimativa.

Reavaliar Organizations quando (a) um tema customizado provar a marca por
organização, ou (b) o SSO corporativo por tenant (ED-06) entrar no escopo — aí o IdP
por organização passa a ser o motivo, e a marca vem de brinde. Login gov.br no portal
é ED-38, fora desta tarefa.

## 5. Decisão do dono — PENDENTE

- [ ] Subdomínio como caminho principal (recomendação da frente)
- [ ] E-mail primeiro com Organizations
- [ ] Caminho (`/t/<slug>`)
- [ ] Outra (registrar)
