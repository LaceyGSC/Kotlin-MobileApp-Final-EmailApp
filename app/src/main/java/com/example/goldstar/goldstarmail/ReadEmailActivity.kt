package com.example.goldstar.goldstarmail

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import kotlinx.android.synthetic.main.activity_read_email.*
import java.text.SimpleDateFormat
import java.util.*

/************************************************************
Created by Lacey Taylor
Date: Dec, 2017
Project: GoldStarMail
Description:
    Activity to show and read email

 ***********************************************************/
class ReadEmailActivity : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_email)

        setSupportActionBar(readEmailToolbar)

        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val itemIndex = intent.getIntExtra("Position", 0)
        val emailItem = DataMaster.currentEmailList[itemIndex]

        readEmailBody.scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY

        readEmailBody.settings.builtInZoomControls = true
        readEmailBody.settings.displayZoomControls = false

        readEmailBody.loadData(emailItem.messageHTML, "text/html", null)

        var splitString = emailItem.from.split(",")

        for (string in splitString)
        {
            string.replace("<", "")
            string.replace(">", "")
        }

        readEmailFromText.text = splitString[0]
        if (splitString.size > 1)
        {
            var buildString = ""

            for (string in splitString)
            {
                buildString += "$string\n"
            }

            readEmailFromBlankText.text = buildString
        }


        splitString = emailItem.to.split(",")
        for (string in splitString)
        {
            string.replace("<", "")
            string.replace(">", "")
        }

        readEmailToText.text = splitString[0]
        if (splitString.size > 1)
        {
            var buildString = ""
            for (address in splitString)
            {
                buildString += "$address\n"
            }

            readEmailToText.text = "Multiple"
            readEmailToBlankText.text = buildString

        }

        readEmailSubjectText.text = emailItem.subject

        var simpleDate = SimpleDateFormat("EEE, MMM dd, yyyy   hh:mm aa")
        var time = simpleDate.format(Date(emailItem.internalDate))
        readEmailDateText.text = time

        readEmailShowDetailsButton.setOnClickListener {

            if (readEmailShowDetailsButton.text == "More")
            {
                readEmailFromBlankLayout.visibility = View.VISIBLE
                readEmailToBlankLayout.visibility = View.VISIBLE
                readEmailToInnerLayout.visibility = View.VISIBLE
                readEmailDateLayout.visibility = View.VISIBLE
                readEmailSubjectLayout.visibility = View.GONE


                readEmailShowDetailsButton.text = "Less"
            }
            else
            {
                readEmailFromBlankLayout.visibility = View.GONE
                readEmailToBlankLayout.visibility = View.GONE
                readEmailDateLayout.visibility = View.GONE
                readEmailToInnerLayout.visibility = View.GONE
                readEmailSubjectLayout.visibility = View.VISIBLE
                readEmailShowDetailsButton.text = "More"
            }

        }

        readEmailBottomMenuReply.setOnClickListener {

            val intent = Intent(this, SendEmailActivity::class.java)
            intent.putExtra("Sender", DataMaster.currentAccount?.name)
            intent.putExtra("Position", "$itemIndex")
            intent.putExtra("Type", "reply")
            this.startActivity(intent)

        }

        readEmailBottomMenuForward.setOnClickListener {

            val intent = Intent(this, SendEmailActivity::class.java)
            intent.putExtra("Sender", DataMaster.currentAccount?.name)
            intent.putExtra("Position", "$itemIndex")
            intent.putExtra("Type", "forward")
            this.startActivity(intent)

        }


    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean
    {
        menuInflater.inflate(R.menu.menu_read_email, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean
    {
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean
    {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

}