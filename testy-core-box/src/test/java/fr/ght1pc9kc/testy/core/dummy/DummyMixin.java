package fr.ght1pc9kc.testy.core.dummy;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class DummyMixin {
    @JsonProperty("foo")
    public String foo;
    public String bar;
}
