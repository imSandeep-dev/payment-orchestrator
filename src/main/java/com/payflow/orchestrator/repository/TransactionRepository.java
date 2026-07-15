package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.Transaction;
import com.payflow.orchestrator.domain.TransactionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByMerchantIdAndMerchantOrderId(UUID merchantId, String merchantOrderId);

    Optional<Transaction> findByGatewayReference(String gatewayReference);

    List<Transaction> findByStateInAndUpdatedAtBefore(List<TransactionState> states, Instant cutoff);
    List<Transaction> findByStateIn(List<TransactionState> states);


    long countByGatewayAndStateIn(String gateway, List<TransactionState> states);
    long countByGateway(String gateway);

    @Query("SELECT COALESCE(SUM(t.amountPaise), 0) FROM Transaction t")
    long sumAllAmounts();

    @Query("SELECT COALESCE(SUM(t.amountPaise), 0) FROM Transaction t WHERE t.gateway = :gateway")
    long sumAmountsByGateway(String gateway);
}