package br.com.ziipsan.platform.brand;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.brand.TenantBrandStore.AssetView;
import br.com.ziipsan.platform.brand.TenantBrandStore.CurrentBrand;
import br.com.ziipsan.platform.brand.TenantBrandStore.LogoBytes;
import br.com.ziipsan.platform.brand.TenantBrandStore.SlugUpdate;
import br.com.ziipsan.platform.brand.TenantBrandStore.TenantBrand;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import br.com.ziipsan.platform.infra.scan.ScannerUnavailableException;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Marca do tenant (P1.19, ADR-0016, MARCA-E-TEMAS §3).
 *
 * <p>Sem anotações Spring de propósito, como o {@code DocumentService} da
 * P1.20: o controller constrói o serviço explicitamente com o store e o
 * varredor do ambiente. O pipeline do logo REUSA a P1.20
 * ({@link FileScanner} — ClamAV ou o serviço da hospedagem), não o
 * reconstrói: o logo reencodado é varrido; CLEAN vincula a chave
 * {@code branding.logo} (ou {@code logo_dark}); INFECTED recusa o upload
 * e apaga o ativo; varredor indisponível deixa o ativo NOT_SCANNED e a
 * marca anterior segue valendo — nunca CLEAN sem varredura (a regra dura
 * da P1.20).
 *
 * <p>Semântica do PUT de marca: atualização parcial — campo ausente (null)
 * não muda; campo presente é validado e gravado. O slug é a exceção com
 * limpeza explícita: string vazia limpa o slug, null mantém. Os logos não
 * passam pelo PUT: têm endpoints próprios (upload/remoção).
 *
 * <p>Limites de texto do §3.1: {@code display_name} até 60,
 * {@code short_name} até 20, {@code document_footer} até 300. O contato
 * (telefone, e-mail, site, endereço de atendimento) aceita só essas
 * quatro chaves, cada uma com até 300 caracteres; e-mail, quando presente,
 * precisa ter o formato mínimo {@code algo@algo.algo}.
 */
public final class TenantBrandService {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Set<String> RESERVED_SLUGS = Set.of("app", "www");
    private static final Set<String> CONTACT_FIELDS = Set.of("phone", "email", "site", "address");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final TenantBrandStore store;
    private final FileScanner scanner;

    public TenantBrandService(TenantBrandStore store, FileScanner scanner) {
        if (store == null || scanner == null) {
            throw new IllegalArgumentException("store e scanner são obrigatórios");
        }
        this.store = store;
        this.scanner = scanner;
    }

    /** Entrada do PUT de marca (tudo opcional; null = não muda, exceto o slug vazio = limpa). */
    public record SaveBrandInput(String displayName, String shortName, String color, JsonNode contact,
                                 String documentFooter, String subdomainSlug) {
    }

    /** Marca como o admin a vê, com o ajuste de contraste aplicado. */
    public record AdminBrandView(String slug, String displayName, String shortName, String color,
                                 String textOnColor, JsonNode contact, String documentFooter,
                                 AssetView logo, AssetView logoDark, List<String> warnings) {
    }

    /** Resultado do PUT: a marca salva e os avisos (ex.: cor escurecida). */
    public record SaveBrandResult(AdminBrandView brand, boolean colorAdjusted,
                                  String requestedColor, String savedColor) {
    }

    /** Resultado do upload de logo (status HTTP decidido pelo controller: 200 CLEAN, 202 NOT_SCANNED). */
    public record UploadResult(String slug, AssetView asset, String scanStatus) {
    }

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    /** Marca pré-login do slug (função SECURITY DEFINER da V9_2). */
    public Optional<TenantBrand> resolveBySlug(String slug) {
        return store.resolveBySlug(slug);
    }

    /** Bytes do logo CLEAN do tenant do slug. */
    public Optional<LogoBytes> logoBytes(String slug, UUID assetId) {
        return store.logoBytes(slug, assetId);
    }

    /** Marca corrente do tenant para o admin. */
    public AdminBrandView currentBrand(UUID tenantId) {
        return viewOf(store.currentBrand(tenantId), List.of());
    }

    // ------------------------------------------------------------------
    // Escrita: marca
    // ------------------------------------------------------------------

