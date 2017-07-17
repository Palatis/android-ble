package tw.idv.palatis.ble.services;

import android.bluetooth.BluetoothGattCharacteristic;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.UUID;

import tw.idv.palatis.ble.BluetoothLeDevice;
import tw.idv.palatis.ble.annotation.GattService;
import tw.idv.palatis.ble.database.Observable;

/**
 * A class that handles the Device Information Service from Bluetooth SIG
 * <p>
 * {@see https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.service.device_information.xml}
 */
@GattService
public class DeviceInformationService extends BluetoothGattService {
    private static final String TAG = "DevInfoService";

    // service UUID
    @SuppressWarnings("unused")
    public static final UUID UUID_SERVICE = new UUID(0x0000180a00001000L, 0x800000805f9b34fbL);

    // characteristics UUID
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_SYSTEM_ID = new UUID(0x00002a2300001000L, 0x800000805f9b34fbL);
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_MODEL_NUMBER = new UUID(0x00002a2400001000L, 0x800000805f9b34fbL);
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_SERIAL_NUMBER = new UUID(0x00002a2500001000L, 0x800000805f9b34fbL);
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_FIRMWARE_REVISION = new UUID(0x00002a2600001000L, 0x800000805f9b34fbL);
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_HARDWARE_REVISION = new UUID(0x00002a2700001000L, 0x800000805f9b34fbL);
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_SOFTWARE_REVISION = new UUID(0x00002a2800001000L, 0x800000805f9b34fbL);
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_MANUFACTURER_NAME = new UUID(0x00002a2900001000L, 0x800000805f9b34fbL);
    // dunno what's the format of this
    // public static final UUID UUID_IEEE_11073_20601_REG_CERT = new UUID(0x00002a2a00001000L, 0x800000805f9b34fbL);
    // lazy
    // public static final UUID UUID_PNP_ID = new UUID(0x00002a5000001000L, 0x800000805f9b34fbL);

    private final BluetoothGattCharacteristic mSystemIdCharacteristic;
    private final BluetoothGattCharacteristic mModelNumberCharacteristic;
    private final BluetoothGattCharacteristic mSerialNumberCharacteristic;
    private final BluetoothGattCharacteristic mFirmwareRevisionCharacteristic;
    private final BluetoothGattCharacteristic mHardwareRevisionCharacteristic;
    private final BluetoothGattCharacteristic mSoftwareRevisionCharacteristic;
    private final BluetoothGattCharacteristic mManufacturerNameCharacteristic;

    private final OnDeviceInformationChangedObservable mOnDeviceInformationChangedObservable = new OnDeviceInformationChangedObservable();

    public DeviceInformationService(@NonNull BluetoothLeDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService) {
        super(device, nativeService);
        mSystemIdCharacteristic = mNativeService.getCharacteristic(UUID_SYSTEM_ID);
        mModelNumberCharacteristic = mNativeService.getCharacteristic(UUID_MODEL_NUMBER);
        mSerialNumberCharacteristic = mNativeService.getCharacteristic(UUID_SERIAL_NUMBER);
        mFirmwareRevisionCharacteristic = mNativeService.getCharacteristic(UUID_FIRMWARE_REVISION);
        mHardwareRevisionCharacteristic = mNativeService.getCharacteristic(UUID_HARDWARE_REVISION);
        mSoftwareRevisionCharacteristic = mNativeService.getCharacteristic(UUID_SOFTWARE_REVISION);
        mManufacturerNameCharacteristic = mNativeService.getCharacteristic(UUID_MANUFACTURER_NAME);
    }

    @Override
    public void onCharacteristicRead(@NonNull BluetoothGattCharacteristic characteristic) {
        final UUID uuid = characteristic.getUuid();
        if (UUID_SYSTEM_ID.equals(uuid)) {
            mOnDeviceInformationChangedObservable.notifySystemIdChanged(characteristic.getValue().clone());
        } else if (UUID_MODEL_NUMBER.equals(uuid)) {
            mOnDeviceInformationChangedObservable.notifyModelNumberChanged(characteristic.getStringValue(0));
        } else if (UUID_SERIAL_NUMBER.equals(uuid)) {
            mOnDeviceInformationChangedObservable.notifySerialNumberChanged(characteristic.getStringValue(0));
        } else if (UUID_FIRMWARE_REVISION.equals(uuid)) {
            mOnDeviceInformationChangedObservable.notifyFirmwareRevisionChanged(characteristic.getStringValue(0));
        } else if (UUID_SOFTWARE_REVISION.equals(uuid)) {
            mOnDeviceInformationChangedObservable.notifySoftwareRevisionChanged(characteristic.getStringValue(0));
        } else if (UUID_HARDWARE_REVISION.equals(uuid)) {
            mOnDeviceInformationChangedObservable.notifyHardwareRevisionChanged(characteristic.getStringValue(0));
        } else if (UUID_MANUFACTURER_NAME.equals(uuid)) {
            mOnDeviceInformationChangedObservable.notifyManufacturerNameChanged(characteristic.getStringValue(0));
        } else {
            Log.v(TAG, "Unknown characteristic " + uuid);
        }

        super.onCharacteristicRead(characteristic);
    }

