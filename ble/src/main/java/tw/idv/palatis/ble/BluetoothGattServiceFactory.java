package tw.idv.palatis.ble;

import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.UUID;

import dalvik.system.DexFile;
import tw.idv.palatis.ble.services.BluetoothGattService;

/**
 * factory used for creating {@link tw.idv.palatis.ble.services.BluetoothGattService}
 */
public abstract class BluetoothGattServiceFactory {
    private static final String TAG = "BtGattServiceFactory";

    @NonNull
    public abstract BluetoothGattService newInstance(@NonNull BluetoothDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService);


    /**
     * the default factory, creates only {@link tw.idv.palatis.ble.services.BluetoothGattService}
     */
    static final BluetoothGattServiceFactory DEFAULT_SERVICE_FACTORY = new BluetoothGattServiceFactory() {
        @NonNull
        @Override
        public BluetoothGattService newInstance(@NonNull BluetoothDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService) {
            return new BluetoothGattService(device, nativeService);
        }
    };

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
    public static final class ReflectedGattServiceFactory extends BluetoothGattServiceFactory {
        private LinkedHashMap<UUID, Constructor<? extends BluetoothGattService>> mServiceConstructors = new LinkedHashMap<>();

        public ReflectedGattServiceFactory(Context context) {
            try {
                final DexFile dexFile = new DexFile(context.getPackageCodePath());
                final Enumeration<String> classNames = dexFile.entries();
                while (classNames.hasMoreElements()) {
                    try {
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
                                final Constructor<? extends BluetoothGattService> constructor = (Constructor<? extends BluetoothGattService>) klass.getDeclaredConstructor(BluetoothDevice.class, android.bluetooth.BluetoothGattService.class, Handler.class);

                                Log.v(TAG, "initialize(): Found constructor for BluetoothGattService: " + klass.getName());
                                mServiceConstructors.put(uuid, constructor);
                            }
                        } catch (NoSuchFieldException ex) {
                            Log.v(TAG, "initialize(): no UUID_SERVICE static field for " + klass.getName());
                        } catch (NoSuchMethodException ex) {
                            Log.v(TAG, "initialize(): no c-tor <init>(" + BluetoothGatt.class.getSimpleName() + ", " + BluetoothGattService.class.getSimpleName() + ", " + Handler.class.getSimpleName() + ") for " + klass.getName());
                        }
                    } catch (ClassNotFoundException | IllegalAccessException ex) {
                        Log.e(TAG, "ReflectedGattServiceFactory: " + ex.getMessage());
                    }
                }
            } catch (IOException ex) {
                throw new IllegalArgumentException("problem when finding sub-classes of BluetoothGattService in package", ex);
            }
        }

        @NonNull
        @Override
        public BluetoothGattService newInstance(@NonNull BluetoothDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService) {
            final Constructor<? extends BluetoothGattService> ctor = mServiceConstructors.get(nativeService.getUuid());
            if (ctor != null) {
                try {
                    return ctor.newInstance(device, nativeService, null);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                    throw new IllegalArgumentException("problem when creating an instance of BluetoothGattService " + nativeService.getUuid(), ex);
                }
            }
            return new BluetoothGattService(device, nativeService);
        }
    }
}