    public SaveBrandResult saveBrand(UUID tenantId, UUID actorId, SaveBrandInput input) {
        requireTenant(tenantId);
        if (input == null) {
            throw validation("brand", "empty", "Os dados da marca são obrigatórios.");
        }
        Map<String, String> upserts = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        boolean colorAdjusted = false;
        String requestedColor = null;
        String savedColor = null;
        if (input.displayName() != null) {
            String value = input.displayName().strip();
            if (value.isEmpty() || value.length() > 60) {
                throw validation("displayName", "invalid-length",
                        "O nome de exibição precisa ter de 1 a 60 caracteres.");
            }
            upserts.put("branding.display_name", Json.write(value));
        }
        if (input.shortName() != null) {
            String value = input.shortName().strip();
            if (value.isEmpty() || value.length() > 20) {
                throw validation("shortName", "invalid-length",
                        "O nome curto precisa ter de 1 a 20 caracteres.");
            }
            upserts.put("branding.short_name", Json.write(value));
        }
        if (input.color() != null) {
            String requested = input.color().strip();
            if (!BrandContrast.isHexColor(requested)) {
                throw validation("color", "invalid-color", "A cor precisa estar no formato #RRGGBB.");
            }
            BrandContrast.AdjustedColor adjusted = BrandContrast.adjustForWhiteBackground(requested);
            requestedColor = adjusted.requested();
            savedColor = adjusted.saved();
            colorAdjusted = adjusted.adjusted();
            if (colorAdjusted) {
                warnings.add("A cor foi escurecida de " + requestedColor + " para " + savedColor
                        + " para garantir contraste 4,5:1 sobre fundo branco.");
            }
            upserts.put("branding.color", Json.write(savedColor));
        }
        if (input.contact() != null) {
            upserts.put("branding.contact", Json.write(contactFields(input.contact())));
        }
        if (input.documentFooter() != null) {
            String value = input.documentFooter().strip();
            if (value.length() > 300) {
                throw validation("documentFooter", "invalid-length",
                        "O rodapé de documento precisa ter até 300 caracteres.");
            }
            upserts.put("branding.document_footer", Json.write(value));
        }
        SlugUpdate slugUpdate = SlugUpdate.keep();
        if (input.subdomainSlug() != null) {
            String raw = input.subdomainSlug().strip();
            if (raw.isEmpty()) {
                slugUpdate = SlugUpdate.clear();
            } else {
                String slug = raw.toLowerCase();
                if (!SLUG.matcher(slug).matches()) {
                    throw validation("subdomainSlug", "invalid-slug",
                            "O subdomínio precisa ter de 1 a 63 caracteres: minúsculas, números e hífen, sem hífen nas pontas.");
                }
                if (RESERVED_SLUGS.contains(slug)) {
                    throw validation("subdomainSlug", "reserved-slug",
                            "O subdomínio '" + slug + "' é reservado.");
                }
                slugUpdate = SlugUpdate.set(slug);
            }
        }
        if (upserts.isEmpty() && !slugUpdate.change()) {
            throw validation("brand", "empty", "Nenhum campo da marca foi informado.");
        }
        store.saveSettings(tenantId, actorId, upserts, List.of(), slugUpdate);
        AdminBrandView saved = viewOf(store.currentBrand(tenantId), warnings);
        return new SaveBrandResult(saved, colorAdjusted, requestedColor, savedColor);
    }

    // ------------------------------------------------------------------
    // Escrita: logo
    // ------------------------------------------------------------------

