package br.com.ziipsan.intelligence.gis.expandedimport;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Casamento de unidades consumidoras (SG-1349) e conciliacao
 * base x desenho (SG-1356) da GIS-18, como regras puras.
 *
 * <p>Casamento: por matricula (chave exata) ou por endereco
 * NORMALIZADO (exato ou por prefixo de logradouro+numero). Nenhum
 * casamento automatico acontece com ambiguidade: dois ou mais
 * candidatos viram {@code AMBIGUOUS} e vao para a fila de revisao
 * ({@code gis_unit_match_queue}, SQL pendente da ED-55), decidida por
 * pessoa. Sem candidato, {@code NONE} tambem vai para a fila — o
 * sistema nunca liga uma unidade por chute.
 *
 * <p>A normalizacao de endereco e deliberadamente conservadora:
 * caixa, acentos, pontuacao e espacos. Nao expande abreviaturas nem
 * corrige grafia — expandir "R." para "RUA" sem tabela oficial seria
 * inventar dado de endereco.
 */
public final class GisUnitMatching {

    /** Unidade consumidora candidata (vem do cadastro comercial). */
    public record UnitCandidate(String unitId, String registrationKey,
                                String address) {
    }

    public enum MatchMethod { REGISTRATION, ADDRESS_EXACT, ADDRESS_PREFIX, NONE }

    public enum MatchStatus { MATCHED, PENDING_REVIEW, AMBIGUOUS }

    /** Resultado: no maximo um candidato quando MATCHED. */
    public record MatchResult(MatchMethod method, MatchStatus status,
                              String candidateUnitId) {
    }

    /** Item da conciliacao base x desenho (SG-1356). */
    public enum ReconciliationOutcome {
        ONLY_IN_BASE, ONLY_IN_DRAWING, GEOMETRY_DIVERGENT,
        ATTRIBUTE_DIVERGENT, AGREED
    }

    private GisUnitMatching() {
    }

    public static String normalizeAddress(String address) {
        if (address == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(address, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll(" +", " ")
                .trim();
    }

    public static MatchResult match(String registrationKey, String address,
                                    List<UnitCandidate> candidates) {
        if (candidates == null) {
            candidates = List.of();
        }
        if (registrationKey != null && !registrationKey.isBlank()) {
            List<UnitCandidate> byKey = candidates.stream()
                    .filter(c -> registrationKey.equals(c.registrationKey()))
                    .toList();
            if (byKey.size() == 1) {
                return new MatchResult(MatchMethod.REGISTRATION,
                        MatchStatus.MATCHED, byKey.get(0).unitId());
            }
            if (byKey.size() > 1) {
                return new MatchResult(MatchMethod.REGISTRATION,
                        MatchStatus.AMBIGUOUS, null);
            }
        }
        String wanted = normalizeAddress(address);
        if (!wanted.isEmpty()) {
            List<UnitCandidate> exact = candidates.stream()
                    .filter(c -> wanted.equals(normalizeAddress(c.address())))
                    .toList();
            if (exact.size() == 1) {
                return new MatchResult(MatchMethod.ADDRESS_EXACT,
                        MatchStatus.MATCHED, exact.get(0).unitId());
            }
            if (exact.size() > 1) {
                return new MatchResult(MatchMethod.ADDRESS_EXACT,
                        MatchStatus.AMBIGUOUS, null);
            }
            List<UnitCandidate> prefix = candidates.stream()
                    .filter(c -> {
                        String candidate = normalizeAddress(c.address());
                        return candidate.startsWith(wanted + " ")
                                || wanted.startsWith(candidate + " ");
                    })
                    .toList();
            if (prefix.size() == 1) {
                return new MatchResult(MatchMethod.ADDRESS_PREFIX,
                        MatchStatus.PENDING_REVIEW, prefix.get(0).unitId());
            }
            if (prefix.size() > 1) {
                return new MatchResult(MatchMethod.ADDRESS_PREFIX,
                        MatchStatus.AMBIGUOUS, null);
            }
        }
        return new MatchResult(MatchMethod.NONE,
                MatchStatus.PENDING_REVIEW, null);
    }

    /**
     * Decisao humana MATCHED sobre item da fila (correcao D4 da
     * revisao cruzada): item AMBIGUOUS nasce SEM candidato unico
     * ({@code candidateUnitId == null}) — a decisao precisa poder
     * INFORMAR o candidato escolhido entre os avaliados, senao o
     * ambiguo so poderia ser rejeitado e a fila perderia a funcao
     * justamente nos casos feitos para decisao humana.
     *
     * <p>Regras: o candidato efetivo e o informado na decisao, ou o
     * candidato unico ja registrado no item (PENDING_REVIEW por
     * prefixo); sem nenhum dos dois, MATCHED e recusado. Havendo
     * conjunto avaliado registrado, o candidato efetivo TEM de
     * pertencer a ele (nunca uma unidade fora da avaliacao/tenant).
     *
     * @return o id da unidade que casa (o que vai gravado no item)
     */
    public static String resolveDecisionCandidate(String recordedCandidateId,
                                                  String chosenCandidateId,
                                                  List<String> evaluatedCandidateIds) {
        String effective = chosenCandidateId != null && !chosenCandidateId.isBlank()
                ? chosenCandidateId : recordedCandidateId;
        if (effective == null || effective.isBlank()) {
            throw new IllegalArgumentException(
                    "decisao MATCHED exige o candidato escolhido entre os avaliados");
        }
        if (evaluatedCandidateIds != null && !evaluatedCandidateIds.isEmpty()
                && !evaluatedCandidateIds.contains(effective)) {
            throw new IllegalArgumentException(
                    "candidato da decisao fora do conjunto avaliado: " + effective);
        }
        return effective;
    }

    /**
     * Conciliacao (SG-1356): compara a existencia e, quando ambos
     * existem, geometria (por igualdade de WKT normalizado) e um
     * resumo de atributos. Divergencia nunca e resolvida aqui — vira
     * item da fila {@code gis_reconciliation_item} para decisao humana
     * (KEEP_BASE / KEEP_DRAWING / FIELD_SURVEY / IGNORED).
     */
    public static ReconciliationOutcome reconcile(boolean inBase,
                                                  boolean inDrawing,
                                                  String baseGeomWkt,
                                                  String drawingGeomWkt,
                                                  String baseAttrDigest,
                                                  String drawingAttrDigest) {
        if (inBase && !inDrawing) {
            return ReconciliationOutcome.ONLY_IN_BASE;
        }
        if (!inBase && inDrawing) {
            return ReconciliationOutcome.ONLY_IN_DRAWING;
        }
        if (!inBase) {
            throw new IllegalArgumentException(
                    "conciliacao sem base nem desenho nao e um item");
        }
        if (!normalizeWkt(baseGeomWkt).equals(normalizeWkt(drawingGeomWkt))) {
            return ReconciliationOutcome.GEOMETRY_DIVERGENT;
        }
        if (baseAttrDigest == null ? drawingAttrDigest != null
                : !baseAttrDigest.equals(drawingAttrDigest)) {
            return ReconciliationOutcome.ATTRIBUTE_DIVERGENT;
        }
        return ReconciliationOutcome.AGREED;
    }

    private static String normalizeWkt(String wkt) {
        return wkt == null ? "" : wkt.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }
}
