package com.moneyx.merchant.service;

import com.moneyx.apikey.exception.EmailNotVerifiedException;
import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.apikey.service.IssuedApiKey;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.exception.EmailVerificationException;
import com.moneyx.merchant.exception.EmailVerificationException.Reason;
import com.moneyx.merchant.mail.EmailVerificationMailer;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.support.MutableClock;
import com.moneyx.support.TestClockConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verificação de e-mail e chaves mx_live_ contra o PostgreSQL REAL. O "e-mail" é capturado por um mailer de teste.
 */
@SpringBootTest
@Import({TestClockConfig.class, EmailVerificationIntegrationTest.CapturingMailerConfig.class})
class EmailVerificationIntegrationTest {

    /** Depois de "agora": o Merchant usa a hora real na criação e o banco exige verificação >= criação. */
    private Instant start;

    /** Guarda os e-mails "enviados"; pode simular falha do SMTP. */
    static class CapturingMailer implements EmailVerificationMailer {
        final List<String[]> sent = new CopyOnWriteArrayList<>();
        final AtomicBoolean failing = new AtomicBoolean();

        @Override
        public void send(String to, String verificationLink) {
            if (failing.get()) {
                throw new IllegalStateException("SMTP down");
            }
            sent.add(new String[]{to, verificationLink});
        }

        String lastTokenFor(String email) {
            for (int i = sent.size() - 1; i >= 0; i--) {
                if (sent.get(i)[0].equals(email)) {
                    return sent.get(i)[1].substring(sent.get(i)[1].indexOf("token=") + "token=".length());
                }
            }
            throw new AssertionError("no email sent to " + email);
        }
    }

    @TestConfiguration
    static class CapturingMailerConfig {
        @Bean
        @Primary
        CapturingMailer capturingMailer() {
            return new CapturingMailer();
        }
    }

    @Autowired
    private MerchantService merchantService;
    @Autowired
    private EmailVerificationService verification;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private MerchantRepository merchants;
    @Autowired
    private CapturingMailer mailer;
    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> createdMerchants = new ArrayList<>();

    @BeforeEach
    void setUp() {
        start = Instant.now().plus(Duration.ofHours(1)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        clock.set(start);
        mailer.sent.clear();
        mailer.failing.set(false);
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : createdMerchants) {
            jdbc.update("DELETE FROM email_verification_tokens WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM api_keys WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM merchants WHERE id = ?", id);
        }
    }

    @Test
    void registrationSendsALinkWhoseTokenIsStoredOnlyAsAHash() {
        Merchant merchant = register();

        assertThat(mailer.sent).hasSize(1);
        String link = mailer.sent.getFirst()[1];
        assertThat(link).startsWith("http://localhost:8080/v1/merchants/verify-email?token=");
        String token = mailer.lastTokenFor(merchant.getEmail());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_verification_tokens WHERE token_hash = ?",
                Integer.class, token)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_verification_tokens WHERE merchant_id = ?",
                Integer.class, merchant.getId())).isEqualTo(1);
        assertThat(reload(merchant).isEmailVerified()).isFalse();
    }

    @Test
    void theLinkVerifiesTheEmailAndOpeningItAgainStillSucceeds() {
        Merchant merchant = register();
        String token = mailer.lastTokenFor(merchant.getEmail());
        clock.advance(Duration.ofMinutes(5));

        verification.confirm(token);
        verification.confirm(token); // o antivírus do e-mail abriu antes; a pessoa abre de novo

        assertThat(reload(merchant).getEmailVerifiedAt()).isEqualTo(start.plus(Duration.ofMinutes(5)));
    }

    @Test
    void invalidAndExpiredLinksAreRejected() {
        Merchant merchant = register();
        String token = mailer.lastTokenFor(merchant.getEmail());

        assertReason(() -> verification.confirm("not-a-token"), Reason.INVALID_TOKEN);
        assertReason(() -> verification.confirm(null), Reason.INVALID_TOKEN);
        clock.advance(Duration.ofHours(24));
        assertReason(() -> verification.confirm(token), Reason.EXPIRED_TOKEN);
        assertThat(reload(merchant).isEmailVerified()).isFalse();
    }

    @Test
    void resendingReplacesTheOldLinkAndIsLimitedToOncePerMinute() {
        Merchant merchant = register();
        String old = mailer.lastTokenFor(merchant.getEmail());

        assertReason(() -> verification.resend(merchant.getId()), Reason.TOO_SOON);
        clock.advance(Duration.ofSeconds(60));
        verification.resend(merchant.getId());
        String fresh = mailer.lastTokenFor(merchant.getEmail());

        assertThat(fresh).isNotEqualTo(old);
        assertReason(() -> verification.confirm(old), Reason.INVALID_TOKEN);
        verification.confirm(fresh);
        assertReason(() -> verification.resend(merchant.getId()), Reason.ALREADY_VERIFIED);
    }

    @Test
    void tooSoonReportsHowLongToWait() {
        Merchant merchant = register();
        clock.advance(Duration.ofSeconds(20));
        assertThatThrownBy(() -> verification.resend(merchant.getId()))
                .isInstanceOfSatisfying(EmailVerificationException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(40)));
    }

    @Test
    void noEmailIsSentWhenTheTransactionRollsBack() {
        Merchant merchant = register();
        mailer.sent.clear();
        clock.advance(Duration.ofMinutes(2));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            verification.requestFor(reload(merchant));
            status.setRollbackOnly();
        });

        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void aMailFailureDoesNotUndoTheRegistration() {
        mailer.failing.set(true);
        Merchant merchant = register();
        assertThat(merchants.findById(merchant.getId())).isPresent();
    }

    @Test
    void liveKeysRequireAVerifiedEmail() {
        Merchant merchant = register();
        assertThatThrownBy(() -> apiKeyService.issueFor(merchant.getId(), true))
                .isInstanceOf(EmailNotVerifiedException.class);

        verification.confirm(mailer.lastTokenFor(merchant.getEmail()));
        IssuedApiKey live = apiKeyService.issueFor(merchant.getId(), true);

        assertThat(live.plainText()).startsWith("mx_live_");
        assertThat(live.apiKey().isLive()).isTrue();
        assertThat(apiKeyService.findActiveByPlainText(live.plainText())).isPresent();
        // a chave do cadastro continua sendo de teste
        assertThat(apiKeyService.listFor(merchant.getId())).filteredOn(k -> !k.isLive()).hasSize(1);
    }

    @RepeatedTest(3)
    void concurrentConfirmationsOfTheSameLinkAllSucceedAndVerifyOnce() throws Exception {
        Merchant merchant = register();
        String token = mailer.lastTokenFor(merchant.getEmail());
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                verification.confirm(token);
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS); // nenhuma lança exceção
        }
        pool.shutdown();
        assertThat(reload(merchant).isEmailVerified()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM email_verification_tokens WHERE merchant_id = ? AND used_at IS NOT NULL",
                Integer.class, merchant.getId())).isEqualTo(1);
    }

    private Merchant register() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        Merchant merchant = merchantService.register("Verification Test LTDA", document,
                "verification-" + UUID.randomUUID() + "@moneyx.dev").merchant();
        createdMerchants.add(merchant.getId());
        return merchant;
    }

    private Merchant reload(Merchant merchant) {
        return merchants.findById(merchant.getId()).orElseThrow();
    }

    private static void assertReason(Runnable call, Reason reason) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(EmailVerificationException.class,
                e -> assertThat(e.getReason()).isEqualTo(reason));
    }
}
