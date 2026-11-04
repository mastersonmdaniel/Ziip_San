package br.com.ziipsan.app.about;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * P1.24: licenças de terceiros do Sobre (ADR-0021 decisão 7; AJUDA §9 passo 4).
 *
 * <p>Fonte: {@code third-party-licenses.json}, gerado dos SBOMs CycloneDX
 * (Maven e npm) pelo script da P0.40 e embalado na imagem. O formato é o
 * do gerador: {@code {components: [{name, version, licenses: [...]}]}}.
 *
 * <p>Arquivo ausente NÃO derruba o boot nem finge lista: a resposta diz
 * {@code available: false} com lista vazia. Hoje o CI gera o arquivo em
 * /tmp só para conferir contra a lista permitida — embalá-lo nos recursos
 * da aplicação é passo do pipeline de release, declarado no relatório da
 * frente, não inventado aqui. As licenças do frontend (ex.: Lucide, P0.46)
 * já vivem em {@code frontend/src/brand/licenses.ts} e a tela /about as
 * mostra junto das do backend quando disponíveis.
 */
public class ThirdPartyLicensesLoader {

    public record License(String name, String version, List<String> licenses) {
    }

    public record Licenses(boolean available, List<License> licenses) {
    }

    private final String resource;
    private final Licenses licenses;

    public ThirdPartyLicensesLoader() {
        this("third-party-licenses.json");
    }

    ThirdPartyLicensesLoader(String resource) {
        this.resource = resource;
        this.licenses = load();
    }

    public Licenses licenses() {
        return licenses;
    }

    private Licenses load() {
        JsonMapper mapper = JsonMapper.builder().build();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return new Licenses(false, List.of());
            }
            JsonNode root = mapper.readTree(in);
            JsonNode components = root == null ? null : root.get("components");
            if (components == null || !components.isArray()) {
                throw new IllegalStateException("third-party-licenses embalado inválido: " + resource);
            }
            List<License> out = new ArrayList<>();
            for (JsonNode comp : components) {
                List<String> lics = new ArrayList<>();
                JsonNode licsNode = comp.get("licenses");
                if (licsNode != null && licsNode.isArray()) {
                    licsNode.forEach(l -> lics.add(l.asString()));
                }
                out.add(new License(
                        comp.hasNonNull("name") ? comp.get("name").asString() : "unknown",
                        comp.hasNonNull("version") ? comp.get("version").asString() : "",
                        List.copyOf(lics)));
            }
            return new Licenses(true, List.copyOf(out));
        } catch (IllegalStateException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Pacote de licenças inválido: " + resource, e);
        }
    }
}
