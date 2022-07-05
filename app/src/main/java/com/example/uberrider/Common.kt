package com.example.uberrider

import com.example.uberrider.Model.RiderInfoModel
import java.lang.StringBuilder

object Common {
    fun buildWelcomeMessage(): String {
        return StringBuilder("Welcome ").append(currentUser!!.firstName).append(" ")
            .append(currentUser.lastName).toString()
    }

    val driverInfoRef: String = "DriverInfo"
    val diverLocationRef = "DriverLocation"
    val TokenRef: String = "Tokens"
    val riderRef = "RidersInfo"
    val pendingRequestRef = "Requests/Pending"
    val confirmedRequestRef = "Requests/Confirmed"
    val inTripRequestRef = "Requests/InTrip"
    val canceledRequestRef = "Requests/Canceled"
    val finishRequestRef = "Requests/Finished"
    lateinit var currentUser : RiderInfoModel
    lateinit var riderKey : String
}