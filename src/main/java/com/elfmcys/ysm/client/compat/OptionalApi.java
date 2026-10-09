package com.elfmcys.ysm.client.compat;

import com.elfmcys.ysm.YesSteveModel;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Calls an installed provider's real API without linking an absent provider at startup. */
public final class OptionalApi {
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();
    private static final ClassValue<ConcurrentHashMap<CallKey, Method>> METHODS = new ClassValue<>() {
        @Override protected ConcurrentHashMap<CallKey, Method> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final ClassValue<ConcurrentHashMap<String, Field>> FIELDS = new ClassValue<>() {
        @Override protected ConcurrentHashMap<String, Field> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private OptionalApi() {}

    public static void initialize(String integration, Runnable initializer) {
        query(integration, null, () -> { initializer.run(); return null; });
    }

    /** Cleanup must also execute after a provider has been disabled mid-operation. */
    public static void cleanup(String integration, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError error) {
            disable(integration, error);
        }
    }

    private static void disable(String integration, Throwable error) {
        if (DISABLED.add(integration)) {
            YesSteveModel.LOGGER.warn("Disabling incompatible optional integration {}", integration, error);
        }
    }

    public static <T> T query(String integration, T fallback, Supplier<T> action) {
        if (DISABLED.contains(integration)) return fallback;
        try {
            return action.get();
        } catch (RuntimeException | LinkageError error) {
            disable(integration, error);
            return fallback;
        }
    }

    public static Class<?> type(String name) {
        try {
            return Class.forName(name, false, OptionalApi.class.getClassLoader());
        } catch (ClassNotFoundException error) {
            throw new Failure(error);
        }
    }

    public static boolean instance(String name, Object value) {
        return value != null && type(name).isInstance(value);
    }

    public static Object callStatic(String name, String method, Object... args) {
        return invoke(type(name), null, method, args);
    }

    public static Object call(Object target, String method, Object... args) {
        if (target == null) throw new Failure(new NullPointerException("Provider target: " + method));
        return invoke(target.getClass(), target, method, args);
    }

    private static Object invoke(Class<?> type, Object target, String name, Object[] args) {
        var key = new CallKey(name, target == null,
                Arrays.stream(args).map(a -> a == null ? Void.class : a.getClass()).toList());
        try {
            var method = METHODS.get(type).computeIfAbsent(key, ignored -> {
                Method found = null;
                for (var candidate : type.getMethods()) {
                    if (!candidate.getName().equals(name) || candidate.isBridge()
                            || Modifier.isStatic(candidate.getModifiers()) != (target == null)
                            || !accepts(candidate.getParameterTypes(), args)) continue;
                    if (found != null) throw new Failure(new NoSuchMethodException("Ambiguous provider API: " + key));
                    found = candidate;
                }
                if (found == null) throw new Failure(new NoSuchMethodException(type.getName() + "." + key));
                if (!found.trySetAccessible()) throw new Failure(new IllegalAccessException(found.toString()));
                return found;
            });
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException error) {
            throw failure(error);
        }
    }

    private static boolean accepts(Class<?>[] parameters, Object[] args) {
        if (parameters.length != args.length) return false;
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                if (parameters[i].isPrimitive()) return false;
            } else if (!boxed(parameters[i]).isInstance(args[i])) return false;
        }
        return true;
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Field field(Class<?> type, String name) {
        return FIELDS.get(type).computeIfAbsent(name, ignored -> {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    var result = current.getDeclaredField(name);
                    if (!result.trySetAccessible()) throw new Failure(new IllegalAccessException(result.toString()));
                    return result;
                } catch (NoSuchFieldException ignoredField) { }
            }
            throw new Failure(new NoSuchFieldException(type.getName() + "." + name));
        });
    }

    public static Object get(Object target, String name) {
        try { return field(target.getClass(), name).get(target); }
        catch (IllegalAccessException error) { throw new Failure(error); }
    }

    public static Object getStatic(String type, String name) {
        try { return field(type(type), name).get(null); }
        catch (IllegalAccessException error) { throw new Failure(error); }
    }

    public static void set(Object target, String name, Object value) {
        try { field(target.getClass(), name).set(target, value); }
        catch (IllegalAccessException error) { throw new Failure(error); }
    }

    public static void setStatic(String type, String name, Object value) {
        try { field(type(type), name).set(null, value); }
        catch (IllegalAccessException error) { throw new Failure(error); }
    }

    public static Object construct(String name, Object... args) {
        try {
            java.lang.reflect.Constructor<?> found = null;
            for (var constructor : type(name).getConstructors()) {
                if (!accepts(constructor.getParameterTypes(), args)) continue;
                if (found != null) throw new Failure(new NoSuchMethodException("Ambiguous provider constructor: " + name));
                found = constructor;
            }
            if (found == null) throw new Failure(new NoSuchMethodException("Provider constructor: " + name));
            return found.newInstance(args);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    private static RuntimeException failure(ReflectiveOperationException error) {
        Throwable cause = error instanceof InvocationTargetException invoked ? invoked.getCause() : error;
        if (cause instanceof Error fatal) throw fatal;
        return cause instanceof RuntimeException runtime ? runtime : new Failure(cause);
    }

    public static boolean isDisabled(String integration) { return DISABLED.contains(integration); }

    public static boolean bool(Object value) { return (Boolean) value; }
    public static float number(Object value) { return ((Number) value).floatValue(); }
    public static long integer(Object value) { return ((Number) value).longValue(); }
    public static String enumName(Object value) { return ((Enum<?>) value).name(); }

    private record CallKey(String name, boolean isStatic, List<Class<?>> arguments) {}
    public static final class Failure extends RuntimeException {
        public Failure(Throwable cause) { super(cause); }
    }
}
