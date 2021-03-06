package com.tuk.myapp

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_main2.*
import kotlinx.android.synthetic.main.popup_marketinfo.view.*


class MainActivity2 : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerClickListener {

    private lateinit var mMap: GoogleMap

    lateinit var btn_market_create : FloatingActionButton
    lateinit var btn_market_delete : FloatingActionButton
    lateinit var btn_market_register : FloatingActionButton
    lateinit var btn_market_show : FloatingActionButton
    lateinit var fab_main : FloatingActionButton
    lateinit var fv_button: FloatingActionButton
    lateinit var btnToResearch : Button

    private lateinit var listView: ListView
    private lateinit var editTextName: EditText
    private lateinit var editTextNumber: EditText
    private lateinit var buttonAdd: Button
    private var dataArrayList: ArrayList<String>? = null
    private var adapter: ArrayAdapter<String>? = null
    private lateinit var prefe1: SharedPreferences


    var db: FirebaseFirestore = FirebaseFirestore.getInstance()
    var st: FirebaseStorage = FirebaseStorage.getInstance() // ?????????????????? ???????????? ?????? ?????????


    private var isFabOpen = false // Fab ?????? default??? ????????????

    private var isMarkerMake = false // Fab ?????? default??? ????????????

    private var isPositionMake = false // Fab ?????? default??? ????????????

    //????????? ?????? ?????????
    var markerList = ArrayList<MyModel>()


    //?????????
    lateinit var createIntent: Intent
    lateinit var fvIntent: Intent


    var current_position : LatLng? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        createIntent = Intent(applicationContext, CreateActivity::class.java)


        val mapFragment: SupportMapFragment = supportFragmentManager.findFragmentById(R.id.mapview) as SupportMapFragment
        mapFragment.getMapAsync(this)

        var current_marker : Marker? = null


        btn_market_create = findViewById(R.id.marker_create)
        btn_market_delete = findViewById(R.id.marker_delete)
        btn_market_register = findViewById(R.id.marker_register)
        btn_market_show = findViewById(R.id.marker_show)

        fab_main = findViewById(R.id.fab_main)
        fv_button=findViewById(R.id.fab_fv)
        btnToResearch = findViewById(R.id.btnToResearch)

//        editTextName = findViewById(R.id.edtTxt_addName_actvtMain)

        //??????(?????? ???????????????)?????? ??????
        saveMarkerData()

        reloadMarker()

        //????????? ??????
        fab_main.setOnClickListener {
            toggleFab()
        }
        fv_button.setOnClickListener {
            fvIntent= Intent(this,MainActivity3::class.java)
            startActivity(fvIntent)

        }


        // ????????? ?????? ?????? ????????? - ?????? ??????
        btn_market_create.setOnClickListener {
            if((isMarkerMake == false)&& (current_position != null)){
                current_marker = mMap.addMarker(
                    MarkerOptions()
                        .position(current_position!!)
                        .flat(true)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker1))
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
        btn_market_delete.setOnClickListener {
            if(isMarkerMake == true) {
                current_marker?.remove()
                isMarkerMake = !isMarkerMake
            }
            else{
                Toast.makeText(this, "????????? ???????????? ???????????????.", Toast.LENGTH_SHORT).show()
            }
        }

        btn_market_register.setOnClickListener{
            if(isMarkerMake == true){
                startActivity(createIntent)
            }
            else {
                Toast.makeText(this, "????????? ???????????? ???????????????.", Toast.LENGTH_SHORT).show()
            }
        }

        btn_market_show.setOnClickListener{
            reloadMarker()
            Toast.makeText(this,"?????? ???????????? ?????? !",Toast.LENGTH_SHORT).show()
        }

        //?????? ???????????? ???????????? ??????
        btnToResearch.setOnClickListener{
            var intentToResearch = Intent(applicationContext, MainActivity::class.java)
            startActivity(intentToResearch)
        }



    }

    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap
        val marker = LatLng(35.241615, 128.695587)
        mMap.addMarker(
            MarkerOptions()
                .position(marker)
                .flat(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker1))
        )

        mMap.setOnMapClickListener(this)
        mMap.setOnMarkerClickListener(this)

        mMap.moveCamera(CameraUpdateFactory.newLatLng(marker))
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker, 16F))

    }
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

        viewPopup.favorite_check.setOnClickListener {
            fvIntent= Intent(this,MainActivity3::class.java)
            fvIntent.putExtra("title",viewPopup.store_title_name.text.toString())
            fvIntent.putExtra("content",viewPopup.store_information.text.toString())
            startActivity(fvIntent)
//            Toast.makeText(this, "?????????????????????.",Toast.LENGTH_SHORT).show()
        }

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
            this@MainActivity2.markerList.add(myModel)
        }
    }



    private fun toggleFab() {
        // ????????? ?????? ?????? ?????? - ???????????? ????????? ?????? ???????????? ???????????????
        if (isFabOpen) {
            ObjectAnimator.ofFloat(btn_market_create, "translationY", 0f).apply { start() }
            ObjectAnimator.ofFloat(btn_market_delete, "translationY", 0f).apply { start() }
            ObjectAnimator.ofFloat(btn_market_register, "translationY", 0f).apply { start() }
            ObjectAnimator.ofFloat(btn_market_show, "translationY", 0f).apply { start() }
            ObjectAnimator.ofFloat(fab_main, View.ROTATION, 45f, 0f).apply { start() }
        } else { // ????????? ?????? ?????? ?????? - ???????????? ????????? ?????? ????????? ???????????????
            ObjectAnimator.ofFloat(btn_market_create, "translationY", -640f).apply { start() }
            ObjectAnimator.ofFloat(btn_market_delete, "translationY", -480f).apply { start() }
            ObjectAnimator.ofFloat(btn_market_register, "translationY", -320f).apply { start() }
            ObjectAnimator.ofFloat(btn_market_show, "translationY", -160f).apply { start() }
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
                        mMap.addMarker(
                            MarkerOptions()
                                .position(dbMarkerPosition!!)
                                .title(document.get("storeName").toString())
                                .flat(true)
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker1))
                        )
                    }
                }
            }
    }


//    private fun moveCamera(map: GoogleMap, marker: Marker) {
//        map.animateCamera(
//            CameraUpdateFactory.newLatLngZoom(
//                LatLng(
//                    marker.position.latitude,
//                    marker.position.longitude
//                ), 16f
//            )
//        )
//        marker.showInfoWindow()
//    }


}