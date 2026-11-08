package br.com.ziipsan.core.contractor;

import br.com.ziipsan.core.certification.governance.GovernanceValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Penalidades de contrato de empreiteira (ED-37; SG-494).
 *
 * <p>REGRA DO PROJETO (brief da ED-37): o catalogo juridico de fabrica da
 * P3.31 ficou VAZIO por desenho, sem fonte — portanto NENHUMA clausula,
 * percentual ou valor padrao e criado aqui. Cada clausula e PARAMETRIZADA
 * por contrato no momento do cadastro (base de calculo, valor/percentual e
 * teto opcionais declarados por quem cadastra); sem clausula cadastrada, o
 * contrato simplesmente nao tem penalidade alguma.
 */
public final class Penalties {
    private Penalties() {}

    /** Base de calculo declarada na clausula. */
    public enum Basis {
        /** Valor fixo por ocorrencia. */
        FIXED_AMOUNT,
        /** Percentual sobre o valor global do contrato. */
        PERCENT_OF_CONTRACT,
        /** Percentual sobre o valor bruto da medicao vinculada. */
        PERCENT_OF_MEASUREMENT,
        /** Valor por dia (ex.: atraso), multiplicado pelos dias da ocorrencia. */
        PER_DAY
    }

    /**
     * Clausula penal parametrizada de UM contrato.
     *
     * @param value percentual (0..100) para bases PERCENT_*; valor monetario
     *              para FIXED_AMOUNT e PER_DAY
     * @param capOrNull teto monetario opcional declarado; nulo = sem teto
     */
    public record PenaltyClause(UUID id, UUID tenantId, UUID contractId, String code, String description,
                                Basis basis, BigDecimal value, BigDecimal capOrNull) {
        public PenaltyClause {
            if (id == null) throw new GovernanceValidationException("PENALTY_CLAUSE_ID_REQUIRED", "clausula penal exige id");
            if (tenantId == null) throw new GovernanceValidationException("TENANT_REQUIRED", "tenant obrigatorio");
            if (contractId == null) throw new GovernanceValidationException("PENALTY_CONTRACT_REQUIRED", "clausula penal exige o contrato");
            if (code == null || code.isBlank()) throw new GovernanceValidationException("PENALTY_CODE_REQUIRED", "clausula penal exige codigo declarado");
            if (description == null || description.isBlank()) throw new GovernanceValidationException("PENALTY_DESCRIPTION_REQUIRED", "clausula penal exige descricao declarada");
            if (basis == null) throw new GovernanceValidationException("PENALTY_BASIS_REQUIRED", "clausula penal exige base de calculo declarada");
            if (value == null || value.signum() <= 0) throw new GovernanceValidationException("PENALTY_VALUE_INVALID", "valor/percentual da clausula deve ser positivo");
            if ((basis == Basis.PERCENT_OF_CONTRACT || basis == Basis.PERCENT_OF_MEASUREMENT) && value.compareTo(new BigDecimal("100")) > 0)
                throw new GovernanceValidationException("PENALTY_PERCENT_RANGE", "percentual da clausula fora de 0..100");
            if (capOrNull != null && capOrNull.signum() <= 0)
                throw new GovernanceValidationException("PENALTY_CAP_INVALID", "teto da clausula, quando declarado, deve ser positivo");
        }

        /**
         * Calculo da penalidade de uma ocorrencia. Bases percentuais exigem a
         * referencia correspondente (valor do contrato / bruto da medicao);
         * PER_DAY exige os dias. Referencia ausente = recusa explicita, nunca
         * calculo silencioso sobre zero.
         */
        public BigDecimal compute(BigDecimal contractValueOrNull, BigDecimal measurementGrossOrNull, int days) {
            BigDecimal raw = switch (basis) {
                case FIXED_AMOUNT -> value;
                case PERCENT_OF_CONTRACT -> {
                    if (contractValueOrNull == null) throw new GovernanceValidationException("PENALTY_REFERENCE_MISSING", "clausula percentual sobre contrato exige o valor do contrato");
                    yield contractValueOrNull.multiply(value).movePointLeft(2);
                }
                case PERCENT_OF_MEASUREMENT -> {
                    if (measurementGrossOrNull == null) throw new GovernanceValidationException("PENALTY_REFERENCE_MISSING", "clausula percentual sobre medicao exige a medicao vinculada");
                    yield measurementGrossOrNull.multiply(value).movePointLeft(2);
                }
                case PER_DAY -> {
                    if (days <= 0) throw new GovernanceValidationException("PENALTY_DAYS_REQUIRED", "clausula por dia exige os dias da ocorrencia");
                    yield value.multiply(BigDecimal.valueOf(days));
                }
            };
            BigDecimal amount = raw.setScale(2, RoundingMode.HALF_UP);
            if (capOrNull != null && amount.compareTo(capOrNull) > 0) amount = capOrNull.setScale(2, RoundingMode.HALF_UP);
            return amount;
        }
    }

    /** Aplicacao de uma clausula a uma ocorrencia concreta (valor congelado no ato). */
    public record PenaltyApplication(UUID id, UUID tenantId, UUID contractId, UUID clauseId,
                                     UUID measurementIdOrNull, LocalDate occurredOn, String description,
                                     int days, BigDecimal amount, Status status, String waiveReasonOrNull) {
        public enum Status { APPLIED, WAIVED }

        public PenaltyApplication {
            if (id == null) throw new GovernanceValidationException("PENALTY_APPLICATION_ID_REQUIRED", "aplicacao de penalidade exige id");
            if (tenantId == null) throw new GovernanceValidationException("TENANT_REQUIRED", "tenant obrigatorio");
            if (contractId == null) throw new GovernanceValidationException("PENALTY_CONTRACT_REQUIRED", "aplicacao exige o contrato");
            if (clauseId == null) throw new GovernanceValidationException("PENALTY_CLAUSE_REQUIRED", "aplicacao exige a clausula parametrizada do contrato");
            if (occurredOn == null) throw new GovernanceValidationException("PENALTY_DATE_REQUIRED", "aplicacao exige a data da ocorrencia");
            if (description == null || description.isBlank()) throw new GovernanceValidationException("PENALTY_DESCRIPTION_REQUIRED", "aplicacao exige a descricao da ocorrencia");
            if (days < 0) throw new GovernanceValidationException("PENALTY_DAYS_INVALID", "dias da ocorrencia nao podem ser negativos");
            if (amount == null || amount.signum() < 0) throw new GovernanceValidationException("PENALTY_AMOUNT_INVALID", "valor da penalidade nao pode ser nulo/negativo");
            if (status == null) throw new GovernanceValidationException("PENALTY_STATUS_REQUIRED", "aplicacao exige estado");
            if (status == Status.WAIVED && (waiveReasonOrNull == null || waiveReasonOrNull.isBlank()))
                throw new GovernanceValidationException("PENALTY_WAIVE_REASON_REQUIRED", "penalidade relevada exige motivo declarado");
        }

        public PenaltyApplication waived(String reason) {
            if (status == Status.WAIVED)
                throw new GovernanceValidationException("PENALTY_ALREADY_WAIVED",
                        "aplicacao de penalidade ja relevada (WAIVED): relevamento nao se repete");
            return new PenaltyApplication(id, tenantId, contractId, clauseId, measurementIdOrNull, occurredOn,
                    description, days, amount, Status.WAIVED, reason);
        }
    }
}
