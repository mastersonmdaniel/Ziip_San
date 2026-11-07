package br.com.ziipsan.reporting.spi;

import java.util.Set;
import java.util.UUID;

/**
 * Quem pede o relatorio, ja autenticado pela borda (BFF/Keycloak). O nucleo
 * nao autentica: recebe o contexto e aplica a regra da P1.12 sobre ele.
 *
 * @param maskCeiling teto de mascara do usuario (ADR-0008): o arquivo sai com
 *                    a mesma mascara da tela deste usuario.
 */
public record ReportUserContext(UUID tenantId, UUID userId, Set<String> permissions,
                                MaskCeiling maskCeiling, Set<String> roles) {

    public ReportUserContext {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        if (maskCeiling == null) {
            maskCeiling = MaskCeiling.FULL;
        }
    }

    /** Permissao por papel e por recurso: o papel pode vir com a permissao
     *  do recurso (reporting.rpt-001:export) ou com o curinga de exportacao
     *  do modulo (reporting:export). Sem uma das duas, a exportacao e negada. */
    public boolean canExport(String requiredPermission) {
        return permissions.contains(requiredPermission)
                || permissions.contains("reporting:export")
                || permissions.contains("*:export");
    }

    public enum MaskCeiling {
        /** Ve o dado em claro na tela; o arquivo sai em claro. */
        UNMASKED,
        /** Ve parcial na tela (ex.: documento ***.***.789-00); arquivo igual. */
        PARTIAL,
        /** Nao ve o dado; no arquivo a coluna sai totalmente mascarada. */
        FULL
    }
}
