# Versionamento, licenças e changelog — P0.39 / P0.40 / P0.41

## P0.39 — Versão gerada pela tag

- **Backend (Maven):** a versão do projeto é `${revision}` no pom raiz (padrão
  `0.0.0-SNAPSHOT` sem flag) e em todos os parents dos módulos. A release passa
  `-Drevision=<versão da tag>`; `scripts/version-from-tag.sh` deriva essa versão
  (`v1.2.0` → `1.2.0`; sem tag exata → `0.0.0-SNAPSHOT`). O `flatten-maven-plugin`
  resolve o placeholder no pom publicado, para nenhum artefato sair com a versão
  literal `${revision}`.
- **Frontend (Vite):** a versão NÃO vem do `package.json` (sentinela `"0.0.0"`, só para
  o npm não reclamar). O `vite.config.ts` injeta `__ZIIPSAN_VERSION__`,
  `__ZIIPSAN_COMMIT__` e `__ZIIPSAN_BUILD_DATE__` a partir de `ZIIPSAN_VERSION` (CI)
  ou da tag git, com commit e **data do commit** (não o relógio da máquina).
- **Artefatos gerados (nunca versionados à mão):** `build-info.properties`
  (spring-boot-maven-plugin), `git.properties` (git-commit-id-maven-plugin) e
  `version.json` — no backend, filtrado de `src/main/resources-filtered/version.json`
  para o classpath; no frontend, escrito no `dist/` pelo plugin `ziipsan-version-json`
  do Vite. Os três carregam a mesma versão, commit e data.
- **Determinismo:** dois builds do mesmo commit dão a mesma versão — a versão vem da
  tag (ou do padrão fixo de dev), o commit é o HEAD e a data é a data do commit. O job
  `version-check` do CI prova isso avaliando `project.version` duas vezes com o mesmo
  `-Drevision` e comparando.
- **Cabeçalhos em toda resposta:** `Ziip-Version` e `Ziip-Min-Client-Version`
  (`VersionHeaderFilter`, filtro de servlet — roda inclusive em 401/403/404). O mínimo
  padrão é a própria versão do build (propriedade `ziipsan.version.min-client-version`
  permite um piso diferente, mas nenhum release precisa decidir isso à mão).
- **SPA antigo:** o interceptor de resposta (`api.ts`) compara o mínimo anunciado com
  a versão do build (`versionInfo.ts`); se o cliente está abaixo, dispara o evento que
  o `VersionBanner` escuta, com botão de recarga. Builds `dev` nunca avisam.
- **Verificação de "nenhuma versão à mão":** `scripts/check-no-hardcoded-version.py`
  (job `version-check`): parents `${revision}`, `package.json` na sentinela, Vite sem
  ler `package.json`, template de `version.json` com placeholders.
- **E2E pendente:** o spec `frontend/e2e/version-banner.spec.ts` existe, mas NÃO foi
  rodado nesta VM (sem Keycloak/navegador; Playwright precisaria baixar o Chromium).
  O que está provado: cabeçalhos (integração backend), comparação + banner (vitest).

## P0.40 — Licenças de terceiros pelo SBOM

- O backend já gerava SBOM CycloneDX no `package` (`target/bom.json`, plugin
  CycloneDX); o frontend gera o seu com `npm sbom --sbom-format=cyclonedx`.
- `scripts/generate-third-party-licenses.py` funde os SBOMs em
  `third-party-licenses.json` (artefato de build, **não versionado**; o CI o regenera).
  Componentes sem licença no SBOM saem com `licenses: []`.
- `scripts/check-third-party-licenses.py` confere contra
  `third-party-licenses-allowed.json` (lista SPDX permitida). **Componente sem nenhuma
  licença permitida, ou sem licença conhecida, BLOQUEIA o CI** (job `licenses`).
  GPL/AGPL e licenças proprietárias não estão na lista de propósito; adicionar uma
  licença à lista é decisão de release, registrada no commit que a adiciona.
- Regras decididas a partir dos SBOMs reais (não presumidas): (a) módulos próprios
  (`pkg:maven/br.com.ziipsan/*`) são excluídos como "terceiros", com a lista de
  excluídos registrada na saída; (b) licenciamento dual: basta UMA licença permitida
  (o distribuidor escolhe — é assim que o Jakarta passa pelo EPL-2.0, sem precisar
  admitir a variante GPL); expressões AND viram um token único que bloqueia para
  revisão; (c) rótulos verificados contra o texto: "Broadcom Foundation License" do
  Spring Session mapeia para Apache-2.0 porque o LICENSE.txt do jar É o texto Apache
  2.0; (d) "Public Domain" (aopalliance, declarado no pom) é a única entrada não-SPDX
  da lista, documentada como tal. O resultado medido nesta frente: 688 componentes de
  terceiros (161 Maven + 539 npm, após dedup), 0 sem licença, check verde.

## P0.41 — Changelog do cliente

Ver `content/release-notes/README.md` (esquema) e `schema.json` (JSON Schema).
Resumo: um `<version>.yaml` por release, validado por
`scripts/check-changelog.py`; commit marcado `[security]`, `[legislation]` ou
`[regulatory-parameter]` (ou prefixos equivalentes) sem item de mesmo `type`
referenciando o SHA **bloqueia a release** (job `changelog`, em PR contra a base).
Não há arquivo de release note commitado por esta frente: a primeira versão lançada
criará o primeiro arquivo, com a versão da tag — um arquivo `0.0.0.yaml` ou
`UNRELEASED.yaml` seria versão inventada.
