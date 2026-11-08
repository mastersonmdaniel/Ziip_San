package br.com.ziipsan.core.sac.kiosk;

import br.com.ziipsan.core.parameters.MissingParameterException;

/**
 * Parametros da ED-40. NENHUM limite de espera e constante escondida no
 * codigo: os limites sao PARAMETRO do tenant cuja DEFINITION carrega o
 * valor de fabrica da Lei estadual SC 12.465/2002 (15 minutos em dia util
 * comum; 30 minutos em vespera e no dia seguinte a feriado prolongado).
 * Tenant de outro estado ou com norma propria troca o VALOR do parametro
 * — o codigo so valida. Ausencia de valor aceito e vigente e erro
 * explicito (MissingParameterException), nunca fallback silencioso,
 * seguindo o padrao de SacParameters da ED-35.
 */
public final class KioskParameters {

    public static final String KIOSK_ENABLED = "portal.kiosk_enabled";
    public static final String WAIT_LIMIT_BUSINESS_DAY = "sac.queue_wait_limit_business_day_minutes";
    public static final String WAIT_LIMIT_HOLIDAY_ADJACENT = "sac.queue_wait_limit_holiday_adjacent_minutes";

    /** Valor de FABRICA da definition: Lei SC 12.465/2002, dia util comum. */
    public static final int FACTORY_WAIT_LIMIT_BUSINESS_DAY_MINUTES = 15;
    /** Valor de FABRICA da definition: Lei SC 12.465/2002, vespera/dia seguinte a feriado prolongado. */
    public static final int FACTORY_WAIT_LIMIT_HOLIDAY_ADJACENT_MINUTES = 30;

    private KioskParameters() {}

    public static boolean kioskEnabled(Boolean raw) {
        if (raw == null) throw new MissingParameterException(KIOSK_ENABLED,
                "modo quiosque nao configurado para o tenant");
        return raw;
    }

    public static int waitLimitBusinessDayMinutes(Integer raw) {
        return positive(WAIT_LIMIT_BUSINESS_DAY, raw);
    }

    public static int waitLimitHolidayAdjacentMinutes(Integer raw) {
        return positive(WAIT_LIMIT_HOLIDAY_ADJACENT, raw);
    }

    private static int positive(String key, Integer raw) {
        if (raw == null) throw new MissingParameterException(key,
                "limite de espera nao configurado para o tenant (valor de fabrica da Lei SC 12.465/2002 consta da definition)");
        if (raw <= 0) throw new IllegalArgumentException(key + " deve ser maior que zero: " + raw);
        return raw;
    }
}
