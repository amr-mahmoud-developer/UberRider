package com.example.uberrider

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat.getRotation
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.uberrider.Common.riderKey
import com.example.uberrider.Model.*
import com.example.uberrider.databinding.FragmentHomeBinding
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.database.*
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import de.hdodenhof.circleimageview.CircleImageView
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.abs
import kotlin.math.atan


class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var panelTitle: TextView
    private lateinit var requestDriverView: View
    private lateinit var driverFinishedTrip: ValueEventListener
    private lateinit var driverStartTrip: ValueEventListener
    private lateinit var geoFire: GeoFire
    private lateinit var searchingDriverProgressDialog: AlertDialog
    private lateinit var searchingDriverTxt: TextView
    private lateinit var slideUpPanelView: FrameLayout
    private lateinit var slideUpPanelLayout: SlidingUpPanelLayout
    private lateinit var requestDriverBtn: Button
    var driverInfo: DriverInfoModel? = null
    private var added = false

    //marker reference to control drivers moving
    private lateinit var driverMarker: HashMap<String, MapMarkerModel>

    private var queryListenerSet = false
    private lateinit var mapFragment: SupportMapFragment

    private lateinit var mMap: GoogleMap

    //location Properties
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var geoQuery: GeoQuery


    //current rider position
    private lateinit var riderCurrentPos: LatLng

    //nearest driver variable
    var nearestDriverDist: Float? = null
    private lateinit var nearestDriverKey: String

    //trip captain key
    private lateinit var capTripKey: String
    private var isConfirmed = false


    private lateinit var driverConfirmListener: ValueEventListener


    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!


    //Register for Location Permission Activity
    @SuppressLint("MissingPermission")
    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { res ->
            // Handle Permission granted/rejected
            res.entries.forEach {
                if (it.value) {
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    fusedLocationProviderClient.lastLocation.addOnSuccessListener {
                        if (it != null) {
                            mMap.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(
                                        it.latitude,
                                        it.longitude
                                    ), 18f
                                )
                            )
                        } else return@addOnSuccessListener
                    }

                } else {
                    return@registerForActivityResult
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //initialize Slide Up Panel Layout  & Slide Up View
        slideUpPanelLayout = binding.slidingLayout
        slideUpPanelView = binding.slidePanelView
        requestDriverView = inflater.inflate(R.layout.request_driver_layout, null)
        slideUpPanelView.addView(requestDriverView)

        // Initialize the AutocompleteSupportFragment.
        val autocompleteFragment =
            childFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                    as AutocompleteSupportFragment
        // Specify the types of place data to return.
        autocompleteFragment.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG
            )
        )

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                Log.i("PlacesTest", "Place: ${place.name}, ${place.id}")
            }

            override fun onError(status: Status) {
                Log.i("PlacesTest", "An error occurred: $status")
            }
        })
        autocompleteFragment.setHint("Where To ?")



        init()
        return root
    }


    private fun init() {
        initDriverSearchProgress()

        //initialize driverMarker reference
        driverMarker = hashMapOf()


        // Initialize the places SDK
        Places.initialize(requireContext(), getString(R.string.api_key))

        // Create a new PlacesClient instance
        val placesClient = Places.createClient(requireContext())

        requestDriverBtn = slideUpPanelView.findViewById(R.id.request_driver_btn)


        //Location Initialize
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireActivity())
        locationRequest = LocationRequest.create()
        locationRequest.priority = Priority.PRIORITY_HIGH_ACCURACY
        locationRequest.setInterval(1000).setFastestInterval(500).setSmallestDisplacement(8f)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val latitude = locationResult.lastLocation!!.latitude
                val longitude = locationResult.lastLocation!!.longitude
                riderCurrentPos = LatLng(
                    latitude,
                    longitude
                )
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(riderCurrentPos, 18f))

                val cityName: String
                // get city name based on Location provided from fusedLocationProvider
                val geoCoder = Geocoder(context, Locale.getDefault())
                try {
                    val addressList = geoCoder.getFromLocation(
                        latitude,
                        longitude,
                        1
                    )
                    cityName = addressList.get(0).locality
                    val driverLocationRef =
                        FirebaseDatabase.getInstance()
                            .getReference(Common.diverLocationRef + "/${cityName}")

                    //get GeoFire object within driver location reference
                    geoFire = GeoFire(driverLocationRef)

                } catch (e: Exception) {
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()

                }



                if (!queryListenerSet) {
                    //associate query for specific current user location and radius around
                    geoQuery =
                        geoFire.queryAtLocation(GeoLocation(latitude, longitude), 1.0)
                    setGeoQueryListener()
                    queryListenerSet = true
                }


            }


        }
        requestDriverBtn.setOnClickListener {

            if (this::nearestDriverKey.isInitialized) {
                val request = RiderRequestModel(
                    Common.currentUser, Common.riderKey,
                    riderCurrentPos.latitude,
                    riderCurrentPos.longitude
                )
                FirebaseDatabase.getInstance().getReference(Common.pendingRequestRef)
                    .child(nearestDriverKey)
                    .setValue(request)
                searchingDriverProgressDialog.show()
                if (this::driverFinishedTrip.isInitialized && this::driverStartTrip.isInitialized && this::driverConfirmListener.isInitialized)
                    removeAllListeners()
                addListenerForDriverConfirm()
                addListenerForTripStart()
                addListenerForTripFinished()
            } else {
                Toast.makeText(context, "No Drivers Available For Now", Toast.LENGTH_LONG).show()
            }
        }


    }

    private fun removeAllListeners() {
        FirebaseDatabase.getInstance()
            .getReference(Common.finishRequestRef).child(nearestDriverKey).child(riderKey)
            .removeEventListener(driverFinishedTrip)
        FirebaseDatabase.getInstance()
            .getReference(Common.inTripRequestRef).child(nearestDriverKey).child(riderKey)
            .removeEventListener(driverStartTrip)
        FirebaseDatabase.getInstance()
            .getReference(Common.confirmedRequestRef).child(nearestDriverKey).child(riderKey)
            .removeEventListener(driverConfirmListener)
    }

    private fun setGeoQueryListener() {
        //listener to retrieve keys & locations around user
        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
            override fun onKeyEntered(key: String, location: GeoLocation) {

                //get the Driver information that entered the range
                FirebaseDatabase.getInstance()
                    .getReference(Common.driverInfoRef + "/${key}")
                    .get().addOnSuccessListener {
                        driverInfo = it.getValue(DriverInfoModel::class.java)
                        if (!isConfirmed)
                            addMarkerOnMap(location, driverInfo, key)

                        //save current location as previous location
                        //when driver moving this current location becomes previous
                        driverMarker.get(key)!!.previousPosition =
                            LatLng(location.latitude, location.longitude)

                        //get and save nearest driver key
                        saveNearestDriverKey(location, key)
                    }.addOnFailureListener {
                        Toast.makeText(
                            context,
                            it.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }

            }

            override fun onKeyExited(key: String) {
                if (driverMarker.containsKey(key))
                    driverMarker.get(key)!!.marker.remove()
            }


            override fun onKeyMoved(key: String, location: GeoLocation) {
                if (isConfirmed) {
                    if (key == capTripKey) {
                        //on driver moving save the current and previous position
                        driverMarker.get(key)!!.currentPosition =
                            LatLng(location.latitude, location.longitude)
                        val currentLatLng = driverMarker.get(key)!!.currentPosition
                        val previousLatLng = driverMarker.get(key)!!.previousPosition
                        //update care location with animation
                        updateCarLocation(
                            currentLatLng,
                            previousLatLng,
                            driverMarker.get(key)!!.marker
                        )
                        //when animation is done we save current position as previous position
                        //to reference to it next moving
                        driverMarker.get(key)!!.previousPosition = currentLatLng
                    }
                } else {
                    if (!driverMarker.containsKey(key)) {
                        FirebaseDatabase.getInstance()
                            .getReference(Common.driverInfoRef + "/${key}")
                            .get().addOnSuccessListener {
                                driverInfo = it.getValue(DriverInfoModel::class.java)

                                addMarkerOnMap(location, driverInfo, key)
                                //save current location as previous location
                                //when driver moving this current location becomes previous
                                driverMarker.get(key)!!.previousPosition =
                                    LatLng(location.latitude, location.longitude)
                                //get and save nearest driver key
                                saveNearestDriverKey(location, key)
                            }.addOnFailureListener {
                                Toast.makeText(
                                    context,
                                    it.message,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }else {
                        //on driver moving save the current and previous position
                        driverMarker.get(key)!!.currentPosition =
                            LatLng(location.latitude, location.longitude)
                        val currentLatLng = driverMarker.get(key)!!.currentPosition
                        val previousLatLng = driverMarker.get(key)!!.previousPosition
                        //update care location with animation
                        updateCarLocation(
                            currentLatLng,
                            previousLatLng,
                            driverMarker.get(key)!!.marker
                        )
                        //when animation is done we save current position as previous position
                        //to reference to it next moving
                        driverMarker.get(key)!!.previousPosition = currentLatLng

                        //get nearest driver location
                        saveNearestDriverKey(location, key)
                    }
                }
            }

            override fun onGeoQueryReady() {

            }

            override fun onGeoQueryError(error: DatabaseError) {
            }
        })
    }
    private fun addMarkerOnMap(location: GeoLocation, driverInfo: DriverInfoModel?, key: String) {

        //set marker to show drivers on map
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(
                    LatLng(
                        location.latitude,
                        location.longitude
                    )
                )
                .title(driverInfo!!.firstName)
                .flat(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                .anchor(.5f, .5f)
        )
        //save marker reference for each entered key to manipulate it after
        driverMarker.set(key, MapMarkerModel(marker!!))

    }

    private fun addCancelListener() {
        val cancelListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.value == "driver canceled") {
                    cancelAll()
                    showCancelDialog()
                }
            }

            private fun showCancelDialog() {

                val builder = AlertDialog.Builder(context)
                builder.setTitle("Cancel")
                builder.setMessage("the trip has been canceled by driver")
                //builder.setPositiveButton("OK", DialogInterface.OnClickListener(function = x))

                builder.setPositiveButton("ok") { dialog, which ->
                    dialog.dismiss()
                }

                builder.show()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show()
            }
        }
        FirebaseDatabase.getInstance()
            .getReference(Common.canceledRequestRef)
            .child(capTripKey).child(riderKey)
            .removeEventListener(cancelListener)
        FirebaseDatabase.getInstance()
            .getReference(Common.canceledRequestRef)
            .child(capTripKey).child(riderKey)
            .addValueEventListener(cancelListener)

    }

    private fun cancelAll() {
        isConfirmed = false
        driverMarker.forEach { it -> it.value.marker.remove() }
        driverMarker.clear()
        geoQuery.removeAllListeners()
        setGeoQueryListener()
        FirebaseDatabase.getInstance()
            .getReference(Common.pendingRequestRef)
            .child(nearestDriverKey)
            .removeValue()
        FirebaseDatabase.getInstance()
            .getReference(Common.confirmedRequestRef)
            .child(nearestDriverKey)
            .removeValue()
        FirebaseDatabase.getInstance()
            .getReference(Common.inTripRequestRef)
            .child(nearestDriverKey)
            .removeValue()
        FirebaseDatabase.getInstance()
            .getReference(Common.canceledRequestRef)
            .child(capTripKey).child(riderKey)
            .setValue(null)
        slideUpPanelView.removeAllViews()
        slideUpPanelView.addView(requestDriverView)

    }

    private fun initDriverSearchProgress() {

        val llPadding = 30
        val ll = LinearLayout(context)
        ll.orientation = LinearLayout.HORIZONTAL
        ll.setPadding(llPadding, llPadding, llPadding, llPadding)
        ll.gravity = Gravity.CENTER

        val searchDriverProgressBar = ProgressBar(context)
        searchDriverProgressBar.isIndeterminate = true
        searchDriverProgressBar.setPadding(0, 0, llPadding, 0)

        searchingDriverTxt = TextView(context)
        searchingDriverTxt.text = "Searching For Driver"
        searchingDriverTxt.setTextColor(Color.parseColor("#000000"))
        searchingDriverTxt.textSize = 20f

        ll.addView(searchDriverProgressBar)
        ll.addView(searchingDriverTxt)

        val builder = AlertDialog.Builder(context)
        builder.setCancelable(true)
        builder.setView(ll)

        searchingDriverProgressDialog = builder.create()
    }

    private fun addListenerForTripFinished() {
        driverFinishedTrip = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.value == "Finished") {
                    showCostDialog()
                    showNotification()
                    slideUpPanelView.removeAllViews()
                    slideUpPanelView.addView(requestDriverView)
                }
            }

            private fun showNotification() {
                //build Notfication and set properties
                val builder = NotificationCompat.Builder(requireContext(), "1")
                    .setSmallIcon(R.drawable.splash_screen)
                    .setContentTitle("Cost")
                    .setContentText("the trip has been finished and the cost is : 22$")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)

                createNotificationChannel()
                with(NotificationManagerCompat.from(requireContext())) {
                    // notificationId is a unique int for each notification that you must define
                    notify(1, builder.build())
                }
            }

            private fun createNotificationChannel() {
                // Create the NotificationChannel, but only on API 26+ because
                // the NotificationChannel class is new and not in the support library
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val name = getString(R.string.app_name)
                    val descriptionText = "description text for notification"
                    val importance = NotificationManager.IMPORTANCE_HIGH
                    val channel = NotificationChannel("1", name, importance).apply {
                        description = descriptionText
                    }
                    // Register the channel with the system
                    val notificationManager: NotificationManager =
                        activity!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.createNotificationChannel(channel)
                }

            }

            private fun showCostDialog() {
                val builder = AlertDialog.Builder(context)
                builder.setTitle("Trip Cost")
                builder.setMessage("the trip has been finished and the cost is : 22$")
                //builder.setPositiveButton("OK", DialogInterface.OnClickListener(function = x))

                builder.setPositiveButton("ok") { dialog, which ->
                    dialog.dismiss()
                }

                builder.show()

            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    requireContext(),
                    error.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        FirebaseDatabase.getInstance()
            .getReference(Common.finishRequestRef).child(nearestDriverKey).child(Common.riderKey)
            .addValueEventListener(driverFinishedTrip)
        isConfirmed = false


    }

    private fun addListenerForTripStart() {

        driverStartTrip = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.value == "inTrip") {
                    showStartTripDialog()
                    panelTitle.text = "The Trip Has Started"
                }
            }

            private fun showStartTripDialog() {
                val builder = AlertDialog.Builder(context)
                builder.setTitle("Start The Trip")
                builder.setMessage("the trip has started")
                //builder.setPositiveButton("OK", DialogInterface.OnClickListener(function = x))
                builder.setPositiveButton("ok") { dialog, which ->
                    dialog.dismiss()
                }

                builder.show()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    requireContext(),
                    error.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        FirebaseDatabase.getInstance()
            .getReference(Common.inTripRequestRef).child(nearestDriverKey).child(Common.riderKey)
            .addValueEventListener(driverStartTrip)

    }

    private fun addListenerForDriverConfirm() {
        driverConfirmListener = object : ValueEventListener {
            lateinit var driverInfo: DriverInfoModel
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.value == "Confirmed") {
                    FirebaseDatabase.getInstance()
                        .getReference(
                            Common.driverInfoRef.plus("/").plus(nearestDriverKey)
                        )
                        .addListenerForSingleValueEvent(
                            object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    driverInfo =
                                        snapshot.getValue(DriverInfoModel::class.java)!!
                                    searchingDriverProgressDialog.dismiss()
                                    capTripKey = nearestDriverKey
                                    isConfirmed = true
                                    driverMarker.forEach { key, _ ->
                                        if (key != capTripKey) {
                                            driverMarker.get(key)!!.marker.remove()
                                        }
                                    }
                                    if (!added){
                                        addCancelListener()
                                        added = true
                                    }
                                    showConfirmLayout(driverInfo)
                                }

                                private fun showConfirmLayout(driverInfo: DriverInfoModel) {
                                    val confirmView =
                                        layoutInflater.inflate(R.layout.confirm_driver_layout, null)
                                    panelTitle = confirmView.findViewById(R.id.panel_title)
                                    slideUpPanelView.removeAllViews()
//                                    slideUpPanelView.getChildAt(0).visibility = ViewGroup.GONE
                                    slideUpPanelView.addView(confirmView)
                                    slideUpPanelLayout.anchorPoint = 1f
                                    slideUpPanelLayout.panelState =
                                        SlidingUpPanelLayout.PanelState.EXPANDED
                                    slideUpPanelLayout.panelHeight = 135

                                    val cancel_btn =
                                        confirmView.findViewById<Button>(R.id.cancel_btn)
                                    val riderName =
                                        confirmView.findViewById<TextView>(R.id.driver_name)
                                    val riderNumber =
                                        confirmView.findViewById<TextView>(R.id.driver_number)
                                    val carNumber =
                                        confirmView.findViewById<TextView>(R.id.car_number)
                                    val riderImage =
                                        confirmView.findViewById<CircleImageView>(R.id.rider_image)

                                    riderName.text =
                                        "Name : ${driverInfo.firstName} ${driverInfo.lastName}"
                                    riderNumber.text = "Mobile : ${driverInfo.phoneNumber}"
                                    carNumber.text = "Car Number : ${driverInfo.carNumber}"
                                    if (!driverInfo.avatar.isEmpty())
                                        Glide.with(requireContext()).load(driverInfo.avatar)
                                            .into(riderImage)

                                    cancel_btn.setOnClickListener {
                                        cancelAll()
                                        FirebaseDatabase.getInstance()
                                            .getReference(Common.canceledRequestRef)
                                            .child(nearestDriverKey).child(Common.riderKey)
                                            .setValue("rider canceled")
                                    }

                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Toast.makeText(
                                        requireContext(),
                                        error.message,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }

                            })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show()
            }
        }
        FirebaseDatabase.getInstance()
            .getReference(Common.confirmedRequestRef).child(nearestDriverKey).child(Common.riderKey)
            .addValueEventListener(driverConfirmListener)
    }


    private fun saveNearestDriverKey(location: GeoLocation, key: String) {
        val result = FloatArray(1)

        //get distance between rider and driver to get nearest driver for trip request
        //and save it to result
        Location.distanceBetween(
            riderCurrentPos.latitude,
            riderCurrentPos.longitude,
            location.latitude,
            location.longitude,
            result
        )
        if (nearestDriverDist == null) {
            nearestDriverDist = result[0]
            nearestDriverKey = key
        } else if (nearestDriverDist!! > result.get(0)) {
            nearestDriverDist = result[0]
            nearestDriverKey = key
        }
    }


    /*
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        getLocationPermission()
        val locationButton =
            (mapFragment.view?.findViewById<View>(Integer.parseInt("1"))?.parent as View).findViewById<View>(
                Integer.parseInt("2")
            )
        val rlp = locationButton.getLayoutParams() as RelativeLayout.LayoutParams
        rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
        rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
        rlp.setMargins(0, 0, 30, 180)
        mMap.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(
                requireContext(),
                R.raw.uber_maps_style
            )
        )

        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest, locationCallback,
            Looper.myLooper()!!
        )


    }

    @SuppressLint("MissingPermission")
    private fun getLocationPermission() {

        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true
            return
        } else {
            activityResultLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )

        }
    }

    fun carAnimator(): ValueAnimator {
        val valueAnimator = ValueAnimator.ofFloat(0f, 1f)
        valueAnimator.duration = 3000
        valueAnimator.interpolator = LinearInterpolator()
        return valueAnimator
    }

    //update driver car location with animation
    private fun updateCarLocation(currentLatLng: LatLng, previousLatLng: LatLng, marker: Marker) {
        val valueAnimator = carAnimator()
        valueAnimator.addUpdateListener { va ->
            val multiplier = va.animatedFraction
            val nextLocation = LatLng(
                multiplier * currentLatLng.latitude + (1 - multiplier) * previousLatLng.latitude,
                multiplier * currentLatLng.longitude + (1 - multiplier) * previousLatLng.longitude
            )
            val rotation = getRotation(previousLatLng, currentLatLng)
            marker.rotation = rotation
            marker.position = nextLocation
        }
        valueAnimator.start()
    }

    //get rotation of car marker
    fun getRotation(start: LatLng, end: LatLng): Float {
        val latDifference: Double = abs(start.latitude - end.latitude)
        val lngDifference: Double = abs(start.longitude - end.longitude)
        var rotation = -1F
        when {
            start.latitude < end.latitude && start.longitude < end.longitude -> {
                rotation = Math.toDegrees(atan(lngDifference / latDifference)).toFloat()
            }
            start.latitude >= end.latitude && start.longitude < end.longitude -> {
                rotation = (90 - Math.toDegrees(atan(lngDifference / latDifference)) + 90).toFloat()
            }
            start.latitude >= end.latitude && start.longitude >= end.longitude -> {
                rotation = (Math.toDegrees(atan(lngDifference / latDifference)) + 180).toFloat()
            }
            start.latitude < end.latitude && start.longitude >= end.longitude -> {
                rotation =
                    (90 - Math.toDegrees(atan(lngDifference / latDifference)) + 270).toFloat()
            }
        }
        return rotation
    }

    override fun onDestroyView() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        _binding = null
        super.onDestroyView()
    }
}