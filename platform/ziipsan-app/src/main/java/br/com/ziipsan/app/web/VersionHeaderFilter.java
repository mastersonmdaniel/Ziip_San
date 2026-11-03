package br.com.ziipsan.app.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * P0.39: cabeçalhos de versão em TODA resposta (inclusive 401/403/404 e /actuator/health).
 *
 * <p>Como filtro de servlet (antes da cadeia de segurança), ele roda mesmo quando a segurança
 * recusa a requisição — o que um interceptor MVC não faria. A versão vem do build-info
 * gerado no build (nunca escrita à mão); sem build-info (ex.: teste que não passou por
 * generate-resources), cai para {@code dev} em vez de quebrar a subida.
 *
 * <p>{@code Ziip-Min-Client-Version} é, por padrão, a própria versão do build: qualquer SPA
 * de build anterior é "antigo" e mostra o aviso de recarga (ver VersionBanner no frontend).
 * Um piso diferente pode ser fixado por propriedade, mas o padrão não exige decisão do dono
 * a cada release — o piso acompanha a versão.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class VersionHeaderFilter extends OncePerRequestFilter {

    static final String VERSION_HEADER = "Ziip-Version";
    static final String MIN_CLIENT_HEADER = "Ziip-Min-Client-Version";

    private final String version;
    private final String minClientVersion;

    public VersionHeaderFilter(ObjectProvider<BuildProperties> buildProperties,
                               org.springframework.core.env.Environment environment) {
        BuildProperties info = buildProperties.getIfAvailable();
        this.version = info != null && info.getVersion() != null ? info.getVersion() : "dev";
        String configuredMin = environment.getProperty("ziipsan.version.min-client-version");
        this.minClientVersion = configuredMin != null && !configuredMin.isBlank() ? configuredMin : this.version;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(VERSION_HEADER, version);
        response.setHeader(MIN_CLIENT_HEADER, minClientVersion);
        chain.doFilter(request, response);
    }
}
