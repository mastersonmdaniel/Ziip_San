package br.com.ziipsan.platform.portal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P3.3/ADR-0043 - codigo de vinculacao: alta entropia, nunca matricula. */
class LinkingCodeTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Test
    void codigosGeradosSaoDistintosEComFormaDeAltaEntropia() {
        String a = LinkingCode.generate();
        String b = LinkingCode.generate();
        assertThat(a).isNotEqualTo(b);
        assertThat(LinkingCode.hasCodeShape(a)).isTrue();
        assertThat(a).hasSizeGreaterThanOrEqualTo(16);
    }

    @Test
    void matriculaNumericaNaoTemFormaDeCodigo() {
        assertThat(LinkingCode.hasCodeShape("123456789012")).isFalse();
        assertThat(LinkingCode.hasCodeShape("123")).isFalse();
        assertThat(LinkingCode.hasCodeShape(null)).isFalse();
    }

    @Test
    void conferenciaPeloIndiceCego() {
        var protector = PortalTestSupport.protector();
        String code = LinkingCode.generate();
        String hash = LinkingCode.storageHash(protector, TENANT, code);
        assertThat(hash).hasSize(64);
        assertThat(LinkingCode.matches(protector, TENANT, hash, code)).isTrue();
        assertThat(LinkingCode.matches(protector, TENANT, hash, LinkingCode.generate())).isFalse();
    }
}
