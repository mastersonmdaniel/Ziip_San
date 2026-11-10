package br.com.ziipsan.intelligence.losses.dma;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import br.com.ziipsan.intelligence.losses.dma.DmaModel.DmcPeriodInputs;

/** Fixtures deterministicas do II-6 (series reais sao integracao — P-II6-SERIES). */
final class DmaFixtures {

    static final LocalDate START = LocalDate.of(2026, 10, 1);
    static final LocalDate END = LocalDate.of(2026, 10, 31);

    static BigDecimal bd(String v) { return new BigDecimal(v); }

    static DmcPeriodInputs inputs(String code, String input, String exported, String billedMetered,
                                  String billedUnmetered, String unbilled, String apparentOrNull) {
        return new DmcPeriodInputs(UUID.nameUUIDFromBytes(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                code, START, END, bd(input), bd(exported), bd(billedMetered), bd(billedUnmetered),
                bd(unbilled), apparentOrNull == null ? Optional.empty() : Optional.of(bd(apparentOrNull)));
    }

    private DmaFixtures() {}
}
