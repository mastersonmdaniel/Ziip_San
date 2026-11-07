package br.com.ziipsan.operations.metering.hydrometry;

import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.serviceorder.TargetVerifierRegistry;

/**
 * Hidrômetro como alvo de OS (ED-14, correção B1 da revisão): {@link TargetType#METER}
 * já existe no núcleo (P1.31/P3.4); esta classe registra no
 * {@link TargetVerifierRegistry} REAL do core um
 * {@link TargetVerifierRegistry.TargetExistenceVerifier} — a interface verdadeira
 * ({@code exists(Connection, tenantId, targetId)}), e não uma interface local.
 *
 * <p>O verificador de produção é o {@code sqlVerifier} do próprio core sobre a tabela
 * {@code meters} do schema do tenant. O registro é por instância (o registry é
 * instance-scoped, um por schema de tenant — nunca estático).
 */
public final class MeterTargetVerifier {

    private MeterTargetVerifier() {}

    /** Verificador SQL do core sobre {@code meters} (schema do tenant informado na integração). */
    public static TargetVerifierRegistry.TargetExistenceVerifier sqlVerifier(String tenantSchema) {
        return TargetVerifierRegistry.sqlVerifier(tenantSchema, "meters");
    }

    /** Registra o verificador de METER no registry da instância do núcleo de OS. */
    public static void registerInto(TargetVerifierRegistry registry, String tenantSchema) {
        if (registry == null) throw new IllegalArgumentException("TargetVerifierRegistry é obrigatório");
        registry.register(TargetType.METER, sqlVerifier(tenantSchema));
    }

    /**
     * Variante com consulta injetada (testes e integrações sem JDBC direto): devolve um
     * verificador da interface REAL, cuja existência é respondida pela porta dada.
     */
    public static TargetVerifierRegistry.TargetExistenceVerifier verifier(MeterExistence existence) {
        if (existence == null) throw new IllegalArgumentException("Porta de existência é obrigatória");
        return (connection, tenantId, targetId) -> targetId != null && existence.exists(tenantId, targetId);
    }

    @FunctionalInterface
    public interface MeterExistence { boolean exists(java.util.UUID tenantId, java.util.UUID meterId); }
}
