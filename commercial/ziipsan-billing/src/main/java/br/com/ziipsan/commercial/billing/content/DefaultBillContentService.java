package br.com.ziipsan.commercial.billing.content;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Implementacao pura do {@link BillContentService} (sem banco: catalogo em codigo + R__). */
@Service
public class DefaultBillContentService implements BillContentService {

    @Override
    public List<BillFieldRequirement> requiredFields(String profileKey, LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date e obrigatoria");
        }
        return RequiredBillContent.requiredFor(profileKey, date);
    }

    @Override
    public void assertTemplateApprovable(Set<String> templateFieldKeys, Set<String> lockedFieldKeys,
                                         String profileKey, LocalDate date) {
        InvoiceTemplateContentRule.assertApprovable(templateFieldKeys, lockedFieldKeys, profileKey, date);
    }

    @Override
    public List<LegalMessageEngine.SelectedMessage> messagesFor(LegalMessageEngine.EmissionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context e obrigatorio");
        }
        return LegalMessageEngine.select(context);
    }

    @Override
    public LegalMessageEngine.EmissionProof emissionProof(UUID billId, int billVersionNo,
                                                          UUID billTemplateVersionId,
                                                          LegalMessageEngine.EmissionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context e obrigatorio");
        }
        return LegalMessageEngine.proof(billId, billVersionNo, billTemplateVersionId, context);
    }
}
