package br.com.ziipsan.intelligence.gis.asset;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Inspecao interna de esgoto por video — OPE-12 (reaproveitavel na
 * drenagem quando existir, como a ED-64 declarou): codigos
 * padronizados de defeito, grau de condicao de 1 a 5 e pontuacao do
 * trecho; importacao do arquivo de transferencia da NASSCO (PACP);
 * desobstrucao programada nos trechos de reincidencia.
 *
 * <p><b>Padrao confirmado, nao presumido:</b> o aceite manda
 * confirmar com o cliente e o responsavel tecnico se vale o PACP ou
 * a pratica da WRc — o parametro {@code asset.cctv_standard}
 * ({@code PACP} ou {@code WRC}) e [A DEFINIR] sem valor de fabrica.
 * Sem o parametro, a importacao e RECUSADA (nenhum arquivo e lido
 * sob um padrao presumido). Com {@code PACP}, o parser le o formato
 * de transferencia textual delimitado entregue pelo equipamento
 * (uma linha por defeito: trecho;distancia;codigo;grau) e recusa
 * linha malformada nomeando a linha — o layout binario/proprietario
 * de cada fabricante NAO e inventado aqui.
 *
 * <p>Pontuacao do trecho: soma dos graus dos defeitos ponderada
 * pela maior nota — formula DECLARADA desta entrega (media dos
 * graus + peso do pior defeito), documentada no doc da GIS-19;
 * nenhuma fonte fixa formula oficial, por isso ela e regra do
 * produto registrada, nao "norma".
 */
public final class CctvInspectionService {

    private CctvInspectionService() {
    }

    public enum CctvStandard { PACP, WRC }

    /** Resolve o padrao do tenant; nulo/vazio/invalido = [A DEFINIR], importacao recusada. */
    public static CctvStandard requireStandard(String tenantParameterValue) {
        if (tenantParameterValue == null || tenantParameterValue.isBlank())
            throw new IllegalStateException("asset.cctv_standard [A DEFINIR]: confirmar PACP ou WRc com o cliente e o responsavel tecnico antes de importar (OPE-12)");
        try {
            return CctvStandard.valueOf(tenantParameterValue.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("asset.cctv_standard invalido (esperado PACP ou WRC): " + tenantParameterValue);
        }
    }

    /** Defeito observado: codigo padronizado, grau de condicao 1..5, distancia no trecho. */
    public record DefectObservation(String defectCode, int conditionGrade, double distanceMeters) {
        public DefectObservation {
            if (defectCode == null || defectCode.isBlank())
                throw new IllegalArgumentException("defeito sem codigo padronizado");
            if (conditionGrade < 1 || conditionGrade > 5)
                throw new IllegalArgumentException("grau de condicao fora de 1..5: " + conditionGrade);
            if (distanceMeters < 0) throw new IllegalArgumentException("distancia negativa no trecho");
        }
    }

    /** Inspecao de um trecho (arco de esgoto) com seus defeitos. */
    public record SegmentInspection(UUID segmentAssetId, String inspectionRef,
                                    List<DefectObservation> defects) {
        public SegmentInspection {
            Objects.requireNonNull(segmentAssetId, "inspecao sem trecho");
            defects = defects == null ? List.of() : List.copyOf(defects);
        }

        /** Grau do trecho = pior defeito (0 = trecho sem defeito observado). */
        public int segmentGrade() {
            return defects.stream().mapToInt(DefectObservation::conditionGrade).max().orElse(0);
        }

        /**
         * Pontuacao do trecho (regra declarada do produto, ver
         * javadoc): pior grau x 20 + media dos graus x 2, teto 100.
         * Trecho sem defeitos = 0.
         */
        public double segmentScore() {
            if (defects.isEmpty()) return 0.0;
            double avg = defects.stream().mapToInt(DefectObservation::conditionGrade).average().orElse(0);
            return Math.min(100.0, segmentGrade() * 20.0 + avg * 2.0);
        }
    }

    /**
     * Parser do arquivo de transferencia PACP (texto delimitado por
     * ';': trecho;distancia_m;codigo;grau). Cabecalho opcional
     * iniciado por '#'. Linha malformada aborta nomeando a linha.
     *
     * <p><b>Correcao D6 (revisao ED-56):</b> o parser e ACOPLADO ao
     * portao — nao existe mais caminho publico de parse sem
     * confirmacao. Este metodo exige o valor cru do parametro
     * {@code asset.cctv_standard} do tenant, chama
     * {@link #requireStandard} (recusa [A DEFINIR] sem confirmacao) e
     * recusa o parser PACP quando o padrao confirmado e outro
     * ({@code WRC} nao parseia como PACP).
     */
    public static List<DefectObservation> parsePacpTransfer(String segmentRef, String content,
                                                            String tenantStandardValue) {
        CctvStandard standard = requireStandard(tenantStandardValue);
        if (standard != CctvStandard.PACP)
            throw new IllegalStateException("parser PACP recusado: asset.cctv_standard confirmado e "
                    + standard + " (OPE-12) — importar pela pratica do padrao confirmado");
        Objects.requireNonNull(content, "arquivo de transferencia vazio");
        java.util.List<DefectObservation> out = new java.util.ArrayList<>();
        String[] lines = content.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split(";", -1);
            if (parts.length != 4)
                throw new IllegalArgumentException("PACP: linha " + (i + 1) + " malformada (esperado trecho;distancia;codigo;grau): " + line);
            if (!parts[0].trim().equals(segmentRef))
                throw new IllegalArgumentException("PACP: linha " + (i + 1) + " de outro trecho (" + parts[0] + " != " + segmentRef + ")");
            try {
                double dist = Double.parseDouble(parts[1].trim());
                int grade = Integer.parseInt(parts[3].trim());
                out.add(new DefectObservation(parts[2].trim(), grade, dist));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("PACP: linha " + (i + 1) + " com distancia/grau nao numericos: " + line);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Reincidencia: trecho com {@code occurrences} obstrucoes/
     * reparos no historico >= limiar do tenant entra na desobstrucao
     * programada. O limiar e do tenant (parametro entregue pelo
     * chamador); sem limiar positivo, nada e programado por
     * presuncao.
     */
    public static boolean isReincident(long obstructionOccurrences, int tenantThreshold) {
        if (tenantThreshold <= 0)
            throw new IllegalArgumentException("limiar de reincidencia deve ser positivo quando configurado");
        return obstructionOccurrences >= tenantThreshold;
    }
}
