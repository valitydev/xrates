package dev.vality.xrates.exchange.impl.provider.psb;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.adapter.common.secret.VaultSecretService;
import dev.vality.xrates.domain.ExchangeRate;
import dev.vality.xrates.exception.ProviderUnavailableResultException;
import dev.vality.xrates.exchange.ExchangeProvider;
import dev.vality.xrates.exchange.impl.provider.psb.data.PsbExchangeRootData;
import dev.vality.xrates.exchange.impl.provider.psb.data.PsbPaymentSystem;
import dev.vality.xrates.service.SecretService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.joda.money.CurrencyUnit;
import org.springframework.core.NestedRuntimeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class PsbExchangeProvider implements ExchangeProvider {

    public static final String DEFAULT_ENDPOINT = "https://3ds.payment.ru/cgi-bin/curr_rate_by_date";

    public static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("Europe/Moscow");

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final CurrencyUnit DESTINATION_CURRENCY_UNIT = CurrencyUnit.of("RUB");

    private final String url;

    private final ZoneId timezone;

    private final PsbPaymentSystem psbPaymentSystem;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    private final SecretService secretService;

    public PsbExchangeProvider(
            PsbPaymentSystem psbPaymentSystem,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            SecretService secretService) {
        this(DEFAULT_ENDPOINT, psbPaymentSystem, restTemplate, objectMapper, secretService);
    }

    public PsbExchangeProvider(
            String url,
            PsbPaymentSystem psbPaymentSystem,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            SecretService secretService) {
        this(url, DEFAULT_TIMEZONE, psbPaymentSystem, restTemplate, objectMapper, secretService);
    }

    public PsbExchangeProvider(
            String url,
            ZoneId timezone,
            PsbPaymentSystem psbPaymentSystem,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            SecretService secretService) {
        this.url = url;
        this.timezone = timezone;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.psbPaymentSystem = psbPaymentSystem;
        this.secretService = secretService;
    }

    @Override
    public List<ExchangeRate> getExchangeRates(Instant time) {
        log.info("Trying to get exchange rates from psb endpoint, url='{}', time='{}'", url, time);
        LocalDate date = time.atZone(timezone).toLocalDate();
        String paymentSystem = psbPaymentSystem.getValue();
        String terminalId = secretService.getTerminalId(paymentSystem);
        String secretKey = secretService.getSecretKey(psbPaymentSystem.getValue());

        PsbExchangeRootData psbExchangeRootData = request(buildUrl(url, terminalId, secretKey, date));
        validateResponse(psbExchangeRootData);

        List<ExchangeRate> exchangeRates = psbExchangeRootData.getRates().stream()
                .filter(currency -> paymentSystem.equals(currency.getIps()))
                .map(currency -> new ExchangeRate(
                        CurrencyUnit.of(currency.getCurrencyCode()),
                        DESTINATION_CURRENCY_UNIT,
                        currency.getValue()
                )).collect(Collectors.toList());

        log.info(
                "Exchange rates from psb have been retrieved, url='{}', time='{}', exchangeRates='{}'",
                url,
                time,
                exchangeRates
        );
        return exchangeRates;
    }

    private PsbExchangeRootData request(String url) {
        try {
            return objectMapper.readValue(restTemplate.getForObject(url, String.class), PsbExchangeRootData.class);
        } catch (IOException | NestedRuntimeException ex) {
            throw new ProviderUnavailableResultException(String.format(
                    "Failed to get data from psb endpoint, url='%s'",
                    url
            ), ex);
        }
    }

    private String buildUrl(String endpoint, String terminalId, String secretKey, LocalDate date) {
        return UriComponentsBuilder
                .fromUriString(endpoint)
                .queryParam("TERMINAL", terminalId)
                .queryParam("DATE", date.format(DATE_TIME_FORMATTER))
                .queryParam("P_SIGN", buildSign(terminalId, date.format(DATE_TIME_FORMATTER), secretKey))
                .build()
                .toUriString();
    }

    private String buildSign(String terminalId, String date, String secretKey) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(Hex.decodeHex(secretKey), HmacAlgorithms.HMAC_SHA_1.getName());
            Mac mac = Mac.getInstance(HmacAlgorithms.HMAC_SHA_1.getName());
            mac.init(keySpec);

            return Hex.encodeHexString(
                    mac.doFinal(prepareDataForSign(terminalId, date).getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException | DecoderException ex) {
            throw new ProviderUnavailableResultException(ex);
        }
    }

    private String prepareDataForSign(String... parameters) {
        StringBuilder sb = new StringBuilder();
        for (String parameter : parameters) {
            sb.append(parameter.length());
            sb.append(parameter);
        }
        return sb.toString();
    }

    private void validateResponse(PsbExchangeRootData psbExchangeRootData) {
        if (psbExchangeRootData.hasError()) {
            throw new ProviderUnavailableResultException(String.format(
                    "Error in psb response, error='%s'",
                    psbExchangeRootData.getError()
            ));
        }
        if (psbExchangeRootData.getRates() == null || psbExchangeRootData.getRates().isEmpty()) {
            throw new ProviderUnavailableResultException("Empty currency list in psb response");
        }
    }
}
