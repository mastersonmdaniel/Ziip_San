package br.com.ziipsan.core.transparency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import br.com.ziipsan.core.protocol.DeadlineCountingRule;
import br.com.ziipsan.core.protocol.ProtocolChannel;
import br.com.ziipsan.core.transparency.LaiRequest.AnswerOutcome;
import br.com.ziipsan.core.transparency.TransparencyParameters.LaiMode;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
/**
 * Teste de aceite nomeado na spec da ED-99: tenant AUTARQUIA x tenant de
 * OUTRA natureza (PRIVATE), ponta a ponta no dominio.
 */
class TenantNatureAcceptanceTest {
    @Test @DisplayName("tenant autarquia: modulo ligado por padrao (link do municipio); em NATIVE_SIC o pedido corre com prazos da lei")
    void autarchyTenant() {
        // padrao da natureza: ligado, modo EXTERNAL_LINK com o link do municipio
        assertThat(TransparencyParameters.laiMode("AUTARCHY", null)).isEqualTo(LaiMode.EXTERNAL_LINK);
        var point = LaiAccessPoint.forMode(
                TransparencyParameters.laiMode("AUTARCHY", null),
                "https://lai.municipio.exemplo.br", false);
        assertThat(point.externalUrl()).isEqualTo("https://lai.municipio.exemplo.br");
        assertThat(point.falaBrEnrolled()).isFalse(); // Fala.BR: adesao voluntaria, nao presumida
        // tenant opta pelo SIC nativo: pedido como protocolo da P1.36, 20 dias
        var mode = TransparencyParameters.laiMode("AUTARCHY", "NATIVE_SIC");
        var request = new LaiRequest(UUID.randomUUID(), ProtocolChannel.PORTAL,
                LocalDate.of(2026, 1, 5), mode,
                LaiDeadlines.fromTenantValues(null, null, null, null),
                DeadlineCountingRule.CALENDAR, Set.of());
        assertThat(request.currentResponseDue()).isEqualTo(LocalDate.of(2026, 1, 25));
        request.answer(LocalDate.of(2026, 1, 22), AnswerOutcome.GRANTED);
        assertThat(request.answeredWithinDeadline()).isTrue();
    }
    @Test @DisplayName("tenant PRIVATE: modulo desligado por padrao — sem ponto de acesso, sem pedido nativo, sem dados abertos")
    void privateTenant() {
        assertThat(TransparencyParameters.laiMode("PRIVATE", null)).isEqualTo(LaiMode.NONE);
        assertThat(TransparencyParameters.moduleEnabled("PRIVATE", null)).isFalse();
        assertThatThrownBy(() -> LaiAccessPoint.forMode(LaiMode.NONE, null, false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LaiRequest(UUID.randomUUID(), ProtocolChannel.PORTAL,
                LocalDate.of(2026, 1, 5), LaiMode.NONE, LaiDeadlines.FACTORY,
                DeadlineCountingRule.CALENDAR, Set.of()))
            .isInstanceOf(IllegalStateException.class);
        var dataset = new OpenDataExport.Dataset("tarifas", java.util.List.of("categoria"),
                java.util.List.of(java.util.Map.of("categoria", "RESIDENCIAL")));
        assertThatThrownBy(() -> OpenDataExport.export(dataset, OpenDataExport.Format.CSV, false, true))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("tenant SEM (outra natureza): tambem desligado por padrao, mas pode ligar voluntariamente")
    void semTenantOptIn() {
        assertThat(TransparencyParameters.laiMode("SEM", null)).isEqualTo(LaiMode.NONE);
        assertThat(TransparencyParameters.laiMode("SEM", "EXTERNAL_LINK")).isEqualTo(LaiMode.EXTERNAL_LINK);
        assertThat(TransparencyParameters.moduleEnabled("SEM", "EXTERNAL_LINK")).isTrue();
    }
}
