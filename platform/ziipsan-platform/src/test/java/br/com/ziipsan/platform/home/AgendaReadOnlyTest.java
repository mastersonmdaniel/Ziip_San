package br.com.ziipsan.platform.home;

import br.com.ziipsan.core.agenda.AgendaEventSource;
import br.com.ziipsan.platform.home.web.HomeController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aceite da P3.39: "a agenda nao cria nem altera evento (teste)". A prova e
 * estrutural, por reflexao: nem o servico da agenda, nem a porta das
 * fontes, nem o controller expoem metodo publico de escrita — nao e uma
 * convencao de uso, e a ausencia do metodo.
 */
class AgendaReadOnlyTest {

    // "register-event" NAO entra aqui (correcao R5 da revisao cruzada): era
    // entrada morta — nome de metodo Java nunca contem hifen, logo nunca
    // casava. O register do AgendaEventTypeRegistry e fiacao legitima do
    // registro de tipos e nao e varrido por este teste, de proposito.
    private static final Set<String> WRITE_PREFIXES = Set.of(
            "create", "add", "save", "update", "delete", "remove",
            "insert", "post", "put", "patch");

    private static void assertNoWriteMethods(Class<?> type) {
        for (Method m : type.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic()) {
                continue;
            }
            String name = m.getName().toLowerCase();
            assertThat(WRITE_PREFIXES.stream().anyMatch(name::startsWith))
                    .as("%s.%s nao pode ser metodo de escrita da agenda", type.getSimpleName(), m.getName())
                    .isFalse();
        }
    }

    @Test
    void agendaServiceHasNoWriteMethod() {
        assertNoWriteMethods(AgendaService.class);
    }

    @Test
    void agendaSourcePortHasNoWriteMethod() {
        assertNoWriteMethods(AgendaEventSource.class);
    }

    @Test
    void homeControllerHasOnlyGetMappings() {
        for (Method m : HomeController.class.getDeclaredMethods()) {
            boolean hasMapping = java.util.Arrays.stream(m.getAnnotations())
                    .anyMatch(a -> a.annotationType().getSimpleName().endsWith("Mapping"));
            if (hasMapping) {
                assertThat(m.isAnnotationPresent(
                        org.springframework.web.bind.annotation.GetMapping.class))
                        .as("metodo %s deve ser GET", m.getName()).isTrue();
            }
        }
        assertNoWriteMethods(HomeController.class);
    }
}
