package com.example.navver;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;

public class SecondFragment extends Fragment {
    private LocationManager locationManager;
    private WifiManager wifiManager;
    private WebView map;

    private boolean isFirstRun = true;

    public SecondFragment() {
        // require a empty public constructor
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (isFirstRun) {
            SharedPreferences logger = getSafeContext().getSharedPreferences("logs", MODE_PRIVATE);
            logger.edit().remove("content").commit();

            if (ContextCompat.checkSelfPermission(getSafeContext(), Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                Helper.add_log_line(getSafeContext(), "ACCESS WIFI permission missing");

            } else {
                Helper.add_log_line(getSafeContext(), "ACCESS WIFI STATE enabled");
            }
            if (ContextCompat.checkSelfPermission(getSafeContext(), Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                Helper.add_log_line(getSafeContext(), "CHANGE WIFI permission missing");
            } else {
                Helper.add_log_line(getSafeContext(), "CHANGE WIFI STATE enabled");
            }
            if (ContextCompat.checkSelfPermission(getSafeContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Helper.add_log_line(getSafeContext(), "ACCESS FINE permission missing");
            } else {
                Helper.add_log_line(getSafeContext(), "ACCESS FINE LOCATION enabled");
            }

            isFirstRun = false;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        locationManager = (LocationManager) getSafeContext().getSystemService(Context.LOCATION_SERVICE);

        return inflater.inflate(R.layout.fragment_second, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button current_location = (Button) getView().findViewById(R.id.current_location);
        Button search_button = (Button) getView().findViewById(R.id.search);

        current_location.setOnClickListener(v -> {
            setLocation();
        });

        search_button.setOnClickListener(v -> {
            if (!validate_locations()) {
                Toast.makeText(getSafeContext(), "Resolving addresses, try again!", Toast.LENGTH_SHORT).show();
            }

            EditText source = (EditText) getView().findViewById(R.id.starting_point_xy);
            EditText destination = (EditText) getView().findViewById(R.id.ending_point_xy);

            String source_coordinate = source.getText().toString();
            String destination_coordinate = destination.getText().toString();

            map = getView().findViewById(R.id.map);
            map.getSettings().setJavaScriptEnabled(true);
            map.setWebViewClient(new WebViewClient());
            map.loadUrl("https://www.openstreetmap.org/directions?engine=fossgis_osrm_foot&route=" +
                    source_coordinate + ";" + destination_coordinate);
        });

        EditText starting_text_editText = (EditText) getView().findViewById(R.id.starting_point_text);
        EditText starting_xy_editText = (EditText) getView().findViewById(R.id.starting_point_xy);
        Handler starting_handler = new Handler();

        starting_text_editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    if(validateContext()) return;
                    // If EditText lost focus, start a delay to execute code after user finished typing
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            if(validateContext()) return;
                            setStreetName(starting_text_editText, starting_xy_editText);
                        }
                    };
                    starting_handler.postDelayed(runnable, 0); // Adjust the delay as needed
                }
            }
        });

        EditText ending_text_editText = (EditText) getView().findViewById(R.id.ending_point_text);
        EditText ending_xy_editText = (EditText) getView().findViewById(R.id.ending_point_xy);
        Handler ending_handler = new Handler();

