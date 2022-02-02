package dev.vality.xrates.handler;

import dev.vality.geck.serializer.Geck;
import dev.vality.machinegun.msgpack.Nil;
import dev.vality.machinegun.msgpack.Value;
import dev.vality.machinegun.stateproc.*;
import dev.vality.woody.api.flow.error.WUnavailableResultException;
import dev.vality.woody.api.flow.error.WUndefinedResultException;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import dev.vality.xrates.exception.ProviderUnavailableResultException;
import dev.vality.xrates.exception.UnknownSourceException;
import dev.vality.xrates.service.ExchangeRateService;
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
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {"sources.needInitialize=false"})
public class ProcessorHandlerTest {

    @LocalServerPort
    private int port;

    @MockBean
    private ExchangeRateService exchangeRateService;

    private ProcessorSrv.Iface client;

    @Before
    public void setup() throws URISyntaxException {
        client = new THSpawnClientBuilder()
                .withAddress(new URI("http://localhost:" + port + "/v1/processor"))
                .build(ProcessorSrv.Iface.class);
    }

    @Test(expected = WUndefinedResultException.class)
    public void testWhenInvalidSource() throws TException {
        given(exchangeRateService.getExchangeRatesBySourceType(eq("incorrect")))
                .willThrow(new UnknownSourceException("Source not found"));
        client.processSignal(
                new SignalArgs(
                        Signal.init(
                                new InitSignal(Value.bin(Geck.toMsgPack(Value.nl(new Nil()))))
                        ),
                        new Machine(
                                "rates",
                                "incorrect",
                                Collections.emptyList(),
                                new HistoryRange()
                        )
                ));
    }

    @Test(expected = WUnavailableResultException.class)
    public void testWhenProviderThrowError() throws TException {
        given(exchangeRateService.getExchangeRatesBySourceType(any()))
                .willThrow(new ProviderUnavailableResultException("test"));

        client.processSignal(
                new SignalArgs(
                        Signal.init(
                                new InitSignal(Value.bin(Geck.toMsgPack(Value.nl(new Nil()))))
                        ),
                        new Machine(
                                "rates",
                                "CBR",
                                Collections.emptyList(),
                                new HistoryRange()
                        )
                ));
    }

}
