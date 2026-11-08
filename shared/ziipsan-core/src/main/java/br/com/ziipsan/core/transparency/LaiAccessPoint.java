package br.com.ziipsan.core.transparency;

import br.com.ziipsan.core.transparency.TransparencyParameters.LaiMode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Ponto de acesso do cidadao ao pedido LAI (ED-99; GES-06), conforme o
 * {@code pub.lai_mode} do tenant:
 * <ul>
 *   <li>{@code EXTERNAL_LINK} — padrao quando o modulo esta ligado: o
 *       portal aponta para o sistema de acesso a informacao do municipio
 *       (URL obrigatoria e validada como http/https com host);</li>
 *   <li>{@code NATIVE_SIC} — o pedido nasce como protocolo nativo
 *       ({@link LaiRequest}); URL externa nao se aplica;</li>
 *   <li>{@code NONE} — modulo desligado: nao existe ponto de acesso
 *       (a fabrica {@link #forMode} recusa construir um).</li>
 * </ul>
 * A adesao ao Fala.BR e VOLUNTARIA: {@code falaBrEnrolled} so registra a
 * escolha do tenant; nenhuma integracao e presumida aqui.
 */
public record LaiAccessPoint(LaiMode mode, String externalUrl, boolean falaBrEnrolled) {

    public LaiAccessPoint {
        Objects.requireNonNull(mode, "modo LAI obrigatorio");
        if (mode == LaiMode.NONE) throw new IllegalArgumentException(
                "pub.lai_mode=NONE nao tem ponto de acesso (modulo desligado)");
        if (mode == LaiMode.EXTERNAL_LINK) {
            externalUrl = TransparencyParameters.externalUrl(externalUrl, mode);
            assertHttpUrl(externalUrl);
        }
    }

    public static LaiAccessPoint forMode(LaiMode mode, String externalUrl, boolean falaBrEnrolled) {
        return new LaiAccessPoint(mode, externalUrl, falaBrEnrolled);
    }

    /** O pedido e atendido dentro do ZiipSan (protocolo nativo da P1.36)? */
    public boolean nativeSic() { return mode == LaiMode.NATIVE_SIC; }

    private static void assertHttpUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    TransparencyParameters.LAI_EXTERNAL_URL + " invalida: " + url, e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    TransparencyParameters.LAI_EXTERNAL_URL + " deve ser URL http/https com host: " + url);
        }
    }
}
