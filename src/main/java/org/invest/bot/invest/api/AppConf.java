package org.invest.bot.invest.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConf {
    @Value("${tinkoff.readonly}")
    private String tinkoffToken;

    @Bean
    public InvestApiCore investApiCore(){
        return new InvestApiCore(tinkoffToken);
    }
}
