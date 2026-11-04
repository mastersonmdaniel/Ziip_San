package br.com.ziipsan.core.protocol;

/**
 * Value of the tenant parameter {@code sac.holiday_scope}
 * (PARAMETROS-DO-TENANT, 20d §5, ATD-04; factory default NATIONAL):
 * which spheres of {@code cal.holidays} (P0.19) count in the service
 * deadline. NATIONAL counts national holidays only; NATIONAL_STATE
 * adds the state holidays of the service's state; NATIONAL_STATE_MUNICIPAL
 * adds the municipal holidays of the service's municipality as well.
 */
public enum HolidayScope {
    NATIONAL,
    NATIONAL_STATE,
    NATIONAL_STATE_MUNICIPAL
}
