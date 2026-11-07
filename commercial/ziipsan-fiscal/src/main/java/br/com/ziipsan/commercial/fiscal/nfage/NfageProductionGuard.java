package br.com.ziipsan.commercial.fiscal.nfage;

import java.util.Objects;

/**
 * Trava tripla de produção do documento fiscal (dicionário DAD-18: {@code environment}
 * com trava tripla), como regra pura testável. Ambiente de produção
 * ({@link Ambiente#PRODUCTION}) só é permitido quando as TRÊS condições valem juntas:
 *
 * <ol>
 *   <li>habilitação explícita de produção registrada para o emitente/tenant
 *       (não é default, não é inferida do ambiente);</li>
 *   <li>{@code tpAmb} coerente com o ambiente: produção = 1, homologação = 2;</li>
 *   <li>contexto NÃO é somente-teste ({@code testOnly == false}: um contexto marcado
 *       como de teste nunca emite em produção, mesmo com habilitação).</li>
 * </ol>
 *
 * <p>Homologação ({@code tpAmb=2}) não exige habilitação de produção — é o ambiente do
 * aceite externo da P2.6 — mas exige o {@code tpAmb} coerente. Qualquer incoerência é
 * {@link IllegalStateException} explícita antes de qualquer escrita ou transmissão:
 * a trava falha fechada, como no espelho de plataforma da V10_1_1.
 */
public final class NfageProductionGuard {

    /** Ambientes do dicionário fiscal (V4_5), nos mesmos nomes do espelho V10_1_1. */
    public enum Ambiente { SIMULATED, HOMOLOGATION, PRODUCTION }

    /**
     * @param ambiente                    ambiente pretendido da emissão
     * @param tpAmb                       tipo de ambiente do leiaute (1 = produção, 2 = homologação)
     * @param habilitacaoProducaoExplicita habilitação de produção registrada (só relevante em produção)
     * @param contextoSomenteTeste        contexto marcado test-only (nunca produz em produção)
     */
    public record ContextoAmbiente(Ambiente ambiente, Integer tpAmb,
                                   boolean habilitacaoProducaoExplicita, boolean contextoSomenteTeste) {
    }

    private NfageProductionGuard() {
    }

    /**
     * @throws IllegalStateException se o ambiente for incoerente ou a trava tripla de
     *                               produção não estiver integralmente satisfeita
     */
    public static void exigirPermitido(ContextoAmbiente ctx) {
        Objects.requireNonNull(ctx, "Contexto de ambiente é obrigatório");
        Objects.requireNonNull(ctx.ambiente(), "Ambiente da emissão é obrigatório");
        switch (ctx.ambiente()) {
            case PRODUCTION -> {
                if (!Integer.valueOf(1).equals(ctx.tpAmb())) {
                    throw new IllegalStateException(
                            "Ambiente PRODUCTION exige tpAmb=1 (recebido: " + ctx.tpAmb() + ")");
                }
                if (!ctx.habilitacaoProducaoExplicita()) {
                    throw new IllegalStateException(
                            "Ambiente PRODUCTION exige habilitação explícita de produção do emitente/tenant");
                }
                if (ctx.contextoSomenteTeste()) {
                    throw new IllegalStateException(
                            "Contexto somente-teste (test-only) nunca emite em PRODUCTION");
                }
            }
            case HOMOLOGATION -> {
                if (!Integer.valueOf(2).equals(ctx.tpAmb())) {
                    throw new IllegalStateException(
                            "Ambiente HOMOLOGATION exige tpAmb=2 (recebido: " + ctx.tpAmb() + ")");
                }
            }
            case SIMULATED -> {
                if (ctx.tpAmb() != null && !Integer.valueOf(2).equals(ctx.tpAmb())) {
                    throw new IllegalStateException(
                            "Ambiente SIMULATED não aceita tpAmb de produção (recebido: " + ctx.tpAmb() + ")");
                }
            }
        }
    }
}
