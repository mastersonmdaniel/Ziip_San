# ADR-0016 — Marca do produto em pacote único, marca do tenant e renomeação `sigsan` → `ziipsan`

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0006 (BFF), ADR-0009 (desenvolvimento fora do Drive), ADR-0021 (Sobre); detalhes em
  `docs/arquitetura/MARCA-E-TEMAS.md` §§2, 3, 5 e 6
- **Ajuste de 12/10/2026 (plano v2.3), antes de qualquer implementação:** item 4 passa a incluir na renomeação os
  códigos de contrato da API em inglês (prefixo `urn:ziipsan:problem:` e códigos de problema, tabela do plano P0.33),
  por decisão do dono do produto; mensagens ao usuário seguem em português.
- **Ajuste de 12/10/2026 (editais), antes de qualquer implementação:** item 2 passa a incluir o app do cliente
  publicado nas lojas com o nome, o ícone e as cores do tenant (ADR-0026), como mais um canal da marca do tenant ao
  lado do portal e dos e-mails; item 6 não muda, porque a marca nas lojas é do tenant, e não de um revendedor; decisão
  do dono do produto.

## Contexto

O nome "SIGSAN" está escrito à mão em telas e no realm (`frontend/index.html:6,8`, `Layout.tsx:61`, `Login.tsx:40`,
`AuthCallback.tsx:45`, `PageHeader.tsx:6`, `infra/keycloak/realms/sigsan-realm.json:3,298`) e em código legado com
domínios `sigsan.com.br` fixos (`KeycloakAdminService.java:59,107-122,154,186`). Não há logo, favicon, manifest, tema de
login ou e-mail do Keycloak, nem metadados no PDF do MTR. Trocar a marca hoje exige caçar literais.

O nome comercial é Ziip SAN; o código usa `sigsan` em 544 arquivos: 27 módulos, pacotes `br.com.sigsan`, roles do
banco, propriedades, variáveis, realm, URN de erro, tópico MQTT e o contexto HKDF da cifra
(`EnvDerivedTenantKeyProvider.java:32-33`). Não existe dado real; o banco de desenvolvimento pode ser recriado.

Clientes querem a própria marca em contas, documentos e portal.

## Decisão

1. **Pacote de marca único** (`branding/<marca>/`, validado por schema): nome, logos, favicon, ícones, cores de marca,
   textos institucionais, dados legais, domínios, remetente de e-mail, tema do Keycloak, rodapés, metadados de PDF e
   Sobre. Trocar a marca é trocar o pacote (`ZIIPSAN_BRAND`) e refazer o build.
2. **Marca do tenant** (nome, logo, cor) em `tenant_settings`, aplicada só em logo, cabeçalho, contas, PDFs, portal do
   cliente final e e-mails. A interface de trabalho segue o tema do usuário (ADR-0017). O produto aparece discreto
   ("operado com {produto}"), configurável no pacote.
3. **Antes do login**, a marca do tenant vem do subdomínio; Keycloak Organizations é avaliado na P0.13.
4. **Renomeação completa `sigsan` → `ziipsan` no P0**, como tarefa única, na ordem de MARCA-E-TEMAS §5.2, com todos os
   testes. Inclui o contexto HKDF, que passa a `ziipsan/enc/v1` e `ziipsan/bidx/v1` e fica **congelado para sempre**.
   Inclui também a URN de erro, que passa de `urn:sigsan:problema:` a `urn:ziipsan:problem:`, com os códigos de
   problema em inglês (`versao-desatualizada` → `stale-version`, `nao-encontrado` → `not-found`; lista no plano
   P0.33); `title` e `detail` continuam em português. URN e códigos, prefixo MQTT, realm, audiência e emissor também
   ficam congelados depois da renomeação.
5. Nada visível ao usuário traz o nome em literal; teste do CI recusa literais de marca fora do pacote.
6. White-label por revenda não entra; o pacote é resolvido num único ponto para não impedi-lo.

## Consequências

### Positivas

- Re-brand vira troca de pacote e configuração externa (domínio, DNS, e-mail), sem alterar código.
- Nome interno e comercial alinhados antes de existir dado, contrato de API ou equipamento de campo.
- Cliente vê a própria marca nos documentos sem afetar a acessibilidade da interface.

### Negativas

- A renomeação toca quase todo o repositório e exige que nenhum outro trabalho esteja aberto.
- Migrations já aplicadas mudam de checksum; o banco de desenvolvimento e o Keycloak local são recriados.
- ADRs e diário anteriores continuam citando caminhos `sigsan`, válidos até a tag `pre-rename`.
- Subdomínio por tenant torna o slug público e pede certificado curinga.
- Upload de logo abre superfície de ataque: tipo pelos bytes, reencode, quarentena até a varredura pelo ClamAV
  (exceção do ADR-0010, mesmo pipeline dos anexos do ADR-0020) e CSP no ativo.

### O que muda no código

- Novo módulo `shared/ziipsan-brand` com `BrandProvider`; plugin do Vite para a casca; `GET /api/v1/public/brand` e
  `GET /api/v1/public/tenant-brand`; tema do Keycloak gerado; `tenant_assets` e `TenantAssetStore`.
- Renomeação de módulos, pacotes, propriedades, variáveis, roles, realm, URN e códigos de problema, tópico e contexto
  HKDF; teste de vetor fixo da cifra; teste de contrato dos códigos de problema, com `errors.ts` do frontend e testes
  atualizados no mesmo commit.
- `KeycloakAdminService` removido com a P0.13.
- `scripts/check-brand-literals.sh` no `ci.sh`.

### O que muda no roadmap

- Renomeação logo após a P0.1, antes das demais tarefas de código do P0.

## Alternativas consideradas

- **Manter `sigsan` no código e trocar só o visível.** Rejeitada: o nome interno vaza em URN, emissor, tópico e
  logs, e trocar depois do go-live afetaria dado cifrado e integrações.
- **Renomear depois do go-live.** Rejeitada: exigiria recifrar dados e migrar clientes da API e equipamentos.
- **White-label por revenda já.** Rejeitada por ora: sem demanda; o desenho não o impede.
- **Cor do tenant como primária da interface.** Rejeitada: tira o controle de contraste e muda o produto a cada
  cliente.
- **Marca em variáveis de ambiente soltas.** Rejeitada: sem validação nem ativos versionados juntos.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Renomeação `sigsan` → `ziipsan` com todos os testes, contexto HKDF congelado por teste de vetor fixo, URN `urn:ziipsan:problem:` e códigos de problema em inglês | P0 |
| Pacote de marca com schema, `BrandProvider`, plugin do Vite, endpoint público e teste de literais | P0 |
| Tema de login e de e-mail do Keycloak gerado do pacote | P0 |
| Marca do tenant: chaves, upload de logo, subdomínio, endpoint pré-login, cabeçalho | P1 |
| Avaliação de Keycloak Organizations para a marca no login | P0 (com a P0.13) |
| Marca do tenant e metadados nos PDFs | P2 |
| Marca do tenant no portal do cliente final e nos e-mails | P3 |
