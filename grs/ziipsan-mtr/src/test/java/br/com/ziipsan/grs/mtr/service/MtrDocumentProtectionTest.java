package br.com.ziipsan.grs.mtr.service;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteGenerator;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.grs.mtr.domain.WasteTransporter;
import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MtrDocumentProtectionTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CPF = "52998224725";

    private MtrDocumentProtection documents;

    @BeforeEach
    void setUp() {
        byte[] master = new byte[32];
        for (int i = 0; i < master.length; i++) {
            master[i] = (byte) (31 - i);
        }
        var keys = new EnvDerivedTenantKeyProvider(master);
        documents = new MtrDocumentProtection(new FieldProtector(new FieldCipher(keys), new BlindIndex(keys)));
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("contextos separados: o mesmo CPF dá índices diferentes como motorista e como gerador, e uma cifra não decifra no outro campo")
    void contextsAreSeparated() {
        WasteMtr mtr = new WasteMtr();
        documents.assignDriverCpf(mtr, CPF);
        WasteGenerator generator = new WasteGenerator();
        documents.assignGeneratorDocument(generator, CPF);

        assertThat(mtr.getDriverCpfHash()).isNotEqualTo(generator.getDocumentHash());

        WasteGenerator swapped = new WasteGenerator();
        swapped.setDocumentCiphertext(mtr.getDriverCpfCiphertext());
        assertThatThrownBy(() -> documents.revealGeneratorDocument(swapped)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("outro tenant não decifra o CPF do motorista")
    void otherTenantCannotReveal() {
        WasteMtr mtr = new WasteMtr();
        documents.assignDriverCpf(mtr, CPF);
        TenantContextHolder.setTenantId(OTHER_TENANT);
        assertThatThrownBy(() -> documents.revealDriverCpf(mtr)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("CPF do motorista em branco limpa as três colunas (CHECK: cifra nula se e só se índice nulo)")
    void blankDriverCpfClears() {
        WasteMtr mtr = new WasteMtr();
        documents.assignDriverCpf(mtr, CPF);
        documents.assignDriverCpf(mtr, "  ");
        assertThat(mtr.getDriverCpfCiphertext()).isNull();
        assertThat(mtr.getDriverCpfHash()).isNull();
        assertThat(mtr.getDriverCpfMasked()).isNull();
        assertThat(documents.revealDriverCpf(mtr)).isNull();
    }

    @Test
    @DisplayName("motorista sem nome é recusado")
    void driverWithoutNameIsRejected() {
        var drivers = List.of(new MtrDocumentProtection.Driver(" ", CPF, null, null, null));
        assertThatThrownBy(() -> documents.assignDrivers(new WasteTransporter(), drivers))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motorista 1 sem nome");
    }

    @Test
    @DisplayName("sem tenant no contexto: erro, nada é cifrado")
    void noTenant() {
        TenantContextHolder.clear();
        WasteMtr mtr = new WasteMtr();
        assertThatThrownBy(() -> documents.assignDriverCpf(mtr, CPF))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sem tenant");
        assertThat(mtr.getDriverCpfCiphertext()).isNull();
    }
}
