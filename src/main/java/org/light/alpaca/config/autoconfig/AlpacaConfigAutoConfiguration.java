package org.light.alpaca.config.autoconfig;

import org.light.alpaca.config.bootstrap.AlpacaInstantiationAwareBeanPostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Lee
 * @date 2016/10/20 0020
 */
@Configuration
public class AlpacaConfigAutoConfiguration {
	private static final Logger LOG = LoggerFactory.getLogger(AlpacaConfigAutoConfiguration.class);

	@Bean
	public AlpacaInstantiationAwareBeanPostProcessor alpacaBeanPostProcessor() {
		return new AlpacaInstantiationAwareBeanPostProcessor();
	}
}
