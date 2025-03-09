package com.example.gpstracker03;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

// add this for ACCESS_FINE_LOCATION
import android.Manifest;
import android.widget.Toast;

// add this to launch permission requester
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import android.app.Activity;


public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_FINE_LOCATION = 99;
    private static final int UPDATE_INTERVAL_SLOW = 5*1000;
    private static final int UPDATE_INTERVAL_FAST = 1*1000;
    private static final double FEET_PER_METER = 3.28084;
    private static final double FEET_PER_MILE = 5280;

    private static final double METERS_PER_MILE = FEET_PER_MILE / FEET_PER_METER;  //1609.344 = 5280 / 3.28084

    private static final double SECONDS_PER_HOUR = 3600.0;

    // references to the UI elements
    TextView tv_lat, tv_lon, tv_speedMPS, tv_speedMPH;
    TextView tv_distanceMiles, tv_accuracy, tv_distanceFromStartMeters;

    // Googles location services
    FusedLocationProviderClient fusedLocationProviderClient;
    // Location config is a file for location service provider properties
    LocationRequest locationRequest;

    // Location callback
    LocationCallback locationCallback;

    Boolean isFirstGPSMeasurement = true;

    Location initialLocationMeasurement;

    float distanceFromStartMeters = 0;

    private Context context;
    SharedPreferences sharedPreferences;

    // location permission requester
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();

        // request location permissions
        checkLocationPermission();

        // give each UI variable a value
        tv_lat = findViewById(R.id.tv_lat);
        tv_lon = findViewById(R.id.tv_lon);
        tv_speedMPS = findViewById(R.id.tv_speedMPS);
        tv_speedMPH = findViewById(R.id.tv_speedMPH);
        tv_distanceMiles = findViewById(R.id.tv_distanceMiles);
        tv_distanceFromStartMeters = findViewById(R.id.tv_distanceMeters);
//        tv_distanceFromStartMeters = findViewById(R.id.tv_distanceFromStartMeters);
        tv_accuracy = findViewById(R.id.tv_accuracy);

        // set properties of the location request
        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL_FAST);
        locationRequest.setFastestInterval(UPDATE_INTERVAL_FAST);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // triggered whenever the update interval is met
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);

                Location location = locationResult.getLastLocation();
                updateUIValues(location);
            }
        };

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        updateGPS();
        startLocationUpdates();
    }    // end onCreate method

    private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        tv_lon.setText("Location tracking is OFF");
        tv_lat.setText("Location tracking is OFF");
        tv_speedMPS.setText("Location tracking is OFF");
    }

    private void startLocationUpdates() {
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
        updateGPS();
    }

    // launch the permission request if not granted
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            Toast.makeText(this, "Location Permission Already Granted", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults, int deviceId) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId);

        switch(requestCode) {
            case PERMISSIONS_FINE_LOCATION:
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    updateGPS();
                }
                else {
                    Toast.makeText( this, "app requires permission to use GPS", Toast.LENGTH_SHORT).show();
//                    finish();
                }
                break;
        }
    }

    // get permission from user to track GPS
    // get current location from the fused client
    // update the UI e.g. set all the properties and associated text views
    private void updateGPS() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MainActivity.this);
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)  {
            // user granted permission
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    // we got permission. Put the values into the UI views
                    updateUIValues(location);
                }
            });

        }
        else {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_FINE_LOCATION);
            }
        }
    }

    double   speedMPS;  // meters per second
    double   speedMPH;  // miles per hour
    double accuracy; // accuracty radius in meters
        double[] speedArray = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    int currentSpeedIndex = 0;
    double speedAverageMPH = 0;

    private void updateUIValues(Location location) {

//        sharedPreferences = context.getSharedPreferences("location",Context.MODE_PRIVATE);
//        Location myLocation = sharedPreferences.getString("location",null);
//        sharedPreferences.edit().putString("STRENGTH",myLocation).apply();

        accuracy = location.getAccuracy();
        tv_accuracy.setText(String.format("%1.2f", accuracy));

        if (accuracy < 20.0) {
            if (isFirstGPSMeasurement) {
                isFirstGPSMeasurement = false;
                distanceFromStartMeters = 0;
                initialLocationMeasurement = location;
/*
                if (location == null) {
                    sharedPreferences.edit().remove("LOCATION_LAT").apply();
                    sharedPreferences.edit().remove("LOCATION_LON").apply();
                    sharedPreferences.edit().remove("LOCATION_PROVIDER").apply();
                } else {
                    sharedPreferences.edit().putString("LOCATION_LAT", String.valueOf(location.getLatitude())).apply();
                    sharedPreferences.edit().putString("LOCATION_LON", String.valueOf(location.getLongitude())).apply();
                    sharedPreferences.edit().putString("LOCATION_PROVIDER", location.getProvider()).apply();
                }
*/
            } else {
/*
                String lat = sharedPreferences.getString("LOCATION_LAT", "");
                String lon = sharedPreferences.getString("LOCATION_LON", "");
                String provider = sharedPreferences.getString("LOCATION_PROVIDER", "");

                if (!lat.isEmpty() && !lon.isEmpty() && !provider.isEmpty()) {
//                    initialLocationMeasurement = new Location(provider);
                    initialLocationMeasurement.setLatitude(Double.parseDouble(lat));
                    initialLocationMeasurement.setLongitude(Double.parseDouble(lon));
                }
*/

                distanceFromStartMeters = location.distanceTo(initialLocationMeasurement);
            }

            tv_distanceFromStartMeters.setText(String.format("%1.2f", distanceFromStartMeters));
//            tv_distanceFromStartMeters.setText(String.valueOf(distanceFromStartMeters));

            double distanceMiles = distanceFromStartMeters / METERS_PER_MILE;
            tv_distanceMiles.setText(String.format("%1.4f", distanceMiles));

            tv_lat.setText(String.format("%1.2f", location.getLatitude()) );
            tv_lon.setText(String.format("%1.2f", location.getLongitude()) );
            if (location.hasSpeed()) {
                speedMPS = location.getSpeed(); // * FEET_PER_METER;
                speedMPH = location.getSpeed() * (SECONDS_PER_HOUR / METERS_PER_MILE);

                speedArray[currentSpeedIndex] = speedMPH;
                if (++currentSpeedIndex > 9) currentSpeedIndex = 0;

                double avgSpeed = 0;
                double speedTotal = 0;

                for (int i = 0; i < 10; i++) {
                    speedTotal += speedArray[i];
                }

                avgSpeed = speedTotal / 10;

                tv_speedMPS.setText(String.format("%1.2f", speedMPS));
//            tv_speedMPS.setText(String.valueOf(speedMPH));
                tv_speedMPH.setText(String.format("%1.2f", avgSpeed));

                //            tv_speed.setText(String.valueOf(location.getSpeed()));
            } else {
                tv_speedMPS.setText("0.00");
                tv_speedMPH.setText("0.00");
                currentSpeedIndex = 0;  // start over
                for (int i = 0; i < 10; i++) speedArray[i] = 0.0;
            }

            tv_distanceFromStartMeters.setText(String.format("%1.2f", distanceFromStartMeters));
        }
    }
}