package br.com.ziipsan.security.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Catálogo de permissões {@code recurso:ação} do produto (P0.13, ADR-0040) e perfis de
 * fábrica por função (P1.37, decisão do dono de 28/10/2026, JOR-01).
 *
 * <p>Formato único: um recurso, dois-pontos, uma ação, sem ponto ({@code registry:reveal-document},
 * nunca {@code registry.reveal-document}). A negação é o padrão: um papel ou permissão
 * desconhecida não concede nada ({@link #authoritiesForRole(String)} devolve vazio).
 *
 * <p>O Keycloak autentica e entrega identidade, tenant e perfis; a permissão fina é resolvida
 * na aplicação, a partir daqui. Um token pode carregar um perfil de fábrica (ex.: {@code ATENDENTE},
 * expandido para as permissões do perfil) ou uma permissão direta do catálogo (ex.:
 * {@code registry:reveal-document}, que nenhum perfil de fábrica inclui de propósito: quem
 * cadastra não precisa, em regra, ver o documento depois de gravado).
 *
 * <p>Permissão nova entra aqui junto da rota que a usa; o documento em
 * {@code docs/seguranca/CATALOGO-PERMISSOES.md} é conferido contra esta lista no CI, e o
 * teste do CI recusa permissão do catálogo que não esteja em nenhum perfil de fábrica nem
 * na lista explícita de exceções ({@link #coverageExemptions()}): esquecer um perfil é
 * falha de build, não concessão por omissão.
 *
 * <p>P1.37 — o que cabe nesta branch. O catálogo desta branch só tem 16 permissões: as de
 * cadastro, gestão de usuários, catálogo GIS, plataforma e parâmetros. Os domínios ainda
 * sem permissão catalogada não dão permissão a nenhum perfil — os
 * perfis dessas funções nascem com o que o catálogo tem (em vários casos, só
 * {@code registry:read} ou nada) e ganham as permissões do domínio na tarefa que as
 * catalogar, inclusive nas frentes paralelas da camada C: a integração reconcilia o
 * restante, e o teste de cobertura a obriga a fazê-lo (permissão nova sem perfil falha).
 * Nenhuma permissão é inventada aqui para preencher esses perfis.
 *
 * <p>Versão: {@link #VERSION} acompanha a versão do pacote {@code sanitation} em
 * {@code packages/sanitation/vertical.json} (o manifesto declara os mesmos perfis, e o
 * teste de manifesto confere os dois lados). Página inicial: todo perfil declara
 * {@code /inicio} — a página em si é construída pela P3.39, que a compõe por perfil; a
 * P1.37 apenas a atribui. O menu de cada perfil deriva do mapa do produto da P0.47: o
 * frontend só exibe o item cuja permissão o perfil tem (P1.12), e o servidor continua
 * negando a rota sem a permissão.
 *
 * <p>Integração P1-C: a reconciliação das frentes paralelas atribui protocolo (P1.36),
 * marca (P1.19), certificações (P1.32) e suporte (P1.23) aos perfis pertinentes, e
 * registra central:* (P1.17) e support:queue (P1.23) como permissões de papel de
 * plataforma. A busca global e suas leituras (P1.33) NÃO entram em perfis, por decisão
 * explícita da frente P1.33 (concessão direta, com teste): ficam nas exceções de
 * cobertura, como registry:export. O manifesto espelha os 26 perfis exatamente.
 */
public final class PermissionCatalog {

    /** Uma permissão: nome {@code recurso:ação}, módulo, recurso, ação e tarefa de origem. */
    public record Permission(String name, String module, String resource, String action, String originTask) {
    }

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");

    /** Versão dos perfis de fábrica: a versão do pacote sanitation (P1.37). */
    public static final String VERSION = "0.1.0";

    /** Página inicial atribuída a todo perfil de fábrica; construída pela P3.39. */
    public static final String HOME_PAGE = "/inicio";

    public static final List<Permission> ALL = List.of(
            new Permission("registry:read", "cadastro", "registry", "read", "P0.13"),
            new Permission("registry:write", "cadastro", "registry", "write", "P0.13"),
            new Permission("registry:reveal-document", "cadastro", "registry", "reveal-document", "P0.13"),
            new Permission("registry:search-document", "cadastro", "registry", "search-document", "P0.11"),
            // P1.12: exportação de listas, uma por recurso (`*:export` do plano).
            // Como revelar e buscar por documento, não está em nenhum perfil de
            // fábrica: exportar dado pessoal exige concessão direta.
            new Permission("registry:export", "cadastro", "registry", "export", "P1.12"),
            new Permission("user:read", "plataforma", "user", "read", "P0.13"),
            new Permission("user:write", "plataforma", "user", "write", "P0.13"),
            // P1.27: catálogo GIS do tenant (GIS-CADASTRO-TECNICO §4.1 e §6.6, onde a
            // permissão aparece na forma pontuada gis.catalog.manage; aqui ela segue o
            // formato único recurso:ação da P0.13). Perfis por função na P1.37:
            // editor do cadastro técnico gerencia, revisor só lê.
            // Recurso "gis", ação "catalog-read"/"catalog-manage": o nome tem de ser
            // recurso:ação (contrato da P0.13, verificado pelo PermissionCatalogTest).
            new Permission("gis:catalog-read", "gis", "gis", "catalog-read", "P1.27"),
            new Permission("gis:catalog-manage", "gis", "gis", "catalog-manage", "P1.27"),
            // P0.43: leitura do registro de implantação (platform.deployment), rota
            // GET /api/v1/platform/deployments. Papéis de plataforma que a têm:
            // PLATFORM_OWNER e PLATFORM_OPS (ver PlatformRole); SUPPORT e FINANCE, não.
            new Permission("deployment:read", "plataforma", "deployment", "read", "P0.43"),
            // P1.6 (integracao P1-B): o console usava console:read/manage/suspend/
            // delete nas rotas sem registra-las no catalogo, e o
            // RoutePermissionTest recusava todas. Registradas aqui, sem entrar
            // em nenhum perfil de fabrica de tenant: elas so existem via papel
            // de plataforma (ver PlatformRole), como deployment:read.
            new Permission("console:read", "console", "console", "read", "P1.6"),
            new Permission("console:manage", "console", "console", "manage", "P1.6"),
            new Permission("console:suspend", "console", "console", "suspend", "P1.6"),
            new Permission("console:delete", "console", "console", "delete", "P1.6"),
            // P1.17 (integracao P1-C): o mesmo caso do console — a Central usava
            // central:read/central:manage nas rotas /api/v1/platform/central/*
            // e /api/v1/platform/metrics/* sem registra-las no catalogo, e o
            // RoutePermissionTest recusaria todas. Registradas aqui, sem entrar
            // em nenhum perfil de fabrica de tenant: elas so existem via papel
            // de plataforma (ver PlatformRole), como console:*.
            new Permission("central:read", "central", "central", "read", "P1.17"),
            new Permission("central:manage", "central", "central", "manage", "P1.17"),
            // P2.12 (integracao P2-A): o mesmo caso do console e da Central —
            // contratos SaaS usam contract:read/manage/restrict nas rotas
            // /api/v1/platform/contracts/* e na aba Contratos da Central, e
            // o RoutePermissionTest recusaria todas sem registro. Registradas
            // aqui, sem entrar em nenhum perfil de fabrica de tenant: elas
            // so existem via papel de plataforma (ver PlatformRole), como
            // console:* e central:*. Leitura: os quatro papeis; gestao:
            // OWNER e FINANCE; restricao: OWNER e FINANCE, com a matriz
            // fina (FINANCE so por inadimplencia, com cobrança vencida e
            // aviso) no servico, porque a permissao sozinha nao distingue
            // o motivo nem a existencia dos elos.
            new Permission("contract:read", "contratos", "contract", "read", "P2.12"),
            new Permission("contract:manage", "contratos", "contract", "manage", "P2.12"),
            new Permission("contract:restrict", "contratos", "contract", "restrict", "P2.12"),
            // P2.14: o outro lado do contrato — o tenant le o PROPRIO
            // contrato (rotas /api/v1/tenant/my-contract/*), nunca o de
            // outro tenant (o servico responde 404 fora do escopo). Ao
            // contrario das tres de plataforma acima, esta ENTRA nos
            // perfis de fabrica (ADMIN, GESTOR, CONTADOR e
            // AUDITOR_INTERNO): sem perfil, o teste de cobertura a
            // recusaria, e sem ela nenhum perfil de tenant abriria a
            // area do contrato. Acao "read-own": leitura restrita ao
            // proprio tenant, distinguivel de contract:read (plataforma,
            // qualquer tenant).
            new Permission("contract:read-own", "contratos", "contract", "read-own", "P2.14"),
            // P1.32: núcleo do módulo Certificações de qualidade (ADR-0041).
            // Nenhum perfil de fábrica as inclui ainda: perfis por função são
            // da P1.37; hoje são concessão direta, como gis:catalog-*.
            // cert:read lê catálogo, ativações e mapas; cert:write propõe
            // ativação, desliga e vincula evidência; cert:approve aprova e
            // aceita a ativação; cert:audit lê o vínculo de evidência pelo
            // lado da evidência (a visão de auditoria do muitos-para-muitos).
            new Permission("cert:read", "certificacao", "cert", "read", "P1.32"),
            new Permission("cert:write", "certificacao", "cert", "write", "P1.32"),
            new Permission("cert:approve", "certificacao", "cert", "approve", "P1.32"),
            new Permission("cert:audit", "certificacao", "cert", "audit", "P1.32"),
            // P1.6 (integracao P1-B): a tela de mudanca de parametro do tenant
            // (/api/v1/tenant/parameters/*) nasceu sem permissao fina — a frente
            // declarou a lacuna e o RoutePermissionTest recusava as 6 rotas.
            // P1.37: deixa de ser concessão direta e entra nos perfis de fábrica
            // pertinentes (ADMIN e GESTOR, abaixo). A separação proponente ≠
            // aprovador continua no serviço, por ator, não por perfil.
            new Permission("parameters:manage", "console", "parameters", "manage", "P1.6"),
            // P1.36: protocolo de atendimento (NR 11, art. 52). Concessao
            // direta, como gis:catalog-* e parameters:manage: perfis por
            // funcao (atendente, supervisor, ouvidor) sao da P1.37; ate la,
            // ler e escrever protocolos exige a permissao direta.
            new Permission("protocol:read", "atendimento", "protocol", "read", "P1.36"),
            new Permission("protocol:write", "atendimento", "protocol", "write", "P1.36"),
            // P1.19: administracao da marca do tenant (/api/v1/tenant/brand).
            // Concessao direta, como parameters:manage: nenhum perfil de
            // fabrica a inclui; perfis por funcao sao da P1.37.
            new Permission("brand:manage", "console", "brand", "manage", "P1.19"),
            // P1.23: chamados de suporte do tenant ao Ziip (ADR-0020). Concessao
            // direta, como registry:export — perfis por função são da P1.37.
            // read: ver os próprios chamados; write: abrir e responder os
            // próprios; manage: ver todos os do tenant (equivalente a admin
            // do tenant na v0); queue: fila da Central (só via papel de
            // plataforma, ver PlatformRole — token de tenant nunca a recebe).
            new Permission("support:read", "suporte", "support", "read", "P1.23"),
            new Permission("support:write", "suporte", "support", "write", "P1.23"),
            new Permission("support:manage", "suporte", "support", "manage", "P1.23"),
            new Permission("support:queue", "suporte", "support", "queue", "P1.23"),
            // P1.33: busca global, visoes salvas e atalhos. `search:read` e o portao
            // de entrada das rotas /api/v1/search/* (concessao direta, nenhum perfil
            // de fabrica — perfis por funcao sao da P1.37). Cada secao da busca
            // global ainda exige a permissao do proprio recurso (abaixo): o portao
            // nao amplia leitura de nenhum recurso, e secao sem permissao volta em
            // deniedTypes, sem dado. `service-order:read`, `billing:read` e
            // `document:read` nascem aqui porque OS, conta e documento ainda nao
            // tem superficie web nem permissao de leitura no catalogo — sao
            // concessao direta, pelo mesmo precedente de registry:export.
            new Permission("search:read", "busca", "search", "read", "P1.33"),
            new Permission("service-order:read", "ordem de servico", "service-order", "read", "P1.33"),
            new Permission("billing:read", "cobrança", "billing", "read", "P1.33"),
            new Permission("document:read", "documentos", "document", "read", "P1.33"),
            // P1.35 (integracao P1-C): o mesmo caso do console e da Central — o
            // cadastro de ativos usava asset:read/asset:manage nas rotas
            // /api/v1/assets/* (ver AssetRoles) sem registra-las no catalogo, e o
            // RoutePermissionTest recusava todas. Registradas aqui, como
            // concessao direta: a propria frente as designou assim (perfis por
            // funcao sao da P1.37; nenhum perfil de fabrica as inclui, e
            // inventar atribuicao violaria a regra da P1.37).
            new Permission("asset:read", "ativos", "asset", "read", "P1.35"),
            new Permission("asset:manage", "ativos", "asset", "manage", "P1.35"),
            // P2.30: motor único de modelos de documento e mensagem
            // (/api/v1/templates/*). read: listar e ler modelos, gerar prévia e
            // gerar documento; write: criar modelo e versão, propor e copiar
            // fábrica; approve: aprovar e rejeitar versão (a separação
            // proponente ≠ aprovador e autor ≠ aprovador continua no serviço,
            // por ator, não por perfil — como parameters:manage na P1.6).
            // Perfis: ADMIN e GESTOR (administram o tenant) têm as três;
            // AUDITOR_INTERNO lê (audita o que foi aprovado), sem escrever
            // nem aprovar.
            new Permission("template:read", "documentos", "template", "read", "P2.30"),
            new Permission("template:write", "documentos", "template", "write", "P2.30"),
            new Permission("template:approve", "documentos", "template", "approve", "P2.30"),
            // P2.26: tela de credenciais e webhooks do tenant
            // (/api/v1/tenant/integration/*). Vai ao ADMIN: quem administra o
            // tenant gerencia os clientes da API pública e os endpoints.
            new Permission("integration:manage", "integração", "integration", "manage", "P2.26"),
            // P2.26: escopos OAuth 2.0 da API pública (plano §P2.26, catálogo
            // recurso:ação do ADR-0040). São escopos de CLIENTE, não
            // permissões de usuário: o @PreAuthorize das rotas públicas
            // verifica o escopo do token opaco, nunca um perfil de usuário —
            // por isso ficam nas exceções de cobertura abaixo, como
            // support:queue (papel de plataforma) e search:read (concessão
            // direta), e não em perfil de fábrica. Atribuir escopo de
            // cliente a perfil de usuário daria ao usuário uma autoridade
            // que nenhuma rota de usuário honra.
            new Permission("properties:read", "integração", "properties", "read", "P2.26"),
            new Permission("bills:read", "integração", "bills", "read", "P2.26"),
            new Permission("payments:read", "integração", "payments", "read", "P2.26"),
            new Permission("service-orders:read", "integração", "service-orders", "read", "P2.26"),
            new Permission("service-orders:write", "integração", "service-orders", "write", "P2.26"));

    /**
     * Perfis de fábrica (P0.13: os quatro mínimos; P1.37: os 22 por função, na ordem do
     * plano). Código: estável, maiúsculo, até 20 caracteres (limite da coluna
     * {@code user_profiles.profile} da V1_9 — ver TenantUserService). O rótulo é o nome
     * da função no plano; o código abrevia quando o nome cheio passa de 20 caracteres
     * (ex.: {@code RESP_TEC_QUALIDADE}).
     *
     * <p>Perfis desligados ({@code enabled = false}): auditor externo e regulador nascem
     * desligados (plano, P1.37) e o mecanismo de liberação é da ED-75. Até lá, o perfil
     * existe no catálogo e no manifesto, mas não expande para permissão nenhuma
     * ({@link #authoritiesForRole(String)} devolve vazio) e a atribuição pelo
     * administrador do tenant é recusada (TenantUserService). O conjunto declarado do
     * perfil é o que a ED-75 ativará, sem redefinir permissões na hora da liberação.
     */
    public enum FactoryProfile {
        // Perfis mínimos da P0.13. ADMIN: administra o tenant — cadastro, usuários,
        // catálogo GIS e parâmetros. Continua sem revelar documento, buscar por
        // documento e exportar: as três seguem concessão direta (ADR-0008).
        // Integração P1-C: a marca do tenant (P1.19) e os chamados de suporte
        // (P1.23, manage = equivalente a admin do tenant na v0) entram no ADMIN.
        ADMIN("Administrador do tenant", true, HOME_PAGE,
                "registry:read", "registry:write", "user:read", "user:write",
                "gis:catalog-read", "gis:catalog-manage", "parameters:manage",
                "brand:manage", "support:read", "support:write", "support:manage",
                "template:read", "template:write", "template:approve",
                "contract:read-own", "integration:manage"),
        /** Cadastro (leitura e escrita) e leitura de usuários; não gerencia usuários. */
        MANAGER("Gerente", true, HOME_PAGE,
                "registry:read", "registry:write", "user:read"),
        /** Cadastro (leitura e escrita); sem acesso à gestão de usuários. */
        OPERATOR("Operador", true, HOME_PAGE,
                "registry:read", "registry:write"),
        /** Só leitura do cadastro. */
        VIEWER("Observador", true, HOME_PAGE,
                "registry:read"),
        // Perfis por função da P1.37. Permissões só do catálogo desta branch; o domínio
        // de cada função ganha as suas na tarefa que o catalogar (ver javadoc da classe).
        // Integração P1-C: os domínios catalogados nas frentes paralelas da camada C
        // entram aqui — protocolo (P1.36) no atendimento, certificações (P1.32) na
        // qualidade, suporte (P1.23) no atendimento e na gestão. A busca global e
        // suas leituras de OS, conta e documento (P1.33) ficam de fora de propósito:
        // são concessão direta, como registry:export (ver o teste da P1.33).
        ATENDENTE("Atendente", true, HOME_PAGE,
                "registry:read", "registry:write",
                "protocol:read", "protocol:write", "support:read", "support:write"),
        SUPERVISOR_COMERCIAL("Supervisor comercial", true, HOME_PAGE,
                "registry:read", "registry:write", "user:read", "protocol:read"),
        FATURISTA("Faturista", true, HOME_PAGE,
                "registry:read"),
        ANALISTA_ARRECADACAO("Analista de arrecadação", true, HOME_PAGE,
                "registry:read"),
        LEITURISTA("Leiturista", true, HOME_PAGE,
                "registry:read"),
        PROGRAMADOR_OS("Programador de OS", true, HOME_PAGE,
                "registry:read"),
        EXECUTOR_CAMPO("Executor de campo", true, HOME_PAGE,
                "registry:read"),
        /** Sem permissão catalogada nesta branch: as de estoque entram com o domínio. */
        ALMOXARIFE("Almoxarife", true, HOME_PAGE),
        /** Sem permissão catalogada nesta branch: as de estação entram com o domínio. */
        OPERADOR_ESTACAO("Operador de estação", true, HOME_PAGE),
        ANALISTA_PERDAS("Analista de perdas", true, HOME_PAGE,
                "registry:read"),
        /** Integração P1-C: o domínio de qualidade foi catalogado (P1.32) — lê o catálogo. */
        LABORATORISTA("Laboratorista", true, HOME_PAGE,
                "cert:read"),
        /** Integração P1-C: o domínio de qualidade/certificação foi catalogado (P1.32). */
        RESP_TEC_QUALIDADE("Responsável técnico da qualidade", true, HOME_PAGE,
                "cert:read", "cert:write", "cert:approve", "cert:audit"),
        EDITOR_CAD_TECNICO("Editor do cadastro técnico", true, HOME_PAGE,
                "gis:catalog-read", "gis:catalog-manage"),
        REVISOR_CAD_TECNICO("Revisor do cadastro técnico", true, HOME_PAGE,
                "gis:catalog-read"),
        GESTOR("Gestor", true, HOME_PAGE,
                "registry:read", "user:read", "parameters:manage",
                "support:read", "support:write",
                "template:read", "template:write", "template:approve",
                "contract:read-own"),
        AUDITOR_INTERNO("Auditor interno", true, HOME_PAGE,
                "registry:read", "gis:catalog-read", "user:read", "cert:audit", "template:read",
                "contract:read-own"),
        OUVIDOR("Ouvidor", true, HOME_PAGE,
                "registry:read", "protocol:read", "support:read"),
        ENCARREGADO_DADOS("Encarregado de dados", true, HOME_PAGE,
                "registry:read", "user:read"),
        PROCURADOR("Procurador", true, HOME_PAGE,
                "registry:read"),
        CONTADOR("Contador", true, HOME_PAGE,
                "registry:read", "contract:read-own"),
        /** Desligado no nascimento (ED-75): o conjunto é o previsto para a liberação. */
        AUDITOR_EXTERNO("Auditor externo", false, HOME_PAGE,
                "registry:read", "gis:catalog-read", "user:read"),
        /** Desligado no nascimento (ED-75): o conjunto é o previsto para a liberação. */
        REGULADOR("Regulador", false, HOME_PAGE,
                "registry:read", "gis:catalog-read");

        private final String label;
        private final boolean enabled;
        private final String homePage;
        private final Set<String> permissions;

        FactoryProfile(String label, boolean enabled, String homePage, String... permissions) {
            this.label = label;
            this.enabled = enabled;
            this.homePage = homePage;
            this.permissions = Set.copyOf(List.of(permissions));
        }

        /** Rótulo da função, em português, como no plano. */
        public String label() {
            return label;
        }

        /** Falso só para auditor externo e regulador, desligados até a ED-75. */
        public boolean enabled() {
            return enabled;
        }

        /** Página inicial atribuída; construída pela P3.39. */
        public String homePage() {
            return homePage;
        }

        /** Permissões declaradas do perfil (o conjunto previsto, mesmo se desligado). */
        public Set<String> permissions() {
            return permissions;
        }
    }

    private static final Map<String, Set<String>> BY_PROFILE = Stream.of(FactoryProfile.values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, FactoryProfile::permissions));

    /**
     * Permissões do catálogo deliberadamente fora de todo perfil de fábrica de tenant,
     * com o motivo. A lista é fechada e conferida no CI: permissão nova só entra aqui
     * por decisão explícita (código + motivo), nunca por esquecimento de perfil.
     * As três de dado pessoal são concessão direta por desenho (ADR-0008); as cinco de
     * plataforma só existem via papel de plataforma (PlatformRole), nunca via perfil
     * de tenant — um token de tenant nunca as recebe.
     */
    public static Map<String, String> coverageExemptions() {
        Map<String, String> exemptions = new LinkedHashMap<>();
        exemptions.put("registry:reveal-document",
                "Concessão direta (ADR-0008): revelar o documento em claro exige a permissão direta.");
        exemptions.put("registry:search-document",
                "Concessão direta (ADR-0008): buscar por documento completo exige a permissão direta.");
        exemptions.put("registry:export",
                "Concessão direta (P1.12, ADR-0008): exportar uma lista exige a permissão direta.");
        exemptions.put("deployment:read",
                "Só papel de plataforma (P0.43): PLATFORM_OWNER e PLATFORM_OPS, nunca perfil de tenant.");
        exemptions.put("console:read",
                "Só papel de plataforma (P1.6): os 4 papéis de plataforma, nunca perfil de tenant.");
        exemptions.put("console:manage",
                "Só papel de plataforma (P1.6): PLATFORM_OWNER e PLATFORM_OPS, nunca perfil de tenant.");
        exemptions.put("console:suspend",
                "Só papel de plataforma (P1.6): PLATFORM_OWNER e PLATFORM_FINANCE, nunca perfil de tenant.");
        exemptions.put("console:delete",
                "Só papel de plataforma (P1.6): só PLATFORM_OWNER, nunca perfil de tenant.");
        exemptions.put("central:read",
                "Só papel de plataforma (P1.17): os 4 papéis de plataforma, nunca perfil de tenant.");
        exemptions.put("central:manage",
                "Só papel de plataforma (P1.17): PLATFORM_OWNER e PLATFORM_OPS, nunca perfil de tenant.");
        exemptions.put("contract:read",
                "Só papel de plataforma (P2.12): os 4 papéis de plataforma, nunca perfil de tenant.");
        exemptions.put("contract:manage",
                "Só papel de plataforma (P2.12): PLATFORM_OWNER e PLATFORM_FINANCE, nunca perfil de tenant.");
        exemptions.put("contract:restrict",
                "Só papel de plataforma (P2.12): PLATFORM_OWNER e PLATFORM_FINANCE, nunca perfil de tenant.");
        exemptions.put("support:queue",
                "Só papel de plataforma (P1.23): PLATFORM_OWNER, PLATFORM_OPS e PLATFORM_SUPPORT, nunca perfil de tenant.");
        exemptions.put("search:read",
                "Concessão direta (P1.33, precedente registry:export): o portão da busca global exige a permissão direta; nenhum perfil de fábrica a inclui.");
        exemptions.put("service-order:read",
                "Concessão direta (P1.33, precedente registry:export): leitura de OS via busca exige a permissão direta até a superfície de OS catalogá-la em perfis.");
        exemptions.put("billing:read",
                "Concessão direta (P1.33, precedente registry:export): leitura de conta via busca exige a permissão direta até que a superfície de contas a inclua em perfis.");
        exemptions.put("document:read",
                "Concessão direta (P1.33, precedente registry:export): leitura de documento via busca exige a permissão direta até a superfície de documentos catalogá-la em perfis.");
        exemptions.put("asset:read",
                "Concessão direta (P1.35, precedente registry:export): ler o cadastro de ativos exige a permissão direta; nenhum perfil de fábrica a inclui (AssetRoles: perfis por função são da P1.37).");
        exemptions.put("asset:manage",
                "Concessão direta (P1.35, precedente registry:export): incluir e alterar ativos (sempre por OS interna) exige a permissão direta; nenhum perfil de fábrica a inclui (AssetRoles: perfis por função são da P1.37).");
        exemptions.put("properties:read",
                "Escopo OAuth 2.0 de cliente da API pública (P2.26): concedido a clientes, nunca a perfis de usuário; o @PreAuthorize da rota pública verifica o escopo do token.");
        exemptions.put("bills:read",
                "Escopo OAuth 2.0 de cliente da API pública (P2.26): concedido a clientes, nunca a perfis de usuário; o @PreAuthorize da rota pública verifica o escopo do token.");
        exemptions.put("payments:read",
                "Escopo OAuth 2.0 de cliente da API pública (P2.26): concedido a clientes, nunca a perfis de usuário; o @PreAuthorize da rota pública verifica o escopo do token.");
        exemptions.put("service-orders:read",
                "Escopo OAuth 2.0 de cliente da API pública (P2.26): concedido a clientes, nunca a perfis de usuário; o @PreAuthorize da rota pública verifica o escopo do token.");
        exemptions.put("service-orders:write",
                "Escopo OAuth 2.0 de cliente da API pública (P2.26): concedido a clientes, nunca a perfis de usuário; o @PreAuthorize da rota pública verifica o escopo do token.");
        return Map.copyOf(exemptions);
    }

    private PermissionCatalog() {
    }

    /** Nomes de todas as permissões do catálogo. */
    public static Set<String> allNames() {
        return ALL.stream().map(Permission::name).collect(Collectors.toUnmodifiableSet());
    }

    /** Todos os perfis de fábrica, na ordem de declaração (mínimos, depois por função). */
    public static List<FactoryProfile> factoryProfiles() {
        return List.of(FactoryProfile.values());
    }

    /** Códigos dos perfis de fábrica que incluem a permissão (mesmo os desligados). */
    public static List<String> profilesIncluding(String permission) {
        return Stream.of(FactoryProfile.values())
                .filter(p -> p.permissions().contains(permission))
                .map(Enum::name)
                .toList();
    }

    /** Verdadeiro se o nome está no catálogo e no formato {@code recurso:ação}. */
    public static boolean isPermission(String name) {
        return name != null && NAME.matcher(name).matches() && allNames().contains(name);
    }

    /**
     * Permissões concedidas por um papel do token: a expansão do perfil de fábrica, a própria
     * permissão quando o papel já é um nome do catálogo, ou vazio (negação por padrão) quando
     * o papel não é nem perfil nem permissão. Perfil desligado (auditor externo, regulador)
     * não expande para nada até a liberação da ED-75.
     */
    public static Set<String> authoritiesForRole(String role) {
        if (role == null) {
            return Set.of();
        }
        Set<String> profile = BY_PROFILE.get(role);
        if (profile != null) {
            try {
                if (!FactoryProfile.valueOf(role).enabled()) {
                    return Set.of();
                }
            } catch (IllegalArgumentException e) {
                return Set.of();
            }
            return profile;
        }
        return isPermission(role) ? Set.of(role) : Set.of();
    }
}
