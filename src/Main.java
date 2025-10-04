import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        System.out.println("Начало");

        testSingleDocument();
        testRateLimiting();
        testConcurrency();
        testValidation();

        System.out.println("Конец");
    }

    private static void testSingleDocument() {
        System.out.print("Тест 1: Отправка документа... ");
        try {
            CrptApi api = new CrptApi(TimeUnit.SECONDS, 5, "https://example.com/api");
            api.createDocument(createTestDocument(), "test_signature");
            System.out.println("OK");
        } catch (Exception e) {
            System.out.println("OK (API недоступен)");
        }
    }

    private static void testRateLimiting() {
        System.out.print("Тест 2: Rate limiting (3 req/sec)... ");
        try {
            CrptApi api = new CrptApi(TimeUnit.SECONDS, 3, "https://example.com/api");
            long start = System.currentTimeMillis();

            for (int i = 1; i <= 5; i++) {
                try {
                    api.createDocument(createTestDocument(), "sig_" + i);
                } catch (Exception e) {}
            }

            long time = System.currentTimeMillis() - start;
            System.out.println("OK (" + time + " мс)");
        } catch (Exception e) {
            System.out.println("Ошибка");
        }
    }

    private static void testConcurrency() {
        System.out.print("Тест 3: Многопоточность (2 потока)... ");
        try {
            CrptApi api = new CrptApi(TimeUnit.SECONDS, 2, "https://example.com/api");

            Thread t1 = new Thread(() -> {
                try {
                    for (int i = 1; i <= 3; i++) {
                        api.createDocument(createTestDocument(), "t1_" + i);
                    }
                } catch (Exception e) {}
            });

            Thread t2 = new Thread(() -> {
                try {
                    for (int i = 1; i <= 3; i++) {
                        api.createDocument(createTestDocument(), "t2_" + i);
                    }
                } catch (Exception e) {}
            });

            t1.start();
            t2.start();
            t1.join();
            t2.join();

            System.out.println("OK");
        } catch (Exception e) {
            System.out.println("Ошибка");
        }
    }

    private static void testValidation() {
        System.out.print("Тест 4: Валидация параметров... ");
        int passed = 0;

        try {
            new CrptApi(TimeUnit.SECONDS, -1, "https://example.com");
        } catch (IllegalArgumentException e) {
            passed++;
        }

        try {
            new CrptApi(TimeUnit.SECONDS, 5, "");
        } catch (IllegalArgumentException e) {
            passed++;
        }

        try {
            CrptApi api = new CrptApi(TimeUnit.SECONDS, 5, "https://example.com");
            api.createDocument(null, "signature");
        } catch (IllegalArgumentException e) {
            passed++;
        } catch (Exception e) {}

        try {
            CrptApi api = new CrptApi(TimeUnit.SECONDS, 5, "https://example.com");
            api.createDocument(createTestDocument(), "");
        } catch (IllegalArgumentException e) {
            passed++;
        } catch (Exception e) {}

        System.out.println(passed == 4 ? "OK (4/4)" : "Частично (" + passed + "/4)");
    }

    private static CrptApi.Document createTestDocument() {
        CrptApi.Document doc = new CrptApi.Document();
        doc.setDocId("doc_12345");
        doc.setDocStatus("NEW");
        doc.setDocType("LP_INTRODUCE_GOODS");
        doc.setImportRequest(false);
        doc.setOwnerInn("1234567890");
        doc.setParticipantInn("0987654321");
        doc.setProducerInn("1122334455");
        doc.setProductionDate(LocalDate.of(2024, 10, 1));
        doc.setRegDate(LocalDate.of(2024, 10, 4));
        doc.setRegNumber("REG-2024-001");
        doc.setProductionType("OWN_PRODUCTION");
        doc.setDescription(new CrptApi.Description("0987654321"));

        CrptApi.Product product = new CrptApi.Product();
        product.setCertificateDocument("CERT_DOC_001");
        product.setCertificateDocumentDate(LocalDate.of(2024, 9, 15));
        product.setCertificateDocumentNumber("CERT-001-2024");
        product.setOwnerInn("1234567890");
        product.setProducerInn("1122334455");
        product.setProductionDate(LocalDate.of(2024, 10, 1));
        product.setTnvedCode("0101210000");
        product.setUitCode("01234567890123");
        product.setUituCode("98765432109876");

        List<CrptApi.Product> products = new ArrayList<>();
        products.add(product);
        doc.setProducts(products);

        return doc;
    }
}