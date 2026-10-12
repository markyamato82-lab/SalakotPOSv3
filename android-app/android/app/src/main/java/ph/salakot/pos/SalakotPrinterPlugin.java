package ph.salakot.pos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.graphics.Typeface;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Native printing for SalakotPOS.
 * printRaw      - send ESC/POS bytes to a network printer (TCP, usually port 9100). Works with almost any LAN receipt printer.
 * printBluetooth- send ESC/POS bytes to a paired Bluetooth printer (classic SPP).
 * listBluetooth - paired Bluetooth devices.
 * scan          - find devices on this Wi-Fi with a printer port open.
 * networkInfo   - this tablet's Wi-Fi address, to pick the range to scan.
 * printHtml     - Android's own print dialog (any printer Android knows, PDF).
 * shareFile     - save or share an exported file (Excel, CSV) through Android's share sheet.
 */
@CapacitorPlugin(
    name = "SalakotPrinter",
    permissions = { @Permission(alias = "bluetooth", strings = { Manifest.permission.BLUETOOTH_CONNECT }) }
)
public class SalakotPrinterPlugin extends Plugin {

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final ExecutorService io = Executors.newCachedThreadPool();
    private WebView printView; // kept so the print job's WebView isn't garbage-collected mid-print

    // ---------------------------------------------------------------- network printers
    @PluginMethod
    public void printRaw(PluginCall call) {
        final String host = call.getString("host");
        final int port = call.getInt("port", 9100);
        final int timeout = call.getInt("timeout", 6000);
        final String data = call.getString("data");
        if (host == null || host.isEmpty() || data == null) { call.reject("Missing printer address or print data."); return; }
        io.execute(() -> {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), timeout);
                s.setSoTimeout(timeout);
                OutputStream out = s.getOutputStream();
                out.write(Base64.decode(data, Base64.DEFAULT));
                out.flush();
                // give the printer a moment to take the data before the connection closes
                try { Thread.sleep(250); } catch (InterruptedException ignored) { }
                s.shutdownOutput();
                call.resolve();
            } catch (Exception e) {
                call.reject(networkError(e, host, port));
            }
        });
    }

    private static String networkError(Exception e, String host, int port) {
        if (e instanceof SocketTimeoutException) return "The printer at " + host + " didn't answer. Check it's switched on and on the same Wi-Fi as this tablet.";
        if (e instanceof ConnectException) return "The printer at " + host + " refused the connection on port " + port + ". Check the port (usually 9100) and that nothing else is printing to it.";
        if (e instanceof NoRouteToHostException) return "This tablet can't reach " + host + ". Connect the tablet to the same Wi-Fi as the printer.";
        if (e instanceof UnknownHostException) return "\"" + host + "\" isn't a valid printer address.";
        return "Couldn't print to " + host + ": " + e.getMessage();
    }

    @PluginMethod
    public void networkInfo(PluginCall call) {
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm == null ? null : cm.getActiveNetwork();
            LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
            JSObject ret = new JSObject();
            if (lp != null) {
                for (LinkAddress la : lp.getLinkAddresses()) {
                    if (la.getAddress() instanceof Inet4Address) {
                        String ip = la.getAddress().getHostAddress();
                        ret.put("ip", ip);
                        ret.put("prefix", la.getPrefixLength());
                        ret.put("base", ip.substring(0, ip.lastIndexOf('.')));
                        break;
                    }
                }
            }
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Couldn't read this tablet's network: " + e.getMessage());
        }
    }

    @PluginMethod
    public void scan(PluginCall call) {
        final String base = call.getString("base", "");
        final int port = call.getInt("port", 9100);
        final int timeout = call.getInt("timeout", 500);
        if (!base.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) { call.reject("Enter a range like 192.168.1"); return; }
        io.execute(() -> {
            ExecutorService pool = Executors.newFixedThreadPool(48);
            try {
                List<Future<String>> jobs = new ArrayList<>();
                for (int i = 1; i <= 254; i++) {
                    final String ip = base + "." + i;
                    jobs.add(pool.submit(() -> {
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(ip, port), timeout);
                            return ip;
                        } catch (Exception e) {
                            return null;
                        }
                    }));
                }
                JSArray found = new JSArray();
                for (Future<String> f : jobs) {
                    String ip = f.get(timeout + 3000L, TimeUnit.MILLISECONDS);
                    if (ip != null) found.put(ip);
                }
                JSObject ret = new JSObject();
                ret.put("found", found);
                ret.put("port", port);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("The search stopped: " + e.getMessage());
            } finally {
                pool.shutdownNow();
            }
        });
    }

    // ---------------------------------------------------------------- Bluetooth printers
    private boolean needsBluetoothPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && getPermissionState("bluetooth") != PermissionState.GRANTED;
    }

    @PluginMethod
    public void listBluetooth(PluginCall call) {
        if (needsBluetoothPermission()) { requestPermissionForAlias("bluetooth", call, "bluetoothPermissionCallback"); return; }
        doListBluetooth(call);
    }

    @PluginMethod
    public void printBluetooth(PluginCall call) {
        if (needsBluetoothPermission()) { requestPermissionForAlias("bluetooth", call, "bluetoothPermissionCallback"); return; }
        doPrintBluetooth(call);
    }

    @PermissionCallback
    private void bluetoothPermissionCallback(PluginCall call) {
        if (needsBluetoothPermission()) {
            call.reject("SalakotPOS needs the Nearby devices permission to use Bluetooth printers. Allow it in Android Settings > Apps > SalakotPOS > Permissions.");
            return;
        }
        if ("printBluetooth".equals(call.getMethodName())) doPrintBluetooth(call); else doListBluetooth(call);
    }

    private BluetoothAdapter adapter() {
        BluetoothManager bm = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        return bm == null ? null : bm.getAdapter();
    }

    @SuppressLint("MissingPermission")
    private void doListBluetooth(PluginCall call) {
        BluetoothAdapter ad = adapter();
        if (ad == null) { call.reject("This tablet doesn't have Bluetooth."); return; }
        if (!ad.isEnabled()) { call.reject("Bluetooth is off. Turn it on, pair the printer in Android Settings, then try again."); return; }
        JSArray list = new JSArray();
        Set<BluetoothDevice> bonded = ad.getBondedDevices();
        if (bonded != null) {
            for (BluetoothDevice d : bonded) {
                JSObject o = new JSObject();
                o.put("name", d.getName() == null ? d.getAddress() : d.getName());
                o.put("address", d.getAddress());
                list.put(o);
            }
        }
        JSObject ret = new JSObject();
        ret.put("devices", list);
        call.resolve(ret);
    }

    @SuppressLint("MissingPermission")
    private void doPrintBluetooth(PluginCall call) {
        final String address = call.getString("address");
        final String data = call.getString("data");
        final BluetoothAdapter ad = adapter();
        if (ad == null) { call.reject("This tablet doesn't have Bluetooth."); return; }
        if (!ad.isEnabled()) { call.reject("Bluetooth is off. Turn it on to print."); return; }
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address.toUpperCase())) { call.reject("Choose a paired Bluetooth printer."); return; }
        if (data == null) { call.reject("Missing print data."); return; }
        io.execute(() -> {
            BluetoothSocket s = null;
            try {
                BluetoothDevice d = ad.getRemoteDevice(address.toUpperCase());
                ad.cancelDiscovery();
                s = d.createRfcommSocketToServiceRecord(SPP);
                s.connect();
                OutputStream out = s.getOutputStream();
                out.write(Base64.decode(data, Base64.DEFAULT));
                out.flush();
                try { Thread.sleep(600); } catch (InterruptedException ignored) { }
                call.resolve();
            } catch (Exception e) {
                call.reject("Couldn't print over Bluetooth. Check the printer is on, paired, and not connected to another device. (" + e.getMessage() + ")");
            } finally {
                if (s != null) { try { s.close(); } catch (Exception ignored) { } }
            }
        });
    }

    // ---------------------------------------------------------------- Android print dialog
    @PluginMethod
    public void printHtml(PluginCall call) {
        final String html = call.getString("html");
        final String name = call.getString("name", "SalakotPOS");
        if (html == null) { call.reject("Nothing to print."); return; }
        getActivity().runOnUiThread(() -> {
            try {
                WebView wv = new WebView(getActivity());
                wv.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        PrintManager pm = (PrintManager) getActivity().getSystemService(Context.PRINT_SERVICE);
                        PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(name);
                        pm.print(name, adapter, new PrintAttributes.Builder().build());
                    }
                });
                wv.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null);
                printView = wv;
                call.resolve();
            } catch (Exception e) {
                call.reject("Couldn't open the print dialog: " + e.getMessage());
            }
        });
    }

    // ---------------------------------------------------------------- exports
    @PluginMethod
    public void shareFile(PluginCall call) {
        final String name = call.getString("name", "export.csv").replaceAll("[\\\\/:*?\"<>|]", "_");
        final String mime = call.getString("mime", "application/octet-stream");
        final String data = call.getString("data");
        if (data == null) { call.reject("Nothing to save."); return; }
        try {
            File dir = new File(getContext().getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) { call.reject("Couldn't prepare the file."); return; }
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(Base64.decode(data, Base64.DEFAULT)); }
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mime);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, name);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().startActivity(Intent.createChooser(send, "Save or share " + name));
            call.resolve();
        } catch (Exception e) {
            call.reject("Couldn't share the file: " + e.getMessage());
        }
    }


    // ---------------------------------------------------------------- iMin built-in printer (D1, Swan, Falcon, M2…)
    // Uses iMin's own SDK via reflection, so the app still builds and runs on non-iMin tablets (the library is simply absent there).
    private Object iminUtils;      // IminPrintUtils instance
    private boolean iminTried, iminOk;

    private synchronized boolean iminInit(int paper) {
        if (iminOk && iminUtils != null) { iminSetPage(paper); return true; }
        if (iminTried && !iminOk) return false;
        iminTried = true;
        try {
            Class<?> cls = Class.forName("com.imin.printerlib.IminPrintUtils");
            iminUtils = cls.getMethod("getInstance", Context.class).invoke(null, getContext());
            Class<?> connType = Class.forName("com.imin.printerlib.IminPrintUtils$PrintConnectType");
            Object spi = java.lang.Enum.valueOf((Class) connType, "SPI");
            cls.getMethod("initPrinter", connType).invoke(iminUtils, spi);
            iminOk = true;
            iminSetPage(paper);
            return true;
        } catch (Throwable t) {
            iminOk = false; iminUtils = null;
            return false;
        }
    }
    private void iminCall(String m, Class<?>[] sig, Object... args) {
        try { iminUtils.getClass().getMethod(m, sig).invoke(iminUtils, args); } catch (Throwable ignored) { }
    }
    private void iminSetPage(int paper) { iminCall("setPageFormat", new Class[]{int.class}, paper == 58 ? 1 : 0); }

    public boolean hasImin() { return iminInit(80); }

    @PluginMethod
    public void iminAvailable(PluginCall call) {
        JSObject r = new JSObject(); r.put("available", iminInit(80)); call.resolve(r);
    }

    @PluginMethod
    public void printImin(PluginCall call) {
        final int paper = call.getInt("paper", 58);
        final boolean cut = Boolean.TRUE.equals(call.getBoolean("cut", true));
        final boolean drawer = Boolean.TRUE.equals(call.getBoolean("drawer", false));
        final JSArray lines = call.getArray("lines");
        if (lines == null) { call.reject("Nothing to print."); return; }
        io.execute(() -> {
            if (!iminInit(paper)) { call.reject("This tablet's built-in iMin printer isn't available."); return; }
            try {
                int width = paper == 58 ? 384 : 576;
                iminCall("setTextWidth", new Class[]{int.class}, width);
                for (int i = 0; i < lines.length(); i++) {
                    org.json.JSONObject l = lines.getJSONObject(i);
                    boolean rule = l.optBoolean("rule", false);
                    boolean bold = l.optBoolean("b", false);
                    boolean dw = l.optBoolean("dw", false), dh = l.optBoolean("dh", false);
                    String a = l.optString("a", "left");
                    String t = rule ? repeat('-', paper == 58 ? 32 : 48) : l.optString("t", "");
                    iminCall("setAlignment", new Class[]{int.class}, a.equals("center") ? 1 : a.equals("right") ? 2 : 0);
                    iminCall("setTextStyle", new Class[]{int.class}, bold ? 1 : 0);
                    int size = 26; if (dw || dh) size = 48;
                    iminCall("setTextSize", new Class[]{int.class}, size);
                    iminCall("printText", new Class[]{String.class, int.class}, t + "\n", 0);
                }
                iminCall("setTextStyle", new Class[]{int.class}, 0);
                iminCall("setTextSize", new Class[]{int.class}, 26);
                iminCall("printAndFeedPaper", new Class[]{int.class}, 60);
                if (cut) iminCall("partialCut", new Class[]{});
                if (drawer) iminOpenDrawer();
                call.resolve();
            } catch (Throwable t) {
                call.reject("The built-in printer couldn't print: " + t.getMessage());
            }
        });
    }
    private void iminOpenDrawer() {
        // iMin cash drawer: try the SDK's own method names across SDK revisions
        for (String m : new String[]{"openDrawer", "setDrawerCallback"}) {
            try { iminUtils.getClass().getMethod(m).invoke(iminUtils); return; } catch (Throwable ignored) { }
        }
    }
    private static String repeat(char c, int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) b.append(c); return b.toString(); }

    @Override
    protected void handleOnDestroy() {
        io.shutdownNow();
    }
}
