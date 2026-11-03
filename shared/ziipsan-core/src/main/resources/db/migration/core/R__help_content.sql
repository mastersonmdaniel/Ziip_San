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

-- Erro explicado: urn:ziipsan:problem:access-denied
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:access-denied', NULL, 'Acesso negado', 'O usuário autenticado não tem a permissão — ou o papel — que a operação exige. A operação não foi executada.', ARRAY['O perfil do usuário não inclui a permissão da operação.', 'A rota exige um papel de plataforma e o token não o tem.']::text[], ARRAY['Peça a um administrador do tenant para revisar o seu perfil.', 'Se você deveria ter acesso, abra um chamado informando a operação tentada.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:approval-required
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:approval-required', NULL, 'Tipo exige aprovação por outra pessoa', 'Este tipo de serviço só executa depois que outra pessoa aprova a ordem. A execução foi recusada porque a aprovação ainda não aconteceu.', ARRAY['O tipo tem aprovação por outra pessoa ligada.', 'A ordem ainda está aguardando aprovação.']::text[], ARRAY['Peça a outra pessoa que aprove a ordem.', 'Depois da aprovação, execute de novo.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:classe-inativa
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:classe-inativa', NULL, 'Classe de feição desativada', 'Não é possível criar um tipo em uma classe desativada. A classe precisa estar ativa para receber tipos.', ARRAY['A classe foi desativada no catálogo.', 'A classe expirou ou foi substituída.']::text[], ARRAY['Reative a classe ou escolha outra classe ativa.', 'Confira o catálogo antes de criar o tipo.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:confirmation-required
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:confirmation-required', NULL, 'Confirmação necessária', 'A operação gerou avisos que precisam da sua confirmação antes de continuar. Nada foi salvo ainda.', ARRAY['Um ou mais avisos exigem confirmação explícita.']::text[], ARRAY['Leia os avisos com atenção.', 'Confirme para prosseguir ou ajuste os dados e envie de novo.']::text[], 'cadastrar-cliente', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:content-too-large
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:content-too-large', NULL, 'Conteúdo muito grande', 'O corpo da requisição excede o limite permitido e foi recusado antes de qualquer processamento.', ARRAY['O corpo enviado é maior que o limite.']::text[], ARRAY['Reduza o tamanho do corpo e envie de novo.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:customer-has-active-link
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:customer-has-active-link', NULL, 'Cliente com vínculo vigente', 'O cliente tem vínculo vigente com imóvel e não pode ser excluído antes de encerrar os vínculos.', ARRAY['Há pelo menos um vínculo vigente entre o cliente e um imóvel.']::text[], ARRAY['Encerre os vínculos do cliente antes de excluir.']::text[], 'cadastrar-cliente', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:customer-without-document
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:customer-without-document', NULL, 'Cliente sem documento', 'A operação exige o documento do cliente, e ele não está cadastrado.', ARRAY['Nenhum documento foi cadastrado para o cliente.']::text[], ARRAY['Cadastre o documento do cliente e tente de novo.']::text[], 'cadastrar-cliente', FALSE)
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

