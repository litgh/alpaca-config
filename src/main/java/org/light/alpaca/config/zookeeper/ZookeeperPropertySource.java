package org.light.alpaca.config.zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ZookeeperPropertySource extends EnumerablePropertySource<CuratorFramework> {
    public static final Logger              log        = LoggerFactory.getLogger(ZookeeperPropertySource.class);
    private             Map<String, Object> properties = new LinkedHashMap<>();

    private String context;
    private String prefix;

    protected String sanitizeKey(String path) {
        return path.replace(this.context + "/", "").replace('/', '.');
    }

    public String getContext() {
        return this.context;
    }

    private MapPropertySource propertySource;

    public ZookeeperPropertySource(String context, String prefix, CuratorFramework source) {
        super(context, source);
        this.context = context;
        this.prefix = prefix;
        if (!this.context.startsWith("/")) {
            this.context = "/" + this.context;
        }

        byte[] value = getPropertyBytes(this.context);
        if (value != null && value.length > 0) {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            try {
                propertySource = (MapPropertySource) loader.load(context, new ByteArrayResource(value), null);
                for (Map.Entry<String, Object> entry : propertySource.getSource().entrySet()) {
                    this.properties.put(StringUtils.hasText(this.prefix) ? this.prefix + "." + entry.getKey() : entry.getKey(), entry.getValue());
                }
            } catch (IOException e) {
            }
        }
        findProperties(this.getContext(), null);
    }

    @Override
    public Object getProperty(String name) {
        return this.properties.get(name);
    }

    private byte[] getPropertyBytes(String fullPath) {
        try {
            byte[] bytes = null;
            try {
                bytes = this.getSource().getData().forPath(fullPath);
            } catch (KeeperException e) {
                if (e.code() != KeeperException.Code.NONODE) { // not found
                    throw e;
                }
            }
            return bytes;
        } catch (Exception exception) {
            ReflectionUtils.rethrowRuntimeException(exception);
        }
        return null;
    }

    @Override
    public String[] getPropertyNames() {
        Set<String> strings = this.properties.keySet();
        return strings.toArray(new String[strings.size()]);
    }

    private void findProperties(String path, List<String> children) {
        try {
            log.trace("entering findProperties for path: " + path);
            if (children == null) {
                children = getChildren(path);
            }
            if (children == null || children.isEmpty()) {
                return;
            }
            for (String child : children) {
                String childPath = path + "/" + child;
                List<String> childPathChildren = getChildren(childPath);

                byte[] bytes = getPropertyBytes(childPath);
                if (bytes == null || bytes.length == 0) {
                    if (childPathChildren == null || childPathChildren.isEmpty()) {
                        registerKeyValue(childPath, "");
                    }
                } else {
                    registerKeyValue(childPath, new String(bytes, Charset.forName("UTF-8")));
                }

                // Check children even if we have found a value for the current znode
                findProperties(childPath, childPathChildren);
            }
            log.trace("leaving findProperties for path: " + path);
        } catch (Exception exception) {
            ReflectionUtils.rethrowRuntimeException(exception);
        }
    }

    private void registerKeyValue(String path, String value) {
        String key = sanitizeKey(path);
        this.properties.put(StringUtils.hasText(this.prefix) ? this.prefix + "." + key : key, value);
    }

    private List<String> getChildren(String path) throws Exception {
        List<String> children = null;
        try {
            children = this.getSource().getChildren().forPath(path);
        } catch (KeeperException e) {
            if (e.code() != KeeperException.Code.NONODE) { // not found
                throw e;
            }
        }
        return children;
    }
}
