package nl.das.terraria;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import nl.das.terraria.dialogs.NotificationDialog;
import nl.das.terraria.dialogs.WaitSpinner;
import nl.das.terraria.fragments.HelpFragment;
import nl.das.terraria.fragments.RulesetsFragment;
import nl.das.terraria.fragments.SettingsFragment;
import nl.das.terraria.fragments.StateFragment;
import nl.das.terraria.fragments.TimersFragment;
import nl.das.terraria.json.Device;
import nl.das.terraria.json.Properties;

public class TerrariaApp extends AppCompatActivity {

    public static final boolean MOCK = false;

    public static int nrOfTerraria;
    public static Properties[] configs;
    public static SharedPreferences sp;
    public static int curTabNr;
    public static String curIPAddress;
    public boolean connected[];

    private Toolbar mTopToolbar;
    private View mTabbar;
    private TextView[] mTabTitles;
    private Button btnContinue;
    private WaitSpinner wait;
    private NotificationDialog ndlg;
    @ColorInt private int colorPrimary;
    @ColorInt private int colorPrimaryDark;
    @ColorInt private int colorAccent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("Terraria", "TerrariaApp.onCreate() start");
        setContentView(R.layout.activity_main);

        // Get the configuration from the properties file
        java.util.Properties config = readConfig();
        mTabTitles = new TextView[nrOfTerraria];
        configs = new Properties[nrOfTerraria];
        connected = new boolean[nrOfTerraria];
        mTabbar = findViewById(R.id.tabbar);
        mTabbar.setVisibility(View.GONE);
        // Get the properties from the TCU's
        for (int i = 0; i < nrOfTerraria; i++) {
            int tabnr = i + 1;
            int r = getResources().getIdentifier("tab" + tabnr, "id", "nl.das.terraria2");
            mTabTitles[i] = findViewById(r);
            configs[i] = new Properties();
            configs[i].setTcu(config.getProperty("t" + tabnr +".tabtitle"));
            configs[i].setMockPostfix(config.getProperty("t" + tabnr +".mock_postfix"));
        }

        // This attribute refers to the hidden toolbar button
        btnContinue = findViewById(R.id.btn_continue);

        // Get the colors in our theme
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = getApplicationContext().getTheme();
        theme.resolveAttribute(R.attr.colorPrimary, typedValue, true);
        colorPrimary = typedValue.data;
        theme.resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
        colorPrimaryDark = typedValue.data;
        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
        colorAccent = typedValue.data;

