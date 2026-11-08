package br.com.ziipsan.commercial.billing.servicecatalog;

import java.time.LocalDate;

/**
 * Documento exigido por tipo de serviço ({@code service_type.required_documents}, ED-34):
 * tipo, validade (meses, nula = sem validade) e estado de verificação do documento apresentado.
 *
 * <p>Lei 13.726/2018: é proibido exigir firma reconhecida ou cópia autenticada quando a
 * autenticidade pode ser conferida de outro modo — requisito nesses termos é RECUSADO
 * na construção, não apenas desaconselhado.
 */
public record RequiredDocument(String code, String name, Integer validityMonths,
                               boolean requiresNotarizedSignature, boolean requiresAuthenticatedCopy) {
    public RequiredDocument {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo do documento e obrigatorio");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("nome do documento e obrigatorio");
        if (validityMonths != null && validityMonths <= 0) throw new IllegalArgumentException("validade deve ser positiva");
        if (requiresNotarizedSignature)
            throw new IllegalArgumentException("firma reconhecida nao pode ser exigida (Lei 13.726/2018): " + code);
        if (requiresAuthenticatedCopy)
            throw new IllegalArgumentException("copia autenticada nao pode ser exigida (Lei 13.726/2018): " + code);
    }

    public enum VerificationState { PENDING, VERIFIED, REJECTED }

    /** Documento apresentado pelo solicitante, com verificação e validade derivada da exigência. */
    public record Presented(RequiredDocument requirement, VerificationState state, LocalDate issuedOn) {
        public Presented {
            if (requirement == null) throw new IllegalArgumentException("exigencia e obrigatoria");
            if (state == null) throw new IllegalArgumentException("estado de verificacao e obrigatorio");
            if (state == VerificationState.VERIFIED && requirement.validityMonths() != null && issuedOn == null)
                throw new IllegalArgumentException("documento com validade exige data de emissao para verificar");
        }

        /** Válido na data = verificado e dentro da validade (quando a exigência tem validade). */
        public boolean validOn(LocalDate date) {
            if (state != VerificationState.VERIFIED) return false;
            if (issuedOn != null && date.isBefore(issuedOn)) return false;
            if (requirement.validityMonths() == null) return true;
            return !date.isAfter(issuedOn.plusMonths(requirement.validityMonths()));
        }
    }
}
