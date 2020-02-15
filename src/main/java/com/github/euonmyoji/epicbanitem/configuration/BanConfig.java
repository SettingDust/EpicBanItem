package com.github.euonmyoji.epicbanitem.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.euonmyoji.epicbanitem.check.CheckRule;
import com.github.euonmyoji.epicbanitem.check.CheckRuleIndex;
import com.github.euonmyoji.epicbanitem.util.repackage.org.bstats.sponge.Metrics;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Streams;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.Types;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializers;
import org.slf4j.Logger;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.plugin.PluginContainer;

/**
 * @author yinyangshi GiNYAi ustc_zzzz
 */
@SuppressWarnings("UnstableApiUsage")
@Singleton
public class BanConfig {
    static final int CURRENT_VERSION = 1;
    private static final TypeToken<CheckRule> RULE_TOKEN = TypeToken.of(CheckRule.class);
    private static final Comparator<String> RULE_NAME_COMPARATOR = Comparator.naturalOrder();
    private static final Comparator<CheckRule> COMPARATOR = Comparator
        .comparing(CheckRule::getPriority)
        .thenComparing(CheckRule::getName, RULE_NAME_COMPARATOR);

    private final Path path;

    @Inject
    private Logger logger;

    @Inject
    private Metrics metrics;

    @Inject
    private AutoFileLoader fileLoader;

    @Inject
    private Injector injector;

    private LoadingCache<String, ImmutableList<CheckRule>> cacheFromIdToCheckRules;
    private ImmutableSortedMap<String, CheckRule> checkRulesByName;
    private ImmutableListMultimap<CheckRuleIndex, CheckRule> checkRulesByIndex;

    @Inject
    private BanConfig(@ConfigDir(sharedRoot = false) Path configDir, EventManager eventManager, PluginContainer pluginContainer) {
        this.path = configDir.resolve("banitem.conf");

        eventManager.registerListeners(pluginContainer, this);
    }

    private static int parseOrElse(String string, int orElse) {
        try {
            return Integer.parseUnsignedInt(string);
        } catch (NumberFormatException e) {
            return orElse;
        }
    }

    private static String findNewName(@Nullable String name, Predicate<String> alreadyExists) {
        if (name == null) {
            name = "undefined-1";
        }
        name = name.toLowerCase();
        if (!CheckRule.NAME_PATTERN.matcher(name).matches()) {
            name = "unrecognized-1";
        }
        if (alreadyExists.test(name)) {
            int defNumber = 2;
            int dashIndex = name.lastIndexOf('-');
            int number = parseOrElse(name.substring(dashIndex + 1), defNumber);
            String prefix = dashIndex >= 0 ? name.substring(0, dashIndex) : name;
            for (name = prefix + '-' + number; alreadyExists.test(name); name = prefix + '-' + number) {
                ++number;
            }
        }
        return name;
    }

    private static List<CheckRule> addAndSort(List<CheckRule> original, CheckRule newCheckRule) {
        CheckRule[] newCheckRules;
        int ruleSize = original.size();
        newCheckRules = new CheckRule[ruleSize + 1];
        newCheckRules[ruleSize] = newCheckRule;
        for (int i = 0; i < ruleSize; ++i) {
            CheckRule checkRule = original.get(i);
            if (checkRule.getName().equals(newCheckRule.getName())) {
                throw new IllegalArgumentException("Rule with the same name already exits");
            }
            newCheckRules[i] = checkRule;
        }
        Arrays.sort(newCheckRules, COMPARATOR);
        return Arrays.asList(newCheckRules);
    }

    public Comparator<CheckRule> getComparator() {
        return COMPARATOR;
    }

    public Set<CheckRuleIndex> getItems() {
        return Objects.requireNonNull(checkRulesByIndex).keySet();
    }

    public List<CheckRule> getRulesWithIdFiltered(String id) {
        return MoreObjects.firstNonNull(cacheFromIdToCheckRules.get(id), ImmutableList.of());
    }

    public List<CheckRule> getRules(CheckRuleIndex index) {
        return Objects.requireNonNull(checkRulesByIndex).get(index);
    }

    public Collection<CheckRule> getRules() {
        return checkRulesByName.values();
    }

    public Set<String> getRuleNames() {
        return checkRulesByName.keySet();
    }

    public Optional<CheckRule> getRule(String name) {
        return Optional.ofNullable(checkRulesByName.get(name));
    }

