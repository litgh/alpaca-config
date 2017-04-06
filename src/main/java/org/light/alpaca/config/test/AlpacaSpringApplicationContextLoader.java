package org.light.alpaca.config.test;

import org.light.alpaca.config.bootstrap.AlpacaApplication;
import org.springframework.boot.test.SpringApplicationContextLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * @author Lee
 * @date 2016/10/20 0020
 */
public class AlpacaSpringApplicationContextLoader extends SpringApplicationContextLoader {
	@Override
	public ApplicationContext loadContext(MergedContextConfiguration config) throws Exception {
		return AlpacaApplication.run(config.getClasses());
	}
}
