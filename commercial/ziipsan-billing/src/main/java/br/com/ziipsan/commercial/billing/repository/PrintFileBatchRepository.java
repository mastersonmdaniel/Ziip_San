package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.PrintFileBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrintFileBatchRepository extends JpaRepository<PrintFileBatchEntity, UUID> {

    /** Lotes pela integridade do arquivo (conferencia de retransmissao a grafica). */
    List<PrintFileBatchEntity> findByFileSha256(String fileSha256);
}
