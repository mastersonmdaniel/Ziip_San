package br.com.ziipsan.intelligence.losses.dmc;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Volume autorizado NAO faturado (ED-85; OPE-05, ficha de perdas da
 * NR 9/ANA): usos operacionais, emergenciais e sociais — descarga de
 * rede, limpeza de reservatorio, combate a incendio e carro-pipa
 * social — registrados na OS operacional que os executou
 * ({@code authorized_unbilled_volume}). O volume de cada registro e
 * calculado SOMENTE pelo metodo declarado do tenant
 * ({@code loss.unbilled_estimate_method}):
 * <ul>
 *   <li>MEASURED — o volume medido informado e o volume;</li>
 *   <li>FLOW_TIME — vazao (L/s) x duracao (s), convertido a m3;</li>
 *   <li>FIXED_BY_SERVICE_TYPE — o volume fixo declarado para o tipo
 *       de servico, informado pelo chamador a partir da configuracao
 *       do tenant (nenhum fixo de fabrica existe nesta entrega).</li>
 * </ul>
 * Entrada que o metodo exige e falta = recusa nomeada, nunca zero.
 */
public final class AuthorizedUnbilledVolume {

    /** Categorias NOMEADAS no aceite (OPE-05). */
    public enum Category {
        NETWORK_FLUSHING,      // descarga de rede
        RESERVOIR_CLEANING,    // limpeza de reservatorio
        FIREFIGHTING,          // combate a incendio
        SOCIAL_TANKER          // carro-pipa social
    }

    private final UUID workOrderId;
    private final Category category;
    private final LossParameters.UnbilledEstimateMethod method;
    private final double volumeM3;

    private AuthorizedUnbilledVolume(UUID workOrderId, Category category,
                                     LossParameters.UnbilledEstimateMethod method, double volumeM3) {
        this.workOrderId = workOrderId;
        this.category = category;
        this.method = method;
        this.volumeM3 = volumeM3;
    }

    public static AuthorizedUnbilledVolume of(UUID workOrderId, Category category,
                                              LossParameters.UnbilledEstimateMethod method,
                                              Double measuredM3OrNull, Double flowLitresPerSecondOrNull,
                                              Long durationSecondsOrNull, Double fixedByServiceTypeM3OrNull) {
        Objects.requireNonNull(workOrderId, "OS operacional do uso autorizado obrigatoria");
        Objects.requireNonNull(category, "categoria do uso autorizado obrigatoria");
        Objects.requireNonNull(method, "metodo de estimativa obrigatorio (loss.unbilled_estimate_method)");
        double volume = switch (method) {
            case MEASURED -> {
                if (measuredM3OrNull == null) throw new IllegalArgumentException(
                        "metodo MEASURED exige o volume medido da OS");
                yield measuredM3OrNull;
            }
            case FLOW_TIME -> {
                if (flowLitresPerSecondOrNull == null || durationSecondsOrNull == null)
                    throw new IllegalArgumentException(
                            "metodo FLOW_TIME exige vazao (L/s) e duracao (s) da OS");
                if (flowLitresPerSecondOrNull < 0 || durationSecondsOrNull < 0)
                    throw new IllegalArgumentException("vazao/duracao negativas no metodo FLOW_TIME");
                yield flowLitresPerSecondOrNull * durationSecondsOrNull / 1000.0;
            }
            case FIXED_BY_SERVICE_TYPE -> {
                if (fixedByServiceTypeM3OrNull == null) throw new IllegalArgumentException(
                        "metodo FIXED_BY_SERVICE_TYPE exige o volume fixo declarado do tipo de servico (configuracao do tenant)");
                yield fixedByServiceTypeM3OrNull;
            }
        };
        if (volume < 0 || Double.isNaN(volume))
            throw new IllegalArgumentException("volume autorizado nao faturado negativo: " + volume);
        return new AuthorizedUnbilledVolume(workOrderId, category, method, volume);
    }

    public static double totalM3(List<AuthorizedUnbilledVolume> entries) {
        Objects.requireNonNull(entries, "registros de uso autorizado obrigatorios");
        return entries.stream().mapToDouble(AuthorizedUnbilledVolume::volumeM3).sum();
    }

    public UUID workOrderId() { return workOrderId; }
    public Category category() { return category; }
    public LossParameters.UnbilledEstimateMethod method() { return method; }
    public double volumeM3() { return volumeM3; }
}
