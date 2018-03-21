package com.example.goldstar.goldstarmail

import java.math.BigInteger

/************************************************************
Created by Lacey Taylor
Date: Nov, 2017
Project: GoldStarMail
Description:
    Data class to represent basics of an email. ID is unique alphanumeric mix
    replyTo only exits if specified in incoming email
    messageHTML is body of email in "text/html" format

 ***********************************************************/
data class DataEmailItem(val id : String,
                         val snippet : String,
                         val historyID : BigInteger,
                         val internalDate : Long,
                         var labels : MutableList<String>,
                         val to : String,
                         val subject : String,
                         val returnPath : String,
                         val from : String,
                         val replyTo : String,
                         val messageHTML : String,
                         var unread: Boolean
                         )