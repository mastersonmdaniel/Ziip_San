package br.com.ziipsan.platform.notification;

import br.com.ziipsan.core.api.ApiException;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Regras puras da central de notificações (P3.36), calculáveis à mão e testadas sem banco:
 *
 * <ul>
 *   <li><b>Categorias obrigatórias.</b> O conjunto vigente é o piso da plataforma
 *       ({@link #PLATFORM_FLOOR}: segurança, aprovação pendente, aviso legal) UNIÃO o que o
 *       tenant declarar em {@code notify.mandatory_categories} (classe c = piso: o tenant
 *       acrescenta, nunca remove). Preferência que tente desligar canal de categoria
 *       obrigatória é recusada com 422 nomeando o campo — em especial, <b>aviso legal não é
 *       desligável</b> (teste dedicado).</li>
 *   <li><b>Silêncio por período.</b> Janela [início, fim) no fuso do tenant; janela que cruza
 *       a meia-noite (início &gt; fim) vale nos dois trechos. Dentro do silêncio, aviso NÃO
 *       obrigatório é retido (não descartado) até o fim da janela; aviso obrigatório ignora
 *       o silêncio — silenciar não é desligar.</li>
 *   <li><b>Resumo diário.</b> Com {@link DigestMode#DAILY}, aviso não obrigatório é retido
 *       até o início do dia seguinte no fuso do tenant, quando o worker monta o resumo.
 *       Aviso obrigatório é sempre imediato.</li>
 * </ul>
 */
public final class NotificationPolicy {

    /** Piso da plataforma do parâmetro {@code notify.mandatory_categories} (aceite da P3.36). */
    public static final Set<NotificationCategory> PLATFORM_FLOOR = Set.of(
            NotificationCategory.SECURITY,
            NotificationCategory.PENDING_APPROVAL,
            NotificationCategory.LEGAL_NOTICE);

    private NotificationPolicy() {
    }

    /** Piso ∪ declaração do tenant. {@code null} do tenant é erro explícito, não conjunto vazio presumido. */
    public static Set<NotificationCategory> effectiveMandatoryCategories(Set<NotificationCategory> tenantDeclared) {
        if (tenantDeclared == null) {
            throw new IllegalArgumentException("notify.mandatory_categories do tenant é obrigatório (pode ser vazio, não nulo)");
        }
        EnumSet<NotificationCategory> effective = EnumSet.copyOf(PLATFORM_FLOOR);
        effective.addAll(tenantDeclared);
        return Set.copyOf(effective);
    }

    public static boolean isMandatory(NotificationCategory category, Set<NotificationCategory> tenantDeclared) {
        return effectiveMandatoryCategories(tenantDeclared).contains(category);
    }

    /**
     * Recusa (422) desligar qualquer canal de categoria obrigatória. É a guarda chamada por
     * TODA gravação de preferência — inclusive a do consumidor (P1.3/ED-41 leem o mesmo registro).
     */
    public static void assertCanDisable(NotificationCategory category, Set<NotificationCategory> tenantDeclared) {
        if (isMandatory(category, tenantDeclared)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "enabled", "mandatory-category",
                    "Categoria obrigatória não pode ser desligada: " + category.name()));
        }
    }

    /** Verdade se {@code instant} cai na janela de silêncio [start, end) no fuso dado. Início == fim = sem silêncio. */
    public static boolean isSilenced(Instant instant, LocalTime start, LocalTime end, ZoneId zone) {
        if (start == null || end == null || start.equals(end)) {
            return false;
        }
        LocalTime local = instant.atZone(zone).toLocalTime();
        if (start.isBefore(end)) {
            return !local.isBefore(start) && local.isBefore(end);
        }
        // Janela cruzando a meia-noite: [start, 24h) ∪ [00h, end).
        return !local.isBefore(start) || local.isBefore(end);
    }

    /** Fim da janela de silêncio que contém {@code instant} (pré-condição: {@link #isSilenced} verdade). */
    public static Instant silenceEndsAt(Instant instant, LocalTime start, LocalTime end, ZoneId zone) {
        ZonedDateTime local = instant.atZone(zone);
        if (start.isBefore(end)) {
            return local.toLocalDate().atTime(end).atZone(zone).toInstant();
        }
        // Cruzando a meia-noite: antes da meia-noite termina amanhã em end; depois, hoje em end.
        if (!local.toLocalTime().isBefore(start)) {
            return local.toLocalDate().plusDays(1).atTime(end).atZone(zone).toInstant();
        }
        return local.toLocalDate().atTime(end).atZone(zone).toInstant();
    }

    public enum DeliveryTiming { NOW, HELD }

    /** Decisão de entrega de uma linha de aviso (o {@code deliver_after} gravado na ingestão). */
    public record DeliveryDecision(DeliveryTiming timing, Instant deliverAfter) {
    }

    /**
     * Quando entregar. A decisão vale para TODA linha de entrega — inclusive a IN_APP da
     * própria central, cuja ingestão chama esta regra com as preferências do destinatário
     * (fiação no {@code NotificationCenterService.ingest}, correção pós-revisão); as linhas
     * externas (e-mail/push) são da P3.5, que lê a mesma política. Ordem das regras (a
     * primeira que retém vence, e o retido mais tarde prevalece combinando silêncio e
     * resumo): obrigatório → agora; desligado não chega aqui (a preferência filtra antes,
     * no plano de ingestão do serviço); silêncio → fim da janela; resumo diário → início
     * do dia seguinte no fuso do tenant.
     */
    public static DeliveryDecision decide(Instant now, NotificationCategory category,
                                          Set<NotificationCategory> tenantDeclared,
                                          DigestMode digestMode,
                                          LocalTime silenceStart, LocalTime silenceEnd, ZoneId zone) {
        if (isMandatory(category, tenantDeclared)) {
            return new DeliveryDecision(DeliveryTiming.NOW, now);
        }
        Instant deliverAfter = now;
        if (digestMode == DigestMode.DAILY) {
            deliverAfter = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
        }
        if (isSilenced(now, silenceStart, silenceEnd, zone)) {
            Instant silenceEndAt = silenceEndsAt(now, silenceStart, silenceEnd, zone);
            if (silenceEndAt.isAfter(deliverAfter)) {
                deliverAfter = silenceEndAt;
            }
        }
        return deliverAfter.equals(now)
                ? new DeliveryDecision(DeliveryTiming.NOW, now)
                : new DeliveryDecision(DeliveryTiming.HELD, deliverAfter);
    }
}
