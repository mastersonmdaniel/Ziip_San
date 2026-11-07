package br.com.ziipsan.commercial.accounting.export;

import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator.JournalEntry;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Exportacao diaria ao sistema contabil oficial por arquivo (ED-32; o canal da
 * ED-62, quando existir, consome o mesmo conteudo). O produto NAO substitui o
 * sistema contabil oficial (ADR-0028, e): este arquivo e a integracao.
 * Formato: cabecalho + 2 linhas por lancamento (D e C), campos separados por
 * ';', sem JSON escrito a mao.
 * Cabecalho: ED32;versao=1;data=<aaaa-mm-dd>;total=<soma dos incluidos>
 * Linha: <data do fato>;<mes contabil>;<evento>;<conta>;D|C;<valor>;<historico>;<origem>
 *
 * Correcao pos-revisao (G2): a exportacao FILTRA por dia de fato — entram so
 * os lancamentos cuja factDate e o dia pedido; o total do cabecalho soma so
 * os incluidos. (Antes o "filtro" era uma tautologia e o arquivo de um dia
 * podia conter lancamentos de qualquer mes.)
 * Historico e origem sao sanitizados (';' e quebras de linha viram espaco)
 * para nao quebrar o formato (correcao pos-revisao L2).
 */
public final class DailyAccountingExport {

    private DailyAccountingExport() {}

    public static String export(List<JournalEntry> entries, LocalDate day) {
        Objects.requireNonNull(entries, "lancamentos sao obrigatorios");
        Objects.requireNonNull(day, "dia da exportacao e obrigatorio");
        var selected = entries.stream().filter(e -> day.equals(e.factDate())).toList();
        StringBuilder sb = new StringBuilder();
        java.math.BigDecimal total = selected.stream().map(JournalEntry::amount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        sb.append("ED32;versao=1;data=").append(day.format(DateTimeFormatter.ISO_LOCAL_DATE)).append(";total=").append(total.toPlainString()).append("\n");
        for (JournalEntry e : selected) {
            sb.append(e.factDate()).append(';')
              .append(e.accountingMonth()).append(';')
              .append(e.eventKey()).append(';')
              .append(e.debitAccountCode()).append(";D;").append(e.amount().toPlainString()).append(';')
              .append(sanitize(e.history())).append(';').append(sanitize(e.sourceReference())).append("\n");
            sb.append(e.factDate()).append(';')
              .append(e.accountingMonth()).append(';')
              .append(e.eventKey()).append(';')
              .append(e.creditAccountCode()).append(";C;").append(e.amount().toPlainString()).append(';')
              .append(sanitize(e.history())).append(';').append(sanitize(e.sourceReference())).append("\n");
        }
        return sb.toString();
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.replace(';', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
