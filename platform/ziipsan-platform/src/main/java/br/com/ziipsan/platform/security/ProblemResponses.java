package br.com.ziipsan.platform.security;

import br.com.ziipsan.core.json.Json;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resposta de erro em problem+json (RFC 9457) escrita fora dos controllers: filtros de segurança e
 * tenant, que rodam antes do DispatcherServlet e por isso não passam pelo tratador global.
 *
 * <p>O {@code type} é uma URN ({@code urn:ziipsan:problem:<codigo>}): identifica o problema sem
 * prometer uma página de documentação que não existe.
 */
public final class ProblemResponses {

    public static final String TYPE_PREFIX = "urn:ziipsan:problem:";

    private ProblemResponses() {
    }

    public static void write(HttpServletResponse response, int status, String code, String title, String detail)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", TYPE_PREFIX + code);
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        response.getWriter().write(Json.write(body));
    }
}
