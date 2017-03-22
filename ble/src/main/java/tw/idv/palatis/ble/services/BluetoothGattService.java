package tw.idv.palatis.ble.services;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.UUID;

import dalvik.system.DexFile;
import tw.idv.palatis.ble.BluetoothDevice;

import static tw.idv.palatis.ble.BuildConfig.DEBUG;

/**
 * A delegate service to wrap around the native {@link android.bluetooth.BluetoothGattService}
 * <p>
 * Created by Palatis on 2017/3/15.
 */
public class BluetoothGattService {
    private static final String TAG = BluetoothGattService.class.getSimpleName();

    private static ArrayMap<UUID, Constructor<? extends BluetoothGattService>> sServiceConstructors = new ArrayMap<>();

    public static void initialize(Context context) {
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

    public static BluetoothGattService fromNativeService(@NonNull BluetoothDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService) {
        final Constructor<? extends BluetoothGattService> ctor = sServiceConstructors.get(nativeService.getUuid());
        if (ctor != null) {
            try {
                return ctor.newInstance(device, nativeService);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                throw new IllegalArgumentException("problem when creating an instance of BluetoothGattService " + nativeService.getUuid(), ex);
            }
        }
        return new BluetoothGattService(device, nativeService);
    }

    protected final Handler mHandler = new Handler(Looper.getMainLooper());

    @NonNull
    final android.bluetooth.BluetoothGattService mNativeService;
    @NonNull
    protected final BluetoothDevice mDevice;

    public BluetoothGattService(@NonNull BluetoothDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService) {
        mDevice = device;
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

    /**
     * a convinient function to return the bluetooth device associated with this service
     * @return the {@link BluetoothDevice}
     */
    @NonNull
    public BluetoothDevice getDevice() { return mDevice; }

    public void onDescriptorRead(@NonNull BluetoothGattDescriptor descriptor) {
    }

    public void onDescriptorWrite(@NonNull BluetoothGattDescriptor descriptor) {
    }

    public void onCharacteristicRead(@NonNull BluetoothGattCharacteristic characteristic) {
    }

    public void onCharacteristicWrite(@NonNull BluetoothGattCharacteristic characteristic) {
    }

    public void onCharacteristicChanged(@NonNull BluetoothGattCharacteristic characteristic) {
    }
}
