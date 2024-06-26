package dev.vality.xrates.handler;

import dev.vality.machinarium.client.EventSinkClient;
import dev.vality.machinarium.domain.TMachineEvent;
import dev.vality.machinarium.domain.TSinkEvent;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import dev.vality.xrates.base.Rational;
import dev.vality.xrates.base.TimestampInterval;
import dev.vality.xrates.rate.*;
import dev.vality.xrates.service.SecretService;
import org.apache.thrift.TException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {"sources.needInitialize=false"})
public class EventSinkHandlerTest {

    @LocalServerPort
    private int port;

    @MockBean
    private EventSinkClient<Change> eventSinkClient;

    private EventSinkSrv.Iface client;

    @MockBean
    private SecretService secretService;

    @Before
    public void setup() throws URISyntaxException {
        client = new THSpawnClientBuilder()
                .withAddress(new URI("http://localhost:" + port + "/v1/event_sink"))
                .build(EventSinkSrv.Iface.class);
        String terminalId = "12345";
        String secretKey = "C50E41160302E0F5D6D59F1AA3925C45";
        when(secretService.getTerminalId(anyString())).thenReturn(terminalId);
        when(secretService.getSecretKey(anyString())).thenReturn(secretKey);
    }


    @Test
    public void testGetEvents() throws TException {
        List<TSinkEvent<Change>> events = buildEvents();
        EventRange eventRange = new EventRange(1);

        given(eventSinkClient.getEvents(eq(eventRange.getLimit())))
                .willReturn(events);

        List<SinkEvent> sinkEvents = client.getEvents(eventRange);
        assertEquals(1, sinkEvents.size());
        assertEquals(events.get(0).getId(), sinkEvents.get(0).getId());
        assertEquals(events.get(0).getSourceId(), sinkEvents.get(0).getSource());
        assertEquals(events.get(0).getEvent().getCreatedAt().toString(), sinkEvents.get(0).getCreatedAt());
        assertEquals(events.get(0).getEvent().getData(), sinkEvents.get(0).getPayload().getChanges().get(0));

        eventRange.setAfter(1);
        given(eventSinkClient.getEvents(eq(eventRange.getLimit()), eq(eventRange.getAfter())))
                .willReturn(Collections.emptyList());
        assertTrue(eventSinkClient.getEvents(eventRange.getLimit(), eventRange.getAfter()).isEmpty());
    }

    private List<TSinkEvent<Change>> buildEvents() {
        return Collections.singletonList(
                new TSinkEvent<>(
                        1L,
                        "namespace",
                        "CBR",
                        new TMachineEvent<>(
                                1L,
                                Instant.now(),
                                Change.created(
                                        new ExchangeRateCreated(
                                                new ExchangeRateData(
                                                        new TimestampInterval(
                                                                Instant.now().toString(),
                                                                Instant.now().toString()
                                                        ),
                                                        Collections.singletonList(
                                                                new Quote(
                                                                        new Currency(
                                                                                "RUB",
                                                                                (short) 2
                                                                        ),
                                                                        new Currency(
                                                                                "USD",
                                                                                (short) 2
                                                                        ),
                                                                        new Rational(390417, 1000000)
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

}
