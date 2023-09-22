package com.example.lasermap

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.io.IOException
import java.io.OutputStream
import java.util.*
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    // Useful constants
    companion object {
        private const val TAG = "LaserMaps"
        private const val MY_PERMISSIONS_REQUEST_LOCATION = 99
        private const val MY_PERMISSIONS_REQUEST_BACKGROUND_LOCATION = 66
        private const val MY_PERMISSIONS_REQUEST_BLUETOOTH = 77
        private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private lateinit var coordList: MutableList<LatLng>

    // TODO just for debug
    var appendTime = true

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    private val requestingLocationUpdates: Boolean = true
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var outputStream: OutputStream
    private lateinit var bluetoothSocket: BluetoothSocket

    // For location permission
    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = 30
        fastestInterval = 10
        priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        maxWaitTime = 60
    }

    private var BTValue = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkBluetoothPermission()

        // hardcode the needed device
//        val macAddress = "98:DA:60:04:6B:12"
//        val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
//        val pairedDevices: Set<BluetoothDevice> = bluetoothManager.adapter.getBondedDevices()
//        val bluetoothDevice = bluetoothManager.adapter.getRemoteDevice(macAddress);

//        Thread {
//            checkBluetoothPermission()
//            try {
//                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid)
//                bluetoothManager.adapter.cancelDiscovery()
//                bluetoothSocket.connect()
//                outputStream = bluetoothSocket.getOutputStream()
//                Log.e("Message", "Connected to HC-06")
//                runOnUiThread {
//                    Toast.makeText(this@MapsActivity, "Bluetooth successfully connected", Toast.LENGTH_LONG).show()
//                }
//            } catch (e: IOException) {
//                Log.e("Message", "Turn on bluetooth and restart the app")
//                runOnUiThread {
//                    Toast.makeText(
//                        this@MapsActivity,
//                        "Turn on bluetooth and restart the app",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//                throw RuntimeException(e)
//            }
//        }.start()

        mapFragment.getMapAsync(this)
    }

    fun checkBluetoothPermission() {
        if ((ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED) ||
            (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) ||
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
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
                        requestBluetoothConnection()
                    }
                    .create()
                    .show()
            } else {
                // No explanation needed, we can request the permission.
                requestBluetoothConnection()
            }
        }
    }

    private fun requestBluetoothConnection() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            ),
            MY_PERMISSIONS_REQUEST_BLUETOOTH
        )
    }

    private fun sendCommand(value: Int) {
        try {
            var tmp = "${value}\n" // I have to make a string, or it will not work
            outputStream.write(tmp.toByteArray())
            Log.d("Command -> ", value.toString())
        } catch (e: IOException) {
            throw java.lang.RuntimeException(e)
        }
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
        mMap.moveCamera(CameraUpdateFactory.zoomTo(19f))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(skoltech))
        mMap.setOnMapClickListener {
            if (appendTime) {
                if (::coordList.isInitialized) {
                    coordList += it
                }
                else {
                    coordList = mutableListOf(it)
                }

                if (coordList.size >= 5) {
                    appendTime = false
                    BTValue = calcAngle(it.latitude, it.longitude,
                                        coordList[0].latitude, coordList[0].longitude)
                    val polyline1 = googleMap.addPolyline(PolylineOptions()
                        .clickable(true)
                        .addAll(coordList))
                    polyline1.isVisible = true
                }
            }
            else {
                Log.i(TAG, distance(it.latitude, it.longitude, coordList[0]).toString())
                if (distance(it.latitude, it.longitude, coordList[0]) < 5) {
                    coordList.removeAt(0)
                    Toast.makeText(this@MapsActivity,
                        "angle: ${BTValue}",
                        Toast.LENGTH_LONG).show()

                    if (coordList.size > 0) {
                        BTValue = calcAngle(it.latitude, it.longitude,
                                            coordList[0].latitude, coordList[0].longitude)
                    }
                    else {
                        appendTime = true
                    }
                }
                else {
                    Toast.makeText(this@MapsActivity,
                        "angle: ${calcAngle(it.latitude, it.longitude,
                            coordList[0].latitude, coordList[0].longitude)}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
        checkLocationPermission()
    }

    fun distance(point1x: Double, point1y: Double, point2: LatLng): Double {
        return sqrt(((point1x - point2.latitude) * 10000).pow(2.0) +
                       ((point1y - point2.longitude) * 10000).pow(2.0)
        )
    }

    fun calcAngle(point1x: Double, point1y: Double, point2x: Double, point2y: Double): Int {
        // It keeps the direction too the north
        var theta = Math.atan2(point2y - point1y, point2x - point1x)
        theta += Math.PI
        var angle = Math.toDegrees(theta)
        if (angle < 0) {
            angle += 360
        }
        return round(angle).toInt()
    }

    private var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val locationList = locationResult.locations
            if (locationList.isNotEmpty()) {
                //The last location in the list is the newest
                val location = locationList.last()
//                Toast.makeText(
//                    this@MapsActivity,
//                    "Got Location: " + location.toString(),
//                    Toast.LENGTH_LONG
//                ).show()
//                Log.i(TAG, distance(location.latitude, location.longitude, coordList[0]).toString())
//                if (distance(location.latitude, location.longitude, coordList[0]) < 5) {
//                    coordList.removeAt(0)
//                    Toast.makeText(this@MapsActivity,
//                        "angle: ",
//                        Toast.LENGTH_LONG).show()
//
//                }
            }
            if (::outputStream.isInitialized)  {
                sendCommand(BTValue)
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
            MY_PERMISSIONS_REQUEST_BLUETOOTH -> {

            }
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
        checkLocationPermission()
        fusedLocationClient.requestLocationUpdates(locationRequest,
            locationCallback,
            Looper.getMainLooper())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close()
                Log.d(TAG, "Connection closed")
            } catch (e: IOException) {
                Log.d(TAG, "Error while closing the connection")
            }
        }
    }
}
