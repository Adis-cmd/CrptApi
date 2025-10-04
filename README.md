# CrptApi

Клиент для работы с API Честного знака.

## Использование

```java
CrptApi api = new CrptApi(TimeUnit.SECONDS, 5, "https://example.com/api");

CrptApi.Document document = new CrptApi.Document();
// заполнить поля документа

api.createDocument(document, "signature");
```

## Запуск тестов

```bash
javac Main.java CrptApi.java
java Main
```

## Описание

Класс CrptApi реализует потокобезопасное ограничение количества запросов к API.

Параметры конструктора:
- `timeUnit` - единица времени
- `requestLimit` - максимальное количество запросов за период
- `apiUrl` - адрес API
