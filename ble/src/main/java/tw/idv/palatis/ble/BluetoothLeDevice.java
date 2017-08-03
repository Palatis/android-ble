package tw.idv.palatis.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.CallSuper;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tw.idv.palatis.ble.database.HandlerObserver;
import tw.idv.palatis.ble.database.Observable;
import tw.idv.palatis.ble.services.BluetoothGattService;

import static java.lang.annotation.RetentionPolicy.SOURCE;
import static tw.idv.palatis.ble.BluetoothGattServiceFactory.DEFAULT_SERVICE_FACTORY;

public class BluetoothLeDevice {
    private static final String TAG = "BluetoothLeDevice";

    private static final UUID UUID_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static BluetoothManager sBtMgr;

    public static void initialize(Context context) {
        sBtMgr = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    @Retention(SOURCE)
    @IntDef({
            BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING,
            BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_DISCONNECTED
    })
    public @interface ConnectionState {
    }

    public static long deviceIdFromAddress(String bdAddress) {
        return Long.parseLong(bdAddress.replace(":", ""), 16);
    }

    public static String addressFromDeviceId(long bdAddress) {
        return String.format(
                Locale.getDefault(),
                "%02x:%02x:%02x:%02x:%02x:%02x",
                bdAddress >> 40 & 0x00ff,
                bdAddress >> 32 & 0x00ff,
                bdAddress >> 24 & 0x00ff,
                bdAddress >> 16 & 0x00ff,
                bdAddress >> 8 & 0x00ff,
                bdAddress & 0x00ff
        );
    }

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private final long mId;
    private final String mDeviceAddress;
    private BluetoothDevice mNativeDevice;
    private BluetoothGatt mGatt = null;
    private int mRssi = -127;
    private final Object mGattLock = new Object();

    private ExecutorService mGattExecutor = Executors.newSingleThreadExecutor();

    private final OnErrorObservable mOnErrorObservable = new OnErrorObservable();
    private final OnConnectionStateChangedObservable mOnConnectionStateChangedObservable = new OnConnectionStateChangedObservable();
    private final OnServiceDiscoveredObservable mOnServiceDiscoveredObservable = new OnServiceDiscoveredObservable();

    private BluetoothGattServiceFactory mServiceFactory = DEFAULT_SERVICE_FACTORY;

    private final ArrayList<BluetoothGattService> mGattServices = new ArrayList<>();

    public BluetoothLeDevice(@NonNull String address) {
        mDeviceAddress = address;
        mId = deviceIdFromAddress(getAddress());
        setNativeDevice(null);
    }

    public BluetoothLeDevice(@NonNull BluetoothDevice device) {
        mDeviceAddress = device.getAddress();
        mId = deviceIdFromAddress(getAddress());
        setNativeDevice(device);
    }

    @CallSuper
    public void setNativeDevice(BluetoothDevice device) {
        mNativeDevice = device;
        mOnConnectionStateChangedObservable.notifyConnectionStateChanged(getConnectionState());
        mOnConnectionStateChangedObservable.notifyAvailabilityChanged(isAvailable());
        Log.d(TAG, "setNativeDevice(): " + getAddress() + " => " + device);
    }

    protected BluetoothDevice getNativeDevice() {
        if (mGatt != null)
            return mGatt.getDevice();
        return mNativeDevice;
    }

    public void setServiceFactory(@Nullable BluetoothGattServiceFactory factory) {
        mServiceFactory = factory == null ? DEFAULT_SERVICE_FACTORY : factory;
    }

    /**
     * @return {@link #getAddress()} expressed in long
     */
    public long getId() {
        return mId;
    }

    /**
     * @return the name of the device
     */
    @Nullable
    public String getName() {
        final BluetoothDevice device = getNativeDevice();
        return device == null ? null : device.getName();
    }

    /**
     * @return the bluetooth MAC address of the device
     */
    @NonNull
    public String getAddress() {
        return getNativeDevice() == null ? mDeviceAddress : getNativeDevice().getAddress();
    }

    public boolean isAvailable() {
        return getNativeDevice() != null;
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
        return getNativeDevice().getBondState();
    }

    public void createBond() {
        getNativeDevice().createBond();
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
        if (mGatt == null)
            return BluetoothProfile.STATE_DISCONNECTED;
        return sBtMgr.getConnectionState(mGatt.getDevice(), BluetoothProfile.GATT);
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, @ConnectionState int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onConnectionStateChange(): Failed! device = " + getAddress() + ", status = " + status + ", newState = " + newState);
                mOnErrorObservable.dispatchGattError(status);

                gatt.close();
                if (mGatt == gatt) {
                    if (mGattExecutor != null) {
                        mGattExecutor.shutdownNow();
                        mGattExecutor = null;
                    }
                    mGattServices.clear();
                    mGatt = null;
                }
            }

            Log.v(TAG, "onConnectionStateChanged(): device = " + getAddress() + ", " + status + " => " + newState);

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    if (mGatt != null && mGatt != gatt) {
                        gatt.close();
                        return;
                    }
                    mHandler.removeCallbacks(mDisconnectRunnable);
                    mHandler.removeCallbacks(mCloseRunnable);
                    mHandler.removeCallbacks(mConnectRunnable);
                    mGattExecutor = Executors.newSingleThreadExecutor();
                    mGattExecutor.execute(() -> {
                        try {
                            Thread.sleep(500);
                            if (mGatt != null && sBtMgr.getConnectionState(getNativeDevice(), BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED)
                                mGatt.discoverServices();
                        } catch (InterruptedException ex) {
                            Log.v(TAG, "onConnectionStateChanged(): gat.discoverServices() interrupted.");
                        }
                    });
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.d(TAG, "onConnectionStateChanged(): gatt conn closed.");
                    close();
                    mGatt = null;
                case BluetoothProfile.STATE_DISCONNECTING:
                    if (mGattExecutor != null) {
                        mGattExecutor.shutdownNow();
                        mGattExecutor = null;
                    }
                    mGattServices.clear();
                case BluetoothProfile.STATE_CONNECTING:
                    break;
                default:
                    Log.d(TAG, "unknown state " + newState);
            }

            mOnConnectionStateChangedObservable.notifyConnectionStateChanged(newState);
            mOnConnectionStateChangedObservable.notifyAvailabilityChanged(isAvailable());
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onServiceDiscovered(): Failed! device = " + getAddress() + ", status = " + status);
                mOnErrorObservable.dispatchGattError(status);
                return;
            }

