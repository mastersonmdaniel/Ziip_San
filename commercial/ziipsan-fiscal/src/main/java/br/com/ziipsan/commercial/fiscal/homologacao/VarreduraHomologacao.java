package br.com.ziipsan.commercial.fiscal.homologacao;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Teste de varredura da homologação (padrão da P0.45, exigido pelo aceite da
 * P3.7): recusa XML com o certificado da Ziip/TGP (a) FORA da área separada, em
 * qualquer momento, ou (b) em QUALQUER lugar — inclusive na área separada —
 * DEPOIS do fim do ciclo (ciclo expurgado: o que devia ter sido apagado e
 * restou é violação).
 *
 * <p><b>Detecção real do certificado (correção D1 da revisão):</b> num XML
 * assinado de verdade, o certificado viaja no elemento {@code X509Certificate}
 * como DER codificado em <b>base64</b> — o thumbprint (hex do hash do DER)
 * <b>nunca</b> aparece como texto no arquivo. Por isso a detecção NÃO é uma
 * busca textual pelo thumbprint: ela extrai o conteúdo de cada elemento
 * {@code X509Certificate} (qualquer namespace), decodifica base64→DER e
 * calcula o thumbprint do DER — SHA-1 (o thumbprint clássico de certificado
 * X.509) e SHA-256 (o hash do pacote, {@link Sha256}) — comparando os dois
 * com o thumbprint esperado configurado. Um XML cujo certificado em base64
 * tenha qualquer um dos dois hashes igual ao esperado É detectado.
 *
 * <p>Fallback fechado (fail-closed), mantido por compatibilidade com os
 * marcadores de teste do projeto: se o conteúdo de um {@code X509Certificate}
 * for textualmente igual ao thumbprint esperado (marcador), se o conteúdo
 * recebido for o próprio certificado em base64 ou o thumbprint puro, ou se o
 * thumbprint aparecer como texto em qualquer ponto do conteúdo, a detecção
 * também acusa. Esse fallback só ADICIONA recusas — nunca dispensa nem
 * substitui a detecção por hash acima, que é a que enxerga o certificado real.
 *
 * <p>O thumbprint esperado é configuração de quem opera (cofre local), nunca
 * constante desta classe. XMLs assinados com certificado de TESTE (restauração
 * da P2.18) não são alvo desta varredura — o alvo é só o certificado da
 * Ziip/TGP.
 *
 * <p>Como toda varredura do padrão P0.45, esta classe recebe os achados já lidos
 * (localização + conteúdo) e decide; a leitura do disco/banco é de quem chama
 * (teste de CI ou script de varredura do roteiro, que aplica o mesmo algoritmo).
 */
public final class VarreduraHomologacao {

    /** Um arquivo/objeto lido pela varredura. */
    public record Achado(String localizacao, String conteudoXml) {
    }

    /** Uma violação: onde está o XML e por que ele não pode estar ali. */
    public record Violacao(String localizacao, String motivo) {
    }

    /** Elemento X509Certificate com ou sem prefixo de namespace, conteúdo em base64. */
    private static final Pattern ELEMENTO_X509 = Pattern.compile(
            "<(?:[A-Za-z0-9_.-]+:)?X509Certificate(?:\\s[^>]*)?>(.*?)</(?:[A-Za-z0-9_.-]+:)?X509Certificate\\s*>",
            Pattern.DOTALL);

    private final String thumbprintZiipTgp;

    public VarreduraHomologacao(String thumbprintZiipTgp) {
        if (thumbprintZiipTgp == null || thumbprintZiipTgp.isBlank()) {
            throw new IllegalArgumentException(
                    "Thumbprint do certificado da Ziip/TGP é configuração obrigatória da varredura");
        }
        this.thumbprintZiipTgp = thumbprintZiipTgp;
    }

    /** Normaliza um thumbprint para comparação: sem espaços, sem ':' e em minúsculas. */
    public static String normalizarThumbprint(String thumbprint) {
        return thumbprint == null ? "" : thumbprint.replaceAll("[\\s:]+", "").toLowerCase();
    }

    /** Conteúdos brutos de todos os elementos {@code X509Certificate} do XML (sem espaços). */
    public static List<String> extrairCertificadosBase64(String conteudoXml) {
        if (conteudoXml == null) {
            return List.of();
        }
        List<String> certificados = new ArrayList<>();
        Matcher m = ELEMENTO_X509.matcher(conteudoXml);
        while (m.find()) {
            certificados.add(m.group(1).replaceAll("\\s+", ""));
        }
        return List.copyOf(certificados);
    }