        // Create the main toolbar
        mTopToolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(mTopToolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        Log.i("Terraria", "TerrariaApp.onCreate() end");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i("Terraria", "TerrariaApp.onStart() start");
        boolean ok = false;
        // Check if the SharedPreferences are there and filled
        sp = getApplicationContext().getSharedPreferences("TerrariaApp", 0);
        if (sp != null) {
            boolean ipAddressesFilled = true;
            for (int i = 0; i < nrOfTerraria; i++) {
                if (sp.getString("terrarium" + (i + 1) + "_ip_address", "x").equalsIgnoreCase("x")) {
                    ipAddressesFilled = false;
                }
            }
            ok = ipAddressesFilled;
        }
        // Check if the ip addresses of the TCU are reacheable
        String message = "";
        if (ok) {
            message = isConnected();
            ok = (message.length() == 0);
        }
        if (!ok) {
            ndlg = new NotificationDialog(this, "Error", message);
            ndlg.show();
            mTabbar.setVisibility(View.GONE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, new SettingsFragment())
                    .commit();
        } else {
            // Get the properties of the TCU
            getProperties();
        }
        super.onStart();
        Log.i("Terraria", "TerrariaApp.onStart() end");
    }
    /*
    This is the onClick method of the btn_coninue.
    It will be clicked by the background thread when the properties of the TCU are read.
     */
    public void go(View view) {
        Log.i("Terraria", "TerrariaApp.go() button clicked");
        mTabbar.setVisibility(View.VISIBLE);
        curTabNr = 1;
        mTabTitles[curTabNr - 1].setTextColor(Color.WHITE);
        curIPAddress = getApplicationContext().getSharedPreferences("TerrariaApp", 0).getString("terrarium1_ip_address", "");
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.layout, StateFragment.newInstance(curTabNr))
                .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_state_item) {
            mTabbar.setVisibility(View.VISIBLE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, StateFragment.newInstance(curTabNr))
                    .commit();
            return true;
        }
        if (id == R.id.menu_timers_item) {
            mTabbar.setVisibility(View.VISIBLE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, TimersFragment.newInstance(curTabNr))
                    .commit();
            return true;
        }
        if (id == R.id.menu_program_item) {
            mTabbar.setVisibility(View.VISIBLE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, RulesetsFragment.newInstance(curTabNr))
                    .commit();
            return true;
        }
        if (id == R.id.menu_config_item) {
            mTabbar.setVisibility(View.GONE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, new SettingsFragment())
                    .commit();
        }
        if (id == R.id.menu_help_item) {
            mTabbar.setVisibility(View.GONE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, new HelpFragment())
                    .commit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onTabSelect(View v){
        mTabTitles[curTabNr - 1].setTextColor(Color.BLACK);
        switch (v.getId()) {
            case R.id.tab1:
                curTabNr = 1;
                break;
            case R.id.tab2:
                curTabNr = 2;
                break;
            case R.id.tab3:
                curTabNr = 3;
                break;
            case R.id.tab4:
                curTabNr = 4;
                break;
            case R.id.tab5:
                curTabNr = 5;
                break;
        }
        mTabbar.setVisibility(View.VISIBLE);
        mTabTitles[curTabNr - 1].setTextColor(Color.WHITE);
        String key = "terrarium" + curTabNr + "_ip_address";
        curIPAddress = getApplicationContext().getSharedPreferences("TerrariaApp", 0).getString(key, "");
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.layout, StateFragment.newInstance(curTabNr))
                .commit();
    }

    private java.util.Properties readConfig() {
        java.util.Properties config = new java.util.Properties();
        AssetManager assetManager = getAssets();
        try {
            config.load(assetManager.open("config.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        nrOfTerraria = Integer.parseInt(config.getProperty("nrOfTerraria"));
        return config;
    }

    private void getProperties() {
        Log.i("Terraria", "TerrariaApp.getProperties() start");
        Context ctx = this;
        wait = new WaitSpinner(this);
        wait.start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Executed in separate thread
                Log.i("Terraria", "TerrariaApp.getProperties() thread start");
                for (int i = 0; i < nrOfTerraria; i++) {
                    String name = configs[i].getTcu();
                    String pfx = configs[i].getMockPostfix();
                    if (MOCK) {
                        Log.i("Terraria","Mock Property response");
                        try {
                            Gson gson = new Gson();
                            String response = new BufferedReader(
                                    new InputStreamReader(getResources().getAssets().open("properties_" + pfx + ".json")))
                                    .lines().collect(Collectors.joining("\n"));
                            configs[i] = gson.fromJson(response, Properties.class);
                        } catch (IOException e) {
                            Log.e("Terraria", e.getMessage());
                        }
                    } else {
                        try {
                            String ip = sp.getString("terrarium" + (i + 1) + "_ip_address", "x");
                            URL url = new URL("http://" + ip + "/properties");
                            Log.i("Terraria","TerrariaApp.getProperties() thread get properties from " + url);
                            HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
                            httpConnection.setRequestMethod("GET");
                            httpConnection.setRequestProperty("Accept", "application/json");
                            String json = new BufferedReader(new InputStreamReader(httpConnection.getInputStream(), StandardCharsets.UTF_8))
                                    .lines().collect(Collectors.joining("\n"));
                            configs[i] = new Gson().fromJson(json, Properties.class);
                            Log.i("Terraria","TerrariaApp.getProperties() thread got it:\n" + json);
                        } catch (Exception e) {
                            // Now tell the UI thread to show the dialog and end thread
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    wait.dismiss();
                                    ndlg = new NotificationDialog(ctx, "Error", "Terrarium Control Unit '" + name + "' is niet bekend op het netwerk met dit IP adres.");
                                    ndlg.show();
                                    return;
                                }
                            });
                        }
                    }
                    configs[i].setTcu(name);
                    configs[i].setMockPostfix(pfx);
                }
                Log.i("Terraria","TerrariaApp.getProperties() thread got all properties");
                // Now tell the UI thread to continue
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTabbar.setVisibility(View.VISIBLE);
                        for (int i = 0; i < nrOfTerraria; i++) {
                            mTabTitles[i].setVisibility(View.VISIBLE);
                            mTabTitles[i].setText(configs[i].getTcu());
                        }
                        btnContinue.callOnClick();
                        wait.dismiss();
                    }
                });
            }
        }).start();
        Log.i("Terraria", "TerrariaApp.getProperties() end");
    }

    public String isConnected() {
        Log.i("Terraria", "TerrariaApp.isConnected() start");
        final String[] message = {""};
        // Executed in separate thread
        Runtime runtime = Runtime.getRuntime();
        for (int i = 0; i < nrOfTerraria; i++) {
            String ip = sp.getString("terrarium" + (i + 1) + "_ip_address", "x");
            try {
                Log.i("Terraria", "Pinging " + ip + "...");
                Process ipProcess = runtime.exec("/system/bin/ping -c 1 -w 1 " + ip);
                int exitValue = ipProcess.waitFor();
                Log.i("Terraria", ip + " " + (exitValue == 0 ? "found" : "not found"));
                if (exitValue != 0) {
                    message[0] += "Terrarium Control Unit is niet bereikbaar op IP adres " + ip + "\n";
                }
                connected[i] = (exitValue == 0);
                ipProcess.destroy();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Log.i("Terraria", "TerrariaApp.isConnected() end");
        return message[0];
    }
}