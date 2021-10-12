package nl.das.terraria.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.das.terraria.R;
import nl.das.terraria.RequestQueueSingleton;
import nl.das.terraria.TerrariaApp;
import nl.das.terraria.dialogs.NotificationDialog;
import nl.das.terraria.dialogs.WaitSpinner;

public class SettingsFragment extends Fragment {
    private static final String IPV4_PATTERN = "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$";
    private static final Pattern pattern = Pattern.compile(IPV4_PATTERN);

    private Button btnSave;
    private EditText[] edt;
    private SharedPreferences sharedPreferences;
    private InputMethodManager imm;
    private int curnr;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v =  inflater.inflate(R.layout.fragment_settings, container, false);
        imm = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        btnSave = (Button) view.findViewById(R.id.settings_btnSave);
        btnSave.setEnabled(false);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSave.requestFocusFromTouch();
                Log.i("Terraria", "Save settings");
                saveSettings();
                btnSave.setEnabled(false);
            }
        });
        sharedPreferences = getActivity().getApplicationContext().getSharedPreferences("TerrariaApp", 0);
        edt = new EditText[TerrariaApp.nrOfTerraria];
        for (int i = 0; i < TerrariaApp.nrOfTerraria; i++) {
            int tnr = i + 1;
            int r = getResources().getIdentifier("tvwTerrarium" + tnr, "id", "nl.das.terraria2");
            TextView tvw = view.findViewById(r);
            tvw.setVisibility(View.VISIBLE);
            tvw.setText(TerrariaApp.configs[i].getTcu() + " IP adres");
            r = getResources().getIdentifier("edtTerrarium" + tnr, "id", "nl.das.terraria2");
            EditText etv = view.findViewById(r);
            etv.setVisibility(View.VISIBLE);
            etv.setText(sharedPreferences.getString("terrarium" + tnr + "_ip_address", "192.168.xxx.xxx"));
            etv.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String value = String.valueOf(etv.getText()).trim();
                    if (checkIPAddress(etv, value)) {
                        imm.hideSoftInputFromWindow(etv.getWindowToken(), 0);
                        btnSave.setEnabled(true);
                    }
                }
                return false;
            });
            edt[i] = etv;
        }
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (int i = 0; i < TerrariaApp.nrOfTerraria; i++) {
            editor.putString("terrarium" + (i + 1) + "_ip_address", edt[i].getText().toString());
        }
        editor.commit();
    }

    private boolean checkIPAddress(EditText field, String ipaddress) {
        Matcher matcher = pattern.matcher(ipaddress);
        boolean res = matcher.matches();
        if (!res) {
            field.setError("Waarde is geen IP address. Formaat: xxx.xxx.xxx.xxx");
        }
        return res;
    }
}