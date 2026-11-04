package br.com.ziipsan.platform.tenant;

/**
 * P2.2: papeis dos pools de conexao.
 *
 * <p>Cada papel tem credencial propria no banco (role de login distinta) e
 * limite proprio de conexoes. No PgBouncer em modo transaction o pool e por
 * par (usuario, banco): papeis distintos sao pools distintos no pooler, e
 * nao apenas no Hikari — e por isso um lote que satura o pool de lote nao
 * esgota o pool da API, mesmo atraves do PgBouncer.
 *
 * <p>Limites padrao (ARQUITETURA-ALVO §4): API 20 (o pool existente do
 * {@code DataSourceConfig}), lote 10 (o valor que o §4 previa para o worker)
 * e ingestao 5 (o valor que o §4 previa para ingestion). A soma por processo
 * (20 + 10 + 5 = 35, mais o migrador 2) continua cabendo com folga no
 * {@code max_connections=200} do postgres-primary, contando Keycloak e
 * operacao — e cada pool do PgBouncer respeita o seu
 * {@code default_pool_size} por par (usuario, banco).
 *
 * <p>As roles sao as criadas por {@code infra/postgres/init/sql/security.sql}:
 * lote e ingestao nascem NOLOGIN e herdam os privilegios de
 * {@code ziipsan_app} por membership — nunca de {@code ziipsan_worker},
 * cuja policy {@code *_worker_all} veria todos os tenants.
 */
public enum PoolRole {

    /** Requisicoes da API (backoffice e portal): a role existente. */
    API("ziipsan_app", 20),

    /** Lote (Spring Batch da P2.1): execucoes de job e rotas. */
    BATCH("ziipsan_batch", 10),

    /** Ingestao (webhooks, sincronizacao, cargas em lote). */
    INGEST("ziipsan_ingest", 5);

    private final String defaultUsername;
    private final int defaultMaxPoolSize;

    PoolRole(String defaultUsername, int defaultMaxPoolSize) {
        this.defaultUsername = defaultUsername;
        this.defaultMaxPoolSize = defaultMaxPoolSize;
    }

    /** Role de login padrao do papel (o deploy pode trocar o usuario via configuracao). */
    public String defaultUsername() {
        return defaultUsername;
    }

    /** Teto padrao de conexoes Hikari do papel (o deploy pode trocar via configuracao). */
    public int defaultMaxPoolSize() {
        return defaultMaxPoolSize;
    }

    /** Nome do pool Hikari: distinto por papel, visivel nas metricas {@code hikaricp.*}. */
    public String poolName() {
        return "ziipsan-" + name().toLowerCase();
    }
}
