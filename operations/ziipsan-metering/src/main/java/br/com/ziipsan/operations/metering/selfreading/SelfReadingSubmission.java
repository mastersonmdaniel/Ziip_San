package br.com.ziipsan.operations.metering.selfreading;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A submissao de autoleitura do consumidor (II-7, E2): a foto como
 * artefacto, a leitura que o consumidor eventualmente declarou, a prova
 * geografica capturada e a materia de coerencia (leitura anterior +
 * historico). As coordenadas da ligacao vem no campo proprio -- sao o
 * termo de comparacao da prova geografica.
 *
 * @param id                   identificador da submissao
 * @param unitId               unidade consumidora
 * @param reference            competencia do ciclo
 * @param declaredReading      leitura declarada pelo consumidor; null = nao declarou
 * @param photo                artefacto da foto; null = submissao sem foto
 * @param geoProof             prova geografica capturada; null = sem prova
 * @param connectionPosition   coordenadas cadastradas da ligacao; null = sem coordenadas
 * @param previousReading      leitura anterior; null = primeira leitura
 * @param history              consumos historicos (&gt;= 0), do mais antigo ao mais recente
 */
public record SelfReadingSubmission(UUID id, UUID unitId, YearMonth reference,
                                    Integer declaredReading, PhotoArtifact photo,
                                    GeoProof geoProof, GeoPoint connectionPosition,
                                    Integer previousReading, List<Integer> history) {

    public SelfReadingSubmission {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(history, "history");
        history = List.copyOf(history);
        for (Integer h : history) {
            if (h == null || h < 0) {
                throw new IllegalArgumentException("historico so aceita consumos >= 0");
            }
        }
        if (declaredReading != null && declaredReading < 0) {
            throw new IllegalArgumentException("leitura declarada nao pode ser negativa");
        }
        if (previousReading != null && previousReading < 0) {
            throw new IllegalArgumentException("leitura anterior nao pode ser negativa");
        }
    }
}
