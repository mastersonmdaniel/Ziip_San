package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.domain.BillEntity;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.security.crypto.FieldCipher;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Grava e le o que a conta precisa provar depois da emissao (P2.5, DAD-15):
 * quem foi faturado e com quais versoes de parametro.
 *
 * <p><b>Cifra:</b> o snapshot vai para {@code bills.billed_customer_snapshot}
 * SOMENTE como texto cifrado do {@link FieldCipher} (AES-256-GCM, chave do
 * tenant, contexto {@value #SNAPSHOT_CONTEXT} na AAD: o ciphertext nao decifra
 * em outro tenant nem em outro campo). O FieldProtector nao e usado aqui de
 * proposito: ele protege um identificador unico (cifra + indice cego + mascara),
 * e o snapshot e um documento inteiro, sem busca por igualdade -- o documento
 * dentro dele ja entra mascarado (ver {@link BilledCustomerSnapshot}).
 *
 * <p><b>Imutabilidade pratica:</b> a captura acontece na emissao. Trocar o
 * titular da unidade depois nao chama este servico de novo para contas ja
 * emitidas, e nada aqui relê o cadastro: a leitura devolve o que foi capturado.
 * Conta sem titular (unidade sem usuario vigente, P1.41) nao chama
 * {@link #captureBilledCustomer}: os dois campos ficam NULL (o CHECK
 * chk_bills_billed_customer_pair garante o par) e a fatura fica retida.
 */
public final class BillSnapshotService {

    /** Contexto da cifra do snapshot (entra na AAD e separa este campo de qualquer outro). */
    public static final String SNAPSHOT_CONTEXT = "bills.billed_customer_snapshot";

    private final FieldCipher cipher;

    public BillSnapshotService(FieldCipher cipher) {
        this.cipher = Objects.requireNonNull(cipher, "cipher");
    }

    /**
     * Captura o titular faturado na emissao: id do cliente do vinculo
     * {@code show_on_bill} vigente + snapshot cifrado. Chamado uma vez, na
     * emissao; conta que ja tem snapshot nao e recapturada (a troca posterior
     * de titular nao reescreve a historia da conta).
     */
    public void captureBilledCustomer(BillEntity bill, UUID billedCustomerId, BilledCustomerSnapshot snapshot) {
        Objects.requireNonNull(bill, "bill");
        Objects.requireNonNull(billedCustomerId, "billedCustomerId");
        Objects.requireNonNull(snapshot, "snapshot");
        if (bill.getBilledCustomerSnapshot() != null) {
            throw new IllegalStateException(
                    "conta ja tem snapshot do titular faturado: troca de titular nao reescreve conta emitida");
        }
        UUID tenantId = requireTenant(bill);
        bill.setBilledCustomerId(billedCustomerId);
        bill.setBilledCustomerSnapshot(cipher.encrypt(tenantId, SNAPSHOT_CONTEXT, snapshot.toJson()));
    }

    /**
     * Snapshot capturado na emissao, decifrado. {@code null} quando a conta nao
     * tem titular faturado. Quem chama e responsavel por registrar o acesso ao
     * dado pessoal (LGPD), como no FieldProtector.
     */
    public BilledCustomerSnapshot revealBilledCustomer(BillEntity bill) {
        Objects.requireNonNull(bill, "bill");
        if (bill.getBilledCustomerSnapshot() == null) {
            return null;
        }
        return BilledCustomerSnapshot.fromJson(
                cipher.decrypt(requireTenant(bill), SNAPSHOT_CONTEXT, bill.getBilledCustomerSnapshot()));
    }

    /** Nome impresso da conta: o nome do snapshot (titular da emissao), nao o do cadastro atual. */
    public String printedHolderName(BillEntity bill, HolderNameDisplay display, String ownerName) {
        Objects.requireNonNull(display, "display");
        BilledCustomerSnapshot snapshot = revealBilledCustomer(bill);
        if (snapshot == null) {
            throw new IllegalStateException("conta sem titular faturado nao tem nome impresso (fatura retida)");
        }
        return display.printedName(snapshot.name(), ownerName);
    }

    /** Grava os ids das parameter_versions lidas na emissao, em coluna propria (alem do tariff_snapshot). */
    public void captureParameterVersions(BillEntity bill, List<UUID> parameterVersionIds) {
        Objects.requireNonNull(bill, "bill");
        Objects.requireNonNull(parameterVersionIds, "parameterVersionIds");
        bill.setParameterVersionIds(Json.write(parameterVersionIds.stream().map(UUID::toString).toList()));
    }

    /** Ids gravados na emissao; lista vazia quando a coluna esta vazia. */
    public List<UUID> parameterVersionIds(BillEntity bill) {
        Objects.requireNonNull(bill, "bill");
        if (bill.getParameterVersionIds() == null || bill.getParameterVersionIds().isBlank()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (JsonNode node : Json.readTree(bill.getParameterVersionIds())) {
            ids.add(UUID.fromString(node.asString()));
        }
        return List.copyOf(ids);
    }

    private static UUID requireTenant(BillEntity bill) {
        if (bill.getTenantId() == null) {
            throw new IllegalStateException("sem tenant na conta: snapshot cifrado nao pode ser processado");
        }
        return bill.getTenantId();
    }
}
