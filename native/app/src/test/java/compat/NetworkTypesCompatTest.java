package compat;

/** JVM contract tests against the exact helper compiled into the APK. */
public final class NetworkTypesCompatTest {
    private static final long NR = 1L << 19;
    private static final long LTE = 1L << 12;
    private static final long OTHER_TYPES = Long.MIN_VALUE | (1L << 60) | LTE | 1L;
    private static final String PACKAGE = "com.test.fiveg";
    private static int assertions;

    public interface Api17 {
        long getAllowedNetworkTypesForReason(int subId, int reason);
        boolean setAllowedNetworkTypesForReason(int subId, int reason, long types, String pkg);
    }

    public interface Api16 {
        long getAllowedNetworkTypesForReason(int subId, int reason);
        boolean setAllowedNetworkTypesForReason(int subId, int reason, long types);
    }

    public interface BothApis extends Api17, Api16 {}

    // Deliberately private: reflection must use the public interface, not this class.
    private static class Device {
        long mask;
        long requested;
        String callingPackage;
        int reads;
        int writes3;
        int writes4;
        int lastSubId = Integer.MIN_VALUE;
        int lastReason = Integer.MIN_VALUE;
        boolean accepts = true;
        boolean ignoreWrite;
        boolean denyWrite;
        int failReadAt;

        Device(long mask) { this.mask = mask; }

        public long getAllowedNetworkTypesForReason(int subId, int reason) {
            reads++;
            lastSubId = subId;
            lastReason = reason;
            if (reads == failReadAt) throw new SecurityException("read denied");
            return mask;
        }

        boolean write(int subId, int reason, long types, String pkg) {
            lastSubId = subId;
            lastReason = reason;
            requested = types;
            callingPackage = pkg;
            if (denyWrite) throw new SecurityException("write denied");
            if (accepts && !ignoreWrite) mask = types;
            return accepts;
        }

        int writes() { return writes3 + writes4; }
    }

    private static final class Device17 extends Device implements Api17 {
        Device17(long mask) { super(mask); }
        public boolean setAllowedNetworkTypesForReason(int subId, int reason, long types,
                                                       String pkg) {
            writes4++;
            return write(subId, reason, types, pkg);
        }
    }

    private static final class Device16 extends Device implements Api16 {
        Device16(long mask) { super(mask); }
        public boolean setAllowedNetworkTypesForReason(int subId, int reason, long types) {
            writes3++;
            return write(subId, reason, types, null);
        }
    }

    private static final class BothDevice extends Device implements BothApis {
        BothDevice(long mask) { super(mask); }
        public boolean setAllowedNetworkTypesForReason(int subId, int reason, long types,
                                                       String pkg) {
            writes4++;
            return write(subId, reason, types, pkg);
        }
        public boolean setAllowedNetworkTypesForReason(int subId, int reason, long types) {
            writes3++;
            return write(subId, reason, types, null);
        }
    }

    private interface CheckedRunnable { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        group("1/5 public-interface reflection, A17/A16 ABI, NR-only mask updates",
                NetworkTypesCompatTest::abiAndMasks);
        group("2/5 rejected/denied writes never fall back or repeat",
                NetworkTypesCompatTest::writeFailures);
        group("3/5 invalid subscriptions and unreadable state never write",
                NetworkTypesCompatTest::readGuards);
        group("4/5 write success requires successful matching readback",
                NetworkTypesCompatTest::readback);
        group("5/5 no-op and all-radio-disable safeguards",
                NetworkTypesCompatTest::noOpAndSafety);
        System.out.println("PASS: 5 groups, " + assertions + " assertions");
    }

