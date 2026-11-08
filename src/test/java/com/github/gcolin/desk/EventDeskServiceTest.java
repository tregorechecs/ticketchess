package com.github.gcolin.desk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.event.Event;
import com.github.gcolin.event.EventDao;
import com.github.gcolin.event.EventStatus;
import com.github.gcolin.event.EventType;
import com.github.gcolin.payment.PaymentStatus;
import com.github.gcolin.payment.PaymentType;
import com.github.gcolin.platform.Caches;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.SendMail;
import com.github.gcolin.platform.TestContext;
import com.github.gcolin.platform.TestDb;
import com.github.gcolin.player.Find;
import com.github.gcolin.player.Player;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import com.github.gcolin.registration.PlayerSubscriptionOption;
import com.github.gcolin.registration.PlayerSubscriptionOptionDao;
import com.github.gcolin.registration.PlayerSubscriptionOptionStatus;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EventDeskServiceTest {

    private EventDeskService service;
    private DataSource dataSource;
    private Find find;
    private Caches caches;
    private int eventId;
    private int subId;

    @BeforeEach
    void setUp() throws Exception {
        service = new EventDeskService();
        dataSource = TestDb.newDataSource();
        find = mock(Find.class);
        caches = new Caches();
        SendMail mail = mock(SendMail.class);

        service.setDataSource(dataSource);
        inject(service, "caches", caches);
        inject(service, "mail", mail);

        Player player = new Player();
        player.setName("Doe");
        player.setFirstname("John");
        player.setCategory("SenM");
        player.setRating("1800");
        player.setNrffe("A00001");
        when(find.player(anyString(), any())).thenReturn(player);

        withFind(() -> RequestContext.run(dataSource, () -> {
            Event event = new Event();
            event.setName("Open");
            event.setStatus(EventStatus.ACTIVE);
            event.setEventType(EventType.STANDARD);
            event.setPriceCents(1000L);
            event.setYoungPriceCents(500L);
            event.setStartDate(LocalDate.of(2030, 1, 1).atStartOfDay());
            event.setEndDate(LocalDate.of(2030, 1, 2).atStartOfDay());
            new EventDao().persist(event);
            eventId = event.getId();

            PlayerSubscription sub = new PlayerSubscription();
            sub.setEvent(event);
            sub.setStatus(PlayerSubscriptionStatus.NOT_PAID);
            sub.setNrFfe("A00001");
            sub.setCreationUser("player@example.com");
            sub.setAmountCents(1000L);
            new PlayerSubscriptionDao().persist(sub);
            subId = sub.getId();
            return null;
        }));
    }

    private void withFind(Runnable test) {
        try (TestContext ignored = TestContext.open(find)) {
            test.run();
        }
    }

    @Test
    void applyOpsShouldUpdateAttendanceAndStatus() {
        withFind(() -> {
            EventDeskOp op = new EventDeskOp();
            op.setId("op-1");
            op.setSubId(subId);
            op.setPresent(true);
            op.setStatus("PAID");
            op.setClientTs(1L);

            List<String> acked = service.applyOps(eventId, List.of(op));
            assertEquals(List.of("op-1"), acked);

            RequestContext.run(dataSource, () -> {
                PlayerSubscription sub = new PlayerSubscriptionDao().find(subId);
                assertTrue(sub.getAttendanceAt() != null);
                assertEquals(PlayerSubscriptionStatus.PAID, sub.getStatus());
                assertNotNull(sub.getPayment());
                assertEquals(PaymentType.CASH, sub.getPayment().getType());
                assertEquals(PaymentStatus.PAID, sub.getPayment().getStatus());
                assertEquals(10.0, sub.getPayment().getAmount());
                assertEquals("player@example.com", sub.getPayment().getUserEmail());
                return null;
            });
        });
    }

    @Test
    void applyOpsShouldCreateCashPaymentForOption() {
        withFind(() -> {
            int optionId = RequestContext.run(dataSource, () -> {
                PlayerSubscription sub = new PlayerSubscriptionDao().find(subId);
                PlayerSubscriptionOption option = new PlayerSubscriptionOption();
                option.setPlayerSubscription(sub);
                option.setStatus(PlayerSubscriptionOptionStatus.NOT_PAID);
                option.setAmountCents(500L);
                option.setDescription("Repas");
                new PlayerSubscriptionOptionDao().persist(option);
                return option.getId();
            });

            EventDeskOp op = new EventDeskOp();
            op.setId("op-opt-1");
            op.setSubId(subId);
            op.setOptionId(optionId);
            op.setStatus("PAID");
            op.setClientTs(1L);

            List<String> acked = service.applyOps(eventId, List.of(op));
            assertEquals(List.of("op-opt-1"), acked);

            RequestContext.run(dataSource, () -> {
                PlayerSubscriptionOption option = new PlayerSubscriptionOptionDao().find(optionId);
                assertEquals(PlayerSubscriptionOptionStatus.PAID, option.getStatus());
                assertNotNull(option.getPayment());
                assertEquals(PaymentType.CASH, option.getPayment().getType());
                assertEquals(PaymentStatus.PAID, option.getPayment().getStatus());
                assertEquals(5.0, option.getPayment().getAmount());
                assertEquals("player@example.com", option.getPayment().getUserEmail());
                return null;
            });
        });
    }

    @Test
    void snapshotShouldMapPlayers() {
        withFind(() -> {
            List<EventDeskPlayerDto> dtos = service.snapshot(eventId);
            assertEquals(1, dtos.size());
            assertEquals(subId, dtos.get(0).getSubId());
            assertEquals("Doe", dtos.get(0).getName());
            assertFalse(dtos.get(0).isPresent());
            assertEquals("NOT_PAID", dtos.get(0).getStatus());
        });
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
