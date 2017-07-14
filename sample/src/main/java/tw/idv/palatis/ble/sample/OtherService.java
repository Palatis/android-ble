package tw.idv.palatis.ble.sample;

import android.support.annotation.NonNull;

import java.util.UUID;

import tw.idv.palatis.ble.BluetoothDevice;
import tw.idv.palatis.ble.annotation.GattService;
import tw.idv.palatis.ble.services.BluetoothGattService;

@GattService("tw.idv.palatis.ble.blah.OtherGattServiceFactory")
public class OtherService extends BluetoothGattService {
    public static final UUID UUID_SERVICE = new UUID(0x01, 0x02);

    public OtherService(@NonNull final BluetoothDevice device, @NonNull final android.bluetooth.BluetoothGattService nativeService) {
        super(device, nativeService);
    }
}
