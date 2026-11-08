package br.com.ziipsan.compliance.lgpd.privacyiso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aceite da ED-74: privacidade desde a concepção, com registro de que o tratamento teve RIPD. */
class PrivacyByDesignCheckTest {

    private static final UUID CHECKER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);

    private static Iso27701Activation on() {
        return Iso27701Activation.enabled("2025", Iso27701Activation.EditionUse.TARGET,
                "escopo", "sgpi");
    }

    @Test
    @DisplayName("tratamento novo com referência de RIPD completa a verificação")
    void newProcessingWithDpia() {
        var c = PrivacyByDesignCheck.register(on(), "REL-2026-11", true, "RIPD-2026-03", "",
                CHECKER, DAY);
        assertThat(c.complete()).isTrue();
    }

    @Test
    @DisplayName("tratamento novo ou alterado sem RIPD é recusado")
    void newProcessingWithoutDpiaRefused() {
        assertThatThrownBy(() -> PrivacyByDesignCheck.register(on(), "REL-2026-11", true,
                " ", "", CHECKER, DAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrivacyByDesignCheck.register(on(), "REL-2026-11", true,
                null, null, CHECKER, DAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("mudança sem tratamento novo exige justificativa declarada")
    void unchangedNeedsRationale() {
        assertThatThrownBy(() -> PrivacyByDesignCheck.register(on(), "REL-2026-11", false,
                null, " ", CHECKER, DAY)).isInstanceOf(IllegalArgumentException.class);
        var c = PrivacyByDesignCheck.register(on(), "REL-2026-11", false, null,
                "mudança só de texto de tela, sem dado pessoal novo", CHECKER, DAY);
        assertThat(c.complete()).isTrue();
    }

    @Test
    @DisplayName("dpiaRef só-espaços é normalizada para vazio, como as demais classes (D4)")
    void whitespaceDpiaRefNormalizedToEmpty() {
        var c = PrivacyByDesignCheck.register(on(), "REL-2026-11", false, "   ",
                "mudança só de texto de tela, sem dado pessoal novo", CHECKER, DAY);
        assertThat(c.dpiaRef()).isEqualTo("");
        var trimmed = PrivacyByDesignCheck.register(on(), "REL-2026-11", true, "  RIPD-2026-03  ",
                "", CHECKER, DAY);
        assertThat(trimmed.dpiaRef()).isEqualTo("RIPD-2026-03");
    }

    @Test
    @DisplayName("sem referência da mudança, a verificação não nasce")
    void changeRefRequired() {
        assertThatThrownBy(() -> PrivacyByDesignCheck.register(on(), " ", true, "RIPD-1", "",
                CHECKER, DAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("referências respeitam o teto do SQL pendente (200)")
    void refLengthsRespectSql() {
        assertThatThrownBy(() -> PrivacyByDesignCheck.register(on(), "X".repeat(201), true,
                "RIPD-1", "", CHECKER, DAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("com a norma desligada, nenhuma verificação do pacote nasce")
    void disabledRefuses() {
        assertThatThrownBy(() -> PrivacyByDesignCheck.register(Iso27701Activation.disabled(),
                "REL-2026-11", true, "RIPD-1", "", CHECKER, DAY))
                .isInstanceOf(IllegalStateException.class);
    }
}
