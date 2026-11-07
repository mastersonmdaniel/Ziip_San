package br.com.ziipsan.operations.metering.hydrometry;

import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.*;
import br.com.ziipsan.operations.metering.hydrometry.domain.MeterVerification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** B3: toda gravação de verificação calcula a próxima data — não existe setter para digitá-la. */
class MeterVerificationServiceTest {

    private MeterVerificationService service(int interval, List<MeterVerification> saved) {
        HydrometryParameters params = new HydrometryParameters() {
            public int verificationIntervalYears(UUID tenantId) { return interval; }
            public Map<String, Long> replaceByVolumeM3ByClass(UUID tenantId) { return Map.of(); }
            public int samplePlanPct(UUID tenantId) { return 0; }
        };
        return new MeterVerificationService(saved::add, params);
    }

    @Test @DisplayName("verificação inicial/subsequente/após reparo: next = performed + intervalo do tenant")
    void nextAlwaysCalculated() {
        List<MeterVerification> saved = new ArrayList<>();
        MeterVerificationService svc = service(7, saved);
        MeterVerification v = svc.record(UUID.randomUUID(), new MeterVerificationService.RecordCommand(
                UUID.randomUUID(), VerificationType.INITIAL, LocalDate.of(2026, 1, 15),
                PerformedBy.OWN_BENCH, null, null, VerificationResult.APPROVED));
        assertThat(v.getNextVerificationOn()).isEqualTo(LocalDate.of(2033, 1, 15));
        MeterVerification v2 = service(5, saved).record(UUID.randomUUID(), new MeterVerificationService.RecordCommand(
                UUID.randomUUID(), VerificationType.AFTER_REPAIR, LocalDate.of(2026, 1, 15),
                PerformedBy.OWN_BENCH, null, null, VerificationResult.APPROVED));
        assertThat(v2.getNextVerificationOn()).isEqualTo(LocalDate.of(2031, 1, 15));
        assertThat(saved).hasSize(2);
    }

    @Test @DisplayName("laboratório externo exige laboratório e certificado; entidade não expõe setter da próxima data")
    void externalLabAndNoSetter() {
        List<MeterVerification> saved = new ArrayList<>();
        MeterVerificationService svc = service(7, saved);
        assertThatThrownBy(() -> svc.record(UUID.randomUUID(), new MeterVerificationService.RecordCommand(
                UUID.randomUUID(), VerificationType.SUBSEQUENT, LocalDate.now(),
                PerformedBy.EXTERNAL_LAB, null, null, VerificationResult.APPROVED))).isInstanceOf(IllegalArgumentException.class);
        MeterVerification ok = svc.record(UUID.randomUUID(), new MeterVerificationService.RecordCommand(
                UUID.randomUUID(), VerificationType.SUBSEQUENT, LocalDate.of(2026, 3, 1),
                PerformedBy.EXTERNAL_LAB, "Lab Metrologia", "CERT-9", VerificationResult.APPROVED));
        assertThat(ok.getNextVerificationOn()).isEqualTo(LocalDate.of(2033, 3, 1));
        assertThatThrownBy(() -> MeterVerification.class.getMethod("setNextVerificationOn", LocalDate.class))
                .isInstanceOf(NoSuchMethodException.class);
    }
}
