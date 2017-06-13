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
 * A class that handles the Device Information Service from Bluetooth SIG
 * <p>
 * {@see https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.service.device_information.xml}
 */
public class DeviceInformationService extends BluetoothGattService {
    private static final String TAG = "DevInfoService";

    // service UUID
    @SuppressWarnings("unused")
    public static final UUID UUID_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");

    // characteristics UUID
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_SYSTEM_ID = UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb");
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_MODEL_NUMBER = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_SERIAL_NUMBER = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb");
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_FIRMWARE_REVISION = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    @SuppressWarnings("WeakerAccess")
    public static final UUID UUID_MANUFACTURER_NAME = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");

    private final BluetoothGattCharacteristic mSystemIdCharacteristic;
    private final BluetoothGattCharacteristic mModelNumberCharacteristic;
    private final BluetoothGattCharacteristic mSerialNumberCharacteristic;
    private final BluetoothGattCharacteristic mFirmwareRevisionCharacteristic;
    private final BluetoothGattCharacteristic mManufacturerNameCharacteristic;

    private byte[] mSystemId = null;
    private String mModelNumber = null;
    private String mSerialNumber = null;
    private String mFirmwareRevision = null;
    private String mManufacturerName = null;

    private final OnDeviceInformationChangedObservable mOnDeviceInformationChangedObservable;

    public DeviceInformationService(@NonNull BluetoothDevice device, @NonNull android.bluetooth.BluetoothGattService nativeService, @Nullable Handler handler) {
        super(device, nativeService);

        mOnDeviceInformationChangedObservable = new OnDeviceInformationChangedObservable(handler == null ? new Handler(Looper.getMainLooper()) : handler);

        mSystemIdCharacteristic = mNativeService.getCharacteristic(UUID_SYSTEM_ID);
        mModelNumberCharacteristic = mNativeService.getCharacteristic(UUID_MODEL_NUMBER);
        mSerialNumberCharacteristic = mNativeService.getCharacteristic(UUID_SERIAL_NUMBER);
        mFirmwareRevisionCharacteristic = mNativeService.getCharacteristic(UUID_FIRMWARE_REVISION);
        mManufacturerNameCharacteristic = mNativeService.getCharacteristic(UUID_MANUFACTURER_NAME);
    }

    @Override
    public void onCharacteristicRead(@NonNull BluetoothGattCharacteristic characteristic) {
        final UUID uuid = characteristic.getUuid();
        if (UUID_SYSTEM_ID.equals(uuid)) {
            mSystemId = characteristic.getValue();
            mOnDeviceInformationChangedObservable.dispatchSystemIdChanged(mSystemId);
        } else if (UUID_MODEL_NUMBER.equals(uuid)) {
            mModelNumber = characteristic.getStringValue(0);
            mOnDeviceInformationChangedObservable.dispatchModelNumberChanged(mModelNumber);
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

        super.onCharacteristicRead(characteristic);
    }

    @Nullable
    public byte[] getSystemId() {
        if (mSystemIdCharacteristic != null)
            mDevice.readCharacteristic(this, mSystemIdCharacteristic);
        return mSystemId;
    }

    @Nullable
    public String getModelNumber() {
        if (mModelNumberCharacteristic != null)
            mDevice.readCharacteristic(this, mModelNumberCharacteristic);
        return mModelNumber;
    }

    @Nullable
    public String getSerialNumber() {
        if (mSerialNumberCharacteristic != null)
            mDevice.readCharacteristic(this, mSerialNumberCharacteristic);
        return mSerialNumber;
    }

    @Nullable
    public String getFirmwareRevision() {
        if (mFirmwareRevisionCharacteristic != null)
            mDevice.readCharacteristic(this, mFirmwareRevisionCharacteristic);
        return mFirmwareRevision;
    }

    @Nullable
    public String getManufacturerName() {
        if (mManufacturerNameCharacteristic != null)
            mDevice.readCharacteristic(this, mManufacturerNameCharacteristic);
        return mManufacturerName;
    }

    public boolean addOnDeviceInformationChangedListener(@NonNull OnDeviceInformationChangedListener listener) {
        return mOnDeviceInformationChangedObservable.registerObserver(listener);
    }

    public boolean removeOnDeviceInformationChangedListener(@NonNull OnDeviceInformationChangedListener listener) {
        return mOnDeviceInformationChangedObservable.unregisterObserver(listener);
    }

    private class OnDeviceInformationChangedObservable extends Observable<OnDeviceInformationChangedListener> {
        public OnDeviceInformationChangedObservable(@Nullable Handler handler) {
            super(handler);
        }

        void dispatchSystemIdChanged(final byte[] newSystemId) {
            dispatch(new OnDispatchCallback<OnDeviceInformationChangedListener>() {
                @Override
                public void onDispatch(final OnDeviceInformationChangedListener observer) {
                    observer.onSystemIdChanged(newSystemId);
                }
            });
        }

        void dispatchModelNumberChanged(final String newModelNumber) {
            dispatch(new OnDispatchCallback<OnDeviceInformationChangedListener>() {
                @Override
                public void onDispatch(final OnDeviceInformationChangedListener observer) {
                    observer.onModelNumberChanged(newModelNumber);
                }
            });
        }

        void dispatchSerialNumberChanged(final String newSerialNumber) {
            dispatch(new OnDispatchCallback<OnDeviceInformationChangedListener>() {
                @Override
                public void onDispatch(final OnDeviceInformationChangedListener observer) {
                    observer.onSerialNumberChanged(newSerialNumber);
                }
            });
        }

        void dispatchFirmwareRevisionChanged(final String newFirmwareRevision) {
            dispatch(new OnDispatchCallback<OnDeviceInformationChangedListener>() {
                @Override
                public void onDispatch(final OnDeviceInformationChangedListener observer) {
                    observer.onFirmwareRevisionChanged(newFirmwareRevision);
                }
            });
        }

        void dispatchManufacturerNameChanged(final String newManufacturerName) {
            dispatch(new OnDispatchCallback<OnDeviceInformationChangedListener>() {
                @Override
                public void onDispatch(final OnDeviceInformationChangedListener observer) {
                    observer.onManufacturerNameChanged(newManufacturerName);
                }
            });
        }
    }

    public interface OnDeviceInformationChangedListener {
        @UiThread
        void onSystemIdChanged(@Nullable byte[] newSystemId);

        @UiThread
        void onModelNumberChanged(@Nullable String newModelNumber);

        @UiThread
        void onSerialNumberChanged(@Nullable String newSerialNumber);

        @UiThread
        void onFirmwareRevisionChanged(@Nullable String newRevisionNumber);

        @UiThread
        void onManufacturerNameChanged(@Nullable String newManufacturerName);
    }
}