            synchronized (this) {
                final List<android.bluetooth.BluetoothGattService> services = gatt.getServices();
                for (final android.bluetooth.BluetoothGattService nativeService : services) {
                    BluetoothGattService service = mServiceFactory.newInstance(BluetoothLeDevice.this, nativeService);
                    if (service == null)
                        service = new BluetoothGattService(BluetoothLeDevice.this, nativeService);
                    mGattServices.add(service);
                }
            }

            mHandler.postDelayed(() -> {
                for (final BluetoothGattService service : mGattServices)
                    mOnServiceDiscoveredObservable.notifyServiceDiscovered(service);
            }, 250);
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

            synchronized (mGattLock) {
                mGattLock.notify();
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

            synchronized (mGattLock) {
                mGattLock.notify();
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

            synchronized (mGattLock) {
                mGattLock.notify();
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

            synchronized (mGattLock) {
                mGattLock.notify();
            }
        }
    };

    /**
     * connect to the device
     *
     * @param context the application's {@link Context}
     */
    public void connect(@NonNull Context context) {
        mHandler.removeCallbacks(mConnectRunnable);
        mHandler.post(mConnectRunnable.setContext(context));
    }

    private final ConnectRunnable mConnectRunnable = new ConnectRunnable();

    private final class ConnectRunnable implements Runnable {
        private Context mContext;

        public synchronized ConnectRunnable setContext(Context context) {
            mContext = context.getApplicationContext();
            return this;
        }

        @Override
        public synchronized void run() {
            if (mContext == null)
                return;

            if (mGatt != null) {
                if (getConnectionState() == BluetoothProfile.STATE_DISCONNECTED) {
                    close();
                    mHandler.postDelayed(this, 1500);
                    return;
                } else {
                    mOnConnectionStateChangedObservable.notifyConnectionStateChanged(getConnectionState());
                    return;
                }
            }
            if (mNativeDevice == null) {
                mOnConnectionStateChangedObservable.notifyConnectionStateChanged(getConnectionState());
                return;
            }
            Log.d(TAG, "connect(): " + getAddress() + " issued.");
            mGatt = mNativeDevice.connectGatt(mContext, false, mGattCallback);
        }
    }

    public void disconnect() {
        mHandler.removeCallbacks(mDisconnectRunnable);
        mHandler.post(mDisconnectRunnable);
    }

    private final Runnable mDisconnectRunnable = () -> {
        synchronized (this) {
            if (mGatt == null)
                return;
            Log.d(TAG, "disconnect(): " + getAddress() + " issued.");
            mGatt.disconnect();
            mOnConnectionStateChangedObservable.notifyConnectionStateChanged(getConnectionState());
        }
    };

    public void close() {
        mHandler.removeCallbacks(mCloseRunnable);
        mHandler.postDelayed(mCloseRunnable, 1000);
    }

    private final Runnable mCloseRunnable = () -> {
        synchronized (this) {
            if (mGatt == null)
                return;
            Log.d(TAG, "close(): gatt connection closed.");
            mGatt.close();
            mGatt = null;
            mOnConnectionStateChangedObservable.notifyConnectionStateChanged(getConnectionState());
        }
    };

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
        if (getConnectionState() != BluetoothProfile.STATE_CONNECTED)
            return;

        try {
            mGattExecutor.execute(() -> {
                try {
                    synchronized (mGattLock) {
                        mGatt.readCharacteristic(characteristic);

                        long start_ms = System.currentTimeMillis();
                        mGattLock.wait(3000);
                        if (System.currentTimeMillis() - start_ms >= 3000) {
                            mOnErrorObservable.dispatchTimedOut(service);
                            return;
                        }

                        service.onCharacteristicRead(characteristic);
                    }
                } catch (InterruptedException ignored) {
                    Log.v(TAG, "readCharacteristic(): thread interrupted.");
                } catch (Exception ex) {
                    mOnErrorObservable.dispatchFatalError(service, ex);
                }
            });
        } catch (NullPointerException ex) {
            mOnErrorObservable.dispatchFatalError(service, ex);
        }
    }

