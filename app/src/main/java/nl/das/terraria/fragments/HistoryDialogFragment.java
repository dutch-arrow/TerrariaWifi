package nl.das.terraria.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.gson.JsonSyntaxException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.das.terraria.R;
import nl.das.terraria.RequestQueueSingleton;
import nl.das.terraria.dialogs.NotificationDialog;
import nl.das.terraria.dialogs.WaitSpinner;

public class HistoryDialogFragment extends DialogFragment {

    private View view;
    private LineChart chart;
    private List<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
    private LineData lineData = new LineData(dataSets);


    // map: <device, <time, on>>
    private Map<String, Map<Integer, Boolean>> history_state = new HashMap<>();
    private Map<Integer, Integer> history_temp = new HashMap<>();
    SimpleDateFormat dtfmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    SimpleDateFormat tmfmt = new SimpleDateFormat("HH:mm");
    private long xstart;
    private int hmstart;
    private long xend;
    private String[] devicesNl = {"lamp1", "lamp2", "lamp3", "lamp4", "lamp5", "pomp", "nevel", "sproeier", "vent_in", "vent_uit", ""};
    private String[] devicesEn = {"light1", "light2", "light3", "light4", "light5", "pump", "mist", "sprayer", "fan_in", "fan_out", ""};
    private boolean[] devState = {false, false, false, false, false, false, false, false, false, false, false};

    public HistoryDialogFragment() {
        // Empty constructor required for DialogFragment
    }

    public static HistoryDialogFragment newInstance(String device) {
        HistoryDialogFragment frag = new HistoryDialogFragment();
//        Bundle args = new Bundle();
//        args.putString("device", device);
//        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        view  = inflater.inflate(R.layout.history_dlg, null);
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        chart = (LineChart) view.findViewById(R.id.linechart);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setDescription(null);
        chart.setDrawGridBackground(true);
        chart.setDrawMarkers(false);
        chart.getLegend().setEnabled(false);

        // Read in both history files
        readHistoryState();
        readHistoryTemperture();
        builder
                .setTitle("Bekijk de recording")
                .setView(view)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        HistoryDialogFragment.this.getDialog().cancel();
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();
        int width = (int)(getResources().getDisplayMetrics().widthPixels);
        int height = (int)(getResources().getDisplayMetrics().heightPixels*0.90);
        getDialog().getWindow().setLayout(width, height);;
    }

