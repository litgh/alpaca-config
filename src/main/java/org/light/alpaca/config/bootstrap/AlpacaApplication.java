package org.light.alpaca.config.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class AlpacaApplication {
	private static final Logger LOG                            = LoggerFactory.getLogger(AlpacaApplication.class);
	private static final String BOOTSTRAP_PROPERTY_SOURCE_NAME = "bootstrap";

	public static ConfigurableApplicationContext run(Object source, String... args) {
		return new SpringApplicationBuilder()
				       .initializers(new AlpacaContextInitializer())
				       .sources(source)
				       .properties("spring.config.name:" + BOOTSTRAP_PROPERTY_SOURCE_NAME)
				       .bannerMode(Banner.Mode.OFF)
				       .run(args);
	}

	public static ConfigurableApplicationContext run(Object source, boolean web, String... args) {
		return new SpringApplicationBuilder()
				       .initializers(new AlpacaContextInitializer())
				       .sources(source)
				       .properties("spring.config.name:" + BOOTSTRAP_PROPERTY_SOURCE_NAME)
				       .bannerMode(Banner.Mode.OFF)
				       .web(web)
				       .run(args);
	}

	public static ConfigurableApplicationContext run(Object[] source, String... args) {
		return new SpringApplicationBuilder()
				       .initializers(new AlpacaContextInitializer())
				       .sources(source)
				       .properties("spring.config.name:" + BOOTSTRAP_PROPERTY_SOURCE_NAME)
				       .bannerMode(Banner.Mode.OFF)
				       .run(args);
	}

}
