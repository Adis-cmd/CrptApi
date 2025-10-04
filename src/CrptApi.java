import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Потокобезопасный клиент для работы с API Честного Знака с поддержкой ограничения количества запросов.
 */
public class CrptApi {
    private final HttpClient httpClient;
    private final Lock lock;
    private final String apiUrl;
    private final long intervalMillis;
    private final int requestLimit;
    private final Queue<Long> requestTimestamps;
    private final ObjectMapper objectMapper;

    /**
     * Создает экземпляр CrptApi с ограничением количества запросов.
     *
     * @param timeUnit     единица времени для интервала ограничения (секунда, минута и т.д.)
     * @param requestLimit максимальное количество запросов за интервал (должно быть положительным)
     * @param apiUrl       URL конечной точки API (не может быть null или пустым)
     * @throws IllegalArgumentException если requestLimit <= 0 или apiUrl невалиден
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit, String apiUrl) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("requestLimit must be positive");
        }
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("apiUrl cannot be null or empty");
        }

        this.intervalMillis = timeUnit.toMillis(1);
        this.requestLimit = requestLimit;
        this.lock = new ReentrantLock(true);
        this.httpClient = HttpClient.newHttpClient();
        this.requestTimestamps = new LinkedList<>();
        this.apiUrl = apiUrl;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Создает документ для ввода в оборот товара, произведенного в РФ.
     *
     * @param document  документ с информацией о товаре
     * @param signature строка с цифровой подписью
     * @throws IllegalArgumentException если document или signature равны null/пустые
     * @throws InterruptedException     если поток прерван во время ожидания лимита запросов
     * @throws IOException              если произошла ошибка сети или сериализации
     */
    public void createDocument(Document document, String signature)
            throws InterruptedException, IOException {
        if (document == null) {
            throw new IllegalArgumentException("document cannot be null");
        }
        if (signature == null || signature.trim().isEmpty()) {
            throw new IllegalArgumentException("signature cannot be null or empty");
        }

        waitForRateLimit();

        String jsonBody = objectMapper.writeValueAsString(
                new DocumentRequest(document, signature)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Обеспечивает соблюдение лимита запросов, блокируя при необходимости.
     */
    private void waitForRateLimit() throws InterruptedException {
        lock.lock();
        try {
            long now = System.currentTimeMillis();

            while (!requestTimestamps.isEmpty() &&
                    now - requestTimestamps.peek() >= intervalMillis) {
                requestTimestamps.poll();
            }

            while (requestTimestamps.size() >= requestLimit) {
                long oldestTimestamp = requestTimestamps.peek();
                long waitTime = intervalMillis - (now - oldestTimestamp);

                if (waitTime > 0) {
                    lock.unlock();
                    try {
                        Thread.sleep(waitTime);
                    } finally {
                        lock.lock();
                    }
                    now = System.currentTimeMillis();

                    while (!requestTimestamps.isEmpty() &&
                            now - requestTimestamps.peek() >= intervalMillis) {
                        requestTimestamps.poll();
                    }
                }
            }

            requestTimestamps.offer(now);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Обертка для документа и подписи при отправке в API.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class DocumentRequest {
        @JsonProperty("document")
        private final Document document;

        @JsonProperty("signature")
        private final String signature;

        public DocumentRequest(Document document, String signature) {
            this.document = document;
            this.signature = signature;
        }
    }

    /**
     * Документ для ввода в оборот товара, произведенного в РФ (LP_INTRODUCE_GOODS).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Document {
        @JsonProperty("description")
        private Description description;

        @JsonProperty("doc_id")
        private String docId;

        @JsonProperty("doc_status")
        private String docStatus;

        @JsonProperty("doc_type")
        private String docType;

        @JsonProperty("importRequest")
        private Boolean importRequest;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("participant_inn")
        private String participantInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        private LocalDate productionDate;

        @JsonProperty("production_type")
        private String productionType;

        @JsonProperty("products")
        private List<Product> products;
        @JsonProperty("reg_date")
        private LocalDate regDate;

        @JsonProperty("reg_number")
        private String regNumber;

        public Document() {}

        public Description getDescription() {
            return description;
        }

        public void setDescription(Description description) {
            this.description = description;
        }

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getDocStatus() {
            return docStatus;
        }

        public void setDocStatus(String docStatus) {
            this.docStatus = docStatus;
        }

        public String getDocType() {
            return docType;
        }

        public void setDocType(String docType) {
            this.docType = docType;
        }

        public Boolean getImportRequest() {
            return importRequest;
        }

        public void setImportRequest(Boolean importRequest) {
            this.importRequest = importRequest;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public LocalDate getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(LocalDate productionDate) {
            this.productionDate = productionDate;
        }

        public String getProductionType() {
            return productionType;
        }

        public void setProductionType(String productionType) {
            this.productionType = productionType;
        }

        public List<Product> getProducts() {
            return products;
        }

        public void setProducts(List<Product> products) {
            this.products = products;
        }

        public LocalDate getRegDate() {
            return regDate;
        }

        public void setRegDate(LocalDate regDate) {
            this.regDate = regDate;
        }

        public String getRegNumber() {
            return regNumber;
        }

        public void setRegNumber(String regNumber) {
            this.regNumber = regNumber;
        }
    }

    /**
     * Секция описания документа.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Description {
        @JsonProperty("participantInn")
        private String participantInn;

        public Description() {}

        public Description(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }
    }

    /**
     * Информация о товаре.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Product {
        @JsonProperty("certificate_document")
        private String certificateDocument;


        @JsonProperty("certificate_document_date")
        private LocalDate certificateDocumentDate;

        @JsonProperty("certificate_document_number")
        private String certificateDocumentNumber;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        private LocalDate productionDate;

        @JsonProperty("tnved_code")
        private String tnvedCode;

        @JsonProperty("uit_code")
        private String uitCode;

        @JsonProperty("uitu_code")
        private String uituCode;

        public Product() {}

        public String getCertificateDocument() {
            return certificateDocument;
        }

        public void setCertificateDocument(String certificateDocument) {
            this.certificateDocument = certificateDocument;
        }

        public LocalDate getCertificateDocumentDate() {
            return certificateDocumentDate;
        }

        public void setCertificateDocumentDate(LocalDate certificateDocumentDate) {
            this.certificateDocumentDate = certificateDocumentDate;
        }

        public String getCertificateDocumentNumber() {
            return certificateDocumentNumber;
        }

        public void setCertificateDocumentNumber(String certificateDocumentNumber) {
            this.certificateDocumentNumber = certificateDocumentNumber;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public LocalDate getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(LocalDate productionDate) {
            this.productionDate = productionDate;
        }

        public String getTnvedCode() {
            return tnvedCode;
        }

        public void setTnvedCode(String tnvedCode) {
            this.tnvedCode = tnvedCode;
        }

        public String getUitCode() {
            return uitCode;
        }

        public void setUitCode(String uitCode) {
            this.uitCode = uitCode;
        }

        public String getUituCode() {
            return uituCode;
        }

        public void setUituCode(String uituCode) {
            this.uituCode = uituCode;
        }
    }
}