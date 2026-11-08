package com.github.gcolin.event;


public class EventCollectionOption {

    private Integer id;

    private EventCollection eventCollection;

    private EventCollectionOptionType optionType;

    private String value;

    public EventCollectionOption() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public EventCollection getEventCollection() {
        return eventCollection;
    }

    public void setEventCollection(EventCollection eventCollection) {
        this.eventCollection = eventCollection;
    }

    public EventCollectionOptionType getOptionType() {
        return optionType;
    }

    public void setOptionType(EventCollectionOptionType optionType) {
        this.optionType = optionType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /** Bean mapping alias for column {@code option_value}. */
    public String getOptionValue() {
        return value;
    }

    public void setOptionValue(String optionValue) {
        this.value = optionValue;
    }
}
