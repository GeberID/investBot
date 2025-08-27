package org.invest.bot.core;

import org.invest.bot.invest.api.InvestApiCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConf {
    @Value("${tinkoff.readonly}")
    private String tinkoffToken;
    private static final Logger log = LoggerFactory.getLogger(AppConf.class);
    @Bean
    public InvestApiCore investApiCore(){
        InvestApiCore apiCoreInstance = new InvestApiCore(tinkoffToken);
        return apiCoreInstance;
    }
}
