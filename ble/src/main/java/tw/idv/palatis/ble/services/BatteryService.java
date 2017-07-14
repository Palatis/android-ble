package tw.idv.palatis.ble.services;

import android.bluetooth.BluetoothGattCharacteristic;
import android.support.annotation.NonNull;

import java.util.UUID;

import tw.idv.palatis.ble.BluetoothDevice;
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

    public BatteryService(@NonNull BluetoothDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService) {
        super(device, nativeService);
        mBatteryLevelCharacteristic = nativeService.getCharacteristic(UUID_BATTERY_LEVEL);
    }

    @Override
    public void onCharacteristicRead(@NonNull BluetoothGattCharacteristic characteristic) {
        onCharacteristicChanged(characteristic);
        super.onCharacteristicRead(characteristic);
    }

    @Override
    public void onCharacteristicChanged(@NonNull BluetoothGattCharacteristic characteristic) {
        mOnBatteryLevelChangedObservable.notifyBatteryLevelChanged(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
    }

    public boolean getBatteryLevel() {
        if (mBatteryLevelCharacteristic != null) {
            mDevice.readCharacteristic(this, mBatteryLevelCharacteristic);
            return true;
        }
        return true;
    }

    public void addOnBatteryLevelChangedListener(OnBatteryLevelChangedListener listener) {
        mOnBatteryLevelChangedObservable.registerObserver(listener);
        mDevice.setCharacteristicNotification(this, mBatteryLevelCharacteristic, mOnBatteryLevelChangedObservable.numObservers() != 0);
    }

    public void removeOnBatteryLevelChangedListener(OnBatteryLevelChangedListener listener) {
        mOnBatteryLevelChangedObservable.unregisterObserver(listener);
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
