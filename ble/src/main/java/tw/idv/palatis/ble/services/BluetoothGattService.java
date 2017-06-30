package tw.idv.palatis.ble.services;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.support.annotation.NonNull;

import java.util.UUID;

import tw.idv.palatis.ble.BluetoothDevice;

/**
 * A delegate service to wrap around the native {@link android.bluetooth.BluetoothGattService}
 * <p>
 * Created by Palatis on 2017/3/15.
 */
public class BluetoothGattService {
    private static final String TAG = "BluetoothGattService";

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
     *
     * @return the {@link BluetoothDevice}
     */
    @NonNull
    public BluetoothDevice getDevice() {
        return mDevice;
    }

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
