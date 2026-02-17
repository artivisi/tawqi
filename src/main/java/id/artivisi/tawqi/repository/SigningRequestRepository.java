package id.artivisi.tawqi.repository;

import id.artivisi.tawqi.domain.Signer;
import id.artivisi.tawqi.domain.SigningRequest;
import id.artivisi.tawqi.domain.SigningRequestStatus;
import id.artivisi.tawqi.domain.SigningWorkflow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningRequestRepository extends JpaRepository<SigningRequest, UUID> {

    List<SigningRequest> findBySignerAndStatus(Signer signer, SigningRequestStatus status);

    List<SigningRequest> findByWorkflowOrderBySequenceOrderAsc(SigningWorkflow workflow);

    Optional<SigningRequest> findByWorkflowAndSigner(SigningWorkflow workflow, Signer signer);
}
