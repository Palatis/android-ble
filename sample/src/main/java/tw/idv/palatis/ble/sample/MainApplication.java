package tw.idv.palatis.ble.sample;

import android.app.Application;
import android.util.Log;

import tw.idv.palatis.ble.BluetoothGattServiceFactory;
import tw.idv.palatis.ble.blah.OtherGattServiceFactory;

public class MainApplication extends Application {
    private static final String TAG = "MainApplication";

    private BluetoothGattServiceFactory mFactory;

    @Override
    public void onCreate() {
        super.onCreate();

        mFactory = new BluetoothGattServiceFactory.MergedGattServiceFactory(
                new BluetoothGattServiceFactory.ReflectedGattServiceFactory(this),
                new AnnotatedGattServiceFactory(BuildConfig.DEBUG),
                new OtherGattServiceFactory(BuildConfig.DEBUG),
                new tw.idv.palatis.ble.services.AnnotatedGattServiceFactory(BuildConfig.DEBUG)
        );

        Log.d(TAG, "onCreate(): yeah!");
    }
}
