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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.das.terraria.R;
import nl.das.terraria.TerrariaApp;
import nl.das.terraria.dialogs.NotificationDialog;

public class SettingsFragment extends Fragment {
    private static final String IPV4_PATTERN = "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$";
    private static final Pattern pattern = Pattern.compile(IPV4_PATTERN);

    private Button btnSave;
    private EditText[] edt;
    private SharedPreferences sharedPreferences;
    private InputMethodManager imm;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v =  inflater.inflate(R.layout.fragment_settings, container, false);
        imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        btnSave = view.findViewById(R.id.settings_btnSave);
        btnSave.setEnabled(false);
        btnSave.setOnClickListener(v -> {
            btnSave.requestFocusFromTouch();
            if (saveSettings()) {
                ((TerrariaApp) requireContext()).getProperties();
            }
            btnSave.setEnabled(false);
        });
        sharedPreferences = requireActivity().getApplicationContext().getSharedPreferences("TerrariaApp", 0);
        edt = new EditText[TerrariaApp.nrOfTerraria];
        Log.i("Terraria",getResources().getResourcePackageName(R.id.tvwTerrarium1));
        for (int i = 0; i < TerrariaApp.nrOfTerraria; i++) {
            int tnr = i + 1;
            int r = getResources().getIdentifier("tvwTerrarium" + tnr, "id", "nl.das.terrariawifi");
            TextView tvw = view.findViewById(r);
            tvw.setVisibility(View.VISIBLE);
            tvw.setText(getString(R.string.ipName, TerrariaApp.configs[i].getTcuName()));
            r = getResources().getIdentifier("edtTerrarium" + tnr, "id", "nl.das.terrariawifi");
            EditText etv = view.findViewById(r);
            etv.setVisibility(View.VISIBLE);
            etv.setText(sharedPreferences.getString("terrarium" + tnr + "_ip_address", "192.168.178.xxx"));
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
        saveSettings();
    }

    private boolean saveSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (int i = 0; i < TerrariaApp.nrOfTerraria; i++) {
            editor.putString("terrarium" + (i + 1) + "_ip_address", edt[i].getText().toString());
        }
        editor.apply();
        boolean ok = true;
        for (int i = 0; i < TerrariaApp.nrOfTerraria; i++) {
            String msg = ((TerrariaApp) requireContext()).isConnected(i);
            if (msg.length() > 0) {
                ok = false;
                NotificationDialog ndlg = new NotificationDialog(getContext(), "Error", msg);
                ndlg.show();
            }
        }
        return ok;
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