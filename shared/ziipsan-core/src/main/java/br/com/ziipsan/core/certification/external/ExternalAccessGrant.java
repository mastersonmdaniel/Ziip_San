package br.com.ziipsan.core.certification.external;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Concessao de acesso de um observador externo (ED-75): o ato que
 * liga, para UM sujeito externo e UM tenant, o papel generalizado.
 *
 * <p>Regras do aceite, todas no construtor canonico:</p>
 * <ul>
 *   <li><b>desligado por padrao:</b> a concessao carrega o retrato do
 *       parametro de acesso ({@code reg.external_access} para o
 *       mecanismo generalizado; o auditor nasce do
 *       {@code cert.external_auditor_access} do core V3) — com o
 *       parametro desligado, a concessao NAO esta ativa
 *       ({@link #isActiveAt(Instant)} e falso), qualquer que seja a
 *       validade;</li>
 *   <li><b>expiracao obrigatoria:</b> {@code expiresAt} e obrigatorio
 *       e posterior a {@code validFrom} — concessao sem expiracao NAO
 *       se constroi, por fabrica ou por construtor;</li>
 *   <li><b>classe b, com aceite:</b> quem concede (proponente) e
 *       quem aceita sao pessoas diferentes e ambas obrigatorias;</li>
 *   <li><b>escopo por perfil:</b> o auditor declara as normas do seu
 *       escopo de evidencia (subconjunto das ligadas, conferido na
 *       leitura); o regulador NAO declara norma alguma — pacote de
 *       evidencia esta fora do escopo dele por construcao;</li>
 *   <li><b>somente leitura:</b> a concessao nao tem operacao de
 *       escrita; a tentativa de escrita e recusada no servico
 *       ({@link ExternalAccessService});</li>
 *   <li>o sujeito externo e identificado por REFERENCIA
 *       pseudonimizada (codigo), nunca por dado pessoal — nome,
 *       documento ou e-mail nao sao campos desta classe.</li>
 * </ul>
 *
 * <p>NENHUMA duracao padrao de validade e inventada: a spec nao fixa
 * prazo, logo a expiracao e sempre DECLARADA em cada concessao
 * (pendencia nomeada no relatorio da ED-75 se o dono quiser um teto
 * ou um padrao).</p>
 */
public record ExternalAccessGrant(UUID grantId, UUID tenantId, ExternalProfile profile,
                                  String externalSubjectRef, Set<CertificationStandard> evidenceStandards,
                                  UUID grantedBy, UUID acceptedBy, Instant validFrom,
                                  Instant expiresAt, boolean accessParameterEnabled) {

    /** Teto da referencia pseudonimizada do sujeito externo. */
    public static final int SUBJECT_REF_MAX = 80;

    public ExternalAccessGrant {
        Objects.requireNonNull(grantId, "grantId e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(profile, "profile (perfil por tipo do observador) e obrigatorio");
        if (externalSubjectRef == null || externalSubjectRef.isBlank()
                || externalSubjectRef.trim().length() > SUBJECT_REF_MAX) {
            throw new IllegalArgumentException("externalSubjectRef (referencia pseudonimizada "
                    + "do sujeito externo, sem dado pessoal) e obrigatoria e tem no maximo "
                    + SUBJECT_REF_MAX + " caracteres");
        }
        externalSubjectRef = externalSubjectRef.trim();
        evidenceStandards = CertificationStandard.subsetOf(evidenceStandards);
        if (profile == ExternalProfile.REGULATOR_SERVICE_OWNER && !evidenceStandards.isEmpty()) {
            throw new IllegalArgumentException("o perfil regulador/titular NAO tem escopo de "
                    + "evidencia ISO (GES-22, JOR-23): evidenceStandards deve ser vazio para "
                    + "REGULATOR_SERVICE_OWNER");
        }
        if (profile == ExternalProfile.CERTIFICATION_AUDITOR && evidenceStandards.isEmpty()) {
            throw new IllegalArgumentException("o perfil auditor de certificacao exige o "
                    + "escopo de evidencia declarado (as normas ligadas que ele pode ler)");
        }
        Objects.requireNonNull(grantedBy, "grantedBy (proponente da concessao) e obrigatorio "
                + "(classe b, com aceite)");
        Objects.requireNonNull(acceptedBy, "acceptedBy (aceitante da concessao) e obrigatorio "
                + "(classe b, com aceite)");
        if (grantedBy.equals(acceptedBy)) {
            throw new IllegalArgumentException("concessao de acesso externo exige proponente "
                    + "diferente do aceitante (classe b, com aceite): grantedBy == acceptedBy");
        }
        Objects.requireNonNull(validFrom, "validFrom e obrigatorio");
        Objects.requireNonNull(expiresAt, "expiresAt e OBRIGATORIO em todo acesso externo "
                + "(aceite da ED-75): concessao sem expiracao nao se concede");
        if (!expiresAt.isAfter(validFrom)) {
            throw new IllegalArgumentException("expiresAt deve ser posterior a validFrom: "
                    + "validade vazia ou invertida nao concede acesso");
        }
    }

    /** Concessao de auditor de certificacao, com escopo nas normas declaradas. */
    public static ExternalAccessGrant forAuditor(UUID grantId, UUID tenantId,
                                                 String externalSubjectRef,
                                                 Set<CertificationStandard> evidenceStandards,
                                                 UUID grantedBy, UUID acceptedBy,
                                                 Instant validFrom, Instant expiresAt,
                                                 boolean accessParameterEnabled) {
        return new ExternalAccessGrant(grantId, tenantId, ExternalProfile.CERTIFICATION_AUDITOR,
                externalSubjectRef, evidenceStandards, grantedBy, acceptedBy, validFrom,
                expiresAt, accessParameterEnabled);
    }

    /** Concessao de regulador/titular do servico (agencia, prefeitura), sem escopo ISO. */
    public static ExternalAccessGrant forRegulator(UUID grantId, UUID tenantId,
                                                   String externalSubjectRef,
                                                   UUID grantedBy, UUID acceptedBy,
                                                   Instant validFrom, Instant expiresAt,
                                                   boolean accessParameterEnabled) {
        return new ExternalAccessGrant(grantId, tenantId,
                ExternalProfile.REGULATOR_SERVICE_OWNER, externalSubjectRef, Set.of(),
                grantedBy, acceptedBy, validFrom, expiresAt, accessParameterEnabled);
    }

    /**
     * Ativa somente com o parametro de acesso LIGADO e dentro da
     * validade [validFrom, expiresAt). Fora da validade — inclusive
     * expirada — a concessao nao autoriza leitura alguma.
     */
    public boolean isActiveAt(Instant instant) {
        Objects.requireNonNull(instant, "instant e obrigatorio");
        return accessParameterEnabled && !instant.isBefore(validFrom) && instant.isBefore(expiresAt);
    }

    /** Verdadeiro quando a validade ja terminou no instante dado. */
    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant e obrigatorio");
        return !instant.isBefore(expiresAt);
    }
}
