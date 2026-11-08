package br.com.ziipsan.core.certification.health45001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OsSafetyGateTest {

    private static final Iso45001Activation ON =
            Iso45001Activation.enabled("SYN-escopo", UUID.randomUUID(), UUID.randomUUID());
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID W1 = UUID.randomUUID();
    private static final UUID W2 = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2026, 2, 1);

    private static OsSafetyGate.ExecutorQualification qual(UUID worker, String code,
                                                           LocalDate until) {
        return new OsSafetyGate.ExecutorQualification(worker, code, until);
    }

    private static OsSafetyGate gate(boolean confined, boolean trench, boolean height,
                                     boolean electrical, BigDecimal depth, String analysis,
                                     String permit, List<OsSafetyGate.ExecutorQualification> ex,
                                     UUID release) {
        return OsSafetyGate.assess(ON, ORDER, "SYN-TIPO", OsSafetyGate.Channel.FIELD, confined,
                trench, height, electrical, depth, analysis,
                analysis == null ? null : UUID.randomUUID(), permit, ex, release);
    }

    @Test
    @DisplayName("tipo sem risco e executor habilitado: portão libera")
    void nonRiskyPasses() {
        OsSafetyGate g = gate(false, false, false, false, null, null, null,
                List.of(qual(W1, "SYN-GERAL", START.plusDays(30))), null);
        assertThat(g.evaluateStart(START).startable()).isTrue();
        g.requireStartable(START);
    }

    @Test
    @DisplayName("tipo de risco sem análise e sem permissão: dois bloqueios nomeados")
    void riskyWithoutStepsBlocked() {
        OsSafetyGate g = gate(true, false, false, false, null, null, null,
                List.of(qual(W1, "SYN-EC", START.plusDays(9)),
                        qual(W2, "SYN-EC", START.plusDays(9))), null);
        assertThat(g.evaluateStart(START).blockers()).containsExactly(
                "ANALISE_DE_RISCO_AUSENTE", "PERMISSAO_DE_TRABALHO_AUSENTE");
        assertThatThrownBy(() -> g.requireStartable(START))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("espaço confinado com um executor só: equipe mínima bloqueia")
    void confinedLoneWorkerBlocked() {
        OsSafetyGate g = gate(true, false, false, false, null, "SYN-AR-1", "SYN-PT-1",
                List.of(qual(W1, "SYN-EC", START.plusDays(9))), null);
        assertThat(g.evaluateStart(START).blockers())
                .containsExactly("EQUIPE_MINIMA_ESPACO_CONFINADO");
    }

    @Test
    @DisplayName("vala acima de 1,25 m sem liberação bloqueia; com liberação libera")
    void deepTrenchRelease() {
        List<OsSafetyGate.ExecutorQualification> ex =
                List.of(qual(W1, "SYN-GERAL", START.plusDays(9)));
        OsSafetyGate blocked = gate(false, true, false, false, new BigDecimal("1.80"),
                "SYN-AR-1", null, ex, null);
        assertThat(blocked.evaluateStart(START).blockers())
                .containsExactly("LIBERACAO_VALA_PROFUNDA_AUSENTE");
        OsSafetyGate ok = gate(false, true, false, false, new BigDecimal("1.80"), "SYN-AR-1",
                null, ex, UUID.randomUUID());
        assertThat(ok.evaluateStart(START).startable()).isTrue();
        OsSafetyGate shallow = gate(false, true, false, false, new BigDecimal("1.25"),
                "SYN-AR-1", null, ex, null);
        assertThat(shallow.evaluateStart(START).startable()).isTrue();
    }

    @Test
    @DisplayName("habilitação vencida na data de início bloqueia (fail-closed OPE-14)")
    void expiredQualificationBlocked() {
        OsSafetyGate g = gate(false, false, false, false, null, null, null,
                List.of(qual(W1, "SYN-GERAL", START.minusDays(1))), null);
        assertThat(g.evaluateStart(START).blockers())
                .containsExactly("HABILITACAO_VENCIDA_OU_AUSENTE:" + W1);
    }

    @Test
    @DisplayName("trabalho elétrico exige NR-10 válida de cada executor (RAS-25)")
    void electricalRequiresNr10() {
        List<OsSafetyGate.ExecutorQualification> without = List.of(
                qual(W1, "NR-10", START.plusDays(90)), qual(W2, "SYN-GERAL", START.plusDays(90)));
        OsSafetyGate g = gate(false, false, false, true, null, "SYN-AR-1", "SYN-PT-1", without,
                null);
        assertThat(g.evaluateStart(START).blockers())
                .containsExactly("HABILITACAO_ELETRICA_AUSENTE:" + W2);
        List<OsSafetyGate.ExecutorQualification> with = List.of(
                qual(W1, "NR-10", START.plusDays(90)), qual(W2, "NR-10", START.plusDays(90)));
        assertThat(gate(false, false, false, true, null, "SYN-AR-1", "SYN-PT-1", with, null)
                .evaluateStart(START).startable()).isTrue();
    }

    @Test
    @DisplayName("sem executor bloqueia; canal backoffice (ED-36) usa a mesma régua")
    void noExecutorAndBackofficeChannel() {
        OsSafetyGate g = OsSafetyGate.assess(ON, ORDER, "SYN-TIPO",
                OsSafetyGate.Channel.BACKOFFICE, true, false, false, false, null, "SYN-AR-1",
                UUID.randomUUID(), "SYN-PT-1", List.of(), null);
        assertThat(g.evaluateStart(START).blockers()).contains("SEM_EXECUTOR",
                "EQUIPE_MINIMA_ESPACO_CONFINADO");
    }

    @Test
    @DisplayName("norma desligada: a avaliação não nasce")
    void disabledRefused() {
        assertThatThrownBy(() -> OsSafetyGate.assess(Iso45001Activation.disabled(), ORDER,
                "SYN-TIPO", OsSafetyGate.Channel.FIELD, false, false, false, false, null, null,
                null, null, List.of(), null)).isInstanceOf(IllegalStateException.class);
    }
}
