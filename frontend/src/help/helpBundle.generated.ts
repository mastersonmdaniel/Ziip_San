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
