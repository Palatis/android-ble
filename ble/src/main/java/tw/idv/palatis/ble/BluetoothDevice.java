package tw.idv.palatis.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.CallSuper;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.Log;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import dalvik.system.DexFile;
import tw.idv.palatis.ble.database.WeakObservable;
import tw.idv.palatis.ble.services.BluetoothGattService;

import static java.lang.annotation.RetentionPolicy.SOURCE;
import static tw.idv.palatis.ble.BuildConfig.DEBUG;

public class BluetoothDevice {
    private static final String TAG = "BluetoothDevice";

    private static final UUID UUID_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /**
     * the default factory, creates only {@link tw.idv.palatis.ble.services.BluetoothGattService}
     */
    private static final BluetoothGattServiceFactory DEFAULT_SERVICE_FACTORY = new BluetoothGattServiceFactory() {
        @NonNull
        @Override
        public BluetoothGattService newInstance(@NonNull BluetoothDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService) {
            return new BluetoothGattService(device, nativeService);
        }
    };

    @Retention(SOURCE)
    @IntDef({
            BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING,
            BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_DISCONNECTED
    })
    public @interface ConnectionState {
    }

    private String mDeviceAddress;
    private android.bluetooth.BluetoothDevice mNativeDevice;
    private BluetoothGatt mGatt = null;
    private int mRssi = -127;
    @ConnectionState
    private int mConnectionState = BluetoothProfile.STATE_DISCONNECTED;
    private boolean mAutoConnect = false;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Executor mGattExecutor = Executors.newSingleThreadExecutor();
    private final OnErrorObservable mOnErrorObservable = new OnErrorObservable();
    private final OnConnectionStateChangedObservable mOnConnectionStateChangedObservable = new OnConnectionStateChangedObservable();
    private final OnServiceDiscoveredObservable mOnServiceDiscoveredObservable = new OnServiceDiscoveredObservable();

    private BluetoothGattServiceFactory mServiceFactory = DEFAULT_SERVICE_FACTORY;

    private final ArrayList<BluetoothGattService> mGattServices = new ArrayList<>();

    public BluetoothDevice(@NonNull String address) {
        mDeviceAddress = address;
        setNativeDevice(null);
    }

    public BluetoothDevice(@NonNull android.bluetooth.BluetoothDevice device) {
        mDeviceAddress = device.getAddress();
        setNativeDevice(device);
    }

    @CallSuper
    public void setNativeDevice(android.bluetooth.BluetoothDevice device) {
        mNativeDevice = device;
    }

    protected android.bluetooth.BluetoothDevice getDevice() {
        return mNativeDevice;
    }

    public void setServiceFactory(@Nullable BluetoothGattServiceFactory factory) {
        mServiceFactory = factory == null ? DEFAULT_SERVICE_FACTORY : factory;
    }

    /**
     * @return the name of the device
     */
    public String getName() {
        return mNativeDevice == null ? mDeviceAddress : mNativeDevice.getName();
    }

    /**
     * @return the bluetooth MAC address of the device
     */
    public String getAddress() {
        return mNativeDevice == null ? mDeviceAddress : mNativeDevice.getAddress();
    }

    public boolean isFake() {
        return mNativeDevice == null;
    }

    /**
     * @return the received signal strength in dBm. The valid range is [-127, 127].
     */
    public int getRssi() {
        return mRssi;
    }

    /**
     * @param rssi the new RSSI value
     */
    public void updateRssi(int rssi) {
        mRssi = rssi;
    }

    public int getBondState() {
        return mNativeDevice.getBondState();
    }

    public void createBond() {
        mNativeDevice.createBond();
    }

    /**
     * get the connection state, one of {@link BluetoothProfile#STATE_CONNECTED},
     * {@link BluetoothProfile#STATE_CONNECTING}, {@link BluetoothProfile#STATE_DISCONNECTING}, or
     * {@link BluetoothProfile#STATE_DISCONNECTED}.
     *
     * @return current connection state
     */
    @SuppressWarnings("WrongConstant")
    @ConnectionState
    public int getConnectionState() {
        return mConnectionState;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, @ConnectionState int newState) {
            mConnectionState = newState;

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onConnectionStateChange(): Failed! device = " + getAddress() + ", status = " + status + ", newState = " + newState);
                mOnErrorObservable.dispatchGattError(status);
                return;
            }

            Log.v(TAG, "onConnectionStateChanged(): device = " + getAddress() + ", " + status + " => " + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            } else {
                mGattServices.clear();
                if (!mAutoConnect)
                    mGatt = null;
            }

