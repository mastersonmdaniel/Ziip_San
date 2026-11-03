-- GERADO por scripts/help/generate-help-content.py a partir de content/help.
-- NÃO EDITE À MÃO: regenere (o CI barra o arquivo desatualizado com --check).
--
-- P1.21: publicação do conteúdo de ajuda pelo migrate (migration repetível
-- do Flyway, reaplicada quando o conteúdo muda, sem nova imagem). Upsert por
-- chave estável; nunca apaga linha publicada. Tours e dicas não entram aqui:
-- não há tabela de definição delas na V9_3 — vão ao bundle do frontend.
-- A versão do artigo é sempre 1 nesta fase (ver o docstring do gerador).

-- Artigo: cadastrar-cliente
INSERT INTO support.article
    (tenant_id, slug, module, routes, roles, audience, min_version, current_version)
VALUES (NULL, 'cadastrar-cliente', 'cadastro', ARRAY['/cadastro/clientes', '/cadastro/clientes/novo']::text[], ARRAY['tenant-user']::text[], 'tenant', '1.0.0', 1)
ON CONFLICT (slug) DO UPDATE SET module = EXCLUDED.module,
    routes = EXCLUDED.routes, roles = EXCLUDED.roles,
    audience = EXCLUDED.audience, min_version = EXCLUDED.min_version,
    updated_at = NOW();
INSERT INTO support.article_version
    (tenant_id, article_id, version, title, body, keywords, content_hash)
SELECT NULL, a.id, 1, 'Cadastrar cliente', '# Cadastrar cliente

O cadastro do cliente reúne a identificação (pessoa física ou jurídica) e os
dados de contato. Preencha os campos obrigatórios e avance por etapas: o
formulário valida cada etapa antes de permitir continuar.

Confira o documento digitado antes de salvar: um documento errado impede a
emissão correta da conta e gera retrabalho no atendimento. Depois de salvo, o
cliente aparece na lista de [clientes](/cadastro/clientes) e pode ser
vinculado a um imóvel em [pesquisar imóveis](pesquisar-imoveis.md).', ARRAY['cliente', 'cadastro', 'CPF', 'CNPJ']::text[], 'e7c1ff02149e4cc3a7d2afa3e9cbfb809eb7e7c04e4bc03286f0894a23f51322'
FROM support.article a WHERE a.slug = 'cadastrar-cliente'
ON CONFLICT (article_id, version) DO UPDATE SET title = EXCLUDED.title,
    body = EXCLUDED.body, keywords = EXCLUDED.keywords,
    content_hash = EXCLUDED.content_hash;

-- Artigo: consultar-territorio
INSERT INTO support.article
    (tenant_id, slug, module, routes, roles, audience, min_version, current_version)
VALUES (NULL, 'consultar-territorio', 'cadastro', ARRAY['/cadastro/territorio']::text[], ARRAY['tenant-user']::text[], 'tenant', '1.0.0', 1)
ON CONFLICT (slug) DO UPDATE SET module = EXCLUDED.module,
    routes = EXCLUDED.routes, roles = EXCLUDED.roles,
    audience = EXCLUDED.audience, min_version = EXCLUDED.min_version,
    updated_at = NOW();
INSERT INTO support.article_version
    (tenant_id, article_id, version, title, body, keywords, content_hash)
SELECT NULL, a.id, 1, 'Consultar território', '# Consultar território

A tela de território mostra a organização geográfica do cadastro: localidades,
setores e rotas de leitura. Navegue pelos níveis para localizar a área de um
imóvel ou de um cliente.

A hierarquia do território é a mesma usada na organização das rotas de
leitura e na emissão das contas. Alterações na estrutura do território afetam
o cadastro vinculado: confira os imóveis e clientes da área antes de mudar um
nível.', ARRAY['território', 'setor', 'localidade', 'rota']::text[], '02d1a131aa1996effea9fbc8896688cf43657bb940d7002093fe634cbc6798ec'
FROM support.article a WHERE a.slug = 'consultar-territorio'
ON CONFLICT (article_id, version) DO UPDATE SET title = EXCLUDED.title,
    body = EXCLUDED.body, keywords = EXCLUDED.keywords,
    content_hash = EXCLUDED.content_hash;

