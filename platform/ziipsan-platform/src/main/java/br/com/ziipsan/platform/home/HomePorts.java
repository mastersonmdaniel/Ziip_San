package br.com.ziipsan.platform.home;

import br.com.ziipsan.platform.notification.NotificationCenterService;
import br.com.ziipsan.platform.work.WorkItem;

import java.util.List;
import java.util.UUID;

/**
 * Portas de leitura da pagina inicial (P3.39). A pagina COMPÕE o que as
 * tarefas donas ja entregam — ela nao tem motor proprio de tarefas, de
 * avisos ou de indicadores (precedente do projeto: nada de motor paralelo).
 *
 * <p>Cada porta tem o adaptador para o servico dono neste mesmo pacote,
 * EXCETO a de indicadores: o registro da P3.41 vive no modulo reporting,
 * do qual o platform nao depende — o adaptador nasce na integracao, no
 * modulo que pode depender dos dois (ver RELATORIO-P3.39, pendencia 1).
 */
public final class HomePorts {

    private HomePorts() {
    }

    /**
     * Minhas tarefas (P2.28) JA filtradas pelo escopo do usuario: a porta
     * devolve o que a caixa do usuario contem (visibilidade + delegacoes
     * vigentes da P2.28; o filtro por area da P3.30 entra depois, na caixa).
     * A pagina nao refiltra nem amplia: exibe o que a caixa devolve.
     */
    public interface WorkItems {
        List<WorkItem> inboxOf(UUID userId, int limit);
    }

    /** Avisos da central (P3.36): lista entregue ao usuario + nao lidos. */
    public interface Notifications {
        List<NotificationCenterService.NotificationView> recentOf(UUID userId, int limit);

        long unreadCountOf(UUID userId);
    }

    /**
     * Indicadores do registro da P3.41 marcados para o perfil, JA apurados
     * (valor + semaforo), na ordem do registro. A pagina corta em quatro;
     * a porta devolve a lista inteira do perfil para a pagina declarar
     * quantos ficaram fora ({@code indicatorsTruncated}).
     */
    public interface Indicators {
        List<HomeIndicator> forProfile(UUID tenantId, String profileCode);
    }

    /** Alerta do tenant exibido na pagina inicial quando a ED-07 existir. */
    public record TenantAlert(String code, String title, String recordAddress) {
        public TenantAlert {
            if (code == null || code.isBlank() || title == null || title.isBlank()) {
                throw new IllegalArgumentException("alerta do tenant exige codigo e titulo");
            }
        }
    }

    /**
     * Alertas do tenant (ED-07): a ED-07 ESTENDE esta pagina quando existir.
     * Ate la a unica implementacao e {@link UnavailableTenantAlerts}:
     * {@code available = false} e lista vazia — a pagina declara a secao como
     * indisponivel em vez de fingir uma lista de alertas. Quando a ED-07
     * nascer, ela troca a implementacao desta porta; a composicao nao muda.
     */
    public interface TenantAlerts {
        AlertsResult alertsOf(UUID tenantId);
    }

    /** Resultado da porta de alertas: fonte existe ou ainda nao (ED-07). */
    public record AlertsResult(boolean available, List<TenantAlert> alerts) {
        public AlertsResult {
            alerts = alerts == null ? List.of() : List.copyOf(alerts);
        }

        public static AlertsResult unavailable() {
            return new AlertsResult(false, List.of());
        }
    }

    /** Ate a ED-07: nenhuma fonte de alertas do tenant existe. */
    public static final class UnavailableTenantAlerts implements TenantAlerts {
        @Override
        public AlertsResult alertsOf(UUID tenantId) {
            return AlertsResult.unavailable();
        }
    }
}
