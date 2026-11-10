package br.com.ziipsan.operations.metering.hydrometry.renewal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Retrato de UM hidrómetro do parque para o plano de troca selectiva (II-5,
 * Onda II do PDCA-01) — entrada pura do núcleo, montada na integração a
 * partir da base viva da ED-14 ({@code Meter} + {@code MeterInstallation} +
 * {@code MeterModel} + última {@code MeterVerification}) e, para a aferição
 * pontual, da P3.28 (que NÃO é duplicada aqui: aferição a pedido e plano
 * anual de renovação são objetos distintos).
 *
 * <p>Campos e fontes na base:
 * <ul>
 *   <li>{@code meterId}/{@code meterNumber}/{@code status}/
 *       {@code totalizedVolumeM3}/{@code manufactureYear} — {@code Meter}
 *       da ED-14 (o estado espelha {@code Meter.Status});</li>
 *   <li>{@code unitId}/{@code installedOn} — {@code MeterInstallation}
 *       vigente da ED-14;</li>
 *   <li>{@code accuracyClass} — {@code MeterModel.accuracyClass} da ED-14
 *       (CHECK do SQL pendente da ED-14: 1 ou 2);</li>
 *   <li>{@code lastVerificationOn} — última {@code MeterVerification} da
 *       ED-14 (a próxima data é CALCULADA pelo relógio da II-5 sobre
 *       {@code VerificationSchedule} da ED-14, nunca lida digitada);</li>
 *   <li>{@code technologyKey} — chave de tecnologia/classe composta por quem
 *       monta o retrato (a base da ED-14 NÃO tem campo "tecnologia": tem
 *       classe de exatidão 1/2, R e classe legada A–D; a composição da chave
 *       é dado de integração, pontuada pelo mapa de
 *       {@link RenewalScoreConfig#technologyScores()} — nenhuma taxonomia é
 *       inventada neste núcleo);</li>
 *   <li>{@code waterQualityAggressiveness} — <b>opcional</b>: a base viva
 *       (ED-14/metering) NÃO expõe qualidade da água por hidrómetro
 *       (verificado por leitura directa: só amostras de supervisão de rede,
 *       sem vínculo por hidrómetro). Enquanto a integração não a expuser,
 *       o campo vem {@code null} e o critério fica inerte no score
 *       (componente 0, {@code waterQualityApplied=false}) — é o "quando a
 *       base a expuser" do PDCA-01/estimativa da II-5;</li>
 *   <li>{@code replacementCost} — custo de troca registado pelo tenant
 *       (mesma disciplina do {@code RenewalPlanCalculator} da ED-14: custo
 *       é entrada do chamador, nunca número embutido); obrigatório só no
 *       modo de restrição por orçamento do plano.</li>
 * </ul>
 */
public record MeterRenewalSnapshot(
        UUID meterId,
        String meterNumber,
        UUID unitId,
        MeterParkStatus status,
        LocalDate installedOn,
        Integer manufactureYear,
        long totalizedVolumeM3,
        int accuracyClass,
        String technologyKey,
        LocalDate lastVerificationOn,
        BigDecimal waterQualityAggressiveness,
        BigDecimal replacementCost) {

    /** Estado do hidrómetro no parque — espelha {@code Meter.Status} da ED-14. */
    public enum MeterParkStatus { AVAILABLE, INSTALLED, IN_MAINTENANCE, STOLEN, WRITTEN_OFF }

    public MeterRenewalSnapshot {
        if (meterId == null) throw new IllegalArgumentException("Hidrômetro do retrato é obrigatório");
        if (meterNumber == null || meterNumber.isBlank())
            throw new IllegalArgumentException("Número do hidrômetro é obrigatório");
        if (status == null) throw new IllegalArgumentException("Estado do hidrômetro é obrigatório");
        if (totalizedVolumeM3 < 0)
            throw new IllegalArgumentException("Volume totalizado não pode ser negativo: " + totalizedVolumeM3);
        if (accuracyClass != 1 && accuracyClass != 2)
            throw new IllegalArgumentException(
                "Classe de exatidão deve ser 1 ou 2 (MeterModel da ED-14): " + accuracyClass);
        if (technologyKey == null || technologyKey.isBlank())
            throw new IllegalArgumentException("Chave de tecnologia/classe é obrigatória");
        if (waterQualityAggressiveness != null
                && (waterQualityAggressiveness.signum() < 0
                    || waterQualityAggressiveness.compareTo(BigDecimal.ONE) > 0))
            throw new IllegalArgumentException(
                "Agressividade da qualidade da água deve estar entre 0 e 1: " + waterQualityAggressiveness);
        if (replacementCost != null && replacementCost.signum() < 0)
            throw new IllegalArgumentException("Custo de troca não pode ser negativo: " + replacementCost);
    }

    /**
     * Data-base da idade: a instalação vigente; sem instalação conhecida,
     * 1.º de janeiro do ano de fabrico (leitura declarada — o ano é o dado
     * que {@code Meter.manufactureYear} guarda). Sem nenhuma das duas, não
     * há idade calculável e o score recusa (erro explícito, sem idade
     * inventada).
     */
    public LocalDate ageBaseDate() {
        if (installedOn != null) return installedOn;
        if (manufactureYear != null) return LocalDate.of(manufactureYear, 1, 1);
        return null;
    }
}
