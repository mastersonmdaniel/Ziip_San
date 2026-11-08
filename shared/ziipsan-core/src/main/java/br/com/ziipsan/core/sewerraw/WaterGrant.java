package br.com.ziipsan.core.sewerraw;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Outorga de direito de uso de agua ligada a uma captacao de agua
 * bruta (aceite ED-80; Lei 9.433/1997). Todos os fatos sao
 * DECLARADOS a partir do ato de outorga real do tenant: numero,
 * orgao emissor, vigencia e condicionantes vem do documento — esta
 * classe nao conhece numero, prazo nem vazao de nenhuma outorga.
 *
 * <p>O dominio do corpo hidrico e declarado: para rios de dominio
 * da Uniao, o aceite nomeia a Resolucao ANA 236/2024 como referencia
 * ({@link #federalReferenceDeclared()}); para dominio estadual, a
 * referencia e a declarada no ato ({@code legalBasisRef}). Nenhuma
 * regra de conteudo dessas normas e codificada aqui alem do que o
 * aceite nomeia.
 *
 * <p>Condicionantes: lista de descricoes declaradas do ato; a
 * marcacao de cumprimento vive no servico de alertas/operacao, nao
 * se presume.
 */
public record WaterGrant(
        UUID id,
        UUID tenantId,
        String grantNumber,
        String issuingBody,
        String legalBasisRef,
        WaterDomain domain,
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal grantedFlowValue,
        String grantedFlowUnit,
        List<String> conditions) {

    /** Dominio do corpo hidrico, como declarado no ato de outorga. */
    public enum WaterDomain { UNION_FEDERAL, STATE }

    /** Referencia nomeada no aceite para rios de dominio da Uniao. */
    public static final String UNION_REFERENCE = "Resolucao ANA 236/2024";
    /** Base legal geral nomeada no aceite. */
    public static final String GENERAL_BASIS = "Lei 9.433/1997";

    public WaterGrant {
        Objects.requireNonNull(id, "id da outorga e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant da outorga e obrigatorio");
        if (grantNumber == null || grantNumber.isBlank())
            throw new IllegalArgumentException("numero do ato de outorga e obrigatorio");
        if (issuingBody == null || issuingBody.isBlank())
            throw new IllegalArgumentException("orgao emissor da outorga e obrigatorio");
        if (legalBasisRef == null || legalBasisRef.isBlank())
            throw new IllegalArgumentException("base legal declarada da outorga e obrigatoria");
        Objects.requireNonNull(domain, "dominio do corpo hidrico e obrigatorio");
        Objects.requireNonNull(validFrom, "inicio da vigencia da outorga e obrigatorio");
        Objects.requireNonNull(validTo, "fim da vigencia da outorga e obrigatorio (outorga sem vigencia declarada nao entra)");
        if (validTo.isBefore(validFrom))
            throw new IllegalArgumentException("vigencia da outorga invalida: fim anterior ao inicio");
        if ((grantedFlowValue == null) != (grantedFlowUnit == null || grantedFlowUnit.isBlank()))
            throw new IllegalArgumentException("vazao outorgada exige valor E unidade declarados, ou nenhum dos dois");
        if (grantedFlowValue != null && grantedFlowValue.signum() < 0)
            throw new IllegalArgumentException("vazao outorgada nao pode ser negativa");
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        if (conditions.stream().anyMatch(c -> c == null || c.isBlank()))
            throw new IllegalArgumentException("condicionante da outorga em branco");
    }

    public boolean validOn(LocalDate date) {
        return !date.isBefore(validFrom) && !date.isAfter(validTo);
    }

    /** Para dominio da Uniao, a referencia do aceite e a Res. ANA 236/2024 (declarada, nao presumida no conteudo). */
    public boolean federalReferenceDeclared() {
        return domain == WaterDomain.UNION_FEDERAL && legalBasisRef.contains("236/2024");
    }
}
