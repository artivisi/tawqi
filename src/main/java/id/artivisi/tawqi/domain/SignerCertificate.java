package id.artivisi.tawqi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "signer_certificates")
public class SignerCertificate extends BaseAuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signer_id", nullable = false)
    private Signer signer;

    @Column(name = "transit_key_name", nullable = false)
    private String transitKeyName;

    @Column(name = "certificate_serial")
    private String certificateSerial;

    @Column(name = "certificate_pem", columnDefinition = "TEXT")
    private String certificatePem;

    @Column(name = "issuing_ca_pem", columnDefinition = "TEXT")
    private String issuingCaPem;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    public Signer getSigner() {
        return signer;
    }

    public void setSigner(Signer signer) {
        this.signer = signer;
    }

    public String getTransitKeyName() {
        return transitKeyName;
    }

    public void setTransitKeyName(String transitKeyName) {
        this.transitKeyName = transitKeyName;
    }

    public String getCertificateSerial() {
        return certificateSerial;
    }

    public void setCertificateSerial(String certificateSerial) {
        this.certificateSerial = certificateSerial;
    }

    public String getCertificatePem() {
        return certificatePem;
    }

    public void setCertificatePem(String certificatePem) {
        this.certificatePem = certificatePem;
    }

    public String getIssuingCaPem() {
        return issuingCaPem;
    }

    public void setIssuingCaPem(String issuingCaPem) {
        this.issuingCaPem = issuingCaPem;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
