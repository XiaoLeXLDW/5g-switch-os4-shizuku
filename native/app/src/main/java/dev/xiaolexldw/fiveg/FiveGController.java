/* Copyright (c) 2026 XiaoLeXLDW. */
package dev.xiaolexldw.fiveg;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.telephony.SubscriptionManager;
import android.util.Log;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import compat.NetworkTypesCompat;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;

/** Owns one in-flight operation; a later click is never saved for execution. */
public final class FiveGController {
    public enum Access { READY, SERVICE_STOPPED, PERMISSION_REQUIRED, UNSUPPORTED }

    public static final class State {
        public final int enabled;
        public final boolean busy;
        public final String status;
        public final String detail;
        public final Access access;
        private final int statusResource;
        private final int detailResource;

        public State(int enabled, boolean busy, String status, String detail, Access access) {
            this(enabled, busy, status, detail, access, 0, 0);
        }

        private State(int enabled, boolean busy, String status, String detail, Access access,
                int statusResource, int detailResource) {
            if (enabled < -1 || enabled > 1) throw new IllegalArgumentException("Invalid 5G state");
            if (access == null) throw new IllegalArgumentException("Missing access state");
            this.enabled = enabled;
            this.busy = busy;
            this.status = status == null ? "" : status;
            this.detail = detail == null ? "" : detail;
            this.access = access;
            this.statusResource = statusResource;
            this.detailResource = detailResource;
        }

        static State fromResources(Context context, int enabled, boolean busy,
                int statusResource, int detailResource, Access access) {
            return new State(enabled, busy, context.getString(statusResource),
                    detailResource == 0 ? "" : context.getString(detailResource), access,
                    statusResource, detailResource);
        }

        /** Resolve cached messages again when the app or service locale changes. */
        State localized(Context context) {
            if (statusResource == 0) return this;
            return fromResources(context, enabled, busy, statusResource, detailResource, access);
        }
    }

    public interface Listener { void onStateChanged(State state); }

    private enum Command { READ, ENABLE, DISABLE, TOGGLE }

