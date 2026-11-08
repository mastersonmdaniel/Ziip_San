package br.com.ziipsan.intelligence.gis.qgis;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras puras da GIS-10 (§7.1), espelho legivel das guardas do core
 * V12 ({@code platform.gis_db_role}) e do SQL em db/pendente da ED-55 —
 * o banco e a autoridade; esta classe existe para a api falhar cedo e
 * para as regras serem testaveis sem banco.
 *
 * <p>Principios que NAO sao negociaveis (§7.1 itens 1, 2, 5 e 7):
 * role individual (nome derivado do tenant e do usuario, nunca
 * compartilhada), sem privilegio, tenant resolvido pela identidade da
 * role (nunca por GUC de sessao), escrita somente pelas views
 * {@code qgis_edit_*} (tabelas base sem escrita) e endpoint dedicado
 * fora do pooler, com TLS {@code verify-full} e origem restrita.
 *
 * <p>Correcao A1 (pos-confirmacao): as views sao
 * {@code security_invoker = true}, logo o PostgreSQL confere os
 * privilegios das tabelas base COMO A ROLE — a prescricao antiga
 * ("nenhum grant em tabela base") fazia TODA leitura/edicao falhar
 * com {@code permission denied} (provado em PostgreSQL real). O
 * desenho corrigido concede a role o SELECT minimo nas tabelas base
 * sob as views ({@link #ensureBaseTableGrant}); escrita na base
 * continua proibida — quem escreve e o gatilho SECURITY DEFINER, no
 * pacote do usuario — e a RLS segue como barreira entre tenants.
 */
public final class GisQgisRules {

    /** Estados do mapeamento (os mesmos CHECKs do core V12). */
    public static final Set<String> ROLE_STATUSES = Set.of(
            "REQUESTED", "APPROVED", "ACTIVE", "SUSPENDED", "REVOKED");

    /** Views de leitura geradas (SQL pendente da ED-55). */
    public static final Set<String> READ_VIEWS = Set.of(
            "qgis_node", "qgis_arc", "qgis_structure",
            "qgis_connection", "qgis_zone");

    /** Views de escrita (INSTEAD OF -> pacote QGIS do usuario). */
    public static final Set<String> EDIT_VIEWS = Set.of(
            "qgis_edit_node", "qgis_edit_arc", "qgis_edit_structure");

    /**
     * Tabelas base sob as views de leitura (V13_2): as unicas em que a
     * role recebe o SELECT minimo que o security_invoker exige
     * (correcao A1).
     */
    public static final Set<String> READ_BASE_TABLES = Set.of(
            "gis_node", "gis_arc", "gis_structure",
            "gis_connection", "gis_zone");

    /** Tabelas base: a role qgis NUNCA escreve nelas (§7.1 item 2). */
    public static final Set<String> BASE_TABLES = Set.of(
            "gis_node", "gis_arc", "gis_connection", "gis_structure",
            "gis_zone", "gis_poi", "gis_change_set", "gis_change_set_item");

    private static final Pattern ROLE_NAME =
            Pattern.compile("^qgis_[0-9a-f]{8}_[0-9a-f]{16}$");

    private GisQgisRules() {
    }

    /**
     * Nome deterministico e individual da role:
     * {@code qgis_<8 hex>_<16 hex>}, derivado do SHA-256 dos
     * identificadores COMPLETOS (tenant; tenant+usuario). Derivar de
     * prefixo de UUID colidiria entre usuarios cujos UUIDs partilham
     * o prefixo (o teste GisQgisRulesTest cobre exatamente esse caso);
     * do resumo, a colisao e a do SHA-256 truncado a 64 bits, e a
     * UNIQUE de role_name no core V12 continua sendo a autoridade.
     */
    public static String roleNameFor(String tenantId, String userId) {
        if (tenantId == null || userId == null) {
            throw new IllegalArgumentException("identificador invalido");
        }
        return "qgis_" + sha256Hex(tenantId).substring(0, 8)
                + "_" + sha256Hex(tenantId + ":" + userId).substring(0, 16);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    public static boolean isQgisRoleName(String roleName) {
        return roleName != null && ROLE_NAME.matcher(roleName).matches();
    }

    /** Quatro olhos: quem aprova a role nao e quem pediu (core V12). */
    public static void ensureFourEyes(String requestedBy, String approvedBy) {
        if (approvedBy == null || approvedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "aprovacao da role QGIS exige o aprovador registrado");
        }
        if (approvedBy.equals(requestedBy)) {
            throw new IllegalArgumentException(
                    "quem aprova a role QGIS nao pode ser quem pediu");
        }
    }

    /**
     * Acesso efetivo: somente ACTIVE dentro da validade. SUSPENDED e
     * REVOKED nao leem nem gravam; a revogacao vale na proxima conexao
     * porque {@code platform.gis_role_tenant()} e consultada a cada
     * abertura de view/sessao (§7.1 item 6).
     */
    public static boolean hasAccess(String status, java.time.Instant validUntil,
                                    java.time.Instant now) {
        return "ACTIVE".equals(status) && validUntil != null
                && validUntil.isAfter(now);
    }

    /**
     * Politica do endpoint dedicado (§7.1 item 5): TLS obrigatorio com
     * verificacao completa, fora do pooler da api, e origem por VPN ou
     * IP/CIDR declarado. Qualquer desvio e recusado — endpoint QGIS
     * aberto a internet nao e uma configuracao valida.
     */
    public static void ensureEndpointPolicy(boolean tlsRequired,
                                            String sslMode,
                                            boolean viaPooler,
                                            boolean vpnRequired,
                                            String allowedCidr) {
        if (!tlsRequired || !"verify-full".equals(sslMode)) {
            throw new IllegalArgumentException(
                    "endpoint QGIS exige TLS com sslmode=verify-full");
        }
        if (viaPooler) {
            throw new IllegalArgumentException(
                    "endpoint QGIS nao pode passar pelo pooler da api");
        }
        if (!vpnRequired && (allowedCidr == null || allowedCidr.isBlank())) {
            throw new IllegalArgumentException(
                    "endpoint QGIS exige VPN ou CIDR de origem declarado");
        }
    }

    /**
     * Guarda de objeto: a role so pode ser autorizada sobre as views
     * qgis_* / qgis_edit_*; autorizar tabela base por esta guarda e
     * recusado sempre (grant em tabela base tem guarda propria,
     * {@link #ensureBaseTableGrant}).
     */
    public static void ensureGrantTarget(String objectName) {
        if (objectName == null || !(READ_VIEWS.contains(objectName)
                || EDIT_VIEWS.contains(objectName))) {
            throw new IllegalArgumentException(
                    "role QGIS so recebe grant nas views qgis_*: " + objectName);
        }
    }

    /**
     * Guarda dos grants MINIMOS em tabela base (correcao A1
     * pos-confirmacao): com {@code security_invoker = true}, os
     * privilegios das tabelas base sao conferidos como o invocador —
     * sem SELECT na base, a role nao le nem edita NADA pelas views
     * ({@code permission denied for table}, provado em PostgreSQL
     * real). O minimo compativel com o desenho e SELECT, e somente
     * nas cinco tabelas sob as views de leitura: ele atende a leitura
     * das views e a selecao das linhas das views editaveis, porque a
     * escrita em si e do gatilho SECURITY DEFINER sobre as tabelas de
     * pacote — nunca da base. INSERT/UPDATE/DELETE em tabela base
     * seguem recusados (a role continua sem como escrever na rede
     * fora do pacote, §7.1 item 2), assim como qualquer grant em
     * gis_change_set/gis_change_set_item ou fora das cinco tabelas.
     * A prescricao anterior ("NADA nas tabelas base") era
     * incompativel com security_invoker e foi substituida por esta.
     */
    public static void ensureBaseTableGrant(String objectName, String privilege) {
        if (objectName == null || !READ_BASE_TABLES.contains(objectName)
                || !"SELECT".equals(privilege)) {
            throw new IllegalArgumentException(
                    "grant em tabela base da role QGIS e so o SELECT minimo "
                            + "nas tabelas sob as views (security_invoker): "
                            + objectName + "/" + privilege);
        }
    }
}
