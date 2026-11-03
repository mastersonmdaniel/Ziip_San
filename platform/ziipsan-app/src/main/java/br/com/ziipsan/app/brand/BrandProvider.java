package br.com.ziipsan.app.brand;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * P0.34: marca do produto lida do pacote branding/ empacotado como recurso.
 *
 * <p>A copia em src/main/resources/branding/ e gerada de branding/ por
 * scripts/brand/sync-brand-resources.sh; o check de sincronia quebra o CI se
 * divergir. {@code ZIIPSAN_BRAND} (ou a propriedade {@code ziipsan.brand})
 * escolhe a pasta; pasta inexistente (recurso ausente) impede o bean de subir,
 * que e a recusa de subir do MARCA-E-TEMAS §2.4. Identificadores tecnicos
 * seguem {@code ziipsan} qualquer que seja a marca: so o que o usuario ve
 * vem daqui. Nenhum dado legal sensivel (CNPJ etc.) e exposto: o endpoint
 * publico devolve so nome, descricao, cores e textos de exibicao.
 */
public class BrandProvider {

    public record BrandData(String productName, String shortName, String description, String brandPrimary,
                            ThemeColor themeColor, Attribution attribution) {
    }

    public record ThemeColor(String light, String dark) {
    }

    public record Attribution(boolean enabled, String text) {
    }

    public record BrandTexts(String description, String footer, String emailSignature, String aboutText) {
    }

    private final BrandData brand;
    private final BrandTexts texts;

    public BrandProvider(String brandDir) {
        this.brand = load("branding/" + brandDir + "/brand.json", BrandData.class);
        this.texts = load("branding/" + brandDir + "/texts/pt-BR.json", BrandTexts.class);
    }

    /** Marca efetiva: variavel de ambiente vence a propriedade, que vence o padrao. */
    public static BrandProvider fromEnvironment() {
        String dir = System.getenv("ZIIPSAN_BRAND");
        if (dir == null || dir.isBlank()) {
            dir = System.getProperty("ziipsan.brand", "ziipsan");
        }
        return new BrandProvider(dir);
    }

    public BrandData brand() {
        return brand;
    }

    public BrandTexts texts() {
        return texts;
    }

    private static <T> T load(String resource, Class<T> type) {
        // Jackson 3 (tools.jackson), o padrao do Spring Boot 4: o Jackson 2
        // (com.fasterxml) nao esta no classpath da aplicacao.
        // Campos desconhecidos sao ignorados de proposito: o schema do pacote
        // tem campos opcionais (domains, legal, pdf) que o endpoint nao usa,
        // e um campo novo no pacote nao pode derrubar o boot.
        JsonMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try (InputStream in = BrandProvider.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Pacote de marca ausente no classpath: " + resource
                        + " (rode scripts/brand/sync-brand-resources.sh)");
            }
            return mapper.readValue(in, type);
        } catch (IllegalStateException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Pacote de marca invalido: " + resource, e);
        }
    }
}
