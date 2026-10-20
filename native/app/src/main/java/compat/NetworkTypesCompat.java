// Copyright 2026 XiaoLeXLDW.
package compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Uses the device's interface declaration, without embedding an old Binder ABI. */
public final class NetworkTypesCompat {
    static final long NR = 1L << 19;
    private static final int USER = 0;

    private NetworkTypesCompat() {}

    public static final class Result {
        public final long before;
        public final long requested;
        public final long after;

        Result(long before, long requested, long after) {
            this.before = before;
            this.requested = requested;
            this.after = after;
        }
    }

    public static int readState(Object phone, int subId) throws Exception {
        long mask = read(api(), phone, subId);
        return (mask & NR) != 0 ? 1 : 0;
    }

    public static Result setEnabled(Object phone, int subId, boolean enabled,
                                    String callingPackage) throws Exception {
        return apply(api(), phone, subId, enabled, callingPackage);
    }

    private static Class<?> api() throws ClassNotFoundException {
        return Class.forName("com.android.internal.telephony.ITelephony");
    }

    static long read(Class<?> api, Object phone, int subId) throws Exception {
        if (subId < 0 || subId == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("No default data SIM");
        }
        if (phone == null) {
            throw new IllegalStateException("Phone service unavailable");
        }
        Method getter = api.getMethod("getAllowedNetworkTypesForReason", int.class, int.class);
        Object value = invoke(getter, phone, subId, USER);
        if (!(value instanceof Long) || ((Long) value).longValue() == 0) {
            throw new IllegalStateException("Allowed network types unavailable");
        }
        return ((Long) value).longValue();
    }

    static Result apply(Class<?> api, Object phone, int subId, boolean enabled,
                        String callingPackage) throws Exception {
        if (callingPackage == null || callingPackage.length() == 0) {
            throw new IllegalArgumentException("Missing calling package");
        }
        long before = read(api, phone, subId);
        long requested = enabled ? before | NR : before & ~NR;
        if (requested == 0) {
            throw new IllegalStateException("Refusing to disable all radio technologies");
        }
        if (requested == before) {
            return new Result(before, requested, before);
        }

        Method setter;
        boolean usesCallingPackage;
        try {
            setter = api.getMethod("setAllowedNetworkTypesForReason",
                    int.class, int.class, long.class, String.class);
            usesCallingPackage = true;
        } catch (NoSuchMethodException missingNewSignature) {
            setter = api.getMethod("setAllowedNetworkTypesForReason",
                    int.class, int.class, long.class);
            usesCallingPackage = false;
        }
        Object accepted = usesCallingPackage
                ? invoke(setter, phone, subId, USER, requested, callingPackage)
                : invoke(setter, phone, subId, USER, requested);
        if (!Boolean.TRUE.equals(accepted)) {
            throw new IllegalStateException("System rejected the requested network types");
        }
        long after = read(api, phone, subId);
        if (((after & NR) != 0) != enabled) {
            throw new IllegalStateException("5G setting was not confirmed by readback");
        }
        return new Result(before, requested, after);
    }

    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new ReflectiveOperationException("Phone service call failed", cause);
        }
    }
}
