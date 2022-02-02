package dev.vality.xrates.exchange;

import dev.vality.xrates.domain.ExchangeRate;
import dev.vality.xrates.domain.SourceData;
import dev.vality.xrates.exception.ProviderUnavailableResultException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class Source {

    private final ExchangeProvider exchangeProvider;

    private final CronResolver cronResolver;

    private final Instant initialTime;

    private final String sourceId;

    public SourceData getSourceDataFromInitialTime() throws ProviderUnavailableResultException {
        return getSourceData(initialTime);
    }

    public SourceData getSourceData(Instant prevUpperBound) throws ProviderUnavailableResultException {
        Instant executionTime = cronResolver.getLastExecution(prevUpperBound);
        if (cronResolver.getExecutionWithDelay(executionTime).isBefore(prevUpperBound)) {
            executionTime = cronResolver.getNextExecution(prevUpperBound);
        }

        Instant lowerBound = cronResolver.getExecutionWithDelay(executionTime);
        Instant upperBound = cronResolver.getNextExecutionWithDelay(executionTime);

        List<ExchangeRate> rates = exchangeProvider.getExchangeRates(lowerBound);

        return new SourceData(
                lowerBound,
                upperBound,
                cronResolver.getNextExecution(executionTime),
                rates
        );
    }

}
