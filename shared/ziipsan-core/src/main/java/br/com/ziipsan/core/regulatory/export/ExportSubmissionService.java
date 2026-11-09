package br.com.ziipsan.core.regulatory.export;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Validacao pre-envio e registro de envio (ED-97, RAS-08), em memoria
 * (a persistencia e o SQL pendente deste pacote; a fiacao e pendencia
 * P-ED97-04).
 *
 * <p>Regras de validacao, nesta ordem: (1) pacote nao vazio; (2) campos
 * obrigatorios do leiaute presentes como chaves nas linhas agregadas
 * (linha no formato {@code chave=valor;...}) quando o leiaute esta
 * completo; (3) leiaute incompleto ([SEM FONTE]) so valida com aceite
 * expresso do tenant dos valores proprios (RAS-31). Pacote invalido e
 * RECUSADO nomeando a falha — nada e registrado.</p>
 */
public final class ExportSubmissionService {

    private final List<SubmissionRecord> records = new ArrayList<>();

    public SubmissionRecord validateAndRegister(UUID tenantId, ExportPackage pkg,
                                                boolean tenantAcceptedOwnValues, Instant now) {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(pkg); Objects.requireNonNull(now);
        AgencyExportProfile profile = pkg.profile();
        // D1 (REVISAO-ED-97): reverificacao no registro — defesa em
        // profundidade sobre a garantia do construtor canonico: pacote
        // agregado com linha nominal ou com hash divergente do conteudo
        // canonico NAO e registrado.
        if (pkg.kind() == ExportPackage.Kind.AGGREGATE_PUBLIC
                && pkg.lines().stream().anyMatch(ExportPackage::isNominalLine)) {
            throw new IllegalArgumentException(
                "linha nominal recusada na exportacao agregada publica (reverificacao no registro)");
        }
        if (!pkg.expectedSha256().equals(pkg.sha256Hex())) {
            throw new IllegalArgumentException("hash do pacote diverge do conteudo canonico (reverificacao no registro)");
        }
        if (pkg.lines().isEmpty()) {
            throw new IllegalArgumentException("pacote vazio recusado para " + profile.profileKey());
        }
        if (!profile.layoutComplete()) {
            if (!tenantAcceptedOwnValues) {
                throw new IllegalArgumentException("leiaute incompleto [SEM FONTE] exige aceite do tenant "
                        + "dos valores proprios (RAS-31): " + profile.profileKey());
            }
        } else {
            for (String req : profile.requiredFieldNames()) {
                boolean present = pkg.lines().stream().anyMatch(l -> l.contains(req + "="));
                if (!present) {
                    throw new IllegalArgumentException("campo obrigatorio ausente no pacote: " + req);
                }
            }
        }
        SubmissionRecord rec = new SubmissionRecord(UUID.randomUUID(), tenantId, profile.agencyCode(),
                profile.profileKey(), profile.layoutVersion(), pkg.periodRef(), pkg.kind(),
                pkg.sha256Hex(), SubmissionRecord.Status.GENERATED_VALIDATED, now, null);
        records.add(rec);
        return rec;
    }

    public List<SubmissionRecord> records() { return List.copyOf(records); }

    /** Evidencias de um periodo, para a agenda da ED-98 (RAS-08). */
    public List<String> evidenceRefsFor(String periodRef) {
        return records.stream().filter(r -> r.periodRef().equals(periodRef))
                .map(SubmissionRecord::evidenceRef).toList();
    }
}
