package br.com.ziipsan.platform.field;

/**
 * Diretiva que o servidor devolve ao aparelho a cada contato (ED-18):
 *
 * <ul>
 *   <li>{@link #NONE} — aparelho ACTIVE: sincronizacao normal;</li>
 *   <li>{@link #RETIRE} — aparelho RETIRED: o aparelho envia as pendencias uma
 *       ultima vez e apaga os dados locais;</li>
 *   <li>{@link #BLOCK} — aparelho BLOCKED, PENDING_APPROVAL (ainda nao aprovado)
 *       ou nao registrado: a sincronizacao e recusada e o aparelho apaga os
 *       dados locais sem enviar nada.</li>
 * </ul>
 */
public enum DeviceDirective {
    NONE,
    RETIRE,
    BLOCK;

    public static DeviceDirective forStatus(AppDeviceStatus status) {
        return switch (status) {
            case ACTIVE -> NONE;
            case RETIRED -> RETIRE;
            // Pendente NAO e NONE: NONE faria o app sincronizar e limpar a rota
            // como aparelho ativo, embora o portao recuse o envio.
            case BLOCKED, PENDING_APPROVAL -> BLOCK;
        };
    }
}
