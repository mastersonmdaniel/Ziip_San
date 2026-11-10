package br.com.ziipsan.integration.works;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * ED-102 (GES-12, decisao 54 do dono — ambito COMPLETO): convenio ou
 * contrato de repasse federal/estadual ligado a projetos e pacotes do
 * GIS da ED-53. A ligacao ao GIS e por REFERENCIA (projectRef/packageRef
 * resolvidos pela {@link GisProjectPort}); nenhum tipo do GIS e
 * duplicado aqui — a ED-102 e a camada de convenios SOBRE a ED-53.
 *
 * <p>Nenhum numero de convenio real, concedente real ou valor legal e
 * presumido: {@code externalRef} e o identificador no ente concedente,
 * declarado na implantacao; {@code concedenteName} e texto declarado.
 */
public record FundingAgreement(
        UUID tenantId,
        UUID id,
        Kind kind,
        String externalRef,
        String concedenteName,
        BigDecimal totalValue,
        BigDecimal counterpartValue,
        LocalDate startDate,
        LocalDate endDate,
        Status status,
        Set<GisLink> gisLinks) {

    /** Natureza do instrumento: esfera (federal/estadual) x tipo. */
    public enum Kind {
        FEDERAL_CONVENIO, FEDERAL_CONTRATO_REPASSE,
        ESTADUAL_CONVENIO, ESTADUAL_CONTRATO_REPASSE
    }

    public enum Status { DRAFT, ACTIVE, SUSPENDED, CLOSED, ACCOUNT_RENDERED }

    /** Ligacao a um projeto do GIS e, opcionalmente, a um pacote dele. */
    public record GisLink(String projectRef, String packageRef) {
        public GisLink {
            if (projectRef == null || projectRef.isBlank())
                throw new IllegalArgumentException("projectRef do GIS e obrigatorio (ED-102 consome a ED-53 por referencia)");
        }
    }

    public FundingAgreement {
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio (multi-tenant)");
        Objects.requireNonNull(id, "id e obrigatorio");
        Objects.requireNonNull(kind, "kind e obrigatorio");
        if (externalRef == null || externalRef.isBlank())
            throw new IllegalArgumentException("externalRef (identificador no concedente) e obrigatorio — nenhum numero e inventado");
        if (concedenteName == null || concedenteName.isBlank())
            throw new IllegalArgumentException("concedenteName e obrigatorio");
        Objects.requireNonNull(totalValue, "totalValue e obrigatorio");
        Objects.requireNonNull(counterpartValue, "counterpartValue e obrigatorio");
        if (totalValue.signum() <= 0)
            throw new IllegalArgumentException("totalValue tem de ser positivo");
        if (counterpartValue.signum() < 0 || counterpartValue.compareTo(totalValue) > 0)
            throw new IllegalArgumentException("counterpartValue tem de estar entre 0 e o valor total do instrumento");
        Objects.requireNonNull(startDate, "startDate e obrigatoria");
        Objects.requireNonNull(endDate, "endDate e obrigatoria");
        if (endDate.isBefore(startDate))
            throw new IllegalArgumentException("endDate nao pode ser anterior a startDate (vigencia invalida)");
        Objects.requireNonNull(status, "status e obrigatorio");
        if (gisLinks == null || gisLinks.isEmpty())
            throw new IllegalArgumentException("o instrumento tem de estar ligado a ao menos um projeto do GIS (C1)");
        gisLinks = Set.copyOf(new LinkedHashSet<>(gisLinks));
    }

    /** Parcela do concedente = total - contrapartida do convenente. */
    public BigDecimal grantorValue() { return totalValue.subtract(counterpartValue); }
}
