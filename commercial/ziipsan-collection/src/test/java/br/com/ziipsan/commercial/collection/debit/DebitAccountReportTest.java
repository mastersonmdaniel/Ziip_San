package br.com.ziipsan.commercial.collection.debit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static br.com.ziipsan.commercial.collection.debit.DebitAccountReport.Channel.FEBRABAN_150_LEGADO;
import static br.com.ziipsan.commercial.collection.debit.DebitAccountReport.Channel.PIX_AUTOMATICO;
import static br.com.ziipsan.commercial.collection.debit.DebitAccountReport.Outcome.FALHOU;
import static br.com.ziipsan.commercial.collection.debit.DebitAccountReport.Outcome.LIQUIDADO;
import static br.com.ziipsan.commercial.collection.debit.DebitRegulatoryCalendar.AgreementKind.PUBLIC_SERVICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebitAccountReportTest {

    private static DebitAccountReport.DebitEntry entry(DebitAccountReport.Channel channel,
                                                       DebitAccountReport.Outcome outcome,
                                                       String amount, LocalDate settlement) {
        return new DebitAccountReport.DebitEntry(channel, outcome, new BigDecimal(amount),
                settlement, LocalDate.of(2026, 12, 1));
    }

    @Test
    void relatorioSomaPorCanalEDesfechoComValoresCalculadosAMao() {
        var entries = List.of(
                entry(PIX_AUTOMATICO, LIQUIDADO, "100.00", LocalDate.of(2026, 12, 10)),
                entry(PIX_AUTOMATICO, LIQUIDADO, "50.00", LocalDate.of(2026, 12, 11)),
                entry(PIX_AUTOMATICO, FALHOU, "70.00", LocalDate.of(2026, 12, 12)),
                entry(FEBRABAN_150_LEGADO, LIQUIDADO, "200.00", LocalDate.of(2026, 12, 10)),
                // fora do periodo: nao entra
                entry(PIX_AUTOMATICO, LIQUIDADO, "999.00", LocalDate.of(2027, 1, 5)));
        var report = DebitAccountReport.build(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31),
                entries, 10, 4, PUBLIC_SERVICE);
        var pix = report.byChannel().get(PIX_AUTOMATICO);
        assertThat(pix.count()).isEqualTo(3);
        assertThat(pix.settledCount()).isEqualTo(2);
        assertThat(pix.settledTotal()).isEqualByComparingTo("150.00");
        assertThat(pix.failedCount()).isEqualTo(1);
        var legacy = report.byChannel().get(FEBRABAN_150_LEGADO);
        assertThat(legacy.settledTotal()).isEqualByComparingTo("200.00");
        assertThat(report.pendingConversion()).isEqualTo(6);
        assertThat(report.conversionRate()).isEqualByComparingTo("0.4000");
        assertThat(report.nextShutdown()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void periodoSemLancamentosDevolveRelatorioZerado() {
        var report = DebitAccountReport.build(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31),
                List.of(), 0, 0, PUBLIC_SERVICE);
        assertThat(report.byChannel().get(PIX_AUTOMATICO).settledTotal()).isEqualByComparingTo("0");
        assertThat(report.conversionRate()).isEqualByComparingTo("0");
    }

    @Test
    void collectionMonthForaDoDiaUmERecusado() {
        assertThatThrownBy(() -> new DebitAccountReport.DebitEntry(PIX_AUTOMATICO, LIQUIDADO,
                new BigDecimal("10.00"), LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertidosAcimaDaBaseLegadaERecusado() {
        assertThatThrownBy(() -> DebitAccountReport.build(LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31), List.of(), 3, 4, PUBLIC_SERVICE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
