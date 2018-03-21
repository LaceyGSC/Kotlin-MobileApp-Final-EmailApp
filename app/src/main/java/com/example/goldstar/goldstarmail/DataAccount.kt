package com.example.goldstar.goldstarmail

import com.google.gson.Gson
import java.math.BigInteger

/************************************************************
Created by Lacey Taylor
Date: Nov, 2017
Project: GoldStarMail
Description:
    Data class to contain the information for each individual account
    uses the email address of the account as its name

 ***********************************************************/
data class DataAccount(var name: String,
                       var latestHistoryID : BigInteger = BigInteger("0"),
                       var labelIDMap : MutableMap<String,String>,
                       var labelNameMap : MutableMap<String,String>,
                       var labelMessageMap : MutableMap<String, MutableList<DataEmailItem>>)

//Latest history ID is largest id of last message pulled for this account
//LabelIDMap is used to convert gmail file ID to Name
//LabelNameMap converts human picked name back to unique ID
//LabelMessageMap collects emails into groups based on label ID
{

    companion object
    {
        fun fromJSON(jString : String) : DataAccount
        {
            var gson  = Gson()

            return gson.fromJson(jString, DataAccount::class.javaObjectType)

        }
    }

    fun toJSON() : String
    {
        var gson  = Gson()

        var jString : String = gson.toJson(this)

        return jString
    }
}