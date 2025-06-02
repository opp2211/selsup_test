package org.example;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final CrptClient client;

    private final int requestLimit;
    private final long timeLimitMillis;
    private final Queue<Long> requestTimestamps;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeLimitMillis = timeUnit.toMillis(1);

        this.requestTimestamps = new LinkedList<>();

        this.client = new CrptClient();
    }

    public void introduceRuGoods(ProductGroup productGroup, Document document, String signature) {
        checkAvailabilityToRequest();

        var documentCreateRequest = DocumentCreateRequest.builder()
                .documentFormat(DocumentCreateRequest.DocumentFormat.MANUAL)
                .document(document)
                .signature(signature)
                .type(DocumentCreateRequest.DocumentType.LP_INTRODUCE_GOODS)
                .build();

        client.createDocument(productGroup, documentCreateRequest);
    }

    // По сути аналогичная фукциональность реализована в библиотеке RateLimiter от Google Guava
    private synchronized void checkAvailabilityToRequest() {
        long now = System.currentTimeMillis();

        if (requestTimestamps.size() < requestLimit) {
            requestTimestamps.add(now);
            return;
        }

        long oldestRequestTime = requestTimestamps.poll();
        long diff = now - oldestRequestTime;
        long timeToWait = timeLimitMillis - diff;
        if (timeToWait > 0) {
            try {
                Thread.sleep(timeToWait);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        requestTimestamps.add(now);
    }

    public static class CrptClient {

        private static final String BASE_URI = "https://ismp.crpt.ru/api/v3";
        private static final String TOKEN = "<token>";

        private final HttpClient httpClient;
        private final ObjectMapper mapper;

        public CrptClient() {
            httpClient = HttpClient.newHttpClient();
            mapper = new ObjectMapper();
        }

        public void createDocument(ProductGroup pg, DocumentCreateRequest body) {
            try {
                var uri = URI.create(BASE_URI + "/lk/documents/create" + "?pg=" + pg.getName());
                var bodyString = mapper.writeValueAsString(body);

                var httpRequest = HttpRequest.newBuilder()
                        .uri(uri)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + TOKEN)
                        .POST(HttpRequest.BodyPublishers.ofString(bodyString))
                        .build();

                var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Unexpected response code: " + response.statusCode());
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Builder
    @Getter
    public static class DocumentCreateRequest {

        /*
         * Формат передачи
         */
        @JsonProperty("document_format")
        private DocumentFormat documentFormat;

        /*
         * Содержимое документа
         */
        @JsonProperty("product_document")
        private Document document;

        /*
         * Подпись
         */
        private String signature;

        /*
         * Тип
         */
        private DocumentType type;


        public enum DocumentFormat {
            MANUAL, //json
            XML,
            CSV,
        }

        public enum DocumentType {
            LP_INTRODUCE_GOODS,
            //other types
        }
    }

    @Builder
    @Getter
    public static class Document {

        private DocumentDescription description;

        @JsonProperty("doc_id")
        private String id;

        @JsonProperty("doc_status")
        private String status;

        @JsonProperty("doc_type")
        private String type;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("participant_inn")
        private String participantInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private LocalDate productionDate;

        @JsonProperty("production_type")
        private ProductionType productionType;

        private List<Product> products;

        @JsonProperty("reg_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private LocalDate regDate;

        @JsonProperty("reg_number")
        private String regNumber;


        @Builder
        @Getter
        public static class DocumentDescription {
            private String participantInn;
        }

        public enum ProductionType {
            OWN_PRODUCTION,
            CONTRACT_PRODUCTION,
        }

        @Builder
        @Getter
        public static class Product {
            @JsonProperty("certificate_document")
            private String certificateDocument;

            @JsonProperty("certificate_document_date")
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            private LocalDate certificateDocumentDate;

            @JsonProperty("certificate_document_number")
            private String certificateDocumentNumber;

            @JsonProperty("owner_inn")
            private String ownerInn;

            @JsonProperty("producer_inn")
            private String producerInn;

            @JsonProperty("production_date")
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            private LocalDate productionDate;

            @JsonProperty("tnved_code")
            private String tnvedCode;

            @JsonProperty("uit_code")
            private String uitCode;

            @JsonProperty("uitu_code")
            private String uituCode;
        }
    }

    @RequiredArgsConstructor
    @Getter
    public enum ProductGroup {
        CLOTHES("clothes", 1),
        //other product groups
        ;

        private final String name;
        private final int code;
    }

}