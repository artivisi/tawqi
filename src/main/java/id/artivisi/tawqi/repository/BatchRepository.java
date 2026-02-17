package id.artivisi.tawqi.repository;

import id.artivisi.tawqi.domain.Batch;
import id.artivisi.tawqi.domain.BatchStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRepository extends JpaRepository<Batch, UUID> {

    List<Batch> findByStatus(BatchStatus status);
}
