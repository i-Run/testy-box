package fr.irun.testy.core.dummy;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class DummyMixin {
    @JsonProperty("foo")
    public String foo;
    public String bar;
}
