package com.ozzyozdil.javamap;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.room.Room;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.snackbar.Snackbar;
import com.ozzyozdil.javamap.databinding.ActivityMapsBinding;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;

    LocationManager locationManager;
    LocationListener locationListener;

    ActivityResultLauncher<String> permissionLauncher;

    SharedPreferences sharedPreferences;
    boolean lastLocationInfo;

    PlaceDatabase db;
    PlaceDao placeDao;

    Double selectedLatitude;
    Double selectedLongitude;

    Place selectedPlace;

    AlertDialog.Builder alert;

    // RxJava
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(MapsActivity.this);

        registerLauncher();

        sharedPreferences = MapsActivity.this.getSharedPreferences("com.ozzyozdil.javamaps", MODE_PRIVATE);
        lastLocationInfo = false;

        db = Room.databaseBuilder(getApplicationContext(), PlaceDatabase.class, "Places").build();
        placeDao = db.placeDao();

        selectedLatitude = 0.0;
        selectedLongitude = 0.0;

    }

    // Harita haz??r oldu??unda ne olsun
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapLongClickListener(this);

        // Yeni yer mi eklenecek yoksa eklenmi?? yer mi a????lacak ona g??re xml i ??ekillendirmek i??in kontrol ediyoruz
        Intent intent = getIntent();
        String intentInfo = intent.getStringExtra("info");

        if (intentInfo.equals("new")){

            alert = new AlertDialog.Builder(this);
            alert.setTitle("??pucu");
            alert.setMessage("????aret b??rakmak i??in ekrana uzun bas??n??z");
            alert.show();

            binding.btnSave.setVisibility(View.VISIBLE);
            binding.btnDelete.setVisibility(View.GONE);  // GONE = kaplad?????? alan ile birlikte kald??r??r
                                                         // INVISIBLE = g??r??nmez olur ama h??la xml de yer kaplar

            // save butonu t??klanamaz oldu
            binding.btnSave.setEnabled(false);

            // Casting
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {

                    lastLocationInfo = sharedPreferences.getBoolean("info", false);
                    if (!lastLocationInfo){

                        LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15));
                        sharedPreferences.edit().putBoolean("info", true).apply();
                    }
                }
            };

            // Permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){

                    Snackbar.make(binding.getRoot(), "Konuma eri??im i??in izin gerekiyor.", Snackbar.LENGTH_INDEFINITE).setAction("??zin Ver", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {

                            // Request Permission
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                        }
                    }).show();
                }
                else{

                    // Request Permission
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                }
            }
            else{

                // Konumu al??r
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 2, locationListener);

                // Son bilinen konumu al??r ??rn(uygulama a????ld??????nda son bilinen konumdan ba??layacak)
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null){

                    LatLng lastUserLocation = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastUserLocation,15));
                }

                // Konumun a????k oldu??undan emin ol
                mMap.setMyLocationEnabled(true);
            }
        }
        else{

            alert = new AlertDialog.Builder(this);
            alert.setTitle("??pucu");
            alert.setMessage("K??rm??z?? i??arete t??klad??ktan sonra sa?? altta beliren butonlardan daha fazla i??lem yapabilirsiniz.");
            alert.show();

            mMap.clear();

            selectedPlace = (Place) intent.getSerializableExtra("place");
            LatLng latLng = new LatLng(selectedPlace.latitude, selectedPlace.longitude);

            mMap.addMarker(new MarkerOptions().position(latLng).title(selectedPlace.name));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));

            binding.etxtPlaceName.setText(selectedPlace.name);
            binding.btnSave.setVisibility(View.GONE);
            binding.btnDelete.setVisibility(View.VISIBLE);

        }

        // latitude     41.1944176
        // longitude    28.7209601
    }

    // Uzun t??kland??????nda ne olsun bunu yapmadan ??nce onMapReady de tan??ml??caz
    @Override
    public void onMapLongClick(@NonNull LatLng latLng) {

        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(latLng));

        selectedLatitude = latLng.latitude;
        selectedLongitude = latLng.longitude;

        // save butonu konum se??tikten sonra a????ld??
        binding.btnSave.setEnabled(true);
    }

    // OnClick
    public void save(View view) {

        // Room Database
        Place place = new Place(binding.etxtPlaceName.getText().toString(),selectedLatitude,selectedLongitude);
        // RxJava
        compositeDisposable.add(placeDao.insert(place)             // place i veritaban??na ekliyoruz
                .subscribeOn(Schedulers.io())                      // bu eklemeyi io thread (input/output) da yap
                .observeOn(AndroidSchedulers.mainThread())         // mainThread (Ana i?? par??ac??????) da g??zlemle/dinle
                .subscribe(MapsActivity.this :: handleResponse));  // subscribe = MapsActivity de  :: handleResponse metodunu ??al????t??rd??k
    }

    // OnClick
    public void delete(View view){

        if (selectedPlace != null){

            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setMessage("Bu kaydedilen konumu silmek istiyor musun?");

            alert.setPositiveButton("Evet", new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which){

                    // RxJava
                    compositeDisposable.add(placeDao.delete(selectedPlace)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(MapsActivity.this :: handleResponse));

                }
            });

            alert.setNegativeButton("Hay??r", new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which){
                    //save
                }
            });

            alert.show();

        }
    }

    // Cleaning Method
    private void handleResponse(){

        Intent intent = new Intent(MapsActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);  // T??m activity leri kapat??r
        startActivity(intent);
    }

    // Activity Result Launcher
    private void registerLauncher(){

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
            @Override
            public void onActivityResult(Boolean result) {

                if (result){

                    if (ContextCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){

                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 2, locationListener);

                        Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (lastLocation != null){

                            LatLng lastUserLocation = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastUserLocation, 15));
                        }
                    }
                }
                else{
                    Toast.makeText(MapsActivity.this, "Konum eri??imi gerekiyor!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}