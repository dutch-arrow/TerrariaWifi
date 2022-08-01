package nl.das.terraria;

import static nl.das.terraria.R.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

import nl.das.terraria.dialogs.NotificationDialog;
import nl.das.terraria.dialogs.WaitSpinner;
import nl.das.terraria.fragments.HelpFragment;
import nl.das.terraria.fragments.HistoryFragment;
import nl.das.terraria.fragments.RulesetsFragment;
import nl.das.terraria.fragments.SettingsFragment;
import nl.das.terraria.fragments.StateFragment;
import nl.das.terraria.fragments.TimersFragment;
import nl.das.terraria.json.Properties;

public class TerrariaApp extends AppCompatActivity {

    public static final boolean[] MOCK = {false, false, false};

    public static int nrOfTerraria;
    public static Properties[] configs;
    public static SharedPreferences sp;
    public static int curTabNr;
    public static String curIPAddress;
    public boolean[] connected;

    private View mTabbar;
    private TextView[] mTabTitles;
    private Button btnContinue;
    private WaitSpinner wait;
    private NotificationDialog ndlg;
    private Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_main);

        // Get the configuration from the properties file
        java.util.Properties config = readConfig();
        mTabTitles = new TextView[nrOfTerraria];
        configs = new Properties[nrOfTerraria];
        connected = new boolean[nrOfTerraria];
        mTabbar = findViewById(id.tabbar);
        mTabbar.setVisibility(View.GONE);
        // Get the properties from the TCU's
        for (int i = 0; i < nrOfTerraria; i++) {
            int tabnr = i + 1;
            int r = getResources().getIdentifier("tab" + tabnr, "id", "nl.das.terrariawifi");
            mTabTitles[i] = findViewById(r);
            configs[i] = new Properties();
            configs[i].setTcuName(config.getProperty("t" + tabnr +".tabtitle"));
            configs[i].setMockPostfix(config.getProperty("t" + tabnr +".mock_postfix"));
        }

        // This attribute refers to the hidden toolbar button
        btnContinue = findViewById(id.btn_continue);

        // Create the main toolbar
        Toolbar mTopToolbar = findViewById(id.main_toolbar);
        setSupportActionBar(mTopToolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean ok = false;
        // Check if the SharedPreferences are there and filled
        sp = getApplicationContext().getSharedPreferences("TerrariaApp", 0);
        boolean showSettings = false;
        if (sp != null) {
            for (int i = 0; i < nrOfTerraria; i++) {
                boolean ipAddressesFilled = !sp.getString("terrarium" + (i + 1) + "_ip_address", "x").equalsIgnoreCase("x");
                // Check if the ip addresses of the TCUs are reacheable
                String message = "";
                if (ipAddressesFilled || MOCK[i]) {
                    if (!MOCK[i]) {
                        message = isConnected(i);
                        ok = (message.length() == 0);
                    } else {
                        ok = true;
                    }
                }
                if (!ok) {
                    if (message.length() == 0) {
                        message = TerrariaApp.configs[i].getTcuName() + " heeft nog geen IP adres";
                    }
                    ndlg = new NotificationDialog(this, "Error", message);
                    ndlg.show();
                    showSettings = true;
                }
            }
            if (!showSettings) {
                getProperties();
            } else {
                mTabbar.setVisibility(View.GONE);
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(id.layout, new SettingsFragment())
                        .commit();
            }
        }
        super.onStart();
    }
    /*
    This is the onClick method of the btn_coninue.
    It will be clicked by the background thread when the properties of the TCU are read.
     */
    public void go(View view) {
        mTabbar.setVisibility(View.VISIBLE);
        curTabNr = 1;
        mTabTitles[curTabNr - 1].setTextColor(Color.WHITE);
        curIPAddress = getApplicationContext().getSharedPreferences("TerrariaApp", 0).getString("terrarium" + curTabNr + "_ip_address", "");
        getSupportFragmentManager()
                .beginTransaction()
                .replace(id.layout, StateFragment.newInstance(curTabNr))
                .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        this.menu = menu;
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
        if (id == R.id.menu_history_item) {
            mTabbar.setVisibility(View.VISIBLE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, HistoryFragment.newInstance(curTabNr))
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
        curTabNr = Integer.parseInt((String)v.getTag());
        mTabbar.setVisibility(View.VISIBLE);
        mTabTitles[curTabNr - 1].setTextColor(Color.WHITE);
        menu.findItem(id.menu_history_item).setVisible(configs[curTabNr - 1].getTcu().endsWith("PI"));
        String key = "terrarium" + curTabNr + "_ip_address";
        curIPAddress = getApplicationContext().getSharedPreferences("TerrariaApp", 0).getString(key, "");
        getSupportFragmentManager()
                .beginTransaction()
                .replace(id.layout, StateFragment.newInstance(curTabNr))
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
    public void getProperties() {
        Context ctx = this;
        wait = new WaitSpinner(this);
        wait.start();
        new Thread(() -> {
            // Executed in separate thread
            for (int tcunr = 0; tcunr < nrOfTerraria; tcunr++) {
                String name = configs[tcunr].getTcuName();
                String pfx = configs[tcunr].getMockPostfix();
                if (MOCK[tcunr]) {
                    try {
                        Gson gson = new Gson();
                        String response = new BufferedReader(
                                new InputStreamReader(getResources().getAssets().open("properties_" + pfx + ".json")))
                                .lines().collect(Collectors.joining("\n"));
                        configs[tcunr] = gson.fromJson(response, Properties.class);
                    } catch (IOException e) {
                    }
                } else {
                    try {
                        String ip = sp.getString("terrarium" + (tcunr + 1) + "_ip_address", "x");
                        URL url = new URL("http://" + ip + "/properties");
                        HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
                        httpConnection.setRequestMethod("GET");
                        httpConnection.setRequestProperty("Accept", "application/json");
                        String json = new BufferedReader(new InputStreamReader(httpConnection.getInputStream(), StandardCharsets.UTF_8))
                                .lines().collect(Collectors.joining("\n"));
                        configs[tcunr] = new Gson().fromJson(json, Properties.class);
//                        Log.i("Terraria", "TerrariaApp.getProperties() thread got it:\n" + json);
                    } catch (Exception e) {
                        // Now tell the UI thread to show the dialog and end thread
                        runOnUiThread(() -> {
                            wait.dismiss();
                            ndlg = new NotificationDialog(ctx, "Error", "Terrarium Control Unit '" + name + "' is niet bekend op het netwerk met dit IP adres.");
                            ndlg.show();
                        });
                    }
                }
                configs[tcunr].setTcuName(name);
                configs[tcunr].setMockPostfix(pfx);
            }
            // Now tell the UI thread to continue
            runOnUiThread(() -> {
                mTabbar.setVisibility(View.VISIBLE);
                for (int i = 0; i < nrOfTerraria; i++) {
                    mTabTitles[i].setVisibility(View.VISIBLE);
                    mTabTitles[i].setText(getString(R.string.tabName, configs[i].getTcuName(), (MOCK[i] ? " (Test)" : "")));
                }
                curTabNr = 1;
                btnContinue.callOnClick();
                wait.dismiss();
            });
        }).start();
    }

    public String isConnected(int tcunr) {
        String message = "";
        // Executed in separate thread
        Runtime runtime = Runtime.getRuntime();
        String ip = sp.getString("terrarium" + (tcunr + 1) + "_ip_address", "x");
        try {
            Process ipProcess = runtime.exec("/system/bin/ping -c 1 -w 1 " + ip);
            int exitValue = ipProcess.waitFor();
            if (exitValue != 0) {
                message = TerrariaApp.configs[tcunr].getTcuName() + " is niet bereikbaar op IP adres " + ip;
            }
            connected[tcunr] = (exitValue == 0);
            ipProcess.destroy();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return message;
    }
}