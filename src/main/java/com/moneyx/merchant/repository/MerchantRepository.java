package com.moneyx.merchant.repository;

import com.moneyx.merchant.domain.Merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a dados de Merchant.
 *
 * O Spring Data gera a implementação em tempo de execução a partir dos nomes dos métodos
 * (ex.: existsByDocument -> SELECT ... WHERE document = ?).
 */
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    boolean existsByDocument(String document);

    boolean existsByEmail(String email);

    /** Login: o e-mail já chega normalizado (minúsculas, sem espaços), igual ao guardado. */
    Optional<Merchant> findByEmail(String email);
}
