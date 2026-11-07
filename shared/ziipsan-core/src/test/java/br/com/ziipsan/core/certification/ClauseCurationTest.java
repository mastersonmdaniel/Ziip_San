package br.com.ziipsan.core.certification;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-76: formato do mapa de clausulas por edicao. Dados 100%
 * sinteticos (prefixo SYN-, titulos assumidamente ficticios): nenhum
 * titulo, resumo ou numero aqui e de norma real — o conteudo real do
 * mapa nasce da curadoria sobre os textos comprados na P1.28, que ficam
 * fora do repositorio.
 */
class ClauseCurationTest {

    private static ClauseCuration.ClauseEntry entry(String number, String title, String summary, int order) {
        return new ClauseCuration.ClauseEntry(number, title, summary, order);
    }

    @Test
    @DisplayName("o mapa ordena por sortOrder e número e recusa cláusula repetida na mesma edição")
    void mapOrdersAndRefusesDuplicateClause() {
        ClauseCuration.EditionClauseMap map = new ClauseCuration.EditionClauseMap("SYN-EXEMPLO-1", "1",
                List.of(entry("2.1", "Titulo ficticio dois", null, 2),
                        entry("1.1", "Titulo ficticio um", "Resumo ficticio um.", 1)));
        assertThat(map.clauses()).extracting(ClauseCuration.ClauseEntry::clauseNumber)
                .containsExactly("1.1", "2.1");
        assertThat(map.clause("2.1").title()).isEqualTo("Titulo ficticio dois");
        assertThat(map.clause("9.9")).isNull();

        assertThatThrownBy(() -> new ClauseCuration.EditionClauseMap("SYN-EXEMPLO-1", "1",
                List.of(entry("1.1", "Titulo ficticio um", null, 1),
                        entry("1.1", "Outro titulo ficticio", null, 2))))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("número de cláusula fora do formato e título vazio são recusados")
    void invalidClauseEntryIsRefused() {
        assertThatThrownBy(() -> entry("1..2", "Titulo ficticio", null, 0))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> entry("  ", "Titulo ficticio", null, 0))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> entry("1.1", " ", null, 0))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> entry("1.1", "Titulo ficticio", null, -1))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("a entrada de cláusula não tem campo capaz de guardar texto de norma")
    void clauseEntryHasNoNormTextField() {
        assertThat(Arrays.stream(ClauseCuration.ClauseEntry.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("clauseNumber", "title", "summary", "sortOrder");
    }

    @Test
    @DisplayName("o JSON de curadoria faz ida e volta sem perder cláusula")
    void jsonRoundTrip() {
        ClauseCuration.EditionClauseMap map = new ClauseCuration.EditionClauseMap("SYN-EXEMPLO-1", "1",
                List.of(entry("1.1", "Titulo ficticio um", "Resumo ficticio um.", 1),
                        entry("2.1", "Titulo ficticio dois", null, 2)));
        String json = ClauseCuration.toJson(map);
        assertThat(ClauseCuration.fromJson(json)).isEqualTo(map);
    }

    @Test
    @DisplayName("o JSON de curadoria recusa campo desconhecido — é a trava contra texto de norma")
    void jsonRefusesUnknownField() {
        String withText = "{\"standardCode\":\"SYN-EXEMPLO-1\",\"edition\":\"1\",\"clauses\":["
                + "{\"clauseNumber\":\"1.1\",\"title\":\"Titulo ficticio um\","
                + "\"texto\":\"qualquer conteudo\",\"sortOrder\":1}]}";
        assertThatThrownBy(() -> ClauseCuration.fromJson(withText))
                .isInstanceOf(ApiException.Validation.class);

        String withRootExtra = "{\"standardCode\":\"SYN-EXEMPLO-1\",\"edition\":\"1\","
                + "\"fonteTexto\":\"x\",\"clauses\":[]}";
        assertThatThrownBy(() -> ClauseCuration.fromJson(withRootExtra))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("o envelope do arquivo entregue, com a lista vazia, é legível pelo caminho de carga")
    void emptyEnvelopeIsReadable() {
        assertThat(ClauseCuration.fromJsonEnvelope("{\"mapas\":[],\"versaoFormato\":1}")).isEmpty();
        assertThat(ClauseCuration.fromJsonEnvelope(ClauseCuration.toJsonEnvelope(List.of()))).isEmpty();
    }

    @Test
    @DisplayName("o envelope faz ida e volta com um mapa sintético")
    void envelopeRoundTripWithSyntheticMap() {
        ClauseCuration.EditionClauseMap map = new ClauseCuration.EditionClauseMap("SYN-EXEMPLO-1", "1",
                List.of(entry("1.1", "Titulo ficticio um", "Resumo ficticio um.", 1),
                        entry("2.1", "Titulo ficticio dois", null, 2)));
        String envelope = ClauseCuration.toJsonEnvelope(List.of(map));
        assertThat(ClauseCuration.fromJsonEnvelope(envelope)).containsExactly(map);
    }

    @Test
    @DisplayName("o envelope recusa chave desconhecida, versão de formato errada e texto dentro de mapa")
    void envelopeRefusesUnknownKeyWrongVersionAndInnerText() {
        assertThatThrownBy(() -> ClauseCuration.fromJsonEnvelope(
                "{\"mapas\":[],\"versaoFormato\":1,\"fonte\":\"x\"}"))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> ClauseCuration.fromJsonEnvelope(
                "{\"mapas\":[],\"versaoFormato\":2}"))
                .isInstanceOf(ApiException.Validation.class);
        String withText = "{\"mapas\":[{\"standardCode\":\"SYN-EXEMPLO-1\",\"edition\":\"1\","
                + "\"clauses\":[{\"clauseNumber\":\"1.1\",\"title\":\"Titulo ficticio um\","
                + "\"texto\":\"qualquer conteudo\",\"sortOrder\":1}]}],\"versaoFormato\":1}";
        assertThatThrownBy(() -> ClauseCuration.fromJsonEnvelope(withText))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("o JSON de curadoria exige código, edição e lista de cláusulas")
    void jsonRequiresCoreFields() {
        assertThatThrownBy(() -> ClauseCuration.fromJson("{\"edition\":\"1\",\"clauses\":[]}"))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> ClauseCuration.fromJson(
                "{\"standardCode\":\"SYN-EXEMPLO-1\",\"edition\":\"1\"}"))
                .isInstanceOf(ApiException.Validation.class);
    }
}
