package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.20 - documentos: quitacao, DANFAG marcado/marcado, XML com hash, log de download. */
class BillDocumentServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID BILL = UUID.randomUUID();
    private static final byte[] XML = "<NFe>original</NFe>".getBytes(StandardCharsets.UTF_8);

    private final List<String> downloads = new ArrayList<>();

    private BillDocumentService service(boolean paid, boolean branded, boolean tagged, String storedHash) {
        var port = new BillDocumentService.DocumentPort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return s.equals("dono"); }
            @Override public Optional<UUID> unitOfBill(UUID t, UUID b) { return Optional.of(UNIT); }
            @Override public Optional<BillDocumentService.BillDocuments> documents(UUID t, UUID b) {
                return Optional.of(new BillDocumentService.BillDocuments(paid, paid ? "QUITADA" : null,
                        new byte[]{1}, branded, tagged, XML, storedHash));
            }
        };
        return new BillDocumentService(port, (t, s, b, kind) -> downloads.add(kind));
    }

    private BillDocumentService ok() { return service(true, true, true, BillDocumentService.sha256Hex(XML)); }

    @Test void danfagComMarcaEEstruturaMarcadaBaixaERegistra() {
        assertThat(ok().danfagPdf(TENANT, "dono", BILL).kind()).isEqualTo("DANFAG_PDF");
        assertThat(downloads).containsExactly("DANFAG_PDF");
    }
    @Test void danfagSemMarcaDoTenantERecusado() {
        assertThatThrownBy(() -> service(true, false, true, BillDocumentService.sha256Hex(XML))
                .danfagPdf(TENANT, "dono", BILL)).isInstanceOf(IllegalStateException.class);
        assertThat(downloads).isEmpty();
    }
    @Test void danfagSemEstruturaMarcadaERecusado() {
        assertThatThrownBy(() -> service(true, true, false, BillDocumentService.sha256Hex(XML))
                .danfagPdf(TENANT, "dono", BILL)).isInstanceOf(IllegalStateException.class);
    }
    @Test void xmlComHashIgualAoGravadoBaixa() {
        assertThat(ok().fiscalXml(TENANT, "dono", BILL).bytes()).isEqualTo(XML);
    }
    @Test void xmlComHashDivergenteERecusado() {
        assertThatThrownBy(() -> service(true, true, true, "0".repeat(64)).fiscalXml(TENANT, "dono", BILL))
                .isInstanceOf(IllegalStateException.class);
        assertThat(downloads).isEmpty();
    }
    @Test void contaPagaTemQuitacaoEContaAbertaNao() {
        assertThat(ok().settlement(TENANT, "dono", BILL)).isEqualTo("QUITADA");
        assertThatThrownBy(() -> service(false, true, true, BillDocumentService.sha256Hex(XML))
                .settlement(TENANT, "dono", BILL)).isInstanceOf(ApiException.BadRequest.class);
    }
    @Test void unidadeDeOutroTitularNaoBaixa() {
        assertThatThrownBy(() -> ok().danfagPdf(TENANT, "outro", BILL))
                .isInstanceOf(ApiException.Forbidden.class);
    }
    @Test void contaInexistenteRespondeIgualANaoVinculada() {
        // D7: sem oraculo de existencia — tipo E mensagem identicos para
        // conta inexistente e conta existente de unidade nao vinculada.
        var semConta = new BillDocumentService(new BillDocumentService.DocumentPort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return true; }
            @Override public Optional<UUID> unitOfBill(UUID t, UUID b) { return Optional.empty(); }
            @Override public Optional<BillDocumentService.BillDocuments> documents(UUID t, UUID b) {
                return Optional.empty();
            }
        }, (t, s, b, kind) -> downloads.add(kind));
        ApiException inexistente = captura(() -> semConta.danfagPdf(TENANT, "dono", BILL));
        ApiException naoVinculada = captura(() -> ok().danfagPdf(TENANT, "outro", BILL));
        assertThat(inexistente).isInstanceOf(ApiException.Forbidden.class);
        assertThat(inexistente.getMessage()).isEqualTo(naoVinculada.getMessage());
        assertThat(downloads).isEmpty();
    }

    private ApiException captura(Runnable r) {
        try { r.run(); } catch (ApiException e) { return e; }
        throw new AssertionError("esperava ApiException");
    }
}
