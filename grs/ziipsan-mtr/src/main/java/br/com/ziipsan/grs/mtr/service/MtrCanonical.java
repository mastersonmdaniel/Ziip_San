package br.com.ziipsan.grs.mtr.service;

import br.com.ziipsan.grs.mtr.domain.WasteCdf;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Conteúdo canônico do MTR e do CDF para a cadeia de hash: lista fixa de campos, em ordem fixa,
 * com formato fixo. Não inclui {@code version}, {@code updatedAt}, nem o próprio hash.
 *
 * <p>Qualquer campo novo que deva ser protegido precisa ser acrescentado aqui, o que muda o hash dos
 * documentos já emitidos: por isso o primeiro campo é a versão do formato canônico.
 */
public final class MtrCanonical {

    static final String FORMAT_VERSION = "mtr-canonical-1";
    private static final DateTimeFormatter INSTANT = DateTimeFormatter.ISO_INSTANT;

    private MtrCanonical() {
    }

    /**
     * @param driverCpf CPF do motorista EM CLARO (só dígitos), ou {@code null} se não há motorista.
     *                  Desde a V2_8 o MTR guarda só a cifra do CPF; o hash continua calculado sobre o
     *                  CPF, não sobre a cifra (que muda a cada gravação por causa do IV aleatório e
     *                  mudaria o significado da cadeia). Quem chama decifra e entrega o valor: esta
     *                  classe é pura e não lê banco nem chave.
     */
    public static String of(WasteMtr m, String driverCpf) {
        return join(List.of(
                FORMAT_VERSION,
                "mtr",
                s(m.getMtrNumber()),
                s(m.getStatus()),
                id(m.getGenerator() == null ? null : m.getGenerator().getId()),
                id(m.getTransporter() == null ? null : m.getTransporter().getId()),
                id(m.getDestination() == null ? null : m.getDestination().getId()),
                id(m.getWasteClass() == null ? null : m.getWasteClass().getId()),
                number(m.getQuantityKg()),
                s(m.getPackagingType()),
                s(m.getPackagingCount()),
                s(m.getVehiclePlate()),
                s(driverCpf),
                time(m.getIssuedAt()),
                time(m.getCollectedAt()),
                time(m.getReceivedAt()),
                time(m.getDestinedAt()),
                s(m.getGeneratorSignature()),
                s(m.getTransporterSignature()),
                s(m.getDestinationSignature())));
    }

    public static String of(WasteCdf c) {
        return join(List.of(
                FORMAT_VERSION,
                "cdf",
                id(c.getMtr() == null ? null : c.getMtr().getId()),
                id(c.getDestination() == null ? null : c.getDestination().getId()),
                s(c.getDestinationMethod()),
                s(c.getTechnicianCrea()),
                s(c.getTechnicianName()),
                time(c.getIssuedAt()),
                s(c.getPdfChecksum())));
    }

    // O separador é um caractere de controle que não aparece em texto digitado, para que
    // ("ab", "c") e ("a", "bc") não produzam o mesmo conteúdo.
    private static String join(List<String> fields) {
        StringJoiner j = new StringJoiner("\u001f");
        fields.forEach(j::add);
        return j.toString();
    }

    private static String s(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String id(UUID id) {
        return id == null ? "" : id.toString();
    }

    private static String number(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    private static String time(OffsetDateTime t) {
        return t == null ? "" : INSTANT.format(t.withOffsetSameInstant(ZoneOffset.UTC));
    }
}
