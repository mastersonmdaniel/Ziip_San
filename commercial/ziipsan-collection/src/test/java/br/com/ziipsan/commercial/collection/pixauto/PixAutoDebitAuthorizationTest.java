package br.com.ziipsan.commercial.collection.pixauto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static br.com.ziipsan.commercial.collection.pixauto.PixAutoDebitFixture.AUTHORIZED_AT;
import static br.com.ziipsan.commercial.collection.pixauto.PixAutoDebitFixture.PAYER_CPF;
import static br.com.ziipsan.commercial.collection.pixauto.PixAutoDebitFixture.PROOF_HASH;
import static br.com.ziipsan.commercial.collection.pixauto.PixAutoDebitFixture.RECEIVER_CNPJ;
import static br.com.ziipsan.commercial.collection.pixauto.PixAutoDebitFixture.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P4.5 - autorizacao: jornadas 1 a 4, QR Code com primeiro pagamento imediato,
 * prova guardada e a trava de que autorizacao nova de debito em conta so
 * nasce como Pix Automatico.
 */
class PixAutoDebitAuthorizationTest {

    @Test
    void jornadasDeUmAQuatroSaoAceitas() {
        for (int journey = 1; journey <= 4; journey++) {
            var store = new PixAutoDebitFixture.InMemoryStore();
            var service = PixAutoDebitFixture.service(store, new PixAutoDebitFixture.FakePsp());
            var auth = service.authorize(PixAutoDebitFixture.request(journey, false, false, null));
            assertThat(auth.journey()).isEqualTo(journey);
            assertThat(auth.status()).isEqualTo(PixAutoDebitAuthorization.Status.ATIVA);
            assertThat(auth.pspAuthorizationId()).isEqualTo("PSP-AUTH-1");
        }
    }

    @Test
    void jornadaForaDeUmAQuatroERecusadaSemRegistrarNoPsp() {
        for (int journey : new int[]{0, 5}) {
            var psp = new PixAutoDebitFixture.FakePsp();
            var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(), psp);
            assertThatThrownBy(() -> service.authorize(
                    PixAutoDebitFixture.request(journey, false, false, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("jornada");
            assertThat(psp.calls).isEmpty();
        }
    }

    @Test
    void qrCodeComPrimeiroPagamentoImediatoEnviaAPrimeiraInstrucaoNaJornada() {
        var store = new PixAutoDebitFixture.InMemoryStore();
        var psp = new PixAutoDebitFixture.FakePsp();
        var service = PixAutoDebitFixture.service(store, psp);
        var first = PixAutoDebitFixture.draft("COB-IMEDIATA-1", "150.00", LocalDate.of(2027, 3, 1));
        var auth = service.authorize(PixAutoDebitFixture.request(2, true, true, first));
        assertThat(auth.qrCodeAuthorization()).isTrue();
        assertThat(store.instructions).hasSize(1);
        var instruction = store.instructions.values().iterator().next();
        assertThat(instruction.status()).isEqualTo(PixAutoDebitInstruction.Status.ENVIADA);
        assertThat(instruction.total()).isEqualByComparingTo("150.00");
        assertThat(instruction.notifiedAt()).isEqualTo(AUTHORIZED_AT);
        assertThat(psp.calls).containsExactly("registerAuthorization", "submitDebit:COB-IMEDIATA-1");
    }

    @Test
    void primeiroPagamentoImediatoSemQrCodeERecusado() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var first = PixAutoDebitFixture.draft("COB-X", "10.00", LocalDate.of(2027, 3, 1));
        assertThatThrownBy(() -> service.authorize(PixAutoDebitFixture.request(1, false, true, first)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QR Code");
    }

    @Test
    void primeiroPagamentoImediatoSemInstrucaoERecusado() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        assertThatThrownBy(() -> service.authorize(PixAutoDebitFixture.request(2, true, true, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primeiro pagamento");
    }

    @Test
    void autorizacaoSemProvaERecusada() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var semHash = new PixAutoDebitService.AuthorizationRequest(
                AccountDebitAuthorizationGate.DebitChannel.PIX_AUTOMATICO, TENANT, RECEIVER_CNPJ, null,
                PAYER_CPF, 1, false, null, "jornada/registro-1", AUTHORIZED_AT, true, 3, null, false, null);
        assertThatThrownBy(() -> service.authorize(semHash))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prova");
        var hashCurto = new PixAutoDebitService.AuthorizationRequest(
                AccountDebitAuthorizationGate.DebitChannel.PIX_AUTOMATICO, TENANT, RECEIVER_CNPJ, null,
                PAYER_CPF, 1, false, "abc123", "jornada/registro-1", AUTHORIZED_AT, true, 3, null, false, null);
        assertThatThrownBy(() -> service.authorize(hashCurto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prova");
        var semReferencia = new PixAutoDebitService.AuthorizationRequest(
                AccountDebitAuthorizationGate.DebitChannel.PIX_AUTOMATICO, TENANT, RECEIVER_CNPJ, null,
                PAYER_CPF, 1, false, PROOF_HASH, " ", AUTHORIZED_AT, true, 3, null, false, null);
        assertThatThrownBy(() -> service.authorize(semReferencia))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prova");
    }

    @Test
    void autorizacaoGuardadaCarregaAProva() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var auth = service.authorize(PixAutoDebitFixture.request(3, false, false, null));
        assertThat(auth.proofHash()).isEqualTo(PROOF_HASH);
        assertThat(auth.proofReference()).isEqualTo("jornada/registro-1");
        assertThat(auth.authorizedAt()).isEqualTo(AUTHORIZED_AT);
    }

    @Test
    void autorizacaoNovaPorFebraban150ERecusada() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var legado = new PixAutoDebitService.AuthorizationRequest(
                AccountDebitAuthorizationGate.DebitChannel.FEBRABAN_150_LEGADO, TENANT, RECEIVER_CNPJ, null,
                PAYER_CPF, 1, false, PROOF_HASH, "jornada/registro-1", AUTHORIZED_AT,
                true, 3, null, false, null);
        assertThatThrownBy(() -> service.authorize(legado))
                .isInstanceOf(AccountDebitAuthorizationGate.LegacyDebitAuthorizationRejectedException.class)
                .hasMessageContaining("so por Pix Automatico");
    }

    @Test
    void documentoDoPagadorInvalidoERecusado() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var req = new PixAutoDebitService.AuthorizationRequest(
                AccountDebitAuthorizationGate.DebitChannel.PIX_AUTOMATICO, TENANT, RECEIVER_CNPJ, null,
                "11111111111", 1, false, PROOF_HASH, "jornada/registro-1", AUTHORIZED_AT,
                true, 3, null, false, null);
        assertThatThrownBy(() -> service.authorize(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pagador");
    }

    @Test
    void retentativaNaoPrevistaComTetoPositivoERecusada() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var req = new PixAutoDebitService.AuthorizationRequest(
                AccountDebitAuthorizationGate.DebitChannel.PIX_AUTOMATICO, TENANT, RECEIVER_CNPJ, null,
                PAYER_CPF, 1, false, PROOF_HASH, "jornada/registro-1", AUTHORIZED_AT,
                false, 2, null, false, null);
        assertThatThrownBy(() -> service.authorize(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetries = 0");
    }
}