    private static void abiAndMasks() throws Exception {
        check(!java.lang.reflect.Modifier.isPublic(Device17.class.getModifiers()),
                "fixture implementation must be private");
        Device17 modern = new Device17(OTHER_TYPES);
        NetworkTypesCompat.Result enabled = apply(Api17.class, modern, 7, true);
        eq(OTHER_TYPES, enabled.before, "original mask recorded");
        eq(OTHER_TYPES | NR, enabled.requested, "enable changes NR alone");
        eq(OTHER_TYPES | NR, enabled.after, "readback recorded");
        eq(OTHER_TYPES | NR, modern.mask, "all high/non-NR bits preserved");
        eq(1, modern.writes4, "A17 four-argument call");
        eq(0, modern.writes3, "A17 does not use old ABI");
        eq(2, modern.reads, "A17 reads before and after");
        eq(PACKAGE, modern.callingPackage, "A17 receives package identity");
        eq(7, modern.lastSubId, "requested SIM passed through");
        eq(0, modern.lastReason, "only USER reason modified");

        Device16 old = new Device16(OTHER_TYPES | NR);
        NetworkTypesCompat.Result disabled = apply(Api16.class, old, 4, false);
        eq(OTHER_TYPES | NR, disabled.before, "A16 before mask");
        eq(OTHER_TYPES, disabled.requested, "disable removes NR alone");
        eq(OTHER_TYPES, disabled.after, "A16 readback");
        eq(OTHER_TYPES, old.mask, "A16 high/non-NR bits preserved");
        eq(1, old.writes3, "A16 three-argument fallback");
        eq(0, old.writes4, "A16 does not use absent ABI");
        eq(2, old.reads, "A16 reads before and after");

        BothDevice both = new BothDevice(LTE);
        apply(BothApis.class, both, 1, true);
        eq(1, both.writes4, "new ABI wins when both are declared");
        eq(0, both.writes3, "no duplicate legacy call");
        eq(PACKAGE, both.callingPackage, "both-ABI fixture receives package");
    }

    private static void writeFailures() throws Exception {
        BothDevice rejected = new BothDevice(LTE);
        rejected.accepts = false;
        expect(IllegalStateException.class, () -> apply(BothApis.class, rejected, 1, true));
        eq(1, rejected.writes4, "false response attempted once");
        eq(0, rejected.writes3, "false response never retries old ABI");
        eq(1, rejected.reads, "false response fails before readback");
        eq(LTE, rejected.mask, "false response leaves state untouched");

        BothDevice denied = new BothDevice(LTE);
        denied.denyWrite = true;
        Throwable failure = expect(SecurityException.class,
                () -> apply(BothApis.class, denied, 1, true));
        eq("write denied", failure.getMessage(), "original exception unwrapped");
        eq(1, denied.writes4, "permission failure attempted once");
        eq(0, denied.writes3, "permission failure never retries old ABI");
        eq(1, denied.reads, "permission failure fails before readback");
        eq(LTE, denied.mask, "permission failure leaves state untouched");

        Device16 rejectedOld = new Device16(LTE);
        rejectedOld.accepts = false;
        expect(IllegalStateException.class, () -> apply(Api16.class, rejectedOld, 1, true));
        eq(1, rejectedOld.writes(), "old-ABI rejection attempted once");
    }

    private static void readGuards() throws Exception {
        for (int subId : new int[] {-1, Integer.MAX_VALUE}) {
            Device17 device = new Device17(LTE);
            expect(IllegalArgumentException.class,
                    () -> NetworkTypesCompat.read(Api17.class, device, subId));
            expect(IllegalArgumentException.class,
                    () -> apply(Api17.class, device, subId, true));
            eq(0, device.reads, "invalid subscription never calls getter: " + subId);
            eq(0, device.writes(), "invalid subscription never calls setter: " + subId);
        }
        Device17 denied = new Device17(LTE);
        denied.failReadAt = 1;
        expect(SecurityException.class, () -> apply(Api17.class, denied, 1, true));
        eq(1, denied.reads, "initial read attempted once");
        eq(0, denied.writes(), "failed initial read prohibits write");

        Device17 empty = new Device17(0);
        expect(IllegalStateException.class, () -> apply(Api17.class, empty, 1, true));
        eq(0, empty.writes(), "zero mask prohibits write");
        expect(IllegalStateException.class,
                () -> NetworkTypesCompat.read(Api17.class, null, 1));

        Device17 zeroSim = new Device17(LTE);
        apply(Api17.class, zeroSim, 0, true);
        eq(1, zeroSim.writes(), "subscription zero is valid");
        eq(0, zeroSim.lastSubId, "subscription zero passes through unchanged");
    }

