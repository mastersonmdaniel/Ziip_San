package br.com.ziipsan.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Set;

/**
 * Limite de corpo (P0.16, 413 do plano).
 *
 * <p>Posição: ANTES da autenticação nas duas cadeias — um corpo gigante não deve
 * custar autenticação nem chegar ao Jackson. Ponto de corte: <b>filtro</b>, e
 * não conector: não há {@code server.tomcat.max-*} no repo e o filtro devolve
 * 413 em problem+json no formato dos demais erros, em vez do erro cru do Tomcat.
 * Cobre {@code /api/v1/**} e {@code /bff/**}, só métodos com corpo. Limite
 * padrão: 1 MiB. Não há upload multipart no código (nenhum
 * {@code MultipartFile}), só JSON — o limite cobre todos os corpos atuais.
 *
 * <p>Dois caminhos: fast path por {@code Content-Length} acima do limite (413
 * imediato, sem ler o corpo); sem {@code Content-Length} (chunked), o stream é
 * embrulhado com contagem e estoura em 413 ao exceder — inclusive quando o
 * {@code Content-Length} mente para baixo, porque o embrulho vale sempre.
 *
 * <p>Não é {@code @Component}: senão o Spring Boot o registraria também como
 * filtro de servlet comum, fora da cadeia.
 */
public class ContentLengthLimitFilter extends OncePerRequestFilter {

    private static final Set<String> METHODS_WITH_BODY = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final long maxBytes;

    public ContentLengthLimitFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!METHODS_WITH_BODY.contains(request.getMethod())) {
            return true;
        }
        String path = RateLimitFilter.pathOf(request);
        return !path.startsWith("/api/v1") && !path.startsWith("/bff");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            // Fast path: nem lê o corpo.
            write413(response);
            return;
        }
        try {
            chain.doFilter(new CountingRequestWrapper(request, maxBytes), response);
        } catch (LimitExceededException e) {
            if (!response.isCommitted()) {
                write413(response);
            }
        }
    }

    private static void write413(HttpServletResponse response) throws IOException {
        ProblemResponses.write(response, 413, "content-too-large", "Conteúdo muito grande",
                "O corpo da requisição excede o limite permitido.");
    }

    /** Estouro do limite no meio da leitura (corpo chunked ou Content-Length mentiroso). */
    static final class LimitExceededException extends RuntimeException {
        LimitExceededException() {
            super("Corpo acima do limite");
        }
    }

    private static final class CountingRequestWrapper extends HttpServletRequestWrapper {

        private final long maxBytes;
        private CountingServletInputStream countingStream;

        CountingRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (countingStream == null) {
                countingStream = new CountingServletInputStream(super.getInputStream(), maxBytes);
            }
            return countingStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String enc = getCharacterEncoding();
            Charset charset = enc == null ? Charset.defaultCharset() : Charset.forName(enc);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class CountingServletInputStream extends ServletInputStream {

        private final InputStream delegate;
        private final long maxBytes;
        private long count;

        CountingServletInputStream(InputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                count++;
                check();
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = delegate.read(buf, off, len);
            if (n > 0) {
                count += n;
                check();
            }
            return n;
        }

        private void check() {
            if (count > maxBytes) {
                throw new LimitExceededException();
            }
        }

        @Override
        public boolean isFinished() {
            try {
                return delegate.available() == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Leitura assíncrona não suportada pelo limite de corpo");
        }
    }
}
