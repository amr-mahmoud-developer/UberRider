package com.example.uberrider.Model

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker

class MapMarkerModel (val marker: Marker) {
    lateinit var currentPosition : LatLng
    lateinit var previousPosition : LatLng
}