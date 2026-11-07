package br.com.ziipsan.platform.trustcenter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.38/P3.9: a lista pública de suboperadores — estrangeiro sem
 * cláusulas-padrão da ANPD não é publicável (a mesma conferência que
 * bloqueia a aprovação da minuta na P3.9) e o aviso de mudança carrega
 * anúncio + vigência. Os nomes abaixo são FIXTURE genérica de teste,
 * não suboperadores reais do projeto.
 */
class SubprocessorTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private static Subprocessor domestic() {
        return new Subprocessor("Provedor Nacional (fixture)", "Hospedagem de arquivos",
                "Guardar anexos cifrados", "BR", true, false, true,
                Subprocessor.ChangeStatus.ACTIVE, null, null);
    }

    private static Subprocessor foreign(boolean clauses) {
        return new Subprocessor("Foreign Provider (fixture)", "Notification delivery",
                "Enviar notificações transacionais", "US", true, clauses, true,
                Subprocessor.ChangeStatus.ACTIVE, null, null);
    }

    @Test
    @DisplayName("P3.38: suboperador nacional publicável entra na lista pública")
    void domesticIsListed() {
        assertThatCode(domestic()::requirePublishable).doesNotThrowAnyException();
        assertThat(domestic().isPubliclyListed()).isTrue();
        assertThat(domestic().isForeign()).isFalse();
    }

    @Test
    @DisplayName("P3.9/P3.38: estrangeiro SEM cláusulas-padrão da ANPD é recusado na publicação e fica fora da lista")
    void foreignWithoutClausesIsBlocked() {
        assertThatThrownBy(() -> foreign(false).requirePublishable())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cláusulas-padrão");
        assertThat(foreign(false).isPubliclyListed()).isFalse();
    }

    @Test
    @DisplayName("P3.9/P3.38: estrangeiro COM cláusulas-padrão incorporadas é publicável")
    void foreignWithClausesIsListed() {
        assertThatCode(() -> foreign(true).requirePublishable()).doesNotThrowAnyException();
        assertThat(foreign(true).isPubliclyListed()).isTrue();
    }

    @Test
    @DisplayName("P3.38: aviso de mudança exige anúncio e vigência em par, e só vigora na vigência")
    void announcedChangeCarriesNoticeAndEffectiveDate() {
        Subprocessor changing = new Subprocessor("Provedor Nacional (fixture)", "Hospedagem de arquivos",
                "Guardar anexos cifrados", "BR", true, false, true,
                Subprocessor.ChangeStatus.CHANGE_ANNOUNCED, NOW, NOW.plusSeconds(30L * 24 * 3600));
        assertThat(changing.changeEffectiveAt(NOW)).isFalse();
        assertThat(changing.changeEffectiveAt(NOW.plusSeconds(31L * 24 * 3600))).isTrue();

        assertThatThrownBy(() -> new Subprocessor("X (fixture)", "Serviço", "Fim", "BR",
                false, false, true, Subprocessor.ChangeStatus.CHANGE_ANNOUNCED, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Subprocessor("X (fixture)", "Serviço", "Fim", "BR",
                false, false, true, Subprocessor.ChangeStatus.CHANGE_ANNOUNCED,
                NOW, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior ao anúncio");
    }

    @Test
    @DisplayName("P3.38: removido ou invisível não aparece na lista pública")
    void removedOrHiddenIsNotListed() {
        Subprocessor removed = new Subprocessor("Provedor Nacional (fixture)", "Hospedagem de arquivos",
                "Guardar anexos cifrados", "BR", true, false, true,
                Subprocessor.ChangeStatus.REMOVED, null, null);
        assertThat(removed.isPubliclyListed()).isFalse();
        Subprocessor hidden = new Subprocessor("Provedor Nacional (fixture)", "Hospedagem de arquivos",
                "Guardar anexos cifrados", "BR", true, false, false,
                Subprocessor.ChangeStatus.ACTIVE, null, null);
        assertThat(hidden.isPubliclyListed()).isFalse();
    }
}
