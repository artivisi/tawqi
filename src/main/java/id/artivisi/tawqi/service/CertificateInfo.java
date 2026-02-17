package id.artivisi.tawqi.service;

import java.time.Instant;

public record CertificateInfo(
        String certificatePem,
        String serialNumber,
        String issuingCaPem,
        Instant expiresAt) {
}
