package br.com.ziipsan.commercial.fiscal.queue;

import br.com.ziipsan.commercial.fiscal.nfage.NfageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Fila do operador (P2.33): filtros por estado/competência/grupo, explicação e tempo de contingência. */
class FiscalQueueServiceTest {

    private static final class ReadMemoria implements FiscalDocumentReadPort {
        final List<FiscalDocumentView> docs;

        ReadMemoria(List<FiscalDocumentView> docs) {
            this.docs = docs;
        }

        @Override
        public List<FiscalDocumentView> listByTenant(UUID tenantId) {
            return docs;
        }

        @Override
        public Optional<FiscalDocumentView> findById(UUID tenantId, UUID documentId) {
            return docs.stream().filter(d -> d.id().equals(documentId)).findFirst();
        }

        @Override
        public Set<Long> usedNumbers(UUID tenantId, UUID issuerId, int serie) {
            return Set.of();
        }
    }

    private static Clock clockEm(String instante) {
        return Clock.fixed(Instant.parse(instante), ZoneOffset.UTC);
    }

    @Test
    @DisplayName("contingência não transmitida aparece com o tempo decorrido medido pelo relógio")
    void contingencyElapsedTime() {
        // Início da contingência na projeção: 2026-12-10T10:00-03:00 menos 26h
        // (FiscalQueueTestSupport) = 2026-12-09T08:00-03:00 = 11:00Z de 09/12.
        // Relógio em 2026-12-10T13:00Z -> decorrido = 26h. Conferido à mão.
        var doc = FiscalQueueTestSupport.nfage(NfageStatus.RESERVED, true, false, null,
                YearMonth.of(2026, 12), FiscalQueueTestSupport.GROUP);
        var service = new FiscalQueueService(clockEm("2026-12-10T13:00:00Z"), new ReadMemoria(List.of(doc)));
        var items = service.queue(FiscalQueueTestSupport.TENANT, FiscalQueueQuery.all());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).queueState()).isEqualTo(FiscalQueueState.CONTINGENCY);
        assertThat(items.get(0).contingencyElapsed()).contains(Duration.ofHours(26));
    }

    @Test
    @DisplayName("filtros por estado, competência e grupo se combinam; documento de outro tenant não vaza")
    void filtersCombine() {
        var rejected = FiscalQueueTestSupport.nfage(NfageStatus.REJECTED, false, false, "204",
                YearMonth.of(2026, 12), FiscalQueueTestSupport.GROUP);
        var authorizedOtherMonth = FiscalQueueTestSupport.nfage(NfageStatus.AUTHORIZED, false, false, null,
                YearMonth.of(2026, 11), FiscalQueueTestSupport.GROUP);
        var authorizedOtherGroup = FiscalQueueTestSupport.nfage(NfageStatus.AUTHORIZED, false, false, null,
                YearMonth.of(2026, 12), UUID.randomUUID());
        var foreign = new FiscalDocumentView(UUID.randomUUID(), UUID.randomUUID(), FiscalDocumentKind.NFAGE,
                NfageStatus.AUTHORIZED, false, "k", 1, 1, YearMonth.of(2026, 12),
                FiscalQueueTestSupport.GROUP, false, null, false, null, null,
                "BILL", UUID.randomUUID(), rejected.issuedAt());
        var service = new FiscalQueueService(clockEm("2026-12-10T13:00:00Z"),
                new ReadMemoria(List.of(rejected, authorizedOtherMonth, authorizedOtherGroup, foreign)));

        assertThat(service.queue(FiscalQueueTestSupport.TENANT,
                new FiscalQueueQuery(FiscalQueueState.REJECTED, null, null)))
                .extracting(i -> i.document().id()).containsExactly(rejected.id());
        assertThat(service.queue(FiscalQueueTestSupport.TENANT,
                new FiscalQueueQuery(null, YearMonth.of(2026, 12), FiscalQueueTestSupport.GROUP)))
                .extracting(i -> i.document().id()).containsExactly(rejected.id());
        // Grupo diferente + competência diferente: só o de novembro entra sem filtro de grupo.
        assertThat(service.queue(FiscalQueueTestSupport.TENANT,
                new FiscalQueueQuery(null, YearMonth.of(2026, 11), null)))
                .extracting(i -> i.document().id()).containsExactly(authorizedOtherMonth.id());
    }

    @Test
    @DisplayName("rejeitada entra com a explicação do catálogo da P2.16; VOIDED e trânsito normal ficam fora")
    void rejectedExplanationAndExclusions() {
        var rejected = FiscalQueueTestSupport.nfage(NfageStatus.REJECTED, false, false, "204",
                YearMonth.of(2026, 12), FiscalQueueTestSupport.GROUP);
        var voided = FiscalQueueTestSupport.nfage(NfageStatus.VOIDED);
        var inTransit = FiscalQueueTestSupport.nfage(NfageStatus.TRANSMITTED);
        var service = new FiscalQueueService(clockEm("2026-12-10T13:00:00Z"),
                new ReadMemoria(List.of(rejected, voided, inTransit)));
        var items = service.queue(FiscalQueueTestSupport.TENANT, FiscalQueueQuery.all());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).rejectionExplanation()).isPresent();
        assertThat(items.get(0).rejectionExplanation().get().catalogCode()).isEqualTo("nfage:204");
    }

    @Test
    @DisplayName("regressão D1: avulsa não transmitida sem início de contingência lista a fila sem exceção")
    void avulsaWithoutContingencyStartListsQueue() {
        // Projeção válida: o record só exige contingencyStartedAt com a flag de
        // contingência marcada, e o classificador ignora a flag para a avulsa —
        // avulsa não transmitida, flag desligada e instante nulo é caso legítimo.
        var avulsa = new FiscalDocumentView(UUID.randomUUID(), FiscalQueueTestSupport.TENANT,
                FiscalDocumentKind.NFSE_AVULSA, null, false, null, 1, 77,
                YearMonth.of(2026, 12), null, false, null, false, null, null,
                "SERVICE_ORDER", UUID.randomUUID(),
                java.time.OffsetDateTime.parse("2026-12-10T08:00:00-03:00"));
        var rejected = FiscalQueueTestSupport.nfage(NfageStatus.REJECTED, false, false, "204",
                YearMonth.of(2026, 12), FiscalQueueTestSupport.GROUP);
        var service = new FiscalQueueService(clockEm("2026-12-10T13:00:00Z"),
                new ReadMemoria(List.of(avulsa, rejected)));
        // A listagem inteira não pode cair por causa deste documento (NPE da revisão).
        var items = service.queue(FiscalQueueTestSupport.TENANT, FiscalQueueQuery.all());
        assertThat(items).hasSize(2);
        var avulsaItem = items.stream().filter(i -> i.document().id().equals(avulsa.id()))
                .findFirst().orElseThrow();
        assertThat(avulsaItem.queueState()).isEqualTo(FiscalQueueState.CONTINGENCY);
        // Sem instante de início, não há tempo decorrido a mostrar — nunca inventado.
        assertThat(avulsaItem.contingencyElapsed()).isEmpty();
    }
}
