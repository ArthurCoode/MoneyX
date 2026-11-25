package com.moneyx.payment.provider;

import com.moneyx.payment.provider.mercadopago.MercadoPagoPixProvider;
import com.moneyx.payment.provider.mercadopago.MercadoPagoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O Spring escolhe UM provedor pela propriedade moneyx.pix.provider e consegue criá-lo de verdade.
 *
 * Existe porque os testes unitários montam o provedor à mão (new ...): a primeira subida real com o Mercado Pago
 * falhou com "No default constructor found" (dois construtores e nenhum marcado), e só a app real mostrou.
 * Aqui sobe um contexto pequeno do Spring, sem banco e sem rede.
 */
class PixProviderSelectionTest {

    @Configuration
    @EnableConfigurationProperties(MercadoPagoProperties.class)
    @Import({SandboxPixProvider.class, MercadoPagoPixProvider.class})
    static class Providers {
        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Providers.class);

    @Test
    void sandboxIsTheDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(PixProvider.class).isInstanceOf(SandboxPixProvider.class);
        });
    }

    @Test
    void mercadoPagoIsSelectedAndBuiltBySpring() {
        runner.withPropertyValues("moneyx.pix.provider=mercadopago", "moneyx.pix.mercadopago.access-token=APP_USR-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PixProvider.class);
                    assertThat(context.getBean(PixProvider.class).name()).isEqualTo("mercadopago");
                });
    }

    @Test
    void mercadoPagoWithoutTokenRefusesToStart() {
        runner.withPropertyValues("moneyx.pix.provider=mercadopago")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("MERCADOPAGO_ACCESS_TOKEN"));
    }
}
