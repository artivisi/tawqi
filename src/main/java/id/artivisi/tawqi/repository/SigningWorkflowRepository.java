package id.artivisi.tawqi.repository;

import id.artivisi.tawqi.domain.Document;
import id.artivisi.tawqi.domain.SigningWorkflow;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningWorkflowRepository extends JpaRepository<SigningWorkflow, UUID> {

    Optional<SigningWorkflow> findByDocument(Document document);
}
