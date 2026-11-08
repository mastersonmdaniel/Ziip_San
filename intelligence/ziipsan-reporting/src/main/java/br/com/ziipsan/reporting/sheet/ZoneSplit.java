package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;

/**
 * Rateio por municipio e separacao urbano/rural de uma apuracao (aceite da
 * ED-77: "rateio por municipio e separacao urbano e rural"). Os tres valores
 * sao percentuais (0..100) da mesma base; urbano + rural tem de fechar no
 * total do municipio - divergencia e recusa nomeada, nunca ajuste silencioso.
 */
public record ZoneSplit(String municipalityCode, BigDecimal totalPercent,
                        BigDecimal urbanPercent, BigDecimal ruralPercent) {
    public ZoneSplit {
        if (municipalityCode == null || municipalityCode.isBlank()) {
            throw new IndicatorValidationException("SHEET_MUNICIPALITY_REQUIRED",
                    "ficha sem municipio: o rateio por municipio e obrigatorio (ED-77)");
        }
        if (totalPercent == null || urbanPercent == null || ruralPercent == null) {
            throw new IndicatorValidationException("SHEET_ZONE_SPLIT_REQUIRED",
                    "ficha de " + municipalityCode + " exige total, urbano e rural (ED-77)");
        }
        if (urbanPercent.add(ruralPercent).compareTo(totalPercent) != 0) {
            throw new IndicatorValidationException("SHEET_ZONE_SPLIT_MISMATCH",
                    "urbano + rural (" + urbanPercent.add(ruralPercent).toPlainString()
                            + ") difere do total (" + totalPercent.toPlainString()
                            + ") em " + municipalityCode + ": rateio recusado");
        }
    }
}
