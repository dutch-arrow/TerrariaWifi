package nl.das.terraria.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.das.terraria.RequestQueueSingleton;
import nl.das.terraria.TerrariaApp;
import nl.das.terraria.R;
import nl.das.terraria.Utils;
import nl.das.terraria.VoidRequest;
import nl.das.terraria.dialogs.NotificationDialog;
import nl.das.terraria.dialogs.WaitSpinner;
import nl.das.terraria.json.Timer;

public class TimersListFragment extends Fragment {

    private String deviceID;
    private String curIPAddress;
    private Button btnSave;
    private Button btnRefresh;
    private EditText[] edtTimeOn = new EditText[4];
    private EditText[] edtTimeOff = new EditText[4];
    private EditText[] edtRepeat = new EditText[4];
    private EditText[] edtPeriod = new EditText[4];


    public static Map<String, Timer[]> timers = new HashMap<>();
    private WaitSpinner wait;
    private InputMethodManager imm;

    public TimersListFragment() { }

    public static TimersListFragment newInstance(String device) {
        TimersListFragment fragment = new TimersListFragment();
        Bundle args = new Bundle();
        args.putString("deviceID", device);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        deviceID = getArguments().getString("deviceID");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.four_timers_frg, container, false);
        imm = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        curIPAddress = getContext().getSharedPreferences("TerrariaApp", 0).getString("terrarium1_ip_address", "");
        btnSave = (Button) view.findViewById(R.id.ti_btnSave);
        btnSave.setEnabled(false);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSave.requestFocusFromTouch();
                Log.i("Terraria", "Save timers for device " + deviceID);
                saveTimers();
                imm.hideSoftInputFromWindow(btnSave.getWindowToken(), 0);
                btnSave.setEnabled(false);
            }
        });
        btnRefresh = (Button) view.findViewById(R.id.ti_btnRefresh);
        btnRefresh.setEnabled(true);
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("Terraria", "Refresh timers for device " + deviceID);
                getTimers();
                imm.hideSoftInputFromWindow(btnRefresh.getWindowToken(), 0);
                btnSave.setEnabled(false);
            }
        });
        int resId;
        for (int i = 0; i < 4; i++) {
            int nr = i;
            resId = getResources().getIdentifier("it_edtTimeOn_" + (i + 1), "id", getContext().getPackageName());
            edtTimeOn[i] = view.findViewById(resId);
            edtTimeOn[i].setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    imm.hideSoftInputFromWindow(edtTimeOn[nr].getWindowToken(), 0);
                    if (checkTime(edtTimeOn[nr])) {
                        String value = String.valueOf(edtTimeOn[nr].getText()).trim();
                        int hr = Utils.getH(value);
                        int min = Utils.getM(value);
                        Log.i("Terraria", "EditorAction: Save hourOn=" + hr + " minuteOn=" + min);
                        timers.get(deviceID)[nr].setHourOn(hr);
                        timers.get(deviceID)[nr].setMinuteOn(min);
                        edtTimeOff[nr].requestFocus();
                        btnSave.setEnabled(true);
                    }
                }
                return false;
            });
            edtTimeOn[i].setOnFocusChangeListener(((v, hasFocus) -> {
                if (!hasFocus) {
                    if (checkTime(edtTimeOn[nr])) {
                        String value = String.valueOf(edtTimeOn[nr].getText()).trim();
                        int hr = Utils.getH(value);
                        int min = Utils.getM(value);
                        Log.i("Terraria", "FocusChange: Save hourOn=" + hr + " minuteOn=" + min);
                        timers.get(deviceID)[nr].setHourOn(hr);
                        timers.get(deviceID)[nr].setMinuteOn(min);
                        btnSave.setEnabled(true);
                    }
                }
            }));
            resId = getResources().getIdentifier("it_edtTimeOff_" + (i + 1), "id", getContext().getPackageName());
            edtTimeOff[i] = view.findViewById(resId);
            edtTimeOff[i].setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    imm.hideSoftInputFromWindow(edtTimeOff[nr].getWindowToken(), 0);
                    if (checkTime(edtTimeOff[nr])) {
                        String value = String.valueOf(edtTimeOff[nr].getText()).trim();
                        int hr = Utils.getH(value);
                        int min = Utils.getM(value);
                        Log.i("Terraria", "EditorAction: Save hourOff=" + hr + " minuteOff=" + min);
                        timers.get(deviceID)[nr].setHourOff(hr);
                        timers.get(deviceID)[nr].setMinuteOff(min);
                        if (value.length() > 0) {
                            edtPeriod[nr].setText("0");
                            TimersListFragment.timers.get(deviceID)[nr].setPeriod(0);
                        }
                        edtRepeat[nr].requestFocus();
                        btnSave.setEnabled(true);
                    }
                }
                return false;
            });
            edtTimeOff[i].setOnFocusChangeListener(((v, hasFocus) -> {
                if (!hasFocus) {
                    if (checkTime(edtTimeOff[nr])) {
                        String value = String.valueOf(edtTimeOff[nr].getText()).trim();
                        int hr = Utils.getH(value);
                        int min = Utils.getM(value);
                        Log.i("Terraria", "FocusChange: Save hourOn=" + hr + " minuteOn=" + min);
                        timers.get(deviceID)[nr].setHourOff(hr);
                        timers.get(deviceID)[nr].setMinuteOff(min);
                        if (value.length() > 0) {
                            edtPeriod[nr].setText("0");
                            TimersListFragment.timers.get(deviceID)[nr].setPeriod(0);
                        }
                        btnSave.setEnabled(true);
                    }
                }
            }));
            resId = getResources().getIdentifier("it_edtRepeat_" + (i + 1), "id", getContext().getPackageName());
            edtRepeat[i] = view.findViewById(resId);
            edtRepeat[i].setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    imm.hideSoftInputFromWindow(edtRepeat[nr].getWindowToken(), 0);
                    String value = String.valueOf(edtRepeat[nr].getText()).trim();
                    try {
                        int rv = Integer.parseInt(value);
                        if (rv < 0 || rv > 1) {
                            edtRepeat[nr].setError("Herhaling moet 0 of 1 zijn. 0 betekent niet aktief, 1 dagelijks");
                        }
                    }
                    catch (NumberFormatException e) {
                        edtRepeat[nr].setError("Herhaling moet 0 of 1 zijn.");
                    }
                    if (edtRepeat[nr].getError() == null) {
                        Log.i("Terraria", "EditorAction: Save repeat=" + value);
                        timers.get(deviceID)[nr].setRepeat(Integer.parseInt(value));
                        edtPeriod[nr].requestFocus();
                        btnSave.setEnabled(true);
                    }
                }
                return false;
            });
            edtRepeat[i].setOnFocusChangeListener(((v, hasFocus) -> {
                if (!hasFocus) {
                    String value = String.valueOf(edtRepeat[nr].getText()).trim();
                    try {
                        int rv = Integer.parseInt(value);
                        if (rv < 0 || rv > 1) {
                            edtRepeat[nr].setError("Herhaling moet 0 of 1 zijn. 0 betekent niet aktief, 1 dagelijks");
                        }
                    }
                    catch (NumberFormatException e) {
                        edtRepeat[nr].setError("Herhaling moet 0 of 1 zijn.");
                    }
                    if (edtRepeat[nr].getError() == null) {
                        Log.i("Terraria", "FocusChange: Save repeat=" + value);
                        timers.get(deviceID)[nr].setRepeat(Integer.parseInt(value));
                        btnSave.setEnabled(true);
                    }
                }
            }));
            resId = getResources().getIdentifier("it_edtPeriod_" + (i + 1), "id", getContext().getPackageName());
            edtPeriod[i] = view.findViewById(resId);
            edtPeriod[i].setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    imm.hideSoftInputFromWindow(edtPeriod[nr].getWindowToken(), 0);
                    String value = String.valueOf(edtPeriod[nr].getText()).trim();
                    try {
                        int val = Integer.parseInt(value);
                        if (val < 0 || val > 3600) {
                            edtPeriod[nr].setError("Periode moet een getal tussen 0 en 3600 zijn.");
                        }
                    }
                    catch (NumberFormatException e) {
                        edtPeriod[nr].setError("Periode moet een getal tussen 0 en 3600 zijn.");
                    }
                    if (edtPeriod[nr].getError() == null) {
                        Log.i("Terraria", "EditorAction: Save period="+ edtPeriod[nr].getText().toString());
                        TimersListFragment.timers.get(deviceID)[nr].setPeriod(Integer.parseInt(value));
                        if (!value.equalsIgnoreCase("0")) {
                            edtTimeOff[nr].setText("");
                            timers.get(deviceID)[nr].setHourOff(0);
                            timers.get(deviceID)[nr].setMinuteOff(0);
                        }
                        btnSave.setEnabled(true);
                    }
                }
                btnSave.requestFocusFromTouch();
                return false;
            });
            edtPeriod[i].setOnFocusChangeListener(((v, hasFocus) -> {
                if (!hasFocus) {
                    String value = String.valueOf(edtPeriod[nr].getText()).trim();
                    try {
                        int val = Integer.parseInt(value);
                        if (val < 0 || val > 3600) {
                            edtPeriod[nr].setError("Periode moet een getal tussen 0 en 3600 zijn.");
                        }
                    }
                    catch (NumberFormatException e) {
                        edtPeriod[nr].setError("Periode moet een getal tussen 0 en 3600 zijn.");
                    }
                    if (edtPeriod[nr].getError() == null) {
                        Log.i("Terraria", "FocusChange: Save period="+ edtPeriod[nr].getText().toString());
                        TimersListFragment.timers.get(deviceID)[nr].setPeriod(Integer.parseInt(value));
                        if (!value.equalsIgnoreCase("0")) {
                            edtTimeOff[nr].setText("");
                            timers.get(deviceID)[nr].setHourOff(0);
                            timers.get(deviceID)[nr].setMinuteOff(0);
                        }
                        btnSave.setEnabled(true);
                    }
                }
            }));
        }
        getTimers();
     }

    private void updateTimers() {
        int ids[] = {R.id.it_layTimer_1, R.id.it_layTimer_2, R.id.it_layTimer_3, R.id.it_layTimer_4};
        for (int i = 0; i < timers.get(deviceID).length; i++) {
            ConstraintLayout fcv = getView().findViewById(ids[i]);
            fcv.setVisibility(View.VISIBLE);
            edtTimeOn[i].setText(Utils.cvthm2string(timers.get(deviceID)[i].getHourOn(), timers.get(deviceID)[i].getMinuteOn()));
            edtTimeOff[i].setText(Utils.cvthm2string(timers.get(deviceID)[i].getHourOff(), timers.get(deviceID)[i].getMinuteOff()));
            edtRepeat[i].setText(timers.get(deviceID)[i].getRepeat() + "");
            edtPeriod[i].setText(timers.get(deviceID)[i].getPeriod() + "");
            Log.i("Terraria", "timer " + i + " added");
        }

    }

    private void getTimers() {
        wait = new WaitSpinner(requireContext());
        wait.start();
        String url = "http://" + curIPAddress + "/timers/" + deviceID;
        Log.i("Terraria","Execute GET request " + url);
        // Request sensor readings.
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, url, null,
                (Response.Listener<JSONArray>) response1 -> {
                    Gson gson = new Gson();
                    try {
                        Timer devTimers[] = gson.fromJson(response1.toString(), new TypeToken<Timer[]>() {}.getType());
                        Log.i("Terraria", "Retrieved " + devTimers.length + " timers for device " + deviceID);
                        timers.put(deviceID, devTimers);
                        updateTimers();
                        wait.dismiss();
                    } catch (JsonSyntaxException e) {
                        new NotificationDialog(requireContext(), "Error", "Timers response contains errors:\n" + e.getMessage()).show();
                    }
                },
                (Response.ErrorListener) error -> {
                    if (error.getMessage() == null) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        error.printStackTrace(pw);
                        Log.i("Terraria", "getTimers error:\n" + sw.toString());
                    } else {
                        Log.i("Terraria", "Error " + error.getMessage());
                        new NotificationDialog(requireContext(), "Error", "Kontakt met Control Unit verloren.").show();
                    }
                    wait.dismiss();
                }
        );
        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(requireContext()).add(jsonArrayRequest);
    }

    private void saveTimers() {
        wait = new WaitSpinner(requireContext());
        wait.start();
        String url = "http://" + curIPAddress + "/timers/" + deviceID;
        Log.i("Terraria","Execute PUT request " + url);
        Gson gson = new Gson();
        String json = gson.toJson(timers.get(deviceID));
        Log.i("Terraria","JSON sent:");
        Log.i("Terraria",json);
        VoidRequest req = new VoidRequest(Request.Method.PUT, url, json,
                (Response.Listener<Void>) response -> {
                    Log.i("Terrarium", "The timers for device " + deviceID + " have been saved.");
                    wait.dismiss();
                },
                (Response.ErrorListener) error -> {
                    wait.dismiss();
                    Log.i("Terrarium","Error " + error.getMessage());
                    new NotificationDialog(requireActivity(), "Error", "Kontakt met Control Unit verloren.").show();
                }
        );
        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(requireContext()).add(req);
    }

    public boolean checkTime(EditText field) {
        Log.i("Terrarium", "Check time input");
        String value = String.valueOf(field.getText()).trim();
        if (value.length() != 0) {
            String[] parts = value.split("\\.");
            if (parts.length == 2) {
                try {
                    int hr = Integer.parseInt(parts[0].trim());
                    if (hr < 0 || hr > 23) {
                        field.setError("Uuropgave moet tussen 0 en 23 zijn");
                    }
                } catch (NumberFormatException e) {
                    field.setError("Uuropgave is geen getal");
                }
                try {
                    int min = Integer.parseInt(parts[1].trim());
                    if (min < 0 || min > 59) {
                        field.setError("Minutenopgave moet tussen 0 en 59 zijn");
                    }
                } catch (NumberFormatException e) {
                    field.setError("Minutenopgave is geen getal");
                }
            } else {
                field.setError("Tijdopgave is niet juist. Formaat: hh.mm");
            }
        } else {
            Log.i("Terrarium", "No timevalue given.");
        }
        return field.getError() == null;
    }
}