    /**
     * Upload de logo: reencode (P1.19) + varredura (P1.20). CLEAN vincula e
     * devolve o ativo; INFECTED apaga o ativo e recusa (422); varredor
     * indisponível deixa NOT_SCANNED e devolve o ativo em quarentena — o
     * controller responde 202 e a marca anterior segue valendo.
     */
    public UploadResult uploadLogo(UUID tenantId, UUID actorId, String kind, byte[] content) {
        requireTenant(tenantId);
        String normalizedKind = validateKind(kind);
        LogoImage.ReencodedLogo reencoded = LogoImage.reencode(content);
        String slug = store.currentBrand(tenantId).slug();
        AssetView asset = store.insertQuarantinedAsset(tenantId, normalizedKind, "image/png",
                reencoded.width(), reencoded.height(), reencoded.pngBytes().length,
                reencoded.sha256(), reencoded.pngBytes());
        FileScanner.ScanResult result;
        try {
            result = scanner.scan(new ByteArrayInputStream(reencoded.pngBytes()));
        } catch (ScannerUnavailableException | IOException e) {
            store.markAssetNotScanned(tenantId, asset.id());
            return new UploadResult(slug, withStatus(asset, "NOT_SCANNED"), "NOT_SCANNED");
        }
        if (result.verdict() == FileScanner.ScanVerdict.INFECTED) {
            String threat = result.threat().orElse("ameaça sem nome");
            store.discardInfectedAsset(tenantId, actorId, asset, threat);
            throw validation("logo", "scan-infected",
                    "O logo foi recusado pela varredura de segurança.");
        }
        Instant scannedAt = Instant.now();
        store.activateCleanAsset(tenantId, actorId, asset, result.scanner(), scannedAt);
        return new UploadResult(slug, withScan(asset, "CLEAN", scannedAt, result.scanner()), "CLEAN");
    }

    /** Remoção do logo (ativo + chave). Devolve {@code false} quando não havia logo. */
    public boolean deleteLogo(UUID tenantId, UUID actorId, String kind) {
        requireTenant(tenantId);
        return store.deleteLogo(tenantId, actorId, validateKind(kind));
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private AdminBrandView viewOf(CurrentBrand current, List<String> warnings) {
        Map<String, JsonNode> settings = current.settings();
        String color = textSetting(settings, "branding.color");
        return new AdminBrandView(
                current.slug(),
                textSetting(settings, "branding.display_name"),
                textSetting(settings, "branding.short_name"),
                color,
                color != null && BrandContrast.isHexColor(color) ? BrandContrast.textOnColor(color) : null,
                objectSetting(settings, "branding.contact"),
                textSetting(settings, "branding.document_footer"),
                current.logo(),
                current.logoDark(),
                List.copyOf(warnings));
    }

    private static String textSetting(Map<String, JsonNode> settings, String key) {
        JsonNode node = settings.get(key);
        return node != null && node.isTextual() ? node.asString() : null;
    }

    private static JsonNode objectSetting(Map<String, JsonNode> settings, String key) {
        JsonNode node = settings.get(key);
        return node != null && node.isObject() ? node : null;
    }

    private static Map<String, String> contactFields(JsonNode contact) {
        if (!contact.isObject()) {
            throw validation("contact", "invalid-contact", "O contato precisa ser um objeto.");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        var names = contact.propertyNames();
        while (names.hasNext()) {
            String field = names.next();
            if (!CONTACT_FIELDS.contains(field)) {
                throw validation("contact", "invalid-contact",
                        "O contato aceita só phone, email, site e address.");
            }
            JsonNode value = contact.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (!value.isTextual()) {
                throw validation("contact", "invalid-contact",
                        "O campo '" + field + "' do contato precisa ser texto.");
            }
            String text = value.asString().strip();
            if (text.length() > 300) {
                throw validation("contact", "invalid-length",
                        "O campo '" + field + "' do contato precisa ter até 300 caracteres.");
            }
            if ("email".equals(field) && !EMAIL.matcher(text).matches()) {
                throw validation("contact", "invalid-contact", "O e-mail do contato é inválido.");
            }
            if (!text.isEmpty()) {
                fields.put(field, text);
            }
        }
        return fields;
    }

    private static String validateKind(String kind) {
        if (!"LOGO".equals(kind) && !"LOGO_DARK".equals(kind)) {
            throw validation("kind", "invalid-kind", "O logo precisa ser LOGO ou LOGO_DARK.");
        }
        return kind;
    }

    private static AssetView withStatus(AssetView asset, String scanStatus) {
        return withScan(asset, scanStatus, asset.scannedAt(), asset.scanner());
    }

    private static AssetView withScan(AssetView asset, String scanStatus, Instant scannedAt, String scanner) {
        return new AssetView(asset.id(), asset.kind(), asset.contentType(), asset.width(), asset.height(),
                asset.sha256(), scanStatus, scannedAt, scanner, asset.threat());
    }

    private static void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta operação exige um token de tenant.");
        }
    }

    private static ApiException.Validation validation(String field, String code, String message) {
        return ApiException.Validation.of(new ApiException.FieldError(field, code, message));
    }
}
