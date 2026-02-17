package id.artivisi.tawqi.repository;

import id.artivisi.tawqi.domain.Signer;
import id.artivisi.tawqi.domain.SignerCertificate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignerCertificateRepository extends JpaRepository<SignerCertificate, UUID> {

    Optional<SignerCertificate> findBySignerAndActiveTrue(Signer signer);
}