-- Erro explicado: urn:ziipsan:problem:deletion-unavailable
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:deletion-unavailable', NULL, 'Exclusão indisponível', 'O recurso não tem exclusão lógica no cadastro, então a exclusão não foi executada.', ARRAY['O tipo de registro não prevê exclusão lógica.']::text[], ARRAY['Localidade, setor comercial e quadra: bloqueie com blocked=true.', 'Rota de leitura: desative com active=false.']::text[], 'consultar-territorio', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:effects-pending
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:effects-pending', NULL, 'Efeitos pendentes impedem o encerramento', 'Há alvos com efeito ainda não aplicado, por isso a ordem não pode ser encerrada como executada. Encerrar assim marcaria trabalho como feito sem aplicar o efeito.', ARRAY['Um ou mais alvos ainda não tiveram o efeito aplicado.', 'A aplicação foi interrompida antes de terminar.']::text[], ARRAY['Aplique os efeitos pendentes e tente encerrar de novo.', 'Se o trabalho não foi feito, encerre como não executada.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:filter-unavailable
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:filter-unavailable', NULL, 'Filtro indisponível', 'Um filtro informado ainda não tem suporte no cadastro, então a busca não foi executada.', ARRAY['Imóveis: profileId, numberFrom e numberTo ainda não têm suporte.', 'Clientes: o filtro city ainda não é suportado.']::text[], ARRAY['Remova o filtro indisponível e tente de novo.']::text[], 'pesquisar-imoveis', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:inactive-type
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:inactive-type', NULL, 'Tipo de serviço inativo', 'O tipo de serviço está inativo e não aceita novas ordens. Nada foi criado.', ARRAY['O tipo foi desativado no catálogo.', 'O tipo expirou ou foi substituído.']::text[], ARRAY['Reative o tipo ou escolha outro tipo ativo.', 'Confira o catálogo de tipos antes de criar a ordem.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:internal-error
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:internal-error', NULL, 'Erro interno', 'Ocorreu um erro inesperado no servidor. Informe o identificador da requisição ao suporte.', ARRAY['Uma falha não tratada interrompeu a operação.']::text[], ARRAY['Tente novamente em instantes.', 'Se o problema persistir, abra um chamado informando o identificador da requisição.']::text[], NULL, TRUE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:invalid-cursor
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:invalid-cursor', NULL, 'Cursor inválido', 'O cursor de paginação é inválido — ele não pôde ser decodificado como a próxima página da busca keyset.', ARRAY['O cursor não tem o formato esperado (valor codificado, ponto e identificador).', 'O valor codificado não é base64 válido ou o identificador não é um UUID.']::text[], ARRAY['Volte à primeira página da busca e avance de novo a partir dela.']::text[], 'pesquisar-imoveis', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:invalid-filter
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:invalid-filter', NULL, 'Filtro inválido', 'Um filtro foi informado com valor fora do aceito, então a busca não foi executada.', ARRAY['nameMatch aceita apenas contains ou startsWith.', 'Na busca de quadras, o texto deve ser só o código (dígitos).']::text[], ARRAY['Ajuste o filtro para um valor aceito e tente de novo.']::text[], 'pesquisar-imoveis', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:invalid-inscription
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:invalid-inscription', NULL, 'Inscrição inválida', 'A inscrição informada não está no formato LLL.SSS.QQQ.LLLL.SSS.', ARRAY['A inscrição foi digitada fora do formato esperado.']::text[], ARRAY['Confira a inscrição e digite-a no formato LLL.SSS.QQQ.LLLL.SSS.']::text[], 'pesquisar-imoveis', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:invalid-page
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:invalid-page', NULL, 'Página inválida', 'A paginação pedida é inválida — a página começa em 0, o tamanho deve ser ao menos 1 e a página não pode exceder o limite suportado.', ARRAY['Página negativa.', 'Tamanho de página menor que 1.', 'A página pedida excede o limite suportado.']::text[], ARRAY['Ajuste a página e o tamanho e tente de novo.']::text[], 'pesquisar-imoveis', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:invalid-request
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:invalid-request', NULL, 'Requisição inválida', 'A requisição não pôde ser atendida pela rota chamada — rota inexistente, método não permitido ou outro erro da própria camada web.', ARRAY['A rota não existe.', 'O método não é permitido para o recurso.']::text[], ARRAY['Confira o endereço e o método da requisição.', 'Volte à lista e navegue a partir dela.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:invalid-resource
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:invalid-resource', NULL, 'Recurso de exportação desconhecido', 'O recurso pedido para exportar não existe. Só há exportação de clientes e de imóveis.', ARRAY['O nome do recurso veio errado ou não existe.', 'A rota de exportação mudou.']::text[], ARRAY['Exporte clientes ou imóveis.', 'Confira o nome do recurso na chamada.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:invalid-sort
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:invalid-sort', NULL, 'Ordenação inválida', 'A ordenação pedida não é suportada — a direção é asc ou desc e a chave precisa estar entre as suportadas pela lista.', ARRAY['A direção informada não é asc nem desc.', 'A chave de ordenação não está entre as suportadas.', 'A paginação keyset ordena por nome crescente.']::text[], ARRAY['Use uma ordenação suportada.', 'Para outra ordenação, use a busca por offset.']::text[], 'pesquisar-imoveis', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:invalid-transition
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:invalid-transition', NULL, 'Passo fora da ordem da ordem de serviço', 'A ordem está num estado que não permite este passo. Cada passo só acontece no seu estado: rascunho vira enviada, enviada vira programada, e assim por diante.', ARRAY['A ordem já avançou para outro estado.', 'O passo foi pedido fora da sequência.']::text[], ARRAY['Confira o estado atual da ordem.', 'Faça o passo permitido nesse estado e siga a sequência.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:item-duplicado
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:item-duplicado', NULL, 'Item já existe para este tipo', 'Já existe um item igual para este tipo, com o mesmo material, diâmetro, ângulo, classe e seção. Não dá para cadastrar o mesmo item duas vezes.', ARRAY['O item já foi cadastrado para este tipo.', 'Os campos vieram iguais aos de um item existente.']::text[], ARRAY['Reaproveite o item existente.', 'Mude material, diâmetro, ângulo, classe ou seção para criar outro.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:item-nao-aprovado
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:item-nao-aprovado', NULL, 'Item fora do estado aprovado', 'Só é possível ativar ou desativar um item aprovado. Este item está em outro estado de aprovação.', ARRAY['O item ainda está pendente ou foi recusado.', 'A aprovação ainda não aconteceu.']::text[], ARRAY['Aprove o item primeiro.', 'Depois da aprovação, ative ou desative.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:item-nao-pendente
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:item-nao-pendente', NULL, 'Item fora do estado pendente', 'Só é possível recusar um item pendente de aprovação. Este item já foi aprovado ou recusado.', ARRAY['O item já passou pela aprovação.', 'A recusa foi pedida duas vezes.']::text[], ARRAY['Confira o estado de aprovação do item.', 'Um item aprovado se ativa ou desativa; um recusado se corrige e reenvia.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:mfa-required
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:mfa-required', NULL, 'Segundo fator exigido', 'Esta rota exige segundo fator (OTP ou WebAuthn) e a sessão atual não o tem. Nada foi executado.', ARRAY['A sessão foi aberta sem o segundo fator.']::text[], ARRAY['Entre novamente com o segundo fator.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:no-effect-handler
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:no-effect-handler', NULL, 'Efeito sem execução registrada', 'Nenhuma execução está registrada para este código de efeito, por isso a ordem foi recusada em vez de marcar o efeito como aplicado. Marcar sem executar mentiria o histórico.', ARRAY['O código de efeito não tem manipulador registrado.', 'O tipo foi declarado sem a execução correspondente.']::text[], ARRAY['Peça ao responsável que registre a execução deste efeito.', 'Enquanto isso, não marque o efeito como aplicado à mão.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:no-verifier
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:no-verifier', NULL, 'Classe de alvo sem verificador', 'A classe de alvo não tem verificador registrado, por isso não dá para confirmar que o alvo existe antes de declarar a ordem.', ARRAY['A classe ainda não ganhou verificador.', 'A classe foi digitada errada.']::text[], ARRAY['Use uma classe declaravel com verificador.', 'Peça ao responsável que registre o verificador da classe.']::text[], NULL, FALSE)
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

