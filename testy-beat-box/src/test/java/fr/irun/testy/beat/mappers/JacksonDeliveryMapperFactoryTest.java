package fr.irun.testy.beat.mappers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Delivery;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonDeliveryMapperFactoryTest {

    private static final String JSON_SINGLE_MODEL = "{" +
            "\"login\": \"okenobi\"," +
            "\"name\": \"Kenobi\"," +
            "\"firstName\": \"Obiwan\"" +
            "}";

    private static final String JSON_MULTIPLE_MODEL = "[" +
            "{" +
            "\"login\": \"okenobi\"," +
            "\"name\": \"Kenobi\"," +
            "\"firstName\": \"Obiwan\"" +
            "}," +
            "{" +
            "\"login\": \"askywalker\"," +
            "\"name\": \"Skywalker\"," +
            "\"firstName\": \"Anakin\"" +
            "}" +
            "]";

    @Test
    void should_map_delivery_for_class() {
        final ObjectMapper objectMapper = new ObjectMapper();

        final DeliveryMapper<TestModel> tested = JacksonDeliveryMapperFactory.forClass(objectMapper, TestModel.class);
        assertThat(tested).isNotNull();

        final Delivery delivery = new Delivery(null, null, JSON_SINGLE_MODEL.getBytes(StandardCharsets.UTF_8));
        final TestModel actual = tested.map(delivery);
        assertThat(actual).isNotNull();
        assertThat(actual.getLogin()).isEqualTo("okenobi");
        assertThat(actual.getName()).isEqualTo("Kenobi");
        assertThat(actual.getFirstName()).isEqualTo("Obiwan");
    }

    @Test
    void should_map_for_reference_type() {
        final ObjectMapper objectMapper = new ObjectMapper();
        final TypeReference<List<TestModel>> typeReference = new TypeReference<List<TestModel>>() {
        };

        final DeliveryMapper<List<TestModel>> tested = JacksonDeliveryMapperFactory.forTypeReference(objectMapper, typeReference);
        assertThat(tested).isNotNull();

        final Delivery delivery = new Delivery(null, null, JSON_MULTIPLE_MODEL.getBytes(StandardCharsets.UTF_8));

        final List<TestModel> actualList = tested.map(delivery);
        assertThat(actualList).isNotNull();
        assertThat(actualList).hasSize(2);
        {
            final TestModel actual = actualList.get(0);
            assertThat(actual).isNotNull();
            assertThat(actual.getLogin()).isEqualTo("okenobi");
            assertThat(actual.getName()).isEqualTo("Kenobi");
            assertThat(actual.getFirstName()).isEqualTo("Obiwan");
        }
        {
            final TestModel actual = actualList.get(1);
            assertThat(actual).isNotNull();
            assertThat(actual.getLogin()).isEqualTo("askywalker");
            assertThat(actual.getName()).isEqualTo("Skywalker");
            assertThat(actual.getFirstName()).isEqualTo("Anakin");
        }
    }

    private static final class TestModel {
        private String login;
        private String name;
        private String firstName;

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
    }

}