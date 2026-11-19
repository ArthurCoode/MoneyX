package com.moneyx.merchant.api;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.domain.ApiKeyGenerator;
import com.moneyx.apikey.domain.ApiKeyGenerator.GeneratedApiKey;
import com.moneyx.apikey.service.IssuedApiKey;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.exception.DuplicateMerchantException;
import com.moneyx.merchant.exception.MerchantNotFoundException;
import com.moneyx.merchant.service.MerchantRegistration;
import com.moneyx.merchant.service.MerchantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes da camada web.
 *
 * @WebMvcTest sobe apenas a infraestrutura MVC (controllers, JSON, validação),
 * sem banco. O serviço é substituído por um mock via @MockitoBean.
 * O MockMvc simula requisições HTTP sem abrir uma porta real.
 */
@WebMvcTest(MerchantController.class)
class MerchantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MerchantService service;

    @Test
    void createReturns201WithMerchantAndApiKey() throws Exception {
        Merchant merchant = new Merchant("Loja LTDA", "12345678000190", "contato@loja.com");
        GeneratedApiKey generated = ApiKeyGenerator.generate();
        IssuedApiKey issued = new IssuedApiKey(ApiKey.issue(merchant.getId(), generated), generated.plainText());
        when(service.register("Loja LTDA", "12.345.678/0001-90", "contato@loja.com"))
                .thenReturn(new MerchantRegistration(merchant, issued));

        mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "legalName": "Loja LTDA",
                                  "document": "12.345.678/0001-90",
                                  "email": "contato@loja.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/v1/merchants/" + merchant.getId())))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.merchant.id").value(merchant.getId().toString()))
                .andExpect(jsonPath("$.merchant.document").value("12345678000190"))
                .andExpect(jsonPath("$.merchant.status").value("ACTIVE"))
                .andExpect(jsonPath("$.merchant.version").doesNotExist())
                .andExpect(jsonPath("$.apiKey.id").value(issued.apiKey().getId().toString()))
                .andExpect(jsonPath("$.apiKey.key").value(generated.plainText()))
                .andExpect(jsonPath("$.apiKey.prefix").value(generated.displayPrefix()))
                .andExpect(jsonPath("$.apiKey.keyHash").doesNotExist());
    }

    @Test
    void createReturns400WithFieldErrorsWhenFieldsAreInvalid() throws Exception {
        mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "legalName": "",
                                  "document": "123",
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("legalName", "document", "email")));

        verify(service, never()).register(any(), any(), any());
    }

    @Test
    void validationMessagesAreAlwaysInEnglish() throws Exception {
        mockMvc.perform(post("/v1/merchants")
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "legalName": "",
                                  "document": "12345678901",
                                  "email": "contato@loja.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("legalName"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @Test
    void createReturns409WhenMerchantAlreadyExists() throws Exception {
        when(service.register(any(), any(), any()))
                .thenThrow(new DuplicateMerchantException("A merchant with this document already exists"));

        mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "legalName": "Loja LTDA",
                                  "document": "12345678000190",
                                  "email": "contato@loja.com"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("A merchant with this document already exists"));
    }

    @Test
    void createReturns400WhenBodyIsMalformedJson() throws Exception {
        mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(service, never()).register(any(), any(), any());
    }

    @Test
    void getByIdReturns404WhenMerchantDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(id)).thenThrow(new MerchantNotFoundException(id));

        mockMvc.perform(get("/v1/merchants/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Merchant not found: " + id));
    }

    @Test
    void getByIdReturnsMerchant() throws Exception {
        Merchant merchant = new Merchant("Loja LTDA", "12345678901", "contato@loja.com");
        when(service.getById(merchant.getId())).thenReturn(merchant);

        mockMvc.perform(get("/v1/merchants/{id}", merchant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Loja LTDA"))
                .andExpect(jsonPath("$.email").value("contato@loja.com"));
    }

    @Test
    void getByIdReturns400WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/v1/merchants/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
