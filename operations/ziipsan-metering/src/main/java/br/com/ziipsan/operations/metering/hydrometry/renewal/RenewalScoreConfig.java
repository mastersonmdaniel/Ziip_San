package br.com.ziipsan.operations.metering.hydrometry.renewal;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Configuração do score de troca (II-5): TODOS os pesos, referências e
 * mapas são PARÂMETROS declarados do tenant — nenhum valor de fábrica é
 * assumido neste núcleo (pendência P-II5-PARAM da estimativa). Na
 * integração, os valores vigentes vêm dos parâmetros
 * {@code meter.renewal_*} (SQL pendente da II-5) e de
 * {@code meter.replace_by_volume_m3_by_class} (ED-14, reutilizado — não
 * duplicado); sem valor vigente, quem monta a configuração falha com erro
 * explícito, no padrão das portas de parâmetros do projeto.
 *
 * <p>Regra de forma dos pesos (declarada, para o score ser calculável à
 * mão e auditável): cada peso está em [0,1] e a soma dos quatro é
 * EXACTAMENTE 1 — sem normalização silenciosa nem arredondamento
 * escondido. O score total é a soma ponderada de componentes em [0,1],
 * logo também está em [0,1].
 */
public record RenewalScoreConfig(
        RenewalWeights weights,
        int ageReferenceYears,
        Map<String, Long> volumeThresholdM3ByClass,
        Map<String, BigDecimal> technologyScores) {

    /** Pesos dos quatro critérios do PDCA-01 (B-4/II-5): idade, volume, classe/tecnologia, qualidade da água. */
    public record RenewalWeights(BigDecimal age, BigDecimal volume,
                                 BigDecimal classTechnology, BigDecimal waterQuality) {
        public RenewalWeights {
            if (age == null || volume == null || classTechnology == null || waterQuality == null)
                throw new IllegalArgumentException("Os quatro pesos do score são obrigatórios (parâmetro meter.renewal_score_weights)");
            for (BigDecimal w : new BigDecimal[]{age, volume, classTechnology, waterQuality})
                if (w.signum() < 0 || w.compareTo(BigDecimal.ONE) > 0)
                    throw new IllegalArgumentException("Peso do score deve estar entre 0 e 1: " + w);
            BigDecimal sum = age.add(volume).add(classTechnology).add(waterQuality);
            if (sum.compareTo(BigDecimal.ONE) != 0)
                throw new IllegalArgumentException(
                    "A soma dos pesos do score deve ser exactamente 1 (regra declarada da II-5): " + sum.toPlainString());
        }
    }

    public RenewalScoreConfig {
        if (weights == null) throw new IllegalArgumentException("Pesos do score são obrigatórios");
        if (ageReferenceYears <= 0)
            throw new IllegalArgumentException(
                "Referência de idade (anos) deve ser positiva (parâmetro meter.renewal_age_reference_years): " + ageReferenceYears);
        if (volumeThresholdM3ByClass == null || volumeThresholdM3ByClass.isEmpty())
            throw new IllegalArgumentException(
                "Limiares de volume por classe sem valor (meter.replace_by_volume_m3_by_class da ED-14)");
        for (Map.Entry<String, Long> e : volumeThresholdM3ByClass.entrySet())
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue() <= 0)
                throw new IllegalArgumentException(
                    "Limiar de volume por classe deve ser positivo (classe " + e.getKey() + "): " + e.getValue());
        if (technologyScores == null || technologyScores.isEmpty())
            throw new IllegalArgumentException(
                "Mapa de pontuação por tecnologia/classe sem valor (parâmetro meter.renewal_technology_scores)");
        for (Map.Entry<String, BigDecimal> e : technologyScores.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null)
                throw new IllegalArgumentException("Pontuação de tecnologia/classe inválida para a chave " + e.getKey());
            if (e.getValue().signum() < 0 || e.getValue().compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException(
                    "Pontuação de tecnologia/classe deve estar entre 0 e 1 (chave " + e.getKey() + "): " + e.getValue());
        }
        volumeThresholdM3ByClass = Map.copyOf(volumeThresholdM3ByClass);
        technologyScores = Map.copyOf(technologyScores);
    }

    /** Limiar de volume (m³) da classe de exatidão — mesma semântica do parâmetro da ED-14. */
    public long volumeThresholdM3(int accuracyClass) {
        Long v = volumeThresholdM3ByClass.get(String.valueOf(accuracyClass));
        if (v == null) throw new IllegalArgumentException(
            "meter.replace_by_volume_m3_by_class sem limiar para a classe " + accuracyClass);
        return v;
    }

    /** Pontuação [0,1] da chave de tecnologia/classe; chave desconhecida é erro explícito, nunca 0 silencioso. */
    public BigDecimal technologyScore(String technologyKey) {
        BigDecimal v = technologyScores.get(technologyKey);
        if (v == null) throw new IllegalArgumentException(
            "meter.renewal_technology_scores sem pontuação para a tecnologia/classe " + technologyKey);
        return v;
    }
}
