package com.github.euonmyoji.epicbanitem.util.nbt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.Types;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.MemoryDataContainer;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
@SuppressWarnings("WeakerAccess")
public final class NbtTypeHelper {
    public static void setObject(DataQuery query, DataView view, Function<Object, Object> valueTransformer) {
        List<String> queryParts = query.getParts();
        int lastQueryPartIndex = queryParts.size() - 1;
        Object[] subViews = new Object[lastQueryPartIndex];
        for (int i = 0; i < lastQueryPartIndex; ++i) {
            Object subView = getObject(queryParts.get(i), i == 0 ? view : subViews[i - 1]);
            subViews[i] = Objects.isNull(subView) ? ImmutableMap.of() : subView;
        }
        for (int i = lastQueryPartIndex; i > 0; --i) {
            Object subView = setObject(queryParts.get(i), subViews[i - 1], valueTransformer);
            valueTransformer = o -> subView;
        }
        DataQuery firstQuery = DataQuery.of(queryParts.get(0));
        Object value = valueTransformer.apply(view.get(firstQuery).orElse(null));
        if (Objects.isNull(value)) {
            view.remove(firstQuery);
        } else {
            view.set(firstQuery, value);
        }
    }

    @Nullable
    public static Object getObject(DataQuery query, DataView view) {
        Object subView = view;
        for (String queryPart : query.getParts()) {
            subView = getObject(queryPart, subView);
            if (Objects.isNull(subView)) {
                break;
            }
        }
        return subView;
    }

    public static Object setObject(String key, Object view, Function<Object, Object> transformFunction) {
        Map<String, Object> map = getAsMap(view);
        if (Objects.nonNull(map)) {
            Object newValue = transformFunction.apply(map.get(key));
            if (Objects.nonNull(newValue)) {
                map = new HashMap<>(map); // make it mutable
                map.put(key, newValue);
                return map;
            } else {
                map = new HashMap<>(map); // make it mutable
                map.remove(key);
                return map;
            }
        }
        Integer index = Types.asInt(key);
        if (Objects.nonNull(index)) {
            List<Object> list = getAsList(view);
            if (Objects.nonNull(list)) {
                if (index >= 0) {
                    int size = list.size();
                    if (index == size) {
                        list = new ArrayList<>(list); // make it mutable
                        list.add(transformFunction.apply(size > 0 ? list.get(size - 1) : null));
                        return list;
                    } else if (index < size) {
                        Object newValue = transformFunction.apply(list.get(index));
                        if (Objects.nonNull(newValue)) {
                            list = new ArrayList<>(list); // make it mutable
                            list.set(index, newValue);
                            return list;
                        }
                    }
                }
                throw new IllegalArgumentException("Cannot set value for \"" + key + "\"");
            }
            long[] longArray = getAsLongArray(view);
            if (Objects.nonNull(longArray)) {
                if (index >= 0) {
                    int size = longArray.length;
                    if (index == size) {
                        Object newValue = transformFunction.apply((long) 0);
                        if (newValue instanceof Long) {
                            longArray = Arrays.copyOf(longArray, size + 1);
                            longArray[size] = (Long) newValue;
                            return longArray;
                        }
                    } else if (index < size) {
                        Object newValue = transformFunction.apply(longArray[index]);
                        if (newValue instanceof Long) {
                            longArray[index] = (Long) newValue;
                            return longArray;
                        }
                    }
                }
                throw new IllegalArgumentException("Cannot set value for \"" + key + "\"");
            }
            int[] intArray = getAsIntegerArray(view);
            if (Objects.nonNull(intArray)) {
                if (index >= 0) {
                    int size = intArray.length;
                    if (index == size) {
                        Object newValue = transformFunction.apply(0);
                        if (newValue instanceof Integer) {
                            intArray = Arrays.copyOf(intArray, size + 1);
                            intArray[size] = (Integer) newValue;
                            return intArray;
                        }
                    } else if (index < size) {
                        Object newValue = transformFunction.apply(intArray[index]);
                        if (newValue instanceof Integer) {
                            intArray[index] = (Integer) newValue;
                            return intArray;
                        }
                    }
                }
                throw new IllegalArgumentException("Cannot set value for \"" + key + "\"");
            }
            byte[] byteArray = getAsByteArray(view);
            if (Objects.nonNull(byteArray)) {
                if (index >= 0) {
                    int size = byteArray.length;
                    if (index == size) {
                        Object newValue = transformFunction.apply((byte) 0);
                        if (newValue instanceof Byte) {
                            byteArray = Arrays.copyOf(byteArray, size + 1);
                            byteArray[size] = (Byte) newValue;
                            return byteArray;
                        }
                    } else if (index < size) {
                        Object newValue = transformFunction.apply(byteArray[index]);
                        if (newValue instanceof Byte) {
                            byteArray[index] = (Byte) newValue;
                            return byteArray;
                        }
                    }
                }
                throw new IllegalArgumentException("Cannot set value for \"" + key + "\"");
            }
        }
        throw new IllegalArgumentException("Cannot set value for \"" + key + "\"");
    }

