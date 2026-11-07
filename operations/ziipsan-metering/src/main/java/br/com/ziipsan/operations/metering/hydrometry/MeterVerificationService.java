package br.com.ziipsan.operations.metering.hydrometry;

import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.PerformedBy;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.VerificationResult;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.VerificationType;
import br.com.ziipsan.operations.metering.hydrometry.domain.MeterVerification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Gravação de verificação metrológica (ED-14, correção B3): a próxima verificação é
 * CALCULADA aqui, em TODO caminho de escrita, por
 * {@link VerificationSchedule#nextVerificationOn} (data da verificação + intervalo do
 * tenant, padrão 7/só reduz). A entidade {@code MeterVerification} NÃO expõe setter de
 * {@code nextVerificationOn}: a data nasce no construtor a partir do valor calculado
 * por este serviço — data digitada não tem caminho de escrita.
 */
public class MeterVerificationService {

    public interface VerificationStore { void save(MeterVerification verification); }

    private final VerificationStore store;
    private final HydrometryParameters parameters;

    public MeterVerificationService(VerificationStore store, HydrometryParameters parameters) {
        this.store = store; this.parameters = parameters;
    }

    public record RecordCommand(UUID meterId, VerificationType type, LocalDate performedOn, PerformedBy performedBy,
                                String externalLabName, String certificateRef, VerificationResult result) {}

    public MeterVerification record(UUID tenantId, RecordCommand cmd) {
        if (cmd.meterId() == null) throw new IllegalArgumentException("Hidrômetro da verificação é obrigatório");
        if (cmd.type() == null) throw new IllegalArgumentException("Tipo da verificação é obrigatório (inicial, subsequente ou após reparo)");
        if (cmd.performedOn() == null) throw new IllegalArgumentException("Data da verificação é obrigatória");
        if (cmd.result() == null) throw new IllegalArgumentException("Resultado da verificação é obrigatório");
        if (cmd.performedBy() == PerformedBy.EXTERNAL_LAB
                && (isBlank(cmd.externalLabName()) || isBlank(cmd.certificateRef())))
            throw new IllegalArgumentException("Verificação por laboratório externo exige laboratório e certificado/relatório referenciados");
        int interval = parameters.verificationIntervalYears(tenantId);
        LocalDate next = VerificationSchedule.nextVerificationOn(cmd.performedOn(), interval);
        MeterVerification v = new MeterVerification(cmd.meterId(), cmd.type(), cmd.performedOn(), cmd.performedBy(),
                cmd.externalLabName(), cmd.certificateRef(), cmd.result(), next);
        v.setTenantId(tenantId);
        store.save(v);
        return v;
    }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
