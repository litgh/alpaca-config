package org.light.alpaca.config.bootstrap;

import org.light.alpaca.config.zookeeper.ZookeeperContext;
import org.light.alpaca.config.zookeeper.ZookeeperPropertySource;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.boot.context.properties.ConfigurationBeanFactoryMetaData;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.ConfigurableEnvironment;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Lee
 * @date 2016/10/20 0020
 */
public class AlpacaInstantiationAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor, ApplicationContextAware, PriorityOrdered {
	private static final Logger LOG = LoggerFactory.getLogger(AlpacaInstantiationAwareBeanPostProcessor.class);
	private ConfigurableApplicationContext context;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		context = (ConfigurableApplicationContext) applicationContext;
	}

	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		ConfigurationBeanFactoryMetaData beans = context.getBean(ConfigurationBeanFactoryMetaData.class);
		CuratorFramework curator = context.getBean(CuratorFramework.class);
		ConfigurableEnvironment env = context.getEnvironment();

		ConfigurationProperties annotation = beans.findFactoryAnnotation(beanName, ConfigurationProperties.class);
		if (annotation == null) {
			annotation = AnnotationUtils.findAnnotation(bean.getClass(), ConfigurationProperties.class);
		}

		if (annotation != null) {
			List<ZookeeperContext> contexts = new ArrayList<>();
			List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());

			if (annotation.value().startsWith("alpaca.datasource.") && env.getProperty(annotation.value(), "null").equals("null")) {
				addProfiles(contexts, "config/databases/default", "", activeProfiles);
				addProfiles(contexts, "config/databases/" + annotation.value().substring(18), annotation.value(), activeProfiles);
			}

			if (annotation.value().startsWith("alpaca.components.") && env.getProperty(annotation.value(), "null").equals("null")) {
				addProfiles(contexts, "config/components/" + annotation.value().substring(18), annotation.value(), activeProfiles);
			}

			if (!contexts.isEmpty()) {
				Collections.reverse(contexts);
				for (ZookeeperContext propertySourceContext : contexts) {
					ZookeeperPropertySource propertySource = new ZookeeperPropertySource(propertySourceContext.getContext(), propertySourceContext.getPrefix(), curator);
					if (propertySource.getPropertyNames().length > 0) {
						if (LOG.isDebugEnabled()) {
							for (String s : propertySource.getPropertyNames()) {
								LOG.debug("load property {}: {}", s, propertySource.getProperty(s));
							}
						}
						env.getPropertySources().addLast(propertySource);
					}
				}
				ConfigurationPropertiesBindingPostProcessor bind = context.getBean(ConfigurationPropertiesBindingPostProcessor.class);
				bind.setPropertySources(env.getPropertySources());
			}
		}
		return bean;
	}

	private void addProfiles(List<ZookeeperContext> contexts, String baseContext, String prefix, List<String> profiles) {
		for (String profile : profiles) {
			contexts.add(new ZookeeperContext(baseContext + "/" + profile, prefix, profile));
		}
	}

	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		return null;
	}

	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
		return true;
	}

	@Override
	public PropertyValues postProcessPropertyValues(PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeansException {
		return pvs;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public int getOrder() {
		return PriorityOrdered.HIGHEST_PRECEDENCE;
	}
}
