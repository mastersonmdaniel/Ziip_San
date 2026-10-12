package br.com.sigsan.compliance.lgpd.engine;

import br.com.sigsan.compliance.lgpd.domain.DpiaRecord;
import br.com.sigsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DpiaEngineTest {

    private final DpiaEngine engine = new DpiaEngine();

    /** DPIA sem nenhum fator de risco: medidas de segurança descritas e prazo de retenção definido. */
    private static DpiaRecord clean() {
        DpiaRecord d = new DpiaRecord();
        d.setProcessingActivity("Faturamento mensal");
        d.setDataCategories(Json.write(List.of("consumo")));
        d.setSpecialCategories("[]");
        d.setDataSubjects(Json.write(List.of("clientes")));
        d.setRecipients("[]");
        d.setThirdCountryTransfers("[]");
        d.setSecurityMeasures(Json.write(Map.of("cifra", "AES-256")));
        d.setRetentionPeriod("5 anos");
        return d;
    }

    @Test
    @DisplayName("medidas de mitigação são gravadas como array JSON válido, não como List.toString()")
    void mitigationMeasuresAreJsonArray() {
        DpiaRecord d = clean();
        d.setSpecialCategories(Json.write(List.of("saude")));
        engine.assessRisk(d);

        String stored = d.getMitigationMeasures();
        assertThat(stored).startsWith("[\"");
        JsonNode node = Json.readTree(stored);
        assertThat(node.isArray()).isTrue();
        node.forEach(item -> assertThat(item.isString()).isTrue());
        assertThat(node.get(0).asString()).startsWith("Revisar descricao do tratamento");
        assertThat(node.toString()).contains("Mitigar: Dados sensiveis (Art. 11)");
    }

    @Test
    @DisplayName("sem nenhum fator: escore 0 e risco NEGLIGIBLE")
    void negligible() {
        DpiaRecord d = clean();
        engine.assessRisk(d);
        assertThat(d.getRiskScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(d.getRiskLevel()).isEqualTo(DpiaRecord.RiskLevel.NEGLIGIBLE);
        assertThat(d.getLikelihood()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("medidas de segurança vazias ({}) contam como ausentes: LOW com 10 pontos")
    void emptySecurityMeasuresCountAsMissing() {
        DpiaRecord d = clean();
        d.setSecurityMeasures("{}");
        engine.assessRisk(d);
        assertThat(d.getRiskScore()).isEqualByComparingTo("10");
        assertThat(d.getRiskLevel()).isEqualTo(DpiaRecord.RiskLevel.LOW);
    }

    @Test
    @DisplayName("dado sensível sem segurança e retenção indefinida: HIGH, probabilidade HIGH")
    void high() {
        DpiaRecord d = clean();
        d.setSpecialCategories(Json.write(List.of("biometria")));
        d.setSecurityMeasures(null);
        d.setRetentionPeriod("indefinido");
        engine.assessRisk(d);
        assertThat(d.getRiskScore()).isEqualByComparingTo("50");
        assertThat(d.getRiskLevel()).isEqualTo(DpiaRecord.RiskLevel.HIGH);
        assertThat(d.getLikelihood()).isEqualTo("HIGH");
        assertThat(d.getResidualRisk()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("sensível + grande escala + monitoramento + transferência + terceiros: CRITICAL")
    void critical() {
        DpiaRecord d = clean();
        d.setSpecialCategories(Json.write(List.of("saude")));
        d.setDataSubjects(Json.write(List.of("pacientes", "clientes", "funcionarios", "visitantes", "fornecedores")));
        d.setThirdCountryTransfers(Json.write(List.of("EUA")));
        d.setRecipients(Json.write(List.of("terceiros contratados")));
        engine.assessRisk(d);
        assertThat(d.getRiskScore()).isEqualByComparingTo("80");
        assertThat(d.getRiskLevel()).isEqualTo(DpiaRecord.RiskLevel.CRITICAL);
        assertThat(d.getResidualRisk()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("vírgula dentro de um item não o divide: 4 itens não são grande escala")
    void commaInsideItemIsOneEntry() {
        DpiaRecord d = clean();
        // A contagem antiga separava por vírgula e veria 5 itens aqui.
        d.setDataSubjects(Json.write(List.of("clientes, inclusive menores", "funcionarios", "terceirizados", "fornecedores")));
        engine.assessRisk(d);
        assertThat(d.getRiskScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("coluna com conteúdo que não é JSON lança erro em vez de virar palpite")
    void invalidStoredJsonFails() {
        DpiaRecord d = clean();
        d.setSpecialCategories("[saude, biometria]"); // forma de List.toString()
        assertThatThrownBy(() -> engine.assessRisk(d)).isInstanceOf(IllegalStateException.class);
    }
}
