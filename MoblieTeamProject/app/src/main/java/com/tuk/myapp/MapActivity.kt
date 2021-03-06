package com.tuk.myapp

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.tuk.myapp.databinding.ActivityMapBinding
import kotlinx.android.synthetic.main.activity_map.*
import kotlinx.android.synthetic.main.activity_map.fab_main
import kotlinx.android.synthetic.main.popup_marketinfo.view.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class MapActivity : AppCompatActivity(), OnMapReadyCallback, CoroutineScope, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerClickListener {

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job


    private lateinit var binding: ActivityMapBinding
    private lateinit var map: GoogleMap
    private var currentSelectMarker: Marker? = null

    private lateinit var searchResult: SearchResultEntity

    private lateinit var locationManager: LocationManager // ??????????????? ?????? ???????????? ????????? ??? ??????????????? ?????? ?????????

    private lateinit var myLocationListener: MyLocationListener // ?????? ????????? ????????? ?????????

    companion object {
        const val SEARCH_RESULT_EXTRA_KEY: String = "SEARCH_RESULT_EXTRA_KEY"
        const val CAMERA_ZOOM_LEVEL = 17f
        const val PERMISSION_REQUEST_CODE = 2021
    }

    //----------------------------------------------------------------

    var db: FirebaseFirestore = FirebaseFirestore.getInstance()
    var st: FirebaseStorage = FirebaseStorage.getInstance() // ?????????????????? ???????????? ?????? ?????????

    private var isFabOpen = false // Fab ?????? default??? ????????????

    private var isMarkerMake = false // Fab ?????? default??? ????????????
    //????????? ?????? ?????????
    var markerList = ArrayList<MyModel>()


    //?????????
    lateinit var createIntent: Intent

    var current_position : LatLng? = null


    //----------------------------------------------------------------


    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)



        job = Job()

        if (::searchResult.isInitialized.not()) {
            intent?.let {
                searchResult = it.getParcelableExtra<SearchResultEntity>(SEARCH_RESULT_EXTRA_KEY)
                    ?: throw Exception("???????????? ???????????? ????????????.")
                setupGoogleMap()
                set_location.setText(searchResult.fullAddress+" ???")
            }

        }

        bindViews()


        //----------------------------------------------------------------
        createIntent = Intent(applicationContext, CreateActivity::class.java)

        var current_marker : Marker? = null

        //??????(?????? ???????????????)?????? ??????
        saveMarkerData()

        reloadMarker()

        //????????? ??????
        binding.fabMain.setOnClickListener {
            toggleFab()
        }

        // ????????? ?????? ?????? ????????? - ?????? ??????
        binding.markerCreate.setOnClickListener {
            if((isMarkerMake == false)&& (current_position != null)){
                current_marker = map.addMarker(
                    MarkerOptions()
                        .position(current_position!!)
                        .title(markerList[1].marketMenu)
                        .draggable(true)
                        .flat(true)
                )!!
                isMarkerMake = !isMarkerMake
                //????????? ????????? ?????????(?????? ??????)
                createIntent.putExtra("lat", current_position!!.latitude.toString())
                createIntent.putExtra("lon", current_position!!.longitude.toString())
            }
            else {
                Toast.makeText(this, "?????? ????????? ????????????????????? ?????? ????????? ?????? ???????????????.", Toast.LENGTH_SHORT).show()
            }

        }

        // ????????? ?????? ?????? ????????? - ?????? ??????
        binding.markerDelete.setOnClickListener {
            if(isMarkerMake == true) {
                current_marker?.remove()
                isMarkerMake = !isMarkerMake
            }
            else{
                Toast.makeText(this, "????????? ???????????? ???????????????.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.markerRegister.setOnClickListener{
            if(isMarkerMake == true){
                startActivity(createIntent)
            }
            else {
                Toast.makeText(this, "????????? ???????????? ???????????????.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.markerShow.setOnClickListener{
            reloadMarker()
            Toast.makeText(this,"?????? ???????????? ?????? !",Toast.LENGTH_SHORT).show()
        }

        //?????? ???????????? ???????????? ??????
        binding.btnToResearch.setOnClickListener{
            var intentToResearch = Intent(applicationContext, MainActivity::class.java)
            startActivity(intentToResearch)
        }


        //----------------------------------------------------------------




    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun bindViews() = with(binding) {
        // ?????? ?????? ?????? ?????????
        currentLocationButton.setOnClickListener {
            binding.progressCircular.isVisible = true
            getMyLocation()

        }
    }


    private fun setupGoogleMap() {
        val mapFragment =
            supportFragmentManager.findFragmentById(binding.mapFragment.id) as SupportMapFragment
        mapFragment.getMapAsync(this) // callback ?????? (onMapReady)

        // ?????? ????????? ????????????

    }

    override fun onMapReady(map: GoogleMap) {
        this.map = map
        currentSelectMarker = setupMarker(searchResult)

        currentSelectMarker?.showInfoWindow()

        map.setOnMapClickListener(this@MapActivity)
        map.setOnMarkerClickListener(this@MapActivity)
    }

    private fun setupMarker(searchResult: SearchResultEntity): Marker? {

        // ????????? ?????? ??????/?????? ??????
        val positionLatLng = LatLng(
            searchResult.locationLatLng.latitude.toDouble(),
            searchResult.locationLatLng.longitude.toDouble()
        )

        // ????????? ?????? ?????? ??????
        val markerOptions = MarkerOptions().apply {
            position(positionLatLng)
            title(searchResult.name)
            //  snippet(searchResult.fullAddress)
        }

        // ????????? ??? ??????
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(positionLatLng, CAMERA_ZOOM_LEVEL))

        return map.addMarker(markerOptions)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getMyLocation() {
        // ?????? ????????? ?????????
        if (::locationManager.isInitialized.not()) {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }

        // GPS ?????? ????????????
        val isGpsEnable = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        // ?????? ??????
        if (isGpsEnable) {
            when {
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) && shouldShowRequestPermissionRationale(
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) -> {
                    showPermissionContextPop()
                }

                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED -> {
                    makeRequestAsync()
                }

                else -> {
                    setMyLocationListener()
                }
            }
        }
    }

    private fun showPermissionContextPop() {
        AlertDialog.Builder(this)
            .setTitle("????????? ???????????????.")
            .setMessage("??? ????????? ?????????????????? ????????? ???????????????.")
            .setPositiveButton("??????") { _, _ ->
                makeRequestAsync()
            }
            .create()
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun setMyLocationListener() {
        val minTime = 3000L // ?????? ????????? ??????????????? ????????? ?????? ??????
        val minDistance = 100f // ?????? ?????? ??????

        // ???????????? ????????? ?????????
        if (::myLocationListener.isInitialized.not()) {
            myLocationListener = MyLocationListener()
        }

        // ?????? ?????? ???????????? ??????
        with(locationManager) {
            requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                minTime,
                minDistance,
                myLocationListener
            )
            requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                minTime,
                minDistance,
                myLocationListener
            )
        }
    }

    private fun onCurrentLocationChanged(locationLatLngEntity: LocationLatLngEntity) {
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(
                    locationLatLngEntity.latitude.toDouble(),
                    locationLatLngEntity.longitude.toDouble()
                ), CAMERA_ZOOM_LEVEL
            )
        )

        loadReverseGeoInformation(locationLatLngEntity)
        removeLocationListener() // ?????? ????????? ?????? ????????? ???????????? ?????? ???????????? ??????
    }

    private fun loadReverseGeoInformation(locationLatLngEntity: LocationLatLngEntity) {
        // ????????? ??????
        launch(coroutineContext) {
            try {
                binding.progressCircular.isVisible = true

                // IO ??????????????? ?????? ????????? ?????????
                withContext(Dispatchers.IO) {
                    val response = RetrofitUtil.apiService.getReverseGeoCode(
                        lat = locationLatLngEntity.latitude.toDouble(),
                        lon = locationLatLngEntity.longitude.toDouble()
                    )
                    if (response.isSuccessful) {

                        val body = response.body()

                        // ?????? ????????? ?????? UI ??????????????? ??????
                        withContext(Dispatchers.Main) {
                            Log.e("list", body.toString())
                            body?.let {
                                currentSelectMarker = setupMarker(
                                    SearchResultEntity(
                                        fullAddress = it.addressInfo.fullAddress ?: "?????? ?????? ??????",
                                        name = "??? ??????",
                                        locationLatLng = locationLatLngEntity
                                    )
                                )
                                // ?????? ????????????
                                currentSelectMarker?.showInfoWindow()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MapActivity, "???????????? ???????????? ????????? ??????????????????.", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressCircular.isVisible = false
            }
        }
    }

    private fun removeLocationListener() {
        if (::locationManager.isInitialized && ::myLocationListener.isInitialized) {
            locationManager.removeUpdates(myLocationListener) // myLocationListener ??? ???????????? ???????????? ?????????
        }
    }

    // ?????? ?????? ?????? ??????
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    setMyLocationListener()
                } else {
                    Toast.makeText(this, "????????? ?????? ???????????????.", Toast.LENGTH_SHORT).show()
                    binding.progressCircular.isVisible = false
                }
            }
        }
    }

    private fun makeRequestAsync() {
        // ????????? ?????? ??????. ?????? ????????? ???????????? ????????????
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            PERMISSION_REQUEST_CODE
        )
    }


    //----------------------------------------------------------------
    override fun onMapClick(point: LatLng) {
        Toast.makeText(this, "??????????$point", Toast.LENGTH_SHORT).show()
        current_position = point


    }

    override fun onMarkerClick(p0: Marker): Boolean {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val viewPopup = inflater.inflate(R.layout.popup_marketinfo, null)

        db.collection("Stores").document(p0.position.latitude.toString()).get()
            .addOnSuccessListener { document ->
                if (document.data != null) {
                    viewPopup.store_information.setText(document.get("storeInfo") as String?)
                    viewPopup.store_title_name.setText(document.get("storeName") as String?)
                    viewPopup.store_payType.setText(document.get("storePayType") as String?)
                    viewPopup.store_type.setText(document.get("storeType") as String?)
                    viewPopup.day_text.setText(document.get("storeClosedDay") as String?)
                    viewPopup.menu_name.setText(document.get("storeMenu") as String?)


                    // storeName ???  ex. test111 ??? ??????????????? ???, ?????? ?????? ??? storeName???
                    var imgFileName = document.get("storeName").toString() + "_img"
                    var stRef = st.reference.child(document.get("storeName").toString()).child(imgFileName) // ???????????? ??????
                    stRef.downloadUrl.addOnCompleteListener(){
                        Glide.with(this /* context */)
                            .load(it.result)
                            .into(viewPopup.store_picture)
                    }




                    Toast.makeText(this,"????????????",Toast.LENGTH_SHORT).show()
                }
                else {
                    Toast.makeText(this,"????????? ?????? ??? ??????",Toast.LENGTH_SHORT).show()
                }
            }

        val alertDialog = AlertDialog.Builder(this).create()

        alertDialog.setView(viewPopup)
        alertDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        alertDialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        alertDialog.show()
        alertDialog.window!!.setLayout(800,1400)

        viewPopup.close_button.setOnClickListener {
            alertDialog.dismiss()
        }

        return true
    }

    //????????? ?????? ?????????, myModel ??? ??????????????????
    private fun saveMarkerData(){
        //?????? ??????????????? ?????? ???????????? ???????????????????????? ????????? ???????????? ?????? ???????????? ????????? ????????? ??? ??? ???
        for (i in 0..10) {
            val myModel = MyModel(
                positionInfo = "$i ?????? ??????",
                marketName = "$i ?????? ??????",
                marketInfo = "$i ?????? ??????",
                marketMenu = "$i ?????? ??????",
                marketTime = "$i ?????? ??????"
            )
            this@MapActivity.markerList.add(myModel)
        }
    }


    private fun toggleFab() {
        if (isFabOpen) {
            ObjectAnimator.ofFloat(binding.markerCreate, "translationY", 0f).apply { start() }
            ObjectAnimator.ofFloat(binding.markerDelete, "translationY", 0f).apply { start() }
            ObjectAnimator.ofFloat(binding.markerRegister, "translationY", 0f).apply { start() }
            ObjectAnimator.ofFloat(binding.markerShow, "translationY", 0f).apply { start() }
            ObjectAnimator.ofFloat(fab_main, View.ROTATION, 45f, 0f).apply { start() }
        } else { // ????????? ?????? ?????? ?????? - ???????????? ????????? ?????? ????????? ???????????????
            ObjectAnimator.ofFloat(binding.markerCreate, "translationY", -640f).apply { start() }
            ObjectAnimator.ofFloat(binding.markerDelete, "translationY", -480f).apply { start() }
            ObjectAnimator.ofFloat(binding.markerRegister, "translationY", -320f).apply { start() }
            ObjectAnimator.ofFloat(binding.markerShow, "translationY", -160f).apply { start() }
            ObjectAnimator.ofFloat(fab_main, View.ROTATION, 0f, 45f).apply { start() }
        }

        isFabOpen = !isFabOpen


    }

    private fun reloadMarker(){
        db.collection("Stores").get()
            .addOnSuccessListener { documents ->
                for(document in documents){
                    if (document.data != null) {
                        val dbMarkerPosition : LatLng = LatLng(document.get("storeLatValue").toString().toDouble(), document.get("storeLonValue").toString().toDouble())
                        map.addMarker(
                            MarkerOptions()
                                .position(dbMarkerPosition!!)
                                .title(document.get("storeName").toString())
                                .draggable(true)
                                .flat(true)
                        )
                    }
                }
            }
    }

    //----------------------------------------------------------------

    inner class MyLocationListener : LocationListener {
        override fun onLocationChanged(location: Location) {
            // ?????? ?????? ??????
            val locationLatLngEntity = LocationLatLngEntity(
                location.latitude.toFloat(),
                location.longitude.toFloat()
            )

            onCurrentLocationChanged(locationLatLngEntity)
        }

    }

}