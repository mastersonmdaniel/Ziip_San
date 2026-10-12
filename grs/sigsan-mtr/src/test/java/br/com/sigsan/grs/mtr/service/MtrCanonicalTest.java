package br.com.sigsan.grs.mtr.service;

import br.com.sigsan.grs.mtr.domain.WasteDestination;
import br.com.sigsan.grs.mtr.domain.WasteGenerator;
import br.com.sigsan.grs.mtr.domain.WasteMtr;
import br.com.sigsan.grs.mtr.domain.WasteTransporter;
import br.com.sigsan.grs.mtr.domain.WasteClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MtrCanonicalTest {

    private static final String CPF = "52998224725";

    private static <T extends br.com.sigsan.core.domain.BaseEntity> T withId(T e, String uuid) {
        e.setId(UUID.fromString(uuid));
        return e;
    }

    private static WasteMtr mtr() {
        WasteMtr m = new WasteMtr();
        m.setMtrNumber("2026.00000001.TENA.01");
        m.setGenerator(withId(new WasteGenerator(), "00000000-0000-0000-0000-000000000001"));
        m.setTransporter(withId(new WasteTransporter(), "00000000-0000-0000-0000-000000000002"));
        m.setDestination(withId(new WasteDestination(), "00000000-0000-0000-0000-000000000003"));
        m.setWasteClass(withId(new WasteClass(), "00000000-0000-0000-0000-000000000004"));
        m.setQuantityKg(new BigDecimal("100.50"));
        m.setStatus(WasteMtr.MtrStatus.EMITTED);
        m.setIssuedAt(OffsetDateTime.of(2026, 10, 12, 9, 0, 0, 0, ZoneOffset.UTC));
        return m;
    }

    @Test
    @DisplayName("é determinístico")
    void deterministic() {
        assertThat(MtrCanonical.of(mtr(), null)).isEqualTo(MtrCanonical.of(mtr(), null));
    }

    @Test
    @DisplayName("não depende de version nem de updatedAt: mudam sem alterar o manifesto")
    void ignoresBookkeepingFields() {
        WasteMtr a = mtr();
        WasteMtr b = mtr();
        b.setVersion(7L);
        b.setUpdatedAt(OffsetDateTime.now());
        b.setUpdatedBy(UUID.randomUUID());
        assertThat(MtrCanonical.of(b, null)).isEqualTo(MtrCanonical.of(a, null));
    }

    @Test
    @DisplayName("não inclui o próprio hash da cadeia (a versão anterior incluía)")
    void ignoresTheHashItself() {
        WasteMtr a = mtr();
        WasteMtr b = mtr();
        b.setHashChain("qualquer coisa");
        assertThat(MtrCanonical.of(b, null)).isEqualTo(MtrCanonical.of(a, null));
    }

    @Test
    @DisplayName("muda quando o manifesto muda: status, quantidade, partes e assinatura")
    void changesWithTheManifest() {
        String base = MtrCanonical.of(mtr(), null);

        WasteMtr status = mtr();
        status.setStatus(WasteMtr.MtrStatus.COLLECTED);
        assertThat(MtrCanonical.of(status, null)).isNotEqualTo(base);

        WasteMtr quantity = mtr();
        quantity.setQuantityKg(new BigDecimal("100.51"));
        assertThat(MtrCanonical.of(quantity, null)).isNotEqualTo(base);

        WasteMtr destination = mtr();
        destination.setDestination(withId(new WasteDestination(), "00000000-0000-0000-0000-0000000000ff"));
        assertThat(MtrCanonical.of(destination, null)).isNotEqualTo(base);

        WasteMtr signed = mtr();
        signed.setTransporterSignature("{\"signedAt\":\"2026-10-12\"}");
        assertThat(MtrCanonical.of(signed, null)).isNotEqualTo(base);
    }

    @Test
    @DisplayName("a mesma quantidade em escalas diferentes (100.5 e 100.50) dá o mesmo conteúdo")
    void quantityScaleDoesNotMatter() {
        WasteMtr a = mtr();
        a.setQuantityKg(new BigDecimal("100.5"));
        WasteMtr b = mtr();
        b.setQuantityKg(new BigDecimal("100.500"));
        assertThat(MtrCanonical.of(a, null)).isEqualTo(MtrCanonical.of(b, null));
    }

    @Test
    @DisplayName("o mesmo instante em fusos diferentes dá o mesmo conteúdo")
    void sameInstantInDifferentOffsets() {
        WasteMtr a = mtr();
        WasteMtr b = mtr();
        b.setIssuedAt(OffsetDateTime.of(2026, 10, 12, 6, 0, 0, 0, ZoneOffset.ofHours(-3)));
        assertThat(MtrCanonical.of(b, null)).isEqualTo(MtrCanonical.of(a, null));
    }

    @Test
    @DisplayName("campos vizinhos não se confundem: (\"ab\",\"c\") difere de (\"a\",\"bc\")")
    void separatorPreventsAmbiguity() {
        WasteMtr a = mtr();
        a.setVehiclePlate("AB");
        WasteMtr b = mtr();
        b.setVehiclePlate("A");
        assertThat(MtrCanonical.of(a, "C")).isNotEqualTo(MtrCanonical.of(b, "BC"));
    }

    @Test
    @DisplayName("formato congelado: conteúdo e hash de um MTR conhecido são os mesmos de antes da cifra (V2_8)")
    void frozenFormat() {
        // O hash abaixo foi calculado pela versão anterior, que lia o CPF em claro da entidade
        // (WasteMtr.driverCpf). Com o CPF agora cifrado, o mesmo CPF decifrado tem de dar o mesmo hash.
        WasteMtr m = mtr();
        m.setVehiclePlate("ABC1D23");
        String canonical = MtrCanonical.of(m, CPF);
        assertThat(canonical).isEqualTo(String.join("\u001f",
                "mtr-canonical-1", "mtr", "2026.00000001.TENA.01", "EMITTED",
                "00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002",
                "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004",
                "100.5", "", "", "ABC1D23", "52998224725", "2026-10-12T09:00:00Z",
                "", "", "", "", "", ""));
        assertThat(HashChain.next(null, canonical))
                .isEqualTo("9526c7ead39695227ca9909d9fd82178f938ad5a3af1b601baa1e49e1b232ff5");
    }

    @Test
    @DisplayName("o CPF do motorista entra no conteúdo: outro CPF, outro hash; sem motorista, campo vazio")
    void driverCpfIsPartOfTheContent() {
        assertThat(MtrCanonical.of(mtr(), CPF)).isNotEqualTo(MtrCanonical.of(mtr(), "11144477735"));
        assertThat(MtrCanonical.of(mtr(), CPF)).isNotEqualTo(MtrCanonical.of(mtr(), null));
        assertThat(MtrCanonical.of(mtr(), null)).isEqualTo(MtrCanonical.of(mtr(), ""));
    }

    @Test
    @DisplayName("começa pela versão do formato canônico")
    void startsWithFormatVersion() {
        assertThat(MtrCanonical.of(mtr(), null)).startsWith(MtrCanonical.FORMAT_VERSION + "\u001f");
    }
}