-- Erro explicado: urn:ziipsan:problem:not-pending-approval
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:not-pending-approval', NULL, 'Ordem fora do estado de aprovação', 'Só uma ordem aguardando aprovação pode ser aprovada. Esta ordem está em outro estado.', ARRAY['A ordem já foi aprovada, recusada ou avançou.', 'A aprovação foi pedida duas vezes.']::text[], ARRAY['Confira o estado atual da ordem.', 'Se ela voltou para rascunho, envie de novo para aprovação.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:operational-types-disabled
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:operational-types-disabled', NULL, 'Tipos operacionais desligados', 'Os tipos operacionais estão desligados pelo parâmetro so.operational_types_enabled, por isso nenhum tipo operacional pode ser declarado agora.', ARRAY['O parâmetro so.operational_types_enabled está desligado.', 'O tenant optou por não usar tipos operacionais.']::text[], ARRAY['Ligue o parâmetro para voltar a usar tipos operacionais.', 'Ou declare um tipo não operacional.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:order-closed
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:order-closed', NULL, 'Ordem encerrada não aceita mais registros', 'A ordem já foi encerrada e não aceita registro de segurança, equipe nem permissão de entrada. O encerramento congela a ordem.', ARRAY['A ordem foi encerrada antes do registro.', 'O registro foi enviado duas vezes, depois do encerramento.']::text[], ARRAY['Confira se a ordem está mesmo encerrada.', 'Para novos registros, abra uma nova ordem.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:precondition-failed
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:precondition-failed', NULL, 'Pré-condição não atendida', 'O If-Match enviado não corresponde à versão atual — o recurso foi alterado por outra pessoa. Nada foi alterado.', ARRAY['Outro usuário alterou o recurso depois que você o abriu.']::text[], ARRAY['Recarregue o recurso e tente de novo com o ETag atual.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:precondition-required
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:precondition-required', NULL, 'Pré-condição exigida', 'A escrita exige o cabeçalho If-Match com o ETag atual do recurso, e ele não foi enviado. Nada foi alterado.', ARRAY['A requisição não trouxe o cabeçalho If-Match.']::text[], ARRAY['Recarregue o recurso, pegue o ETag atual e reenvie com If-Match.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:record-already-deleted
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:record-already-deleted', NULL, 'Registro já excluído', 'O cliente ou imóvel já está excluído, então a exclusão não foi repetida.', ARRAY['O registro já passou por exclusão lógica.']::text[], ARRAY['Volte à lista e confira a situação do registro.']::text[], 'pesquisar-imoveis', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:record-deleted
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:record-deleted', NULL, 'Registro excluído', 'O cliente ou imóvel já está excluído e não pode ser alterado.', ARRAY['O registro passou por exclusão lógica.']::text[], ARRAY['Volte à lista e confira a situação do registro.', 'Se o registro deveria estar ativo, abra um chamado informando o que você tentava fazer.']::text[], 'pesquisar-imoveis', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:safety-gate
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:safety-gate', NULL, 'Portão de segurança bloqueia o passo', 'A ordem não pode seguir sem os itens de segurança exigidos para este serviço (análise de risco, permissão, equipe mínima, treinamento). A mensagem lista o que falta.', ARRAY['Falta análise de risco, permissão ou treinamento exigido.', 'A equipe mínima para o risco não foi montada.']::text[], ARRAY['Complete os itens listados na mensagem.', 'Depois de completar, repita o passo.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:search-in-url
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:search-in-url', NULL, 'Busca na URL', 'A busca por texto vai no corpo da requisição de busca, nunca na URL.', ARRAY['O termo de busca foi enviado como parâmetro da lista (GET).']::text[], ARRAY['Use a busca da tela, que envia o termo no corpo da requisição.']::text[], 'pesquisar-imoveis', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:stale-version
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:stale-version', NULL, 'Versão desatualizada', 'O registro foi alterado por outra pessoa depois que você o abriu. A gravação não foi aplicada, para não sobrescrever a alteração.', ARRAY['Outro usuário salvou o mesmo registro enquanto você editava.']::text[], ARRAY['Recarregue e confira o que mudou antes de tentar de novo.']::text[], 'pesquisar-imoveis', FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:state-conflict
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:state-conflict', NULL, 'Registro em estado que impede a operação', 'O registro mudou de estado ou está num estado que impede esta operação. Nada foi alterado.', ARRAY['Outro usuário alterou o registro antes.', 'A operação exige um estado diferente do atual.']::text[], ARRAY['Recarregue o registro e confira o estado atual.', 'Refaça a operação no estado permitido.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:targets-frozen
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:targets-frozen', NULL, 'Alvos só entram em rascunho ou aberta', 'Alvos só podem ser incluídos enquanto a ordem está em rascunho ou aberta. Depois que ela avança, a lista de alvos congela.', ARRAY['A ordem já foi enviada, programada ou executada.', 'O alvo foi incluído depois do congelamento.']::text[], ARRAY['Inclua os alvos antes de enviar a ordem.', 'Para novos alvos, abra uma nova ordem.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:tenant-inactive
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:tenant-inactive', NULL, 'Tenant indisponível', 'O tenant do token não existe ou não está ativo. Nenhum dado do tenant foi acessado.', ARRAY['O tenant foi desativado ou o identificador está errado.']::text[], ARRAY['Confirme com o administrador do tenant se o acesso está ativo.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:tenant-mismatch
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:tenant-mismatch', NULL, 'Tenant divergente', 'O cabeçalho de tenant não corresponde ao tenant do token, então a requisição foi recusada. Nada foi executado.', ARRAY['O cabeçalho de tenant informa um tenant diferente do token.']::text[], ARRAY['Ajuste o cabeçalho de tenant para o tenant do token e tente de novo.', 'Entre novamente se o token for de outro tenant.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:tenant-missing
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:tenant-missing', NULL, 'Tenant ausente', 'O token não identifica o tenant, então a requisição não pode ser atribuída a nenhum tenant. Nada foi executado.', ARRAY['O token não traz a identificação do tenant.']::text[], ARRAY['Entre novamente.', 'Em integração, obtenha um token que identifique o tenant.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:tenant-not-allowed
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:tenant-not-allowed', NULL, 'Token de tenant em rota de plataforma', 'Rotas de plataforma exigem um token sem tenant (papel PLATFORM_*); um token de tenant não é aceito nelas.', ARRAY['A requisição usou um token de tenant numa rota de plataforma.']::text[], ARRAY['Use um token de plataforma, sem tenant, para esta rota.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:tipo-duplicado
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:tipo-duplicado', NULL, 'Tipo já existe nesta classe', 'Já existe um tipo com este código nesta classe. O código do tipo é único dentro da classe.', ARRAY['O código já foi usado nesta classe.', 'O tipo já foi criado antes.']::text[], ARRAY['Reaproveite o tipo existente.', 'Use outro código para criar um novo tipo.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:tipo-nao-aprovado
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:tipo-nao-aprovado', NULL, 'Tipo fora do estado aprovado', 'Só é possível ativar ou desativar um tipo aprovado. Este tipo está em outro estado de aprovação.', ARRAY['O tipo ainda está pendente ou foi recusado.', 'A aprovação ainda não aconteceu.']::text[], ARRAY['Aprove o tipo primeiro.', 'Depois da aprovação, ative ou desative.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:tipo-nao-pendente
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:tipo-nao-pendente', NULL, 'Tipo fora do estado pendente', 'Só é possível recusar um tipo pendente de aprovação. Este tipo já foi aprovado ou recusado.', ARRAY['O tipo já passou pela aprovação.', 'A recusa foi pedida duas vezes.']::text[], ARRAY['Confira o estado de aprovação do tipo.', 'Um tipo aprovado se ativa ou desativa; um recusado se corrige e reenvia.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:tipo-nao-usavel
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:tipo-nao-usavel', NULL, 'Tipo não aprovado ou inativo', 'Um item só pode ser criado em um tipo aprovado e ativo. Este tipo está pendente, recusado ou desativado.', ARRAY['O tipo ainda não foi aprovado.', 'O tipo foi desativado.']::text[], ARRAY['Aprove e ative o tipo primeiro.', 'Depois disso, crie o item.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:too-many-requests
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:too-many-requests', NULL, 'Muitas requisições', 'O limite de requisições foi excedido. Aguarde antes de tentar de novo; nada foi executado.', ARRAY['Limite de requisições por minuto excedido.', 'Cota de busca por documento por usuário por hora excedida.']::text[], ARRAY['Aguarde o tempo informado e tente novamente.']::text[], NULL, FALSE)
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,
    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,
    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,
    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();

-- Erro explicado: urn:ziipsan:problem:unauthenticated
INSERT INTO support.error_explanation
    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)
VALUES ('urn:ziipsan:problem:unauthenticated', NULL, 'Não autenticado', 'A sessão acabou ou a requisição não trouxe um token de acesso válido. Nada foi executado.', ARRAY['A sessão expirou.', 'O token de acesso está ausente ou inválido.']::text[], ARRAY['Entre novamente.', 'Em integração, envie um token de acesso válido no cabeçalho Authorization.']::text[], NULL, FALSE)
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