    public void writeCharacteristic(final BluetoothGattService service, final BluetoothGattCharacteristic characteristic, final byte[] data) {
        if (getConnectionState() != BluetoothProfile.STATE_CONNECTED)
            return;

        try {
            mGattExecutor.execute(() -> {
                try {
                    synchronized (mGattLock) {
                        characteristic.setValue(data);
                        mGatt.writeCharacteristic(characteristic);
                        long start_ms = System.currentTimeMillis();
                        mGattLock.wait(3000);
                        if (System.currentTimeMillis() - start_ms >= 3000) {
                            mOnErrorObservable.dispatchTimedOut(service);
                            return;
                        }
                        service.onCharacteristicWrite(characteristic);
                    }
                } catch (InterruptedException ignored) {
                    Log.v(TAG, "writeCharacteristic(): thread interrupted.");
                } catch (Exception ex) {
                    mOnErrorObservable.dispatchFatalError(service, ex);
                }
            });
        } catch (NullPointerException ex) {
            mOnErrorObservable.dispatchFatalError(service, ex);
        }
    }

    public void readDescriptor(final BluetoothGattService service, final BluetoothGattDescriptor descriptor) {
        if (getConnectionState() != BluetoothProfile.STATE_CONNECTED)
            return;

        try {
            mGattExecutor.execute(() -> {
                try {
                    synchronized (mGattLock) {
                        mGatt.readDescriptor(descriptor);

                        long start_ms = System.currentTimeMillis();
                        mGattLock.wait(3000);
                        if (System.currentTimeMillis() - start_ms >= 3000) {
                            mOnErrorObservable.dispatchTimedOut(service);
                            return;
                        }

                        service.onDescriptorRead(descriptor);
                    }
                } catch (InterruptedException ignored) {
                    Log.v(TAG, "readCharacteristic(): thread interrupted.");
                } catch (Exception ex) {
                    mOnErrorObservable.dispatchFatalError(service, ex);
                }
            });
        } catch (NullPointerException ex) {
            mOnErrorObservable.dispatchFatalError(service, ex);
        }
    }

