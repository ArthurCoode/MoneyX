package com.moneyx.merchant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Comerciante (estabelecimento) que utiliza o MoneyX para receber pagamentos.
 *
 * É a raiz do isolamento multi-tenant: nas próximas fases, API Keys,
 * pagamentos e webhooks pertencerão sempre a um Merchant.
 */
@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    private UUID id;

    @Column(name = "legal_name", nullable = false, length = 150)
    private String legalName;

    /** CPF ou CNPJ, armazenado apenas com dígitos (11 ou 14). */
    @Column(nullable = false, unique = true, length = 14)
    private String document;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Controle de concorrência otimista: evita que duas atualizações simultâneas se sobrescrevam. */
    @Version
    private Long version;

    /** Exigido pelo JPA. Protegido para que o código da aplicação não crie Merchants inválidos. */
    protected Merchant() {
    }

    public Merchant(String legalName, String document, String email) {
        this.id = UUID.randomUUID();
        this.legalName = requireText(legalName, "legalName").trim();
        this.document = normalizeDocument(document);
        this.email = requireText(email, "email").trim().toLowerCase(Locale.ROOT);
        this.status = MerchantStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void suspend() {
        if (status != MerchantStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE merchants can be suspended. Current status: " + status);
        }
        changeStatus(MerchantStatus.SUSPENDED);
    }

    public void activate() {
        if (status != MerchantStatus.SUSPENDED) {
            throw new IllegalStateException("Only SUSPENDED merchants can be activated. Current status: " + status);
        }
        changeStatus(MerchantStatus.ACTIVE);
    }

    public void close() {
        if (status == MerchantStatus.CLOSED) {
            throw new IllegalStateException("Merchant is already CLOSED.");
        }
        changeStatus(MerchantStatus.CLOSED);
    }

    /** Apenas comerciantes ACTIVE podem operar (usar a API). SUSPENDED e CLOSED ficam bloqueados. */
    public boolean isActive() {
        return status == MerchantStatus.ACTIVE;
    }

    private void changeStatus(MerchantStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    private static String normalizeDocument(String document) {
        String digits = requireText(document, "document").replaceAll("\\D", "");
        if (digits.length() != 11 && digits.length() != 14) {
            throw new IllegalArgumentException("document must have 11 (CPF) or 14 (CNPJ) digits");
        }
        return digits;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getDocument() {
        return document;
    }

    public String getEmail() {
        return email;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Merchant other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
