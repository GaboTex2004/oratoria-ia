package com.oratoria.backend.billing;

import java.util.UUID;
import org.springframework.stereotype.Component;
import com.oratoria.backend.shared.exception.BadRequestException;

@Component
public class MockPaymentProvider implements PaymentProvider {
    private final DemoPaymentGuard guard;
    public MockPaymentProvider(DemoPaymentGuard guard) { this.guard = guard; }
    @Override public Result process(Order order, Scenario scenario) {
        guard.requireEnabled();
        if (!"PENDING".equals(order.status()) || !"BOB".equals(order.currency())
                || order.amount() == null || order.amount().signum() <= 0 || order.amount().scale() > 2)
            throw new BadRequestException("Orden o importe inválido para el simulador.");
        String status = switch (scenario) {
            case DEMO_APPROVED -> "APPROVED";
            case DEMO_DECLINED -> "REJECTED";
            case DEMO_PENDING -> "PENDING";
        };
        return new Result(UUID.randomUUID(), status,
                status.equals("APPROVED") ? "DEMO-" + UUID.randomUUID().toString().substring(0, 12) : null);
    }
}
