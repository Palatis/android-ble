package tw.idv.palatis.ble.sample;

import android.support.annotation.NonNull;

import java.util.UUID;

import tw.idv.palatis.ble.BluetoothDevice;
import tw.idv.palatis.ble.annotation.GattService;
import tw.idv.palatis.ble.services.BluetoothGattService;

@GattService
public class BlahService extends BluetoothGattService {
    public static final UUID UUID_SERVICE = new UUID(0x0, 0x0);

    public BlahService(@NonNull final BluetoothDevice device, @NonNull final android.bluetooth.BluetoothGattService nativeService) {
        super(device, nativeService);
    }
}
