package dev.vality.xrates.handler;

import dev.vality.machinarium.domain.CallResultData;
import dev.vality.machinarium.domain.SignalResultData;
import dev.vality.machinarium.domain.TMachine;
import dev.vality.machinarium.domain.TMachineEvent;
import dev.vality.machinarium.handler.AbstractProcessorHandler;
import dev.vality.machinegun.msgpack.Nil;
import dev.vality.machinegun.msgpack.Value;
import dev.vality.machinegun.stateproc.ComplexAction;
import dev.vality.xrates.domain.SourceData;
import dev.vality.xrates.rate.Change;
import dev.vality.xrates.service.ExchangeRateService;
import dev.vality.xrates.util.ProtoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class ProcessorHandler extends AbstractProcessorHandler<Value, Change> {

    private final ExchangeRateService exchangeRateService;

    public ProcessorHandler(ExchangeRateService exchangeRateService) {
        super(Value.class, Change.class);
        this.exchangeRateService = exchangeRateService;
    }

    @Override
    protected SignalResultData<Change> processSignalInit(
            TMachine<Change> thriftMachine,
            Value value) {
        String machineId = thriftMachine.getMachineId();
        log.info("Trying to process signal init, namespace='{}', machineId='{}'", thriftMachine.getNs(), machineId);

        SourceData sourceData = exchangeRateService.getExchangeRatesBySourceType(machineId);

        SignalResultData<Change> signalResultData = new SignalResultData<>(
                Value.nl(new Nil()),
                Collections.singletonList(ProtoUtil.buildCreatedChange(sourceData)),
                ProtoUtil.buildComplexActionWithDeadline(sourceData.getNextExecutionTime(),
                        ProtoUtil.buildLastEventHistoryRange())
        );

        log.info("Response: {}", signalResultData);
        return signalResultData;
    }

    @Override
    protected SignalResultData<Change> processSignalTimeout(
            TMachine<Change> thriftMachine,
            List<TMachineEvent<Change>> thriftMachineEvents) {
        String machineId = thriftMachine.getMachineId();

        log.info("Trying to process signal timeout, namespace='{}', machineId='{}', events='{}'",
                thriftMachine.getNs(), machineId, thriftMachineEvents
        );

        Change change = ProtoUtil.getLastEvent(thriftMachineEvents);
        if (change == null) {
            throw new IllegalStateException("Failed to process signal timeout because previous changes not found");
        }
        SourceData sourceData = exchangeRateService.getExchangeRatesBySourceType(ProtoUtil.getUpperBound(change),
                machineId);

        SignalResultData<Change> signalResultData = new SignalResultData<>(
                Value.nl(new Nil()),
                Collections.singletonList(ProtoUtil.buildCreatedChange(sourceData)),
                ProtoUtil.buildComplexActionWithDeadline(sourceData.getNextExecutionTime(),
                        ProtoUtil.buildLastEventHistoryRange())
        );
        log.info("Response: {}", signalResultData);
        return signalResultData;
    }

    @Override
    protected CallResultData<Change> processCall(
            String namespace,
            String machineId,
            Value args,
            List<TMachineEvent<Change>> thriftMachineEvents) {
        return new CallResultData<>(
                Value.nl(new Nil()),
                ProtoUtil.getLastEvent(thriftMachineEvents),
                Collections.emptyList(),
                new ComplexAction()
        );
    }

}
