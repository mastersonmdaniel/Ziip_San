package br.com.ziipsan.core.transparency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import br.com.ziipsan.core.transparency.ActiveTransparency.PublishedItem;
import br.com.ziipsan.core.transparency.ActiveTransparency.Section;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
class ActiveTransparencyTest {
    private static PublishedItem item(Section s) {
        return new PublishedItem(s, "item " + s, "https://portal.exemplo.br/" + s.name().toLowerCase());
    }
    @Test @DisplayName("completude exige as SEIS secoes da spec; secao ausente e nomeada, nunca presumida")
    void completeness() {
        var t = new ActiveTransparency();
        assertThat(t.complete()).isFalse();
        assertThat(t.missingSections()).containsExactlyInAnyOrder(Section.values());
        for (Section s : Section.values()) {
            if (s != Section.INDICATORS) t.publish(item(s));
        }
        assertThat(t.missingSections()).containsExactly(Section.INDICATORS);
        t.publishIndicators((tenantId, a, b) -> List.of(new ActiveTransparency.IndicatorSnapshot(
                        "AGUA.LIGACOES_ATIVAS", "Ligacoes ativas", a, b, new BigDecimal("12345"))),
                UUID.randomUUID(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(t.complete()).isTrue();
        assertThat(t.indicators()).hasSize(1);
        assertThat(t.indicators().get(0).value()).isEqualByComparingTo("12345");
    }
    @Test @DisplayName("indicador nao entra como item comum; item sem titulo/referencia recusado")
    void guards() {
        var t = new ActiveTransparency();
        assertThatThrownBy(() -> t.publish(item(Section.INDICATORS)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublishedItem(Section.TARIFFS, " ", "ref"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublishedItem(Section.TARIFFS, "titulo", null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
