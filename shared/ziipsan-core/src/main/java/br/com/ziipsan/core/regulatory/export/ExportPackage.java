package br.com.ziipsan.core.regulatory.export;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pacote de exportacao com a SEPARACAO exigida pelo aceite: exportacao
 * AGREGADA PUBLICA x exportacao NOMINAL RESTRITA.
 *
 * <ul>
 *   <li>AGREGADA PUBLICA: so linhas agregadas (categoria/faixa/periodo).
 *       Linha nominal (com identificador de pessoa/economia) e RECUSADA
 *       na construcao — nominal nunca vaza para o agregado.</li>
 *   <li>NOMINAL RESTRITA: linhas nominais, marcadas restritas; nunca
 *       publicaveis (o registro de envio as trata como restritas e o
 *       escopo externo do regulador da ED-75 nao as inclui — lastro em
 *       codigo: {@link RegulatorExternalScope#resourceForPackage} devolve
 *       vazio e {@link RegulatorExternalScope#canRegulatorReadPackage}
 *       e falso para este kind, correcao R7-B1; o registro distingue o
 *       kind em {@link SubmissionRecord#isRestricted}).</li>
 * </ul>
 *
 * O hash do pacote e SHA-256 do conteudo canonico (perfil + periodo +
 * linhas ordenadas) — o mesmo hash que o registro de envio guarda.
 *
 * <p>Correcao D1 (REVISAO-ED-97): as garantias valem POR CONSTRUCAO, no
 * construtor canonico — nao so pela fabrica {@link #build}: o construtor
 * recusa linha nominal em pacote agregado, exige ao menos uma linha
 * nominal em pacote nominal restrito e CONFERE o hash recebido contra o
 * hash recalculado do conteudo canonico (hash forjado e recusado). A
 * deteccao de linha nominal e insensivel a caixa.</p>
 */
public record ExportPackage(AgencyExportProfile profile, String periodRef, Kind kind,
                            List<String> lines, String sha256Hex) {

    public enum Kind { AGGREGATE_PUBLIC, NOMINAL_RESTRICTED }

    public ExportPackage {
        Objects.requireNonNull(profile); Objects.requireNonNull(periodRef);
        Objects.requireNonNull(kind);
        lines = lines == null ? List.of() : List.copyOf(lines);
        Objects.requireNonNull(sha256Hex);
        // D1: separacao agregado x nominal garantida na construcao.
        if (kind == Kind.AGGREGATE_PUBLIC) {
            for (String l : lines) {
                if (isNominalLine(l)) {
                    throw new IllegalArgumentException(
                        "linha nominal recusada na exportacao agregada publica: " + l);
                }
            }
        }
        if (kind == Kind.NOMINAL_RESTRICTED && lines.stream().noneMatch(ExportPackage::isNominalLine)) {
            throw new IllegalArgumentException("pacote nominal restrito sem nenhuma linha nominal");
        }
        // D1: integridade do hash garantida na construcao — o hash
        // guardado tem de ser o hash do conteudo canonico.
        if (!computeSha256(profile, periodRef, kind, lines).equals(sha256Hex)) {
            throw new IllegalArgumentException("hash do pacote diverge do conteudo canonico");
        }
    }

    /**
     * Linha nominal = carrega marcador de identificador pessoal/nominal.
     * Deteccao INSENSIVEL A CAIXA (correcao D1): os marcadores do desenho
     * do pacote sao o prefixo {@code NOMINAL:} e as chaves {@code cpf=} /
     * {@code nome=} no formato {@code chave=valor;...} das linhas — em
     * qualquer caixa ({@code CPF=}, {@code Nome=}, {@code nominal:} etc.
     * tambem sao nominais). Nenhum formato novo e inventado.
     */
    public static boolean isNominalLine(String line) {
        if (line == null) return false;
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.stripLeading().startsWith("nominal:")
                || lower.contains("cpf=") || lower.contains("nome=");
    }

    /** Conteudo canonico do hash (perfil + periodo + tipo + linhas ordenadas). */
    public static String canonicalContent(AgencyExportProfile profile, String periodRef, Kind kind,
                                          List<String> lines) {
        List<String> ls = lines == null ? List.of() : lines;
        return profile.profileKey() + "|" + profile.layoutVersion() + "|" + periodRef
                + "|" + kind.name() + "|" + String.join("\n", ls.stream().sorted().toList());
    }

    /** Hash esperado do conteudo canonico — usado pela fabrica, pelo
     *  construtor canonico e pela reverificacao do servico de envio. */
    public static String computeSha256(AgencyExportProfile profile, String periodRef, Kind kind,
                                       List<String> lines) {
        return sha256(canonicalContent(profile, periodRef, kind, lines));
    }

    /** Hash recalculado deste pacote (para reverificacao por terceiros). */
    public String expectedSha256() {
        return computeSha256(profile, periodRef, kind, lines);
    }

    public static ExportPackage build(AgencyExportProfile profile, String periodRef, Kind kind,
                                      List<String> lines) {
        Objects.requireNonNull(kind);
        List<String> ls = lines == null ? List.of() : List.copyOf(lines);
        if (kind == Kind.AGGREGATE_PUBLIC) {
            for (String l : ls) {
                if (isNominalLine(l)) {
                    throw new IllegalArgumentException(
                        "linha nominal recusada na exportacao agregada publica: " + l);
                }
            }
        }
        if (kind == Kind.NOMINAL_RESTRICTED && ls.stream().noneMatch(ExportPackage::isNominalLine)) {
            throw new IllegalArgumentException("pacote nominal restrito sem nenhuma linha nominal");
        }
        return new ExportPackage(profile, periodRef, kind, ls,
                computeSha256(profile, periodRef, kind, ls));
    }

    public boolean isPublic() { return kind == Kind.AGGREGATE_PUBLIC; }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
