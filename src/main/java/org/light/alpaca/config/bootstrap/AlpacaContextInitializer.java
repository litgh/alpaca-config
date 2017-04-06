package org.light.alpaca.config.bootstrap;

import org.light.alpaca.config.zookeeper.ZookeeperContext;
import org.light.alpaca.config.zookeeper.ZookeeperProperties;
import org.light.alpaca.config.zookeeper.ZookeeperPropertySource;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author Lee
 * @date 2016/10/20 0020
 */
public class AlpacaContextInitializer implements ApplicationContextInitializer {
	private static final Logger LOG                = LoggerFactory.getLogger(AlpacaContextInitializer.class);
	private static final String APPLICATION_NAME   = "spring.application.name";
	private static final String DEFAULT_PROPERTIES = "defaultProperties";

	private Path baseDir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("alpaca");

	private String uri = "";

	private CredentialsProvider provider = new UsernamePasswordCredentialsProvider("", "");

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		if (!applicationContext.getEnvironment().containsProperty(APPLICATION_NAME)) {
			throw new IllegalStateException("Please specify the application name: '" + APPLICATION_NAME + "'");
		}

		try {
			cloneConfig("master");

			String[] profiles = applicationContext.getEnvironment().getActiveProfiles();
			YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
			LOG.info("loading {}.yml", profiles);
			for (String profile : profiles) {
				PropertySource<?> propertySource = loader.load("alpaca-zookeeper-config", new FileSystemResource(baseDir.resolve(profile + ".yml").toFile()), null);
				if (propertySource != null) {
					if (applicationContext.getEnvironment().getPropertySources().contains(DEFAULT_PROPERTIES)) {
						applicationContext.getEnvironment().getPropertySources().addBefore(DEFAULT_PROPERTIES, propertySource);
					} else {
						applicationContext.getEnvironment().getPropertySources().addLast(propertySource);
					}
				}
			}
		} catch (IOException | GitAPIException e) {
			throw new RuntimeException("clone alpaca/config failed", e);
		}

		ZookeeperProperties properties = new ZookeeperProperties();
		LOG.info("using zookeeper: {}", applicationContext.getEnvironment().getProperty("zhao.alpaca.zookeeper.connect-string"));
		properties.setConnectString(applicationContext.getEnvironment().getProperty("zhao.alpaca.zookeeper.connect-string"));

		RetryPolicy retryPolicy = new ExponentialBackoffRetry(properties.getBaseSleepTimeMs(), properties.getMaxRetries(), properties.getMaxSleepMs());
		CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder();
		CuratorFramework curator = builder.retryPolicy(retryPolicy).connectString(properties.getConnectString()).build();
		curator.start();
		LOG.trace("blocking until connected to zookeeper for " + properties.getBlockUntilConnectedWait() + properties.getBlockUntilConnectedUnit());
		try {
			curator.blockUntilConnected(properties.getBlockUntilConnectedWait(), properties.getBlockUntilConnectedUnit());
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		LOG.trace("connected to zookeeper");

		applicationContext.getBeanFactory().registerSingleton(CuratorFramework.class.getName(), curator);

		load(applicationContext, curator);
	}

	private void addProfiles(List<ZookeeperContext> contexts, String baseContext, String prefix, List<String> profiles) {
		for (String profile : profiles) {
			contexts.add(new ZookeeperContext(baseContext + "/" + profile, prefix, profile));
		}
	}

	private void cloneConfig(String label) throws IOException, GitAPIException {
		ProxySelector defaultProxy = ProxySelector.getDefault();
		setProxy();
		Git git = null;
		try {
			git = createGitClient();
			if (shouldPull(git)) {
				fetch(git);
				checkout(git, label);
				if (!isClean(git)) {
					LOG.warn("The local repository is dirty. Resetting it to origin/" + label + ".");
					resetHard(git, label, "refs/remotes/origin/" + label);
				}
			}

		} catch (GitAPIException e) {
			throw new IllegalStateException("Cannot clone or checkout repository", e);
		} catch (Exception e) {
			throw new IllegalStateException("Cannot load environment", e);
		} finally {
			try {
				if (git != null) {
					git.close();
				}
			} catch (Exception e) {
				LOG.warn("Could not close git repository", e);
			}
		}
		ProxySelector.setDefault(defaultProxy);
	}

