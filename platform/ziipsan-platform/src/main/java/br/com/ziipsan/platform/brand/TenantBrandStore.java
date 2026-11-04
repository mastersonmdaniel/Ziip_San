package br.com.ziipsan.platform.brand;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência da marca do tenant (P1.19). Mesma separação do pipeline de
 * documentos da P1.20 ({@code DocumentRepository} / {@code JdbcDocumentRepository}):
 * o serviço orquestra validação, contraste, reencode e varredura; o store
 * só fala com o banco. Assim o serviço é testável sem PostgreSQL, com um
 * falso em memória.
 *
 * <p>Leituras pré-login ({@link #resolveBySlug} e {@link #logoBytes}) não
 * usam contexto de tenant: elas chamam as funções SECURITY DEFINER da
 * V9_2, que resolvem o tenant pelo slug e fixam a GUC localmente. As
 * demais operações rodam numa transação própria com
 * {@code app.current_tenant} local (precedente do
 * {@code TenantParameterService}), porque {@code tenant_settings} e
 * {@code tenant_assets} têm RLS forçada.
 */
public interface TenantBrandStore {

    /** Logo como a marca pré-login o expõe (sem estado de varredura). */
    record PublicLogoRef(UUID id, String contentType, int width, int height, String sha256) {
    }

    /** Marca pré-login de um tenant, como a função da V9_2 a devolve. */
    record TenantBrand(UUID tenantId, String slug, String displayName, String shortName, String color,
                       JsonNode contact, PublicLogoRef logo, PublicLogoRef logoDark) {
    }

    /** Bytes de um logo CLEAN, como a função da V9_2 os devolve. */
    record LogoBytes(String contentType, int width, int height, String sha256, byte[] content) {
    }

    /** Ativo de marca como o admin o vê (com estado da varredura). */
    record AssetView(UUID id, String kind, String contentType, int width, int height, String sha256,
                     String scanStatus, Instant scannedAt, String scanner, String threat) {
    }

    /**
     * Estado corrente da marca para o admin (chaves brutas + ativos). O
     * ativo de cada kind é o apontado pela chave {@code branding.*} (o
     * logo efetivo) quando existe; sem vínculo, o mais recente — de modo
     * que um upload em quarentena nunca se apresenta como marca efetiva.
     */
    record CurrentBrand(String slug, Map<String, JsonNode> settings, AssetView logo, AssetView logoDark) {
    }

    /** Atualização de slug: {@code change=false} mantém; {@code slug=null} com change limpa. */
    record SlugUpdate(boolean change, String slug) {
        public static SlugUpdate keep() {
            return new SlugUpdate(false, null);
        }

        public static SlugUpdate set(String slug) {
            return new SlugUpdate(true, slug);
        }

        public static SlugUpdate clear() {
            return new SlugUpdate(true, null);
        }
    }

    /** Marca pré-login do slug, ou vazio quando o slug é desconhecido/inativo. */
    Optional<TenantBrand> resolveBySlug(String slug);

    /** Bytes do logo CLEAN do tenant do slug, ou vazio em qualquer outro caso. */
    Optional<LogoBytes> logoBytes(String slug, UUID assetId);

    /** Estado corrente da marca do tenant (leitura com contexto do tenant). */
    CurrentBrand currentBrand(UUID tenantId);

    /**
     * Grava chaves {@code branding.*} (valor = texto JSON pronto, ex.:
     * {@code "\"Nome\""} para string, {@code "{...}"} para o contato),
     * apaga as chaves listadas, aplica a atualização de slug e audita a
     * troca (só os nomes das chaves, nunca os valores — o contato do
     * tenant não vai para a trilha).
     */
    void saveSettings(UUID tenantId, UUID actorId, Map<String, String> upserts,
                      List<String> deleteKeys, SlugUpdate slugUpdate);

    /**
     * Insere o ativo em quarentena (ainda sem varredura). Pode coexistir
     * com o logo efetivo do kind e com outros pendentes (a V9_2 não impõe
     * unicidade por (tenant, kind)): o novo upload não desvincula a marca
     * anterior; só a ativação de um CLEAN a substitui.
     */
    AssetView insertQuarantinedAsset(UUID tenantId, String kind, String contentType, int width, int height,
                                     long sizeBytes, String sha256, byte[] content);

    /**
     * Ativa o ativo limpo: marca CLEAN com os dados da varredura, apaga
     * outros ativos do mesmo kind, aponta a chave {@code branding.logo}
     * (ou {@code branding.logo_dark}) para ele e audita.
     */
    void activateCleanAsset(UUID tenantId, UUID actorId, AssetView asset, String scanner, Instant scannedAt);

    /** Descarta o ativo recusado pela varredura e audita o bloqueio. */
    void discardInfectedAsset(UUID tenantId, UUID actorId, AssetView asset, String threat);

    /** Marca o ativo como NOT_SCANNED (varredura tentada, varredor indisponível). */
    void markAssetNotScanned(UUID tenantId, UUID assetId);

    /**
     * Remove o logo do kind (todos os ativos do kind + chave
     * {@code branding.*}) e audita. Devolve {@code true} quando havia algo
     * para remover.
     */
    boolean deleteLogo(UUID tenantId, UUID actorId, String kind);
}
