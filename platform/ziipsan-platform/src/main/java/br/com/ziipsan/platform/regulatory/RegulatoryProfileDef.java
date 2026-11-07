package br.com.ziipsan.platform.regulatory;

import br.com.ziipsan.core.json.Json;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Definição de um perfil regulatório de fábrica (P2.31): a norma de condições
 * gerais de uma ERI, numa vigência, com os valores das chaves dos grupos G1,
 * G5, G6 e G9 e das bifurcações de PARAMETROS-DO-TENANT.
 *
 * <p>Cada valor carrega o ato e o artigo de origem, a base de contagem do
 * prazo (quando a chave é um prazo) e a marca de confirmação. Valor que o
 * mapa de ERIs ({@code docs/normas/partes/04}) não confirmou no texto lido
 * nasce {@link Confirmation#REQUIRES_TENANT_VALUE}, com {@code valueJson}
 * nulo: ele NUNCA recebe um número de fábrica inventado, e a ativação do
 * perfil exige que o tenant o informe pelo envelope da P0.19.
 *
 * <p>Regra local que contraria lei federal não vira valor: ela entra em
 * {@link ProfileDivergence} e o valor efetivo carregado é o piso federal
 * (ver {@code RegulatoryLoadGuard}).
 */
public record RegulatoryProfileDef(
        String profileKey,
        String agencyCode,
        String agencyName,
        String generalConditionsAct,
        LocalDate validFrom,
        LocalDate validTo,
        List<ProfileValue> values,
        List<ProfileDivergence> divergences,
        Map<String, AnaAdoption> anaAdoption,
        List<NormativeFact> normativeFacts,
        String notes) {

    public RegulatoryProfileDef {
        if (profileKey == null || profileKey.isBlank()) {
            throw new IllegalArgumentException("profileKey é obrigatória");
        }
        if (agencyCode == null || agencyCode.isBlank()) {
            throw new IllegalArgumentException("agencyCode é obrigatório");
        }
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo anterior a validFrom no perfil " + profileKey);
        }
        values = List.copyOf(values);
        divergences = List.copyOf(divergences);
        anaAdoption = Map.copyOf(anaAdoption);
        normativeFacts = List.copyOf(normativeFacts);
    }

    /** Um valor de chave do perfil, com a procedência completa. */
    public record ProfileValue(
            String definitionKey,
            String valueJson,
            String sourceAct,
            String sourceArticle,
            DeadlineBasis deadlineBasis,
            Confirmation confirmation,
            String note) {

        public ProfileValue {
            if (definitionKey == null || definitionKey.isBlank()) {
                throw new IllegalArgumentException("definitionKey é obrigatória");
            }
            if (confirmation == null) {
                throw new IllegalArgumentException("confirmation é obrigatória para " + definitionKey);
            }
            if (confirmation == Confirmation.REQUIRES_TENANT_VALUE && valueJson != null) {
                throw new IllegalArgumentException("Valor que exige o tenant não pode trazer valor de "
                        + "fábrica: " + definitionKey);
            }
            if (confirmation == Confirmation.CONFIRMED && valueJson == null) {
                throw new IllegalArgumentException("Valor confirmado sem conteúdo: " + definitionKey
                        + " (ausência confirmada usa CONFIRMED_ABSENT)");
            }
        }

        public boolean hasFactoryValue() {
            return confirmation == Confirmation.CONFIRMED;
        }
    }

    /** Base de contagem de um prazo do perfil (mapa de ERIs §3, achado 6). */
    public enum DeadlineBasis {
        CALENDAR_DAYS,
        BUSINESS_DAYS,
        /** A norma da ERI não define (ex.: AGIR, RN 001/2013). */
        NOT_DEFINED,
        NOT_APPLICABLE
    }

    /** Marca de confirmação da leitura do texto da norma (mapa de ERIs: C / NC). */
    public enum Confirmation {
        /** Confirmado no texto lido, com artigo. */
        CONFIRMED,
        /** Confirmada a AUSÊNCIA da regra no texto lido (ex.: ARESC sem teto de idade da dívida). */
        CONFIRMED_ABSENT,
        /** Não confirmado no texto lido: sem valor de fábrica; a ativação exige o valor do tenant. */
        REQUIRES_TENANT_VALUE
    }

    /**
     * Adoção de uma NR da ANA pela ERI (mapa de ERIs §5). A chave do mapa
     * é o número da NR ({@code NR11}); a ligação ao catálogo da P1.34
     * (DAD-28) é pelo {@code cert_standard.code} — o slug
     * {@code catalog_key} do JSON de normas, ex.: NR 11 =
     * {@code ana-nr-11-res-230-2024} — resolvido por
     * {@link AnaNormCatalogLink} e gravado como {@code catalogCode} em
     * cada entrada do JSON de {@code reg.ana_nr_adoption}.
     */
    public record AnaAdoption(String status, List<String> acts) {
        public AnaAdoption {
            acts = acts == null ? List.of() : List.copyOf(acts);
        }
    }

    /** Fato normativo que consta do perfil sem ser um valor de parâmetro. */
    public record NormativeFact(String code, LocalDate date, String source) {
    }

    /**
     * Regra local recusada na carga por contrariar lei federal, exibida ao
     * tenant como divergência (mapa de ERIs §3, achados 3 e 7; §6, item 5).
     */
    public record ProfileDivergence(
            String ruleCode,
            String localRule,
            String localSource,
            String federalNorm,
            Resolution resolution,
            String note) {

        public enum Resolution {
            /** Vale o piso federal; a regra local fica só registrada. */
            FEDERAL_FLOOR_APPLIED,
            /** A regra local é recusada e nenhum valor a substitui. */
            RULE_REJECTED
        }
    }

    /** Valor de uma chave, ou {@code null} se o perfil não a define. */
    public ProfileValue value(String definitionKey) {
        for (ProfileValue v : values) {
            if (v.definitionKey().equals(definitionKey)) {
                return v;
            }
        }
        return null;
    }

    /** A vigência cobre a data (relógio injetável: a troca de perfil é testada com data simulada). */
    public boolean covers(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date é obrigatória");
        }
        if (validFrom != null && date.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !date.isAfter(validTo);
    }

    public static Builder builder(String profileKey, String agencyCode, String agencyName,
                                  String generalConditionsAct, LocalDate validFrom, LocalDate validTo) {
        return new Builder(profileKey, agencyCode, agencyName, generalConditionsAct, validFrom, validTo);
    }

    /**
     * Montagem do perfil. As chamadas {@code value}, {@code adoption},
     * {@code fact} e {@code divergence} ficam uma por linha, em formato
     * regular: o gerador {@code scripts/regulatory/generate-regulatory-profile-catalog.py}
     * lê este arquivo e produz a {@code R__regulatory_profile_catalog.sql}
     * da mesma fonte (precedente da P1.34: a R__ não se edita à mão).
     */
    public static final class Builder {
        private final String profileKey;
        private final String agencyCode;
        private final String agencyName;
        private final String generalConditionsAct;
        private final LocalDate validFrom;
        private final LocalDate validTo;
        private final List<ProfileValue> values = new ArrayList<>();
        private final List<ProfileDivergence> divergences = new ArrayList<>();
        private final Map<String, AnaAdoption> anaAdoption = new LinkedHashMap<>();
        private final List<NormativeFact> normativeFacts = new ArrayList<>();
        private String notes;

        private Builder(String profileKey, String agencyCode, String agencyName,
                        String generalConditionsAct, LocalDate validFrom, LocalDate validTo) {
            this.profileKey = profileKey;
            this.agencyCode = agencyCode;
            this.agencyName = agencyName;
            this.generalConditionsAct = generalConditionsAct;
            this.validFrom = validFrom;
            this.validTo = validTo;
        }

        public Builder value(String definitionKey, String valueJson, String sourceAct, String sourceArticle,
                             DeadlineBasis deadlineBasis, Confirmation confirmation, String note) {
            values.add(new ProfileValue(definitionKey, valueJson, sourceAct, sourceArticle,
                    deadlineBasis, confirmation, note));
            return this;
        }

        public Builder adoption(String nr, String status, String acts) {
            List<String> list = new ArrayList<>();
            if (acts != null && !acts.isBlank()) {
                for (String act : acts.split(";")) {
                    list.add(act.trim());
                }
            }
            anaAdoption.put(nr, new AnaAdoption(status, list));
            return this;
        }

        public Builder fact(String code, LocalDate date, String source) {
            normativeFacts.add(new NormativeFact(code, date, source));
            return this;
        }

        public Builder divergence(String ruleCode, String localRule, String localSource, String federalNorm,
                                  ProfileDivergence.Resolution resolution, String note) {
            divergences.add(new ProfileDivergence(ruleCode, localRule, localSource, federalNorm,
                    resolution, note));
            return this;
        }

        public Builder note(String notes) {
            this.notes = notes;
            return this;
        }

        public RegulatoryProfileDef build() {
            List<ProfileValue> all = new ArrayList<>(values);
            if (!anaAdoption.isEmpty()) {
                Map<String, Object> adoptionJson = new LinkedHashMap<>();
                anaAdoption.forEach((nr, adoption) -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("status", adoption.status());
                    entry.put("acts", adoption.acts());
                    // Ligação DAD-28: o code real do catálogo da P1.34
                    // (cert_standard.code = catalog_key = id slug do JSON de
                    // normas), resolvido por AnaNormCatalogLink. Sem código
                    // conhecido a entrada fica sem o campo — e a validação
                    // de ligação no aceite recusa a NR fora do mapa.
                    String catalogCode = AnaNormCatalogLink.catalogCode(nr);
                    if (catalogCode != null) {
                        entry.put("catalogCode", catalogCode);
                    }
                    adoptionJson.put(nr, entry);
                });
                all.add(new ProfileValue("reg.ana_nr_adoption", Json.write(adoptionJson),
                        "Mapa de ERIs (docs/normas/partes/04), §5 e anexos A/B/F", null,
                        DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED,
                        "Atos de adoção das NR da ANA; cada NR é ligada ao catálogo de normas "
                                + "da P1.34 pelo cert_standard.code em catalogCode (DAD-28), "
                                + "validado no aceite da ativação."));
            }
            if (!normativeFacts.isEmpty()) {
                List<Object> factsJson = new ArrayList<>();
                for (NormativeFact fact : normativeFacts) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("code", fact.code());
                    entry.put("date", String.valueOf(fact.date()));
                    entry.put("source", fact.source());
                    factsJson.add(entry);
                }
                all.add(new ProfileValue("reg.normative_facts", Json.write(factsJson),
                        "Res. ANA 230/2024 (NR 11)", "art. 74, §3º",
                        DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null));
            }
            return new RegulatoryProfileDef(profileKey, agencyCode, agencyName, generalConditionsAct,
                    validFrom, validTo, all, divergences, anaAdoption, normativeFacts, notes);
        }
    }
}
