package br.com.ziipsan.core.certification.env14001;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma licenca ambiental do tenant e as suas condicionantes (aceite
 * da ED-70): cada licenca declara codigo, descricao, orgao emissor
 * por REFERENCIA declarada pelo tenant (nenhum orgao, numero ou prazo
 * de fabrica existe), VALIDADE (inicio e fim), RESPONSAVEL e
 * EVIDENCIAS por tipo + referencia; cada condicionante declara
 * descricao, prazo proprio quando houver e evidencia.
 *
 * <p>O ALERTA DE VENCIMENTO e calculado, nunca gravado: {@link
 * #expiryStatus} recebe a data de referencia e a JANELA DE ALERTA EM
 * DIAS DECLARADA PELO CHAMADOR (parametro do tenant na fiacao —
 * nenhum numero de dias de fabrica e presumido aqui) e devolve
 * VALID / EXPIRING / EXPIRED. Licenca com validade invertida e
 * recusada; condicionante sem descricao e recusada.</p>
 *
 * <p>Registro da norma: a ativacao e COMPONENTE e o construtor
 * canonico exige a norma ligada (padrao ED-73/D1).</p>
 */
public record EnvironmentalLicense(Iso14001Activation activation, UUID id, UUID tenantId,
                                   String licenseCode, String description, String issuerRef,
                                   LocalDate validFrom, LocalDate validUntil, UUID responsibleId,
                                   List<EvidenceRef> evidence, List<Condition> conditions) {

    /** Referencia de evidencia: tipo + identificador, nunca o conteudo. */
    public record EvidenceRef(String kind, String ref) {
        public EvidenceRef {
            if (kind == null || kind.isBlank()) {
                throw new IllegalArgumentException("kind da evidencia e obrigatorio");
            }
            if (ref == null || ref.isBlank()) {
                throw new IllegalArgumentException("ref da evidencia e obrigatoria "
                        + "(identificador do documento/registro, sem copiar conteudo)");
            }
            kind = kind.trim();
            ref = ref.trim();
        }
    }

    /**
     * Uma condicionante da licenca: descricao declarada pelo tenant,
     * prazo proprio (nulo quando a condicionante nao tem prazo
     * separado da validade da licenca) e referencia de evidencia do
     * cumprimento (nula enquanto nao cumprida/comprovada).
     */
    public record Condition(String description, LocalDate dueDate, String evidenceRef) {
        public Condition {
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("descricao da condicionante e obrigatoria");
            }
            description = description.trim();
            if (evidenceRef != null) {
                evidenceRef = evidenceRef.trim();
                if (evidenceRef.isBlank()) {
                    throw new IllegalArgumentException("evidenceRef da condicionante, quando "
                            + "declarada, nao pode ser vazia");
                }
            }
        }

        /** Condicionante com evidencia de cumprimento declarada. */
        public boolean evidenced() {
            return evidenceRef != null;
        }
    }

    /** Situacao de vencimento calculada para uma data de referencia. */
    public enum ExpiryStatus {
        VALID, EXPIRING, EXPIRED
    }

    public EnvironmentalLicense {
        Objects.requireNonNull(activation, "activation e obrigatoria (componente do registro)");
        activation.requireEnabled();
        Objects.requireNonNull(id, "id da licenca e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId da licenca e obrigatorio");
        if (licenseCode == null || licenseCode.isBlank()) {
            throw new IllegalArgumentException("licenseCode e obrigatorio "
                    + "(codigo/numero declarado pelo tenant)");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description da licenca e obrigatoria");
        }
        if (issuerRef == null || issuerRef.isBlank()) {
            throw new IllegalArgumentException("issuerRef e obrigatorio (orgao emissor declarado "
                    + "pelo tenant, por referencia — nenhum orgao de fabrica existe)");
        }
        Objects.requireNonNull(validFrom, "validFrom (inicio da validade) e obrigatorio");
        Objects.requireNonNull(validUntil, "validUntil (fim da validade) e obrigatorio");
        if (validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("validade invertida: validUntil anterior a validFrom");
        }
        Objects.requireNonNull(responsibleId, "responsibleId (responsavel pela licenca) e obrigatorio");
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        licenseCode = licenseCode.trim();
        description = description.trim();
        issuerRef = issuerRef.trim();
    }

    /**
     * Alerta de vencimento para {@code asOf}: EXPIRED se a validade ja
     * passou; EXPIRING se o fim da validade esta dentro da janela de
     * alerta declarada pelo chamador; VALID nos demais casos.
     *
     * @param alertWindowDays janela de alerta em dias, declarada pelo
     *                        tenant/chamador (sem valor de fabrica)
     */
    public ExpiryStatus expiryStatus(LocalDate asOf, int alertWindowDays) {
        Objects.requireNonNull(asOf, "asOf (data de referencia do alerta) e obrigatorio");
        if (alertWindowDays < 0) {
            throw new IllegalArgumentException("alertWindowDays nao pode ser negativo: " + alertWindowDays);
        }
        if (asOf.isAfter(validUntil)) return ExpiryStatus.EXPIRED;
        if (!validUntil.isAfter(asOf.plusDays(alertWindowDays))) return ExpiryStatus.EXPIRING;
        return ExpiryStatus.VALID;
    }

    /** Verdadeiro quando a licenca esta vencida na data de referencia. */
    public boolean expired(LocalDate asOf) {
        return expiryStatus(asOf, 0) == ExpiryStatus.EXPIRED;
    }

    /** Condicionantes ainda sem evidencia de cumprimento declarada. */
    public List<Condition> conditionsWithoutEvidence() {
        return conditions.stream().filter(c -> !c.evidenced()).toList();
    }
}
