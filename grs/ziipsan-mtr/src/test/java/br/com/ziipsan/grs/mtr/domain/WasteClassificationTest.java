package br.com.ziipsan.grs.mtr.domain;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E13, teste 9 (classificação).
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WasteClassificationTest {

    @Test
    @DisplayName("HazardClass da NBR 10004:2004 é I, IIA, IIB — 'III' não existe")
    void hazardClassHasNoThree() {
        assertThat(WasteClass.HazardClass.values())
                .containsExactly(WasteClass.HazardClass.I, WasteClass.HazardClass.IIA, WasteClass.HazardClass.IIB);
        assertThatThrownBy(() -> WasteClass.HazardClass.valueOf("III"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WasteClass.HazardClass.valueOf("II"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("código IBAMA: 6 dígitos, com ou sem asterisco de periculosidade; fora do formato recusa")
    void ibamaCodeFormat() {
        assertThat(WasteClassification.requireIbamaCode("190902")).isEqualTo("190902");
        assertThat(WasteClassification.requireIbamaCode("010101*")).isEqualTo("010101");
        assertThat(WasteClassification.hazardousByList("010101*")).isTrue();
        assertThat(WasteClassification.hazardousByList("190902")).isFalse();
        assertThatThrownBy(() -> WasteClassification.requireIbamaCode("19090"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WasteClassification.requireIbamaCode("19 09 02"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WasteClassification.requireIbamaCode("ABCDEF"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WasteClassification.requireIbamaCode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RSS grupos A–E e RCC classes A–D são esquemas próprios validados")
    void rssAndRccSchemes() {
        assertThat(WasteClassification.requireRssGroup("b")).isEqualTo("B");
        assertThat(WasteClassification.requireRccClass("D")).isEqualTo("D");
        assertThatThrownBy(() -> WasteClassification.requireRssGroup("F"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WasteClassification.requireRccClass("E"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("edição 2024 da NBR é declarada NÃO CONFIRMADA — nenhuma classe nova é aceite por ela")
    void edition2024IsUnconfirmed() {
        assertThat(WasteClassification.isUnconfirmedEdition("2024")).isTrue();
        assertThat(WasteClassification.isUnconfirmedEdition("2004")).isFalse();
        assertThat(WasteClassification.NBR_EDITION_CONFIRMED).isEqualTo("2004");
    }
}
