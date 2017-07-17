# These are requred if you want to use `BluetoothDevice.ReflectedGattServiceFactory`.
# it match the incoming `UUID` with the `UUID_SERVICE` static field, and calls the constructor with
# a signature `<init>(BluetoothLeDevice, android.bluetooth.BluetoothGattService)`.
#
# tell proguard to keep them if you want to use it.
-keepclassmembernames,allowoptimization class * extends tw.idv.palatis.ble.services.BluetoothGattService {
    public static final java.util.UUID UUID_SERVICE;
}
# we want the constructor, and its name is not really important, so allow obfuscation here.
-keep,allowoptimization,allowobfuscation class * extends tw.idv.palatis.ble.services.BluetoothGattService {
    <init>(tw.idv.palatis.ble.BluetoothLeDevice, android.bluetooth.BluetoothGattService);
}
