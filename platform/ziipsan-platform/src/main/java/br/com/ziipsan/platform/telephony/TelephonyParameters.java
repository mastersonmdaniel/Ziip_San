package br.com.ziipsan.platform.telephony;

/**
 * Chaves de parametro propostas pela ED-44 (catalogo do core na integracao;
 * NENHUM valor de fabrica inventado — ligado/desligado e politica do tenant):
 * telephony.screen_pop_enabled, telephony.ura_enabled,
 * telephony.ura_client (JSON clientId -> referencia de cofre).
 */
public final class TelephonyParameters {
    private TelephonyParameters() {}
    public static final String SCREEN_POP_ENABLED = "telephony.screen_pop_enabled";
    public static final String URA_ENABLED = "telephony.ura_enabled";
    public static final String URA_CLIENTS = "telephony.ura_client";
}
