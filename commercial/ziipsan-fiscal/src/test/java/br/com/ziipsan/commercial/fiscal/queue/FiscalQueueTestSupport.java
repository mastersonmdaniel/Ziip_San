package br.com.ziipsan.commercial.fiscal.queue;

import br.com.ziipsan.commercial.fiscal.nfage.NfageStatus;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;

/** Fábrica de projeções de documento para os testes da fila (P2.33). */
final class FiscalQueueTestSupport {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID GROUP = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private FiscalQueueTestSupport() {
    }

    static FiscalDocumentView nfage(NfageStatus status, boolean contingency, boolean cancellationPending,
                                    String rejectionCode, YearMonth competence, UUID groupId) {
        OffsetDateTime issued = OffsetDateTime.parse("2026-12-10T10:00:00-03:00");
        return new FiscalDocumentView(UUID.randomUUID(), TENANT, FiscalDocumentKind.NFAGE,
                status, false, "42261211222333000181750010000001231087654321", 1, 123,
                competence, groupId, contingency,
                contingency ? issued.minusHours(26) : null,
                cancellationPending, rejectionCode,
                rejectionCode == null ? null : "mensagem da SEFAZ",
                "BILL", UUID.randomUUID(), issued);
    }

    static FiscalDocumentView nfage(NfageStatus status) {
        return nfage(status, false, false, null, YearMonth.of(2026, 12), GROUP);
    }

    static FiscalDocumentView avulsa(boolean transmitted) {
        return new FiscalDocumentView(UUID.randomUUID(), TENANT, FiscalDocumentKind.NFSE_AVULSA,
                null, transmitted, null, 1, 77, YearMonth.of(2026, 12), null,
                !transmitted, transmitted ? null : OffsetDateTime.parse("2026-12-10T08:00:00-03:00"),
                false, null, null, "SERVICE_ORDER", UUID.randomUUID(),
                OffsetDateTime.parse("2026-12-10T08:00:00-03:00"));
    }
}
