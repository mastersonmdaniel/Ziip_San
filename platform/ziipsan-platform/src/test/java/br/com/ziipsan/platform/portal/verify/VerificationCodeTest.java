package br.com.ziipsan.platform.portal.verify;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationCodeTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Test
    void codigoGeradoTemFormatoAgrupadoEAltaEntropia() {
        String code = VerificationCode.generate();
        assertThat(code).matches("[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}");
        assertThat(VerificationCode.hasCodeShape(code)).isTrue();
        assertThat(VerificationCode.hasCodeShape(code.toLowerCase(java.util.Locale.ROOT))).isTrue();
        assertThat(VerificationCode.hasCodeShape(code.replace("-", ""))).isTrue();
    }

    @Test
    void codigosGeradosNaoSeRepetem() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            assertThat(seen.add(VerificationCode.generate())).isTrue();
        }
    }

    @Test
    void matriculaESequenciasNaoTemFormatoDeCodigo() {
        assertThat(VerificationCode.hasCodeShape("000001-1")).isFalse();
        assertThat(VerificationCode.hasCodeShape("1234567890123456")).isTrue(); // 16 digitos estao no alfabeto...
        // ...mas a geracao nunca e sequencial: o indice cego so casa com codigo emitido.
        var protector = VerifyTestSupport.protector();
        String stored = VerificationCode.storageHash(protector, TENANT, VerificationCode.generate());
        assertThat(VerificationCode.matches(protector, TENANT, stored, "1234567890123456")).isFalse();
    }

    @Test
    void indiceCegoConfereEmTempoConstanteENormaliza() {
        var protector = VerifyTestSupport.protector();
        String code = VerificationCode.generate();
        String stored = VerificationCode.storageHash(protector, TENANT, code);
        assertThat(VerificationCode.matches(protector, TENANT, stored, code)).isTrue();
        assertThat(VerificationCode.matches(protector, TENANT, stored, code.replace("-", ""))).isTrue();
        assertThat(VerificationCode.matches(protector, TENANT, stored, VerificationCode.generate())).isFalse();
        assertThat(VerificationCode.matches(protector, UUID.randomUUID(), stored, code)).isFalse();
        assertThat(stored).doesNotContain(VerificationCode.normalize(code));
    }
}
