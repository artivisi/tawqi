package id.artivisi.tawqi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tawqi")
public class TawqiProperties {

    private Storage storage = new Storage();
    private Vault vault = new Vault();
    private Signing signing = new Signing();
    private Webhook webhook = new Webhook();

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Vault getVault() {
        return vault;
    }

    public void setVault(Vault vault) {
        this.vault = vault;
    }

    public Signing getSigning() {
        return signing;
    }

    public void setSigning(Signing signing) {
        this.signing = signing;
    }

    public Webhook getWebhook() {
        return webhook;
    }

    public void setWebhook(Webhook webhook) {
        this.webhook = webhook;
    }

    public static class Storage {
        private String path = "/var/lib/tawqi/documents";
        private String type = "local";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public static class Vault {
        private String pkiMount = "pki-campus";
        private String transitMount = "transit";
        private String transitKeyPrefix = "signer-";

        public String getPkiMount() {
            return pkiMount;
        }

        public void setPkiMount(String pkiMount) {
            this.pkiMount = pkiMount;
        }

        public String getTransitMount() {
            return transitMount;
        }

        public void setTransitMount(String transitMount) {
            this.transitMount = transitMount;
        }

        public String getTransitKeyPrefix() {
            return transitKeyPrefix;
        }

        public void setTransitKeyPrefix(String transitKeyPrefix) {
            this.transitKeyPrefix = transitKeyPrefix;
        }
    }

    public static class Signing {
        private int batchPoolSize = 4;
        private String qrBaseUrl;
        private String documentIdPrefix = "TQ";

        public int getBatchPoolSize() {
            return batchPoolSize;
        }

        public void setBatchPoolSize(int batchPoolSize) {
            this.batchPoolSize = batchPoolSize;
        }

        public String getQrBaseUrl() {
            return qrBaseUrl;
        }

        public void setQrBaseUrl(String qrBaseUrl) {
            this.qrBaseUrl = qrBaseUrl;
        }

        public String getDocumentIdPrefix() {
            return documentIdPrefix;
        }

        public void setDocumentIdPrefix(String documentIdPrefix) {
            this.documentIdPrefix = documentIdPrefix;
        }
    }

    public static class Webhook {
        private int timeoutSeconds = 10;
        private int retryCount = 3;

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }
    }
}