    public CompletableFuture<Boolean> addRule(CheckRuleIndex index, CheckRule newRule) throws IOException {
        try {
            SortedMap<String, CheckRule> rulesByName = new TreeMap<>(checkRulesByName);

            if (rulesByName.put(newRule.getName(), newRule) != null) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }

            ListMultimap<CheckRuleIndex, CheckRule> rules;
            rules = Multimaps.newListMultimap(new LinkedHashMap<>(), ArrayList::new);

            rules.putAll(this.checkRulesByIndex);
            rules.putAll(index, addAndSort(rules.removeAll(index), newRule));

            this.checkRulesByIndex = ImmutableListMultimap.copyOf(rules);
            this.checkRulesByName = ImmutableSortedMap.copyOfSorted(rulesByName);
            this.cacheFromIdToCheckRules.invalidateAll();

            forceSave();
            return CompletableFuture.completedFuture(Boolean.TRUE);
            // TODO: return CompletableFuture from forceSave
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public CompletableFuture<Boolean> removeRule(@SuppressWarnings("unused todo: why") CheckRuleIndex index, String name) throws IOException {
        try {
            CheckRule rule = checkRulesByName.get(name);
            if (rule != null) {
                SortedMap<String, CheckRule> rulesByName = new TreeMap<>(checkRulesByName);
                rulesByName.remove(name);
                ImmutableListMultimap.Builder<CheckRuleIndex, CheckRule> builder = ImmutableListMultimap.builder();
                checkRulesByIndex.forEach(
                    (s, rule1) -> {
                        if (!rule1.getName().equals(name)) {
                            builder.put(s, rule1);
                        }
                    }
                );
                this.checkRulesByIndex = builder.build();
                this.checkRulesByName = ImmutableSortedMap.copyOfSorted(rulesByName);
                this.cacheFromIdToCheckRules.invalidateAll();

                forceSave();
                return CompletableFuture.completedFuture(Boolean.TRUE);
                // TODO: return CompletableFuture from forceSave
            } else {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void load(ConfigurationNode node) throws IOException {
        // noinspection unused
        int version = node
            .getNode("epicbanitem-version")
            .<Integer>getValue(
                Types::asInt,
                () -> {
                    logger.warn("Ban Config at {} is missing epicbanitem-version option.", path);
                    logger.warn("Try loading using current version {}.", CURRENT_VERSION);
                    return CURRENT_VERSION;
                }
            );
        SortedMap<String, CheckRule> byName = new TreeMap<>(RULE_NAME_COMPARATOR);
        ImmutableListMultimap.Builder<CheckRuleIndex, CheckRule> byItem = ImmutableListMultimap.builder();
        Map<Object, ? extends ConfigurationNode> checkRules = node.getNode("epicbanitem").getChildrenMap();
        boolean needSave = false;
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : checkRules.entrySet()) {
            List<CheckRule> ruleList = new ArrayList<>();
            CheckRuleIndex index = CheckRuleIndex.of(entry.getKey().toString());
            for (ConfigurationNode checkRuleNode : entry.getValue().getChildrenList()) {
                // fix id
                if (!index.isWildcard()) {
                    ConfigurationNode queryIndex = checkRuleNode.getNode("query", "id");
                    if (queryIndex.isVirtual()) {
                        queryIndex.setValue(index.toString());
                        needSave = true;
                    }
                }
                // fix name
                ConfigurationNode nameNode = checkRuleNode.getNode("name");
                ConfigurationNode legacyNameNode = checkRuleNode.getNode("legacy-name");
                String name = nameNode.getValue(Types::asString, legacyNameNode.getString(""));
                if (!CheckRule.NAME_PATTERN.matcher(name).matches() || byName.containsKey(name)) {
                    String newName = findNewName(name, byName::containsKey);

                    legacyNameNode.setValue(name);
                    nameNode.setValue(newName);

                    String msg = "Find duplicate or illegal name, renamed \"{}\" in {} to \"{}\"";
                    logger.warn(msg, name, index, newName);

                    needSave = true;
                }
                // add to rules
                try {
                    CheckRule rule = Objects.requireNonNull(checkRuleNode.getValue(RULE_TOKEN));
                    byName.put(rule.getName(), rule);
                    ruleList.add(rule);
                } catch (ObjectMappingException e) {
                    throw new IOException(e);
                }
            }
            ruleList.sort(COMPARATOR);
            byItem.putAll(index, ruleList);
        }
        this.checkRulesByIndex = byItem.build();
        this.checkRulesByName = ImmutableSortedMap.copyOfSorted(byName);
        this.cacheFromIdToCheckRules.invalidateAll();
        if (needSave) {
            forceSave();
        }
    }

    private void forceSave() {
        this.fileLoader.forceSaving(this.path);
    }

    private void save(ConfigurationNode node) throws IOException {
        node.getNode("epicbanitem-version").setValue(CURRENT_VERSION);
        for (Map.Entry<CheckRuleIndex, CheckRule> entry : checkRulesByIndex.entries()) {
            String key = entry.getKey().toString();
            CheckRule value = entry.getValue();
            try {
                node.getNode("epicbanitem", key).getAppendedNode().setValue(RULE_TOKEN, value);
            } catch (ObjectMappingException e) {
                throw new IOException(e);
            }
        }
    }

    @Listener
    public void onPreInit(GamePreInitializationEvent event) {
        this.checkRulesByIndex = ImmutableListMultimap.of();
        this.checkRulesByName = ImmutableSortedMap.<String, CheckRule>orderedBy(RULE_NAME_COMPARATOR).build();

        this.cacheFromIdToCheckRules =
            Caffeine
                .newBuilder()
                .build(
                    k -> {
                        CheckRuleIndex i = CheckRuleIndex.of(), j = CheckRuleIndex.of(k);
                        Iterable<? extends List<CheckRule>> rules = Arrays.asList(getRules(i), getRules(j));
                        Stream<CheckRule> stream = Streams.stream(Iterables.mergeSorted(rules, getComparator()));
                        return stream.filter(r -> r.idIndexFilter().test(k)).collect(ImmutableList.toImmutableList());
                    }
                );

        TypeSerializers.getDefaultSerializers().registerType(BanConfig.RULE_TOKEN, injector.getInstance(CheckRule.Serializer.class));

        fileLoader.addListener(path, this::load, this::save);
        if (Files.notExists(path)) {
            fileLoader.forceSaving(path, n -> n.getNode("epicbanitem-version").setValue(CURRENT_VERSION).getParent());
        }
    }

    @Listener
    public void onStarting(GameStartingServerEvent event) {
        metrics.addCustomChart(new Metrics.SingleLineChart("enabledCheckRules", () -> this.getRules().size()));
    }
}
