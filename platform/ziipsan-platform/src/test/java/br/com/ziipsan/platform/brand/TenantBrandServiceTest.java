package br.com.ziipsan.platform.brand;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.brand.TenantBrandService.AdminBrandView;
import br.com.ziipsan.platform.brand.TenantBrandService.SaveBrandInput;
import br.com.ziipsan.platform.brand.TenantBrandService.UploadResult;
import br.com.ziipsan.platform.brand.TenantBrandStore.AssetView;
import br.com.ziipsan.platform.brand.TenantBrandStore.CurrentBrand;
import br.com.ziipsan.platform.brand.TenantBrandStore.LogoBytes;
import br.com.ziipsan.platform.brand.TenantBrandStore.SlugUpdate;
import br.com.ziipsan.platform.brand.TenantBrandStore.TenantBrand;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import br.com.ziipsan.platform.infra.scan.ScannerUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.19 — serviço da marca do tenant com um store falso em memória e
 * varredores falsos (sem PostgreSQL, sem daemon ClamAV).
 *
 * <p>O falso reproduz a semântica de vínculo da V9_2/JDBC: o logo efetivo
 * é o apontado pela chave {@code branding.logo} (ou {@code logo_dark});
 * um upload em quarentena coexiste com ele e só o substitui quando um
 * CLEAN é ativado. É esse o contrato que a correção da retomada (fim da
 * unicidade por (tenant, kind)) preserva: a marca anterior segue valendo
 * durante a quarentena e quando a varredura recusa o novo logo.
 */
class TenantBrandServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    // ------------------------------------------------------------------
    // Falso
    // ------------------------------------------------------------------

    private static final class FakeStore implements TenantBrandStore {

        String slug;
        final Map<String, JsonNode> settings = new LinkedHashMap<>();
        final List<AssetView> assets = new ArrayList<>();

        @Override
        public Optional<TenantBrand> resolveBySlug(String slug) {
            return Optional.empty();
        }

        @Override
        public Optional<LogoBytes> logoBytes(String slug, UUID assetId) {
            return Optional.empty();
        }

        @Override
        public CurrentBrand currentBrand(UUID tenantId) {
            return new CurrentBrand(slug, new LinkedHashMap<>(settings),
                    assetFor("LOGO"), assetFor("LOGO_DARK"));
        }

        /** Efetivo (vínculo) quando existe; sem vínculo, o mais recente. */
        private AssetView assetFor(String kind) {
            String key = "LOGO_DARK".equals(kind) ? "branding.logo_dark" : "branding.logo";
            JsonNode node = settings.get(key);
            if (node != null && node.isTextual()) {
                try {
                    UUID linked = UUID.fromString(node.asString());
                    for (AssetView asset : assets) {
                        if (asset.kind().equals(kind) && asset.id().equals(linked)) {
                            return asset;
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Chave com formato inválido: ignora o vínculo, como a função SQL.
                }
            }
            AssetView newest = null;
            for (AssetView asset : assets) {
                if (asset.kind().equals(kind)) {
                    newest = asset;
                }
            }
            return newest;
        }

        @Override
        public void saveSettings(UUID tenantId, UUID actorId, Map<String, String> upserts,
                                 List<String> deleteKeys, SlugUpdate slugUpdate) {
            for (Map.Entry<String, String> entry : upserts.entrySet()) {
                settings.put(entry.getKey(), Json.readTree(entry.getValue()));
            }
            for (String key : deleteKeys) {
                settings.remove(key);
            }
            if (slugUpdate.change()) {
                slug = slugUpdate.slug();
            }
        }

        @Override
        public AssetView insertQuarantinedAsset(UUID tenantId, String kind, String contentType, int width,
                                                int height, long sizeBytes, String sha256, byte[] content) {
            AssetView asset = new AssetView(UUID.randomUUID(), kind, contentType, width, height, sha256,
                    "QUARANTINED", null, null, null);
            assets.add(asset);
            return asset;
        }

        @Override
        public void activateCleanAsset(UUID tenantId, UUID actorId, AssetView asset, String scanner,
                                       Instant scannedAt) {
            replace(asset, new AssetView(asset.id(), asset.kind(), asset.contentType(), asset.width(),
                    asset.height(), asset.sha256(), "CLEAN", scannedAt, scanner, null));
            assets.removeIf(other -> other.kind().equals(asset.kind()) && !other.id().equals(asset.id()));
            String key = "LOGO_DARK".equals(asset.kind()) ? "branding.logo_dark" : "branding.logo";
            settings.put(key, Json.readTree("\"" + asset.id() + "\""));
        }

        @Override
        public void discardInfectedAsset(UUID tenantId, UUID actorId, AssetView asset, String threat) {
            assets.removeIf(other -> other.id().equals(asset.id()));
        }

        @Override
        public void markAssetNotScanned(UUID tenantId, UUID assetId) {
            for (AssetView asset : assets) {
                if (asset.id().equals(assetId)) {
                    replace(asset, new AssetView(asset.id(), asset.kind(), asset.contentType(), asset.width(),
                            asset.height(), asset.sha256(), "NOT_SCANNED", null, null, null));
                }
            }
        }

        @Override
        public boolean deleteLogo(UUID tenantId, UUID actorId, String kind) {
            String key = "LOGO_DARK".equals(kind) ? "branding.logo_dark" : "branding.logo";
            boolean hadKey = settings.remove(key) != null;
            boolean hadAsset = assets.removeIf(asset -> asset.kind().equals(kind));
            return hadKey || hadAsset;
        }

        private void replace(AssetView old, AssetView replacement) {
            for (int i = 0; i < assets.size(); i++) {
                if (assets.get(i).id().equals(old.id())) {
                    assets.set(i, replacement);
                }
            }
        }
    }

    private static FileScanner cleanScanner() {
        return new FileScanner() {
            @Override
            public ScanResult scan(InputStream content) {
                return ScanResult.clean("clamav", Optional.empty());
            }

            @Override
            public Optional<Instant> signatureUpdatedAt() {
                return Optional.empty();
            }
        };
    }

    private static FileScanner infectedScanner() {
        return new FileScanner() {
            @Override
            public ScanResult scan(InputStream content) {
                return ScanResult.infected("EICAR-Test-Signature", "clamav", Optional.empty());
            }

            @Override
            public Optional<Instant> signatureUpdatedAt() {
                return Optional.empty();
            }
        };
    }

    private static FileScanner unavailableScanner() {
        return new FileScanner() {
            @Override
            public ScanResult scan(InputStream content) throws ScannerUnavailableException {
                throw new ScannerUnavailableException("daemon fora do ar");
            }

            @Override
            public Optional<Instant> signatureUpdatedAt() {
                return Optional.empty();
            }
        };
    }

    private static byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x03, 0x69, 0xA0));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String fieldErrorCode(ApiException.Validation validation) {
        assertThat(validation.errors()).hasSize(1);
        return validation.errors().get(0).code();
    }

    // ------------------------------------------------------------------
    // PUT de marca
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cor clara é escurecida até 4,5:1 sobre branco, com aviso; cor escura passa intacta")
    void contrastRuleAppliedOnSave() {
        FakeStore store = new FakeStore();
        TenantBrandService service = new TenantBrandService(store, cleanScanner());

        var light = service.saveBrand(TENANT, ACTOR, new SaveBrandInput(
                null, null, "#E8E8E8", null, null, null));
        assertThat(light.colorAdjusted()).isTrue();
        assertThat(light.requestedColor()).isEqualTo("#E8E8E8");
        assertThat(light.savedColor()).isNotEqualTo("#E8E8E8");
        assertThat(BrandContrast.ratio(light.savedColor(), "#FFFFFF")).isGreaterThanOrEqualTo(4.5);
        assertThat(light.brand().warnings()).hasSize(1);
        assertThat(light.brand().color()).isEqualTo(light.savedColor());

        var dark = service.saveBrand(TENANT, ACTOR, new SaveBrandInput(
                null, null, "#0369A0", null, null, null));
        assertThat(dark.colorAdjusted()).isFalse();
        assertThat(dark.savedColor()).isEqualTo("#0369A0");
        assertThat(dark.brand().warnings()).isEmpty();
        assertThat(dark.brand().textOnColor()).isEqualTo(BrandContrast.textOnColor("#0369A0"));
    }

    @Test
    @DisplayName("PUT parcial: campo ausente não muda; slug vazio limpa; slug reservado ou inválido é recusado")
    void partialSaveAndSlugRules() {
        FakeStore store = new FakeStore();
        TenantBrandService service = new TenantBrandService(store, cleanScanner());

        service.saveBrand(TENANT, ACTOR, new SaveBrandInput(
                "Prefeitura Teste", null, null, null, null, "acme"));
        AdminBrandView kept = service.saveBrand(TENANT, ACTOR, new SaveBrandInput(
                null, "ACME", null, null, null, null)).brand();
        assertThat(kept.displayName()).isEqualTo("Prefeitura Teste");
        assertThat(kept.shortName()).isEqualTo("ACME");
        assertThat(kept.slug()).isEqualTo("acme");

        AdminBrandView cleared = service.saveBrand(TENANT, ACTOR, new SaveBrandInput(
                null, null, null, null, null, "  ")).brand();
        assertThat(cleared.slug()).isNull();

        assertThatThrownBy(() -> service.saveBrand(TENANT, ACTOR, new SaveBrandInput(
                null, null, null, null, null, "app")))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e)).isEqualTo("reserved-slug"));
        assertThatThrownBy(() -> service.saveBrand(TENANT, ACTOR, new SaveBrandInput(
                null, null, null, null, null, "-invalido-")))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e)).isEqualTo("invalid-slug"));
        assertThatThrownBy(() -> service.saveBrand(TENANT, ACTOR, new SaveBrandInput(
                null, null, null, null, null, null)))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e)).isEqualTo("empty"));
    }

    @Test
    @DisplayName("contato aceita só as quatro chaves, com e-mail validado; cor fora do formato é recusada")
    void contactAndColorValidation() {
        FakeStore store = new FakeStore();
        TenantBrandService service = new TenantBrandService(store, cleanScanner());

        AdminBrandView saved = service.saveBrand(TENANT, ACTOR, new SaveBrandInput(
                null, null, null, Json.readTree("{\"phone\": \"(11) 99999-0000\", \"email\": \"a@b.com\"}"),
                null, null)).brand();
        assertThat(saved.contact().get("phone").asString()).isEqualTo("(11) 99999-0000");

        assertThatThrownBy(() -> service.saveBrand(TENANT, ACTOR, new SaveBrandInput(
                null, null, null, Json.readTree("{\"cnpj\": \"00\"}"), null, null)))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e)).isEqualTo("invalid-contact"));
        assertThatThrownBy(() -> service.saveBrand(TENANT, ACTOR, new SaveBrandInput(
                null, null, null, Json.readTree("{\"email\": \"sem-arroba\"}"), null, null)))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e)).isEqualTo("invalid-contact"));
        assertThatThrownBy(() -> service.saveBrand(TENANT, ACTOR, new SaveBrandInput(
                null, null, "azul", null, null, null)))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e)).isEqualTo("invalid-color"));
    }

    // ------------------------------------------------------------------
    // Upload de logo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("upload CLEAN vincula o logo; segundo upload CLEAN substitui o primeiro (re-upload funciona)")
    void cleanUploadLinksAndReplaces() throws Exception {
        FakeStore store = new FakeStore();
        TenantBrandService service = new TenantBrandService(store, cleanScanner());
        byte[] first = pngBytes(200, 200);
        byte[] second = pngBytes(300, 200);

        UploadResult firstResult = service.uploadLogo(TENANT, ACTOR, "LOGO", first);
        assertThat(firstResult.scanStatus()).isEqualTo("CLEAN");
        assertThat(firstResult.asset().scanStatus()).isEqualTo("CLEAN");
        assertThat(firstResult.asset().scannedAt()).isNotNull();
        assertThat(firstResult.asset().scanner()).isEqualTo("clamav");

        // O re-upload é o caso que a unicidade por (tenant, kind) quebrava:
        // com o logo vigente ainda vinculado, o segundo upload tem de passar.
        UploadResult secondResult = service.uploadLogo(TENANT, ACTOR, "LOGO", second);
        assertThat(secondResult.scanStatus()).isEqualTo("CLEAN");
        assertThat(service.currentBrand(TENANT).logo().id()).isEqualTo(secondResult.asset().id());
        assertThat(store.assets).hasSize(1);
    }

    @Test
    @DisplayName("upload INFECTED é recusado e a marca anterior segue valendo; upload sem varredor fica NOT_SCANNED, nunca CLEAN")
    void infectedRefusedAndUnavailableQuarantines() throws Exception {
        FakeStore store = new FakeStore();
        TenantBrandService cleanService = new TenantBrandService(store, cleanScanner());
        UploadResult vigente = cleanService.uploadLogo(TENANT, ACTOR, "LOGO", pngBytes(200, 200));

        TenantBrandService infectedService = new TenantBrandService(store, infectedScanner());
        assertThatThrownBy(() -> infectedService.uploadLogo(TENANT, ACTOR, "LOGO", pngBytes(200, 200)))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e)).isEqualTo("scan-infected"));
        assertThat(serviceCurrentLogoId(store)).isEqualTo(vigente.asset().id());

        TenantBrandService unavailableService = new TenantBrandService(store, unavailableScanner());
        UploadResult quarantined = unavailableService.uploadLogo(TENANT, ACTOR, "LOGO", pngBytes(200, 200));
        assertThat(quarantined.scanStatus()).isEqualTo("NOT_SCANNED");
        assertThat(quarantined.asset().scanStatus()).isEqualTo("NOT_SCANNED");
        // A marca anterior segue valendo: o vínculo não mudou para o pendente.
        assertThat(serviceCurrentLogoId(store)).isEqualTo(vigente.asset().id());
    }

    private static UUID serviceCurrentLogoId(FakeStore store) {
        AssetView logo = new TenantBrandService(store, cleanScanner()).currentBrand(TENANT).logo();
        return logo == null ? null : logo.id();
    }

    @Test
    @DisplayName("remoção apaga vínculo e ativo; segunda remoção devolve falso; kind inválido é recusado")
    void deleteLogoRules() throws Exception {
        FakeStore store = new FakeStore();
        TenantBrandService service = new TenantBrandService(store, cleanScanner());

        assertThat(service.deleteLogo(TENANT, ACTOR, "LOGO")).isFalse();
        service.uploadLogo(TENANT, ACTOR, "LOGO_DARK", pngBytes(200, 200));
        assertThat(service.deleteLogo(TENANT, ACTOR, "LOGO_DARK")).isTrue();
        assertThat(service.currentBrand(TENANT).logoDark()).isNull();
        assertThat(service.deleteLogo(TENANT, ACTOR, "LOGO_DARK")).isFalse();

        assertThatThrownBy(() -> service.uploadLogo(TENANT, ACTOR, "BANNER", pngBytes(200, 200)))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e)).isEqualTo("invalid-kind"));
        assertThatThrownBy(() -> service.uploadLogo(TENANT, ACTOR, "LOGO", "não-imagem".getBytes()))
                .isInstanceOf(ApiException.Validation.class);
    }
}
