package xyz.daikirai.client;

import net.minecraft.client.multiplayer.ServerAddress;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;

/**
 * SRV support for server addresses ({@code _minecraft._tcp.<host>}).
 *
 * <p>1.8.9's {@link ServerAddress} only parses {@code host[:port]}; servers published through an SRV
 * record - {@code hypixel.today} is one, pointing at a proxy on port 25567 - therefore fail to connect,
 * because the client dials the domain's web IP on 25565. This resolves the record the way modern
 * Minecraft does and hands back the SRV target instead.</p>
 *
 * <p>The query goes through the JNDI DNS provider. {@code dns:} (the form 1.13+ uses) has no nameserver
 * on some JVMs - it fails here with "Unknown DNS server" - so the lookup retries with the machine's
 * configured resolvers and then with public ones. Every failure is silent: without a record the address
 * is returned untouched, exactly like vanilla.</p>
 */
public final class ServerSrv {

    /** Answers are cached for the session; a server list repaints the same address many times. */
    private static final Map<String, String[]> CACHE = new HashMap<String, String[]>();

    /** Extra resolvers tried after the configured ones. */
    private static final String[] FALLBACK_RESOLVERS = {"223.5.5.5", "8.8.8.8"};

    private ServerSrv() {
    }

    /**
     * Returns the SRV target of {@code host} as {@code {target, port}}, or {@code null} when the host has
     * no usable record.
     *
     * @param host the address the player typed, without a port
     */
    public static String[] lookup(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        String key = host.toLowerCase();
        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }
        String[] result = query("_minecraft._tcp." + key);
        CACHE.put(key, result);
        return result;
    }

    /** Applies the SRV record to a parsed address; returns the original instance when there is none. */
    public static ServerAddress apply(String raw, ServerAddress parsed) {
        if (raw == null || parsed == null || raw.indexOf(':') >= 0 || raw.startsWith("[")) {
            // An explicit port always wins, and IPv6 literals are never SRV names.
            return parsed;
        }
        String[] srv = lookup(raw);
        if (srv == null || srv[0] == null || srv[0].isEmpty()) {
            return parsed;
        }
        try {
            int port = Integer.parseInt(srv[1]);
            if (port <= 0 || port > 65535) {
                return parsed;
            }
            // ServerAddress(String, int) is private; a reflected constructor keeps this class free of
            // a mixin of its own (the signature is oblivious to the obfuscated field names).
            java.lang.reflect.Constructor<ServerAddress> constructor =
                    ServerAddress.class.getDeclaredConstructor(String.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(srv[0], port);
        } catch (Throwable ignored) {
            return parsed;
        }
    }

    private static String[] query(String name) {
        for (String url : resolverUrls()) {
            String[] answer = query(name, url);
            if (answer != null) {
                return answer;
            }
        }
        return null;
    }

    private static String[] query(String name, String providerUrl) {
        try {
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            // Keep a blocked resolver from freezing the connect screen.
            env.put("com.sun.jndi.dns.timeout.initial", "1500");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            if (providerUrl != null) {
                env.put("java.naming.provider.url", providerUrl);
            }
            Attributes attributes = new InitialDirContext(env).getAttributes(name, new String[]{"SRV"});
            Attribute srv = attributes == null ? null : attributes.get("SRV");
            if (srv == null || srv.size() == 0) {
                return null;
            }
            // Records look like "priority weight port target."
            String[] best = null;
            int bestPriority = Integer.MAX_VALUE;
            int bestWeight = -1;
            for (int i = 0; i < srv.size(); i++) {
                String record = String.valueOf(srv.get(i));
                String[] parts = record.trim().split("\\s+");
                if (parts.length < 4) {
                    continue;
                }
                try {
                    int priority = Integer.parseInt(parts[0]);
                    int weight = Integer.parseInt(parts[1]);
                    int port = Integer.parseInt(parts[2]);
                    String target = parts[3];
                    if (target.endsWith(".")) {
                        target = target.substring(0, target.length() - 1);
                    }
                    if (target.isEmpty() || port <= 0) {
                        continue;
                    }
                    if (priority < bestPriority || priority == bestPriority && weight > bestWeight) {
                        bestPriority = priority;
                        bestWeight = weight;
                        best = new String[]{target, Integer.toString(port)};
                    }
                } catch (NumberFormatException ignored) {
                    // Malformed record: keep looking.
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** {@code dns:} first, then the machine's resolvers, then a couple of public ones. */
    private static List<String> resolverUrls() {
        List<String> urls = new ArrayList<String>();
        urls.add("dns:");
        for (String server : systemResolvers()) {
            urls.add("dns://" + server);
        }
        for (String server : FALLBACK_RESOLVERS) {
            urls.add("dns://" + server);
        }
        return urls;
    }

    /** The configured DNS servers, read through the JDK's own resolver configuration. */
    private static List<String> systemResolvers() {
        List<String> servers = new ArrayList<String>();
        try {
            Class<?> type = Class.forName("sun.net.dns.ResolverConfiguration");
            Method open = type.getMethod("open");
            Object configuration = open.invoke(null);
            Method nameservers = type.getMethod("nameservers");
            Object value = nameservers.invoke(configuration);
            if (value instanceof List) {
                for (Object server : (List<?>) value) {
                    servers.add(String.valueOf(server));
                }
            }
        } catch (Throwable ignored) {
            // Not available on this JVM: the fallbacks still cover it.
        }
        return servers;
    }
}
