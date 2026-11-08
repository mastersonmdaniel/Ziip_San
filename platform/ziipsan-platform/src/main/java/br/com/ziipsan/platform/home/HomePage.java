package br.com.ziipsan.platform.home;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.notification.NotificationCenterService;
import br.com.ziipsan.platform.work.WorkItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A pagina inicial composta para UM perfil de fabrica (P3.39): perfil e
 * pagina atribuida (P1.37), atalhos do perfil, minhas tarefas do usuario
 * (P2.28, ja filtradas pelo escopo dele), avisos da central (P3.36),
 * alertas do tenant (ED-07, quando existir) e no maximo quatro indicadores
 * do papel (P3.41).
 *
 * <p>Perfil DESLIGADO (auditor externo e regulador, ate a ED-75): a
 * composicao nasce com {@code profileEnabled = false} e TODAS as secoes
 * vazias — a pagina nao monta conteudo para um perfil que a P1.37 manda
 * nao expandir para permissao nenhuma.
 *
 * <p>{@link #toJson()} serializa a composicao SOMENTE pela classe
 * {@code Json} do core (nenhum JSON montado a mao), como retrato enxuto:
 * itens de trabalho e avisos entram so com os campos de exibicao (sem o
 * contrato interno inteiro das donas) e os atalhos sem
 * {@code requiredPermission}. DIVERGENCIA DECLARADA (R3 da revisao
 * cruzada, pendencia 7 do relatorio): o {@code HomeController} devolve
 * este record diretamente e o Spring o serializa com Jackson NA FORMA
 * COMPLETA (donas inteiras, atalho com {@code requiredPermission}) —
 * portanto {@code toJson()} NAO e, hoje, o JSON do endpoint, e nenhum
 * teste o afirma como contrato do endpoint (o teste existente prova so o
 * utilitario {@code toJson()} em si). Qual das duas formas sera O
 * contrato e decisao da integracao, nao desta tarefa.
 */
public record HomePage(String profileCode, String profileLabel, boolean profileEnabled,
                       String homeRoute, List<HomeShortcut> shortcuts,
                       List<WorkItem> workItems, long workItemsOverdue,
                       List<NotificationCenterService.NotificationView> notifications,
                       long notificationsUnread,
                       HomePorts.AlertsResult tenantAlerts,
                       List<HomeIndicator> indicators, int indicatorsTotal,
                       boolean indicatorsTruncated) {

    /** Teto do aceite: no maximo quatro indicadores do papel. */
    public static final int MAX_INDICATORS = 4;

    public HomePage {
        shortcuts = shortcuts == null ? List.of() : List.copyOf(shortcuts);
        workItems = workItems == null ? List.of() : List.copyOf(workItems);
        notifications = notifications == null ? List.of() : List.copyOf(notifications);
        indicators = indicators == null ? List.of() : List.copyOf(indicators);
        if (indicators.size() > MAX_INDICATORS) {
            throw new IllegalArgumentException("pagina inicial com mais de " + MAX_INDICATORS
                    + " indicadores: o teto da P3.39 e do registro, nao da tela");
        }
        if (indicatorsTotal < indicators.size()) {
            throw new IllegalArgumentException("total de indicadores menor que os exibidos");
        }
        if (indicatorsTruncated != (indicatorsTotal > indicators.size())) {
            throw new IllegalArgumentException("indicatorsTruncated incoerente com total e exibidos");
        }
    }

    /** Retrato JSON da composicao, via {@code Json} do core. */
    public String toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("profileCode", profileCode);
        root.put("profileLabel", profileLabel);
        root.put("profileEnabled", profileEnabled);
        root.put("homeRoute", homeRoute);
        root.put("shortcuts", shortcuts.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", s.code());
            m.put("label", s.label());
            m.put("route", s.route());
            return m;
        }).toList());
        root.put("workItems", workItems.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.id().toString());
            m.put("type", i.type().name());
            m.put("originModule", i.originModule());
            m.put("dueAt", i.dueAt() == null ? null : i.dueAt().toString());
            m.put("status", i.status().name());
            m.put("recordAddress", i.recordAddress());
            return m;
        }).toList());
        root.put("workItemsOverdue", workItemsOverdue);
        root.put("notifications", notifications.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.id().toString());
            m.put("eventName", n.eventName());
            m.put("category", n.category().name());
            m.put("titleCode", n.titleCode());
            m.put("createdAt", n.createdAt().toString());
            m.put("read", n.readAt() != null);
            return m;
        }).toList());
        root.put("notificationsUnread", notificationsUnread);
        Map<String, Object> alerts = new LinkedHashMap<>();
        alerts.put("available", tenantAlerts.available());
        alerts.put("items", tenantAlerts.alerts().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", a.code());
            m.put("title", a.title());
            m.put("recordAddress", a.recordAddress());
            return m;
        }).toList());
        root.put("tenantAlerts", alerts);
        root.put("indicators", indicators.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", i.code());
            m.put("name", i.name());
            m.put("unit", i.unit());
            m.put("value", i.value().toPlainString());
            m.put("semaphore", i.semaphore());
            return m;
        }).toList());
        root.put("indicatorsTotal", indicatorsTotal);
        root.put("indicatorsTruncated", indicatorsTruncated);
        return Json.write(root);
    }
}
