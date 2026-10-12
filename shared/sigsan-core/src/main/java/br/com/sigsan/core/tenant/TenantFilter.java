package br.com.sigsan.core.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TENANT_UUID_HEADER = "X-Tenant-UUID";

    /**
     * Formato de schema Postgres. O header alimenta o search_path do Hibernate, então um valor
     * malformado seria injeção de SQL via nome de schema. Só letras, dígitos e underscore,
     * começando por letra ou underscore.
     */
    private static final Pattern VALID_SCHEMA = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String raw = request.getHeader(TENANT_HEADER);
            if (raw != null && !raw.isBlank()) {
                applyTenant(request, raw.trim());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * O header principal aceita duas formas: nome de schema (tenant_alpha) ou UUID. Um UUID tem
     * hífen e não é um nome de schema válido, então é tratado como identificador e o schema
     * fica para ser resolvido pelo catálogo.
     */
    private void applyTenant(HttpServletRequest request, String raw) {
        UUID explicitId = parseUuid(request.getHeader(TENANT_UUID_HEADER));

        if (VALID_SCHEMA.matcher(raw).matches()) {
            TenantContextHolder.setTenant(raw);
            UUID id = explicitId != null ? explicitId : parseUuid(raw);
            if (id != null) {
                TenantContextHolder.setTenantId(id);
            } else {
                log.debug("Tenant {} sem UUID; id será resolvido pelo catálogo", raw);
            }
            return;
        }

        if (isUuid(raw)) {
            TenantContextHolder.setTenantId(UUID.fromString(raw));
            if (explicitId != null) {
                TenantContextHolder.setTenantId(explicitId);
            }
            log.debug("Header {} contém UUID; schema será resolvido pelo catálogo", TENANT_HEADER);
            return;
        }

        log.warn("Header {} rejeitado: nem schema válido nem UUID", TENANT_HEADER);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Valor não é um UUID válido: {}", TENANT_UUID_HEADER);
            return null;
        }
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
