package br.com.ziipsan.commercial.collection.legacydebit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Importacao conferida por matricula + condicao de mesmo banco (P0.30). */
class LegacyDebitImportTest {

    @Test
    void importadaEConferidaPorMatriculaFicaAtivaNoLegado() {
        LegacyDebitService svc = LegacyDebitFixture.publicService(
                new LegacyDebitFixture.MemStore(), new LegacyDebitFixture.MemFile());
        LegacyDebitAuthorization auth = LegacyDebitFixture.activeOptant(svc, "M100", "001", "001");
        assertThat(auth.status()).isEqualTo(LegacyDebitAuthorization.Status.ATIVA_LEGADO);
        assertThat(auth.conferredAt()).isEqualTo(LegacyDebitFixture.NOW);
    }

    @Test
    void bancoDoPagadorDiversoDoRecebedorFicaInelegivelENuncaRemete() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitAuthorization auth = LegacyDebitFixture.activeOptant(svc, "M200", "237", "001");
        assertThat(auth.status()).isEqualTo(LegacyDebitAuthorization.Status.INELEGIVEL_BANCO_DIVERSO);
        assertThatThrownBy(() -> svc.generateRemittance(LegacyDebitFixture.TENANT, "001",
                java.util.List.of(new LegacyDebitService.DebitDraft("M200",
                        new java.math.BigDecimal("10.00"), java.time.LocalDate.of(2026, 11, 20))),
                java.util.List.of(), java.util.List.of(), LegacyDebitFixture.TODAY))
                .isInstanceOf(IllegalStateException.class);
        assertThat(file.written).isEmpty();
    }

    @Test
    void matriculaDuplicadaNaImportacaoERecusada() {
        LegacyDebitService svc = LegacyDebitFixture.publicService(
                new LegacyDebitFixture.MemStore(), new LegacyDebitFixture.MemFile());
        LegacyDebitFixture.activeOptant(svc, "M300", "001", "001");
        assertThatThrownBy(() -> svc.importLegacyAuthorization(LegacyDebitFixture.TENANT, "M300",
                null, LegacyDebitFixture.PAYER_CPF, "001", "001", "LEGADO-M300B",
                LegacyDebitFixture.NOW, LegacyDebitFixture.TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("matricula");
    }

    @Test
    void conferenciaComMatriculaNaoImportadaERecusada() {
        // Correcao D2 da revisao: este cenario prova MATRICULA NAO IMPORTADA
        // (a recusa vem da busca no servico), nao divergencia - renomeado
        // para o que ele de fato prova.
        LegacyDebitService svc = LegacyDebitFixture.publicService(
                new LegacyDebitFixture.MemStore(), new LegacyDebitFixture.MemFile());
        svc.importLegacyAuthorization(LegacyDebitFixture.TENANT, "M400", null,
                LegacyDebitFixture.PAYER_CPF, "001", "001", "LEGADO-M400",
                LegacyDebitFixture.NOW, LegacyDebitFixture.TODAY);
        assertThatThrownBy(() -> svc.conferImported(LegacyDebitFixture.TENANT, "M999",
                LegacyDebitFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao importada");
    }

    @Test
    void conferenciaPorMatriculaDivergenteERecusadaNoDominio() {
        // Correcao D2 da revisao: o ramo de divergencia de
        // LegacyDebitAuthorization.confer(...) e inalcançavel via servico
        // (ele busca pela mesma matricula que confere), entao a cobertura
        // real do ramo e no nivel do dominio, sobre autorizacao importada.
        LegacyDebitService svc = LegacyDebitFixture.publicService(
                new LegacyDebitFixture.MemStore(), new LegacyDebitFixture.MemFile());
        LegacyDebitAuthorization auth = svc.importLegacyAuthorization(LegacyDebitFixture.TENANT,
                "M401", null, LegacyDebitFixture.PAYER_CPF, "001", "001", "LEGADO-M401",
                LegacyDebitFixture.NOW, LegacyDebitFixture.TODAY);
        assertThatThrownBy(() -> auth.confer("M999", LegacyDebitFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("divergente");
        // A recusa por divergencia nao confere: estado e instante intactos.
        assertThat(auth.status())
                .isEqualTo(LegacyDebitAuthorization.Status.IMPORTADA_NAO_CONFERIDA);
        assertThat(auth.conferredAt()).isNull();
    }

    @Test
    void autorizacaoNovaPorFebraban150ERecusadaSoPixAutomatico() {
        LegacyDebitService svc = LegacyDebitFixture.publicService(
                new LegacyDebitFixture.MemStore(), new LegacyDebitFixture.MemFile());
        assertThatThrownBy(svc::requestNewAuthorization)
                .isInstanceOf(br.com.ziipsan.commercial.collection.pixauto
                        .AccountDebitAuthorizationGate.LegacyDebitAuthorizationRejectedException.class)
                .hasMessageContaining("Pix Automatico");
    }
}
