# testy-box

[[_TOC_]]

## Description

`testy-box` is a module providing many extensions for **JUnit 5** tests:

* **testy-core-box** provides core extensions. All the other projects depend on it.
* **testy-jooq-box** provides extensions to run an in-memory H2 database. Test data can be inserted using [JOOQ](https://www.jooq.org/).
* **testy-mongo-box** provides extensions to run an in-memory [MongoDB](https://www.mongodb.com/) database. Test data can be inserted.
* **testy-beat-box** provides extensions to run an in-memory [Qpid](https://qpid.apache.org/) AMQP broker and provide reactive RabbitMQ connections.

## testy-core-box

This project provides common extensions:

* [WithObjectMapper](https://rocket.i-run.si/javadoc/fr/irun/testy/core/extensions/WithObjectMapper.html) configures a [Jackson](https://github.com/FasterXML/jackson) mapper for Java to JSON conversion.
* [ChainedExtension](https://rocket.i-run.si/javadoc/fr/irun/testy/core/extensions/ChainedExtension.html) registers other test extensions and initializes them in the order of the declaration.

### WithObjectMapper

This extension creates and stores an `ObjectMapper` at step `BeforeAll`. This mapper can be injected as parameter.

```java
@RegisterExtension
static final WithObjectMapper wObjectMapper = WithObjectMapper
            .builder()
            .addMixin(MyModel.class, MyModelMixin.class)
            .addModule(new ParameterNamesModule())
            .addModule(new JavaTimeModule())
            .build();

@BeforeAll
static void beforeClass(ObjectMapper objectMapper) {
    // (...)
}
```

### ChainedExtension

This extension registers other extensions and runs them:

* `BeforeEach` and `BeforeAll` callbacks are run in the order of the declaration.
* `AfterEach` and `AfterAll` callbacks are run in the reverse order of the declaration.
* `ParameterResolver` resolves a type with the first extension able to resolve it. If none can resolve a parameter, the parameter resolution will fail with standard JUnit exception.

This extension is usefull to register test resources in order (for instance, register the DataSource before loading the database schema):


```java
private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
        .builder()
        .setTraceLevel(DatabaseTraceLevel.ERROR)
        .setCatalog("my_db_catalog")
        .build();

private static final WithDatabaseLoaded wIrunDatabase = WithDatabaseLoaded
        .builder()
        .setDatasourceExtension(wDataSource)
        .build();

@RegisterExtension
static final ChainedExtension chain = ChainedExtension
        .outer(wDataSource)
        .append(wIrunDatabase)
        .register();
```

## testy-jooq-box

This project is used to test SQL repositorites.
It provides extensions to load an in-memory H2 database, execute SQL scripts with [Flyway](https://flywaydb.org) and insert test data.

* [WithInMemoryDatasource](https://rocket.i-run.si/javadoc/fr/irun/testy/jooq/WithInMemoryDatasource.html) loads a H2 SQL database in-memory on a named catalog.
* [WithDatabaseLoaded](https://rocket.i-run.si/javadoc/fr/irun/testy/jooq/WithDatabaseLoaded.html) creates the database schema on the catalog using [Flyway](https://flywaydb.org) SQL scripts.
* [WithDslContext](https://rocket.i-run.si/javadoc/fr/irun/testy/jooq/WithDslContext.html) creates JOOQ `DSLContext` from the input DataSource.
* [WithSampleDataLoaded](https://rocket.i-run.si/javadoc/fr/irun/testy/jooq/WithSampleDataLoaded.html) reset the content of the tables before each test using JOOQ records.

### WithInMemoryDatasource

This extension creates a named H2 database in memory.

```java
@RegisterExtension
static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
            .builder()
            .setTraceLevel(DatabaseTraceLevel.ERROR)
            .setCatalog("my_catalog")
            .build();
```

After this extension has been registered, the `DataSource` can be injected as parameter.

```java
@BeforeAll
static void beforeClass(DataSource dataSource) {
    // (...)
}

@BeforeEach
void setUp(DataSource dataSource) {
    // (...)
}
```

If the test registers more than one data source, the parameters shall be annotated with `javax.inject.Named` to distinct them:

```java
@RegisterExtension
static final WithInMemoryDatasource wDataSource1 = WithInMemoryDatasource
            .builder()
            .setCatalog("my_catalog_1")
            .build();

@RegisterExtension
static final WithInMemoryDatasource wDataSource2 = WithInMemoryDatasource
            .builder()
            .setCatalog("my_catalog_2")
            .build();

@BeforeEach
void setUp(@Named("my_catalog_1") DataSource dataSource1, 
           @Named("my_catalog_2") DataSource dataSource2) {
    // (...)
}
```

### WithDatabaseLoaded

This extension depends on a [DatasourceExtension](https://rocket.i-run.si/javadoc/fr/irun/testy/jooq/DatasourceExtension.html) and runs a [Flyway](https://flywaydb.org/) migration on the related DB catalog.

The SQL scripts shall be located into `db.migration.<catalog>` in the classpath, where `<catalog>` is the name of DataSource catalog. The names of the SQL files shall match [Flyway naming convention](https://flywaydb.org/documentation/migrations#naming).

The SQL scripts are run **before all the test methods**. They are expected to be used to create the database schema.

```java
private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
        .builder()
        .setCatalog("my_catalog")
        .build();

// SQL files shall be located in classpath:db.migration.my_catalog
private static final WithDatabaseLoaded wDatabaseLoaded = WithDatabaseLoaded
        .builder()
        .setDatasourceExtension(wDataSource)
        .build();

@RegisterExtension
static final ChainedExtension chain = ChainedExtension
        .outer(wDataSource)
        .append(wDatabaseLoaded)
        .register();
```

### WithDslContext

This extension depends on a [DatasourceExtension](https://rocket.i-run.si/javadoc/fr/irun/testy/jooq/DatasourceExtension.html) and creates a [JOOQ DSLContext](https://www.jooq.org/doc/3.13/manual/sql-building/dsl-context/) on the related DataSource.

```java
    private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
            .builder()
            .setCatalog("my_catalog")
            .build();
    private static final WithDslContext wDsl = WithDslContext
            .builder()
            .setDatasourceExtension(wDataSource)
            .build();

    @RegisterExtension
    static final ChainedExtension chain = ChainedExtension
            .outer(wDataSource)
            .append(wDsl)
            .register();
```

This DSL can be injected as parameter.

```java
@BeforeEach
void setUp(DSLContext dsl) {
    // (...)
}
```

If many catalogs are registered, the parameter shall be annotated with `javax.inject.Named`:

```java
@BeforeEach
void setUp(@Named("my_catalog_1") DSLContext dsl1, 
           @Named("my_catalog_2") DSLContext dsl2) {
    // (...)
}
```

### WithSampleDataLoaded

This extension deletes and inserts test data **before each test method**.

The test data are inserted as JOOQ records. They can be defined with classes implementing [RelationalDataSet](https://rocket.i-run.si/javadoc/fr/irun/testy/jooq/model/RelationalDataSet.html)

```java
public final class MyElementDataSet implements RelationalDataSet<MyElementRecord> {

    @Override
    public List<MyElementRecord> records() {
        // Return all the records to insert in the table
    }
}
```

These data set can be added to the extension `WithSampleDataLoaded` to setup the test data

```java
private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
        .builder()
        .setCatalog("my_catalog")
        .build();
private static final WithDatabaseLoaded wDatabaseLoaded = WithDatabaseLoaded
        .builder()
        .setDatasourceExtension(wDataSource)
        .build();
private static final WithDslContext wDSLContext = WithDslContext
        .builder()
        .setDatasourceExtension(wDataSource)
        .build();

private static final WithSampleDataLoaded wSamples = WithSampleDataLoaded
        .builder(wDSLContext)
        .addDataset(new MyElementDataSet())
        .build();

@RegisterExtension
static final ChainedExtension chain = ChainedExtension
        .outer(wDataSource)
        .append(wDatabaseLoaded)
        .append(wDSLContext)
        .append(wSamples)
        .register();
```

:fire: Only the tables related to the data sets are emptied before each test. If a test inserts rows into another table, this table shall be emptied manually. :fire:

## testy-mongo-box

This project is used to test MongoDB repositories. It provides extensions to load a Mongo database in memory:

* [WithEmbeddedMongo](https://rocket.i-run.si/javadoc/fr/irun/testy/mongo/WithEmbeddedMongo.html) runs the database in memory.
* [WithMongoData](https://rocket.i-run.si/javadoc/fr/irun/testy/mongo/WithMongoData.html) inserts test data into the database.

### WithEmbeddedMongo

This extension starts MongoDB in memory.

```java
@RegisterExtension
static final WithEmbeddedMongo wMongo = WithEmbeddedMongo
        .builder()
        .setDatabaseName("my_database")
        .build();
```

With this extension, reactive `MongoClient` and `ReactiveMongoDatabaseFactory` can be injected as parameters.

```java
@BeforeEach
void setUp(MongoClient mongoClient, 
           ReactiveMongoDatabaseFactory factory) {
    // (...)
}
```

### WithMongoData

This extension resets the content of the collections before each test method. The data of a collection can be defined by implementing [MongoDataSet](https://rocket.i-run.si/javadoc/fr/irun/testy/mongo/MongoDataSet.html).

```java
public class MyElementDataSet implements MongoDataSet<MyElement> {

    @Override
    public List<MyElement> documents() {
        // List the objects to be inserted in the collection
    }
}
```

Each data set can be associated with a specific collection with the extension.

```java
private static final WithEmbeddedMongo wMongo = WithEmbeddedMongo
        .builder()
        .build();
private static final WithMongoData wMongoData = WithMongoData
        .builder(wMongo)
        .addDataset("my_element_collection", new MyElementDataSet())
        .build();

@RegisterExtension
static final ChainedExtension wExtensions = ChainedExtension
        .outer(wMongo)
        .append(wMongoData)
        .register();
```

Optionally, a specific mapper can be used to convert objects to Mongo Documents by including the extension [WithObjectMapper](https://rocket.i-run.si/javadoc/fr/irun/testy/core/extensions/WithObjectMapper.html).

```java
private static final WithEmbeddedMongo wMongo = WithEmbeddedMongo
        .builder()
        .build();
private static final WithObjectMapper wObjectMapper = WithObjectMapper
        .builder()
        .addModule(new JavaTimeModule())
        .build();
private static final WithMongoData wMongoData = WithMongoData
        .builder(wMongo)
        .withObjectMapper(wObjectMapper)
        .addDataset("my_element_collection", new MyElementDataSet())
        .build();

@RegisterExtension
static final ChainedExtension wExtensions = ChainedExtension
        .outer(wMongo)
        .append(wObjectMapper)
        .append(wMongoData)
        .register();
```

## testy-beat-box

This project is used to test classes using RabbitMQ. It provides an extension to run an embedded AMQP broker.

* [WithRabbitMock](https://rocket.i-run.si/javadoc/fr/irun/testy/beat/extensions/WithRabbitMock.html) runs an embedded AMQP broker.

### WithRabbitMock

This extension runs an embedded AMQP broker ([Qpid](https://qpid.apache.org/) by default).

When registered, the Rabbit [SenderOptions](https://projectreactor.io/docs/rabbitmq/snapshot/api/reactor/rabbitmq/SenderOptions.html) and [ReceiverOptions](https://projectreactor.io/docs/rabbitmq/milestone/api/reactor/rabbitmq/ReceiverOptions.html) can be injected as parameters.

```java
@RegisterExtension
static final WithRabbitMock withRabbitMock = WithRabbitMock
            .builder()
            .build();

@BeforeAll
static void beforeClass(SenderOptions senderOptions,
                        ReceiverOptions receiverOptions) {
    // (...)
}
```

Before each test method, a RabbitMQ [Connection](https://www.rabbitmq.com/releases/rabbitmq-java-client/v1.4.0/rabbitmq-java-client-javadoc-1.4.0/com/rabbitmq/client/Connection.html) and a [Channel](https://www.rabbitmq.com/releases/rabbitmq-java-client/v1.4.0/rabbitmq-java-client-javadoc-1.4.0/com/rabbitmq/client/Channel.html) are opened. They can be injected as parameters.

```java
@RegisterExtension
static final WithRabbitMock withRabbitMock = WithRabbitMock
            .builder()
            .build();

@BeforeEach
void setUp(Connection connection,
           Channel channel) {
    // (...)
}
```

Queues and exchanges can also be created with the extension. When declaring a queue and an exchange, they are bound together with an empty routing key.

```java
@RegisterExtension
static final WithRabbitMock withRabbitMock = WithRabbitMock
            .builder()
            .declareQueueAndExchange("my_queue", "my_exchange")
            .build();
```

The queues are deleted automatically by closing the connection after each test method.

If queues are declared, an [AMQPReceiver](https://rocket.i-run.si/javadoc/fr/irun/testy/beat/messaging/AMQPReceiver.html) can be injected as parameter for each queue.
This receiver can consume messages on the declared queue and reply.

```java
@RegisterExtension
static final WithRabbitMock withRabbitMock = WithRabbitMock
            .builder()
            .declareQueueAndExchange("my_queue", "my_exchange")
            .build();

@Test
void myTest(AMQPReceiver receiver) {
    receiver.consumeAndReply("my-mocked-response");

    // (...)

    final Stream<Delivery> messagesOnQueue = receiver.getMessages();
}
```

If many queues are declared in the extension, the parameter shall be annotated with `javax.inject.Named`.

```java
@RegisterExtension
static final WithRabbitMock withRabbitMock = WithRabbitMock
            .builder()
            .declareQueueAndExchange("my_queue_1", "my_exchange_1")
            .declareQueueAndExchange("my_queue_2", "my_exchange_2")
            .build();

@Test
void myTest(@Named("my_queue_1") AMQPReceiver receiver1,
            @Named("my_queue_2") AMQPReceiver receiver2) {
    // (...)
}
```
