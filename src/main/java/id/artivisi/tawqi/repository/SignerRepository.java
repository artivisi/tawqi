package id.artivisi.tawqi.repository;

import id.artivisi.tawqi.domain.Signer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignerRepository extends JpaRepository<Signer, UUID> {

    Optional<Signer> findByUsernameAndDeletedAtIsNull(String username);
}
