package dev.vality.xrates.service;

import dev.vality.adapter.common.secret.SecretRef;
import dev.vality.adapter.common.secret.SecretValue;
import dev.vality.adapter.common.secret.VaultSecretService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecretService {
    public static final String TERMINAL_ID = "terminal_id";
    public static final String SECRET_KEY = "secret_key";

    private final VaultSecretService vaultSecretService;

    @Value("${spring.application.name}")
    private String applicationName;

    public String getTerminalId(String paymentSystem) {
        SecretValue secret = vaultSecretService.getSecret(applicationName, new SecretRef(paymentSystem, TERMINAL_ID));
        return secret.getValue();
    }

    public String getSecretKey(String paymentSystem) {
        SecretValue secret = vaultSecretService.getSecret(applicationName, new SecretRef(paymentSystem, SECRET_KEY));
        return secret.getValue();
    }
}