    @Nullable
    public static Object getObject(String key, Object view) {
        Map<String, ?> map = getAsMap(view);
        if (Objects.nonNull(map)) {
            Object result = map.get(key);
            if (Objects.nonNull(result)) {
                return result;
            }
        }
        Integer index = Types.asInt(key);
        if (Objects.nonNull(index)) {
            List<?> list = getAsList(view);
            if (Objects.nonNull(list)) {
                return index < 0 || index >= list.size() ? null : list.get(index);
            }
            long[] longArray = getAsLongArray(view);
            if (Objects.nonNull(longArray)) {
                return index < 0 || index >= longArray.length ? null : longArray[index];
            }
            int[] intArray = getAsIntegerArray(view);
            if (Objects.nonNull(intArray)) {
                return index < 0 || index >= intArray.length ? null : intArray[index];
            }
            byte[] byteArray = getAsByteArray(view);
            if (Objects.nonNull(byteArray)) {
                return index < 0 || index >= byteArray.length ? null : byteArray[index];
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static OptionalInt compare(@Nullable Object value, ConfigurationNode node) {
        if (Objects.nonNull(value)) {
            Object another = convert(value, node);
            if (another instanceof String) {
                if (value instanceof String) {
                    return OptionalInt.of(((String) another).compareTo(value.toString()));
                } else {
                    return OptionalInt.empty();
                }
            }
            if (another instanceof Double) {
                if (value instanceof Number) {
                    return OptionalInt.of(Double.compare((Double) another, ((Number) value).doubleValue()));
                } else {
                    return OptionalInt.empty();
                }
            }
            if (another instanceof Long) {
                if (value instanceof Number) {
                    return OptionalInt.of(Long.compare((Long) another, ((Number) value).longValue()));
                } else {
                    return OptionalInt.empty();
                }
            }
            if (another instanceof Float) {
                if (value instanceof Number) {
                    return OptionalInt.of(Float.compare((Float) another, ((Number) value).floatValue()));
                } else {
                    return OptionalInt.empty();
                }
            }
            if (another instanceof Number) {
                if (value instanceof Number) {
                    return OptionalInt.of(Integer.compare(((Number) another).intValue(), ((Number) value).intValue()));
                } else {
                    return OptionalInt.empty();
                }
            }
        }
        return OptionalInt.empty();
    }

    private static final Pattern BOOLEAN;
    private static final Pattern DOUBLE;
    private static final Pattern FLOAT;
    private static final Pattern BYTE;
    private static final Pattern LONG;
    private static final Pattern SHORT;
    private static final Pattern INTEGER;
    private static final Pattern NUMBER;

    // from vanilla
    static {
        BOOLEAN = Pattern.compile("(true|false)", Pattern.CASE_INSENSITIVE);
        BYTE = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)b", Pattern.CASE_INSENSITIVE);
        LONG = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)l", Pattern.CASE_INSENSITIVE);
        SHORT = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)s", Pattern.CASE_INSENSITIVE);
        INTEGER = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)", Pattern.CASE_INSENSITIVE);
        NUMBER = Pattern.compile("[-+]?(?:[0-9]+[.]|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?", Pattern.CASE_INSENSITIVE);
        FLOAT = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f", Pattern.CASE_INSENSITIVE);
        DOUBLE = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d", Pattern.CASE_INSENSITIVE);
    }

    public static Object convert(@Nullable Object previous, ConfigurationNode node) {
        if (node.hasListChildren()) {
            return convert(previous, node.getChildrenList());
        }
        if (node.hasMapChildren()) {
            return convert(previous, node.getChildrenMap());
        }
        String valueString = node.getString("");
        try {
            if (BOOLEAN.matcher(valueString).matches()) {
                boolean b = Boolean.parseBoolean(valueString.substring(1, valueString.length() - 1));
                return previous instanceof Byte ? b ? 1 : 0 : b;
            }
            if (DOUBLE.matcher(valueString).matches()) {
                return Double.parseDouble(valueString.substring(1, valueString.length() - 1));
            }
            if (FLOAT.matcher(valueString).matches()) {
                return Float.parseFloat(valueString.substring(1, valueString.length() - 1));
            }
            if (BYTE.matcher(valueString).matches()) {
                byte b = Byte.parseByte(valueString.substring(1, valueString.length() - 1));
                return previous instanceof Boolean ? b == 0 ? Boolean.FALSE : b == 1 ? Boolean.TRUE : b : b;
            }
            if (LONG.matcher(valueString).matches()) {
                return Long.parseLong(valueString.substring(1, valueString.length() - 1));
            }
            if (SHORT.matcher(valueString).matches()) {
                return Short.parseShort(valueString.substring(1, valueString.length() - 1));
            }
            if (INTEGER.matcher(valueString).matches()) {
                int i = Integer.parseInt(valueString);
                if (previous instanceof Byte && (byte) i == i) {
                    return (byte) i;
                }
                if (previous instanceof Short && (short) i == i) {
                    return (short) i;
                }
                if (previous instanceof Long) {
                    return (long) i;
                }
                if (previous instanceof Float) {
                    return (float) i;
                }
                if (previous instanceof Double) {
                    return (double) i;
                }
                return i;
            }
            if (NUMBER.matcher(valueString).matches()) {
                double n = Double.parseDouble(valueString);
                return previous instanceof Float ? (float) n : n;
            }
            return valueString;
        } catch (NumberFormatException e) {
            return valueString;
        }
    }

    private static Map<String, Object> convert(@Nullable Object previous, Map<Object, ? extends ConfigurationNode> map) {
        Map<String, Object> previousMap = getAsMap(previous);
        if (Objects.isNull(previousMap)) {
            previousMap = Collections.emptyMap();
        }
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            builder.put(key, convert(previousMap.get(key), entry.getValue()));
        }
        return builder.build();
    }

    private static Object convert(@Nullable Object previous, List<? extends ConfigurationNode> list) {
        int size = list.size();
        List<Object> result = new ArrayList<>(size);
        List<Object> previousList = getAsList(previous);
        boolean isByteArray = false, isIntArray = false, isLongArray = false;
        int previousSize = Objects.isNull(previousList) ? 0 : previousList.size();
        if (!list.isEmpty()) {
            boolean isFirst = true;
            for (int i = 0; i < size; ++i) {
                ConfigurationNode node = list.get(i);
                String value = node.getString("");
                if (isFirst) {
                    isFirst = false;
                    if (value.startsWith("B;")) {
                        int j = 1;
                        isByteArray = true;
                        while (++j < value.length()) {
                            if (!Character.isWhitespace(value.charAt(i))) {
                                break;
                            }
                        }
                        value = value.substring(j);
                        if (value.isEmpty()) {
                            continue;
                        }
                    }
                    if (value.startsWith("I;")) {
                        int j = 1;
                        isIntArray = true;
                        while (++j < value.length()) {
                            if (!Character.isWhitespace(value.charAt(i))) {
                                break;
                            }
                        }
                        value = value.substring(j);
                        if (value.isEmpty()) {
                            continue;
                        }
                    }
                    if (value.startsWith("L;")) {
                        int j = 1;
                        isLongArray = true;
                        while (++j < value.length()) {
                            if (!Character.isWhitespace(value.charAt(i))) {
                                break;
                            }
                        }
                        value = value.substring(j);
                        if (value.isEmpty()) {
                            continue;
                        }
                    }
                }
                try {
                    if (isByteArray) {
                        result.add(Byte.parseByte(value));
                    }
                    if (isIntArray) {
                        result.add(Integer.parseInt(value));
                    }
                    if (isLongArray) {
                        result.add(Long.parseLong(value));
                    }
                } catch (NumberFormatException e) {
                    isByteArray = false;
                    isIntArray = false;
                    isLongArray = false;
                }
                result.add(convert(i >= previousSize ? null : previousList.get(i), node));
            }
        }
        if (isByteArray) {
            // noinspection SuspiciousToArrayCall
            return result.toArray(new Byte[0]);
        }
        if (isIntArray) {
            // noinspection SuspiciousToArrayCall
            return result.toArray(new Integer[0]);
        }
        if (isLongArray) {
            // noinspection SuspiciousToArrayCall
            return result.toArray(new Long[0]);
        }
        return result;
    }

    public static boolean isEqual(@Nullable Object value, Object another) {
        Map<String, Object> valueMap = getAsMap(value);
        if (Objects.nonNull(valueMap)) {
            Map<String, Object> anotherMap = getAsMap(another);
            if (Objects.isNull(anotherMap) || anotherMap.size() != valueMap.size()) {
                return false;
            }
            for (Map.Entry<String, Object> entry : anotherMap.entrySet()) {
                if (!isEqual(valueMap.get(entry.getKey()), entry.getValue())) {
                     return false;
                }
            }
            return true;
        }
        List<Object> valueList = getAsList(value);
        if (Objects.nonNull(valueList)) {
            List<Object> anotherList = getAsList(another);
            if (Objects.isNull(anotherList) || anotherList.size() != valueList.size()) {
                return false;
            }
            for (int i = anotherList.size() - 1; i >= 0; --i) {
                if (!isEqual(anotherList.get(i), valueList.get(i))) {
                    return false;
                }
            }
            return true;
        }
        byte[] valueBytes = getAsByteArray(value);
        if (Objects.nonNull(valueBytes)) {
            return Arrays.equals(valueBytes, getAsByteArray(another));
        }
        int[] valueInts = getAsIntegerArray(value);
        if (Objects.nonNull(valueInts)) {
            return Arrays.equals(valueInts, getAsIntegerArray(another));
        }
        long[] valueLongs = getAsLongArray(value);
        if (Objects.nonNull(valueLongs)) {
            return Arrays.equals(valueLongs, getAsLongArray(another));
        }
        return another.equals(value);
    }

    @Nullable
    public static Byte getAsByte(@Nullable Object value) {
        return value instanceof Byte ? (Byte) value : value instanceof Boolean ? (Boolean) value ? (byte) 1 : 0 : null;
    }

    @Nullable
    public static Short getAsShort(@Nullable Object value) {
        return value instanceof Short ? (Short) value : null;
    }

    @Nullable
    public static Integer getAsInteger(@Nullable Object value) {
        return value instanceof Integer ? (Integer) value : null;
    }

    @Nullable
    public static Long getAsLong(@Nullable Object value) {
        return value instanceof Long ? (Long) value : null;
    }

    @Nullable
    public static Float getAsFloat(@Nullable Object value) {
        return value instanceof Float ? (Float) value : null;
    }

    @Nullable
    public static Double getAsDouble(@Nullable Object value) {
        return value instanceof Double ? (Double) value : null;
    }

    @Nullable
    public static String getAsString(@Nullable Object value) {
        return value instanceof String ? (String) value : null;
    }

    @Nullable
    public static byte[] getAsByteArray(@Nullable Object value) {
        return value instanceof byte[] ? (byte[]) value : value instanceof Byte[] ? to((Byte[]) value) : null;
    }

    @Nullable
    public static int[] getAsIntegerArray(@Nullable Object value) {
        return value instanceof int[] ? (int[]) value : value instanceof Integer[] ? to((Integer[]) value) : null;
    }

    @Nullable
    public static long[] getAsLongArray(@Nullable Object value) {
        return value instanceof long[] ? (long[]) value : value instanceof Long[] ? to((Long[]) value) : null;
    }

    @Nullable
    public static List<Object> getAsList(@Nullable Object value) {
        return value instanceof List ? ImmutableList.copyOf((List<?>) value) : null;
    }

    @Nullable
    public static Map<String, Object> getAsMap(@Nullable Object value) {
        return value instanceof Map ? to((Map<?, ?>) value) : value instanceof DataView ? to((DataView) value) : null;
    }

    @SuppressWarnings("deprecation")
    public static DataContainer toNbt(ItemStack stack) {
        DataContainer view = stack.toContainer();
        DataContainer result = new MemoryDataContainer(DataView.SafetyMode.NO_DATA_CLONED);

        view.get(DataQuery.of("ItemType")).ifPresent(id -> result.set(DataQuery.of("id"), id));
        view.get(DataQuery.of("UnsafeData")).ifPresent(nbt -> result.set(DataQuery.of("tag"), nbt));
        view.get(DataQuery.of("UnsafeDamage")).ifPresent(damage -> result.set(DataQuery.of("Damage"), damage));

        return result;
    }

    @SuppressWarnings("deprecation")
    public static ItemStack toItemStack(DataView view) {
        DataContainer result = new MemoryDataContainer(DataView.SafetyMode.NO_DATA_CLONED);

        result.set(DataQuery.of("Count"), 1);
        view.get(DataQuery.of("id")).ifPresent(id -> result.set(DataQuery.of("ItemType"), id));
        view.get(DataQuery.of("tag")).ifPresent(nbt -> result.set(DataQuery.of("UnsafeData"), nbt));
        view.get(DataQuery.of("Damage")).ifPresent(damage -> result.set(DataQuery.of("UnsafeDamage"), damage));

        return ItemStack.builder().fromContainer(result).build();
    }

    private static byte[] to(Byte[] array) {
        byte[] bytes = new byte[array.length];
        for (int i = 0; i < array.length; i++) {
            bytes[i] = array[i];
        }
        return bytes;
    }

    private static int[] to(Integer[] array) {
        int[] ints = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            ints[i] = array[i];
        }
        return ints;
    }

    private static long[] to(Long[] array) {
        long[] longs = new long[array.length];
        for (int i = 0; i < array.length; i++) {
            longs[i] = array[i];
        }
        return longs;
    }

    private static Map<String, Object> to(Map<?, ?> map) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            builder.put(entry.getKey().toString(), entry.getValue());
        }
        return builder.build();
    }

    private static Map<String, Object> to(DataView map) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        for (Map.Entry<DataQuery, Object> entry : map.getValues(false).entrySet()) {
            builder.put(entry.getKey().toString(), entry.getValue());
        }
        return builder.build();
    }

    private NbtTypeHelper() {
        // nothing here
    }
}