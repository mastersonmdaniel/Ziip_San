package br.com.ziipsan.commercial.billing.altsolution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastro da solução individual de esgoto de um imóvel (ED-88; SG-154, item
 * crítico na POC do SAMAE): fossa séptica, sumidouro e/ou filtro, com volume,
 * última limpeza e destino do lodo.
 *
 * <p>O cadastro existe independentemente de o prestador executar o serviço
 * de esgotamento: quem decide é {@code sewer.alt_solution_service_enabled}
 * (ver {@link AltSolutionServicePlan}). Depois da ligação à rede, a solução
 * é DESATIVADA ({@link #deactivateByConnection}) — nunca apagada.
 */
public record AltSolution(UUID id, UUID propertyId, UUID consumerUnitId,
                          Set<Component> components, BigDecimal volumeLiters,
                          LocalDate lastCleaningOn, String sludgeDestination,
                          LocalDate registeredOn, Status status,
                          LocalDate deactivatedOn, UUID deactivationServiceOrderId) {

    /** Componentes da solução individual nomeados no acceptance (SG-154). */
    public enum Component { SEPTIC_TANK, SOAKAWAY, FILTER }

    public enum Status { ACTIVE, DEACTIVATED_BY_CONNECTION }

    public AltSolution {
        Objects.requireNonNull(id, "id da solucao individual e obrigatorio");
        Objects.requireNonNull(propertyId, "imovel (propertyId) e obrigatorio");
        Objects.requireNonNull(consumerUnitId, "unidade consumidora e obrigatoria");
        if (components == null || components.isEmpty())
            throw new IllegalArgumentException("solucao individual exige ao menos um componente "
                    + "(fossa septica, sumidouro ou filtro — SG-154)");
        components = Set.copyOf(components);
        Objects.requireNonNull(volumeLiters, "volume da solucao e obrigatorio (SG-154)");
        if (volumeLiters.signum() <= 0)
            throw new IllegalArgumentException("volume da solucao deve ser positivo: " + volumeLiters);
        if (sludgeDestination == null || sludgeDestination.isBlank())
            throw new IllegalArgumentException("destino do lodo e obrigatorio (SG-154)");
        Objects.requireNonNull(registeredOn, "data do cadastro e obrigatoria");
        // NOTA: nao ha invariante "ultima limpeza <= cadastro" no registro em
        // geral — depois de um esgotamento executado (withCleaningOn), a
        // ultima limpeza e naturalmente POSTERIOR ao cadastro. A trava
        // "limpeza declarada no cadastro nao pode ser futura em relacao a ele"
        // vale so no snapshot inicial e fica na fabrica register().
        Objects.requireNonNull(status, "situacao da solucao e obrigatoria");
        if (status == Status.ACTIVE && (deactivatedOn != null || deactivationServiceOrderId != null))
            throw new IllegalArgumentException("solucao ATIVA nao pode ter data/OS de desativacao");
        if (status == Status.DEACTIVATED_BY_CONNECTION && (deactivatedOn == null || deactivationServiceOrderId == null))
            throw new IllegalArgumentException("solucao desativada exige data e OS da ligacao que a desativou");
    }

    /** Cadastro novo, ativo, ainda sem desativação. A última limpeza DECLARADA
     *  no cadastro não pode ser posterior a ele (snapshot inicial). */
    public static AltSolution register(UUID id, UUID propertyId, UUID consumerUnitId, Set<Component> components,
                                       BigDecimal volumeLiters, LocalDate lastCleaningOn, String sludgeDestination,
                                       LocalDate registeredOn) {
        Objects.requireNonNull(registeredOn, "data do cadastro e obrigatoria");
        if (lastCleaningOn != null && lastCleaningOn.isAfter(registeredOn))
            throw new IllegalArgumentException("ultima limpeza declarada no cadastro nao pode ser posterior "
                    + "ao cadastro: " + lastCleaningOn + " > " + registeredOn);
        return new AltSolution(id, propertyId, consumerUnitId, components, volumeLiters, lastCleaningOn,
                sludgeDestination, registeredOn, Status.ACTIVE, null, null);
    }

    public boolean active() { return status == Status.ACTIVE; }

    /**
     * Desativação DEPOIS da ligação à rede (acceptance): a solução individual
     * sai de uso quando o imóvel liga; o registro permanece, com a OS da
     * ligação (ED-13) que causou a desativação.
     */
    public AltSolution deactivateByConnection(LocalDate connectionOn, UUID connectionServiceOrderId) {
        if (!active())
            throw new IllegalStateException("solucao ja desativada nao pode ser desativada de novo: " + id);
        Objects.requireNonNull(connectionOn, "data da ligacao e obrigatoria para desativar");
        Objects.requireNonNull(connectionServiceOrderId, "OS da ligacao (ED-13) e obrigatoria para desativar");
        if (connectionOn.isBefore(registeredOn))
            throw new IllegalArgumentException("ligacao nao pode desativar solucao antes do cadastro dela: "
                    + connectionOn + " < " + registeredOn);
        return new AltSolution(id, propertyId, consumerUnitId, components, volumeLiters, lastCleaningOn,
                sludgeDestination, registeredOn, Status.DEACTIVATED_BY_CONNECTION,
                connectionOn, connectionServiceOrderId);
    }

    /** Cópia com a última limpeza atualizada (após um esgotamento executado). */
    public AltSolution withCleaningOn(LocalDate cleaningOn) {
        if (!active())
            throw new IllegalStateException("solucao desativada nao recebe limpeza: " + id);
        Objects.requireNonNull(cleaningOn, "data da limpeza e obrigatoria");
        if (cleaningOn.isBefore(registeredOn))
            throw new IllegalArgumentException("limpeza anterior ao cadastro da solucao: " + cleaningOn);
        if (lastCleaningOn != null && cleaningOn.isBefore(lastCleaningOn))
            throw new IllegalArgumentException("limpeza anterior a ultima limpeza registrada: " + cleaningOn);
        return new AltSolution(id, propertyId, consumerUnitId, components, volumeLiters, cleaningOn,
                sludgeDestination, registeredOn, status, deactivatedOn, deactivationServiceOrderId);
    }
}