    private static final String TAG = "FiveGController";
    private static final int PERMISSION_REQUEST = 50;
    private static FiveGController instance;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService telephony = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "fiveg-telephony");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicLong connectionGeneration = new AtomicLong();
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private volatile State current;
    private boolean permissionRequestPending;

    private final Shizuku.OnBinderReceivedListener binderReceived = this::onBinderReceived;
    private final Shizuku.OnBinderDeadListener binderDead = this::onBinderDead;
    private final Shizuku.OnRequestPermissionResultListener permissionResult = this::onPermissionResult;

    private void onBinderReceived() { onMain(() -> {
        connectionGeneration.incrementAndGet();
        permissionRequestPending = false;
        publish(unavailable(safeAccess()));
        refresh();
        FiveGTileService.requestUpdate(context);
    }); }

    private void onBinderDead() { onMain(() -> {
        connectionGeneration.incrementAndGet();
        permissionRequestPending = false;
        publish(unavailable(Access.SERVICE_STOPPED));
        FiveGTileService.requestUpdate(context);
    }); }

    private void onPermissionResult(int request, int result) {
        if (request != PERMISSION_REQUEST) return;
        onMain(() -> {
            permissionRequestPending = false;
            connectionGeneration.incrementAndGet();
            if (result == PackageManager.PERMISSION_GRANTED) {
                refresh();
            } else {
                publish(translated(-1, false, R.string.controller_not_authorized,
                        R.string.controller_allow_app, Access.PERMISSION_REQUIRED));
            }
            FiveGTileService.requestUpdate(context);
        });
    }

    public static synchronized FiveGController get(Context context) {
        if (instance == null) instance = new FiveGController(context.getApplicationContext());
        return instance;
    }

    private FiveGController(Context context) {
        this.context = context;
        current = unavailable(Access.SERVICE_STOPPED);
        // ShizukuProvider initializes Sui automatically. No separate initialization or daemon.
        Shizuku.addBinderDeadListener(binderDead, main);
        Shizuku.addRequestPermissionResultListener(permissionResult, main);
        Shizuku.addBinderReceivedListenerSticky(binderReceived, main);
    }

    public State state() { return current.localized(context); }

    public void addListener(Listener listener) {
        if (listener == null) return;
        onMain(() -> {
            if (listeners.add(listener)) notifyListener(listener, state());
        });
    }

    public void removeListener(Listener listener) {
        if (listener == null) return;
        onMain(() -> listeners.remove(listener));
    }

    public void refresh() { submit(Command.READ); }
    public void setEnabled(boolean enabled) { submit(enabled ? Command.ENABLE : Command.DISABLE); }
    public void toggle() { submit(Command.TOGGLE); }

    /** Called only by the application UI in response to an explicit user action. */
    public void requestPermission() {
        onMain(() -> {
            if (inFlight.get() || permissionRequestPending) return;
            Access access = safeAccess();
            if (access == Access.READY) { refresh(); return; }
            if (access != Access.PERMISSION_REQUIRED) { publish(unavailable(access)); return; }
            try {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    publish(translated(-1, false, R.string.controller_permission_required,
                            R.string.controller_allow_in_shizuku, access));
                    return;
                }
                permissionRequestPending = true;
                publish(translated(-1, false, R.string.controller_waiting_permission,
                        R.string.controller_allow_in_dialog, access));
                Shizuku.requestPermission(PERMISSION_REQUEST);
            } catch (RuntimeException error) {
                permissionRequestPending = false;
                Log.w(TAG, "Permission request failed", error);
                publish(unavailable(safeAccess()));
            }
        });
    }

    private void submit(Command command) {
        if (!inFlight.compareAndSet(false, true)) return;
        onMain(() -> {
            if (permissionRequestPending) { inFlight.set(false); return; }
            long generation = connectionGeneration.get();
            IBinder authorizationBinder = Shizuku.getBinder();
            State previous = current;
            publish(translated(previous.enabled, true,
                    command == Command.READ ? R.string.controller_reading : R.string.controller_switching,
                    0, previous.access));
            telephony.execute(() -> {
                State result;
                try { result = execute(command, generation, authorizationBinder); }
                catch (Exception | LinkageError error) { result = failure(error); }
                State completed = result;
                main.post(() -> finish(command, generation, authorizationBinder, completed));
            });
        });
    }

    private State execute(Command command, long generation, IBinder authorizationBinder) throws Exception {
        Access access = safeAccess();
        if (access != Access.READY) return unavailable(access);
        ensureCurrentConnection(generation, authorizationBinder);
        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (!SubscriptionManager.isValidSubscriptionId(subId) || subId == Integer.MAX_VALUE) {
            return translated(-1, false, R.string.controller_no_data_sim,
                    R.string.controller_select_data_sim, Access.UNSUPPORTED);
        }
        Object phone = phoneService();
        int before = NetworkTypesCompat.readState(phone, subId);
        ensureCurrentConnection(generation, authorizationBinder);
        if (subId != SubscriptionManager.getDefaultDataSubscriptionId()) return changedSubscription();
        if (command == Command.READ) return confirmed(before);

        boolean requested = command == Command.TOGGLE ? before != 1 : command == Command.ENABLE;
        ensureCurrentConnection(generation, authorizationBinder);
        if (subId != SubscriptionManager.getDefaultDataSubscriptionId()) {
            return changedSubscription();
        }
        // NetworkTypesCompat preserves other radio bits and returns only after successful readback.
        NetworkTypesCompat.setEnabled(phone, subId, requested, context.getPackageName());
        ensureCurrentConnection(generation, authorizationBinder);
        if (subId != SubscriptionManager.getDefaultDataSubscriptionId()) return changedSubscription();
        return confirmed(requested ? 1 : 0);
    }

    private Object phoneService() throws Exception {
        if (!HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/os/ServiceManager;", "Lcom/android/internal/telephony/ITelephony;",
                "Lcom/android/internal/telephony/ITelephony$Stub;")) {
            throw new ReflectiveOperationException("Hidden interfaces unavailable");
        }
        // Resolve afresh: keeping an old telephony Binder would survive a service restart incorrectly.
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        IBinder binder = (IBinder) invoke(serviceManager.getMethod("getService", String.class), null, "phone");
        if (binder == null || !binder.isBinderAlive()) throw new RemoteException("Phone service unavailable");
        Class<?> stub = Class.forName("com.android.internal.telephony.ITelephony$Stub");
        Object phone = invoke(stub.getMethod("asInterface", IBinder.class), null, new ShizukuBinderWrapper(binder));
        if (phone == null) throw new RemoteException("Phone interface unavailable");
        return phone;
    }

    private void ensureCurrentConnection(long generation, IBinder authorizationBinder) {
        if (generation != connectionGeneration.get() || authorizationBinder == null
                || authorizationBinder != Shizuku.getBinder() || !authorizationBinder.isBinderAlive()
                || safeAccess() != Access.READY) {
            throw new IllegalStateException("Connection changed during operation");
        }
    }

    private void finish(Command command, long generation, IBinder authorizationBinder, State result) {
        boolean invalidated = generation != connectionGeneration.get() || authorizationBinder != Shizuku.getBinder();
        inFlight.set(false);
        if (!invalidated) publish(result);
        if (command != Command.READ) FiveGTileService.requestUpdate(context);
        // A new Binder may have arrived while a previous call was unwinding. Only read on recovery.
        if (invalidated) refresh();
    }

    private State confirmed(int enabled) {
        return translated(enabled, false,
                enabled == 1 ? R.string.controller_enabled : R.string.controller_disabled,
                R.string.controller_default_data_sim, Access.READY);
    }

    private State changedSubscription() {
        return translated(-1, false, R.string.controller_data_sim_changed,
                R.string.controller_refresh_settings, Access.READY);
    }

    private State failure(Throwable error) {
        Log.w(TAG, "Telephony operation failed", error);
        Access access = safeAccess();
        if (access != Access.READY) return unavailable(access);
        if (error instanceof SecurityException) {
            return translated(-1, false, R.string.controller_access_denied,
                    R.string.controller_sim_access_denied, Access.UNSUPPORTED);
        }
        if (error instanceof ReflectiveOperationException || error instanceof LinkageError) {
            return translated(-1, false, R.string.controller_interface_unavailable,
                    R.string.controller_interface_missing, Access.UNSUPPORTED);
        }
        return translated(-1, false, R.string.controller_unconfirmed,
                R.string.controller_refresh_state, Access.READY);
    }

    private static Access safeAccess() {
        try {
            if (!Shizuku.pingBinder()) return Access.SERVICE_STOPPED;
            if (Shizuku.isPreV11()) return Access.UNSUPPORTED;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    ? Access.READY : Access.PERMISSION_REQUIRED;
        } catch (SecurityException denied) {
            return Access.PERMISSION_REQUIRED;
        } catch (RuntimeException unavailable) {
            return Access.SERVICE_STOPPED;
        }
    }

    private State unavailable(Access access) {
        switch (access) {
            case READY: return translated(-1, false, R.string.controller_not_read, 0, access);
            case PERMISSION_REQUIRED: return translated(-1, false, R.string.controller_permission_required,
                    R.string.controller_allow_to_switch, access);
            case UNSUPPORTED: return translated(-1, false, R.string.controller_shizuku_outdated,
                    R.string.controller_update_shizuku, access);
            default: return translated(-1, false, R.string.controller_shizuku_stopped,
                    R.string.controller_start_shizuku, access);
        }
    }

    private State translated(int enabled, boolean busy, int statusResource, int detailResource, Access access) {
        return State.fromResources(context, enabled, busy, statusResource, detailResource, access);
    }

    private void publish(State state) {
        state = state.localized(context);
        current = state;
        for (Listener listener : new ArrayList<>(listeners)) notifyListener(listener, state);
    }

    private static void notifyListener(Listener listener, State state) {
        try { listener.onStateChanged(state); }
        catch (RuntimeException error) { Log.w(TAG, "State listener failed", error); }
    }

    private void onMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else main.post(action);
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) throws Exception {
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new ReflectiveOperationException("System call failed", cause);
        }
    }
}
