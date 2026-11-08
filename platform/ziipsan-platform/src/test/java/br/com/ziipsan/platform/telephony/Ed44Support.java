package br.com.ziipsan.platform.telephony;
import br.com.ziipsan.core.sac.Customer360View;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.*;
final class Ed44Support {
    static final UUID T=UUID.fromString("00000000-0000-0000-0000-0000000000t1".replace("t","a"));
    static final UUID ATT=UUID.fromString("00000000-0000-0000-0000-0000000000a7");
    static final UUID UC1=UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID UC2=UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final class MemLog implements TelephonyPorts.AccessLogPort {
        final List<TelephonyAccessLog> entries=new ArrayList<>();
        public void append(TelephonyAccessLog e){entries.add(e);} }
    static Customer360View view(UUID uc){ return new Customer360View(uc,
        List.of(new Customer360View.BillSummary("B1",LocalDate.of(2026,3,5),new BigDecimal("100.00"),false)),
        List.of(), List.of(), List.of()); }
}
