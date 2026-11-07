package br.com.ziipsan.operations.metering.hydrometry;

import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.serviceorder.TargetVerifierRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** B1: o verificador da ED-14 implementa/registra a interface REAL do core (P2.5/P1.31). */
class MeterTargetVerifierRealInterfaceTest {

    @Test @DisplayName("registro no TargetVerifierRegistry real torna METER declarable e o verificador responde pela interface real")
    void registersRealVerifier() throws Exception {
        UUID existing = UUID.randomUUID();
        TargetVerifierRegistry registry = new TargetVerifierRegistry();
        assertThat(registry.hasVerifier(TargetType.METER)).isFalse();
        registry.register(TargetType.METER,
                MeterTargetVerifier.verifier((tenant, id) -> id.equals(existing)));
        assertThat(registry.hasVerifier(TargetType.METER)).isTrue();
        assertThat(registry.declarableClasses()).contains(TargetType.METER);
        TargetVerifierRegistry.TargetExistenceVerifier v = registry.verifier(TargetType.METER);
        // assinatura real: exists(Connection, tenantId, targetId)
        assertThat(v.exists(null, UUID.randomUUID(), existing)).isTrue();
        assertThat(v.exists(null, UUID.randomUUID(), UUID.randomUUID())).isFalse();
        assertThat(v.exists(null, UUID.randomUUID(), null)).isFalse();
    }

    @Test @DisplayName("sqlVerifier do core valida schema/tabela e mira a tabela meters")
    void sqlVerifierFactory() {
        assertThat(MeterTargetVerifier.sqlVerifier("tenant_abc")).isNotNull();
        assertThatThrownBy(() -> MeterTargetVerifier.sqlVerifier("Tenant Com Espaco"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("registerInto registra METER com o sqlVerifier")
    void registerInto() {
        TargetVerifierRegistry registry = new TargetVerifierRegistry();
        MeterTargetVerifier.registerInto(registry, "tenant_abc");
        assertThat(registry.hasVerifier(TargetType.METER)).isTrue();
        assertThat(registry.declarableClasses()).contains(TargetType.METER);
    }
}
