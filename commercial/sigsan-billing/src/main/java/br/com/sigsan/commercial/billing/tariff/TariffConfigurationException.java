package br.com.sigsan.commercial.billing.tariff;

/**
 * Tabela tarifária ausente, inconsistente ou incompleta para o cálculo pedido.
 *
 * <p>É deliberadamente uma exceção, e não um valor padrão: tarifa é política do prestador regulada
 * por agência; calcular com um valor suposto produziria uma conta errada que parece certa.
 */
public class TariffConfigurationException extends RuntimeException {

    public TariffConfigurationException(String message) {
        super(message);
    }
}
