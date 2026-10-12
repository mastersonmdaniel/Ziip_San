package br.com.sigsan.compliance.lgpd.engine;

import br.com.sigsan.compliance.lgpd.domain.DataSubjectRequest;
import br.com.sigsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSubjectRightsEngineTest {

    private final DataSubjectRightsEngine engine = new DataSubjectRightsEngine();

    private static DataSubjectRequest received(String metadata) {
        DataSubjectRequest r = new DataSubjectRequest();
        r.setRequestType(DataSubjectRequest.RequestType.ACCESS);
        r.setStatus(DataSubjectRequest.RequestStatus.RECEIVED);
        r.setMetadata(metadata);
        return r;
    }

    @Test
    @DisplayName("sem método de verificação: VERIFICATION_PENDING e nota gravada como JSON válido")
    void parksWithoutVerificationMethod() {
        DataSubjectRequest r = received(null);
        engine.processRequest(r);

        assertThat(r.getStatus()).isEqualTo(DataSubjectRequest.RequestStatus.VERIFICATION_PENDING);
        assertThat(r.getDeadlineAt()).isNotNull();
        Map<String, Object> m = Json.readMap(r.getMetadata());
        assertThat(m).containsOnlyKeys("verificationRequired");
        assertThat((String) m.get("verificationRequired")).contains("Art. 18 §2");
    }

    @Test
    @DisplayName("aspas e barra invertida em metadados existentes sobrevivem; chaves preservadas")
    void keepsExistingKeysAndEscaping() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("origem", "portal \"web\"");
        before.put("caminho", "C:\\anexos\\pedido.pdf");
        before.put("tentativas", 2);
        DataSubjectRequest r = received(Json.write(before));

        engine.processRequest(r);

        // A concatenação antiga cortava o último caractere e colava a chave nova sem escape.
        Map<String, Object> after = Json.readMap(r.getMetadata());
        assertThat(after).containsKeys("origem", "caminho", "tentativas", "verificationRequired");
        assertThat(after.get("origem")).isEqualTo("portal \"web\"");
        assertThat(after.get("caminho")).isEqualTo("C:\\anexos\\pedido.pdf");
        assertThat(after.get("tentativas")).isEqualTo(2);
    }

    @Test
    @DisplayName("metadado ilegível lança erro em vez de ser sobrescrito")
    void invalidMetadataFails() {
        DataSubjectRequest r = received("{origem=portal}");
        assertThatThrownBy(() -> engine.processRequest(r)).isInstanceOf(IllegalStateException.class);
        assertThat(r.getMetadata()).as("o conteúdo original não é apagado").isEqualTo("{origem=portal}");
    }

    @Test
    @DisplayName("com método de verificação: VERIFICATION_PENDING sem mexer nos metadados")
    void withVerificationMethodLeavesMetadata() {
        DataSubjectRequest r = received("{\"a\":1}");
        r.setVerificationMethod("EMAIL");
        engine.processRequest(r);
        assertThat(r.getStatus()).isEqualTo(DataSubjectRequest.RequestStatus.VERIFICATION_PENDING);
        assertThat(r.getMetadata()).isEqualTo("{\"a\":1}");
    }
}
