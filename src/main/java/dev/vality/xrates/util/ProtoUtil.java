package dev.vality.xrates.util;

import dev.vality.machinarium.domain.TMachineEvent;
import dev.vality.machinarium.domain.TSinkEvent;
import dev.vality.machinegun.base.Timer;
import dev.vality.machinegun.stateproc.*;
import dev.vality.xrates.base.Rational;
import dev.vality.xrates.base.TimestampInterval;
import dev.vality.xrates.domain.ExchangeRate;
import dev.vality.xrates.domain.SourceData;
import dev.vality.xrates.rate.Event;
import dev.vality.xrates.rate.SinkEvent;
import dev.vality.xrates.rate.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.math3.fraction.BigFraction;
import org.joda.money.CurrencyUnit;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProtoUtil {

    public static ComplexAction buildComplexActionWithDeadline(Instant deadline, HistoryRange historyRange) {
        return buildComplexActionWithTimer(Timer.deadline(deadline.toString()), historyRange);
    }

    public static ComplexAction buildComplexActionWithTimer(Timer timer, HistoryRange historyRange) {
        SetTimerAction setTimerAction = new SetTimerAction();
        setTimerAction.setTimer(timer);
        setTimerAction.setRange(historyRange);

        ComplexAction complexAction = new ComplexAction();
        complexAction.setTimer(TimerAction.set_timer(setTimerAction));
        return complexAction;
    }

    public static HistoryRange buildLastEventHistoryRange() {
        HistoryRange historyRange = new HistoryRange();
        historyRange.setDirection(Direction.backward);
        historyRange.setLimit(1);
        return historyRange;
    }

    public static HistoryRange buildFirstEventHistoryRange() {
        HistoryRange historyRange = new HistoryRange();
        historyRange.setDirection(Direction.forward);
        historyRange.setLimit(1);
        return historyRange;
    }

    public static HistoryRange buildFirstEventHistoryRangeAfter(long after) {
        HistoryRange historyRange = new HistoryRange();
        historyRange.setDirection(Direction.forward);
        historyRange.setAfter(after);
        historyRange.setLimit(1);
        return historyRange;
    }

    public static Change buildCreatedChange(SourceData sourceData) {
        ExchangeRateData exchangeRateData = new ExchangeRateData();
        exchangeRateData.setInterval(
                new TimestampInterval(sourceData.getLowerBound().toString(), sourceData.getUpperBound().toString())
        );
        exchangeRateData.setQuotes(
                sourceData.getRates().stream().map(
                        exchangeRate -> new Quote(
                                buildCurrency(exchangeRate.getSourceCurrency()),
                                buildCurrency(exchangeRate.getDestinationCurrency()),
                                buildExchangeRate(exchangeRate)
                        )
                ).collect(Collectors.toList())
        );
        return Change.created(new ExchangeRateCreated(exchangeRateData));
    }

    public static Rational buildExchangeRate(ExchangeRate exchangeRate) {
        BigFraction rationalValue = MoneyUtil.exchangeRateToRationalValue(
                exchangeRate.getSourceCurrency(),
                exchangeRate.getDestinationCurrency(),
                exchangeRate.getConversionRate()
        );

        return new Rational(
                rationalValue.getNumerator().longValueExact(),
                rationalValue.getDenominator().longValueExact()
        );
    }

    public static Currency buildCurrency(CurrencyUnit currencyUnit) {
        return new Currency(currencyUnit.getCode(), (short) currencyUnit.getDecimalPlaces());
    }

    public static SinkEvent toSinkEvent(TSinkEvent<Change> changeTSinkEvent) {
        return new SinkEvent(
                changeTSinkEvent.getId(),
                changeTSinkEvent.getEvent().getCreatedAt().toString(),
                changeTSinkEvent.getSourceId(),
                new Event(Collections.singletonList(changeTSinkEvent.getEvent().getData())),
                changeTSinkEvent.getEvent().getId()
        );
    }

    public static Instant getLowerBound(Change change) {
        TimestampInterval timestampInterval = change.getCreated().getExchangeRateData().getInterval();
        return Instant.parse(timestampInterval.getLowerBoundInclusive());
    }

    public static Instant getUpperBound(Change change) {
        TimestampInterval timestampInterval = change.getCreated().getExchangeRateData().getInterval();
        return Instant.parse(timestampInterval.getUpperBoundExclusive());
    }

    public static Change getFirstEvent(List<TMachineEvent<Change>> thriftMachineEvents) {
        if (thriftMachineEvents.isEmpty()) {
            return null;
        }
        return thriftMachineEvents.get(0).getData();
    }

    public static Change getLastEvent(List<TMachineEvent<Change>> thriftMachineEvents) {
        if (thriftMachineEvents.isEmpty()) {
            return null;
        }
        return thriftMachineEvents.get(thriftMachineEvents.size() - 1).getData();
    }

}
