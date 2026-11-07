package br.com.ziipsan.commercial.billing.cashback;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Fatos normativos do cashback de CBS/IBS (P2.34), no padrão
 * {@code NfageObrigatoriedade} da P2.6 (constantes com data e dispositivo +
 * regra pura): percentuais e vigências <b>citados no aceite</b> —
 * devolução de <b>100% da CBS</b> e de <b>20% do IBS</b> (LC 214/2025,
 * arts. 112 a 118), com a CBS sobre consumo devolvida <b>desde 01/2027</b>
 * e o IBS <b>desde 01/2029</b>, por vigência. Nenhuma outra data ou
 * percentual existe aqui: alíquota é da tabela com vigência da P2.4 e o
 * percentual de devolução é o da lei citada, não uma constante de taxa.
 */
public final class CashbackVigencia {

    /** Percentual da CBS devolvido (LC 214/2025, arts. 112 a 118). */
    public static final BigDecimal CBS_RETURN_PCT = new BigDecimal("100");

    /** Percentual do IBS devolvido (LC 214/2025, arts. 112 a 118). */
    public static final BigDecimal IBS_RETURN_PCT = new BigDecimal("20");

    /** Primeiro período de aferição com devolução de CBS: 01/2027 (fato normativo). */
    public static final YearMonth CBS_FROM = YearMonth.of(2027, 1);

    /** Primeiro período de aferição com devolução de IBS: 01/2029 (fato normativo). */
    public static final YearMonth IBS_FROM = YearMonth.of(2029, 1);

    /** Dispositivos que fixam o cashback e suas vigências (rastreabilidade normativa). */
    public static final String DISPOSITIVO = "LC 214/2025, arts. 112 a 118; Decreto 12.955/2026, art. 511";

    private CashbackVigencia() {
    }

    /** A CBS do período é devolvida? (fronteira inclusiva: 12/2026 não; 01/2027 sim.) */
    public static boolean cbsNaVigencia(YearMonth periodoAfericao) {
        Objects.requireNonNull(periodoAfericao, "Período de aferição é obrigatório");
        return !periodoAfericao.isBefore(CBS_FROM);
    }

    /** O IBS do período é devolvido? (fronteira inclusiva: 12/2028 não; 01/2029 sim.) */
    public static boolean ibsNaVigencia(YearMonth periodoAfericao) {
        Objects.requireNonNull(periodoAfericao, "Período de aferição é obrigatório");
        return !periodoAfericao.isBefore(IBS_FROM);
    }
}
