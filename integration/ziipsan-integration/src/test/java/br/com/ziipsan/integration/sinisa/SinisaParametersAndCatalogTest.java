package br.com.ziipsan.integration.sinisa;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parametro sinisa.provider_scope e catalogo carregavel (P4.2): ausencia e erro
 * explicito; catalogo sem origem e recusado; codigos oficiais nao sao inventados
 * (a semente nasce com officialCode nulo, a preencher do Glossario na ED-48).
 */
class SinisaParametersAndCatalogTest {

    @Test
    @DisplayName("provider_scope LOCAL e REGIONAL lidos do JSON do parametro")
    void providerScopeLido() {
        assertThat(SinisaParameters.fromValues(Map.of("sinisa.provider_scope", "\"LOCAL\"")).providerScope())
                .isEqualTo(SinisaProviderScope.LOCAL);
        assertThat(SinisaParameters.fromValues(Map.of("sinisa.provider_scope", "\"REGIONAL\"")).providerScope())
                .isEqualTo(SinisaProviderScope.REGIONAL);
    }

    @Test
    @DisplayName("provider_scope ausente e erro explicito, nunca escopo presumido")
    void providerScopeAusente() {
        assertThatThrownBy(() -> SinisaParameters.fromValues(Map.of()).providerScope())
                .isInstanceOf(MissingParameterException.class);
    }

    @Test
    @DisplayName("provider_scope fora do vocabulario e recusado")
    void providerScopeInvalido() {
        assertThatThrownBy(() -> SinisaParameters
                .fromValues(Map.of("sinisa.provider_scope", "\"MUNICIPAL\"")).providerScope())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do catalogo");
    }

    @Test
    @DisplayName("escopo aceita municipios conforme a definicao oficial: LOCAL = 1, REGIONAL = mais de um")
    void escopoMunicipios() {
        assertThat(SinisaProviderScope.LOCAL.aceitaMunicipios(1)).isTrue();
        assertThat(SinisaProviderScope.LOCAL.aceitaMunicipios(2)).isFalse();
        assertThat(SinisaProviderScope.REGIONAL.aceitaMunicipios(1)).isFalse();
        assertThat(SinisaProviderScope.REGIONAL.aceitaMunicipios(2)).isTrue();
    }

    @Test
    @DisplayName("semente do catalogo carrega com os tres formularios e nenhuma origem vazia")
    void sementeCarrega() throws Exception {
        String json;
        try (InputStream in = getClass().getResourceAsStream("/sinisa/sinisa-catalog-seed.json")) {
            assertThat(in).isNotNull();
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        SinisaCatalog catalog = SinisaCatalog.fromJson(json);

        assertThat(catalog.size()).isEqualTo(6);
        assertThat(catalog.variables().stream().map(SinisaVariable::form)
                .collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrder(SinisaForm.FINANCEIRO, SinisaForm.TECNICO, SinisaForm.BALANCO);
        assertThat(catalog.variables()).allSatisfy(v -> {
            assertThat(v.sourceExpression()).isNotBlank();
            assertThat(v.officialCode()).isNull();
        });
        assertThat(catalog.require("AGUA.AGUA_NAO_FATURADA").sourceKind())
                .isEqualTo(SinisaVariable.SourceKind.DERIVED);
    }

    @Test
    @DisplayName("variavel sem origem declarada e recusada na carga do catalogo")
    void variavelSemOrigemRecusada() {
        String json = "[{\"key\": \"X.Y\", \"officialCode\": null, \"form\": \"TECNICO\","
                + " \"name\": \"Sem origem\", \"unit\": \"m3\", \"sourceKind\": \"METERING\","
                + " \"sourceExpression\": \"  \"}]";
        assertThatThrownBy(() -> SinisaCatalog.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem origem declarada");
    }

    @Test
    @DisplayName("chave duplicada no catalogo e recusada")
    void chaveDuplicadaRecusada() {
        String one = "{\"key\": \"X.Y\", \"officialCode\": null, \"form\": \"TECNICO\","
                + " \"name\": \"Um\", \"unit\": \"m3\", \"sourceKind\": \"MANUAL\","
                + " \"sourceExpression\": \"manual\"}";
        assertThatThrownBy(() -> SinisaCatalog.fromJson("[" + one + "," + one + "]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicada");
    }
}