    /**
     * Thumbprints calculados de um certificado em base64 (DER): SHA-1 e SHA-256
     * em hex minúsculo. Lista vazia se o conteúdo não for base64 decodificável.
     */
    public static List<String> thumbprintsDoCertificadoBase64(String certificadoBase64) {
        if (certificadoBase64 == null || certificadoBase64.isBlank()) {
            return List.of();
        }
        String limpo = certificadoBase64.replaceAll("\\s+", "");
        byte[] der;
        try {
            if (limpo.matches("[A-Za-z0-9+/]+={0,2}")) {
                der = Base64.getMimeDecoder().decode(limpo);
            } else if (limpo.matches("[A-Za-z0-9_-]+={0,2}")) {
                der = Base64.getUrlDecoder().decode(limpo);
            } else {
                return List.of();
            }
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        if (der.length == 0) {
            return List.of();
        }
        return List.of(sha1Hex(der), Sha256.deBytes(der));
    }

    private static String sha1Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 é algoritmo obrigatório de toda JRE; ausência é defeito do ambiente.
            throw new IllegalStateException("SHA-1 indisponível nesta JRE", e);
        }
    }

    /**
     * Verdadeiro se o conteúdo carrega o certificado cujo thumbprint é
     * {@code thumbprintEsperado}: extrai cada {@code X509Certificate},
     * decodifica o DER e compara o SHA-1/SHA-256 calculados (detecção real);
     * o fallback textual fechado cobre marcadores de teste e o thumbprint puro.
     * Método estático para reuso (ex.: restauração confere certificados de teste
     * pelo mesmo algoritmo).
     */
    public static boolean conteudoCarregaCertificado(String conteudoXml, String thumbprintEsperado) {
        if (conteudoXml == null || thumbprintEsperado == null || thumbprintEsperado.isBlank()) {
            return false;
        }
        String esperado = normalizarThumbprint(thumbprintEsperado);
        for (String certificado : extrairCertificadosBase64(conteudoXml)) {
            if (normalizarThumbprint(certificado).equals(esperado)) {
                return true; // marcador textual de teste dentro do elemento
            }
            if (thumbprintsDoCertificadoBase64(certificado).contains(esperado)) {
                return true; // certificado real: hash do DER confere
            }
        }
        // Conteúdo que É o próprio certificado em base64 (sem o XML em volta):
        // o hash do DER decodificado também decide.
        if (thumbprintsDoCertificadoBase64(conteudoXml).contains(esperado)) {
            return true;
        }
        // Fallback textual fechado: o thumbprint como texto em qualquer ponto
        // do conteúdo (marcadores de teste, registros) também acusa.
        return normalizarThumbprint(conteudoXml).contains(esperado);
    }

    /** Verdadeiro se o conteúdo carrega o certificado da Ziip/TGP. */
    public boolean carregaCertificadoZiipTgp(String conteudoXml) {
        return conteudoCarregaCertificado(conteudoXml, thumbprintZiipTgp);
    }

    /**
     * Varre os achados contra a situação do ciclo.
     *
     * @param cicloExpurgado verdadeiro quando o ciclo já acabou e foi expurgado:
     *                       nenhum XML com o certificado pode restar em lugar nenhum
     */
    public List<Violacao> varrer(List<Achado> achados, boolean cicloExpurgado) {
        Objects.requireNonNull(achados, "Lista de achados da varredura é obrigatória");
        List<Violacao> violacoes = new ArrayList<>();
        for (Achado a : achados) {
            if (!carregaCertificadoZiipTgp(a.conteudoXml())) {
                continue;
            }
            boolean naAreaSeparada = a.localizacao() != null
                    && a.localizacao().startsWith(AreaSeparadaHomologacao.ESQUEMA_AREA);
            if (cicloExpurgado) {
                violacoes.add(new Violacao(a.localizacao(),
                        "XML com o certificado da Ziip/TGP após o fim do ciclo: o expurgo do ciclo devia tê-lo apagado"));
            } else if (!naAreaSeparada) {
                violacoes.add(new Violacao(a.localizacao(),
                        "XML com o certificado da Ziip/TGP fora da área separada da homologação"));
            }
        }
        return List.copyOf(violacoes);
    }

    /** Forma usada pelo teste/CI: qualquer violação é falha explícita, com o local. */
    public void exigirLimpo(List<Achado> achados, boolean cicloExpurgado) {
        List<Violacao> violacoes = varrer(achados, cicloExpurgado);
        if (!violacoes.isEmpty()) {
            throw new IllegalStateException("Varredura da homologação recusou " + violacoes.size()
                    + " XML(s): " + violacoes);
        }
    }
}
