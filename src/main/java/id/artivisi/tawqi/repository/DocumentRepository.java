package id.artivisi.tawqi.repository;

import id.artivisi.tawqi.domain.Document;
import id.artivisi.tawqi.domain.DocumentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByDocumentCodeAndDeletedAtIsNull(String documentCode);

    List<Document> findByStatusAndDeletedAtIsNull(DocumentStatus status);

    @Query(value = "SELECT nextval('document_code_seq')", nativeQuery = true)
    long getNextDocumentCodeSequence();
}