    public void writeDescriptor(final BluetoothGattService service, final BluetoothGattDescriptor descriptor, final byte[] data) {
        if (getConnectionState() != BluetoothProfile.STATE_CONNECTED)
            return;

        try {
            mGattExecutor.execute(() -> {
                try {
                    synchronized (mGattLock) {
                        descriptor.setValue(data);
                        mGatt.writeDescriptor(descriptor);

                        long start_ms = System.currentTimeMillis();
                        mGattLock.wait(3000);
                        if (System.currentTimeMillis() - start_ms >= 3000) {
                            mOnErrorObservable.dispatchTimedOut(service);
                            return;
                        }

                        service.onDescriptorWrite(descriptor);
                    }
                } catch (InterruptedException ignored) {
                    Log.v(TAG, "setCharacteristicNotification(): thread interrupted.");
                } catch (Exception ex) {
                    mOnErrorObservable.dispatchFatalError(service, ex);
                }
            });
        } catch (NullPointerException ex) {
            mOnErrorObservable.dispatchFatalError(service, ex);
        }
    }

    public void setCharacteristicNotification(final BluetoothGattService service, final BluetoothGattCharacteristic characteristic, final boolean enabled) {
        if (getConnectionState() != BluetoothProfile.STATE_CONNECTED)
            return;

        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
            Log.v(TAG, "setCharacteristicNotification(): characteristic doesn't support NOTIFY.");

        mGatt.setCharacteristicNotification(characteristic, enabled);
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG);
        if (descriptor == null) {
            Log.e(TAG, "setCharacteristicNotification(): characteristic doesn't have config descriptor! notification might not work.");
            return;
        }

        writeDescriptor(service, descriptor, enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
    }

    // <editor-fold desc="Observer, Observable, and Listeners">
    public void addOnErrorListener(@NonNull OnErrorListener listener) {
        mOnErrorObservable.registerObserver(listener);
    }

    public void removeOnErrorListener(@NonNull OnErrorListener listener) {
        mOnErrorObservable.unregisterObserver(listener);
    }

    public void addOnServiceDiscoveredListener(@NonNull OnServiceDiscoveredListener listener) {
        mOnServiceDiscoveredObservable.registerObserver(listener);
    }

    public void removeOnServiceDiscoveredListener(@NonNull OnServiceDiscoveredListener listener) {
        mOnServiceDiscoveredObservable.unregisterObserver(listener);
    }

    public void addOnConnectionStateChangedListener(@NonNull OnConnectionStateChangedListener listener) {
        mOnConnectionStateChangedObservable.registerObserver(listener);
    }

    public void removeOnConnectionStateChangedListener(@NonNull OnConnectionStateChangedListener listener) {
        mOnConnectionStateChangedObservable.unregisterObserver(listener);
    }

    public abstract static class HandlerOnErrorListener
            extends HandlerObserver<HandlerOnErrorListener>
            implements OnErrorListener {
        public HandlerOnErrorListener(final Handler handler) {
            super(handler);
        }

        protected abstract void onGattError(@NonNull final BluetoothLeDevice device, final int status);

        protected abstract void onTimedOut(@NonNull final BluetoothLeDevice device, @NonNull final BluetoothGattService service);

        protected abstract void onFatalError(@NonNull final BluetoothLeDevice device, @NonNull final BluetoothGattService service, @Nullable final Throwable ex);

        @Override
        public void dispatchGattError(@NonNull final BluetoothLeDevice device, final int status) {
            dispatchChange(observer -> observer.onGattError(device, status));
        }

        @Override
        public void dispatchTimedOut(@NonNull final BluetoothLeDevice device, @NonNull final BluetoothGattService service) {
            dispatchChange(observer -> observer.onTimedOut(device, service));
        }

        @Override
        public void dispatchFatalError(@NonNull final BluetoothLeDevice device, @NonNull final BluetoothGattService service, @Nullable final Throwable ex) {
            dispatchChange(observer -> observer.onFatalError(device, service, ex));
        }
    }

    public interface OnErrorListener {
        void dispatchGattError(@NonNull BluetoothLeDevice device, int status);

        void dispatchTimedOut(@NonNull BluetoothLeDevice device, @NonNull BluetoothGattService service);

