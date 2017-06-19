package tw.idv.palatis.ble.services;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.Log;

import java.util.UUID;

import tw.idv.palatis.ble.BluetoothDevice;
import tw.idv.palatis.ble.database.Observable;

/**
 * A class that handles the Battery Service from Bluetooth SIG
 * <p>
 * {@see https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.service.battery_service.xml}
 */
public class BatteryService extends BluetoothGattService {
    private static final String TAG = "BatteryService";

    // service UUID
    @SuppressWarnings("unused")
    public static final UUID UUID_SERVICE = new UUID(0x0000180f00001000L, 0x800000805f9b34fbL);

    // characteristic UUID
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_BATTERY_LEVEL = new UUID(0x00002a1900001000L, 0x800000805f9b34fbL);

    public static final int LEVEL_UNKNOWN = -1;
    public static final int LEVEL_UNAVAILABLE = -2;

    private BluetoothGattCharacteristic mBatteryLevelCharacteristic;

    private final OnBatteryLevelChangedObservable mOnBatteryLevelChangedObservable;

    public BatteryService(@NonNull BluetoothDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService, @Nullable Handler handler) {
        super(device, nativeService);

        mOnBatteryLevelChangedObservable = new OnBatteryLevelChangedObservable(handler == null ? new Handler(Looper.getMainLooper()) : handler);

        mBatteryLevelCharacteristic = nativeService.getCharacteristic(UUID_BATTERY_LEVEL);
        if (mBatteryLevelCharacteristic == null) {
            Log.e(TAG, "this BATTERY_SERVICE doesn't have BATTERY_LEVEL characteristics... = =");
        }
    }

    @Override
    public void onCharacteristicRead(@NonNull BluetoothGattCharacteristic characteristic) {
        onCharacteristicChanged(characteristic);
        super.onCharacteristicRead(characteristic);
    }

    @Override
    public void onCharacteristicChanged(@NonNull BluetoothGattCharacteristic characteristic) {
        mOnBatteryLevelChangedObservable.dispatchBatteryLevelChanged(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
    }

    public boolean getBatteryLevel() {
        if (mBatteryLevelCharacteristic != null) {
            mDevice.readCharacteristic(this, mBatteryLevelCharacteristic);
            return true;
        }
        return true;
    }

    public boolean addOnBatteryLevelChangedListener(OnBatteryLevelChangedListener listener) {
        boolean result = mOnBatteryLevelChangedObservable.registerObserver(listener);
        mDevice.setCharacteristicNotification(this, mBatteryLevelCharacteristic, mOnBatteryLevelChangedObservable.numObservers() != 0);
        return result;
    }

    public boolean removeOnBatteryLevelChangedListener(OnBatteryLevelChangedListener listener) {
        boolean result = mOnBatteryLevelChangedObservable.unregisterObserver(listener);
        mDevice.setCharacteristicNotification(this, mBatteryLevelCharacteristic, mOnBatteryLevelChangedObservable.numObservers() != 0);
        return result;
    }

    private class OnBatteryLevelChangedObservable extends Observable<OnBatteryLevelChangedListener> {
        public OnBatteryLevelChangedObservable(@Nullable Handler handler) {
            super(handler);
        }

        void dispatchBatteryLevelChanged(final int newLevel) {
            dispatch(new OnDispatchCallback<OnBatteryLevelChangedListener>() {
                @Override
                public void onDispatch(final OnBatteryLevelChangedListener observer) {
                    observer.onBatteryLevelChanged(newLevel);
                }
            });
        }
    }

    public interface OnBatteryLevelChangedListener {
        @UiThread
        void onBatteryLevelChanged(int newLevel);
    }
}
