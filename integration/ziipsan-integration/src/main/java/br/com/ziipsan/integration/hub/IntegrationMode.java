package br.com.ziipsan.integration.hub;

/**
 * P3.37: modo de operação por tenant e integração, do parâmetro
 * {@code integration.<name>.mode} (classe d): {@code SIMULATED} não toca
 * o terceiro, {@code SANDBOX} fala com a homologação do parceiro e
 * {@code PRODUCTION} fala com o ambiente real — e produção SÓ entra com
 * aceite (ver {@code IntegrationParameters.operableMode}).
 */
public enum IntegrationMode {
    SIMULATED,
    SANDBOX,
    PRODUCTION;

    /** O modo chama o terceiro de verdade (homologação ou produção)? */
    public boolean callsPartner() {
        return this != SIMULATED;
    }
}
