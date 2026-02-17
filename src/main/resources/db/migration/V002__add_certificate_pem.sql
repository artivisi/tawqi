ALTER TABLE signer_certificates ADD COLUMN certificate_pem TEXT;
ALTER TABLE signer_certificates ADD COLUMN issuing_ca_pem TEXT;
