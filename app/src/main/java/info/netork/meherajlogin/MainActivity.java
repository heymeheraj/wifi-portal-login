package info.netork.meherajlogin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import info.netork.meherajlogin.NetworkCheckService; // Ensure this import statement is correct

public class MainActivity extends AppCompatActivity {
    WebView captivePortal;
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "VoucherPrefs";
    private static final String VOUCHER_CODE_KEY = "voucherCode";
    private static final int REQUEST_CODE = 100;

    @SuppressLint({"SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE);
            }
        }

        // Start the network check service
        Intent serviceIntent = new Intent(this, NetworkCheckService.class);
        startService(serviceIntent);

        checkWifiNetworkSignInRequired();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, you can proceed with the notification
            } else {
                // Permission denied
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // Check if the Wi-Fi network requires sign-in
    private void checkWifiNetworkSignInRequired() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        connectivityManager.registerNetworkCallback(networkRequest, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
                if (networkCapabilities != null && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
                    Log.d(TAG, "Wi-Fi network requires sign-in");
                    connectivityManager.unregisterNetworkCallback(this);
                    runOnUiThread(() -> loadWebView());
                } else {
                    Log.d(TAG, "Wi-Fi network does not require sign-in");
                    connectivityManager.unregisterNetworkCallback(this);
                    runOnUiThread(() -> showNoSignInRequiredDialog());
                }
            }

            @Override
            public void onLost(Network network) {
                Log.d(TAG, "Network lost");
                connectivityManager.unregisterNetworkCallback(this);
                runOnUiThread(() -> showNoSignInRequiredDialog());
            }
        });
    }

    // Load the WebView for the captive portal
    private void loadWebView() {
        captivePortal = findViewById(R.id.captivePortal);
        captivePortal.getSettings().setJavaScriptEnabled(true);
        captivePortal.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page finished loading: " + url);
                if (url.startsWith("https://aps1-omada-essential-controller.tplinkcloud.com/static/portal/entry.html")) {
                    promptForVoucherCode(view);
                }
            }
        });

        // Enable cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(captivePortal, true);

        // Load initial URL
        captivePortal.loadUrl("http://connectivitycheck.gstatic.com/");
    }

    // Show dialog if no sign-in is required
    private void showNoSignInRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No Sign-In Required")
                .setMessage("You are already connected to the Meheraj Network.")
                .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }

    // Method to prompt for the voucher code
    private void promptForVoucherCode(final WebView view) {
        final EditText input = new EditText(this);
        // Load last entered voucher code
        String lastVoucherCode = getLastVoucherCode();
        if (!lastVoucherCode.isEmpty()) {
            input.setText(lastVoucherCode);
        }
        new AlertDialog.Builder(this)
                .setTitle("Enter Voucher Code")
                .setView(input)
                .setPositiveButton("Submit", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String voucherCode = input.getText().toString();
                        fillVoucherCode(view, voucherCode);
                        saveLastVoucherCode(voucherCode);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        finish();
                    }
                })
                .show();
    }

    // Method to fill in the voucher code and set focus on the input field
    private void fillVoucherCode(WebView view, String voucherCode) {
        Log.d(TAG, "Attempting to fill voucher code");
        view.loadUrl("javascript:(function() {" +
                "var input = document.querySelector('input[placeholder=\"Voucher Code\"]');" +
                "if (input) {" +
                "input.value = '" + voucherCode + "';" +
                "input.focus();" +  // Set focus on the input field
                "input.setSelectionRange(input.value.length, input.value.length);" + // Move cursor to end
                "input.dispatchEvent(new Event('input', { bubbles: true }));" +  // Trigger the input event to ensure visibility
                "console.log('Voucher code entered and focused');" +
                "} else {" +
                "console.log('Voucher input not found');" +
                "}" +
                "})()");
    }

    // Method to save the last entered voucher code
    private void saveLastVoucherCode(String voucherCode) {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(VOUCHER_CODE_KEY, voucherCode);
        editor.apply();
    }

    // Method to get the last entered voucher code
    private String getLastVoucherCode() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return settings.getString(VOUCHER_CODE_KEY, "");
    }
}
