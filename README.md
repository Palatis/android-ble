# Android BLE Extended

This is an BLE extension library that wraps Android's [BluetootGattService](https://developer.android.com/reference/android/bluetooth/BluetoothGattService) into something easier to use.

The problem with Android's bluetooth:
1. simply calling `BluetoothGattCharacteristic.setNotificationEnabled()` is not enough, you need to write the `BluetoothGattDescriptor` and enable the notifications.
2. each operation (`readCharacteristic()`, `writeCharacteristic()`, `readDescriptor()`, `writeDescriptor()`) has to wait till previous operation finish before you can issue the next one.
3. the profile / service / characteristic / descriptor support in Android BLE stack is pretty limitted, this library provides an object oriented way to manipulate them.

## Basic Usage

Register the listeners to `BluetoothDevice` for `onConnectionStateChanged()` and `onServiceDiscovered()`, and register listeners to `BluetoothGattService` for specific characteristic update.

## Custom Service / Characteristics

Extends `BluetoothGattService`:
1. create an `UUID_SERVICE` matching your service UUID
2. make listeners and dispatchers for READ and NOTIFY events...
3. add `@Keep` to sub-classes of `BluetoothGattService` and it's corresponding `UUID_SERVICE` so proguard don't strip them.

That's it!

## Using with Gradle

1. `git submodule add https://github.com/Palatis/android-ble.git` to add this repo to your project
2. modify `settings.gradle` to include the project with correct path to the library module directory
   ```
   include ':app' // your app
   include ':android-ble' // this repo
   project(':android-ble').projectDir = file('./android-ble/ble') // path to library module
   ```
3. add the library module to your app's dependency by modifying `app/build.gradle`
   ```
   ...
   dependencies {
       ...
       compile project(path: ':android-ble')
       ...
   }
   ...
   ```