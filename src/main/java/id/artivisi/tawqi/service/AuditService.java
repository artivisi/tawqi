package id.artivisi.tawqi.service;

import id.artivisi.tawqi.domain.AuditEntry;
import id.artivisi.tawqi.repository.AuditEntryRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;

    public AuditService(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    public void log(String entityType, UUID entityId, String action, String actor,
                    Map<String, Object> details, String ipAddress) {
        AuditEntry entry = new AuditEntry();
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setActor(actor);
        entry.setDetails(details);
        entry.setIpAddress(ipAddress);
        auditEntryRepository.save(entry);
    }

    public List<AuditEntry> getEntriesForEntity(String entityType, UUID entityId) {
        return auditEntryRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }
}
