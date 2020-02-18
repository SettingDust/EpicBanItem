package com.github.euonmyoji.epicbanitem.locale;

import com.github.euonmyoji.epicbanitem.configuration.AutoFileLoader;
import com.github.euonmyoji.epicbanitem.util.TextUtil;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.asset.AssetManager;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextTemplate;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.Tuple;

/**
 * @author yinyangshi GiNYAi ustc_zzzz
 */
@SuppressWarnings("SpellCheckingInspection")
@Singleton
public class LocaleService {
    private static final String MISSING_MESSAGE_KEY = "epicbanitem.error.missingMessage";

    private ResourceBundle resourceBundle;

    private Map<String, TextTemplate> cache;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDir;

    @Inject
    private AutoFileLoader fileLoader;

    @Inject
    public LocaleService(AssetManager assetManager, PluginContainer pluginContainer, EventManager eventManager)
        throws IOException {
        this.cache = Maps.newConcurrentMap();

        Asset fallbackAsset = assetManager
            .getAsset(pluginContainer, "lang/" + Locale.getDefault().toString().toLowerCase() + ".lang")
            .orElse(assetManager.getAsset(pluginContainer, "lang/en_us.lang").orElseThrow(NoSuchFieldError::new));

        this.resourceBundle = new PropertyResourceBundle(new InputStreamReader(fallbackAsset.getUrl().openStream(), Charsets.UTF_8));

        cache.put(
            MISSING_MESSAGE_KEY,
            TextUtil.parseTextTemplate(
                getString(MISSING_MESSAGE_KEY).orElse("Missing language key: {message_key}"),
                Collections.singleton("message_key")
            )
        );

        eventManager.registerListener(pluginContainer, GamePreInitializationEvent.class, this::onPreInit);
    }

    public Optional<String> getString(String path) {
        Optional<String> stringOptional = Optional.empty();
        try {
            stringOptional = Optional.of(resourceBundle.getString(path));
        } catch (MissingResourceException ignore) {}
        return stringOptional;
    }

    @SafeVarargs
    public final Text getTextWithFallback(String path, Tuple<String, ?>... tuples) {
        return getText(path, tuples)
            .orElseGet(() -> getTextWithFallback(MISSING_MESSAGE_KEY, Tuple.of("message_key", path)).toBuilder().color(TextColors.RED).build());
    }

    @SafeVarargs
    public final Optional<Text> getText(String path, Tuple<String, ?>... tuples) {
        return getText(path, Arrays.stream(tuples).collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond)));
    }

    public Optional<Text> getText(String path, Map<String, ?> params) {
        Optional<Text> textOptional = Optional.empty();
        if (!cache.containsKey(path)) {
            getString(path).ifPresent(value -> cache.put(path, TextUtil.parseTextTemplate(value, params.keySet())));
        }
        if (cache.containsKey(path)) {
            textOptional = Optional.of(cache.get(path).apply(params).build());
        }
        return textOptional;
    }

    @Deprecated
    public Text getMessage(String path, Map<String, ?> params) {
        return getText(path, params)
            .orElseGet(() -> getTextWithFallback(MISSING_MESSAGE_KEY, Tuple.of("message_key", path)).toBuilder().color(TextColors.RED).build());
    }

    @Deprecated
    public Text getMessage(String key) {
        return getMessage(key, Collections.emptyMap());
    }

    @Deprecated
    public Text getMessage(String key, String k1, Object v1) {
        return getMessage(key, ImmutableMap.of(k1, v1));
    }

    @Deprecated
    public Text getMessage(String key, String k1, Object v1, String k2, Object v2) {
        return getMessage(key, ImmutableMap.of(k1, v1, k2, v2));
    }

    @Deprecated
    public Text getMessage(String key, String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        return getMessage(key, ImmutableMap.of(k1, v1, k2, v2, k3, v3));
    }

    @Deprecated
    public Text getMessage(String key, String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
        return getMessage(key, ImmutableMap.of(k1, v1, k2, v2, k3, v3, k4, v4));
    }

    @Deprecated
    public Text getMessage(String key, String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5) {
        return getMessage(key, ImmutableMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5));
    }

    private void onPreInit(GamePreInitializationEvent event) throws IOException {
        Path path = this.configDir.resolve("message.lang");
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) Files.createFile(path);
        PropertyResourceBundle extraResourceBundle = new PropertyResourceBundle(new InputStreamReader(Files.newInputStream(path), Charsets.UTF_8));
        extraResourceBundle.setParent(this.resourceBundle);
        this.resourceBundle = extraResourceBundle;
    }

    private static final class PropertyResourceBundle extends java.util.PropertyResourceBundle {

        public PropertyResourceBundle(Reader reader) throws IOException {
            super(reader);
        }

        @Override
        public void setParent(ResourceBundle parent) {
            super.setParent(parent);
        }
    }
}
