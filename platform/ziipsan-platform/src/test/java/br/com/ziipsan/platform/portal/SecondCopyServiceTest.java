package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.3 - 2a via do titular: so unidade vinculada, PDF + Pix da P3.2. */
class SecondCopyServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID BILL = UUID.randomUUID();

    private SecondCopyService service(boolean linked, boolean open) {
        return new SecondCopyService(new SecondCopyService.OpenBillPort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String sub, UUID u) { return linked; }
            @Override public Optional<UUID> openBillId(UUID t, UUID u) { return open ? Optional.of(BILL) : Optional.empty(); }
            @Override public byte[] secondCopyPdf(UUID t, UUID b) { return new byte[]{1, 2, 3}; }
            @Override public Optional<String> pixCopyPaste(UUID t, UUID b) { return Optional.of("pix-copia-e-cola"); }
        });
    }

    @Test
    void titularVinculadoRecebePdfEPix() {
        var copy = service(true, true).secondCopy(TENANT, "sub-1", UNIT);
        assertThat(copy.billId()).isEqualTo(BILL);
        assertThat(copy.pdf()).hasSize(3);
        assertThat(copy.pixCopyPaste()).isEqualTo("pix-copia-e-cola");
    }

    @Test
    void unidadeNaoVinculadaERecusada() {
        assertThatThrownBy(() -> service(false, true).secondCopy(TENANT, "sub-1", UNIT))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void semContaEmAbertoDevolveNaoEncontrado() {
        assertThatThrownBy(() -> service(true, false).secondCopy(TENANT, "sub-1", UNIT))
                .isInstanceOf(ApiException.NotFound.class);
    }
}
