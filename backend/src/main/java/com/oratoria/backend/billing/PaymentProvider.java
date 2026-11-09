package com.oratoria.backend.billing;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentProvider {
    enum Scenario { DEMO_APPROVED, DEMO_DECLINED, DEMO_PENDING }
    record Order(UUID id, BigDecimal amount, String currency, String status) {}
    record Result(UUID transactionId, String status, String authorizationCode) {}
    Result process(Order order, Scenario scenario);
}
