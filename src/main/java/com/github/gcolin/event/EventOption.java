package com.github.gcolin.event;


public class EventOption {

    private Integer id;

    private EventOptionType optionType;

    private Event event;

    private String value;

    public EventOption() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public EventOptionType getOptionType() {
        return optionType;
    }

    public void setOptionType(EventOptionType optionType) {
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

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }
}
