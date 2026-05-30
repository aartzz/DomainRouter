package io.github.sisyphus.domainrouter;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import java.nio.file.Path;
import org.slf4j.Logger;
import pl.spcode.navauth.api.NavAuthAPI;
import pl.spcode.navauth.api.event.NavAuthEventListener;

/**
 * Velocity plugin that routes authenticated players to servers based on the domain
 * (virtual host) they connected through.
 *
 * <p>Listens to NavAuth's {@code AuthenticatedInitialServerEvent}, inspects
 * {@code player.getVirtualHost()}, and sets the initial server according to a
 * configurable domain-to-server mapping.</p>
 */
@Plugin(
    id = "domainrouter",
    name = "DomainRouter",
    version = "1.0.0",
    url = "https://github.com/sisyphus",
    description = "Routes authenticated players to servers based on their connection domain",
    authors = {"Sisyphus"},
    dependencies = {@Dependency(id = "navauth")})
public class DomainRouterPlugin implements NavAuthEventListener {

  @Inject private Logger logger;
  @Inject private Injector injector;
  @Inject @DataDirectory private Path dataDirectory;

  @Subscribe
  public void onProxyInitialization(final ProxyInitializeEvent event) {
    logger.info("Loading DomainRouter plugin...");

    NavAuthAPI api = NavAuthAPI.getInstance();
    DomainRouterListeners listeners = injector.getInstance(DomainRouterListeners.class);
    listeners.loadConfig(dataDirectory, logger);

    api.getEventBus().register(listeners);

    logger.info("DomainRouter loaded successfully.");
  }
}
