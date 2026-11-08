package br.com.ziipsan.core.certification.health45001;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma linha do INVENTÁRIO DE PERIGOS E RISCOS por atividade e local
 * (ED-71), com escopo por área da P3.30 ({@code orgUnitId} = unidade
 * organizacional da V1_8, conferido por FK composta no SQL pendente).
 *
 * <p>SEM FÁBRICA PRESUMIDA: nenhum perigo, atividade ou local é semeado
 * por este pacote. Atividade, local, perigo e controles são DECLARADOS
 * pelo tenant no seu catálogo de SST (o mesmo catálogo que alimenta a
 * habilitação da P1.31); esta classe é só a régua do registro. A ESCALA
 * de severidade × probabilidade também é do tenant: {@code scaleMax} é
 * declarado na linha (a régua só exige 3..10 e valores dentro da escala)
 * e o limiar de ação é parâmetro de quem avalia
 * ({@link #requiresActionPlan(int)}) — nenhuma matriz de risco pronta é
 * presumida aqui.
 *
 * <p>CAMPO DE RISCO PSICOSSOCIAL (aceite da ED-71): a categoria
 * {@link Category#PSYCHOSOCIAL} exige a lista de fatores psicossociais
 * declarados pelo tenant ({@code psychosocialFactors}, códigos/textos do
 * catálogo do tenant); fator psicossocial fora dessa categoria é
 * recusado, para o campo não virar texto livre solto.
 *
 * <p>As categorias são taxonomia PRÓPRIA DO PRODUTO (palavras do
 * produto para organizar o inventário), não texto nem estrutura da
 * ISO 45001 — nenhuma cláusula da norma é reproduzida neste pacote.
 */
public record HazardRiskEntry(Iso45001Activation activation, String activityCode,
                              String activityDescription, String locationRef, UUID orgUnitId,
                              Category category, String hazardDescription, int severity,
                              int likelihood, int scaleMax, List<String> controls,
                              List<String> psychosocialFactors, UUID assessedBy,
                              LocalDate assessedOn) {

    /** Categoria do perigo — taxonomia própria do produto, declarada pelo tenant na linha. */
    public enum Category {
        PHYSICAL, CHEMICAL, BIOLOGICAL, ERGONOMIC, MECHANICAL, ELECTRICAL, PSYCHOSOCIAL, OTHER
    }

    /** Tetos compatíveis com o SQL pendente da ED-71. */
    public static final int CODE_MAX = 60;
    public static final int LOCATION_MAX = 200;
    /** Escala mínima/máxima aceitável para a régua declarada pelo tenant. */
    public static final int SCALE_MIN = 3;
    public static final int SCALE_MAX_LIMIT = 10;

    public HazardRiskEntry {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        activityCode = requireText(activityCode, "activityCode", CODE_MAX);
        activityDescription = requireText(activityDescription, "activityDescription", 500);
        locationRef = requireText(locationRef, "locationRef", LOCATION_MAX);
        Objects.requireNonNull(orgUnitId, "orgUnitId (escopo por área da P3.30) é obrigatório");
        Objects.requireNonNull(category, "category é obrigatória");
        hazardDescription = requireText(hazardDescription, "hazardDescription", 1000);
        if (scaleMax < SCALE_MIN || scaleMax > SCALE_MAX_LIMIT) {
            throw new IllegalArgumentException("scaleMax (escala de risco declarada pelo tenant) "
                    + "deve estar entre " + SCALE_MIN + " e " + SCALE_MAX_LIMIT + ": " + scaleMax);
        }
        if (severity < 1 || severity > scaleMax) {
            throw new IllegalArgumentException("severity fora da escala declarada (1.."
                    + scaleMax + "): " + severity);
        }
        if (likelihood < 1 || likelihood > scaleMax) {
            throw new IllegalArgumentException("likelihood fora da escala declarada (1.."
                    + scaleMax + "): " + likelihood);
        }
        controls = controls == null ? List.of() : List.copyOf(controls);
        if (controls.isEmpty() || controls.stream().anyMatch(c -> c == null || c.isBlank())) {
            throw new IllegalArgumentException("controls é obrigatório: perigo inventariado sem "
                    + "nenhum controle declarado não é avaliação de risco");
        }
        psychosocialFactors = psychosocialFactors == null ? List.of()
                : List.copyOf(psychosocialFactors);
        if (psychosocialFactors.stream().anyMatch(f -> f == null || f.isBlank())) {
            throw new IllegalArgumentException("psychosocialFactors não aceita item em branco");
        }
        if (category == Category.PSYCHOSOCIAL && psychosocialFactors.isEmpty()) {
            throw new IllegalArgumentException("categoria PSYCHOSOCIAL exige o campo de risco "
                    + "psicossocial preenchido (fatores declarados pelo tenant)");
        }
        if (category != Category.PSYCHOSOCIAL && !psychosocialFactors.isEmpty()) {
            throw new IllegalArgumentException("psychosocialFactors só vale na categoria "
                    + "PSYCHOSOCIAL: fora dela o campo fica vazio");
        }
        Objects.requireNonNull(assessedBy, "assessedBy é obrigatório (quem avaliou)");
        Objects.requireNonNull(assessedOn, "assessedOn é obrigatório (quando avaliou)");
    }

    /** Nascimento do registro: só com a norma ligada (a ativação é componente e reconfere). */
    public static HazardRiskEntry register(Iso45001Activation activation, String activityCode,
                                           String activityDescription, String locationRef,
                                           UUID orgUnitId, Category category,
                                           String hazardDescription, int severity, int likelihood,
                                           int scaleMax, List<String> controls,
                                           List<String> psychosocialFactors, UUID assessedBy,
                                           LocalDate assessedOn) {
        return new HazardRiskEntry(activation, activityCode, activityDescription, locationRef,
                orgUnitId, category, hazardDescription, severity, likelihood, scaleMax, controls,
                psychosocialFactors, assessedBy, assessedOn);
    }

    /** Escore do risco na escala declarada: severidade × probabilidade. */
    public int riskScore() {
        return severity * likelihood;
    }

    /**
     * Se o escore atinge o limiar de ação DECLARADO por quem avalia
     * (parâmetro do tenant/serviço — esta régua não presume limiar).
     */
    public boolean requiresActionPlan(int actionThreshold) {
        if (actionThreshold < 1) {
            throw new IllegalArgumentException("actionThreshold deve ser >= 1: " + actionThreshold);
        }
        return riskScore() >= actionThreshold;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(field + " é obrigatório e tem no máximo "
                    + max + " caracteres");
        }
        return value.trim();
    }
}
