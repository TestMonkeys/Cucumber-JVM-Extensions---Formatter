package org.testmonkeys.cucumber.ext.formatters;

public enum Nodes {

    ELEMENTS("elements"),
    STEPS("steps"),
    MATCH("match"),
    RESULT("result"),
    URI("uri"),
    KEYWORD("keyword"),
    NAME("name"),
    DESCRIPTION("description"),
    LINE("line"),
    ID("id"),
    TAGS("tags"),
    TYPE("type"),
    BACKGROUND("background"),
    SCENARIO("SCENARIO"),
    DOC_STRING("doc_string"),
    ROWS("rows"),
    VAL("val"),
    OFFSET("offset"),
    ARGUMENTS("arguments"),
            LOCATION("location"),
    VALUE("value"),
    CONTENT_TYPE("content_type"),
            STATUS("status"),
    ERROR_MESSAGE("error_message"),
    DURATION("duration"),
    AFTER("after"),
    BEFORE("before"),
    CELLS("cells"),
    MIME_TYPE("mime_type"),
    DATA("data"),
    EMBEDDINGS("embeddings"),
    OUTPUT("output");

    public String value;

    Nodes(String value){
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
