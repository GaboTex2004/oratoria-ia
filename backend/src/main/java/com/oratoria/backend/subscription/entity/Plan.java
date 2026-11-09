package com.oratoria.backend.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 40)
    private PlanCode code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal price = java.math.BigDecimal.ZERO;
    @Column(nullable = false, length = 3)
    private String currency = "BOB";

    protected Plan() {
    }

    public Plan(PlanCode code, String name) {
        this.code = code;
        this.name = name;
    }

    public UUID getId() { return id; }
    public PlanCode getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public java.math.BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public void configurePrice(java.math.BigDecimal price) {
        if (price == null || price.signum() < 0 || price.scale() > 2 || price.precision() > 12)
            throw new IllegalArgumentException("Precio de plan inválido.");
        this.price = price;
    }
}
