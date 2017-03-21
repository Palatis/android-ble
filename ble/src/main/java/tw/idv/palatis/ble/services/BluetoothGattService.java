package tw.idv.palatis.ble.services;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import dalvik.system.DexFile;
import tw.idv.palatis.ble.database.WeakObservable;

import static tw.idv.palatis.ble.BuildConfig.DEBUG;

/**
 * A delegate service to wrap around the native {@link android.bluetooth.BluetoothGattService}
 * <p>
 * Created by Palatis on 2017/3/15.
 */
public class BluetoothGattService {
    private static final String TAG = BluetoothGattService.class.getSimpleName();

    private static final UUID UUID_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static ArrayMap<UUID, Constructor<? extends BluetoothGattService>> sServiceConstructors = new ArrayMap<>();

    public static void initialize(Context context) {
        try {
            final DexFile dexFile = new DexFile(context.getPackageCodePath());
            final Enumeration<String> classNames = dexFile.entries();
            while (classNames.hasMoreElements()) {
                // skip framework components...
                final String className = classNames.nextElement();
                if (className.startsWith("android"))
                    continue;

                final Class<?> klass = Class.forName(className);
                try {
                    if (klass != null && !Modifier.isAbstract(klass.getModifiers()) && !klass.equals(BluetoothGattService.class) && BluetoothGattService.class.isAssignableFrom(klass)) {
                        final Field uuidField = klass.getDeclaredField("UUID_SERVICE");
                        uuidField.setAccessible(true);
                        UUID uuid = (UUID) uuidField.get(null);
                        //noinspection unchecked
                        final Constructor<? extends BluetoothGattService> constructor = (Constructor<? extends BluetoothGattService>) klass.getDeclaredConstructor(BluetoothGatt.class, android.bluetooth.BluetoothGattService.class);
                        constructor.setAccessible(true);

                        if (DEBUG)
                            Log.d(TAG, "initialize(): Found constructor for BluetoothGattService: " + klass.getName());
                        sServiceConstructors.put(uuid, constructor);
                    }
                } catch (NoSuchFieldException ex) {
                    if (DEBUG)
                        Log.d(TAG, "initialize(): no UUID_SERVICE static field for " + klass.getName());
                } catch (NoSuchMethodException ex) {
                    if (DEBUG)
                        Log.d(TAG, "initialize(): no constructor with the correct signature <c-tor>(BluetoothGatt.class, BluetoothGattService.class) for " + klass.getName());
                }
            }
        } catch (IOException | ClassNotFoundException | IllegalAccessException ex) {
            throw new IllegalArgumentException("problem when finding sub-classes of BluetoothGattService in package", ex);
        }
    }

    public static BluetoothGattService fromNativeService(@NonNull BluetoothGatt gatt, @NonNull android.bluetooth.BluetoothGattService nativeService) {
        final Constructor<? extends BluetoothGattService> ctor = sServiceConstructors.get(nativeService.getUuid());
        if (ctor != null) {
            try {
                return ctor.newInstance(gatt, nativeService);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                throw new IllegalArgumentException("problem when creating an instance of BluetoothGattService " + nativeService.getUuid(), ex);
            }
        }
        return new BluetoothGattService(gatt, nativeService);
    }

    private static final ArrayMap<BluetoothGatt, Executor> sGattExecutors = new ArrayMap<>();

    protected final Handler mHandler = new Handler(Looper.getMainLooper());

    @NonNull
    final android.bluetooth.BluetoothGattService mNativeService;
    private final BluetoothGatt mGatt;

    private final OnErrorObservable mOnErrorObservable = new OnErrorObservable();

    protected BluetoothGattService(@NonNull BluetoothGatt gatt, @NonNull android.bluetooth.BluetoothGattService nativeService) {
        mGatt = gatt;
        mNativeService = nativeService;
    }

    public int getType() {
        return mNativeService.getType();
    }

    /**
     * get the UUID of this service
     *
     * @return the UUID of the service
     */
    public UUID getUuid() {
        return mNativeService.getUuid();
    }

    /**
     * if there are multiple service with identical UUID for a device, this is to distinquish them.
     *
     * @return the instance ID of the service
     */
    public int getInstanceId() {
        return mNativeService.getInstanceId();
    }