            mOnConnectionStateChangedObservable.dispatchConnectionStateChanged(newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onServiceDiscovered(): Failed! device = " + getAddress() + ", status = " + status);
                mOnErrorObservable.dispatchGattError(status);
                return;
            }

            final List<android.bluetooth.BluetoothGattService> services = gatt.getServices();
            for (final android.bluetooth.BluetoothGattService nativeService : services) {
                BluetoothGattService service = mServiceFactory.newInstance(BluetoothDevice.this, nativeService);
                mGattServices.add(service);
                mOnServiceDiscoveredObservable.dispatchServiceDiscovered(service);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onCharacteristicRead(): Failed! device = " + getAddress() +
                        ", service = " + characteristic.getService().getUuid() +
                        ", characteristic = " + characteristic.getUuid() +
                        ", status = " + status
                );
                mOnErrorObservable.dispatchGattError(status);
                return;
            }

            Log.v(TAG, "onCharacteristicRead(): device = " + getAddress() +
                    ", service = " + characteristic.getService().getUuid() +
                    ", characteristic = " + characteristic.getUuid() +
                    ", data = " + Arrays.toString(characteristic.getValue())
            );

            synchronized (BluetoothDevice.this) {
                BluetoothDevice.this.notify();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onCharacteristicWrite(): Failed! device = " + getAddress() +
                        ", service = " + characteristic.getService().getUuid() +
                        ", characteristic = " + characteristic.getUuid() +
                        ", status = " + status
                );
                mOnErrorObservable.dispatchGattError(status);
                return;
            }

            Log.v(TAG, "onCharacteristicWrite(): device = " + getAddress() +
                    ", service = " + characteristic.getService().getUuid() +
                    ", characteristic = " + characteristic.getUuid() +
                    ", data = " + Arrays.toString(characteristic.getValue())
            );

            synchronized (BluetoothDevice.this) {
                BluetoothDevice.this.notify();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            final BluetoothGattService service = getService(characteristic.getService().getUuid());
            if (service == null) {
                Log.e(TAG, "onCharacteristicChanged(): unregistered service! device = " + getAddress() +
                        ", service = " + characteristic.getService().getUuid() +
                        ", characteristic = " + characteristic.getUuid()
                );
                return;
            }

            Log.v(TAG, "onCharacteristicChanged(): device = " + getAddress() +
                    ", service = " + characteristic.getService().getUuid() +
                    ", characteristic = " + characteristic.getUuid() +
                    ", data = " + Arrays.toString(characteristic.getValue())
            );

            service.onCharacteristicChanged(characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onDescriptorRead(): Failed! device = " + getAddress() +
                        ", service = " + descriptor.getCharacteristic().getService().getUuid() +
                        ", characteristic = " + descriptor.getCharacteristic().getUuid() +
                        ", descriptor = " + descriptor.getUuid() +
                        ", status = " + status
                );
                mOnErrorObservable.dispatchGattError(status);
                return;
            }

            Log.v(TAG, "onDescriptorRead(): device = " + getAddress() +
                    ", service = " + descriptor.getCharacteristic().getService().getUuid() +
                    ", characteristic = " + descriptor.getCharacteristic().getUuid() +
                    ", descriptor = " + descriptor.getUuid() +
                    ", data = " + Arrays.toString(descriptor.getValue())
            );

            synchronized (BluetoothDevice.this) {
                BluetoothDevice.this.notify();
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onDescriptorWrite(): Failed! device = " + getAddress() +
                        ", service = " + descriptor.getCharacteristic().getService().getUuid() +
                        ", characteristic = " + descriptor.getCharacteristic().getUuid() +
                        ", descriptor = " + descriptor.getUuid() +
                        ", status = " + status
                );
                mOnErrorObservable.dispatchGattError(status);
                return;
            }

            Log.v(TAG, "onDescriptorWrite(): device = " + getAddress() +
                    ", service = " + descriptor.getCharacteristic().getService().getUuid() +
                    ", characteristic = " + descriptor.getCharacteristic().getUuid() +
                    ", descriptor = " + descriptor.getUuid() +
                    ", data = " + Arrays.toString(descriptor.getValue())
            );

            synchronized (BluetoothDevice.this) {
                BluetoothDevice.this.notify();
            }
        }

    };

    /**
     * connect to the device
     *
     * @param context     the application's {@link Context}
     * @param autoConnect auto re-connect when disconnected
     */
    public void connect(@NonNull Context context, boolean autoConnect) {
        if (mGatt != null && getConnectionState() != BluetoothProfile.STATE_DISCONNECTED)
            throw new IllegalStateException("device " + getName() + " - " + getAddress() + " not in disconnected state.");
        mGatt = mNativeDevice.connectGatt(context, mAutoConnect = autoConnect, mGattCallback);
        mConnectionState = BluetoothProfile.STATE_CONNECTING;
        mOnConnectionStateChangedObservable.dispatchConnectionStateChanged(mConnectionState);
    }

    public void disconnect() {
        if (mGatt == null)
            return;

        mGatt.disconnect();
        mGatt = null;
        mConnectionState = BluetoothProfile.STATE_DISCONNECTING;
        mOnConnectionStateChangedObservable.dispatchConnectionStateChanged(mConnectionState);
    }

    /**
     * get a service with specific service {@link UUID} and instance ID equals to 0.
     *
     * @param uuid the UUID of the service
     * @return the {@link BluetoothGattService}, {@code null} if not found.
     */
    @Nullable
    public BluetoothGattService getService(@NonNull UUID uuid) {
        for (final BluetoothGattService service : mGattServices)
            if (service.getUuid().equals(uuid))
                return service;
        return null;
    }

    @Nullable
    public BluetoothGattService getService(Class<? extends BluetoothGattService> klass) {
        for (final BluetoothGattService service : mGattServices)
            if (klass.isAssignableFrom(service.getClass()))
                return service;
        return null;
    }

    /**
     * get a service with specific service {@link UUID} and instance ID
     *
     * @param uuid       the {@link UUID} of the service
     * @param instanceId the instance ID
     * @return the {@link BluetoothGattService}, {@code null} if not found.
     */
    @Nullable
    public BluetoothGattService getService(@NonNull UUID uuid, int instanceId) {
        for (final BluetoothGattService service : mGattServices)
            if (service.getUuid().equals(uuid) && service.getInstanceId() == instanceId)
                return service;
        return null;
    }

    /**
     * get all services discovered specific {@link UUID}
     *
     * @param uuid the UUID of the service
     * @return an {@link ArrayList<BluetoothGattService>} containing all services found
     */
    @NonNull
    public List<BluetoothGattService> getServices(@NonNull UUID uuid) {
        final ArrayList<tw.idv.palatis.ble.services.BluetoothGattService> services = new ArrayList<>(mGattServices.size());
        for (final BluetoothGattService service : mGattServices)
            if (service.getUuid().equals(uuid))
                services.add(service);
        return services;
    }

    /**
     * return all services discovered for this device
     *
     * @return all services discovered for this device
     */
    @NonNull
    public List<tw.idv.palatis.ble.services.BluetoothGattService> getServices() {
        return new ArrayList<>(mGattServices);
    }

    public void readCharacteristic(final BluetoothGattService service, final BluetoothGattCharacteristic characteristic) {
        mGattExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (BluetoothDevice.this) {
                        mGatt.readCharacteristic(characteristic);

                        long start_ms = System.currentTimeMillis();
                        BluetoothDevice.this.wait(3000);
                        if (System.currentTimeMillis() - start_ms >= 3000) {
                            mOnErrorObservable.dispatchTimedOut(service);
                            return;
                        }

                        service.onCharacteristicRead(characteristic);
                    }
                } catch (InterruptedException ignored) {
                    if (DEBUG)
                        Log.d(TAG, "readCharacteristic(): thread interrupted.");
                } catch (Exception ex) {
                    mOnErrorObservable.dispatchFatalError(service, ex);
                }
            }
        });
    }

    public void writeCharacteristic(final BluetoothGattService service, final BluetoothGattCharacteristic characteristic, final byte[] data) {
        mGattExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (BluetoothDevice.this) {
                        characteristic.setValue(data);
                        mGatt.writeCharacteristic(characteristic);
                        long start_ms = System.currentTimeMillis();
                        BluetoothDevice.this.wait(3000);
                        if (System.currentTimeMillis() - start_ms >= 3000) {
                            mOnErrorObservable.dispatchTimedOut(service);
                            return;
                        }
                        service.onCharacteristicWrite(characteristic);
                    }
                } catch (InterruptedException ignored) {
                    if (DEBUG)
                        Log.d(TAG, "writeCharacteristic(): thread interrupted.");
                } catch (Exception ex) {
                    mOnErrorObservable.dispatchFatalError(service, ex);
                }
            }
        });
    }

    public void writeDescriptor(final BluetoothGattService service, final BluetoothGattDescriptor descriptor, final byte[] data) {
        mGattExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (BluetoothDevice.this) {
                        descriptor.setValue(data);
                        mGatt.writeDescriptor(descriptor);

                        long start_ms = System.currentTimeMillis();
                        BluetoothDevice.this.wait(3000);
                        if (System.currentTimeMillis() - start_ms >= 3000) {
                            mOnErrorObservable.dispatchTimedOut(service);
                            return;
                        }

                        service.onDescriptorWrite(descriptor);
                    }
                } catch (InterruptedException ignored) {
                    if (DEBUG)
                        Log.d(TAG, "setCharacteristicNotification(): thread interrupted.");
                } catch (Exception ex) {
                    mOnErrorObservable.dispatchFatalError(service, ex);
                }
            }
        });
    }

    public void setCharacteristicNotification(final BluetoothGattService service, final BluetoothGattCharacteristic characteristic, final boolean enabled) {
        if (DEBUG && (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
            Log.d(TAG, "setCharacteristicNotification(): characteristic doesn't support NOTIFY.");

        mGatt.setCharacteristicNotification(characteristic, enabled);
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG);
        if (descriptor == null) {
            Log.e(TAG, "setCharacteristicNotification(): characteristic doesn't have config descriptor! notification might not work.");
            return;
        }

        writeDescriptor(service, descriptor, enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
    }

    public boolean addOnErrorListener(@NonNull OnErrorListener listener) {
        return mOnErrorObservable.registerObserver(listener);
    }

    public boolean removeOnErrorListener(@NonNull OnErrorListener listener) {
        return mOnErrorObservable.unregisterObserver(listener);
    }

    public boolean addOnServiceDiscoveredListener(@NonNull OnServiceDiscoveredListener listener) {
        return mOnServiceDiscoveredObservable.registerObserver(listener);
    }

    public boolean removeOnServiceDiscoveredListener(@NonNull OnServiceDiscoveredListener listener) {
        return mOnServiceDiscoveredObservable.unregisterObserver(listener);
    }

    public boolean addOnConnectionStateChangedListener(@NonNull OnConnectionStateChangedListener listener) {
        return mOnConnectionStateChangedObservable.registerObserver(listener);
    }

    public boolean removeOnConnectionStateChangedListener(@NonNull OnConnectionStateChangedListener listener) {
        return mOnConnectionStateChangedObservable.unregisterObserver(listener);
    }

    private class OnServiceDiscoveredObservable extends WeakObservable<OnServiceDiscoveredListener> {
        void dispatchServiceDiscovered(@NonNull final BluetoothGattService service) {
            dispatch(mHandler, new OnDispatchCallback<OnServiceDiscoveredListener>() {
                @Override
                public void onDispatch(OnServiceDiscoveredListener observer) {
                    observer.onServiceDiscovered(service);
                }
            });
        }
    }

    private class OnConnectionStateChangedObservable extends WeakObservable<OnConnectionStateChangedListener> {
        void dispatchConnectionStateChanged(@ConnectionState final int newState) {
            dispatch(mHandler, new OnDispatchCallback<OnConnectionStateChangedListener>() {
                @Override
                public void onDispatch(OnConnectionStateChangedListener observer) {
                    observer.onConnectionStateChanged(newState);
                }
            });
        }
    }

    private class OnErrorObservable extends WeakObservable<OnErrorListener> {
        void dispatchGattError(final int status) {
            dispatch(mHandler, new OnDispatchCallback<OnErrorListener>() {
                @Override
                public void onDispatch(OnErrorListener observer) {
                    observer.onGattError(status);
                }
            });
        }

        void dispatchTimedOut(@NonNull final BluetoothGattService service) {
            dispatch(mHandler, new OnDispatchCallback<OnErrorListener>() {
                @Override
                public void onDispatch(OnErrorListener observer) {
                    observer.onTimedOut(service);
                }
            });
        }

        void dispatchFatalError(@NonNull final BluetoothGattService service, @NonNull final Throwable ex) {
            dispatch(mHandler, new OnDispatchCallback<OnErrorListener>() {
                @Override
                public void onDispatch(OnErrorListener observer) {
                    observer.onFatalError(service, ex);
                }
            });
        }
    }

    public interface OnErrorListener {
        @UiThread
        void onGattError(int status);

        @UiThread
        void onTimedOut(@NonNull BluetoothGattService service);

        @UiThread
        void onFatalError(@NonNull BluetoothGattService service, @NonNull Throwable ex);
    }

    public interface OnServiceDiscoveredListener {
        @UiThread
        void onServiceDiscovered(@NonNull tw.idv.palatis.ble.services.BluetoothGattService service);
    }

    public interface OnConnectionStateChangedListener {
        @UiThread
        void onConnectionStateChanged(@ConnectionState int newState);
    }

    /**
     * factory used for creating {@link tw.idv.palatis.ble.services.BluetoothGattService}
     */
    public interface BluetoothGattServiceFactory {
        @NonNull
        BluetoothGattService newInstance(@NonNull BluetoothDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService);
    }

    /**
     * <p>
     *   use reflection to find the sub-classes of {@link BluetoothGattService}, has to be
     *   initialized during {@link android.app.Application#onCreate()}.
     * </p>
     * <p>
     *   basic usage:
     *   <ol>
     *     <li>create an instance of {@link ReflectedGattServiceFactory}, and initialize it</li>
     *     <li>construct the {@link BluetoothDevice} from native {@link android.bluetooth.BluetoothDevice}</li>
     *     <li>set the factory with {@link BluetoothDevice#setServiceFactory(BluetoothGattServiceFactory)}</li>
     *     <li>now you can {@link BluetoothDevice#connect(Context, boolean)}</li>
     *   </ol>
     * </p>
     * <p>
     *   note:
     *   <ol>
     *     <li>all concrete subclasses of {@link BluetoothGattService} should have a public constructor with signature {@code <init>(tw.idv.palatis.ble.BluetoothDevice, android.bluetooth.BluetootGattService)}</li>
     *     <li>all concrete subclasses of {@link BluetoothGattService} should have a {@code UUID_SERVICE}</li>
     *     <li>tell proguard to {@code keep} the class, the constructor, and static field {@code UUID_SERVICE}.</li>
     *   </ol>
     * </p>
     */
    public static class ReflectedGattServiceFactory implements BluetoothGattServiceFactory {
        private LinkedHashMap<UUID, Constructor<? extends BluetoothGattService>> mServiceConstructors = new LinkedHashMap<>();

        public ReflectedGattServiceFactory(Context context) {
            try {
                final DexFile dexFile = new DexFile(context.getPackageCodePath());
                final Enumeration<String> classNames = dexFile.entries();
                while (classNames.hasMoreElements()) {
                    final String className = classNames.nextElement();

                    // skip framework components...
                    if (className.startsWith("android") || className.startsWith("java"))
                        continue;

                    final Class<?> klass = Class.forName(className);
                    try {
                        if (klass != null && !Modifier.isAbstract(klass.getModifiers()) && !klass.equals(BluetoothGattService.class) && BluetoothGattService.class.isAssignableFrom(klass)) {
                            final Field uuidField = klass.getDeclaredField("UUID_SERVICE");
                            UUID uuid = (UUID) uuidField.get(null);
                            @SuppressWarnings("unchecked")
                            final Constructor<? extends BluetoothGattService> constructor = (Constructor<? extends BluetoothGattService>) klass.getDeclaredConstructor(BluetoothDevice.class, android.bluetooth.BluetoothGattService.class);

                            if (DEBUG)
                                Log.d(TAG, "initialize(): Found constructor for BluetoothGattService: " + klass.getName());
                            mServiceConstructors.put(uuid, constructor);
                        }
                    } catch (NoSuchFieldException ex) {
                        if (DEBUG)
                            Log.d(TAG, "initialize(): no UUID_SERVICE static field for " + klass.getName());
                    } catch (NoSuchMethodException ex) {
                        if (DEBUG)
                            Log.d(TAG, "initialize(): no c-tor <init>(" + BluetoothGatt.class.getSimpleName() + ", " + BluetoothGattService.class.getSimpleName() + ") for " + klass.getName());
                    }
                }
            } catch (IOException | ClassNotFoundException | IllegalAccessException ex) {
                throw new IllegalArgumentException("problem when finding sub-classes of BluetoothGattService in package", ex);
            }
        }

        @NonNull
        @Override
        public BluetoothGattService newInstance(@NonNull BluetoothDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService) {
            final Constructor<? extends BluetoothGattService> ctor = mServiceConstructors.get(nativeService.getUuid());
            if (ctor != null) {
                try {
                    return ctor.newInstance(device, nativeService);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                    throw new IllegalArgumentException("problem when creating an instance of BluetoothGattService " + nativeService.getUuid(), ex);
                }
            }
            return new BluetoothGattService(device, nativeService);
        }
    }
}
