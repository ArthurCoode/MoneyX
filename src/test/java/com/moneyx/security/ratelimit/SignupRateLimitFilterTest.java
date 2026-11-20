package com.moneyx.security.ratelimit;

import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.merchant.api.MerchantController;
import com.moneyx.merchant.service.MerchantService;
import com.moneyx.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O filtro na cadeia de segurança REAL (SecurityConfig), com um limite pequeno: 2 tentativas, recarga de 1 h.
 *
 * O contexto (e o limitador) é compartilhado entre os métodos: cada teste usa IPs próprios para não depender dos outros.
 * O corpo enviado é inválido de propósito: prova que tentativas que dariam 400 também gastam fichas.
 */
@WebMvcTest(controllers = MerchantController.class, properties = {
        "moneyx.rate-limit.signup.capacity=2",
        "moneyx.rate-limit.signup.refill-period=1h"
})
@Import(SecurityConfig.class)
class SignupRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MerchantService merchantService;

    @MockitoBean
    private ApiKeyService apiKeyService;

    private static MockHttpServletRequestBuilder signup() {
        return post("/v1/merchants").contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    @Test
    void thirdAttemptFromTheSameIpIsRefusedWith429AndRetryAfter() throws Exception {
        String ip = randomIp();
        mockMvc.perform(signup().with(from(ip))).andExpect(status().isBadRequest());
        mockMvc.perform(signup().with(from(ip))).andExpect(status().isBadRequest());

        mockMvc.perform(signup().with(from(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("rate_limit_exceeded"))
                .andExpect(jsonPath("$.detail").value("Too many requests. Try again later"));
    }

    @Test
    void refusedAttemptsNeverReachTheController() throws Exception {
        String ip = randomIp();
        mockMvc.perform(signup().with(from(ip)));
        mockMvc.perform(signup().with(from(ip)));
        org.mockito.Mockito.clearInvocations(merchantService);

        mockMvc.perform(post("/v1/merchants").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName": "Loja", "document": "12345678000190", "email": "a@b.com"}
                                """)
                        .with(from(ip)))
                .andExpect(status().isTooManyRequests());

        verifyNoInteractions(merchantService);
    }

    @Test
    void anotherIpIsNotAffected() throws Exception {
        String ip = randomIp();
        mockMvc.perform(signup().with(from(ip)));
        mockMvc.perform(signup().with(from(ip)));
        mockMvc.perform(signup().with(from(ip))).andExpect(status().isTooManyRequests());

        mockMvc.perform(signup().with(from(randomIp()))).andExpect(status().isBadRequest());
    }

    /** O cliente pode inventar o X-Forwarded-For: trocar o header a cada tentativa NÃO fura o limite. */
    @Test
    void forgedXForwardedForDoesNotBypassTheLimit() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(signup().with(from(ip)).header("X-Forwarded-For", randomIp())).andExpect(status().isBadRequest());
        }

        mockMvc.perform(signup().with(from(ip)).header("X-Forwarded-For", randomIp()))
                .andExpect(status().isTooManyRequests());
    }

    /** Trocar o final de um IPv6 dentro do mesmo /64 também não fura o limite. */
    @Test
    void addressesOfTheSameIpv6NetworkShareTheLimit() throws Exception {
        String network = "2001:db8:" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000)) + ":7";
        mockMvc.perform(signup().with(from(network + "::1"))).andExpect(status().isBadRequest());
        mockMvc.perform(signup().with(from(network + "::2"))).andExpect(status().isBadRequest());

        mockMvc.perform(signup().with(from(network + "::abcd"))).andExpect(status().isTooManyRequests());
    }

    @Test
    void otherRoutesAreNotRateLimited() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/v1/merchants/me").with(from(ip))).andExpect(status().isUnauthorized());
        }
        // e não gastaram fichas do cadastro
        mockMvc.perform(signup().with(from(ip))).andExpect(status().isBadRequest());
    }
}
