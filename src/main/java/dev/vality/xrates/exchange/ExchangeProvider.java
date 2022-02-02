package dev.vality.xrates.exchange;

import dev.vality.xrates.domain.ExchangeRate;
import dev.vality.xrates.exception.ProviderUnavailableResultException;

import java.time.Instant;
import java.util.List;

public interface ExchangeProvider {

    List<ExchangeRate> getExchangeRates(Instant time) throws ProviderUnavailableResultException;

}
