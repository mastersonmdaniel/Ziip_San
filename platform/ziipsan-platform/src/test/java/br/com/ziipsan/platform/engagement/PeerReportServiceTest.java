package br.com.ziipsan.platform.engagement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeerReportServiceTest {

    private PeerReport generate(EngagementTestSupport.Fixture f, String own, PeerCohort cohort) {
        return f.peerService.generate(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER,
                EngagementTestSupport.UNIT, EngagementTestSupport.PERIOD, new BigDecimal(own), cohort);
    }

    /** TESTE DO ACEITE: grupo abaixo do tamanho minimo nao gera comparacao. */
    @Test
    void grupoAbaixoDoMinimoNaoGeraComparacao() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        // piso reutilizado da ED-38 = 5; coorte de 4 nao publica nada
        var r = generate(f, "10", EngagementTestSupport.cohortN(4, "12"));
        assertFalse(r.published());
        assertEquals(PeerReport.Suppression.BELOW_MIN_GROUP_SIZE, r.suppression());
        assertNull(r.cohortAverageM3());
        assertNull(r.cohortMedianM3());
        assertNull(r.position());
        // no minimo exato, publica
        assertTrue(generate(f, "10", EngagementTestSupport.cohortN(5, "12")).published());
    }

    @Test
    void publicadoExpoeSoAgregadosDaEd38() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        // coorte 8,10,12,14,16: media 12, mediana 12 (recalculo a mao)
        var r = generate(f, "10", EngagementTestSupport.cohort("8", "10", "12", "14", "16"));
        assertTrue(r.published());
        assertEquals(0, new BigDecimal("12.0000").compareTo(r.cohortAverageM3()));
        assertEquals(0, new BigDecimal("12").compareTo(r.cohortMedianM3()));
        assertEquals("BELOW_AVERAGE", r.position());
        assertEquals(5, r.minGroupSize());
        // nenhum campo do relatorio carrega valor individual da coorte:
        // media/mediana sao os unicos agregados e o consumo exposto e o proprio
        assertEquals(0, new BigDecimal("10").compareTo(r.ownConsumptionM3()));
    }

    @Test
    void moduloDesligadoSuprime() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.params.feature = false;
        var r = generate(f, "10", EngagementTestSupport.cohortN(9, "12"));
        assertFalse(r.published());
        assertEquals(PeerReport.Suppression.FEATURE_DISABLED, r.suppression());
    }

    @Test
    void semAdesaoORelatorioERecusado() {
        var f = new EngagementTestSupport.Fixture();
        var ex = assertThrows(EngagementValidationException.class,
                () -> generate(f, "10", EngagementTestSupport.cohortN(9, "12")));
        assertEquals("NOT_ENROLLED", ex.code());
    }

    @Test
    void comparativoDaEd38DesligadoSuprime() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.params.billPeer = false;
        var r = generate(f, "10", EngagementTestSupport.cohortN(9, "12"));
        assertFalse(r.published());
        assertEquals(PeerReport.Suppression.PEER_COMPARISON_DISABLED, r.suppression());
    }

    @Test
    void minimoDaEd101AbaixoDoPisoDaEd38EhRecusado() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.params.engMin = 3;
        var ex = assertThrows(EngagementValidationException.class,
                () -> generate(f, "10", EngagementTestSupport.cohortN(9, "12")));
        assertEquals("PEER_GROUP_MIN_BELOW_FLOOR", ex.code());
    }

    @Test
    void minimoEfetivoEhOMaiorEntreEd101EEd38() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.params.engMin = 8; // billMin ausente -> ED-38 usa o piso 5
        assertEquals(8, f.peerService.effectiveMinGroupSize(EngagementTestSupport.TENANT));
        assertFalse(generate(f, "10", EngagementTestSupport.cohortN(7, "12")).published());
        assertTrue(generate(f, "10", EngagementTestSupport.cohortN(8, "12")).published());
        // e no sentido inverso: o minimo da ED-38 tambem amplia o efetivo
        f.params.engMin = null;
        f.params.billMin = 10;
        assertEquals(10, f.peerService.effectiveMinGroupSize(EngagementTestSupport.TENANT));
        assertFalse(generate(f, "10", EngagementTestSupport.cohortN(9, "12")).published());
    }

    @Test
    void moduloDesligadoComMinimoAbaixoDoPisoSuprimeFeatureDisabled() {
        // Correcao D2 (sonda P3 da revisao): guardas primeiro — modulo
        // desligado + eng.peer_group_min_size=3 suprime FEATURE_DISABLED,
        // nao lanca PEER_GROUP_MIN_BELOW_FLOOR.
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.params.feature = false;
        f.params.engMin = 3;
        var r = generate(f, "10", EngagementTestSupport.cohortN(9, "12"));
        assertFalse(r.published());
        assertEquals(PeerReport.Suppression.FEATURE_DISABLED, r.suppression());
        assertNull(r.cohortAverageM3());
        assertNull(r.cohortMedianM3());
        assertNull(r.position());
    }

    @Test
    void moduloDesligadoComMinimoDaEd38AbaixoDoPisoSuprimeFeatureDisabled() {
        // Correcao D2: idem com a violacao no parametro da ED-38.
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.params.feature = false;
        f.params.billMin = 3;
        var r = generate(f, "10", EngagementTestSupport.cohortN(9, "12"));
        assertFalse(r.published());
        assertEquals(PeerReport.Suppression.FEATURE_DISABLED, r.suppression());
    }

    @Test
    void minimoDaEd38AbaixoDoPisoEhRecusaNomeadaDaEd101() {
        // Correcao D2 (sonda P2 da revisao): bill.peer_min_units=3 NAO vaza
        // IllegalStateException crua da ED-38 — vira a recusa nomeada
        // PEER_GROUP_MIN_BELOW_FLOOR da ED-101. O piso segue inviolado.
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.params.billMin = 3;
        var ex = assertThrows(EngagementValidationException.class,
                () -> generate(f, "10", EngagementTestSupport.cohortN(9, "12")));
        assertEquals("PEER_GROUP_MIN_BELOW_FLOOR", ex.code());
        // e pelo metodo publico do minimo efetivo, a mesma traducao
        var ex2 = assertThrows(EngagementValidationException.class,
                () -> f.peerService.effectiveMinGroupSize(EngagementTestSupport.TENANT));
        assertEquals("PEER_GROUP_MIN_BELOW_FLOOR", ex2.code());
    }

    @Test
    void coorteComValorNegativoEhRecusada() {
        assertThrows(EngagementValidationException.class,
                () -> EngagementTestSupport.cohort("5", "-1", "5"));
    }
}