	private boolean shouldPull(Git git) throws GitAPIException {
		boolean shouldPull;
		Status gitStatus = git.status().call();
		boolean isWorkingTreeClean = gitStatus.isClean();
		String originUrl = git.getRepository().getConfig().getString("remote", "origin", "url");

		if (!isWorkingTreeClean) {
			shouldPull = true;
			logDirty(gitStatus);
		} else {
			shouldPull = isWorkingTreeClean && originUrl != null;
		}
		if (!isWorkingTreeClean) {
			LOG.info("Cannot pull from remote " + originUrl + ", the working tree is not clean.");
		}
		return shouldPull;
	}

	private boolean isClean(Git git) {
		StatusCommand status = git.status();
		try {
			return status.call().isClean();
		} catch (Exception e) {
			String message = "Could not execute status command on local repository. Cause: ("
			                 + e.getClass().getSimpleName() + ") " + e.getMessage();
			warn(e, message);
			return false;
		}
	}

	private Ref resetHard(Git git, String label, String ref) {
		ResetCommand reset = git.reset();
		reset.setRef(ref);
		reset.setMode(ResetCommand.ResetType.HARD);
		try {
			Ref resetRef = reset.call();
			if (resetRef != null) {
				LOG.info("Reset label " + label + " to version " + resetRef.getObjectId());
			}
			return resetRef;
		} catch (Exception ex) {
			String message = "Could not reset to remote for " + label + " (current ref="
			                 + ref + "), remote: " + git.getRepository().getConfig()
			                                            .getString("remote", "origin", "url");
			warn(ex, message);
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private void logDirty(Status status) {
		Set<String> dirties = dirties(status.getAdded(), status.getChanged(),
				status.getRemoved(), status.getMissing(), status.getModified(),
				status.getConflicting(), status.getUntracked());
		LOG.warn(String.format("Dirty files found: %s", dirties));
	}

	@SuppressWarnings("unchecked")
	private Set<String> dirties(Set<String>... changes) {
		Set<String> dirties = new HashSet<>();
		for (Set<String> files : changes) {
			dirties.addAll(files);
		}
		return dirties;
	}

	private FetchResult fetch(Git git) {
		FetchCommand fetch = git.fetch();
		fetch.setRemote("origin");
		fetch.setTagOpt(TagOpt.FETCH_TAGS);
		fetch.setTimeout(5);
		fetch.setCredentialsProvider(provider);

		try {
			FetchResult result = fetch.call();
			if (result.getTrackingRefUpdates() != null && result.getTrackingRefUpdates().size() > 0) {
				LOG.info("Fetched for remote master and found " + result.getTrackingRefUpdates().size() + " updates");
				merge(git, "master");
			}
			return result;
		} catch (GitAPIException e) {
			String message = "Could not fetch remote for master remote: "
			                 + git.getRepository().getConfig().getString("remote", "origin", "url");
			warn(e, message);
			return null;
		}
	}

	private Ref checkout(Git git, String label) throws GitAPIException {
		CheckoutCommand checkout = git.checkout();
		if (shouldTrack(git, label)) {
			trackBranch(git, checkout, label);
		} else {
			// works for tags and local branches
			checkout.setName(label);
		}
		return checkout.call();
	}

	private boolean shouldTrack(Git git, String label) throws GitAPIException {
		return isBranch(git, label) && !isLocalBranch(git, label);
	}

	private void trackBranch(Git git, CheckoutCommand checkout, String label) {
		checkout.setCreateBranch(true).setName(label)
		        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
		        .setStartPoint("origin/" + label);
	}

	private boolean isBranch(Git git, String label) throws GitAPIException {
		return containsBranch(git, label, ListBranchCommand.ListMode.ALL);
	}

	private boolean isLocalBranch(Git git, String label) throws GitAPIException {
		return containsBranch(git, label, null);
	}

	private boolean containsBranch(Git git, String label, ListBranchCommand.ListMode listMode)
			throws GitAPIException {
		ListBranchCommand command = git.branchList();
		if (listMode != null) {
			command.setListMode(listMode);
		}
		List<Ref> branches = command.call();
		for (Ref ref : branches) {
			if (ref.getName().endsWith("/" + label)) {
				return true;
			}
		}
		return false;
	}

	private void warn(Exception e, String message) {
		LOG.warn(message);
		if (LOG.isDebugEnabled()) {
			LOG.debug("Stacktrace for: " + message, e);
		}
	}

	private MergeResult merge(Git git, String label) {
		try {
			MergeCommand merge = git.merge();
			merge.include(git.getRepository().getRef("origin/" + label));
			MergeResult result = merge.call();
			if (!result.getMergeStatus().isSuccessful()) {
				LOG.warn("Merged from remote " + label + " with result " + result.getMergeStatus());
			}
			return result;
		} catch (Exception ex) {
			String message = "Could not merge remote for " + label + " remote: " +
			                 git.getRepository().getConfig().getString("remote", "origin", "url");
			warn(ex, message);
			return null;
		}
	}

	private Git createGitClient() throws IOException, GitAPIException {
		if (Files.exists(baseDir.resolve(".git"))) {
			return openGitRepository();
		} else {
			return copyGitRepository();
		}
	}

	private Git openGitRepository() throws IOException {
		return Git.open(baseDir.toFile());
	}

	private synchronized Git copyGitRepository() throws GitAPIException {
		deleteBaseDirIfExists();
		baseDir.toFile().mkdirs();
		if (!Files.exists(baseDir)) {
			throw new IllegalStateException("Could not create baseDir: " + baseDir.toAbsolutePath());
		}
		CloneCommand clone = Git.cloneRepository().setURI(uri).setDirectory(baseDir.toFile()).setTimeout(5)
		                        .setCredentialsProvider(provider);
		try {
			return clone.call();
		} catch (GitAPIException e) {
			deleteBaseDirIfExists();
			throw e;
		}
	}

	private void deleteBaseDirIfExists() {
		if (Files.exists(baseDir)) {
			try {
				FileUtils.delete(baseDir.toFile(), FileUtils.RECURSIVE);
			} catch (IOException e) {
				throw new IllegalStateException("Failed to initialize base directory", e);
			}
		}
	}

	private void setProxy() {
		if (StringUtils.hasText(System.getenv("alpaca_http_proxy"))) {
			URI uri = URI.create(System.getenv("alpaca_http_proxy"));

			ProxySelector.setDefault(new ProxySelector() {
				@Override
				public List<Proxy> select(URI url) {
					LOG.info("using proxy {}", uri.toString());
					LOG.info("http request: {}", url.toString());
					return Arrays.asList(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort())));
				}

				@Override
				public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
					if (uri == null || sa == null || ioe == null) {
						throw new IllegalArgumentException("Arguments can not be null.");
					}
				}
			});
		}
	}

	private void load(ConfigurableApplicationContext applicationContext, CuratorFramework curator) {
		List<String> activeProfiles = Arrays.asList(applicationContext.getEnvironment().getActiveProfiles());
		String appName = applicationContext.getEnvironment().getProperty(APPLICATION_NAME);
		List<ZookeeperContext> contexts = new ArrayList<>();
		String defaultContext = "config/applications/";

		addProfiles(contexts, defaultContext + "default", "", activeProfiles);
		addProfiles(contexts, defaultContext + appName, "", activeProfiles);
		Collections.reverse(contexts);

		for (ZookeeperContext propertySourceContext : contexts) {
			ZookeeperPropertySource propertySource = new ZookeeperPropertySource(propertySourceContext.getContext(), propertySourceContext.getPrefix(), curator);
			if (propertySource.getPropertyNames().length > 0) {
				applicationContext.getEnvironment().getPropertySources().addLast(propertySource);
			}
		}

		LOG.info("dubbo using zookeeper: {}", applicationContext.getEnvironment().getProperty("dubbo.zookeeper.connect-string"));
	}
}
