package br.com.ziipsan.commercial.accounting.export;

import br.com.ziipsan.commercial.accounting.event.*;
import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DailyAccountingExportTest {
    private static JournalEntry entry(String amount, LocalDate month, LocalDate factDate, String history, String ref) {
        return new JournalEntry(AccountingEventKey.PAYMENT_RECEIVED, "1.1.1", "1.1.2", new BigDecimal(amount), month, factDate, history, Sphere.PARTICULAR, Scope.REGULATORY, null, null, ref);
    }
    @Test @DisplayName("exportacao diaria: cabecalho com total e 2 linhas (D e C) por lancamento")
    void dailyExport() {
        var e = entry("250.00", LocalDate.of(2026,3,1), LocalDate.of(2026,3,5), "Baixa conta:1", "pag:1");
        String file = DailyAccountingExport.export(List.of(e), LocalDate.of(2026,3,5));
        assertThat(file).contains("ED32;versao=1;data=2026-03-05;total=250.00");
        assertThat(file).contains("1.1.1;D;250.00").contains("1.1.2;C;250.00");
        assertThat(file.lines().count()).isEqualTo(3);
    }
    @Test @DisplayName("exportacao diaria FILTRA por dia de fato: lancamento de outro dia (mesmo mes) e de outro mes ficam fora; total so dos incluidos")
    void dailyExportFiltersByFactDate() {
        var included = entry("250.00", LocalDate.of(2026,3,1), LocalDate.of(2026,3,5), "Baixa conta:1", "pag:1");
        var otherDaySameMonth = entry("999.00", LocalDate.of(2026,3,1), LocalDate.of(2026,3,6), "Baixa conta:2", "pag:2");
        var otherMonth = entry("500.00", LocalDate.of(2026,2,1), LocalDate.of(2026,2,5), "Baixa conta:3", "pag:3");
        String file = DailyAccountingExport.export(List.of(included, otherDaySameMonth, otherMonth), LocalDate.of(2026,3,5));
        assertThat(file).contains("ED32;versao=1;data=2026-03-05;total=250.00");
        assertThat(file).contains("pag:1");
        assertThat(file).doesNotContain("999.00").doesNotContain("500.00").doesNotContain("pag:2").doesNotContain("pag:3");
        assertThat(file.lines().count()).isEqualTo(3);
    }
    @Test @DisplayName("historico com ';' e sanitizado para nao quebrar o formato")
    void semicolonInHistorySanitized() {
        var e = entry("250.00", LocalDate.of(2026,3,1), LocalDate.of(2026,3,5), "Baixa;conta:1", "pag:1");
        String file = DailyAccountingExport.export(List.of(e), LocalDate.of(2026,3,5));
        assertThat(file).doesNotContain("Baixa;conta:1");
        assertThat(file).contains("Baixa conta:1");
    }
}
