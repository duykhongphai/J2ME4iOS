package javax.bluetooth;

import javax.microedition.io.Connection;

/** Bluetooth is not available on this platform: getLocalDevice() always fails. */
public class LocalDevice {
    private LocalDevice() {}

    public static LocalDevice getLocalDevice() throws BluetoothStateException {
        throw new BluetoothStateException("Bluetooth is not available");
    }

    public static String getProperty(String property) { return null; }

    public static boolean isPowerOn() { return false; }

    public DiscoveryAgent getDiscoveryAgent() { return new DiscoveryAgent(); }

    public String getFriendlyName() { return "J2ME4iOS"; }

    public DeviceClass getDeviceClass() { return new DeviceClass(0); }

    public boolean setDiscoverable(int mode) throws BluetoothStateException { throw new BluetoothStateException("Bluetooth is not available"); }

    public int getDiscoverable() { return DiscoveryAgent.NOT_DISCOVERABLE; }

    public String getBluetoothAddress() { return "000000000000"; }

    public ServiceRecord getRecord(Connection notifier) { throw new IllegalArgumentException(); }

    public void updateRecord(ServiceRecord srvRecord) throws ServiceRegistrationException { throw new ServiceRegistrationException(); }
}
