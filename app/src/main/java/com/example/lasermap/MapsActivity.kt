package com.example.lasermap

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.lasermap.databinding.ActivityMapsBinding
import com.google.android.gms.common.api.Response
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import kotlin.concurrent.thread


// Gson schema for project

class DirectionResults {
    @SerializedName("routes")
    val routes: kotlin.collections.List<Route>? = null
}
class Route {
    @SerializedName("overview_polyline")
    val overviewPolyLine: OverviewPolyLine? = null
    val legs: kotlin.collections.List<Legs>? = null
}
class Legs {
    val steps: kotlin.collections.List<Steps>? = null
}
class Steps {
    val start_location: Location? = null
    val end_location: Location? = null
    val polyline: OverviewPolyLine? = null
}
class OverviewPolyLine {
    @SerializedName("points")
    var points: String? = null
}
class Location {
    val lat = 0.0
    val lng = 0.0
}


object RouteDecode {
    fun decodePoly(encoded: String): ArrayList<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            val position = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(position)
        }
        return poly
    }
}

class MapsActivity: AppCompatActivity(), OnMapReadyCallback {

    // Useful constants
    companion object {
        private const val MY_PERMISSIONS_REQUEST_LOCATION = 99
        private const val MY_PERMISSIONS_REQUEST_BACKGROUND_LOCATION = 66
    }

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    private val requestingLocationUpdates: Boolean = true
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // For location permission
    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = 30
        fastestInterval = 10
        priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        maxWaitTime = 60
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapFragment.getMapAsync(this)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker in Skoltech and move the camera
        val redSquare = LatLng(55.754491, 37.619303)
        val skoltech = LatLng(55.698598, 37.359529)
        mMap.addMarker(MarkerOptions().position(skoltech).title("Marker in Skoltech"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(skoltech))

        // Check if we can access user's location
        checkLocationPermission()
        val parameters = "origin=${skoltech.latitude},${skoltech.longitude}&"+
                "destination=${redSquare.latitude},${redSquare.longitude}&"+
                "mode=bicycling&key=${getResources().getString(R.string.google_maps_key)}"
        Log.d("link", "https://maps.googleapis.com/maps/api/directions/json?${parameters}")
        thread(start = true) {

            download("https://maps.googleapis.com/",
                skoltech, redSquare,
                applicationContext.filesDir)
        }

    }



    private fun download(base_url: String, fromPosition: LatLng, toPosition: LatLng, path: File) {
//        URL(link).openStream().use { input ->
//            FileOutputStream(File(path, "/directions.json")).use { output ->
//                input.copyTo(output)
//            }
//        }
//        val base_url = "http://maps.googleapis.com/"

        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()

        val retrofitBuilder = Retrofit.Builder()
            .baseUrl(base_url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val reqinterface = retrofitBuilder.create(Api::class.java)

        reqinterface.getJson(
            "${fromPosition.latitude},${fromPosition.longitude}",
            "${toPosition.latitude},${toPosition.longitude}",
            getResources().getString(R.string.google_maps_key)
        )?.enqueue(
            object: Callback<DirectionResults?> {
                override fun onResponse(
                    call: Call<DirectionResults?>,
                    directionResults: retrofit2.Response<DirectionResults?>
                ) {
                    Log.d("snet", call.request().toString())
                    Log.d("err", directionResults.errorBody().toString())
                    Log.d("results", directionResults.message().toString())
                    Log.i("zacharia", "inside on success " + directionResults.body()!!.routes!!.size)
                    val routelist = ArrayList<LatLng>()
                    if (directionResults.body()!!.routes!!.isNotEmpty()) {
                        var decodelist: ArrayList<LatLng>
                        val routeA = directionResults.body()!!.routes!![0]
                        Log.i("zacharia", "Legs length : " + routeA.legs!!.size)
                        if (routeA.legs.isNotEmpty()) {
                            val steps = routeA.legs[0].steps
                            Log.i("zacharia", "Steps size :" + steps!!.size)
                            var step: Steps
                            var location: Location?
                            var polyline: String?
                            for (i in steps.indices) {
                                step = steps[i]
                                location = step.start_location
                                routelist.add(LatLng(location!!.lat, location.lng))
                                Log.i("zacharia", "Start Location :" + location.lat + ", " + location.lng)
                                polyline = step.polyline!!.points
                                decodelist = RouteDecode.decodePoly(polyline!!)
                                routelist.addAll(decodelist)
                                location = step.end_location
                                routelist.add(LatLng(location!!.lat, location.lng))
                                Log.i("zacharia", "End Location :" + location.lat + ", " + location.lng)
                            }
                        }
                    }
                    Log.i("zacharia", "routelist size : " + routelist.size)
                    if (routelist.size > 0) {
                        val rectLine = PolylineOptions().width(10f).color(
                            Color.RED
                        )
                        for (i in routelist.indices) {
                            rectLine.add(routelist[i])
                        }
//                        // Adding route on the map
//                        mMap.addPolyline(rectLine)
//                        markerOptions.position(toPosition)
//                        markerOptions.draggable(true)
//                        mMap.addMarker(markerOptions)
                    }
                }

                override fun onFailure(call: Call<DirectionResults?>, t: Throwable) {
                    Log.d("onFailure", t.message!!)
                }
            })
    }

    interface Api {
        @GET("/maps/api/directions/json")
        fun getJson(
            @Query("origin") origin: String?,
            @Query("destination") destination: String?,
            @Query("key") key: String?,
        ): Call<DirectionResults?>?
    }

    private var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val locationList = locationResult.locations
            if (locationList.isNotEmpty()) {
                //The last location in the list is the newest
                val location = locationList.last()
                Toast.makeText(
                    this@MapsActivity,
                    "Got Location: " + location.toString(),
                    Toast.LENGTH_LONG
                )
                    .show()
            }
        }
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs the Location permission, please accept to use location functionality")
                    .setPositiveButton(
                        "OK"
                    ) { _, _ ->
                        //Prompt the user once explanation has been shown
                        requestLocationPermission()
                    }
                    .create()
                    .show()
            } else {
                // No explanation needed, we can request the permission.
                requestLocationPermission()
            }
        } else {
            checkBackgroundLocation()
        }
    }

    private fun checkBackgroundLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBackgroundLocationPermission()
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            MY_PERMISSIONS_REQUEST_LOCATION
        )
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                MY_PERMISSIONS_REQUEST_BACKGROUND_LOCATION
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MY_PERMISSIONS_REQUEST_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        fusedLocationClient.requestLocationUpdates(
                            locationRequest,
                            locationCallback,
                            Looper.getMainLooper()
                        )

                        // Now check background location
                        checkBackgroundLocation()
                    }

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show()

                    // Check if we are in a state where the user has denied the permission and
                    // selected Don't ask again
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    ) {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", this.packageName, null),
                            ),
                        )
                    }
                }
                return
            }
            MY_PERMISSIONS_REQUEST_BACKGROUND_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        fusedLocationClient.requestLocationUpdates(
                            locationRequest,
                            locationCallback,
                            Looper.getMainLooper()
                        )

                        Toast.makeText(
                            this,
                            "Granted Background Location Permission",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show()
                }
                return

            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (requestingLocationUpdates) startLocationUpdates()
    }

    private fun startLocationUpdates() {

        fusedLocationClient.requestLocationUpdates(locationRequest,
            locationCallback,
            Looper.getMainLooper())
    }
}


