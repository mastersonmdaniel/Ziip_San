package br.com.ziipsan.core.certification.assetmgmt55001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintenanceLinksTest {

    @Test
    @DisplayName("OPE-20: vínculo aponta para a linha do plano da ED-56, não a recria")
    void ope20Link() {
        var link = new MaintenanceLinks.NonLinearPreventiveLink(Iso55001Fixtures.on(),
                Iso55001Fixtures.ASSET, "PM-BOMBA-01", MaintenanceLinks.PmTriggerKind.RUNNING_HOURS);
        assertThat(link.trigger()).isEqualTo(MaintenanceLinks.PmTriggerKind.RUNNING_HOURS);
        assertThatThrownBy(() -> new MaintenanceLinks.NonLinearPreventiveLink(
                Iso55001Fixtures.on(), Iso55001Fixtures.ASSET, " ",
                MaintenanceLinks.PmTriggerKind.TIME)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OPE-38: peça abaixo do mínimo LIDO levanta reposição; estado nunca é presumido")
    void ope38Link() {
        var below = new MaintenanceLinks.ConditionMaintenanceLink(Iso55001Fixtures.on(),
                Iso55001Fixtures.ASSET, "VIBRATION", "ROL-6205",
                MaintenanceLinks.SpareStatusRead.BELOW_MINIMUM);
        assertThat(below.requiresRestock()).isTrue();
        var noPart = new MaintenanceLinks.ConditionMaintenanceLink(Iso55001Fixtures.on(),
                Iso55001Fixtures.ASSET, "TEMPERATURE", null, null);
        assertThat(noPart.requiresRestock()).isFalse();
        assertThatThrownBy(() -> new MaintenanceLinks.ConditionMaintenanceLink(
                Iso55001Fixtures.on(), Iso55001Fixtures.ASSET, "VIBRATION", "ROL-6205", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nunca é presumido");
    }
}
