package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.ConnectionStatus;
import br.com.ziipsan.commercial.registry.domain.ConnectionStatus.SystemRole;
import br.com.ziipsan.core.api.ApiException;

/**
 * Regra pura das transições de situação da ligação (ED-13): dada a
 * operação, o papel vigente e a situação-alvo do catálogo único, decide
 * ou recusa. Sem banco; o executor de efeitos e os testes usam a mesma
 * tabela ({@link ConnectionOperation}).
 */
public final class ConnectionOperationRules {

    private ConnectionOperationRules() {
    }

    /**
     * Confere a transição. Recusas explícitas: operação que não admite
     * o tipo da ligação (PLUG é só de esgoto), situação-alvo de outro
     * tipo de ligação, situação-alvo desativada, papel-alvo diferente
     * do papel da operação e papel vigente que não admite a operação.
     *
     * @return a situação-alvo, quando a transição é válida
     */
    public static ConnectionStatus requireTransition(ConnectionOperation operation,
                                                     ConnectionStatus.Kind connectionKind,
                                                     SystemRole currentRole,
                                                     ConnectionStatus target) {
        if (operation == null || connectionKind == null || currentRole == null || target == null) {
            throw new ApiException.BadRequest("transicao-de-ligacao-incompleta",
                    "Operacao, tipo, papel vigente e situacao-alvo sao obrigatorios.");
        }
        if (!operation.allowsKind(connectionKind)) {
            throw new ApiException.BadRequest("operacao-incompativel-com-tipo",
                    "A operacao " + operation + " nao se aplica a ligacao de " + connectionKind
                            + " (aplica-se a " + operation.allowedKinds() + ").");
        }
        if (target.getKind() != connectionKind) {
            throw new ApiException.BadRequest("situacao-de-outro-tipo",
                    "Situacao " + target.getCode() + " e de " + target.getKind()
                            + ", nao de " + connectionKind + ".");
        }
        if (!target.isActive()) {
            throw new ApiException.BadRequest("situacao-desativada",
                    "A situacao da ligacao esta desativada: " + target.getCode());
        }
        if (target.getSystemRole() != operation.targetRole()) {
            throw new ApiException.Conflict("papel-alvo-incompativel",
                    "A operacao " + operation + " exige situacao-alvo de papel "
                            + operation.targetRole() + "; " + target.getCode()
                            + " tem papel " + target.getSystemRole() + ".");
        }
        if (!operation.allowedFrom().contains(currentRole)) {
            throw new ApiException.Conflict("transicao-de-ligacao-invalida",
                    "A operacao " + operation + " nao parte de uma ligacao " + currentRole
                            + " (parte de " + operation.allowedFrom() + ").");
        }
        return target;
    }

    /** Consumo mínimo do ciclo lido DA LIGAÇÃO: o flag e o volume são da situação da ligação. */
    public static int minimumBilledConsumption(ConnectionStatus connectionStatus, int measuredConsumption) {
        if (connectionStatus == null) {
            throw new IllegalArgumentException("situacao da ligacao e obrigatoria para o consumo minimo");
        }
        if (measuredConsumption < 0) {
            throw new IllegalArgumentException("Consumo medido negativo: " + measuredConsumption);
        }
        if (!connectionStatus.isMinimumConsumption()) {
            return measuredConsumption;
        }
        Integer minimum = connectionStatus.getMinimumBillingConsumption();
        if (minimum == null) {
            // O catalogo diz que ha minimo mas nao declara o valor: ausencia
            // e erro explicito, nunca um minimo presumido.
            throw new IllegalStateException("Situacao " + connectionStatus.getCode()
                    + " marca consumo minimo sem minimum_billing_consumption declarado no catalogo.");
        }
        return Math.max(measuredConsumption, minimum);
    }
}
