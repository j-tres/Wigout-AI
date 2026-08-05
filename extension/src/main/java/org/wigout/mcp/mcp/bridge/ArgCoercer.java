package org.wigout.mcp.mcp.bridge;

import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Coerces JSON argument values into Java parameters for reflective bw_call
 * invocation. Supported: primitives (+ boxed), String, UUID (from string),
 * enums (by constant name), API-object parameters (a path string resolved
 * via PathResolver), and arrays of the above (from JSON lists — covers
 * varargs, which reflection sees as a trailing array parameter).
 *
 * Before the type-specific dispatch, any JSON value already assignable to
 * the (boxed) parameter type is passed through unchanged — this covers
 * abstract/widening parameter types the type-specific branches don't name
 * explicitly (e.g. {@code java.lang.Number}, as used by
 * {@code SettableRangedValue.set(Number, Number)}), plus {@code Object} and
 * {@code CharSequence}, without needing a dedicated branch per such type.
 */
public final class ArgCoercer {

    private final PathResolver resolver;

    public ArgCoercer(PathResolver resolver) {
        this.resolver = resolver;
    }

    public Object[] coerce(Method method, List<Object> jsonArgs) throws BitwigApiException {
        final String operation = "bw_call";
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length != jsonArgs.size()) {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                method.getName() + " expects " + paramTypes.length + " argument(s), got " + jsonArgs.size()
                + ". Signature: " + method);
        }
        Object[] out = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            out[i] = coerceOne(paramTypes[i], jsonArgs.get(i), operation, method.getName(), i);
        }
        return out;
    }

    /** An overload chosen by selectAndCoerce, with its coerced argument array. */
    public record Selection(Method method, Object[] args) {}

    /**
     * Picks the best overload for the given JSON args by per-argument
     * compatibility score (exact 3 > numeric widening 2 > coercible 1;
     * any incompatible arg rejects the candidate), then coerces. Ties keep
     * the candidates' declaration order (stable sort) — deterministic.
     * Callers must pre-filter deprecated methods (they own the refusal
     * message, which needs ApiDocIndex).
     */
    public Selection selectAndCoerce(List<Method> candidates, List<Object> jsonArgs, String operation)
            throws BitwigApiException {
        if (candidates.isEmpty()) {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                "No candidate methods to select from.");
        }
        List<Method> arityMatches = candidates.stream()
            .filter(m -> m.getParameterCount() == jsonArgs.size()).toList();
        if (arityMatches.isEmpty()) {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                candidates.get(0).getName() + " exists but not with " + jsonArgs.size()
                + " argument(s). Arities available: " + candidates.stream()
                    .map(m -> String.valueOf(m.getParameterCount())).distinct().sorted().toList());
        }
        List<Method> ranked = arityMatches.stream()
            .map(m -> java.util.Map.entry(m, score(m, jsonArgs)))
            .filter(e -> e.getValue() >= 0)
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())) // stable: ties keep order
            .map(java.util.Map.Entry::getKey)
            .toList();
        BitwigApiException lastMismatch = null;
        for (Method m : ranked) {
            try {
                return new Selection(m, coerce(m, jsonArgs));
            } catch (BitwigApiException e) {
                lastMismatch = e; // scoring is an approximation; try the next-ranked overload
            }
        }
        throw new BitwigApiException(ErrorCode.INVALID_PARAMETER_TYPE, operation,
            "No overload of " + candidates.get(0).getName() + " accepts the given argument(s). Candidates: "
            + arityMatches.stream().map(ArgCoercer::signatureOf).toList()
            + (lastMismatch != null ? ". Last mismatch: " + lastMismatch.getMessage() : ""));
    }

    /** Sum of per-arg scores; -1 if any argument is incompatible with its parameter. */
    private int score(Method m, List<Object> args) {
        int total = 0;
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            int s = scoreOne(params[i], args.get(i));
            if (s < 0) {
                return -1;
            }
            total += s;
        }
        return total;
    }

    /** 3 = exact/assignable, 2 = numeric widening, 1 = coercible, -1 = incompatible. */
    private int scoreOne(Class<?> param, Object json) {
        if (json == null) {
            return param.isPrimitive() ? -1 : 1;
        }
        Class<?> boxed = box(param);
        if (boxed.isInstance(json)) {
            return 3;
        }
        if (json instanceof Number && Number.class.isAssignableFrom(boxed)) {
            return 2;
        }
        if (json instanceof Boolean) {
            return -1; // no boolean conversions exist beyond exact
        }
        if (param == UUID.class && json instanceof String) {
            return 1;
        }
        if (param.isEnum() && json instanceof String) {
            return 1;
        }
        if (param.isArray() && json instanceof List) {
            return 1;
        }
        // API-object parameter via bridge-path string.
        if (!param.isPrimitive() && !param.isArray() && !param.isEnum()
                && param != String.class && param != UUID.class && json instanceof String) {
            return 1;
        }
        return -1;
    }

    private static String signatureOf(Method m) {
        return m.getName() + "(" + java.util.Arrays.stream(m.getParameterTypes())
            .map(Class::getSimpleName).reduce((a, b) -> a + ", " + b).orElse("") + ")";
    }

    private Object coerceOne(Class<?> type, Object json, String operation, String methodName, int index)
            throws BitwigApiException {
        if (json != null && box(type).isInstance(json)) {
            return json;
        }
        if (type == int.class || type == Integer.class) {
            if (json instanceof Number n) return n.intValue();
        } else if (type == long.class || type == Long.class) {
            if (json instanceof Number n) return n.longValue();
        } else if (type == double.class || type == Double.class) {
            if (json instanceof Number n) return n.doubleValue();
        } else if (type == float.class || type == Float.class) {
            if (json instanceof Number n) return n.floatValue();
        } else if (type == boolean.class || type == Boolean.class) {
            if (json instanceof Boolean b) return b;
        } else if (type == String.class) {
            if (json instanceof String s) return s;
        } else if (type == UUID.class) {
            if (json instanceof String s) {
                try {
                    return UUID.fromString(s);
                } catch (IllegalArgumentException e) {
                    throw mismatch(operation, methodName, index, "UUID string", json);
                }
            }
        } else if (type.isEnum()) {
            if (json instanceof String s) {
                for (Object constant : type.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equalsIgnoreCase(s)) {
                        return constant;
                    }
                }
                throw mismatch(operation, methodName, index, "one of " + java.util.Arrays.toString(type.getEnumConstants()), json);
            }
        } else if (type.isArray()) {
            if (json instanceof List<?> list) {
                Object array = Array.newInstance(type.getComponentType(), list.size());
                for (int i = 0; i < list.size(); i++) {
                    Array.set(array, i, coerceOne(type.getComponentType(), list.get(i), operation, methodName, index));
                }
                return array;
            }
        } else {
            // API-object parameter: accept a bridge path string.
            if (json instanceof String path) {
                Object resolved = resolver.resolve(path).target();
                if (type.isInstance(resolved)) {
                    return resolved;
                }
                throw mismatch(operation, methodName, index,
                    type.getSimpleName() + " (a path resolving to one)", "path '" + path + "' resolved to "
                    + (resolved == null ? "null" : resolved.getClass().getSimpleName()));
            }
        }
        throw mismatch(operation, methodName, index, type.getSimpleName(), json);
    }

    private static BitwigApiException mismatch(String operation, String methodName, int index,
                                               String expected, Object got) {
        return new BitwigApiException(ErrorCode.INVALID_PARAMETER_TYPE, operation,
            methodName + " argument " + index + ": expected " + expected + ", got "
            + (got == null ? "null" : got instanceof String ? "'" + got + "'" : got.toString()));
    }

    /** Maps a primitive Class to its boxed wrapper; non-primitive types pass through unchanged. */
    private static Class<?> box(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
