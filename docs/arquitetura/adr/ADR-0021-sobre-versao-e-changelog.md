# ADR-0021 — Sobre, versão gerada no build e changelog do cliente versionado

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** `docs/arquitetura/AJUDA-SUPORTE-SOBRE.md` §9; `pom.xml`; `frontend/package.json`; ADR-0002, ADR-0016,
  ADR-0019; RNF-35

## Contexto

O `pom.xml` está em `1.0.0-SNAPSHOT` e o `frontend/package.json` em `1.0.0`, sem relação entre si. No Ziip Cloud, a
versão é sequencial e escrita à mão em três lugares, e o changelog é HTML no `app.js`. Para o cliente de saneamento,
o registro de mudanças de segurança, legislação e parâmetro regulatório é evidência perante a agência reguladora.

## Decisão

1. **Esquema:** SemVer 2.0.0 com build metadata, uma versão única para backend e frontend. MAJOR para quebra de API
   pública ou de integração; MINOR para funcionalidade; PATCH para correção. Formato `1.4.2+a1b2c3d`; homologação
   `1.5.0-rc.1`; build sem tag `1.5.0-dev.N+sha`.
2. **Geração no build:** a tag `vX.Y.Z` é a fonte. O CI passa `-Drevision` ao Maven e a mesma versão, commit e data
   ao Vite. Nenhuma versão é escrita à mão.
3. **Detecção no frontend:** a API devolve `Ziip-Versao` e `Ziip-Versao-Minima-Cliente`; o SPA avisa para recarregar
   quando difere e bloqueia abaixo da mínima.
4. **Implantação:** a data exibida é gravada pelo job `migrate` na tabela `platform.implantacao` (core `V5_1`).
5. **Changelog do cliente:** um arquivo por versão em `conteudo/novidades/<versao>.yaml`, separado do changelog técnico
   (`CHANGELOG.md`, gerado dos commits). Cada item tem `categoria` (novidade, melhoria, correção, segurança,
   legislação, parâmetro regulatório), `modulo`, `texto`, `referencia` (norma ou resolução, obrigatória em legislação e
   parâmetro regulatório), `afeta` (todos, módulos, agência reguladora ou parâmetro) e `acao_requerida` opcional.
6. **Regra permanente:** toda mudança de segurança, legislação ou parâmetro regulatório tem item no changelog do
   cliente. O CI bloqueia a release quando um commit marcado com essas categorias não tem item. Itens de segurança
   descrevem o efeito, sem detalhe explorável.
7. **Sobre:** dados do produto e da empresa desenvolvedora vêm do pacote de marca (ADR-0016); documentos legais
   mostram a versão vigente e a aceita (`platform.aceite_documento`, ao lado do contrato do ADR-0019; não confundir
   com `parameter_acceptances`, o aceite do conjunto de parâmetros do ADR-0002); licenças de terceiros saem do SBOM
   CycloneDX do RNF-35.
8. **Novidades:** uma vez por versão por usuário, com estado no servidor, mostrando só os itens que afetam o tenant.

## Consequências

- O suporte identifica a versão exata pelo cabeçalho, e o chamado a grava.
- Exige tags e disciplina de marcação de commits; o produto decide MAJOR e MINOR na release.
- O changelog do cliente vira registro auditável, com histórico no Git.
- Licença não permitida em dependência nova bloqueia o build até exceção registrada em ADR.

## Alternativas consideradas

- **Sequencial por commit (Ziip Cloud).** Rejeitada: não indica compatibilidade, e o bump manual gera divergência.
- **CalVer.** Rejeitada: a API de integração e o app de campo offline (P4) precisam de sinal de compatibilidade.
- **Um único `CHANGELOG-CLIENTE.md`.** Rejeitada: não filtra por tenant nem por categoria e gera conflito de merge.
- **Changelog só a partir dos commits.** Rejeitada: linguagem técnica, sem curadoria para o cliente.
- **Versões separadas para backend e frontend.** Rejeitada: são implantados juntos.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Versão pela tag no CI, `build-info`, `git.properties` e `version.json` | P0 |
| Cabeçalhos de versão e aviso de recarga no SPA | P0 |
| SBOM CycloneDX, `licencas-terceiros.json` e lista de licenças permitidas no CI | P0 |
| Esquema de `conteudo/novidades`, validação e bloqueio por categoria no CI | P0 |
| Tabela `platform.implantacao` gravada pelo `migrate`; `GET /api/v1/sobre` mínimo | P0 |
| Sobre completo com pacote de marca e aceites; novidades por usuário | P1 |
| Filtro de novidades por tenant (`afeta`) | P2 |
