package com.moneyx.merchant.service;

import com.jayway.jsonpath.JsonPath;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.domain.MerchantSession;
import com.moneyx.merchant.exception.InvalidCredentialsException;
import com.moneyx.merchant.exception.PasswordResetException;
import com.moneyx.merchant.exception.TooManyAttemptsException;
import com.moneyx.merchant.mail.PasswordResetMailer;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login do dashboard (Etapa 8b) contra o PostgreSQL REAL e o filtro de segurança REAL: senha Argon2id, sessão com
 * expiração por inatividade e absoluta, logout, limite de tentativas e redefinição de senha por link.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestClockConfig.class, DashboardLoginIntegrationTest.CapturingResetMailerConfig.class})
class DashboardLoginIntegrationTest {

    private static final String PASSWORD = "senha-forte-123";

    static class CapturingResetMailer implements PasswordResetMailer {
        final List<String[]> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(String to, String resetLink) {
            sent.add(new String[]{to, resetLink});
        }

        String lastTokenFor(String email) {
            for (int i = sent.size() - 1; i >= 0; i--) {
                if (sent.get(i)[0].equals(email)) {
                    return sent.get(i)[1].substring(sent.get(i)[1].indexOf("token=") + "token=".length());
                }
            }
            throw new AssertionError("no reset email to " + email);
        }
    }

    @TestConfiguration
    static class CapturingResetMailerConfig {
        @Bean
        @Primary
        CapturingResetMailer capturingResetMailer() {
            return new CapturingResetMailer();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MerchantService merchantService;
    @Autowired
    private SessionService sessions;
    @Autowired
    private PasswordResetService resets;
    @Autowired
    private MerchantRepository merchants;
    @Autowired
    private CapturingResetMailer mailer;
    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> createdMerchants = new ArrayList<>();
    private Instant start;
    /** IP próprio de cada teste: o limite por IP de um teste não interfere no outro. */
    private String ip;

    @BeforeEach
    void setUp() {
        // Depois de "agora": o Merchant usa a hora real na criação e o banco exige verificação >= criação.
        start = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
        clock.set(start);
        mailer.sent.clear();
        ip = "10." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256) + "."
                + ThreadLocalRandom.current().nextInt(1, 255);
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : createdMerchants) {
            jdbc.update("DELETE FROM webhook_endpoint_history WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM webhook_endpoints WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM api_keys WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM merchants WHERE id = ?", id); // sessões e tokens: ON DELETE CASCADE
        }
    }

    // ---------- senha ----------

    @Test
    void thePasswordIsStoredOnlyAsAnArgon2idHash() {
        Merchant merchant = register(PASSWORD);
        String stored = jdbc.queryForObject("SELECT password_hash FROM merchants WHERE id = ?", String.class, merchant.getId());
        assertThat(stored).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$").doesNotContain(PASSWORD);
    }

