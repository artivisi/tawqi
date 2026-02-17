package id.artivisi.tawqi.service;

import java.time.Instant;

public record VerificationResult(
        boolean valid,
        String signerName,
        Instant signedAt,
        boolean signatureValid,
        boolean chainValid,
        boolean notRevoked) {

    public static VerificationResult unsigned() {
        return new VerificationResult(false, null, null, false, false, false);
    }
}
