package br.com.ziipsan.core.sewerraw;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Licenca ambiental ligada a captacao de agua bruta (aceite ED-80):
 * numero, orgao emissor, vigencia e condicionantes DECLARADOS a
 * partir do documento real do tenant. Nenhum prazo, numero ou
 * condicionante e conhecido por esta classe.
 */
public record EnvironmentalLicense(
        UUID id,
        UUID tenantId,
        String licenseNumber,
        String licenseKind,
        String issuingBody,
        LocalDate validFrom,
        LocalDate validTo,
        List<String> conditions) {

    public EnvironmentalLicense {
        Objects.requireNonNull(id, "id da licenca e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant da licenca e obrigatorio");
        if (licenseNumber == null || licenseNumber.isBlank())
            throw new IllegalArgumentException("numero da licenca ambiental e obrigatorio");
        if (licenseKind == null || licenseKind.isBlank())
            throw new IllegalArgumentException("tipo da licenca ambiental e obrigatorio (declarado no documento)");
        if (issuingBody == null || issuingBody.isBlank())
            throw new IllegalArgumentException("orgao emissor da licenca e obrigatorio");
        Objects.requireNonNull(validFrom, "inicio da vigencia da licenca e obrigatorio");
        Objects.requireNonNull(validTo, "fim da vigencia da licenca e obrigatorio");
        if (validTo.isBefore(validFrom))
            throw new IllegalArgumentException("vigencia da licenca invalida: fim anterior ao inicio");
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        if (conditions.stream().anyMatch(c -> c == null || c.isBlank()))
            throw new IllegalArgumentException("condicionante da licenca em branco");
    }

    public boolean validOn(LocalDate date) {
        return !date.isBefore(validFrom) && !date.isAfter(validTo);
    }
}
