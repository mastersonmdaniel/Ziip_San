package br.com.ziipsan.platform.work;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link WorkParameters} sobre o catálogo global e as versões aceitas do tenant —
 * mesmo padrão de leitura do {@code TenantParameterService} (P1.6): o valor vigente é
 * a versão APROVADA com aceite de mesmo hash cobrindo hoje; sem versão aceita, vale o
 * padrão de fábrica do catálogo; sem nenhum dos dois, {@link MissingParameterException}
 * (ausência é erro explícito, nunca padrão inventado).
 *
 * <p>Trava da classe a aplicada AQUI também (segunda barreira, além do console):
 * {@code wf.self_approval} cuja definição não seja classe {@code a} com padrão
 * {@code false}, ou cujo valor resolvido seja {@code true}, é inconsistência de
 * catálogo e explode em {@link IllegalStateException} — nenhum tenant, por nenhum
 * caminho, liga a autoaprovação.
 *
 * <p><b>Limites legais lidos na resolução</b> (correção pós-revisão m2): este
 * resolvedor lê também {@code public.parameter_legal_limits} da chave (a mesma
 * tabela que o console da P1.6 confere na proposta) e aplica os limites PRESENTES
 * a todo valor resolvido — {@code LOCKED} (o valor resolvido precisa ser igual ao
 * valor travado), {@code ALLOWED_VALUES} (precisa estar na lista) e
 * {@code MIN_VALUE}/{@code FLOOR}/{@code MAX_VALUE} numéricos. Para
 * {@code wf.self_approval}, um valor aceito {@code true} com os limites da V9_13
 * presentes ({@code LOCKED 'false'} + {@code ALLOWED_VALUES '[false]'}) explode
 * JÁ NA RESOLUÇÃO, antes de qualquer decisão — mesmo se um dia a chamada à
 * {@link ApprovalRule#assertSelfApprovalForbidden} fosse removida de algum caminho.
 * Limite ausente no catálogo não é inventado: sem linha, vale a defesa restante
 * (definição classe a + trava da {@link ApprovalRule}).
 *
 * <p><b>Honestidade sobre a trava (m2):</b> a definição no banco NÃO tem
 * imutabilidade física — não há trigger nem constraint em
 * {@code parameter_definitions}/{@code parameter_legal_limits} que impeça um
 * {@code UPDATE} direto com acesso de escrita ao banco. A defesa é
 * APLICAÇÃO + CONSOLE: o console (P1.6) recusa propostas fora dos limites, este
 * resolvedor explode com qualquer valor resolvido fora deles e a
 * {@link ApprovalRule} explode com {@code true} antes de decidir. Quem tem escrita
 * direta no banco está fora do modelo de ameaça desta trava — registrado com
 * todas as letras no relatório da entrega.
 */
public class TenantWorkParameters implements WorkParameters {

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;

    public TenantWorkParameters(DataSource dataSource, String schema, UUID tenantId) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("dataSource, schema e tenantId são obrigatórios");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
    }

    @Override
    public boolean selfApproval(UUID tenant) {
        requireTenant(tenant);
        Definition def = definition("wf.self_approval");
        if (!"a".equals(def.paramClass()) || def.factoryDefault() == null
                || !Json.readTree(def.factoryDefault()).isBoolean()
                || Json.readTree(def.factoryDefault()).asBoolean()) {
            throw new IllegalStateException("wf.self_approval precisa ser classe a com padrão false no catálogo; "
                    + "encontrado: classe=" + def.paramClass() + ", padrão=" + def.factoryDefault());
        }
        boolean value = booleanValue("wf.self_approval", def);
        ApprovalRule.assertSelfApprovalForbidden(value);
        return value;
    }

    @Override
    public int delegationMaxDays(UUID tenant) {
        requireTenant(tenant);
        Definition def = definition("wf.delegation_max_days");
        int value = intValue("wf.delegation_max_days", def);
        if (value < 1) {
            throw new IllegalStateException("wf.delegation_max_days precisa ser >= 1; resolvido: " + value);
        }
        return value;
    }

    @Override
    public long escalationHours(UUID tenant) {
        requireTenant(tenant);
        Definition def = definition("wf.escalation_hours");
        long value = longValue("wf.escalation_hours", def);
        if (value < 0) {
            throw new IllegalStateException("wf.escalation_hours não pode ser negativo; resolvido: " + value);
        }
        return value;
    }

    /** Definição do catálogo com os limites legais dela (kind + valor textual), quando presentes. */
    private record Definition(String paramClass, String factoryDefault, List<String[]> limits) {
    }

    private void requireTenant(UUID tenant) {
        if (tenant == null || !tenant.equals(tenantId)) {
            throw new IllegalArgumentException("esta instância só resolve parâmetros do seu tenant");
        }
    }

    private String t(String table) {
        return "\"" + schema.replace("\"", "\"\"") + "\"." + table;
    }

    private Definition definition(String key) {
        try (Connection c = dataSource.getConnection()) {
            String paramClass;
            String factoryDefault;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT param_class, factory_default::text FROM public.parameter_definitions WHERE definition_key = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new MissingParameterException(key, "sem definição no catálogo global");
                    }
                    paramClass = rs.getString(1);
                    factoryDefault = rs.getString(2);
                }
            }
            List<String[]> limits = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT limit_kind, limit_value::text FROM public.parameter_legal_limits WHERE definition_key = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        limits.add(new String[]{rs.getString(1), rs.getString(2)});
                    }
                }
            }
            return new Definition(paramClass, factoryDefault, limits);
        } catch (SQLException e) {
            throw new IllegalStateException("leitura do catálogo de parâmetros falhou: " + e.getMessage(), e);
        }
    }

    /** Valor vigente aceito hoje (mesma consulta do TenantParameterService.currentAcceptedValue), ou null. */
    private String acceptedValue(String key) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement guc = c.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
                    guc.setString(1, tenantId.toString());
                    guc.executeQuery().close();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT v.value::text FROM " + t("parameter_versions") + " v"
                                + " WHERE v.tenant_id = ? AND v.definition_key = ? AND v.status = 'APPROVED'"
                                + " AND v.valid_from <= CURRENT_DATE AND (v.valid_to IS NULL OR v.valid_to >= CURRENT_DATE)"
                                + " AND EXISTS (SELECT 1 FROM " + t("parameter_acceptances") + " a"
                                + " WHERE a.tenant_id = v.tenant_id AND a.version_id = v.id"
                                + " AND a.content_hash = v.content_hash)"
                                + " ORDER BY v.valid_from DESC LIMIT 1")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString(1) : null;
                    }
                }
            } finally {
                c.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("leitura do parâmetro " + key + " falhou: " + e.getMessage(), e);
        }
    }

    private JsonNode resolved(String key, Definition def) {
        String json = acceptedValue(key);
        if (json == null) {
            json = def.factoryDefault();
        }
        if (json == null) {
            throw new MissingParameterException(key, "sem valor aceito e sem padrão de fábrica");
        }
        JsonNode value = Json.readTree(json);
        assertWithinLegalLimits(key, value, def);
        return value;
    }

    /**
     * Aplica os limites legais PRESENTES no catálogo ao valor resolvido (correção
     * pós-revisão m2, mesmo espírito de {@code TenantParameterService.checkCatalogLimits},
     * mas como erro de ESTADO: quem resolve parâmetro para decidir não está propondo
     * valor — um valor fora do limite aqui é catálogo adulterado/inconsistente).
     * Limites de outro tipo (contagem, referência, metodologia) não têm valor escalar
     * único para conferir nesta resolução e ficam com o console/módulo dono.
     */
    private void assertWithinLegalLimits(String key, JsonNode value, Definition def) {
        for (String[] limit : def.limits()) {
            String kind = limit[0];
            String raw = limit[1];
            if (kind == null || raw == null) {
                continue;
            }
            switch (kind) {
                case "LOCKED" -> {
                    JsonNode locked = Json.readTree(raw);
                    if (!locked.equals(value)) {
                        throw new IllegalStateException(key + " é LOCKED em " + locked
                                + " pelo limite legal do catálogo; valor resolvido: " + value
                                + " — catálogo adulterado ou versão aceita fora do limite");
                    }
                }
                case "ALLOWED_VALUES" -> {
                    JsonNode allowed = Json.readTree(raw);
                    boolean ok = false;
                    if (allowed.isArray()) {
                        for (JsonNode option : allowed) {
                            if (option.equals(value)) {
                                ok = true;
                                break;
                            }
                        }
                    }
                    if (!ok) {
                        throw new IllegalStateException(key + " resolvido para " + value
                                + ", fora de ALLOWED_VALUES " + allowed + " do catálogo"
                                + " — catálogo adulterado ou versão aceita fora do limite");
                    }
                }
                case "MIN_VALUE", "FLOOR" -> {
                    if (value.isNumber() && value.asDouble() < Double.parseDouble(raw)) {
                        throw new IllegalStateException(key + " resolvido para " + value
                                + ", abaixo do piso legal " + raw + " do catálogo");
                    }
                }
                case "MAX_VALUE" -> {
                    if (value.isNumber() && value.asDouble() > Double.parseDouble(raw)) {
                        throw new IllegalStateException(key + " resolvido para " + value
                                + ", acima do teto legal " + raw + " do catálogo");
                    }
                }
                default -> {
                    // Sem valor escalar único para conferir na resolução.
                }
            }
        }
    }

    private boolean booleanValue(String key, Definition def) {
        JsonNode value = resolved(key, def);
        if (!value.isBoolean()) {
            throw new IllegalStateException(key + " precisa ser BOOLEAN no catálogo; resolvido: " + value);
        }
        return value.asBoolean();
    }

    private int intValue(String key, Definition def) {
        long value = longValue(key, def);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException(key + " fora da faixa de INT; resolvido: " + value);
        }
        return (int) value;
    }

    private long longValue(String key, Definition def) {
        JsonNode value = resolved(key, def);
        if (!value.isIntegralNumber()) {
            throw new IllegalStateException(key + " precisa ser INT no catálogo; resolvido: " + value);
        }
        return value.asLong();
    }
}
