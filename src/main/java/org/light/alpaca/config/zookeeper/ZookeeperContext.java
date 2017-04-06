package org.light.alpaca.config.zookeeper;

/**
 * @author Lee
 * @date 2016/10/19 0019
 */
public class ZookeeperContext {
	private String context;
	private String prefix;
	private String profile;

	public ZookeeperContext(String context, String prefix, String profile) {
		this.context = context;
		this.prefix = prefix;
		this.profile = profile;
	}

	public String getContext() {
		return context;
	}

	public String getProfile() {
		return profile;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}
}
