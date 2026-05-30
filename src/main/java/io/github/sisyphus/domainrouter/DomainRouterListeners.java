package io.github.sisyphus.domainrouter;

import com.google.inject.Inject;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import pl.spcode.navauth.api.event.NavAuthEventListener;
import pl.spcode.navauth.api.event.Subscribe;
import pl.spcode.navauth.api.event.velocity.AuthenticatedInitialServerEvent;

/**
 * Listens to NavAuth's {@link AuthenticatedInitialServerEvent} and applies per-domain routing.
 *
 * <p>Reads a {@code domain-router.properties} config file from the plugin's data directory. Each
 * line maps a virtual hostname to a Velocity server name. When a player authenticates, their
 * connection domain is checked against the map; if a match is found, the player is sent to the
 * corresponding server. A configurable fallback server handles unmatched domains.</p>
 */
public class DomainRouterListeners implements NavAuthEventListener {

  private static final String CONFIG_FILE = "domain-router.properties";
  private static final String FALLBACK_KEY = "fallback";

  @Inject private ProxyServer server;

  private Logger logger;
  private final Map<String, String> domainRoutes = new HashMap<>();
  private String fallbackServer = "";

  /** Called by the main plugin class after the injector creates this instance. */
  void loadConfig(Path dataDirectory, Logger logger) {
    this.logger = logger;
    Path configPath = dataDirectory.resolve(CONFIG_FILE);

    if (Files.notExists(configPath)) {
      logger.warn(
          "Config not found at {}. Creating default — domain routing is disabled until configured.",
          configPath.toAbsolutePath());
      createDefaultConfig(configPath);
      return;
    }

    Properties props = new Properties();
    try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
      props.load(reader);
    } catch (IOException e) {
      logger.error("Failed to load configuration from {}", configPath, e);
      return;
    }

    for (String key : props.stringPropertyNames()) {
      String value = props.getProperty(key).trim();
      if (key.equals(FALLBACK_KEY)) {
        fallbackServer = value;
        logger.info("Fallback server configured: '{}'", fallbackServer);
      } else if (!value.isEmpty()) {
        domainRoutes.put(key, value);
        logger.info("Route: domain '{}' -> server '{}'", key, value);
      }
    }

    logger.info(
        "DomainRouter configured: {} domain routes, fallback='{}'",
        domainRoutes.size(),
        fallbackServer.isEmpty() ? "(NavAuth default)" : fallbackServer);
  }

  private void createDefaultConfig(Path configPath) {
    List<String> lines =
        Arrays.asList(
            "# DomainRouter Configuration",
            "# Maps a connection domain (virtual host) to a Velocity server name.",
            "#",
            "# Format: domain.name=server_name",
            "#",
            "# Example:",
            "#   play.example.com=lobby",
            "#   creative.example.com=creative",
            "#   mc.example.org=survival",
            "#",
            "# Fallback server when no domain matches. Leave empty to let NavAuth",
            "# choose the default (its own initialServers list).",
            "fallback=");
    try {
      Files.createDirectories(configPath.getParent());
      Files.write(configPath, lines, StandardCharsets.UTF_8);
      logger.info("Created default config at {}", configPath.toAbsolutePath());
    } catch (IOException e) {
      logger.error("Failed to write default config to {}", configPath, e);
    }
  }

  @Subscribe
  public void handleAuthenticatedInitialServer(AuthenticatedInitialServerEvent event) {
    var player = event.getPlayer();

    // ------------------------------------------------------------------
    // 1. Extract the domain the player connected through
    // ------------------------------------------------------------------
    var virtualHost = player.getVirtualHost();

    if (virtualHost.isEmpty()) {
      logger.info(
          "[{}] No virtual host info available — falling through to NavAuth default routing.",
          player.getUsername());
      return;
    }

    String host = virtualHost.get().getHostString();
    int port = virtualHost.get().getPort();

    logger.info(
        "[{}] Authenticated — connected via '{}:{}'", player.getUsername(), host, port);

    // ------------------------------------------------------------------
    // 2. Look up the domain in our routing table
    // ------------------------------------------------------------------
    String targetServer = domainRoutes.get(host);

    // If no exact match, try stripping the port if it somehow ended up in hostString
    if (targetServer == null) {
      int colonIdx = host.lastIndexOf(':');
      if (colonIdx > 0) {
        String stripped = host.substring(0, colonIdx);
        targetServer = domainRoutes.get(stripped);
      }
    }

    // ------------------------------------------------------------------
    // 3. Route or fallback
    // ------------------------------------------------------------------
    if (targetServer != null && !targetServer.isEmpty()) {
      routePlayer(event, targetServer, "matched domain '" + host + "'");
    } else {
      logger.info(
          "[{}] No route for domain '{}' — {}", player.getUsername(), host,
          fallbackServer.isEmpty()
              ? "falling through to NavAuth default routing."
              : "trying fallback server '" + fallbackServer + "'.");
      if (!fallbackServer.isEmpty()) {
        routePlayer(event, fallbackServer, "fallback");
      }
    }
  }

  /**
   * Attempts to set the initial server to {@code serverName}. Logs a warning if the server is not
   * registered on the proxy and does <strong>not</strong> modify the event, letting NavAuth apply
   * its own default.
   */
  private void routePlayer(AuthenticatedInitialServerEvent event, String serverName, String reason) {
    var player = event.getPlayer();
    var serverOpt = server.getServer(serverName);

    if (serverOpt.isPresent()) {
      RegisteredServer target = serverOpt.get();
      logger.info(
          "[{}] Routing to '{}' ({})", player.getUsername(), target.getServerInfo().getName(), reason);
      event.setInitialServer(target);
    } else {
      logger.warn(
          "[{}] Server '{}' is not registered on the proxy ({}) — using NavAuth default.",
          player.getUsername(), serverName, reason);
      // Don't modify the event — leave it to NavAuth's own initialServers list.
    }
  }
}
