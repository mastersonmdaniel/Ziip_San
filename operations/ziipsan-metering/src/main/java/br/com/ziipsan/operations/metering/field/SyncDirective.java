package br.com.ziipsan.operations.metering.field;

/**
 * Diretiva de sincronizacao vista pelo metering (espelho de
 * br.com.ziipsan.platform.field.DeviceDirective): NONE sincroniza; RETIRE envia
 * pendencias e apaga; BLOCK recusa e apaga.
 */
public enum SyncDirective {
    NONE,
    RETIRE,
    BLOCK
}
