package nl.das.terraria.fragments;

import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import nl.das.terraria.R;
import nl.das.terraria.RequestQueueSingleton;
import nl.das.terraria.TerrariaApp;
import nl.das.terraria.VoidRequest;
import nl.das.terraria.dialogs.NotificationDialog;
import nl.das.terraria.dialogs.WaitSpinner;
import nl.das.terraria.json.Device;
import nl.das.terraria.json.Sensor;
import nl.das.terraria.json.Sensors;
import nl.das.terraria.json.State;
import nl.das.terraria.json.States;

public class StateFragment extends Fragment {

    private int tabnr;
    private boolean trace = false;
    private String curIPAddress;
    private Sensors sensors;
    private WaitSpinner wait;

    private LinearLayout deviceLayout;
    private TextView tvwDateTime;
    private TextView tvwTTemp;
    private TextView tvwRHum;
    private TextView tvwRTemp;

    public StateFragment() {
        // Required empty public constructor
    }

    public static StateFragment newInstance(int tabnr) {
        Log.i("Terraria", "StateFragment.newInstance() start");
        StateFragment fragment = new StateFragment();
        Bundle args = new Bundle();
        args.putInt("tabnr", tabnr);
        fragment.setArguments(args);
        Log.i("Terraria", "StateFragment.newInstance() end");
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i("Terraria", "StateFragment.onCreate() start");
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tabnr = getArguments().getInt("tabnr");
        }
        Log.i("Terraria", "StateFragment.onCreate() end. Tabnr=" + tabnr);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i("Terraria", "StateFragment.onCreateView() start");
        View view = inflater.inflate(R.layout.fragment_state, container, false);
        deviceLayout = view.findViewById(R.id.trm_lay_device_state);
        for (Device d :  TerrariaApp.configs[tabnr - 1].getDevices()) {
            View v = inflater.inflate(R.layout.fragment_device_state, container, false);
            SwitchCompat sw = v.findViewById(R.id.trm_switchDevice);
            String devname = d.getDevice();
            int r = getResources().getIdentifier(devname, "string", "nl.das.terrariawifi");
            sw.setText(getResources().getString(r));
            if (devname.equalsIgnoreCase("uvlight")) {
                v.findViewById(R.id.trm_lay_uv).setVisibility(View.VISIBLE);
            } else {
                v.findViewById(R.id.trm_lay_uv).setVisibility(View.GONE);
            }
            deviceLayout.addView(v);
        }
        Log.i("Terraria", "StateFragment.onCreateView() end");
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.i("Terraria", "StateFragment.onViewCreated() start");
        super.onViewCreated(view, savedInstanceState);
        curIPAddress = requireContext().getSharedPreferences("TerrariaApp", 0).getString("terrarium" + tabnr + "_ip_address", "");
        // Header

        Log.i("Terraria", TerrariaApp.configs[tabnr - 1].getTcu());

        Button btn = view.findViewById(R.id.trm_refreshButton);
        btn.setOnClickListener(v -> {
            Log.i("Terraria", "refresh State");
            getSensors();
            getState();
        });

        // walk through all device views
        int vix = 0;
        for (Device d :  TerrariaApp.configs[tabnr - 1].getDevices()) {
            View v = deviceLayout.getChildAt(vix);
            SwitchCompat swManual = v.findViewById(R.id.trm_swManualDevice);
            TextView state = v.findViewById(R.id.trm_tvwDeviceState);
            swManual.setOnClickListener(cv -> {
                SwitchCompat sc = (SwitchCompat) cv;
                if (sc.isChecked()) {
                    switchManual(d.getDevice(), true);
                } else {
                    switchManual(d.getDevice(), false);
                    state.setText("");
                }
            });
            SwitchCompat swDevice = v.findViewById(R.id.trm_switchDevice);
            swDevice.setOnClickListener(cv -> {
                SwitchCompat sc = (SwitchCompat) cv;
                if (sc.isChecked()) {
                    switchDevice(d.getDevice(), true);
                } else {
                    switchDevice(d.getDevice(), false);
                    state.setText("");
                }
            });
            if (d.isLifecycle()) {
                TextView tvwHours = v.findViewById(R.id.trm_tvwHours_lcc);
                Button btnReset = v.findViewById(R.id.trm_btnReset);
                btnReset.setOnClickListener(cv -> {
                    Log.i("Terraria", "Reset lifecycle counter for device '" + d.getDevice() + "'");
                    // Create an instance of the dialog fragment and show it
                    ResetHoursDialogFragment dlgReset = ResetHoursDialogFragment.newInstance(d.getDevice());
                    FragmentManager fm = requireActivity().getSupportFragmentManager();
                    // SETS the target fragment for use later when sending results
                    fm.setFragmentResultListener("reset", this, (requestKey, result) -> {
                        tvwHours.setText(result.getInt("hours"));
                        onResetHoursSave(d.getDevice(), result.getInt("hours"));
                    });
                    dlgReset.show(fm, "ResetHoursDialogFragment");
                });
            }
            vix++;
        }

