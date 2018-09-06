package fr.irun.testy.core.extensions;

import org.junit.jupiter.api.extension.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Allow to given an order to the Registered extensions.
 * <p>
 * In this example, {@code WithEntityMongoClient} need that before method of {@code WithObjectMapper} and {@code WithEmbeddedMongo}
 * was invoked before its before method.
 *
 * <pre style="code">
 *     private static WithEmbeddedMongo wMongo = WithEmbeddedMongo.builder().build();
 *
 *     private static WithObjectMapper wMapper = WithObjectMapper.builder()
 *             .addMixin(Entity.class, EntityJacksonMixin.class)
 *             .build();
 *
 *     private static WithEntityMongoClient wEntity = WithEntityMongoClient.builder()
 *             .setEmbeddedMongoExtension(wMongo)
 *             .setObjectMapperExtension(wMapper)
 *             .build();
 *
 *     {@literal @}RegisterExtension
 *     static ChainedExtension wChained = ChainedExtension
 *             .outer(wMapper)
 *             .append(wMongo)
 *             .append(wEntity)
 *             .register();
 * </pre>
 */
public class ChainedExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private final Extension[] extensions;

    private ChainedExtension(List<Extension> extensions) {
        this.extensions = extensions.toArray(new Extension[0]);
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        for (Extension ex : extensions) {
            if (ex instanceof BeforeAllCallback) {
                ((BeforeAllCallback) ex).beforeAll(context);
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        for (int i = extensions.length - 1; i >= 0; i--) {
            Extension ex = extensions[i];
            if (ex instanceof AfterAllCallback) {
                ((AfterAllCallback) ex).afterAll(context);
            }
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        for (Extension ex : extensions) {
            if (ex instanceof BeforeEachCallback) {
                ((BeforeEachCallback) ex).beforeEach(context);
            }
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        for (int i = extensions.length - 1; i >= 0; i--) {
            Extension ex = extensions[i];
            if (ex instanceof AfterEachCallback) {
                ((AfterEachCallback) ex).afterEach(context);
            }
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        for (Extension ex : extensions) {
            if (ex instanceof ParameterResolver) {
                boolean isSupported = ((ParameterResolver) ex).supportsParameter(parameterContext, extensionContext);
                if (isSupported) return true;
            }
        }

        return false;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Object param = null;
        Exception lastException = null;
        for (Extension ex : extensions) {
            try {
                if (ex instanceof ParameterResolver) {
                    param = ((ParameterResolver) ex).resolveParameter(parameterContext, extensionContext);
                }
            } catch (Exception e) {
                lastException = e;
            }
            if (param != null)
                return param;
        }

        return new ParameterResolutionException("Unable to resolve parameter !", lastException);
    }

    public static ChainedExtensionBuilder outer(Extension ex) {
        return new ChainedExtensionBuilder(ex);
    }

    public static class ChainedExtensionBuilder {
        private List<Extension> extensions = new ArrayList<>();

        ChainedExtensionBuilder(Extension ex) {
            extensions.add(ex);
        }

        public ChainedExtensionBuilder append(Extension ex) {
            extensions.add(ex);
            return this;
        }

        public ChainedExtension register() {
            return new ChainedExtension(extensions);
        }
    }
}
