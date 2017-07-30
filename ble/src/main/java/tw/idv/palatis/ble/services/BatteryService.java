package tw.idv.palatis.ble.services;

import android.bluetooth.BluetoothGattCharacteristic;
import android.support.annotation.NonNull;

import java.util.UUID;

import tw.idv.palatis.ble.BluetoothLeDevice;
import tw.idv.palatis.ble.annotation.GattService;
import tw.idv.palatis.ble.database.Observable;

/**
 * A class that handles the Battery Service from Bluetooth SIG
 * <p>
 * {@see https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.service.battery_service.xml}
 */
@GattService
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

    private final OnBatteryLevelChangedObservable mOnBatteryLevelChangedObservable = new OnBatteryLevelChangedObservable();

    public BatteryService(@NonNull BluetoothLeDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService) {
        super(device, nativeService);
        ensureCharacteristics(true);
    }

    @Override
    public void onCharacteristicRead(@NonNull BluetoothGattCharacteristic characteristic) {
        onCharacteristicChanged(characteristic);
    }

    @Override
    public void onCharacteristicChanged(@NonNull BluetoothGattCharacteristic characteristic) {
        mOnBatteryLevelChangedObservable.notifyBatteryLevelChanged(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
    }

    public boolean getBatteryLevel() {
        if (ensureCharacteristics(true)) {
            mDevice.readCharacteristic(this, mBatteryLevelCharacteristic);
            return true;
        }
        return true;
    }

    private boolean ensureCharacteristics(boolean enableNotification) {
        if (mBatteryLevelCharacteristic == null) {
            mBatteryLevelCharacteristic = mNativeService.getCharacteristic(UUID_BATTERY_LEVEL);
            if (enableNotification)
                mDevice.setCharacteristicNotification(this, mBatteryLevelCharacteristic, mOnBatteryLevelChangedObservable.numObservers() != 0);
        }
        return mBatteryLevelCharacteristic != null;
    }

    public void addOnBatteryLevelChangedListener(OnBatteryLevelChangedListener listener) {
        mOnBatteryLevelChangedObservable.registerObserver(listener);
        if (ensureCharacteristics(false))
            mDevice.setCharacteristicNotification(this, mBatteryLevelCharacteristic, mOnBatteryLevelChangedObservable.numObservers() != 0);
    }

    public void removeOnBatteryLevelChangedListener(OnBatteryLevelChangedListener listener) {
        mOnBatteryLevelChangedObservable.unregisterObserver(listener);
        if (ensureCharacteristics(false))
            mDevice.setCharacteristicNotification(this, mBatteryLevelCharacteristic, mOnBatteryLevelChangedObservable.numObservers() != 0);
    }

    private class OnBatteryLevelChangedObservable extends Observable<OnBatteryLevelChangedListener> {
        void notifyBatteryLevelChanged(final int newLevel) {
            notifyChange(observer -> observer.dispatchBatteryLevelChanged(newLevel));
        }
    }

    public interface OnBatteryLevelChangedListener {
        void dispatchBatteryLevelChanged(int newLevel);
    }
}