    private static void readback() throws Exception {
        Device17 unchanged = new Device17(LTE);
        unchanged.ignoreWrite = true;
        expect(IllegalStateException.class, () -> apply(Api17.class, unchanged, 1, true));
        eq(1, unchanged.writes(), "mismatched readback does not repeat write");
        eq(2, unchanged.reads, "mismatched readback was queried");
        eq(LTE, unchanged.mask, "fixture accurately models ignored write");

        Device16 stillEnabled = new Device16(LTE | NR);
        stillEnabled.ignoreWrite = true;
        expect(IllegalStateException.class, () -> apply(Api16.class, stillEnabled, 1, false));
        eq(1, stillEnabled.writes(), "disable mismatch does not repeat write");

        Device17 unreadable = new Device17(LTE);
        unreadable.failReadAt = 2;
        expect(SecurityException.class, () -> apply(Api17.class, unreadable, 1, true));
        eq(1, unreadable.writes(), "readback exception does not repeat write");
        eq(2, unreadable.reads, "readback exception propagates");
    }

    private static void noOpAndSafety() throws Exception {
        Device17 alreadyOn = new Device17(OTHER_TYPES | NR);
        NetworkTypesCompat.Result on = apply(Api17.class, alreadyOn, 1, true);
        eq(0, alreadyOn.writes(), "already-enabled operation performs no write");
        eq(1, alreadyOn.reads, "no-op reads once");
        eq(OTHER_TYPES | NR, on.before, "no-op before");
        eq(on.before, on.requested, "no-op requested");
        eq(on.before, on.after, "no-op after");

        Device16 alreadyOff = new Device16(OTHER_TYPES);
        apply(Api16.class, alreadyOff, 1, false);
        eq(0, alreadyOff.writes(), "already-disabled operation performs no write");
        eq(1, alreadyOff.reads, "disabled no-op reads once");

        Device17 nrOnly = new Device17(NR);
        expect(IllegalStateException.class, () -> apply(Api17.class, nrOnly, 1, false));
        eq(0, nrOnly.writes(), "removing the last radio type is prohibited");
        eq(NR, nrOnly.mask, "last radio type preserved");

        Device17 invalidCaller = new Device17(LTE);
        expect(IllegalArgumentException.class,
                () -> NetworkTypesCompat.apply(Api17.class, invalidCaller, 1, true, null));
        expect(IllegalArgumentException.class,
                () -> NetworkTypesCompat.apply(Api17.class, invalidCaller, 1, true, ""));
        eq(0, invalidCaller.reads, "missing caller is rejected before device access");
        eq(0, invalidCaller.writes(), "missing caller prohibits write");
    }

    private static NetworkTypesCompat.Result apply(Class<?> api, Device device, int subId,
                                                    boolean enabled) throws Exception {
        return NetworkTypesCompat.apply(api, device, subId, enabled, PACKAGE);
    }

    private static void group(String name, CheckedRunnable test) throws Exception {
        test.run();
        System.out.println("PASS " + name);
    }

    private static Throwable expect(Class<? extends Throwable> type, CheckedRunnable action)
            throws Exception {
        assertions++;
        try {
            action.run();
        } catch (Throwable actual) {
            if (type.isInstance(actual)) return actual;
            throw new AssertionError("Expected " + type.getName() + ", got " + actual, actual);
        }
        throw new AssertionError("Expected " + type.getName() + ", no exception thrown");
    }

    private static void eq(long expected, long actual, String message) {
        check(expected == actual, message + ": expected " + expected + ", got " + actual);
    }

    private static void eq(String expected, String actual, String message) {
        check(expected == null ? actual == null : expected.equals(actual),
                message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
