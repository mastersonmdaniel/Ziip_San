package br.com.ziipsan.platform.field;

/**
 * Estado do aparelho do app de campo (core V11, ED-18; ADR-0030). Os nomes sao os do
 * mapa de migrations do plano v3.0 para ACTIVE/BLOCKED/RETIRED; PENDING_APPROVAL e o
 * estado de nascimento (aparelho registrado ainda nao aprovado nao sincroniza).
 */
public enum AppDeviceStatus {
    PENDING_APPROVAL,
    ACTIVE,
    BLOCKED,
    RETIRED;

    /** So ACTIVE aprovado sincroniza; todo o resto e recusado pelo portao de sincronizacao. */
    public boolean canSync() {
        return this == ACTIVE;
    }
}