-- Artigo: pesquisar-imoveis
INSERT INTO support.article
    (tenant_id, slug, module, routes, roles, audience, min_version, current_version)
VALUES (NULL, 'pesquisar-imoveis', 'cadastro', ARRAY['/cadastro/imoveis']::text[], ARRAY['tenant-user']::text[], 'tenant', '1.0.0', 1)
ON CONFLICT (slug) DO UPDATE SET module = EXCLUDED.module,
    routes = EXCLUDED.routes, roles = EXCLUDED.roles,
    audience = EXCLUDED.audience, min_version = EXCLUDED.min_version,
    updated_at = NOW();
INSERT INTO support.article_version
    (tenant_id, article_id, version, title, body, keywords, content_hash)
SELECT NULL, a.id, 1, 'Pesquisar imóveis', '# Pesquisar imóveis

Use a busca livre para encontrar um imóvel por matrícula, nome, documento ou
endereço. O servidor reconhece o formato digitado: não é preciso escolher o
tipo de busca antes de digitar.

Se a inscrição completa estiver preenchida, ela tem prioridade sobre a busca
livre. Use a tecla `/` para ir direto ao campo de busca.

Para cadastrar um imóvel novo, use o botão "Cadastrar imóvel" no topo da
tela. Se o cliente ainda não existir, cadastre-o primeiro em
[clientes](/cadastro/clientes) — veja também
[cadastrar cliente](cadastrar-cliente.md).

Os filtros adicionais ficam em "Mais filtros": localidade, setor e outras
características do cadastro. Limpe os filtros para voltar à lista completa.', ARRAY['imóvel', 'matrícula', 'inscrição', 'pesquisa', 'endereço']::text[], 'b31cf529d4b9e4b9ac19048a0ac8406a89c4d9720758e9ceaf1cb1d39c0d5b39'
FROM support.article a WHERE a.slug = 'pesquisar-imoveis'
ON CONFLICT (article_id, version) DO UPDATE SET title = EXCLUDED.title,
    body = EXCLUDED.body, keywords = EXCLUDED.keywords,
    content_hash = EXCLUDED.content_hash;

-- Erro explicado: urn:ziipsan:problem:confirmation-required
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:confirmation-required', NULL, 'Confirmação necessária', 'A operação gerou avisos que precisam da sua confirmação antes de continuar. Nada foi salvo ainda.', ARRAY['Um ou mais avisos exigem confirmação explícita.']::text[], ARRAY['Leia os avisos com atenção.', 'Confirme para prosseguir ou ajuste os dados e envie de novo.']::text[], 'cadastrar-cliente', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:data-conflict
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:data-conflict', NULL, 'Conflito de dados', 'A operação não pode ser concluída porque os dados mudaram ou já existe um registro com os mesmos dados.', ARRAY['Outro usuário alterou o mesmo registro enquanto você editava.', 'Já existe um registro com o identificador informado.']::text[], ARRAY['Recarregue os dados e confira o que mudou antes de tentar de novo.', 'Se o problema persistir, abra um chamado informando o que você tentava fazer.']::text[], 'pesquisar-imoveis', TRUE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:not-found
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:not-found', NULL, 'Registro não encontrado', 'O item que você procurou não existe neste tenant ou foi removido. Por segurança, um item de outro tenant devolve a mesma resposta.', ARRAY['O endereço ou o identificador digitado está errado.', 'O item foi excluído ou ainda não foi criado.']::text[], ARRAY['Confira o identificador e tente de novo.', 'Volte à lista e pesquise pelo nome ou pelo endereço.']::text[], 'pesquisar-imoveis', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:validation
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:validation', NULL, 'Campos inválidos', 'Alguns campos do formulário não passaram na validação. Nada foi salvo: corrija os campos indicados e envie de novo.', ARRAY['Um campo obrigatório ficou em branco.', 'Um valor está fora do formato esperado (documento, data ou número).']::text[], ARRAY['Leia a mensagem de cada campo destacado no formulário.', 'Corrija os campos e envie novamente.']::text[], 'cadastrar-cliente', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();
