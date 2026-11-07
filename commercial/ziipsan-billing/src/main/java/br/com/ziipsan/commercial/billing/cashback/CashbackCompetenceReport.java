package br.com.ziipsan.commercial.billing.cashback;

import br.com.ziipsan.commercial.billing.math.Money;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Relatório por competência do cashback (P2.34) e o registro do efeito
 * tributário por competência para o pleito de reequilíbrio contratual
 * (LC 214/2025, arts. 373 a 377). Classe pura sobre as devoluções
 * registradas: o relatório diz, por competência (mês de referência da
 * conta), quanto foi devolvido <b>por domicílio</b> e o <b>total</b>; o
 * efeito tributário é a mesma agregação por competência, no formato da
 * tabela {@code cashback_tax_effects} da V4_12.
 *
 * <p>A <b>simulação</b> tarifária do reequilíbrio é da P2.29 — aqui só o
 * registro do efeito, como manda o aceite.
 *
 * <p>Os textos de {@link CashbackOpenPoints} acompanham o relatório: a
 * ativação do {@code tax_event_date} (conferência da LC 227/2026) e o ponto
 * do parecer tributário (autarquia) são declarados, nunca omitidos.
 */
public final class CashbackCompetenceReport {

    /** Linha do relatório: a devolução de um domicílio na competência. */
    public record LinhaDomicilio(String domicileKeyHash, YearMonth assessmentMonth,
                                 BigDecimal cbsDevolvida, BigDecimal ibsDevolvida, BigDecimal totalDevolvido) {
    }

    /** Relatório de uma competência: linhas por domicílio + totais. */
    public record RelatorioCompetencia(YearMonth competence, List<LinhaDomicilio> linhas,
                                       BigDecimal totalCbs, BigDecimal totalIbs, BigDecimal totalGeral,
                                       List<String> notas) {
        public RelatorioCompetencia {
            linhas = List.copyOf(linhas);
            notas = List.copyOf(notas);
        }
    }

    /** Efeito tributário de uma competência (linha de {@code cashback_tax_effects}). */
    public record EfeitoTributario(YearMonth competence, BigDecimal totalCbsDevolvido,
                                   BigDecimal totalIbsDevolvido, BigDecimal totalDevolvido, int devolucoes) {
    }

    private CashbackCompetenceReport() {
    }

    /** Relatório da competência: só entram as devoluções cuja conta é dessa competência. */
    public static RelatorioCompetencia relatorio(YearMonth competence,
                                                 List<CashbackStore.DevolucaoRegistrada> devolucoes) {
        Objects.requireNonNull(competence, "Competência é obrigatória");
        Objects.requireNonNull(devolucoes, "Lista de devoluções é obrigatória");
        List<LinhaDomicilio> linhas = new ArrayList<>();
        BigDecimal totalCbs = Money.ZERO;
        BigDecimal totalIbs = Money.ZERO;
        for (CashbackStore.DevolucaoRegistrada d : devolucoes) {
            if (!YearMonth.from(d.billReferenceMonth()).equals(competence)) {
                continue;
            }
            linhas.add(new LinhaDomicilio(d.domicileKeyHash(), d.assessmentMonth(),
                    d.cbsReturnAmount(), d.ibsReturnAmount(), d.totalReturnAmount()));
            totalCbs = totalCbs.add(d.cbsReturnAmount());
            totalIbs = totalIbs.add(d.ibsReturnAmount());
        }
        linhas.sort(Comparator.comparing(LinhaDomicilio::domicileKeyHash));
        return new RelatorioCompetencia(competence, linhas, totalCbs, totalIbs, totalCbs.add(totalIbs),
                List.of(CashbackOpenPoints.ATIVACAO_TAX_EVENT_DATE_PENDENTE,
                        CashbackOpenPoints.AUTARQUIA_CONTRIBUINTE_PENDENTE));
    }

    /** Efeito tributário por competência, para o registro do pleito de reequilíbrio (arts. 373 a 377). */
    public static List<EfeitoTributario> efeitosPorCompetencia(List<CashbackStore.DevolucaoRegistrada> devolucoes) {
        Objects.requireNonNull(devolucoes, "Lista de devoluções é obrigatória");
        Map<YearMonth, List<CashbackStore.DevolucaoRegistrada>> porCompetencia = new LinkedHashMap<>();
        for (CashbackStore.DevolucaoRegistrada d : devolucoes) {
            porCompetencia.computeIfAbsent(YearMonth.from(d.billReferenceMonth()), k -> new ArrayList<>()).add(d);
        }
        List<EfeitoTributario> efeitos = new ArrayList<>();
        for (Map.Entry<YearMonth, List<CashbackStore.DevolucaoRegistrada>> e : porCompetencia.entrySet()) {
            BigDecimal cbs = Money.ZERO;
            BigDecimal ibs = Money.ZERO;
            for (CashbackStore.DevolucaoRegistrada d : e.getValue()) {
                cbs = cbs.add(d.cbsReturnAmount());
                ibs = ibs.add(d.ibsReturnAmount());
            }
            efeitos.add(new EfeitoTributario(e.getKey(), cbs, ibs, cbs.add(ibs), e.getValue().size()));
        }
        efeitos.sort(Comparator.comparing(EfeitoTributario::competence));
        return List.copyOf(efeitos);
    }
}
