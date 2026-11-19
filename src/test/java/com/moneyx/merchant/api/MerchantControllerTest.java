package com.moneyx.merchant.api;

import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.service.MerchantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void createReturns201WithLocationAndBody() throws Exception {
        Merchant merchant = new Merchant("Loja LTDA", "12345678000190", "contato@loja.com");
        when(service.register("Loja LTDA", "12.345.678/0001-90", "contato@loja.com")).thenReturn(merchant);

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
                .andExpect(jsonPath("$.id").value(merchant.getId().toString()))
                .andExpect(jsonPath("$.document").value("12345678000190"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void createReturns400WhenFieldsAreInvalid() throws Exception {
        mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "legalName": "",
                                  "document": "123",
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(service, never()).register(any(), any(), any());
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
                .andExpect(status().isBadRequest());
    }
}