        tvwDateTime = view.findViewById(R.id.trm_st_tvwDateTime);
        tvwTTemp = view.findViewById(R.id.trm_st_tvwTtemp);
        tvwRHum = view.findViewById(R.id.trm_st_tvwRhum);
        tvwRTemp = view.findViewById(R.id.trm_st_tvwRtemp);

        getSensors();
        getState();
        Log.i("Terraria", "StateFragment.onViewCreated() end");
    }

    private void switchDevice(String device, boolean yes) {
        final WaitSpinner wait = new WaitSpinner(requireActivity());
        wait.start();
        String url = "http://" + curIPAddress + "/device/" + device + (yes ? "/on" : "/off");
        Log.i("Terraria","Execute PUT request " + url);
        // Switch device off.
        StringRequest jsonArrayRequest = new StringRequest(Request.Method.PUT, url,
                response1 -> {
                    Log.i("Terraria", "Switch device " + device + (yes ? " on" : " off"));
                    wait.dismiss();
                },
                error -> {
                    wait.dismiss();
                    Log.i("Terraria","Error " + error.getMessage());
                    new NotificationDialog(getContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
                }
        );
        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(getContext()).add(jsonArrayRequest);
    }

    private void switchManual(String device, boolean yes) {
        final WaitSpinner wait = new WaitSpinner(requireActivity());
        wait.start();
        String url = "http://" + curIPAddress + "/device/" + device + (yes ? "/manual" : "/auto");
        Log.i("Terraria","Execute PUT request " + url);
        // Switch device off.
        StringRequest jsonArrayRequest = new StringRequest(Request.Method.PUT, url,
                response1 -> {
                    Log.i("Terraria", "Switch device " + device + (yes ? " to manual" : " to auto"));
                    wait.dismiss();
                },
                error -> {
                    wait.dismiss();
                    Log.i("Terraria","Error " + error.getMessage());
                    new NotificationDialog(getContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
                }
        );
        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(getContext()).add(jsonArrayRequest);
    }

    private void getSensors() {
        wait = new WaitSpinner(requireContext());
        wait.start();
        if (TerrariaApp.MOCK[tabnr - 1]) {
            Log.i("Terraria", "Retrieved mocked sensor readings");
            Gson gson = new Gson();
            try {
                String response = new BufferedReader(
                    new InputStreamReader(getResources().getAssets().open("sensors_" + TerrariaApp.configs[tabnr - 1].getMockPostfix() + ".json")))
                    .lines().collect(Collectors.joining("\n"));
                sensors = gson.fromJson(response, Sensors.class);
                updateSensors();
            } catch (JsonSyntaxException | IOException e) {
                new NotificationDialog(requireContext(), "Error", "Sensors response contains errors:\n" + e.getMessage()).show();
            }

        } else {
            String url = "http://" + curIPAddress + "/sensors";
            Log.i("Terraria", "Execute GET request " + url);
            // Request sensor readings.
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response1 -> {
                    Log.i("Terraria", "Retrieved sensor readings");
                    Gson gson = new Gson();
                    try {
                        sensors = gson.fromJson(response1.toString(), Sensors.class);
                        updateSensors();
                    } catch (JsonSyntaxException e) {
                        new NotificationDialog(requireContext(), "Error", "Sensors response contains errors:\n" + e.getMessage()).show();
                    }
                },
                error -> {
                    if (error.getMessage() == null) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        error.printStackTrace(pw);
                        Log.i("Terraria", "loadSensors error:\n" + sw);
                    } else {
                        Log.i("Terraria", "Error " + error.getMessage());
                        new NotificationDialog(requireContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
                    }
                    wait.dismiss();
                }
            );
            // Add the request to the RequestQueue.
            RequestQueueSingleton.getInstance(requireContext()).add(jsonObjectRequest);
        }
    }

    private void updateSensors() {
        tvwDateTime.setText(sensors.getClock());
        for (Sensor sensor: sensors.getSensors()) {
            if (sensor.getLocation().equalsIgnoreCase("room")) {
                tvwRTemp.setText(getString(R.string.temperature, sensor.getTemperature()));
                tvwRHum.setText(getString(R.string.humidity, sensor.getHumidity()));
            } else if (sensor.getLocation().equalsIgnoreCase("terrarium")) {
                tvwTTemp.setText(getString(R.string.temperature, sensor.getTemperature()));
            }
        }
    }

    private void getState() {
        // Request state.
        if (TerrariaApp.MOCK[tabnr - 1]) {
            Log.i("Terraria", "Retrieved mocked state readings");
            Gson gson = new Gson();
            try {
                String response = new BufferedReader(
                        new InputStreamReader(getResources().getAssets().open("state_" + TerrariaApp.configs[tabnr - 1].getMockPostfix() + ".json")))
                        .lines().collect(Collectors.joining("\n"));
                List<State> states = gson.fromJson(response, new TypeToken<List<State>>() {}.getType());
                updateState(states);
                wait.dismiss();
            } catch (JsonSyntaxException | IOException e) {
                new NotificationDialog(requireContext(), "Error", "State response contains errors:\n" + e.getMessage()).show();
            }
        } else {
            String url = "http://" +  curIPAddress +  "/state";
            Log.i("Terraria","Execute GET request " + url);
            if (TerrariaApp.configs[tabnr - 1].getTcu().endsWith("PI")) {
                JsonObjectRequest jsonRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                        response1 -> {
                            Gson gson = new Gson();
                            try {
                                States states = gson.fromJson(response1.toString(), States.class);
                                Log.i("Terraria", "Retrieved " + states.getStates().size() + " states and trace is " + states.getTrace());
                                trace = states.getTrace().equalsIgnoreCase("on");
                                updateState(states.getStates());
                            } catch (JsonSyntaxException e) {
                                new NotificationDialog(requireContext(), "Error", "State response contains errors:\n" + e.getMessage()).show();
                            }
                            wait.dismiss();
                        },
                        error -> {
                            if (error.getMessage() == null) {
                                StringWriter sw = new StringWriter();
                                PrintWriter pw = new PrintWriter(sw);
                                error.printStackTrace(pw);
                                Log.i("Terraria", "loadState error:\n" + sw);
                            } else {
                                Log.i("Terraria", "Error " + error.getMessage());
                                new NotificationDialog(requireContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
                            }
                            wait.dismiss();
                        }
                );
                // Add the request to the RequestQueue.
                RequestQueueSingleton.getInstance(requireContext()).add(jsonRequest);
            } else {
                JsonArrayRequest jsonRequest = new JsonArrayRequest(Request.Method.GET, url, null,
                        response1 -> {
                            Log.i("Terraria", "Retrieved " + response1.length() + " states");
                            Gson gson = new Gson();
                            try {
                                List<State> states = gson.fromJson(response1.toString(), new TypeToken<List<State>>() {}.getType());
                                updateState(states);
                            } catch (JsonSyntaxException e) {
                                new NotificationDialog(requireContext(), "Error", "State response contains errors:\n" + e.getMessage()).show();
                            }
                            wait.dismiss();
                        },
                        error -> {
                            if (error.getMessage() == null) {
                                StringWriter sw = new StringWriter();
                                PrintWriter pw = new PrintWriter(sw);
                                error.printStackTrace(pw);
                                Log.i("Terraria", "loadState error:\n" + sw);
                            } else {
                                Log.i("Terraria", "Error " + error.getMessage());
                                new NotificationDialog(requireContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
                            }
                            wait.dismiss();
                        }
                );
                // Add the request to the RequestQueue.
                RequestQueueSingleton.getInstance(requireContext()).add(jsonRequest);
            }
        }
    }

    private void updateState(List<State> states) {
        Log.i("Terraria","updateState() start");
        int vix = 0;
        for (Device d :  TerrariaApp.configs[tabnr - 1].getDevices()) {
            for (State s : states) {
                if (d.getDevice().equalsIgnoreCase(s.getDevice())) {
                    View v = deviceLayout.getChildAt(vix);
                    SwitchCompat swManual = v.findViewById(R.id.trm_swManualDevice);
                    SwitchCompat swDevice = v.findViewById(R.id.trm_switchDevice);
                    TextView state = v.findViewById(R.id.trm_tvwDeviceState);
                    swManual.setChecked(s.getManual().equalsIgnoreCase("yes"));
                    swDevice.setChecked(s.getState().equalsIgnoreCase("on"));
                    state.setText(translateEndTime(s.getEndTime()));
                    if (d.isLifecycle()) {
                        TextView h = v.findViewById(R.id.trm_tvwHours_lcc);
                        h.setText(getString(R.string.hoursOn, s.getHoursOn()));
                    }
                }
            }
            vix++;
        }
        Log.i("Terraria","updateState() start");
    }

    public void onClockSave(String dateTime) {
        final WaitSpinner wait = new WaitSpinner(requireActivity());
        wait.start();
        String url = "http://" + curIPAddress + "/setdate/" + dateTime;
        Log.i("Terraria","Execute PUT request " + url);
        // Switch device off.
        StringRequest jsonArrayRequest = new StringRequest(Request.Method.POST, url,
                response1 -> {
                    Log.i("Terraria", "The clock has been set");
                    try {
                        SimpleDateFormat fmtin = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                        Date dtin = fmtin.parse(dateTime);
                        SimpleDateFormat fmtout = new SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.US);
                        String dtout = fmtout.format(dtin);
                        tvwDateTime.setText(dtout);
                    }
                    catch (ParseException e) {
                        // Cannot be
                    }
                    wait.dismiss();
                },
                error -> {
                    wait.dismiss();
                    Log.i("Terraria","Error " + error.getMessage());
                    new NotificationDialog(getContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
                }
        );
        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(getContext()).add(jsonArrayRequest);
    }

    public void onResetHoursSave(String device, int hoursOn) {
        final WaitSpinner wait = new WaitSpinner(requireActivity());
        wait.start();
        String url = "http://" + curIPAddress + "/counter/" + device  + "/" + hoursOn;
        Log.i("Terraria","Execute POST request " + url);
        // Set counter.
        StringRequest jsonObjectRequest = new StringRequest(Request.Method.POST, url,
                response -> {
                    Log.i("Terraria", "Set lifecycle counter of " + device + " to " + hoursOn);
                    wait.dismiss();
                },
                error -> {
                    wait.dismiss();
                    Log.i("Terrarium","Error " + error.getMessage());
                    new NotificationDialog(getContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
                }
        );
        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(getContext()).add(jsonObjectRequest);
    }

    private String translateEndTime(String endTime) {
        if (endTime == null) {
            return "";
        } else {
            if (endTime.equalsIgnoreCase("no endtime")) {
                return "geen eindtijd";
            } else if (endTime.equalsIgnoreCase("until ideal temperature is reached")) {
                return "tot ideale temperatuur bereikt is";
            } else if (endTime.equalsIgnoreCase("until ideal humidity is reached")) {
                return "tot ideale vochtigheidsgraad bereikt is";
            } else {
                return endTime;
            }
        }
    }

    public void record(boolean start) {
        final WaitSpinner wait = new WaitSpinner(requireActivity());
        wait.start();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String url = "http://" + curIPAddress + "/trace/" + (start ? "on" : "off");
        Log.i("Terraria","Execute POST request " + url);
        VoidRequest voidRequest = new VoidRequest(Request.Method.POST, url, null,
                (Response.Listener<Void>) response1 -> {
                    Log.i("Terraria", "Recording " + (start ? "started..." : "stopped"));
                    wait.dismiss();
                },
                (Response.ErrorListener) error -> {
                    wait.dismiss();
                    Log.i("Terraria","Error " + error.getMessage());
                    new NotificationDialog(getContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
                }
        );
        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(getContext()).add(voidRequest);
    }

    public void viewRecording(String type) { // type = "state" or "temperature"
        final WaitSpinner wait = new WaitSpinner(requireActivity());
        wait.start();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String url = "http://" + curIPAddress + "/history/" + type;
        Log.i("Terraria","Execute GET request " + url);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                (Response.Listener<String>) response1 -> {
                    Log.i("Terraria", "Get the recording of " + type);
                    wait.dismiss();
                },
                (Response.ErrorListener) error -> {
                    wait.dismiss();
                    Log.i("Terraria","Error " + error.getMessage());
                    new NotificationDialog(getContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
                }
        );
        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(getContext()).add(stringRequest);
    }
}