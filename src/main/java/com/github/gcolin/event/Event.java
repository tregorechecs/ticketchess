package com.github.gcolin.event;

import com.github.gcolin.organisation.OrgOwned;
import com.github.gcolin.player.DisplayPlayer;
import com.github.gcolin.event.EventStatus;
import com.github.gcolin.event.EventType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.github.gcolin.registration.PlayerSubscription;

public class Event implements OrgOwned {

    private Integer id;

    private Integer orgId;

    private String name;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private BigDecimal price;

    private Long priceCents;

    private BigDecimal youngPrice;

    private Long youngPriceCents;

    private EventStatus status = EventStatus.DRAFT;

    private EventType eventType = EventType.STANDARD;

    private List<PlayerSubscription> subscriptions = new ArrayList<>();

    /** Lazy loader installed by {@code EventDao}; replaces JPA lazy loading of {@link #subscriptions}. */
    private transient Supplier<List<PlayerSubscription>> subscriptionsLoader;

    private EventGroup eventGroup;

    private EventCollection eventCollection;

    /** Markdown description; loaded only on detail/edit queries, not on list queries. */
    private String description;

    private Map<EventOptionType, EventOption> eventOptions;

    private List<DisplayPlayer> players;

    private Integer nbSubscriptions;
    public Event() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public Integer getOrgId() {
        return orgId;
    }

    @Override
    public void setOrgId(Integer orgId) {
        this.orgId = orgId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getPrice() {
        if (price == null && priceCents != null) {
            price = fromCents(priceCents);
        }
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
        this.priceCents = toCents(price);
    }

    public BigDecimal getYoungPrice() {
        if (youngPrice == null && youngPriceCents != null) {
            youngPrice = fromCents(youngPriceCents);
        }
        return youngPrice;
    }

    public void setYoungPrice(BigDecimal youngPrice) {
        this.youngPrice = youngPrice;
        this.youngPriceCents = toCents(youngPrice);
    }

    public long getPriceCents() {
        return priceCents == null ? 0L : priceCents;
    }

    public void setPriceCents(Long priceCents) {
        this.priceCents = priceCents;
        this.price = fromCents(priceCents);
    }

    public long getYoungPriceCents() {
        return youngPriceCents == null ? 0L : youngPriceCents;
    }

    public void setYoungPriceCents(Long youngPriceCents) {
        this.youngPriceCents = youngPriceCents;
        this.youngPrice = fromCents(youngPriceCents);
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public List<PlayerSubscription> getSubscriptions() {
        if (subscriptionsLoader != null) {
            Supplier<List<PlayerSubscription>> loader = subscriptionsLoader;
            subscriptionsLoader = null;
            subscriptions = loader.get();
        }
        return subscriptions;
    }

    public void setSubscriptions(List<PlayerSubscription> subscriptions) {
        this.subscriptionsLoader = null;
        this.subscriptions = subscriptions;
    }

    public void setSubscriptionsLoader(Supplier<List<PlayerSubscription>> subscriptionsLoader) {
        this.subscriptionsLoader = subscriptionsLoader;
    }

    public List<DisplayPlayer> getPlayers() {
        return players;
    }

    public void setPlayers(List<DisplayPlayer> players) {
        this.players = players;
    }

    public Integer getNbSubscriptions() {
        return nbSubscriptions;
    }

    public void setNbSubscriptions(Integer nbSubscriptions) {
        this.nbSubscriptions = nbSubscriptions;
    }

    public Date getStartDateAsDate() {
        if (startDate == null) {
            return null;
        }
        return Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant());
    }

    public Date getEndDateAsDate() {
        if (endDate == null) {
            return null;
        }
        return Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant());
    }

    public void setEventGroup(EventGroup eventGroup) {
        this.eventGroup = eventGroup;
    }

    public EventGroup getEventGroup() {
        return eventGroup;
    }

    public boolean isFree() {
        return getPriceCents() == 0L && getYoungPriceCents() == 0L;
    }

    private static Long toCents(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private static BigDecimal fromCents(Long cents) {
        if (cents == null) {
            return null;
        }
        return BigDecimal.valueOf(cents, 2);
    }

    public Integer getMaxSubscriptions() {
        if (eventOptions == null || !eventOptions.containsKey(EventOptionType.MAX_SUBSCRIPTIONS)) {
            return null;
        }
        EventOption option = eventOptions.get(EventOptionType.MAX_SUBSCRIPTIONS);
        if (option == null || option.getValue() == null || option.getValue().isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(option.getValue());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getFfeId() {
        if (eventOptions == null || !eventOptions.containsKey(EventOptionType.FFE_ID)) {
            return null;
        }
        EventOption option = eventOptions.get(EventOptionType.FFE_ID);
        if (option == null || option.getValue() == null || option.getValue().isBlank()) {
            return null;
        }
        return option.getValue().trim();
    }

    public boolean isPointageEnabled() {
        if (eventOptions == null || !eventOptions.containsKey(EventOptionType.POINTAGE)) {
            return false;
        }
        EventOption option = eventOptions.get(EventOptionType.POINTAGE);
        return option != null && "1".equals(option.getValue());
    }

    public Map<EventOptionType, EventOption> getEventOptions() {
        return eventOptions;
    }

    public void setEventOptions(Map<EventOptionType, EventOption> eventOptions) {
        this.eventOptions = eventOptions;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public EventCollection getEventCollection() {
        return eventCollection;
    }

    public void setEventCollection(EventCollection eventCollection) {
        this.eventCollection = eventCollection;
    }

    @Override
    public String toString() {
        return "Event [id=" + id + ", name=" + name + "]";
    }
}
