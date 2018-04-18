package com.github.euonmyoji.epicbanitem.util.nbt;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializers;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class QueryResult {
    public static final TypeToken<QueryResult> RESULT_TYPE_TOKEN;

    static {
        RESULT_TYPE_TOKEN = TypeToken.of(QueryResult.class);
        TypeSerializers.getDefaultSerializers().registerType(RESULT_TYPE_TOKEN, new Serializer());
    }

    public static Optional<QueryResult> check(boolean condition) {
        return condition ? success() : failure();
    }

    public static Optional<QueryResult> failure() {
        return Optional.empty();
    }

    public static Optional<QueryResult> success() {
        return Optional.of(new QueryResult(false, false, ImmutableMap.of()));
    }

    public static Optional<QueryResult> successArray(Map<String, QueryResult> children) {
        return Optional.of(new QueryResult(true, false, children));
    }

    public static Optional<QueryResult> successObject(Map<String, QueryResult> children) {
        return Optional.of(new QueryResult(false, true, children));
    }

    private final boolean isArray;
    private final boolean isObject;
    private final Map<String, QueryResult> children;

    private QueryResult(boolean isArray, boolean isObject, Map<String, QueryResult> children) {
        this.children = ImmutableMap.copyOf(children);
        this.isObject = isObject;
        this.isArray = isArray;
    }

    public boolean isArrayChildren() {
        return this.isArray;
    }

    public boolean isObjectChildren() {
        return this.isObject;
    }

    public Map<String, QueryResult> getChildren() {
        return this.children;
    }

    public QueryResult merge(Map<String, QueryResult> anotherChildren) {
        Map<String, QueryResult> children = new LinkedHashMap<>(this.children);
        for (Map.Entry<String, QueryResult> entry : anotherChildren.entrySet()) {
            QueryResult value = entry.getValue();
            children.compute(entry.getKey(), (k, v) -> {
                if (Objects.isNull(v)) {
                    return value;
                } else {
                    return v.merge(value.getChildren());
                }
            });
        }
        return new QueryResult(this.isArray, this.isObject, children);
    }

    private static class Serializer implements TypeSerializer<QueryResult> {

        @Override
        public QueryResult deserialize(TypeToken<?> type, ConfigurationNode node) {
            for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.getChildrenMap().entrySet()) {
                String key = entry.getKey().toString();
                if ("array".equals(key)) {
                    ConfigurationNode value = entry.getValue();
                    ImmutableMap.Builder<String, QueryResult> builder = ImmutableMap.builder();
                    value.getChildrenMap().forEach((k, v) -> builder.put(k.toString(), this.deserialize(type, v)));
                    return new QueryResult(true, false, builder.build());
                }
                if ("object".equals(key)) {
                    ConfigurationNode value = entry.getValue();
                    ImmutableMap.Builder<String, QueryResult> builder = ImmutableMap.builder();
                    value.getChildrenMap().forEach((k, v) -> builder.put(k.toString(), this.deserialize(type, v)));
                    return new QueryResult(false, true, builder.build());
                }
            }
            return new QueryResult(false, false, ImmutableMap.of());
        }

        @Override
        public void serialize(TypeToken<?> type, QueryResult obj, ConfigurationNode node) {
            node.setValue(ImmutableMap.of());
            if (obj.isArrayChildren()) {
                node.getNode("array").setValue(obj.getChildren());
            }
            if (obj.isObjectChildren()) {
                node.getNode("object").setValue(obj.getChildren());
            }
        }
    }

    @Override
    public String toString() {
        String str = "{";
        String nextSeparator = "";
        str = this.isArrayChildren() ? "Array{" : str;
        str = this.isObjectChildren() ? "Object{" : str;
        StringBuilder sb = new StringBuilder("QueryResult").append(str);
        for (Map.Entry<String, QueryResult> entry : this.children.entrySet()) {
            sb.append(nextSeparator).append(entry.getKey());
            sb.append('=').append(entry.getValue());
            nextSeparator = ", ";
        }
        return sb.append('}').toString();
    }
}