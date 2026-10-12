package ph.salakot.pos;

import android.os.Bundle;
import android.view.WindowManager;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SalakotPrinterPlugin.class);   // native printing (network, Bluetooth, Android print)
        super.onCreate(savedInstanceState);
        // a POS screen should never go dark in the middle of service
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // never serve a stale cached page — always load the app bundled in this APK
        try {
            android.webkit.WebView wv = getBridge().getWebView();
            wv.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);
            wv.clearCache(true);
        } catch (Throwable ignored) { }
    }
}
