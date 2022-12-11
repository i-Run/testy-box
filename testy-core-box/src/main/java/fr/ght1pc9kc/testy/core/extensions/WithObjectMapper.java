package fr.ght1pc9kc.testy.core.extensions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Allow getting a Jackson ObjectMapper in Tests.
 * <p>
 * By default this extension search and register modules in classpath, but it is possible
 * to pass specific module list at creation time.
 * </p>
 * <p>
 * The {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} feature is disable, only no null
 * properties was included in serialization.
 * </p>
 */
public class WithObjectMapper implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {
    private static final String P_JACKSON_MAPPER = "jackson-mapper";

    private final Set<Module> modules = new HashSet<>();
    private final Map<Class<?>, Class<?>> mixins = new HashMap<>();
    private final boolean findAndRegisterModules;

    public WithObjectMapper() {
        this.findAndRegisterModules = true;
    }

    private WithObjectMapper(boolean findAndRegisterModules) {
        this.findAndRegisterModules = findAndRegisterModules;
    }

    public static WithObjectMapperBuilder builder() {
        return new WithObjectMapperBuilder();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        ObjectMapper mapper = new ObjectMapper();
        if (findAndRegisterModules) {
            mapper.findAndRegisterModules();
        }

        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .registerModules(modules);

        mixins.forEach(mapper::addMixIn);

        Store store = getStore(context);
        store.put(P_JACKSON_MAPPER, mapper);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Store store = getStore(context);
        Object mapper = store.get(P_JACKSON_MAPPER);
        if (mapper == null) {
            beforeAll(context);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return ObjectMapper.class.equals(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        if (ObjectMapper.class.equals(parameterContext.getParameter().getType())) {
            return getStore(extensionContext).get(P_JACKSON_MAPPER);
        }
        throw new ParameterResolutionException("Unable to resolve ObjectMapper !");
    }

    public ObjectMapper getObjectMapper(ExtensionContext context) {
        return getStore(context).get(P_JACKSON_MAPPER, ObjectMapper.class);
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass()));
    }

    /**
     * Allow to build a more complex ObjectMapper with {@link Module} and Mixin
     * <p>
     * Usage :
     * <pre style="code">
     *     {@literal @}RegisterExtension
     *     WithObjectMapper wMapper = WithObjectMapper.builder()
     *             .dontFindAndRegisterModules()
     *             .addModule(new ParameterNamesModule())
     *             .addModule(new JSR310Module())
     *             .addMixin(Dummy.class, DummyMixin.class)
     *             .build();
     * </pre>
     */
    public static class WithObjectMapperBuilder {
        private final Set<Module> modules = new HashSet<>();
        private final Map<Class<?>, Class<?>> mixins = new HashMap<>();
        private boolean findAndRegisterModules = true;

        /**
         * Avoid looking for Modules in classpath and register there
         *
         * @return the builder
         */
        public WithObjectMapperBuilder dontFindAndRegisterModules() {
            findAndRegisterModules = false;
            return this;
        }

        /**
         * Register specific {@link Module} to ObjectMapper
         *
         * @param module The Module to register
         * @return the builder
         */
        public WithObjectMapperBuilder addModule(Module module) {
            this.modules.add(module);
            return this;
        }

        /**
         * Register multiple {@link Module}s at the same time
         *
         * @param modules The collection of Modules to register
         * @return the builder
         */
        public WithObjectMapperBuilder addModules(Collection<Module> modules) {
            this.modules.addAll(modules);
            return this;
        }

        /**
         * Register Mixin to the {@link ObjectMapper}
         *
         * @param entityClass The entity class
         * @param mixinClass  The Mixin classe
         * @return the builder
         */
        public WithObjectMapperBuilder addMixin(Class<?> entityClass, Class<?> mixinClass) {
            this.mixins.put(entityClass, mixinClass);
            return this;
        }

        /**
         * Build the Object Mapper junit extension
         *
         * @return The extension
         */
        public WithObjectMapper build() {
            WithObjectMapper withObjectMapper = new WithObjectMapper(findAndRegisterModules);
            withObjectMapper.modules.addAll(this.modules);
            withObjectMapper.mixins.putAll(this.mixins);
            return withObjectMapper;
        }
    }
}
