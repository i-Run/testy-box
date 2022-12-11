package fr.ght1pc9kc.testy.core.dummy;

import org.junit.jupiter.api.extension.*;

import java.util.List;

public class DummyExtension implements BeforeEachCallback, BeforeAllCallback, AfterEachCallback, AfterAllCallback, ParameterResolver {

    private final List<String> calls;
    private final String extId;
    private final Object parameter;

    public DummyExtension(List<String> calls, String extId, Object parameter) {
        this.calls = calls;
        this.extId = extId;
        this.parameter = parameter;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        calls.add(extId + "_beforeAll");
    }

    @Override
    public void afterAll(ExtensionContext context) {
        calls.add(extId + "_afterAll");
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        calls.add(extId + "_beforeEach");
    }

    @Override
    public void afterEach(ExtensionContext context) {
        calls.add(extId + "_afterEach");
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        calls.add(extId + "_supportsParameter");
        Class<?> type = parameterContext.getParameter().getType();
        return parameter.getClass().equals(type);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        calls.add(extId + "_resolveParameter");
        Class<?> type = parameterContext.getParameter().getType();
        if (parameter.getClass().equals(type)) {
            return parameter;
        }
        throw new ParameterResolutionException("Fail !");
    }
}
