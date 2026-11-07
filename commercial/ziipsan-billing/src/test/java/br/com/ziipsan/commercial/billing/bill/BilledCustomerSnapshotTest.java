package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.domain.BillEntity;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.TenantKeyProvider;
import br.com.ziipsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Snapshot do titular faturado (P2.5, DAD-15): a conta guarda o snapshot CIFRADO de
 * quem foi faturado na emissao, e ele nao muda quando o titular da unidade troca.
 */
class BilledCustomerSnapshotTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CUSTOMER_A = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CUSTOMER_B = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static BillSnapshotService service() {
        byte[] master = new byte[32];
        for (int i = 0; i < master.length; i++) {
            master[i] = (byte) (i + 1);
        }
        TenantKeyProvider keys = new EnvDerivedTenantKeyProvider(master);
        // O servico usa so o FieldCipher; o BlindIndex nao participa (o snapshot nao
        // e um identificador buscavel por igualdade).
        return new BillSnapshotService(new FieldCipher(keys));
    }

    private static BilledCustomerSnapshot snapshotA() {
        return new BilledCustomerSnapshot("Maria da Silva", "***.456.789-**", "Rua das Flores, 123 - Centro");
    }

    private static BillEntity bill() {
        BillEntity bill = new BillEntity();
        bill.setId(UUID.randomUUID());
        bill.setTenantId(TENANT);
        return bill;
    }

    @Test
    @DisplayName("a coluna guarda so o texto cifrado: nem nome, nem documento mascarado, nem endereco em claro")
    void billStoresOnlyCiphertext() {
        BillEntity bill = bill();
        service().captureBilledCustomer(bill, CUSTOMER_A, snapshotA());

        assertThat(bill.getBilledCustomerId()).isEqualTo(CUSTOMER_A);
        assertThat(bill.getBilledCustomerSnapshot())
                .isNotNull()
                .doesNotContain("Maria")
                .doesNotContain("456.789")
                .doesNotContain("Rua das Flores")
                .doesNotContain("maskedDocument");
    }

    @Test
    @DisplayName("snapshot nao muda com troca posterior de titular: a conta continua mostrando quem foi faturado")
    void snapshotDoesNotChangeWhenHolderChangesLater() {
        BillSnapshotService service = service();
        BillEntity bill = bill();
        service.captureBilledCustomer(bill, CUSTOMER_A, snapshotA());
        String ciphertextAtIssue = bill.getBilledCustomerSnapshot();

        // A troca de titular acontece no cadastro e em contas FUTURAS; nesta conta,
        // nem uma nova captura e permitida.
        assertThatThrownBy(() -> service.captureBilledCustomer(bill, CUSTOMER_B,
                new BilledCustomerSnapshot("Joao Souza", "***.111.222-**", "Av. Central, 900")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(bill.getBilledCustomerId()).isEqualTo(CUSTOMER_A);
        assertThat(bill.getBilledCustomerSnapshot()).isEqualTo(ciphertextAtIssue);
        assertThat(service.revealBilledCustomer(bill)).isEqualTo(snapshotA());
    }

    @Test
    @DisplayName("ida e volta: reveal devolve nome, documento mascarado e endereco capturados")
    void revealRoundTrips() {
        BillEntity bill = bill();
        BillSnapshotService service = service();
        service.captureBilledCustomer(bill, CUSTOMER_A, snapshotA());

        BilledCustomerSnapshot revealed = service.revealBilledCustomer(bill);
        assertThat(revealed.name()).isEqualTo("Maria da Silva");
        assertThat(revealed.maskedDocument()).isEqualTo("***.456.789-**");
        assertThat(revealed.deliveryAddress()).isEqualTo("Rua das Flores, 123 - Centro");
    }

    @Test
    @DisplayName("outro tenant nao decifra o snapshot (a AAD amarra tenant e campo)")
    void anotherTenantCannotDecrypt() {
        BillSnapshotService service = service();
        BillEntity bill = bill();
        service.captureBilledCustomer(bill, CUSTOMER_A, snapshotA());

        BillEntity foreign = bill();
        foreign.setTenantId(OTHER_TENANT);
        foreign.setBilledCustomerId(CUSTOMER_A);
        foreign.setBilledCustomerSnapshot(bill.getBilledCustomerSnapshot());
        assertThatThrownBy(() -> service.revealBilledCustomer(foreign))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("conta sem titular (unidade sem usuario vigente): snapshot nulo, reveal nulo, sem nome impresso")
    void billWithoutHolderHasNoSnapshot() {
        BillSnapshotService service = service();
        BillEntity bill = bill();

        assertThat(service.revealBilledCustomer(bill)).isNull();
        assertThatThrownBy(() -> service.printedHolderName(bill, HolderNameDisplay.USER_HOLDER_ONLY, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("documento sem mascara nao entra no snapshot")
    void unmaskedDocumentIsRefused() {
        assertThatThrownBy(() -> new BilledCustomerSnapshot("Maria da Silva", "123.456.789-09", "Rua das Flores, 123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mascarado");
    }

    @Test
    @DisplayName("parameter_version_ids em coluna propria: ida e volta dos ids lidos na emissao")
    void parameterVersionIdsRoundTrip() {
        BillSnapshotService service = service();
        BillEntity bill = bill();
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

        service.captureParameterVersions(bill, ids);
        assertThat(bill.getParameterVersionIds()).contains(ids.get(0).toString(), ids.get(1).toString());
        assertThat(service.parameterVersionIds(bill)).containsExactlyElementsOf(ids);
        assertThat(service.parameterVersionIds(bill())).isEmpty();
    }

    @Test
    @DisplayName("o nome impresso sai do snapshot da emissao, nao de um cadastro posterior")
    void printedNameComesFromTheSnapshot() {
        BillSnapshotService service = service();
        BillEntity bill = bill();
        service.captureBilledCustomer(bill, CUSTOMER_A, snapshotA());

        assertThat(service.printedHolderName(bill, HolderNameDisplay.USER_HOLDER_ONLY, "Proprietario Atual"))
                .isEqualTo("Maria da Silva");
        assertThat(service.printedHolderName(bill, HolderNameDisplay.USER_HOLDER_AND_OWNER, "Proprietario Atual"))
                .isEqualTo("Maria da Silva / Proprietario Atual");
    }
}