        ending_text_editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    if(validateContext()) return;
                    // If EditText lost focus, start a delay to execute code after user finished typing
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            if(validateContext()) return;
                            setStreetName(ending_text_editText, ending_xy_editText);
                        }
                    };
                    ending_handler.postDelayed(runnable, 0); // Adjust the delay as needed
                }
            }
        });


    }

    private void setStreetName(EditText street_editText, EditText xy_editText) {
        String raw_street_name = street_editText.getText().toString();
        Helper.add_log_line(getSafeContext(), "trying to translate " + raw_street_name);


        RequestQueue queue = Volley.newRequestQueue(getSafeContext());
        String url = "http://nogpsnavigator.top/geocoding/" + raw_street_name;
        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        if(validateContext()) return;
                        JSONObject jsonObject = new JSONObject(response);
                        if (jsonObject.getString("address").equals("unknown")) {
                            street_editText.setText("Address not found... Try again");
                            Helper.add_log_line(getSafeContext(), "failed setting street name");
                        } else {
                            street_editText.setText(jsonObject.getString("address"));
                            xy_editText.setText(jsonObject.getString("lating"));
                            Helper.add_log_line(getSafeContext(), "success in setting street name");
                        }

                    } catch (JSONException e) {
                        Toast.makeText(getSafeContext(), response, Toast.LENGTH_SHORT).show();
                        street_editText.setText("Address not found... Try again");
                        Helper.add_log_line(getSafeContext(), "failed setting street name: " + e.toString());
                    }

                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if(validateContext()) return;
                street_editText.setText("Address not found... Try again");
                String description = Helper.volley_error_description(error);
                Helper.add_log_line(getSafeContext(), "failed setting street name: " + description);
            }

        });

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    private void setLocation() {
        try {
            Helper.add_log_line(getSafeContext(), "trying to get wifi coordinates");
            wifi_coordinates();
            Helper.add_log_line(getSafeContext(), "success in getting wifi coordinates");
        } catch (Exception e) {
            Helper.add_log_line(getSafeContext(), "error in WIFI API: " + e.getMessage());
            try {
                Helper.add_log_line(getSafeContext(), "trying to get cellular coordinates [Normal flow]");
                cellular_coordinates();
            } catch (Exception e2) {
                Helper.add_log_line(getSafeContext(), "error in Cellular API: " + e2.getMessage());
            }
        }
    }

    private void wifi_coordinates() throws Exception {
        JSONArray wifi = scanWifiNetworks();
        RequestQueue queue = Volley.newRequestQueue(getSafeContext());
        String url = "http://nogpsnavigator.top/wifi";
        // Request a string response from the provided URL.
        // Request a JSONObject response from the provided URL
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.POST, url, wifi,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        // Handle response
                        try {
                            if(validateContext()) return;
                            JSONObject server_response = response.getJSONObject(0);
                            if (!server_response.getBoolean("isValid")) {
                                Helper.add_log_line(getSafeContext(), server_response.getString("error"));
                                Toast.makeText(getSafeContext(), "Failed getting location by wifi", Toast.LENGTH_SHORT).show();
                                throw new JSONException("Failed to get location by wifi");
                            }
                            JSONObject inner_location = server_response.getJSONObject("location");

                            String lating = inner_location.getString("latitude") + ", " + inner_location.getString("longitude");
                            Helper.add_log_line(getSafeContext(), "location from wifi: " + lating);
                            translate_coordinates(lating);

                        } catch (JSONException e) {
                            if(validateContext()) return;
                            Toast.makeText(getSafeContext(), e.toString(), Toast.LENGTH_SHORT).show();
                            Helper.add_log_line(getSafeContext(), "error in wifi API, fallback to cellular location (" + e.toString() + ")");
                            cellular_coordinates();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // Handle error
                if(validateContext()) return;
                String description = Helper.volley_error_description(error);
                Helper.add_log_line(getSafeContext(), "error in wifi API, fallback to cellular location (" + description + ")");
                cellular_coordinates();
            }
        });

        // Add the request to the RequestQueue
        queue.add(jsonArrayRequest);
    }


    public JSONArray scanWifiNetworks() throws Exception {


        wifiManager = (WifiManager) getSafeContext().getSystemService(Context.WIFI_SERVICE);

        // Ensure Wi-Fi is enabled
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }

        // Start Wi-Fi scan
        wifiManager.startScan();

        if (ActivityCompat.checkSelfPermission(getSafeContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            throw new Exception("FINE LOCATION Permission missing");
        }
        List<ScanResult> scanResults = wifiManager.getScanResults();
        JSONArray networksArray = new JSONArray();

        for (ScanResult result : scanResults) {
            JSONObject network = new JSONObject();
            network.put("ssid", result.SSID);
            network.put("macAddress", result.BSSID);
            networksArray.put(network);
        }

        if (networksArray.length() == 0) {
            throw new JSONException("Empty array");
        }
        Helper.add_log_line(getSafeContext(), "number of wifi scanned: " + String.valueOf(networksArray.length()));


        return networksArray;
    }

    private void cellular_coordinates() {
        Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        String coordinates = "";

        if (location != null) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            coordinates = latitude + ", " + longitude;
            translate_coordinates(coordinates);

        } else {
            coordinates = "Error fetching location";
        }

        EditText source = (EditText) getView().findViewById(R.id.starting_point_xy);
        source.setText(coordinates);
    }

    private void translate_coordinates(String coordinates) {
        Helper.add_log_line(getSafeContext(), "trying to reverse geocoding " + coordinates);

        RequestQueue queue = Volley.newRequestQueue(getSafeContext());
        String url = "http://nogpsnavigator.top/reverse-geocoding/" + coordinates;

        // set coordinates at xy box
        EditText source_xy = (EditText) getView().findViewById(R.id.starting_point_xy);
        source_xy.setText(coordinates);

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    if(validateContext()) return;
                    EditText source_text = (EditText) getView().findViewById(R.id.starting_point_text);
                    source_text.setText(response.replaceAll("\"", ""));
                    Helper.add_log_line(getSafeContext(), "success in reverse geocoding to " + response);
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if(validateContext()) return;
                Toast.makeText(getSafeContext(), "failed translate coordinates, Try again!", Toast.LENGTH_SHORT).show();

                String description = Helper.volley_error_description(error);
                Helper.add_log_line(getSafeContext(), "failed translate coordinates: " + description);

            }

        });

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    private boolean validate_locations() {
        boolean result = true;
        EditText source = (EditText) getView().findViewById(R.id.starting_point_xy);
        if (source.getText().toString().isEmpty()) {
            setLocation();
            result = false;
        }

        EditText destination_text = (EditText) getView().findViewById(R.id.ending_point_text);
        EditText destination_xy = (EditText) getView().findViewById(R.id.ending_point_xy);
        if (destination_xy.getText().toString().isEmpty()) {
            if (destination_text.getText().toString().isEmpty()) {
                destination_text.setText("Please type address");
            } else {
                setStreetName(destination_text, destination_xy);
                result = false;
            }
        }

        return result;
    }

    private Context getSafeContext() {
        return getContext();
    }

    private boolean validateContext() {
        if(getContext() == null) {
            Log.i("saved", "baby just saves");
        }
        return getContext() == null;
    }
}