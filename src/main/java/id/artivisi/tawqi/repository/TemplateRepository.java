package id.artivisi.tawqi.repository;

import id.artivisi.tawqi.domain.Template;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<Template, UUID> {

    List<Template> findByActiveTrueAndDeletedAtIsNull();
}
