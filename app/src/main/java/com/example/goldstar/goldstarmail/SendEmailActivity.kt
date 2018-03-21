package com.example.goldstar.goldstarmail

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.Base64
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import kotlinx.android.synthetic.main.activity_send_email.*
import java.io.ByteArrayOutputStream
import java.util.*
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage


/************************************************************
Created by Lacey Taylor
Date: Nov, 2017
Project: GoldStarMail
Description:
    Activity to send email

 ***********************************************************/
class SendEmailActivity : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_email)
        setSupportActionBar(sendEmailToolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        sendEmailBody.background = ColorDrawable(Color.WHITE)

        editorBarListenerSetup()


        //Gets index of clicked item in MainActivity recyclerView
        var sender = intent.getStringExtra("Sender").toString()
        var sendTo = ""
        var bbcTo = ""
        var ccTo = ""
        var subject = ""

        when
        {
            intent.getStringExtra("Type") == "reply"   ->
            {
                val index = intent.getStringExtra("Position").toInt()
                val emailItem = DataMaster.currentEmailList[index]

                subject = "Re : ${emailItem.subject}"

                if (emailItem.replyTo != "")
                {
                    sendTo = emailItem.replyTo
                }
                else
                {
                    sendTo = emailItem.from
                }
            }
            intent.getStringExtra("Type") == "forward" ->
            {
                val index = intent.getStringExtra("Position").toInt()
                val emailItem = DataMaster.currentEmailList[index]

                subject = "Fwd: ${emailItem.subject}"


                sendEmailBody.html = emailItem.messageHTML

            }
            intent.getStringExtra("Type") == "new"     ->
            {
                sendTo = ""
            }
        }

        sendEmailTitle.text = sender

        var arraySpinner = mutableListOf<String>()

        arraySpinner.add(DataMaster.currentAccount!!.name)

        for (account in DataMaster.accountList)
        {
            arraySpinner.add(account.name)
        }
        var arrayAdapter = ArrayAdapter<String>(this, R.layout.spinner_item, arraySpinner)

        arrayAdapter.setDropDownViewResource(R.layout.spinner_item)
        sendEmailFromText.adapter = arrayAdapter
        sendEmailFromText.setSelection(0)

        sendEmailToText.setText(sendTo, TextView.BufferType.EDITABLE)
        sendEmailSubjectText.setText(subject, TextView.BufferType.EDITABLE)

        sendEmailShowDetailsButton.setOnClickListener {

            if (sendEmailShowDetailsButton.text == "More")
            {
                sendEmailCopyBlankLayout.visibility = View.VISIBLE
                sendEmailBackCopyBlankLayout.visibility = View.VISIBLE
                sendEmailFromInnerLayout.visibility = View.VISIBLE

                sendEmailShowDetailsButton.text = "Less"
            }
            else
            {
                sendEmailCopyBlankLayout.visibility = View.GONE
                sendEmailBackCopyBlankLayout.visibility = View.GONE

                sendEmailShowDetailsButton.text = "More"
            }

        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean
    {
        menuInflater.inflate(R.menu.menu_send_email, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean
    {
        if (item?.title == "Send")
        {
            var emailRegex : Regex = Regex( "(?:[a-z0-9!#\$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#\$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08" +
                    "\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@" +
                    "(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4]" +
                    "[0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-" +
                    "\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])")

            if(sendEmailToText.text.matches(emailRegex) && sendEmailSubjectText.text.toString() != "")
            {
                var message = createEmail()
                SendEmailTaskRequest(message).execute()
                finish()
            }
            else
            {
                var snackbar : Snackbar = Snackbar.make(sendEmailToInnerLayout, "You did not enter a valid recipent address or subject. Correct and send again", Snackbar.LENGTH_LONG)
                snackbar.show()
            }
        }

        return false
    }

    override fun onSupportNavigateUp(): Boolean
    {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    private fun editorBarListenerSetup()
    {
        action_undo.setOnClickListener { sendEmailBody.undo() }

        action_redo.setOnClickListener { sendEmailBody.redo() }

        action_bold.setOnClickListener { sendEmailBody.setBold() }

        action_italic.setOnClickListener { sendEmailBody.setItalic() }

        action_subscript.setOnClickListener { sendEmailBody.setSubscript() }

        action_superscript.setOnClickListener { sendEmailBody.setSuperscript() }

        action_strikethrough.setOnClickListener { sendEmailBody.setStrikeThrough() }

        action_underline.setOnClickListener { sendEmailBody.setUnderline() }

        action_heading1.setOnClickListener { sendEmailBody.setHeading(1) }

        action_heading2.setOnClickListener { sendEmailBody.setHeading(2) }

        action_heading3.setOnClickListener { sendEmailBody.setHeading(3) }

        action_heading4.setOnClickListener { sendEmailBody.setHeading(4) }

        action_heading5.setOnClickListener { sendEmailBody.setHeading(5) }

        action_heading6.setOnClickListener { sendEmailBody.setHeading(6) }

        action_txt_color.setOnClickListener { sendEmailBody.setTextColor(Color.BLACK) }

        action_bg_color.setOnClickListener { sendEmailBody.setBackgroundColor(resources.getColor(R.color.colorAccent)) }

        action_indent.setOnClickListener { sendEmailBody.setIndent() }

        action_outdent.setOnClickListener { sendEmailBody.setOutdent() }

        action_align_left.setOnClickListener { sendEmailBody.setAlignLeft() }

        action_align_center.setOnClickListener { sendEmailBody.setAlignCenter() }

        action_align_right.setOnClickListener { sendEmailBody.setAlignRight() }

        action_blockquote.setOnClickListener { sendEmailBody.setBlockquote() }

        action_insert_bullets.setOnClickListener { sendEmailBody.setBullets() }

        action_insert_numbers.setOnClickListener { sendEmailBody.setNumbers() }

        action_insert_image.setOnClickListener { sendEmailBody.insertImage("", "") }

        action_insert_link.setOnClickListener { sendEmailBody.insertLink("", "") }

        action_insert_checkbox.setOnClickListener { sendEmailBody.insertTodo() }

    }

    @Throws(MessagingException::class)
    private fun createEmail(): MimeMessage
    {
        val props = Properties()
        val session = Session.getDefaultInstance(props, null)

        var email = MimeMessage(session)

        email.setFrom(InternetAddress(sendEmailFromText.selectedItem.toString()))
        email.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(sendEmailToText.text.toString()))
        email.subject = sendEmailSubjectText.text.toString()
        email.setText(sendEmailBody.html)
        email.isMimeType("text/html")

        return email
    }

    private fun createMessageFromEmail(emailContent: MimeMessage): Message
    {
        val buffer = ByteArrayOutputStream()
        emailContent.writeTo(buffer)

        val byteArra = buffer.toByteArray()
        val encodedEmail = Base64.encodeBase64URLSafeString(byteArra)
        //val encodedEmail = com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64.encodeBase64URLSafe(byteArra)

        val message = Message()
        message.raw = encodedEmail.toString()
        return message
    }

    inner class SendEmailTaskRequest constructor(mimeMessage: MimeMessage) : AsyncTask<Void, Void, Message>()
    {
        private var mService: com.google.api.services.gmail.Gmail
        private lateinit var mLastError: Exception
        private lateinit var messageToSend: MimeMessage

        init
        {
            val SCOPES = arrayOf(GmailScopes.MAIL_GOOGLE_COM)
            val credential = GoogleAccountCredential.usingOAuth2(applicationContext, SCOPES.asList()).setBackOff(ExponentialBackOff())
            credential.selectedAccountName = DataMaster.currentAccount!!.name
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance()
            mService = com.google.api.services.gmail.Gmail.Builder(transport, jsonFactory, credential)
                    .setApplicationName("Gold Star Mail")
                    .build()

            messageToSend = mimeMessage
        }

        override fun onPreExecute()
        {
            super.onPreExecute()
        }

        override fun doInBackground(vararg p0: Void?): Message
        {
            return try
            {
                sendEmail(messageToSend)
            }
            catch (e: Exception)
            {
                mLastError = e
                var mess = Message()
                cancel(true)
                return mess
            }
        }

        fun sendEmail(emailContent: MimeMessage): Message
        {
            var message = createMessageFromEmail(emailContent)
            message = mService.users().messages().send("me", message).execute()

            return message
        }


        override fun onPostExecute(result: Message)
        {
            super.onPostExecute(result)


            if (result == null || result.isEmpty())
            {
                //Snackbar message
                //No results returned
            }
            else
            {
                Log.e("Post", "Sent Message")

            }

        }

    }

}