    @Test
    void thePasswordPolicyIsEnforcedOnSignup() {
        assertThatThrownBy(() -> register("curta")).isInstanceOf(IllegalArgumentException.class);
        String email = "login-" + UUID.randomUUID() + "@moneyx.dev";
        assertThatThrownBy(() -> merchantService.register("Login Test LTDA", document(), email, email))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- login ----------

    @Test
    void loginReturnsATokenThatIsStoredOnlyAsAHash() {
        Merchant merchant = register(PASSWORD);
        MerchantSession.Started started = sessions.login(" " + merchant.getEmail().toUpperCase() + " ", PASSWORD, ip);

        assertThat(started.token()).startsWith("mx_sess_");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant_sessions WHERE merchant_id = ? AND token_hash <> ?",
                Integer.class, merchant.getId(), started.token())).isEqualTo(1);
        assertThat(started.session().getExpiresAt()).isEqualTo(start.plus(Duration.ofHours(24)));
    }

    @Test
    void wrongPasswordUnknownEmailAndAccountWithoutPasswordGiveTheSameAnswer() {
        Merchant merchant = register(PASSWORD);
        Merchant legacy = registerWithoutPassword();

        assertThatThrownBy(() -> sessions.login(merchant.getEmail(), "senha-errada-123", ip))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");
        assertThatThrownBy(() -> sessions.login("ninguem-" + UUID.randomUUID() + "@moneyx.dev", PASSWORD, ip))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");
        assertThatThrownBy(() -> sessions.login(legacy.getEmail(), PASSWORD, ip))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");
    }

    @Test
    void tenAttemptsOnOneEmailBlockItEvenFromManyIps() {
        Merchant merchant = register(PASSWORD);
        for (int i = 0; i < 10; i++) {
            String otherIp = "172.16." + i + ".1";
            assertThatThrownBy(() -> sessions.login(merchant.getEmail(), "senha-errada-123", otherIp))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        // A 11ª, mesmo com a senha CERTA e de outro IP, espera: o balde é da conta.
        assertThatThrownBy(() -> sessions.login(merchant.getEmail(), PASSWORD, "172.16.99.1"))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(90)));
        clock.advance(Duration.ofSeconds(90));
        assertThat(sessions.login(merchant.getEmail(), PASSWORD, "172.16.99.1").token()).startsWith("mx_sess_");
    }

    // ---------- validade da sessão ----------

    @Test
    void aSessionExpiresAfterSixIdleHours() {
        String token = login(register(PASSWORD));
        clock.advance(Duration.ofHours(6).minusSeconds(1));
        assertThat(sessions.authenticate(token)).isPresent(); // uso renova a inatividade
        clock.advance(Duration.ofHours(6).minusSeconds(1));
        assertThat(sessions.authenticate(token)).isPresent();
        clock.advance(Duration.ofHours(6));
        assertThat(sessions.authenticate(token)).isEmpty();
    }

    @Test
    void aSessionNeverLastsMoreThan24HoursEvenIfUsed() {
        String token = login(register(PASSWORD));
        for (int i = 0; i < 4; i++) {
            clock.advance(Duration.ofHours(5));
            assertThat(sessions.authenticate(token)).isPresent();
        }
        clock.advance(Duration.ofHours(4)); // 24 h depois do login
        assertThat(sessions.authenticate(token)).isEmpty();
    }

    @Test
    void lastUsedIsWrittenAtMostOncePerMinute() {
        Merchant merchant = register(PASSWORD);
        String token = login(merchant);
        clock.advance(Duration.ofSeconds(30));
        sessions.authenticate(token);
        assertThat(lastUsed(merchant)).isEqualTo(start);
        clock.advance(Duration.ofSeconds(30));
        sessions.authenticate(token);
        assertThat(lastUsed(merchant)).isEqualTo(start.plusSeconds(60));
    }

    @Test
    void invalidTokensNeverAuthenticate() {
        assertThat(sessions.authenticate("mx_sess_inexistente")).isEmpty();
        assertThat(sessions.authenticate("mx_test_qualquer")).isEmpty();
        assertThat(sessions.authenticate(null)).isEmpty();
    }

    // ---------- pela API, com o filtro real ----------

    @Test
    void theSessionTokenWorksLikeAnApiKeyUntilLogout() throws Exception {
        Merchant merchant = register(PASSWORD);
        String body = mockMvc.perform(post("/v1/sessions").with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(merchant.getEmail(), PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.idleTimeout").value(21600))
                .andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.read(body, "$.token");

        mockMvc.perform(get("/v1/merchants/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchant.getId().toString()));

        mockMvc.perform(delete("/v1/sessions/current").header("Authorization", bearer)).andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/merchants/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCredentialsAnswer401WithACodeAndTooManyAnswer429WithRetryAfter() throws Exception {
        Merchant merchant = register(PASSWORD);
        String wrong = "{\"email\":\"%s\",\"password\":\"senha-errada-123\"}".formatted(merchant.getEmail());
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/v1/sessions").with(from("172.17." + i + ".1")).contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("invalid_credentials"));
        }
        mockMvc.perform(post("/v1/sessions").with(from(ip)).contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "90"))
                .andExpect(jsonPath("$.code").value("rate_limit_exceeded"));
    }

    @Test
    void webhookActionsDoneFromTheDashboardRecordTheSessionAsTheAuthor() throws Exception {
        Merchant merchant = register(PASSWORD);
        MerchantSession.Started started = sessions.login(merchant.getEmail(), PASSWORD, ip);
        mockMvc.perform(post("/v1/webhook-endpoints").header("Authorization", "Bearer " + started.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://loja.example.com/hook\"}"))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForList("SELECT session_id, api_key_id FROM webhook_endpoint_history WHERE merchant_id = ?",
                merchant.getId()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("session_id")).isEqualTo(started.session().getId());
                    assertThat(row.get("api_key_id")).isNull();
                });
    }

    // ---------- redefinição de senha ----------

    @Test
    void resettingThePasswordChangesItRevokesAllSessionsAndVerifiesTheEmail() {
        Merchant merchant = register(PASSWORD);
        String first = login(merchant);
        String second = login(merchant);

        resets.request(merchant.getEmail(), ip);
        assertThat(mailer.sent.getFirst()[1]).startsWith("http://localhost:3000/redefinir-senha?token=");
        resets.confirm(mailer.lastTokenFor(merchant.getEmail()), "outra-senha-forte-456");

        assertThat(sessions.authenticate(first)).isEmpty();
        assertThat(sessions.authenticate(second)).isEmpty();
        assertThatThrownBy(() -> sessions.login(merchant.getEmail(), PASSWORD, ip))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(sessions.login(merchant.getEmail(), "outra-senha-forte-456", ip).token()).startsWith("mx_sess_");
        assertThat(merchants.findById(merchant.getId()).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void aMerchantFromBeforeTheLoginCanSetAPasswordThroughTheLink() {
        Merchant legacy = registerWithoutPassword();
        resets.request(legacy.getEmail(), ip);
        resets.confirm(mailer.lastTokenFor(legacy.getEmail()), PASSWORD);
        assertThat(login(legacy)).startsWith("mx_sess_");
    }

    @Test
    void anUnknownEmailGetsTheSameSilentAnswerAndNoEmail() {
        resets.request("ninguem-" + UUID.randomUUID() + "@moneyx.dev", ip);
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void resetLinksAreSingleUseReplacedByNewOnesAndExpireInOneHour() {
        Merchant merchant = register(PASSWORD);
        resets.request(merchant.getEmail(), ip);
        String old = mailer.lastTokenFor(merchant.getEmail());
        resets.request(merchant.getEmail(), ip);
        String fresh = mailer.lastTokenFor(merchant.getEmail());

        assertThatThrownBy(() -> resets.confirm(old, "outra-senha-forte-456"))
                .isInstanceOfSatisfying(PasswordResetException.class, e -> assertThat(e.getCode()).isEqualTo("invalid_reset_token"));
        clock.advance(Duration.ofHours(1));
        assertThatThrownBy(() -> resets.confirm(fresh, "outra-senha-forte-456"))
                .isInstanceOfSatisfying(PasswordResetException.class, e -> assertThat(e.getCode()).isEqualTo("reset_token_expired"));
    }

    @Test
    void aWeakNewPasswordIsRejectedAndTheLinkStaysUsable() {
        Merchant merchant = register(PASSWORD);
        resets.request(merchant.getEmail(), ip);
        String token = mailer.lastTokenFor(merchant.getEmail());
        assertThatThrownBy(() -> resets.confirm(token, "curta")).isInstanceOf(IllegalArgumentException.class);
        resets.confirm(token, "outra-senha-forte-456");
    }

    @RepeatedTest(3)
    void twoConcurrentConfirmationsOfTheSameLinkChangeThePasswordOnce() throws Exception {
        Merchant merchant = register(PASSWORD);
        resets.request(merchant.getEmail(), ip);
        String token = mailer.lastTokenFor(merchant.getEmail());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (String password : List.of("primeira-senha-forte", "segunda-senha-forte")) {
            results.add(pool.submit(() -> {
                go.await();
                try {
                    resets.confirm(token, password);
                    return true;
                } catch (PasswordResetException e) {
                    return false;
                }
            }));
        }
        go.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();
        assertThat(succeeded).isEqualTo(1);
    }

    // ---------- apoio ----------

    private Merchant register(String password) {
        Merchant merchant = merchantService.register("Login Test LTDA", document(),
                "login-" + UUID.randomUUID() + "@moneyx.dev", password).merchant();
        createdMerchants.add(merchant.getId());
        return merchant;
    }

    private Merchant registerWithoutPassword() {
        Merchant merchant = merchantService.register("Login Test LTDA", document(),
                "legacy-" + UUID.randomUUID() + "@moneyx.dev").merchant();
        createdMerchants.add(merchant.getId());
        return merchant;
    }

    /** Login com a senha padrão dos testes. */
    private String login(Merchant merchant) {
        return sessions.login(merchant.getEmail(), PASSWORD, ip).token();
    }

    private Instant lastUsed(Merchant merchant) {
        return jdbc.queryForObject("SELECT last_used_at FROM merchant_sessions WHERE merchant_id = ?",
                java.sql.Timestamp.class, merchant.getId()).toInstant();
    }

    private static String document() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
    }

    private static RequestPostProcessor from(String remoteAddr) {
        return request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        };
    }
}
