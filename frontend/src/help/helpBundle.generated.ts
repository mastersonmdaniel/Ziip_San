// GERADO por scripts/help/generate-help-content.py a partir de content/help.
// NÃO EDITE À MÃO: regenere (o CI barra o arquivo desatualizado com --check).
export interface HelpArticle {
  slug: string
  title: string
  module: string
  routes: string[]
  roles: string[]
  audience: string
  keywords: string[]
  body: string
}
export interface HelpTourStep {
  target: string
  title: string
  text: string
}
export interface HelpTour {
  id: string
  route: string
  version: number
  title: string
  steps: HelpTourStep[]
}
export interface HelpError {
  code: string
  title: string
  explanation: string
  causes: string[]
  actions: string[]
  article: string | null
  suggestTicket: boolean
}
export interface HelpBundle {
  articles: HelpArticle[]
  tours: HelpTour[]
  hints: Record<string, string>
  errors: HelpError[]
}
export const HELP_BUNDLE: HelpBundle = {
  "articles": [
    {
      "slug": "cadastrar-cliente",
      "title": "Cadastrar cliente",
      "module": "cadastro",
      "routes": [
        "/cadastro/clientes",
        "/cadastro/clientes/novo"
      ],
      "roles": [
        "tenant-user"
      ],
      "audience": "tenant",
      "keywords": [
        "cliente",
        "cadastro",
        "CPF",
        "CNPJ"
      ],
      "body": "# Cadastrar cliente\n\nO cadastro do cliente reúne a identificação (pessoa física ou jurídica) e os\ndados de contato. Preencha os campos obrigatórios e avance por etapas: o\nformulário valida cada etapa antes de permitir continuar.\n\nConfira o documento digitado antes de salvar: um documento errado impede a\nemissão correta da conta e gera retrabalho no atendimento. Depois de salvo, o\ncliente aparece na lista de [clientes](/cadastro/clientes) e pode ser\nvinculado a um imóvel em [pesquisar imóveis](pesquisar-imoveis.md)."
    },
    {
      "slug": "consultar-territorio",
      "title": "Consultar território",
      "module": "cadastro",
      "routes": [
        "/cadastro/territorio"
      ],
      "roles": [
        "tenant-user"
      ],
      "audience": "tenant",
      "keywords": [
        "território",
        "setor",
        "localidade",
        "rota"
      ],
      "body": "# Consultar território\n\nA tela de território mostra a organização geográfica do cadastro: localidades,\nsetores e rotas de leitura. Navegue pelos níveis para localizar a área de um\nimóvel ou de um cliente.\n\nA hierarquia do território é a mesma usada na organização das rotas de\nleitura e na emissão das contas. Alterações na estrutura do território afetam\no cadastro vinculado: confira os imóveis e clientes da área antes de mudar um\nnível."
    },
    {
      "slug": "pesquisar-imoveis",
      "title": "Pesquisar imóveis",
      "module": "cadastro",
      "routes": [
        "/cadastro/imoveis"
      ],
      "roles": [
        "tenant-user"
      ],
      "audience": "tenant",
      "keywords": [
        "imóvel",
        "matrícula",
        "inscrição",
        "pesquisa",
        "endereço"
      ],
      "body": "# Pesquisar imóveis\n\nUse a busca livre para encontrar um imóvel por matrícula, nome, documento ou\nendereço. O servidor reconhece o formato digitado: não é preciso escolher o\ntipo de busca antes de digitar.\n\nSe a inscrição completa estiver preenchida, ela tem prioridade sobre a busca\nlivre. Use a tecla `/` para ir direto ao campo de busca.\n\nPara cadastrar um imóvel novo, use o botão \"Cadastrar imóvel\" no topo da\ntela. Se o cliente ainda não existir, cadastre-o primeiro em\n[clientes](/cadastro/clientes) — veja também\n[cadastrar cliente](cadastrar-cliente.md).\n\nOs filtros adicionais ficam em \"Mais filtros\": localidade, setor e outras\ncaracterísticas do cadastro. Limpe os filtros para voltar à lista completa."
    }
  ],
  "tours": [
    {
      "id": "cadastro-imoveis",
      "route": "/cadastro/imoveis",
      "version": 1,
      "title": "Pesquisar imóveis",
      "steps": [
        {
          "target": "imoveis-search",
          "title": "Busque o imóvel",
          "text": "Digite matrícula, nome, documento ou endereço. A tecla / leva direto a este campo."
        },
        {
          "target": "imoveis-new",
          "title": "Cadastre um imóvel novo",
          "text": "Use este botão quando o imóvel ainda não estiver no cadastro."
        }
      ]
    }
  ],
  "hints": {
    "cadastro.property.inscription": "A inscrição completa tem prioridade sobre a busca livre.",
    "cadastro.property.search": "Matrícula, nome, CPF/CNPJ ou endereço. O servidor reconhece o formato digitado."
  },
  "errors": [
    {
      "code": "urn:ziipsan:problem:access-denied",
      "title": "Acesso negado",
      "explanation": "O usuário autenticado não tem a permissão — ou o papel — que a operação exige. A operação não foi executada.",
      "causes": [
        "O perfil do usuário não inclui a permissão da operação.",
        "A rota exige um papel de plataforma e o token não o tem."
      ],
      "actions": [
        "Peça a um administrador do tenant para revisar o seu perfil.",
        "Se você deveria ter acesso, abra um chamado informando a operação tentada."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:activation-exists",
      "title": "Norma já com ativação em andamento",
      "explanation": "A norma já tem uma ativação proposta, aprovada ou ativa para este tenant. Não é possível propor outra enquanto a atual não for encerrada. Nada foi alterado.",
      "causes": [
        "A norma já foi proposta e aguarda aprovação.",
        "A norma já foi aprovada e aguarda o aceite formal.",
        "A norma já está ativa para este tenant."
      ],
      "actions": [
        "Consulte a lista de ativações e confira o estado atual da norma.",
        "Se a ativação ativa deve ser encerrada, desligue-a antes de propor outra.",
        "Se a proposta está parada, peça a outra pessoa que a aprove ou a retome."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:activation-state",
      "title": "Ativação em estado que impede a operação",
      "explanation": "A ativação está num estado que impede esta operação. Cada passo do ciclo exige o estado anterior: só a proposta pode ser aprovada, só a aprovada pode receber o aceite formal e só a ativa pode ser desligada. Nada foi alterado.",
      "causes": [
        "A ativação já passou deste passo do ciclo.",
        "A ativação foi desligada e precisa de um novo ciclo para voltar.",
        "A ativação ainda não chegou ao passo exigido."
      ],
      "actions": [
        "Recarregue a ativação e confira o estado atual.",
        "Refaça a operação no estado permitido.",
        "Para uma norma desligada, proponha uma nova ativação."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:approval-required",
      "title": "Tipo exige aprovação por outra pessoa",
      "explanation": "Este tipo de serviço só executa depois que outra pessoa aprova a ordem. A execução foi recusada porque a aprovação ainda não aconteceu.",
      "causes": [
        "O tipo tem aprovação por outra pessoa ligada.",
        "A ordem ainda está aguardando aprovação."
      ],
      "actions": [
        "Peça a outra pessoa que aprove a ordem.",
        "Depois da aprovação, execute de novo."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:classe-inativa",
      "title": "Classe de feição desativada",
      "explanation": "Não é possível criar um tipo em uma classe desativada. A classe precisa estar ativa para receber tipos.",
      "causes": [
        "A classe foi desativada no catálogo.",
        "A classe expirou ou foi substituída."
      ],
      "actions": [
        "Reative a classe ou escolha outra classe ativa.",
        "Confira o catálogo antes de criar o tipo."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:clause-map-frozen",
      "title": "Mapa de cláusulas congelado",
      "explanation": "O mapa está congelado porque a norma foi desligada. Ele continua consultável, com as cláusulas e os vínculos preservados, mas não recebe nova evidência. Nada foi alterado.",
      "causes": [
        "A norma foi desligada e o mapa foi congelado no desligamento.",
        "A ativação ainda não foi reativada com um novo aceite."
      ],
      "actions": [
        "Consulte o mapa congelado e os vínculos já registrados.",
        "Para vincular nova evidência, reative a norma com um novo ciclo de proposta, aprovação e aceite."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:confirmation-required",
      "title": "Confirmação necessária",
      "explanation": "A operação gerou avisos que precisam da sua confirmação antes de continuar. Nada foi salvo ainda.",
      "causes": [
        "Um ou mais avisos exigem confirmação explícita."
      ],
      "actions": [
        "Leia os avisos com atenção.",
        "Confirme para prosseguir ou ajuste os dados e envie de novo."
      ],
      "article": "cadastrar-cliente",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:content-too-large",
      "title": "Conteúdo muito grande",
      "explanation": "O corpo da requisição excede o limite permitido e foi recusado antes de qualquer processamento.",
      "causes": [
        "O corpo enviado é maior que o limite."
      ],
      "actions": [
        "Reduza o tamanho do corpo e envie de novo."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:customer-has-active-link",
      "title": "Cliente com vínculo vigente",
      "explanation": "O cliente tem vínculo vigente com imóvel e não pode ser excluído antes de encerrar os vínculos.",
      "causes": [
        "Há pelo menos um vínculo vigente entre o cliente e um imóvel."
      ],
      "actions": [
        "Encerre os vínculos do cliente antes de excluir."
      ],
      "article": "cadastrar-cliente",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:customer-without-document",
      "title": "Cliente sem documento",
      "explanation": "A operação exige o documento do cliente, e ele não está cadastrado.",
      "causes": [
        "Nenhum documento foi cadastrado para o cliente."
      ],
      "actions": [
        "Cadastre o documento do cliente e tente de novo."
      ],
      "article": "cadastrar-cliente",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:data-conflict",
      "title": "Conflito de dados",
      "explanation": "A operação não pode ser concluída porque os dados mudaram ou já existe um registro com os mesmos dados.",
      "causes": [
        "Outro usuário alterou o mesmo registro enquanto você editava.",
        "Já existe um registro com o identificador informado."
      ],
      "actions": [
        "Recarregue os dados e confira o que mudou antes de tentar de novo.",
        "Se o problema persistir, abra um chamado informando o que você tentava fazer."
      ],
      "article": "pesquisar-imoveis",
      "suggestTicket": true
    },
    {
      "code": "urn:ziipsan:problem:deletion-unavailable",
      "title": "Exclusão indisponível",
      "explanation": "O recurso não tem exclusão lógica no cadastro, então a exclusão não foi executada.",
      "causes": [
        "O tipo de registro não prevê exclusão lógica."
      ],
      "actions": [
        "Localidade, setor comercial e quadra: bloqueie com blocked=true.",
        "Rota de leitura: desative com active=false."
      ],
      "article": "consultar-territorio",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:effects-pending",
      "title": "Efeitos pendentes impedem o encerramento",
      "explanation": "Há alvos com efeito ainda não aplicado, por isso a ordem não pode ser encerrada como executada. Encerrar assim marcaria trabalho como feito sem aplicar o efeito.",
      "causes": [
        "Um ou mais alvos ainda não tiveram o efeito aplicado.",
        "A aplicação foi interrompida antes de terminar."
      ],
      "actions": [
        "Aplique os efeitos pendentes e tente encerrar de novo.",
        "Se o trabalho não foi feito, encerre como não executada."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:evidence-link-exists",
      "title": "Evidência já vinculada à cláusula",
      "explanation": "Esta evidência já está vinculada a esta cláusula do mapa. O vínculo é único por evidência e cláusula; a mesma evidência pode servir a outras cláusulas, mas não duas vezes à mesma. Nada foi alterado.",
      "causes": [
        "O vínculo já foi criado antes para esta evidência e esta cláusula.",
        "A operação foi enviada duas vezes."
      ],
      "actions": [
        "Consulte os vínculos da cláusula para confirmar o que já existe.",
        "Para cobrir outra cláusula com a mesma evidência, vincule-a à outra linha do mapa."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:filter-unavailable",
      "title": "Filtro indisponível",
      "explanation": "Um filtro informado ainda não tem suporte no cadastro, então a busca não foi executada.",
      "causes": [
        "Imóveis: profileId, numberFrom e numberTo ainda não têm suporte.",
        "Clientes: o filtro city ainda não é suportado."
      ],
      "actions": [
        "Remova o filtro indisponível e tente de novo."
      ],
      "article": "pesquisar-imoveis",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:inactive-type",
      "title": "Tipo de serviço inativo",
      "explanation": "O tipo de serviço está inativo e não aceita novas ordens. Nada foi criado.",
      "causes": [
        "O tipo foi desativado no catálogo.",
        "O tipo expirou ou foi substituído."
      ],
      "actions": [
        "Reative o tipo ou escolha outro tipo ativo.",
        "Confira o catálogo de tipos antes de criar a ordem."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:internal-error",
      "title": "Erro interno",
      "explanation": "Ocorreu um erro inesperado no servidor. Informe o identificador da requisição ao suporte.",
      "causes": [
        "Uma falha não tratada interrompeu a operação."
      ],
      "actions": [
        "Tente novamente em instantes.",
        "Se o problema persistir, abra um chamado informando o identificador da requisição."
      ],
      "article": null,
      "suggestTicket": true
    },
    {
      "code": "urn:ziipsan:problem:invalid-cursor",
      "title": "Cursor inválido",
      "explanation": "O cursor de paginação é inválido — ele não pôde ser decodificado como a próxima página da busca keyset.",
      "causes": [
        "O cursor não tem o formato esperado (valor codificado, ponto e identificador).",
        "O valor codificado não é base64 válido ou o identificador não é um UUID."
      ],
      "actions": [
        "Volte à primeira página da busca e avance de novo a partir dela."
      ],
      "article": "pesquisar-imoveis",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:invalid-filter",
      "title": "Filtro inválido",
      "explanation": "Um filtro foi informado com valor fora do aceito, então a busca não foi executada.",
      "causes": [
        "nameMatch aceita apenas contains ou startsWith.",
        "Na busca de quadras, o texto deve ser só o código (dígitos)."
      ],
      "actions": [
        "Ajuste o filtro para um valor aceito e tente de novo."
      ],
      "article": "pesquisar-imoveis",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:invalid-inscription",
      "title": "Inscrição inválida",
      "explanation": "A inscrição informada não está no formato LLL.SSS.QQQ.LLLL.SSS.",
      "causes": [
        "A inscrição foi digitada fora do formato esperado."
      ],
      "actions": [
        "Confira a inscrição e digite-a no formato LLL.SSS.QQQ.LLLL.SSS."
      ],
      "article": "pesquisar-imoveis",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:invalid-page",
      "title": "Página inválida",
      "explanation": "A paginação pedida é inválida — a página começa em 0, o tamanho deve ser ao menos 1 e a página não pode exceder o limite suportado.",
      "causes": [
        "Página negativa.",
        "Tamanho de página menor que 1.",
        "A página pedida excede o limite suportado."
      ],
      "actions": [
        "Ajuste a página e o tamanho e tente de novo."
      ],
      "article": "pesquisar-imoveis",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:invalid-request",
      "title": "Requisição inválida",
      "explanation": "A requisição não pôde ser atendida pela rota chamada — rota inexistente, método não permitido ou outro erro da própria camada web.",
      "causes": [
        "A rota não existe.",
        "O método não é permitido para o recurso."
      ],
      "actions": [
        "Confira o endereço e o método da requisição.",
        "Volte à lista e navegue a partir dela."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:invalid-resource",
      "title": "Recurso de exportação desconhecido",
      "explanation": "O recurso pedido para exportar não existe. Só há exportação de clientes e de imóveis.",
      "causes": [
        "O nome do recurso veio errado ou não existe.",
        "A rota de exportação mudou."
      ],
      "actions": [
        "Exporte clientes ou imóveis.",
        "Confira o nome do recurso na chamada."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:invalid-sort",
      "title": "Ordenação inválida",
      "explanation": "A ordenação pedida não é suportada — a direção é asc ou desc e a chave precisa estar entre as suportadas pela lista.",
      "causes": [
        "A direção informada não é asc nem desc.",
        "A chave de ordenação não está entre as suportadas.",
        "A paginação keyset ordena por nome crescente."
      ],
      "actions": [
        "Use uma ordenação suportada.",
        "Para outra ordenação, use a busca por offset."
      ],
      "article": "pesquisar-imoveis",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:invalid-transition",
      "title": "Passo fora da ordem da ordem de serviço",
      "explanation": "A ordem está num estado que não permite este passo. Cada passo só acontece no seu estado: rascunho vira enviada, enviada vira programada, e assim por diante.",
      "causes": [
        "A ordem já avançou para outro estado.",
        "O passo foi pedido fora da sequência."
      ],
      "actions": [
        "Confira o estado atual da ordem.",
        "Faça o passo permitido nesse estado e siga a sequência."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:item-duplicado",
      "title": "Item já existe para este tipo",
      "explanation": "Já existe um item igual para este tipo, com o mesmo material, diâmetro, ângulo, classe e seção. Não dá para cadastrar o mesmo item duas vezes.",
      "causes": [
        "O item já foi cadastrado para este tipo.",
        "Os campos vieram iguais aos de um item existente."
      ],
      "actions": [
        "Reaproveite o item existente.",
        "Mude material, diâmetro, ângulo, classe ou seção para criar outro."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:item-nao-aprovado",
      "title": "Item fora do estado aprovado",
      "explanation": "Só é possível ativar ou desativar um item aprovado. Este item está em outro estado de aprovação.",
      "causes": [
        "O item ainda está pendente ou foi recusado.",
        "A aprovação ainda não aconteceu."
      ],
      "actions": [
        "Aprove o item primeiro.",
        "Depois da aprovação, ative ou desative."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:item-nao-pendente",
      "title": "Item fora do estado pendente",
      "explanation": "Só é possível recusar um item pendente de aprovação. Este item já foi aprovado ou recusado.",
      "causes": [
        "O item já passou pela aprovação.",
        "A recusa foi pedida duas vezes."
      ],
      "actions": [
        "Confira o estado de aprovação do item.",
        "Um item aprovado se ativa ou desativa; um recusado se corrige e reenvia."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:mfa-required",
      "title": "Segundo fator exigido",
      "explanation": "Esta rota exige segundo fator (OTP ou WebAuthn) e a sessão atual não o tem. Nada foi executado.",
      "causes": [
        "A sessão foi aberta sem o segundo fator."
      ],
      "actions": [
        "Entre novamente com o segundo fator."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:no-effect-handler",
      "title": "Efeito sem execução registrada",
      "explanation": "Nenhuma execução está registrada para este código de efeito, por isso a ordem foi recusada em vez de marcar o efeito como aplicado. Marcar sem executar mentiria o histórico.",
      "causes": [
        "O código de efeito não tem manipulador registrado.",
        "O tipo foi declarado sem a execução correspondente."
      ],
      "actions": [
        "Peça ao responsável que registre a execução deste efeito.",
        "Enquanto isso, não marque o efeito como aplicado à mão."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:no-verifier",
      "title": "Classe de alvo sem verificador",
      "explanation": "A classe de alvo não tem verificador registrado, por isso não dá para confirmar que o alvo existe antes de declarar a ordem.",
      "causes": [
        "A classe ainda não ganhou verificador.",
        "A classe foi digitada errada."
      ],
      "actions": [
        "Use uma classe declaravel com verificador.",
        "Peça ao responsável que registre o verificador da classe."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:not-found",
      "title": "Registro não encontrado",
      "explanation": "O item que você procurou não existe neste tenant ou foi removido. Por segurança, um item de outro tenant devolve a mesma resposta.",
      "causes": [
        "O endereço ou o identificador digitado está errado.",
        "O item foi excluído ou ainda não foi criado."
      ],
      "actions": [
        "Confira o identificador e tente de novo.",
        "Volte à lista e pesquise pelo nome ou pelo endereço."
      ],
      "article": "pesquisar-imoveis",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:not-pending-approval",
      "title": "Ordem fora do estado de aprovação",
      "explanation": "Só uma ordem aguardando aprovação pode ser aprovada. Esta ordem está em outro estado.",
      "causes": [
        "A ordem já foi aprovada, recusada ou avançou.",
        "A aprovação foi pedida duas vezes."
      ],
      "actions": [
        "Confira o estado atual da ordem.",
        "Se ela voltou para rascunho, envie de novo para aprovação."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:operational-types-disabled",
      "title": "Tipos operacionais desligados",
      "explanation": "Os tipos operacionais estão desligados pelo parâmetro so.operational_types_enabled, por isso nenhum tipo operacional pode ser declarado agora.",
      "causes": [
        "O parâmetro so.operational_types_enabled está desligado.",
        "O tenant optou por não usar tipos operacionais."
      ],
      "actions": [
        "Ligue o parâmetro para voltar a usar tipos operacionais.",
        "Ou declare um tipo não operacional."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:order-closed",
      "title": "Ordem encerrada não aceita mais registros",
      "explanation": "A ordem já foi encerrada e não aceita registro de segurança, equipe nem permissão de entrada. O encerramento congela a ordem.",
      "causes": [
        "A ordem foi encerrada antes do registro.",
        "O registro foi enviado duas vezes, depois do encerramento."
      ],
      "actions": [
        "Confira se a ordem está mesmo encerrada.",
        "Para novos registros, abra uma nova ordem."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:precondition-failed",
      "title": "Pré-condição não atendida",
      "explanation": "O If-Match enviado não corresponde à versão atual — o recurso foi alterado por outra pessoa. Nada foi alterado.",
      "causes": [
        "Outro usuário alterou o recurso depois que você o abriu."
      ],
      "actions": [
        "Recarregue o recurso e tente de novo com o ETag atual."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:precondition-required",
      "title": "Pré-condição exigida",
      "explanation": "A escrita exige o cabeçalho If-Match com o ETag atual do recurso, e ele não foi enviado. Nada foi alterado.",
      "causes": [
        "A requisição não trouxe o cabeçalho If-Match."
      ],
      "actions": [
        "Recarregue o recurso, pegue o ETag atual e reenvie com If-Match."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:record-already-deleted",
      "title": "Registro já excluído",
      "explanation": "O cliente ou imóvel já está excluído, então a exclusão não foi repetida.",
      "causes": [
        "O registro já passou por exclusão lógica."
      ],
      "actions": [
        "Volte à lista e confira a situação do registro."
      ],
      "article": "pesquisar-imoveis",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:record-deleted",
      "title": "Registro excluído",
      "explanation": "O cliente ou imóvel já está excluído e não pode ser alterado.",
      "causes": [
        "O registro passou por exclusão lógica."
      ],
      "actions": [
        "Volte à lista e confira a situação do registro.",
        "Se o registro deveria estar ativo, abra um chamado informando o que você tentava fazer."
      ],
      "article": "pesquisar-imoveis",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:safety-gate",
      "title": "Portão de segurança bloqueia o passo",
      "explanation": "A ordem não pode seguir sem os itens de segurança exigidos para este serviço (análise de risco, permissão, equipe mínima, treinamento). A mensagem lista o que falta.",
      "causes": [
        "Falta análise de risco, permissão ou treinamento exigido.",
        "A equipe mínima para o risco não foi montada."
      ],
      "actions": [
        "Complete os itens listados na mensagem.",
        "Depois de completar, repita o passo."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:search-in-url",
      "title": "Busca na URL",
      "explanation": "A busca por texto vai no corpo da requisição de busca, nunca na URL.",
      "causes": [
        "O termo de busca foi enviado como parâmetro da lista (GET)."
      ],
      "actions": [
        "Use a busca da tela, que envia o termo no corpo da requisição."
      ],
      "article": "pesquisar-imoveis",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:stale-version",
      "title": "Versão desatualizada",
      "explanation": "O registro foi alterado por outra pessoa depois que você o abriu. A gravação não foi aplicada, para não sobrescrever a alteração.",
      "causes": [
        "Outro usuário salvou o mesmo registro enquanto você editava."
      ],
      "actions": [
        "Recarregue e confira o que mudou antes de tentar de novo."
      ],
      "article": "pesquisar-imoveis",
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:state-conflict",
      "title": "Registro em estado que impede a operação",
      "explanation": "O registro mudou de estado ou está num estado que impede esta operação. Nada foi alterado.",
      "causes": [
        "Outro usuário alterou o registro antes.",
        "A operação exige um estado diferente do atual."
      ],
      "actions": [
        "Recarregue o registro e confira o estado atual.",
        "Refaça a operação no estado permitido."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:targets-frozen",
      "title": "Alvos só entram em rascunho ou aberta",
      "explanation": "Alvos só podem ser incluídos enquanto a ordem está em rascunho ou aberta. Depois que ela avança, a lista de alvos congela.",
      "causes": [
        "A ordem já foi enviada, programada ou executada.",
        "O alvo foi incluído depois do congelamento."
      ],
      "actions": [
        "Inclua os alvos antes de enviar a ordem.",
        "Para novos alvos, abra uma nova ordem."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:tenant-inactive",
      "title": "Tenant indisponível",
      "explanation": "O tenant do token não existe ou não está ativo. Nenhum dado do tenant foi acessado.",
      "causes": [
        "O tenant foi desativado ou o identificador está errado."
      ],
      "actions": [
        "Confirme com o administrador do tenant se o acesso está ativo."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:tenant-mismatch",
      "title": "Tenant divergente",
      "explanation": "O cabeçalho de tenant não corresponde ao tenant do token, então a requisição foi recusada. Nada foi executado.",
      "causes": [
        "O cabeçalho de tenant informa um tenant diferente do token."
      ],
      "actions": [
        "Ajuste o cabeçalho de tenant para o tenant do token e tente de novo.",
        "Entre novamente se o token for de outro tenant."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:tenant-missing",
      "title": "Tenant ausente",
      "explanation": "O token não identifica o tenant, então a requisição não pode ser atribuída a nenhum tenant. Nada foi executado.",
      "causes": [
        "O token não traz a identificação do tenant."
      ],
      "actions": [
        "Entre novamente.",
        "Em integração, obtenha um token que identifique o tenant."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:tenant-not-allowed",
      "title": "Token de tenant em rota de plataforma",
      "explanation": "Rotas de plataforma exigem um token sem tenant (papel PLATFORM_*); um token de tenant não é aceito nelas.",
      "causes": [
        "A requisição usou um token de tenant numa rota de plataforma."
      ],
      "actions": [
        "Use um token de plataforma, sem tenant, para esta rota."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:tipo-duplicado",
      "title": "Tipo já existe nesta classe",
      "explanation": "Já existe um tipo com este código nesta classe. O código do tipo é único dentro da classe.",
      "causes": [
        "O código já foi usado nesta classe.",
        "O tipo já foi criado antes."
      ],
      "actions": [
        "Reaproveite o tipo existente.",
        "Use outro código para criar um novo tipo."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:tipo-nao-aprovado",
      "title": "Tipo fora do estado aprovado",
      "explanation": "Só é possível ativar ou desativar um tipo aprovado. Este tipo está em outro estado de aprovação.",
      "causes": [
        "O tipo ainda está pendente ou foi recusado.",
        "A aprovação ainda não aconteceu."
      ],
      "actions": [
        "Aprove o tipo primeiro.",
        "Depois da aprovação, ative ou desative."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:tipo-nao-pendente",
      "title": "Tipo fora do estado pendente",
      "explanation": "Só é possível recusar um tipo pendente de aprovação. Este tipo já foi aprovado ou recusado.",
      "causes": [
        "O tipo já passou pela aprovação.",
        "A recusa foi pedida duas vezes."
      ],
      "actions": [
        "Confira o estado de aprovação do tipo.",
        "Um tipo aprovado se ativa ou desativa; um recusado se corrige e reenvia."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:tipo-nao-usavel",
      "title": "Tipo não aprovado ou inativo",
      "explanation": "Um item só pode ser criado em um tipo aprovado e ativo. Este tipo está pendente, recusado ou desativado.",
      "causes": [
        "O tipo ainda não foi aprovado.",
        "O tipo foi desativado."
      ],
      "actions": [
        "Aprove e ative o tipo primeiro.",
        "Depois disso, crie o item."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:too-many-requests",
      "title": "Muitas requisições",
      "explanation": "O limite de requisições foi excedido. Aguarde antes de tentar de novo; nada foi executado.",
      "causes": [
        "Limite de requisições por minuto excedido.",
        "Cota de busca por documento por usuário por hora excedida."
      ],
      "actions": [
        "Aguarde o tempo informado e tente novamente."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:unauthenticated",
      "title": "Não autenticado",
      "explanation": "A sessão acabou ou a requisição não trouxe um token de acesso válido. Nada foi executado.",
      "causes": [
        "A sessão expirou.",
        "O token de acesso está ausente ou inválido."
      ],
      "actions": [
        "Entre novamente.",
        "Em integração, envie um token de acesso válido no cabeçalho Authorization."
      ],
      "article": null,
      "suggestTicket": false
    },
    {
      "code": "urn:ziipsan:problem:validation",
      "title": "Campos inválidos",
      "explanation": "Alguns campos do formulário não passaram na validação. Nada foi salvo: corrija os campos indicados e envie de novo.",
      "causes": [
        "Um campo obrigatório ficou em branco.",
        "Um valor está fora do formato esperado (documento, data ou número)."
      ],
      "actions": [
        "Leia a mensagem de cada campo destacado no formulário.",
        "Corrija os campos e envie novamente."
      ],
      "article": "cadastrar-cliente",
      "suggestTicket": false
    }
  ]
}
