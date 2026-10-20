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

        public State(int enabled, boolean busy, String status, String detail, Access access) {
            if (enabled < -1 || enabled > 1) throw new IllegalArgumentException("Invalid 5G state");
            if (access == null) throw new IllegalArgumentException("Missing access state");
            this.enabled = enabled;
            this.busy = busy;
            this.status = status == null ? "" : status;
            this.detail = detail == null ? "" : detail;
            this.access = access;
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
    private volatile State current = unavailable(Access.SERVICE_STOPPED);
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
                publish(new State(-1, false, "尚未授权", "在 Shizuku 中允许 5G 开关访问", Access.PERMISSION_REQUIRED));
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
        // ShizukuProvider initializes Sui automatically. No separate initialization or daemon.
        Shizuku.addBinderDeadListener(binderDead, main);
        Shizuku.addRequestPermissionResultListener(permissionResult, main);
        Shizuku.addBinderReceivedListenerSticky(binderReceived, main);
    }

    public State state() { return current; }

    public void addListener(Listener listener) {
        if (listener == null) return;
        onMain(() -> {
            if (listeners.add(listener)) notifyListener(listener, current);
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
                    publish(new State(-1, false, "需要授权", "请在 Shizuku 的应用授权中允许访问", access));
                    return;
                }
                permissionRequestPending = true;
                publish(new State(-1, false, "等待授权", "请在授权窗口中允许访问", access));
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
            publish(new State(previous.enabled, true,
                    command == Command.READ ? "正在读取" : "正在切换", "", previous.access));
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
            return new State(-1, false, "未选择数据 SIM", "请在系统设置中选择默认移动数据 SIM", Access.UNSUPPORTED);
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

    private static State confirmed(int enabled) {
        return new State(enabled, false, enabled == 1 ? "5G 已开启" : "5G 已关闭", "默认数据 SIM", Access.READY);
    }

    private static State changedSubscription() {
        return new State(-1, false, "数据 SIM 已更改", "请刷新读取当前设置", Access.READY);
    }

    private State failure(Throwable error) {
        Log.w(TAG, "Telephony operation failed", error);
        Access access = safeAccess();
        if (access != Access.READY) return unavailable(access);
        if (error instanceof SecurityException) {
            return new State(-1, false, "系统拒绝访问", "当前系统不允许访问此 SIM 的 5G 设置", Access.UNSUPPORTED);
        }
        if (error instanceof ReflectiveOperationException || error instanceof LinkageError) {
            return new State(-1, false, "系统接口不可用", "当前系统未提供所需的 5G 设置接口", Access.UNSUPPORTED);
        }
        return new State(-1, false, "未能确认设置", "请刷新查看当前 5G 状态", Access.READY);
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

    private static State unavailable(Access access) {
        switch (access) {
            case READY: return new State(-1, false, "尚未读取", "", access);
            case PERMISSION_REQUIRED: return new State(-1, false, "需要授权", "允许 Shizuku 访问后即可切换", access);
            case UNSUPPORTED: return new State(-1, false, "Shizuku 版本过旧", "请更新 Shizuku 后重试", access);
            default: return new State(-1, false, "Shizuku 未运行", "启动 Shizuku 或 Sui 后即可连接", access);
        }
    }

    private void publish(State state) {
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
