package br.com.ziipsan.commercial.billing.saaspricing;

import java.math.BigDecimal;

/**
 * Perfil profissional e homem-hora da planilha (aceite da ED-10): papel,
 * valor-hora e horas mensais sao dados da versao aprovada da planilha.
 * Nenhum papel nem valor-hora consta de fonte nesta entrega - tudo e
 * entrada obrigatoria, nunca tabela presumida.
 */
public record ProfessionalProfile(String role, BigDecimal hourlyRate, BigDecimal monthlyHours) {
    public ProfessionalProfile {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("papel do perfil profissional e obrigatorio");
        if (hourlyRate == null || hourlyRate.signum() < 0) throw new IllegalArgumentException("valor-hora invalido: " + role);
        if (monthlyHours == null || monthlyHours.signum() < 0) throw new IllegalArgumentException("horas mensais invalidas: " + role);
    }
    /** Custo mensal do perfil: homem-hora x horas. */
    public BigDecimal monthlyCost() { return hourlyRate.multiply(monthlyHours); }
}
