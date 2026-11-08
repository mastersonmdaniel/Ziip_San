package br.com.ziipsan.core.fieldos;

import java.util.Optional;
import java.util.UUID;

/** Parametros fake compartilhados pelos testes da ED-20. */
final class TestParamsSupport {
    private TestParamsSupport() {}
    static final class SignatureParams implements FieldOsParameters {
        public Optional<Boolean> trackOnlyInShift(UUID t) { return Optional.of(true); }
        public Optional<Integer> trackRetentionDays(UUID t) { return Optional.empty(); }
        public Optional<Boolean> photoBlurFaces(UUID t) { return Optional.of(false); }
        public Optional<Integer> appointmentWindowHours(UUID t) { return Optional.empty(); }
        public Optional<CustomerAcceptance.Mode> requiredAcceptance(UUID t, String type) {
            return Optional.of(CustomerAcceptance.Mode.SIGNATURE);
        }
    }
}
