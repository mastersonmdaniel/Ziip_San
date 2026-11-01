package br.com.ziipsan.platform.security;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** P0.16: o limite de corpo, sem Spring e sem banco. */
class ContentLengthLimitFilterTest {

    private static final long MAX = 10;

    @Test
    @DisplayName("Content-Length acima do limite: 413 imediato, sem chamar a cadeia")
    void fastPathIs413() throws Exception {
        ContentLengthLimitFilter filter = new ContentLengthLimitFilter(MAX);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/registry/customers/search");
        request.setRequestURI("/api/v1/registry/customers/search");
        request.setContent("01234567890".getBytes(StandardCharsets.UTF_8));
        AtomicBoolean passed = new AtomicBoolean(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> passed.set(true));
        assertThat(passed.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("content-too-large");
    }

    @Test
    @DisplayName("corpo dentro do limite passa e continua legível na cadeia")
    void underLimitPassesAndBodyReadable() throws Exception {
        ContentLengthLimitFilter filter = new ContentLengthLimitFilter(MAX);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/registry/customers/search");
        request.setRequestURI("/api/v1/registry/customers/search");
        request.setContent("0123456789".getBytes(StandardCharsets.UTF_8));
        AtomicReference<byte[]> read = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) ->
                read.set(((HttpServletRequest) req).getInputStream().readAllBytes()));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(new String(read.get(), StandardCharsets.UTF_8)).isEqualTo("0123456789");
    }

    @Test
    @DisplayName("sem Content-Length (chunked), o estouro no meio da leitura dá 413")
    void chunkedOverflowIs413() throws Exception {
        ContentLengthLimitFilter filter = new ContentLengthLimitFilter(MAX);
        byte[] big = "01234567890".getBytes(StandardCharsets.UTF_8);
        HttpServletRequest chunked = new ChunkedRequest(new MockHttpServletRequest("POST", "/api/v1/x"), big);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(chunked, response, (req, res) ->
                ((HttpServletRequest) req).getInputStream().readAllBytes());
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("content-too-large");
    }

    @Test
    @DisplayName("GET com corpo grande não é filtrado (só métodos com corpo)")
    void getIsNotFiltered() throws Exception {
        ContentLengthLimitFilter filter = new ContentLengthLimitFilter(MAX);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/registry/customers");
        request.setRequestURI("/api/v1/registry/customers");
        request.setContent("01234567890".getBytes(StandardCharsets.UTF_8));
        AtomicBoolean passed = new AtomicBoolean(false);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> passed.set(true));
        assertThat(passed.get()).isTrue();
    }

    @Test
    @DisplayName("POST fora de /api/v1 e /bff não é filtrado")
    void outsidePrefixIsNotFiltered() throws Exception {
        ContentLengthLimitFilter filter = new ContentLengthLimitFilter(MAX);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/outro");
        request.setRequestURI("/outro");
        request.setContent("01234567890".getBytes(StandardCharsets.UTF_8));
        AtomicBoolean passed = new AtomicBoolean(false);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> passed.set(true));
        assertThat(passed.get()).isTrue();
    }

    /** Requisição sem Content-Length: getContentLengthLong() = -1, corpo via stream. */
    private static final class ChunkedRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        ChunkedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public ServletInputStream getInputStream() {
            InputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() throws IOException {
                    return in.read();
                }

                @Override
                public boolean isFinished() {
                    try {
                        return in.available() == 0;
                    } catch (IOException e) {
                        return true;
                    }
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
