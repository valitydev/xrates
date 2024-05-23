package dev.vality.xrates.service;

import dev.vality.machinarium.client.AutomatonClient;
import dev.vality.machinarium.domain.TMachineEvent;
import dev.vality.machinarium.exception.MachineAlreadyExistsException;
import dev.vality.machinegun.msgpack.Value;
import dev.vality.xrates.base.Rational;
import dev.vality.xrates.base.TimestampInterval;
import dev.vality.xrates.rate.*;
import dev.vality.xrates.util.ProtoUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {"sources.needInitialize=false"})
public class ExchangeRateServiceTest {

    @Autowired
    private ExchangeRateService exchangeRateService;

    @MockBean
    private AutomatonClient<Value, Change> automatonClient;
    @MockBean
    private  SecretService secretService;

    @Before
    public void setUp() {
        String terminalId = "12345";
        String secretKey = "C50E41160302E0F5D6D59F1AA3925C45";
        when(secretService.getTerminalId(anyString())).thenReturn(terminalId);
        when(secretService.getSecretKey(anyString())).thenReturn(secretKey);
    }

    @Test
    public void testInitSourcesWhenMachineAlreadyExists() {
        doThrow(MachineAlreadyExistsException.class)
                .when(automatonClient).start(any(), any());
        exchangeRateService.initSources();
    }

    @Test
    public void testGetChangeByTime() {
        String sourceId = "SOURCE";
        int daysCount = 365;
        LocalDateTime lower = LocalDate.now().minusDays(daysCount).atStartOfDay();
        LocalDateTime upper = lower.plusDays(1);

        when(
                automatonClient.getEvents(eq(sourceId), ArgumentMatchers.eq(ProtoUtil.buildFirstEventHistoryRange()))
        ).thenReturn(
                List.of(
                        new TMachineEvent<>(
                                1,
                                Instant.now(),
                                Change.created(
                                        new ExchangeRateCreated(
                                                new ExchangeRateData().setInterval(
                                                        new TimestampInterval(
                                                                lower.toInstant(ZoneOffset.UTC).toString(),
                                                                upper.toInstant(ZoneOffset.UTC).toString()
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

        when(
                automatonClient.getEvents(eq(sourceId),
                        ArgumentMatchers.eq(ProtoUtil.buildFirstEventHistoryRangeAfter(daysCount)))
        ).thenReturn(
                List.of(
                        new TMachineEvent<>(
                                1,
                                Instant.now(),
                                Change.created(
                                        new ExchangeRateCreated(
                                                new ExchangeRateData().setInterval(
                                                        new TimestampInterval(
                                                                LocalDate.now()
                                                                        .atStartOfDay()
                                                                        .toInstant(ZoneOffset.UTC)
                                                                        .toString(),
                                                                LocalDate.now()
                                                                        .plusDays(1)
                                                                        .atStartOfDay()
                                                                        .toInstant(ZoneOffset.UTC)
                                                                        .toString()
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

        Instant now = Instant.now();
        Change change = exchangeRateService.getChangeByTime("SOURCE", now);
        assertTrue(ProtoUtil.getLowerBound(change).isBefore(now));
        assertTrue(ProtoUtil.getUpperBound(change).isAfter(now));
    }

    @Test
    public void testConvertAmount() {
        ConversionRequest conversionRequest = new ConversionRequest()
                .setDatetime(Instant.now().toString())
                .setSource("USD")
                .setDestination("RUB")
                .setAmount(100);

        when(automatonClient.getEvents(any(), any()))
                .thenReturn(
                        List.of(
                                new TMachineEvent<>(
                                        1,
                                        Instant.now(),
                                        Change.created(
                                                new ExchangeRateCreated(
                                                        new ExchangeRateData()
                                                                .setInterval(
                                                                        new TimestampInterval(
                                                                                LocalDate.now()
                                                                                        .atStartOfDay()
                                                                                        .toInstant(ZoneOffset.UTC)
                                                                                        .toString(),
                                                                                LocalDate.now()
                                                                                        .plusDays(1)
                                                                                        .atStartOfDay()
                                                                                        .toInstant(ZoneOffset.UTC)
                                                                                        .toString()
                                                                        )
                                                                )
                                                                .setQuotes(List.of(new Quote(
                                                                        new Currency(
                                                                                conversionRequest.getSource(),
                                                                                (short) 2
                                                                        ),
                                                                        new Currency(
                                                                                conversionRequest.getDestination(),
                                                                                (short) 2
                                                                        ),
                                                                        new Rational(
                                                                                700247,
                                                                                10000
                                                                        )
                                                                )))
                                                )
                                        )
                                )
                        )
                );

        assertEquals(7002, exchangeRateService.getConvertedAmount("SOURCE", conversionRequest).longValue());
    }

}
