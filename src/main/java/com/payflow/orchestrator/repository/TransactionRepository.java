package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // Backs API endpoint #3 (Section A7.1): GET /api/v1/payments?merchant_order_id={id}
    Optional<Transaction> findByMerchantIdAndMerchantOrderId(UUID merchantId, String merchantOrderId);

    // Used by the reconciliation engine (Day 11-12) to match a webhook back to a transaction.
    Optional<Transaction> findByGatewayReference(String gatewayReference);
}