        void dispatchFatalError(@NonNull BluetoothLeDevice device, @NonNull BluetoothGattService service, @NonNull Throwable ex);
    }

    private class OnErrorObservable extends Observable<OnErrorListener> {
        void dispatchGattError(final int status) {
            notifyChange(observer -> observer.dispatchGattError(BluetoothLeDevice.this, status));
        }

        void dispatchTimedOut(@NonNull final BluetoothGattService service) {
            notifyChange(observer -> observer.dispatchTimedOut(BluetoothLeDevice.this, service));
        }

        void dispatchFatalError(@NonNull final BluetoothGattService service, @NonNull final Throwable ex) {
            notifyChange(observer -> observer.dispatchFatalError(BluetoothLeDevice.this, service, ex));
        }
    }

    public abstract static class HandlerOnServiceDiscoveredListener
            extends HandlerObserver<HandlerOnServiceDiscoveredListener>
            implements OnServiceDiscoveredListener {
        public HandlerOnServiceDiscoveredListener(final Handler handler) {
            super(handler);
        }

        protected abstract void onServiceDiscovered(@NonNull final BluetoothLeDevice device, @NonNull final BluetoothGattService service);

        @Override
        public final void dispatchServiceDiscovered(@NonNull final BluetoothLeDevice device, @NonNull final BluetoothGattService service) {
            dispatchChange(observer -> observer.onServiceDiscovered(device, service));
        }
    }

    public interface OnServiceDiscoveredListener {
        void dispatchServiceDiscovered(@NonNull BluetoothLeDevice device, @NonNull tw.idv.palatis.ble.services.BluetoothGattService service);
    }

    private class OnServiceDiscoveredObservable extends Observable<OnServiceDiscoveredListener> {
        void notifyServiceDiscovered(@NonNull final BluetoothGattService service) {
            notifyChange(observer -> observer.dispatchServiceDiscovered(BluetoothLeDevice.this, service));
        }
    }

    public abstract static class HandlerOnConnectionStateChangedListener
            extends HandlerObserver<HandlerOnConnectionStateChangedListener>
            implements OnConnectionStateChangedListener {
        public HandlerOnConnectionStateChangedListener(final Handler handler) {
            super(handler);
        }

        protected abstract void onAvailabilityChanged(@NonNull final BluetoothLeDevice device, final boolean available);

        protected abstract void onConnectionStateChanged(final BluetoothLeDevice device, final int newState);

        @Override
        public void dispatchAvailabilityChanged(@NonNull final BluetoothLeDevice device, final boolean available) {
            dispatchChange(observer -> observer.onAvailabilityChanged(device, available));
        }

        @Override
        public void dispatchConnectionStateChanged(@NonNull final BluetoothLeDevice device, final int newState) {
            dispatchChange(observer -> observer.onConnectionStateChanged(device, newState));
        }
    }

    public interface OnConnectionStateChangedListener {
        void dispatchAvailabilityChanged(@NonNull BluetoothLeDevice device, boolean available);

        void dispatchConnectionStateChanged(@NonNull BluetoothLeDevice device, @ConnectionState int newState);
    }

    private class OnConnectionStateChangedObservable extends Observable<OnConnectionStateChangedListener> {
        void notifyConnectionStateChanged(@ConnectionState final int newState) {
            notifyChange(observer -> observer.dispatchConnectionStateChanged(BluetoothLeDevice.this, newState));
        }

        void notifyAvailabilityChanged(final boolean available) {
            notifyChange(observer -> observer.dispatchAvailabilityChanged(BluetoothLeDevice.this, available));
        }
    }
    // </editor-fold>

    protected static String stringFromConnectionState(@ConnectionState int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "CONNECTED";
            case BluetoothProfile.STATE_CONNECTING:
                return "CONNECTING";
            case BluetoothProfile.STATE_DISCONNECTED:
                return "DISCONNECTED";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "DISCONNECTING";
        }
        return "UNKNOWN";
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + hashCode() + " (" + getName() + " [" + getAddress() + "], native = " + getNativeDevice() + ", " + stringFromConnectionState(getConnectionState()) + ", rssi = " + getRssi() + ", gatt = " + mGatt + ")";
    }
}