    public boolean getSystemId() {
        if (mSystemIdCharacteristic != null) {
            mDevice.readCharacteristic(this, mSystemIdCharacteristic);
            return true;
        }
        return false;
    }

    public boolean getModelNumber() {
        if (mModelNumberCharacteristic != null) {
            mDevice.readCharacteristic(this, mModelNumberCharacteristic);
            return true;
        }
        return false;
    }

    public boolean getSerialNumber() {
        if (mSerialNumberCharacteristic != null) {
            mDevice.readCharacteristic(this, mSerialNumberCharacteristic);
            return true;
        }
        return false;
    }

    public boolean getFirmwareRevision() {
        if (mFirmwareRevisionCharacteristic != null) {
            mDevice.readCharacteristic(this, mFirmwareRevisionCharacteristic);
            return true;
        }
        return false;
    }

    public boolean getHardwareRevision() {
        if (mHardwareRevisionCharacteristic != null) {
            mDevice.readCharacteristic(this, mHardwareRevisionCharacteristic);
            return true;
        }
        return false;
    }

    public boolean getSoftwareRevision() {
        if (mSoftwareRevisionCharacteristic != null) {
            mDevice.readCharacteristic(this, mSoftwareRevisionCharacteristic);
            return true;
        }
        return false;
    }

    public boolean getManufacturerName() {
        if (mManufacturerNameCharacteristic != null) {
            mDevice.readCharacteristic(this, mManufacturerNameCharacteristic);
            return true;
        }
        return false;
    }

    // <editor-fold desc="Observer, Observable, and Listeners">
    public void addOnDeviceInformationChangedListener(@NonNull OnDeviceInformationChangedListener listener) {
        mOnDeviceInformationChangedObservable.registerObserver(listener);
    }

    public void removeOnDeviceInformationChangedListener(@NonNull OnDeviceInformationChangedListener listener) {
        mOnDeviceInformationChangedObservable.unregisterObserver(listener);
    }

    public interface OnDeviceInformationChangedListener {
        void dispatchSystemIdChanged(@Nullable byte[] newSystemId);

        void dispatchModelNumberChanged(@Nullable String newModelNumber);

        void dispatchSerialNumberChanged(@Nullable String newSerialNumber);

        void dispatchFirmwareRevisionChanged(@Nullable String newFirmwareRevision);

        void dispatchSoftwareRevisionChanged(@Nullable String newSoftwareRevision);

        void dispatchHardwareRevisionChanged(@Nullable String newHardwareRevision);

        void dispatchManufacturerNameChanged(@Nullable String newManufacturerName);
    }

    private class OnDeviceInformationChangedObservable extends Observable<OnDeviceInformationChangedListener> {
        void notifySystemIdChanged(final byte[] newSystemId) {
            notifyChange(observer -> observer.dispatchSystemIdChanged(newSystemId));
        }

        void notifyModelNumberChanged(final String newModelNumber) {
            notifyChange(observer -> observer.dispatchModelNumberChanged(newModelNumber));
        }

        void notifySerialNumberChanged(final String newSerialNumber) {
            notifyChange(observer -> observer.dispatchSerialNumberChanged(newSerialNumber));
        }

        void notifyFirmwareRevisionChanged(final String newFirmwareRevision) {
            notifyChange(observer -> observer.dispatchFirmwareRevisionChanged(newFirmwareRevision));
        }

        void notifySoftwareRevisionChanged(final String newSoftwareRevision) {
            notifyChange(observer -> observer.dispatchSoftwareRevisionChanged(newSoftwareRevision));
        }

        void notifyHardwareRevisionChanged(final String newHardwareRevision) {
            notifyChange(observer -> observer.dispatchHardwareRevisionChanged(newHardwareRevision));
        }

        void notifyManufacturerNameChanged(final String newManufacturerName) {
            notifyChange(observer -> observer.dispatchManufacturerNameChanged(newManufacturerName));
        }
    }
    // </editor-fold>
}