    private static Executor getExecutorForGatt(BluetoothGatt gatt) {
        Executor executor = sGattExecutors.get(gatt);
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
            sGattExecutors.put(gatt, executor);
        }
        return executor;
    }

    protected final void readCharacteristic(final BluetoothGattCharacteristic characteristic) {
        synchronized (sGattExecutors) {
            getExecutorForGatt(mGatt).execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        synchronized (mGatt) {
                            mGatt.readCharacteristic(characteristic);

                            long start_ms = System.currentTimeMillis();
                            mGatt.wait(3000);
                            if (System.currentTimeMillis() - start_ms >= 3000)
                                mOnErrorObservable.dispatchTimedOut();
                        }
                    } catch (InterruptedException ignored) {
                        if (DEBUG)
                            Log.d(TAG, "readCharacteristic(): thread interrupted.");
                    } catch (Exception ex) {
                        mOnErrorObservable.dispatchFatalError(ex);
                    }
                }
            });
        }
    }

    protected final void writeCharacteristic(@NonNull final BluetoothGattCharacteristic characteristic, @NonNull final byte[] data) {
        synchronized (sGattExecutors) {
            getExecutorForGatt(mGatt).execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        synchronized (mGatt) {
                            characteristic.setValue(data);
                            mGatt.writeCharacteristic(characteristic);
                            long start_ms = System.currentTimeMillis();
                            mGatt.wait(3000);
                            if (System.currentTimeMillis() - start_ms >= 3000)
                                mOnErrorObservable.dispatchTimedOut();
                        }
                    } catch (InterruptedException ignored) {
                        if (DEBUG)
                            Log.d(TAG, "writeCharacteristic(): thread interrupted.");
                    } catch (Exception ex) {
                        mOnErrorObservable.dispatchFatalError(ex);
                    }
                }
            });
        }
    }

    protected final void setCharacteristicNotification(final BluetoothGattCharacteristic characteristic, final boolean enabled) {
        synchronized (sGattExecutors) {
            getExecutorForGatt(mGatt).execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        final int properties = characteristic.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                            if (DEBUG)
                                Log.d(TAG, "setCharacteristicNotification(): characteristic doesn't support NOTIFY.");
                            return;
                        }

                        mGatt.setCharacteristicNotification(characteristic, enabled);
                        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG);
                        if (descriptor != null) {
                            try {
                                synchronized (mGatt) {
                                    descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                                    mGatt.writeDescriptor(descriptor);

                                    long start_ms = System.currentTimeMillis();
                                    mGatt.wait(3000);
                                    if (System.currentTimeMillis() - start_ms >= 3000)
                                        mOnErrorObservable.dispatchTimedOut();
                                }
                            } catch (InterruptedException ignored) {
                                if (DEBUG)
                                    Log.d(TAG, "setCharacteristicNotification(): thread interrupted.");
                            }
                        } else {
                            Log.e(TAG, "setCharacteristicNotification(): characteristic doesn't have config descriptor! notification might not work.");
                        }
                    } catch (Exception ex) {
                        mOnErrorObservable.dispatchFatalError(ex);
                    }
                }
            });
        }
    }

    private void onOperationCompleted() {
        synchronized (mGatt) {
            mGatt.notifyAll();
        }
    }

    /**
     * called when descriptor read, be sure to call super <b>AFTER</b> you're done with the
     * {@param descriptor} contents (or clone it somewhere else), or the next command gets issued
     * and it's contents gets overwritten.
     * @param descriptor the {@link BluetoothGattDescriptor} read
     */
    @CallSuper
    public void onDescriptorRead(@NonNull BluetoothGattDescriptor descriptor) {
        onOperationCompleted();
    }

    /**
     * called when descriptor written, be sure to call super <b>AFTER</b> you're done with the
     * {@param descriptor} contents (or clone it somewhere else), or the next command gets issued
     * and it's contents gets overwritten.
     * @param descriptor the {@link BluetoothGattDescriptor} wrote
     */
    @CallSuper
    public void onDescriptorWrite(@NonNull BluetoothGattDescriptor descriptor) {
        onOperationCompleted();
    }

    /**
     * called when characteristic read, be sure to call super <b>AFTER</b> you're done with the
     * {@param characteristic} contents (or clone it somewhere else), or the next command gets
     * issued and it's contents gets overwritten.
     * @param characteristic the {@link BluetoothGattCharacteristic} read
     */
    @CallSuper
    public void onCharacteristicRead(@NonNull BluetoothGattCharacteristic characteristic) {
        onOperationCompleted();
    }

    /**
     * called when characteristic written, be sure to call super <b>AFTER</b> you're done with the
     * {@param characteristic} contents (or clone it somewhere else), or the next command gets
     * issued and it's contents gets overwritten.
     * @param characteristic the {@link BluetoothGattCharacteristic} wrote
     */
    @CallSuper
    public void onCharacteristicWrite(@NonNull BluetoothGattCharacteristic characteristic) {
        onOperationCompleted();
    }

    public void onCharacteristicChanged(@NonNull BluetoothGattCharacteristic characteristic) {
    }

    public boolean addOnErrorListener(OnErrorListener listener) {
        return mOnErrorObservable.registerObserver(listener);
    }

    public boolean removeOnErrorListener(OnErrorListener listener) {
        return mOnErrorObservable.unregisterObserver(listener);
    }

    private class OnErrorObservable extends WeakObservable<OnErrorListener> {
        void dispatchTimedOut() {
            dispatch(mHandler, new OnDispatchCallback<OnErrorListener>() {
                @Override
                public void onDispatch(OnErrorListener observer) {
                    observer.onTimedOut(BluetoothGattService.this);
                }
            });
        }

        void dispatchFatalError(final Exception ex) {
            dispatch(mHandler, new OnDispatchCallback<OnErrorListener>() {
                @Override
                public void onDispatch(OnErrorListener observer) {
               observer.onFatalError(ex);
                }
            });
        }
    }

    public interface OnErrorListener {
        @UiThread
        void onTimedOut(@NonNull BluetoothGattService service);

        @UiThread
        void onFatalError(@NonNull Exception ex);
    }
}
