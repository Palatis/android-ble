package tw.idv.palatis.ble.services;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.Log;

import java.util.UUID;

import tw.idv.palatis.ble.database.WeakObservable;

/**
 * A class that handles the Device Information Service from Bluetooth SIG
 *
 * {@see https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.service.device_information.xml}
 */
public class DeviceInformationService extends BluetoothGattService {
    private static final String TAG = DeviceInformationService.class.getSimpleName();

    // service UUID
    @SuppressWarnings("unused")
    public static final UUID UUID_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");

    // characteristics UUID
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_SYSTEM_ID = UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb");
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_SERIAL_NUMBER = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb");
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_FIRMWARE_REVISION = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_MANUFACTURER_NAME = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");

    private final BluetoothGattCharacteristic mSystemIdCharacteristics;
    private final BluetoothGattCharacteristic mSerialNumberCharacteristics;
    private final BluetoothGattCharacteristic mFirmwareRevisionCharacteristics;
    private final BluetoothGattCharacteristic mManufacturerNameCharacteristics;

    private byte[] mSystemId = null;
    private String mSerialNumber = null;
    private String mFirmwareRevision = null;
    private String mManufacturerName = null;

    private final OnDeviceInformationChangedObservable mOnDeviceInformationChangedObservable = new OnDeviceInformationChangedObservable();

    DeviceInformationService(@NonNull BluetoothGatt gatt, @NonNull android.bluetooth.BluetoothGattService nativeService) {
        super(gatt, nativeService);

        mSystemIdCharacteristics = mNativeService.getCharacteristic(UUID_SYSTEM_ID);
        mSerialNumberCharacteristics = mNativeService.getCharacteristic(UUID_SERIAL_NUMBER);
        mFirmwareRevisionCharacteristics = mNativeService.getCharacteristic(UUID_FIRMWARE_REVISION);
        mManufacturerNameCharacteristics = mNativeService.getCharacteristic(UUID_MANUFACTURER_NAME);
    }

    @Override
    public void onCharacteristicRead(@NonNull BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicRead(characteristic);

        final UUID uuid = characteristic.getUuid();
        if (UUID_SYSTEM_ID.equals(uuid)) {
            mSystemId = characteristic.getValue();
            mOnDeviceInformationChangedObservable.dispatchSystemIdChanged(mSystemId);
        } else if (UUID_SERIAL_NUMBER.equals(uuid)) {
            mSerialNumber = characteristic.getStringValue(0);
            mOnDeviceInformationChangedObservable.dispatchSerialNumberChanged(mSerialNumber);
        } else if (UUID_FIRMWARE_REVISION.equals(uuid)) {
            mFirmwareRevision = characteristic.getStringValue(0);
            mOnDeviceInformationChangedObservable.dispatchFirmwareRevisionChanged(mFirmwareRevision);
        } else if (UUID_MANUFACTURER_NAME.equals(uuid)) {
            mManufacturerName = characteristic.getStringValue(0);
            mOnDeviceInformationChangedObservable.dispatchManufacturerNameChanged(mManufacturerName);
        } else {
            Log.e(TAG, "Unknown characteristic " + uuid);
        }
    }

    @Nullable
    public byte[] getSystemId() {
        if (mSystemId == null && mSystemIdCharacteristics != null)
            readCharacteristic(mSystemIdCharacteristics);
        return mSystemId;
    }

    @Nullable
    public String getSerialNumber() {
        if (mSerialNumber == null && mSerialNumberCharacteristics != null)
            readCharacteristic(mSerialNumberCharacteristics);
        return mSerialNumber;
    }

    @Nullable
    public String getFirmwareRevision() {
        if (mFirmwareRevision == null && mFirmwareRevisionCharacteristics != null)
            readCharacteristic(mFirmwareRevisionCharacteristics);
        return mFirmwareRevision;
    }

    @Nullable
    public String getManufacturerName() {
        if (mManufacturerName == null && mManufacturerNameCharacteristics != null)
            readCharacteristic(mManufacturerNameCharacteristics);
        return mManufacturerName;
    }

    public boolean addOnDeviceInformationChangedListener(@NonNull OnDeviceInformationChangedListener listener) {
        return mOnDeviceInformationChangedObservable.registerObserver(listener);
    }

    public boolean removeOnDeviceInformationChangedListener(@NonNull OnDeviceInformationChangedListener listener) {
        return mOnDeviceInformationChangedObservable.unregisterObserver(listener);
    }

    private class OnDeviceInformationChangedObservable extends WeakObservable<OnDeviceInformationChangedListener> {
        void dispatchSystemIdChanged(final byte[] newSystemId) {
            housekeeping();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // iterate backward, because observer may unregister itself.
                    for (int i = mObservers.size() - 1; i >= 0; --i) {
                        final OnDeviceInformationChangedListener observer = mObservers.get(i).get();
                        if (observer != null)
                            observer.onSystemIdChanged(newSystemId);
                    }
                }
            });
        }

        void dispatchSerialNumberChanged(final String newSerialNumber) {
            housekeeping();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // iterate backward, because observer may unregister itself.
                    for (int i = mObservers.size() - 1; i >= 0; --i) {
                        final OnDeviceInformationChangedListener observer = mObservers.get(i).get();
                        if (observer != null)
                            observer.onSerialNumberChanged(newSerialNumber);
                    }
                }
            });
        }

        void dispatchFirmwareRevisionChanged(final String newFirmwareRevision) {
            housekeeping();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // iterate backward, because observer may unregister itself.
                    for (int i = mObservers.size() - 1; i >= 0; --i) {
                        final OnDeviceInformationChangedListener observer = mObservers.get(i).get();
                        if (observer != null)
                            observer.onFirmwareRevisionChanged(newFirmwareRevision);
                    }
                }
            });
        }

        void dispatchManufacturerNameChanged(final String newManufacturerName) {
            housekeeping();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // iterate backward, because observer may unregister itself.
                    for (int i = mObservers.size() - 1; i >= 0; --i) {
                        final OnDeviceInformationChangedListener observer = mObservers.get(i).get();
                        if (observer != null)
                            observer.onManufacturerNameChanged(newManufacturerName);
                    }
                }
            });
        }
    }

    public interface OnDeviceInformationChangedListener {
        @UiThread
        void onSystemIdChanged(@Nullable byte[] newSystemId);

        @UiThread
        void onSerialNumberChanged(@Nullable String newSerialNumber);

        @UiThread
        void onFirmwareRevisionChanged(@Nullable String newRevisionNumber);

        @UiThread
        void onManufacturerNameChanged(@Nullable String newManufacturerName);
    }
}