    private void readHistoryState() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String url = "http://" + prefs.getString("terrarium_ip_address", "") + "/history/state";
        Log.i("Terraria", "Execute GET request " + url);
        // Request state history.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                (Response.Listener<String>) response -> {
                     try {
                        Log.i("Terraria", "Retrieved state history");
                        /*  0123456789012345678
                            2021-08-01 05:00:00 start
                            2021-08-01 06:00:00 mist 1 -1
                            2021-08-01 06:00:00 fan_in 0
                            2021-08-01 06:00:00 fan_out 0
                         */
                        String[] lines = response.split("\n");
                        for (String line : lines) {
                            String[] parts = line.split(" ");
                            if (parts[2].equalsIgnoreCase("start")) {
                                xstart = dtfmt.parse(parts[0] + " " + parts[1]).getTime() / 1000;
                                String tm[] = parts[1].split(":");
                                hmstart = Integer.parseInt(tm[0]) * 3600 + Integer.parseInt(tm[1]) * 60 + Integer.parseInt(tm[2]);
                            } else if (parts[2].equalsIgnoreCase("stop")) {
                                xend = dtfmt.parse(parts[0] + " " + parts[1]).getTime() / 1000;
                            } else {
                                int tm = (int)((dtfmt.parse(parts[0] + " " + parts[1]).getTime() / 1000) - xstart);
                                String dev = parts[2];
                                boolean on = parts[3].equalsIgnoreCase("1");
                                if (history_state.get(dev) == null) {
                                    history_state.put(dev, new HashMap<>());
                                }
                                history_state.get(dev).put(tm, on);
                            }
                        }
                        drawChart();
                    } catch (JsonSyntaxException | ParseException e) {
                        new NotificationDialog(requireContext(), "Error", "Timers response contains errors:\n" + e.getMessage()).show();
                    }
                },
                (Response.ErrorListener) error -> {
                    if (error.getMessage() == null) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        error.printStackTrace(pw);
                        Log.i("Terraria", "Retrieved state history error:\n" + sw.toString());
                    } else {
                        Log.i("Terraria", "Error " + error.getMessage());
                        new NotificationDialog(requireContext(), "Error", "Kontakt met Control Unit verloren.").show();
                    }
                }
        );
        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(requireContext()).add(stringRequest);
    }

    private void drawChart() {
        // X axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setAxisMinimum(hmstart);
        xAxis.setAxisMaximum((24 * 60 * 60) + hmstart);
//        xAxis.setLabelRotationAngle(270f);
        xAxis.setLabelCount((((int)(xend - xstart)) / 900) + 1, true); // force 11 labels
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int hr = (int)value / 3600;
                int mn = ((int)value - (hr * 3600)) / 60;
                return String.format("%02d:%02d", (hr >= 24 ? hr -24 : hr), mn);
            }
        });
        // Y axis
        chart.getAxisRight().setEnabled(false); // suppress right y-axis
        YAxis yAxis = chart.getAxisLeft();
        yAxis.setTextSize(14f); // set the text size
        yAxis.setAxisMinimum(0f); // start at zero
        yAxis.setAxisMaximum(16.5f); // the axis maximum is 13.5
        yAxis.setTextColor(Color.BLACK);
        yAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int v = (int) (value * 2f); // 0, 3, 6, 9, ....
                if (v % 3 == 0 ) {
                    if (v == 0) {
                        return "Temp";
                    } else {
                        return devicesNl[(v - 3) / 3];
                    }
                } else {
                    return "";
                }
            }
        });
        yAxis.setGranularity(1.5f); // interval 1.5
        yAxis.setLabelCount(12, true); // force 11 labels

        // Constructing the datasets
        dataSets.add(getDatasets("light1",  Color.BLACK));
        dataSets.add(getDatasets("light2",  Color.BLUE));
        dataSets.add(getDatasets("light3",  Color.GRAY));
        dataSets.add(getDatasets("light4",  Color.RED));
        dataSets.add(getDatasets("light5",  Color.GREEN));
        dataSets.add(getDatasets("pump",    Color.BLACK));
        dataSets.add(getDatasets("sprayer", Color.GRAY));
        dataSets.add(getDatasets("mist",    Color.BLUE));
        dataSets.add(getDatasets("fan_in",  Color.RED));
        dataSets.add(getDatasets("fan_out", Color.GREEN));
        chart.setData(lineData);

        chart.invalidate(); // refresh
    }

    private void readHistoryTemperture() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String url = "http://" + prefs.getString("terrarium_ip_address", "") + "/history/temperature";
        Log.i("Terraria", "Execute GET request " + url);
        // Request state history.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                (Response.Listener<String>) response -> {
                    try {
                        Log.i("Terraria", "Retrieved temperature history");
                        /*
                            2021-08-01 05:00:00 r=21 t=21
                            2021-08-01 06:00:00 r=21 t=21
                            2021-08-01 06:45:00 r=21 t=21
                         */
                        String[] lines = response.split("\n");
                        for (String line : lines) {
                            String[] parts = line.split(" ");
                            if (parts[2].equalsIgnoreCase("start")) {
                                xstart = dtfmt.parse(parts[0] + " " + parts[1]).getTime() / 1000;
                                String tm[] = parts[1].split(":");
                                hmstart = Integer.parseInt(tm[0]) * 3600 + Integer.parseInt(tm[1]) * 60 + Integer.parseInt(tm[2]);
                            } else if (parts[2].equalsIgnoreCase("stop")) {
                                xend = dtfmt.parse(parts[0] + " " + parts[1]).getTime() / 1000;
                            } else {
                                int tm = (int)((dtfmt.parse(parts[0] + " " + parts[1]).getTime() / 1000) - xstart);
                                String room = parts[2].split("=")[1];
                                int terr = Integer.parseInt(parts[3].split("=")[1]);
                                history_temp.put(tm, terr);
                            }
                        }
                        drawTerrTempLine(0xFFF43F1A);
                    } catch (JsonSyntaxException | ParseException e) {
                        new NotificationDialog(requireContext(), "Error", "History response contains errors:\n" + e.getMessage()).show();
                    }
                },
                (Response.ErrorListener) error -> {
                    if (error.getMessage() == null) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        error.printStackTrace(pw);
                        Log.i("Terraria", "Retrieved temperature history error:\n" + sw.toString());
                    } else {
                        Log.i("Terraria", "Error " + error.getMessage());
                        new NotificationDialog(requireContext(), "Error", "Kontakt met Control Unit verloren.").show();
                    }
                }
        );
        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(requireContext()).add(stringRequest);
    }

    public void drawTerrTempLine(int color) {
        int curTemp = 0;
        List<Entry> entries = new ArrayList<Entry>();
        for (int i = 0; i < (xend - xstart); i++) {
            if (history_temp.get(i) != null) {
                curTemp = history_temp.get(i);
            }
            entries.add(new Entry(i + hmstart, (curTemp - 15f) / 20f));
        }
        LineDataSet dataSet = new LineDataSet(entries, ""); // add entries to dataset
        dataSet.setColor(color);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);
        dataSets.add(dataSet);
        chart.invalidate(); // refresh

    }

    private ILineDataSet getDatasets(String device, int color) {
        List<Entry> entries = getEntries(device);
        LineDataSet dataSet = new LineDataSet(entries, ""); // add entries to dataset
        dataSet.setColor(color);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);
        return dataSet;
    }

    private List<Entry> getEntries(String device) {
        int ix = getIndex(device);
        Map<Integer, Boolean> dev_states = history_state.get(device);
        List<Entry> entries = new ArrayList<Entry>();
        for (int i = 0; i < (xend - xstart); i++) {
            if (dev_states.get(i) != null) {
                devState[ix] = dev_states.get(i);
            }
            entries.add(new Entry(i + hmstart, (devState[ix] ? 1f : 0f) + (ix + 1) * 1.5f));
        }
        return entries;
    }

    private int getIndex(String device) {
        for (int i = 0; i < devicesEn.length; i++) {
            if (devicesEn[i].equalsIgnoreCase(device)) {
                return i;
            }
        }
        return devicesEn.length;
    }
}
