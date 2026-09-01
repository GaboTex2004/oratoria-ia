package com.oratoria.backend.subscription.repository;

import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.entity.SubscriptionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findFirstByUserIdAndStatusOrderByStartsAtDesc(
            UUID userId, SubscriptionStatus status);
    long countByUserId(UUID userId);
}
