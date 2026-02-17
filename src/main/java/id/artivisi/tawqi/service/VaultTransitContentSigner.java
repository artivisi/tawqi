package id.artivisi.tawqi.service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.ContentSigner;

public class VaultTransitContentSigner implements ContentSigner {

    private static final AlgorithmIdentifier SHA256_WITH_RSA =
            new AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption,
                    org.bouncycastle.asn1.DERNull.INSTANCE);

    private final VaultPkiService vaultPkiService;
    private final String transitKeyName;
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    public VaultTransitContentSigner(VaultPkiService vaultPkiService, String transitKeyName) {
        this.vaultPkiService = vaultPkiService;
        this.transitKeyName = transitKeyName;
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return SHA256_WITH_RSA;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public byte[] getSignature() {
        try {
            byte[] dataToSign = outputStream.toByteArray();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(dataToSign);
            return vaultPkiService.signHash(transitKeyName, hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
