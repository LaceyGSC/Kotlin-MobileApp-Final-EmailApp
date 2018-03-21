package com.example.goldstar.goldstarmail

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.example.goldstar.goldstarmail.DataMaster.currentFolder
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.Base64
import com.google.api.client.util.ExponentialBackOff
import com.google.api.client.util.StringUtils
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.main_content.*
import pub.devrel.easypermissions.EasyPermissions
import java.io.IOException
import java.math.BigInteger

/************************************************************
Created by Lacey Taylor
Date: Nov, 2017
Project: GoldStarMail
Description:
Master activity class for app, controls most of the networking
along with the moving between accounts and folders logic

 ***********************************************************/

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks, NavigationView.OnNavigationItemSelectedListener
{

    //Required by EasyPermission, but not used in this case
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>?)
    {
        //TODO("not implemented")
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>?)
    {
        //TODO("not implemented")
    }


    //Response codes to determine how to handle errors
    private val REQUEST_ACCOUNT_PICKER = 1000
    private val REQUEST_AUTHORIZATION = 1001
    private val REQUEST_GOOGLE_PLAY_SERVICES = 1002
    private val REQUEST_PERMISSION_GET_ACCOUNTS = 1003

    //Which permissions inside of gmail account to request
    //this requests them all
    private val SCOPES = arrayOf(GmailScopes.MAIL_GOOGLE_COM)
    private lateinit var mCredential: GoogleAccountCredential


    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        //Loads existing data from local storage
        DataMaster.loadAccountData(this@MainActivity)
        DataMaster.loadMasterData(this@MainActivity)

        //Used to authenticate to account
        mCredential = GoogleAccountCredential.usingOAuth2(applicationContext, SCOPES.asList()).setBackOff(ExponentialBackOff())

        //If accounts exist, tries to load up last one used
        //If that not found, defaults to first in list
        //If none found, starts a new process
        if (DataMaster.accountList.size != 0)
        {
            if (DataMaster.currentAccountName != "")
            {
                for (account in DataMaster.accountList)
                {
                    if (account.name == DataMaster.currentAccountName)
                    {
                        DataMaster.currentAccount = account
                        mCredential.selectedAccountName = account.name
                    }
                }
            }
            else
            {
                DataMaster.currentAccount = DataMaster.accountList[0]
                mCredential.selectedAccountName = DataMaster.accountList[0].name
            }

        }
        else
        {
            mCredential.selectedAccountName = null
            DataMaster.currentAccount = DataAccount("", BigInteger("0"), mutableMapOf(), mutableMapOf(), mutableMapOf())

        }

        //Lauches new Send Email activity
        floatingActionButton.setOnClickListener {

            val intent = Intent(this, SendEmailActivity::class.java)
            intent.putExtra("Sender", DataMaster.currentAccount?.name)
            intent.putExtra("Position", "")
            intent.putExtra("Type", "new")
            startActivity(intent)

        }

        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(resources.getColor(R.color.colorAccentTransparent))
        swipeRefreshLayout.setOnRefreshListener {

            syncClient()
        }

        setupRecyclerView()
        setupNavigationDrawer()

        syncClient()
        swipeRefreshLayout.isRefreshing = true

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        return when (item.itemId)
        {
        //If delete is selected, checks to see if it can default to another account.
        //If it can't, it clears all info from removed account and starts process
        //to request a new one
            R.id.action_delete ->
            {
                DataMaster.deleteAccountData(DataMaster.currentAccount, this)


                var builder = AlertDialog.Builder(this)
                builder.setTitle("Are you sure you want remove this account?")

                builder.setPositiveButton("Yes" ,DialogInterface.OnClickListener{
                    dialog: DialogInterface?, which: Int ->

                    if (DataMaster.accountList.size != 0)
                    {
                        DataMaster.currentAccount = DataMaster.accountList[0]
                        syncClient()
                    }
                    else
                    {
                        mCredential.selectedAccountName = null
                        DataMaster.currentAccount = DataAccount("", BigInteger("0"), mutableMapOf(), mutableMapOf(), mutableMapOf())

                        navigationView.menu.removeGroup(R.id.groupAccounts)
                        navigationView.menu.addSubMenu(R.id.groupAccounts, 0, 50, "GroupAccounts")

                        DataMaster.currentFolder = "INBOX"
                        DataMaster.accountList = mutableListOf()
                        DataMaster.currentEmailList = mutableListOf()

                        DataMaster.currentAccountName = DataMaster.currentAccount?.name ?: ""
                        DataMaster.currentFolderName = "INBOX"
                        DataMaster.accountListCount = DataMaster.accountList.size

                        currentFolderContentMain.text = ""
                        currentAccountContentMain.text = ""

                        DataMaster.saveMasterData(this)

                        recyclerView.adapter.notifyDataSetChanged()
                        syncClient()
                        swipeRefreshLayout.isRefreshing = true
                    }
                })

                builder.setNegativeButton("Cancel" ,DialogInterface.OnClickListener{
                    dialog: DialogInterface?, which: Int ->
                    dialog!!.cancel()
                })

                builder.show()

                return true
            }
            R.id.action_remove_inbox_emails ->
            {
                if(currentFolder == "INBOX" || currentFolder == "TRASH"|| currentFolder == "SENT"||currentFolder == "DRAFT"|| currentFolder == "UNREAD")
                {
                    var builder = AlertDialog.Builder(this)
                    builder.setTitle("Cannot Delete System Folder")

                    builder.setNegativeButton("Cancel" ,DialogInterface.OnClickListener{
                        dialog: DialogInterface?, which: Int ->
                        dialog!!.cancel()
                    })

                    builder.show()
                }
                else
                {
                    var builder = AlertDialog.Builder(this)
                    builder.setTitle("Are you sure you want to delete this and move your emails?")

                    builder.setPositiveButton("Yes" ,DialogInterface.OnClickListener{
                        dialog: DialogInterface?, which: Int ->

                        for(email in DataMaster.currentAccount!!.labelMessageMap[DataMaster.currentAccount!!.labelNameMap[DataMaster.currentFolder]] ?: mutableListOf())
                        {
                            email.labels.remove(DataMaster.currentFolder)
                            email.labels.add("INBOX")
                            DataMaster.currentAccount!!.labelMessageMap["INBOX"]!!.add(email)
                        }

                        DataMaster.currentAccount!!.labelMessageMap.remove(DataMaster.currentAccount!!.labelNameMap[DataMaster.currentFolder])
                        MakeLabelUpdateTask(mCredential,"delete",DataMaster.currentFolder).execute()
                        DataMaster.currentFolder = "INBOX"
                        recyclerView.adapter.notifyDataSetChanged()
                        syncClient()
                    })

                    builder.setNegativeButton("Cancel" ,DialogInterface.OnClickListener{
                        dialog: DialogInterface?, which: Int ->
                        dialog!!.cancel()
                    })

                    builder.show()
                }

                return true
            }
            R.id.action_remove_clear_emails ->
            {
                if(currentFolder == "INBOX" || currentFolder == "TRASH"|| currentFolder == "SENT"||currentFolder == "DRAFT"|| currentFolder == "UNREAD")
                {
                    var builder = AlertDialog.Builder(this)
                    builder.setTitle("Cannot Delete System Folder")

                    builder.setNegativeButton("Cancel" ,DialogInterface.OnClickListener{
                        dialog: DialogInterface?, which: Int ->
                        dialog!!.cancel()
                    })

                    builder.show()
                }
                else
                {
                    var builder = AlertDialog.Builder(this)
                    builder.setTitle("Are you sure you want delete folders and emails?")

                    builder.setPositiveButton("Yes" ,DialogInterface.OnClickListener{
                        dialog: DialogInterface?, which: Int ->

                        DataMaster.currentAccount!!.labelMessageMap.remove(DataMaster.currentAccount!!.labelNameMap[DataMaster.currentFolder])
                        MakeLabelUpdateTask(mCredential,"delete",DataMaster.currentFolder).execute()
                        DataMaster.currentFolder = "INBOX"
                        syncClient()
                    })

                    builder.setNegativeButton("Cancel" ,DialogInterface.OnClickListener{
                        dialog: DialogInterface?, which: Int ->
                        dialog!!.cancel()
                    })

                    builder.show()
                }
                return true
            }
            else               -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView()
    {
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = CustomAdapter(this)

        var dividerDecoration = DividerItemDecoration(recyclerView.context, LinearLayoutManager.VERTICAL)
        dividerDecoration.setDrawable(getDrawable(R.drawable.email_card_divider))
        recyclerView.addItemDecoration(dividerDecoration)

        recyclerView.addItemDecoration(DecorationAnimation(this))

        val simpleCallback = ControlCallback(this, recyclerView.adapter as CustomAdapter)
        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun setupNavigationDrawer()
    {
        for (account in DataMaster.accountList)
        {
            navigationView.menu.add(R.id.groupAccounts, 0, 0, account.name)
        }

        val mDrawerToggle = object : ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open_drawer, R.string.close_drawer)
        {
            override fun onDrawerClosed(drawerView: View)
            {
                // invalidateOptionsMenu() // calls onPrepareOptionsMenu()
            }

            override fun onDrawerOpened(drawerView: View)
            {
                //Used to display counter for unready emails in the dedicated folders
                //does not currently support the dynamic folders
                for (label in DataMaster.currentAccount!!.labelNameMap.keys)
                {
                    for (c in 0 until navigationView.menu.size())
                    {
                        var menuItem = navigationView.menu.getItem(c)
                        if (menuItem.title == label)
                        {
                            if (menuItem.actionView != null)
                            {
                                var bubble = menuItem.actionView.findViewById<TextView>(R.id.counterBubble)
                                var countNumber = DataMaster.currentAccount!!.labelMessageMap[DataMaster.currentAccount!!.labelIDMap[label]]!!.count { i -> i.unread }
                                if (countNumber != 0)
                                {
                                    bubble.visibility = View.VISIBLE
                                    bubble.text = countNumber.toString()
                                }
                                else
                                {
                                    bubble.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
            }
        }

        //Deprecated, but allows for navigation drawer to be updated reliably
        drawerLayout.setDrawerListener(mDrawerToggle)

        mDrawerToggle.isDrawerIndicatorEnabled = true
        mDrawerToggle.syncState()

        navigationView.setNavigationItemSelectedListener(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean
    {

        if (item.groupId == R.id.groupLabels || item.groupId == R.id.groupSetLabels)
        {
            //If a label was clicked, switches folders
            DataMaster.currentFolder = DataMaster.currentAccount?.labelNameMap?.get(item.title.toString()) ?: "INBOX"
            drawerLayout.closeDrawer(navigationView)
            swipeRefreshLayout.isRefreshing = true
            syncClient()
        }

        if (item.title == "Add Account")
        {
            //Starts process to add account
            mCredential.selectedAccountName = null
            DataMaster.currentAccount = DataAccount("", BigInteger("0"), mutableMapOf(), mutableMapOf(), mutableMapOf())
        }

        if(item.title == "Add Folder")
        {
            var builder = AlertDialog.Builder(this)
            builder.setTitle("Add Folder...")

            var folderNameText = EditText(this)
            folderNameText.inputType = InputType.TYPE_CLASS_TEXT
            builder.setView(folderNameText)

            builder.setPositiveButton("Create", DialogInterface.OnClickListener{
                dialog: DialogInterface?, which: Int ->
                MakeLabelUpdateTask(mCredential,"add", folderNameText.text.toString()).execute()
            })

            builder.setNegativeButton("Cancel" ,DialogInterface.OnClickListener{
                dialog: DialogInterface?, which: Int ->
                dialog!!.cancel()
            })

            builder.show()

        }

        if (item.groupId == R.id.groupAccounts)
        {
            //If account clicked, swaps those around and defaults to inbox
            for (account in DataMaster.accountList)
            {
                if (account.name == item.title)
                {
                    DataMaster.currentAccount = account
                    mCredential.selectedAccountName = account.name
                    DataMaster.currentFolder = "INBOX"
                }
            }
        }

        drawerLayout.closeDrawer(navigationView)
        swipeRefreshLayout.isRefreshing = true
        recyclerView.adapter.notifyDataSetChanged()
        syncClient()

        return false
    }

    private fun syncClient()
    {
        //Checks to see if app store available, then checks if account chosen before, then tests if device is online
        //If all good, makes async task to request data
        if (!isGooglePlayServicesAvailable())
        {
            acquireGooglePlayServices()
        }
        else if (mCredential.selectedAccountName == null)
        {
            chooseAccount()
        }
        else if (!isDeviceOnline())
        {
            var snackbar: Snackbar = Snackbar.make(recyclerView, "Not able to find a network connection. Please check your network", Snackbar.LENGTH_LONG)
            snackbar.show()
        }
        else
        {
            DataMaster.currentEmailList.clear()

            for (item in DataMaster.currentAccount?.labelMessageMap?.get(DataMaster.currentFolder) ?: mutableListOf())
            {
                DataMaster.currentEmailList.add(item)
            }

            recyclerView.adapter.notifyDataSetChanged()

            MakeLabelRequestTask(mCredential).execute()
            MakeMessageRequestTask(mCredential).execute()
        }
    }

    private fun chooseAccount()
    {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS))
        {
            val accountName = DataMaster.currentAccount?.name
            if (accountName != "" && accountName != null)
            {
                mCredential.selectedAccountName = accountName
                syncClient()
            }
            else
            {
                //Lets you choose an account if none chosen before
                startActivityForResult(mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER)
            }
        }
        else
        {
            // Request the GET_ACCOUNTS permission from a user dialog
            EasyPermissions.requestPermissions(this, "This app needs to access your Google account (via Contacts).", REQUEST_PERMISSION_GET_ACCOUNTS, Manifest.permission.GET_ACCOUNTS)

            //Repeats action to get an account once permissions granted
            chooseAccount()
        }
    }

    //Gets results from prompt to choose account. If all good, sets info and goes
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode)
        {
            REQUEST_GOOGLE_PLAY_SERVICES ->
                if (resultCode != Activity.RESULT_OK)
                {
                    var snackbar: Snackbar = Snackbar.make(recyclerView, "This App Requires Google Play Services", Snackbar.LENGTH_SHORT)
                    snackbar.show()
                }
            REQUEST_ACCOUNT_PICKER       ->
                if (resultCode == Activity.RESULT_OK && data != null && data.extras != null)
                {
                    val accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    if (accountName != null)
                    {
                        mCredential.selectedAccountName = accountName
                        DataMaster.currentAccount?.name = accountName
                        DataMaster.accountList.add(DataMaster.currentAccount ?: DataAccount("", BigInteger("0"), mutableMapOf(), mutableMapOf(), mutableMapOf()))
                        navigationView.menu.add(R.id.groupAccounts, 0, 0, accountName)
                    }
                }
        }

        syncClient()
    }

    private fun isDeviceOnline(): Boolean
    {
        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    private fun isGooglePlayServicesAvailable(): Boolean
    {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this)
        return connectionStatusCode == ConnectionResult.SUCCESS
    }

    private fun acquireGooglePlayServices()
    {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this)
        if (apiAvailability.isUserResolvableError(connectionStatusCode))
        {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode)
        }
    }

    private fun showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode: Int)
    {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val dialog = apiAvailability.getErrorDialog(
                this@MainActivity,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES)
        dialog.show()
    }

    //Requests label data. Label list usually small, so always runs a full update each time
    inner class MakeLabelRequestTask constructor(credential: GoogleAccountCredential) : AsyncTask<Void, Void, List<String>>()
    {
        private var mService: com.google.api.services.gmail.Gmail
        private var unreadMessageMap: MutableMap<String, Int> = mutableMapOf()

        private var reservedLabels = listOf("CATEGORY_PERSONAL",
                                            "CATEGORY_SOCIAL",
                                            "CATEGORY_UPDATES",
                                            "CATEGORY_FORUMS",
                                            "CHAT",
                                            "SENT",
                                            "INBOX",
                                            "TRASH",
                                            "Label_2",
                                            "CATEGORY_PROMOTIONS",
                                            "DRAFT",
                                            "SPAM",
                                            "STARRED",
                                            "UNREAD",
                                            "IMPORTANT",
                                            "Label_1")

        private lateinit var mLastError: Exception

        init
        {
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance()
            mService = com.google.api.services.gmail.Gmail.Builder(transport, jsonFactory, credential)
                    .setApplicationName("Gold Star Mail")
                    .build()
        }

        override fun onPreExecute()
        {
            super.onPreExecute()
            navigationView.menu.removeGroup(R.id.groupLabels)

        }

        override fun doInBackground(vararg p0: Void?): List<String>?
        {
            return try
            {

                getFullLabelData()


            }
            catch (e: Exception)
            {
                mLastError = e
                DataMaster.currentAccount?.latestHistoryID = BigInteger("0")
                cancel(true)
                null
            }
        }

        @Throws(IOException::class)
        private fun getFullLabelData(): List<String>
        {
            //Gets batch of labels, updates every time
            val user: String = "me"
            val listLabels: ListLabelsResponse = mService.users().labels().list(user).execute()
            val labelList = listLabels.labels.toMutableList()
            val returnList: MutableList<String> = mutableListOf()


            for (label in labelList)
            {
                DataMaster.currentAccount?.labelIDMap?.put(label.id, label.name)
                DataMaster.currentAccount?.labelNameMap?.put(label.name, label.id)
                var labelGet = mService.users().labels().get(user, label.id).execute()

                var unreadCount: Int = labelGet.messagesUnread

                unreadMessageMap.put(label.id, unreadCount)


                if (!reservedLabels.contains(label.id))
                {
                    returnList.add(label.name)
                }
            }


            return returnList.toList()
        }


        override fun onPostExecute(result: List<String>?)
        {
            super.onPostExecute(result)

            var menu = navigationView.menu
            menu.addSubMenu(R.id.groupLabels, 0, 200, "groupLabels")

            var sortedUnread = unreadMessageMap

            //Maps labels into the navigation view each time
            for (label in sortedUnread)
            {
                if (!reservedLabels.contains(label.key))
                {
                    menu.add(R.id.groupLabels, 0, 199, DataMaster.currentAccount?.labelIDMap?.get(label.key))
                }

                if (DataMaster.currentAccount?.labelMessageMap?.containsKey(label.key) == false)
                {
                    DataMaster.currentAccount?.labelMessageMap?.put(label.key, mutableListOf())
                }
            }


        }

        override fun onCancelled()
        {
            super.onCancelled()

            when (mLastError)
            {
                is UserRecoverableAuthIOException            ->
                {
                    val x: UserRecoverableAuthIOException? = mLastError as? UserRecoverableAuthIOException
                    startActivityForResult(x?.intent, REQUEST_AUTHORIZATION)
                }

                is GooglePlayServicesAvailabilityIOException ->
                {
                    val x: GooglePlayServicesAvailabilityIOException? = mLastError as? GooglePlayServicesAvailabilityIOException
                }

                is GoogleJsonError                           ->
                {
                    val x: GoogleJsonError? = mLastError as? GoogleJsonError
                }

                else                                         ->
                    Log.e("LastError Labels" , mLastError.toString())
            }

        }

    }

    //Requests messages and data. If request run before, it only pulls from last history date
    //if short pull fails, runs a full update
    inner class MakeMessageRequestTask constructor(credential: GoogleAccountCredential) : AsyncTask<Void, Void, List<String>>()
    {
        private var mService: com.google.api.services.gmail.Gmail
        private lateinit var mLastError: Exception

        init
        {
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance()
            mService = com.google.api.services.gmail.Gmail.Builder(transport, jsonFactory, credential)
                    .setApplicationName("Gold Star Mail")
                    .build()
        }

        override fun doInBackground(vararg p0: Void?): List<String>?
        {
            return try
            {
                if (DataMaster.currentAccount?.latestHistoryID == BigInteger("0"))
                {
                    getFullMessageDataFromApi()
                }
                else
                {
                    getShortMessageDataFromAPI()
                }
            }
            catch (e: Exception)
            {
                mLastError = e
                DataMaster.currentAccount?.latestHistoryID = BigInteger("0")
                cancel(true)
                null
            }
        }

        override fun onPreExecute()
        {
            super.onPreExecute()

            if(DataMaster.currentAccount!!.labelIDMap[DataMaster.currentFolder] != "")
            {
                currentFolderContentMain.text = DataMaster.currentAccount!!.labelIDMap[DataMaster.currentFolder]
            }
            else
            {
                currentFolderContentMain.text = "INBOX"
            }
            currentAccountContentMain.text = "${DataMaster.currentAccount?.name}"

        }

        @Throws(IOException::class)
        private fun getFullMessageDataFromApi(): List<String>
        {
            var user = "me"
            var listMessageID: ListMessagesResponse = mService.users().Messages().list(user).setIncludeSpamTrash(true).execute()
            var listOfMess = listMessageID.messages

            for (list in DataMaster.currentAccount?.labelMessageMap ?: mutableMapOf())
            {
                list.value.clear()
            }

            for (mess in listOfMess)
            {
                var messageDetails = mService.users().messages().get(user, mess.id).execute()
                addMessageToList(messageDetails, true)
            }

            return listOf()
        }

        @Throws(IOException::class)
        private fun getShortMessageDataFromAPI(): List<String>
        {
            var user: String = "me"
            var listHistoryID: ListHistoryResponse = mService
                    .users()
                    .history()
                    .list(user)
                    .setStartHistoryId(DataMaster.currentAccount?.latestHistoryID)
                    .setHistoryTypes(mutableListOf("messageAdded"))
                    .execute()


            var listOfMess = listHistoryID.history ?: mutableListOf()

            if (listOfMess.size > 0)
            {
                for (mess in listOfMess)
                {
                    for (message in mess.messages)
                    {
                        var messageDetails = mService.users().messages().get(user, message.id.toString()).execute()

                        addMessageToList(messageDetails, false)
                    }

                }
            }

            return listOf()
        }

        private fun getBodyWithRecursion(part: MessagePart, mimeType: String): String
        {
            var body = ""

            if (part.parts != null)
            {
                for (item in part.parts)
                {
                    body = "$body ${getBodyWithRecursion(item, mimeType)}"
                }
            }
            else if (part.body.data != null && part.body.attachmentId == null && part.mimeType == mimeType)
            {
                body = part.body.data
            }

            return body
        }

        private fun addMessageToList(messageDetails: Message, isFull: Boolean)
        {
            var labelMessageList: MutableList<DataEmailItem>
            var to = ""
            var subject = ""
            var returnPath = ""
            var from = ""
            var replyTo = ""
            var messageHTML = ""
            val unread: Boolean = messageDetails.labelIds.contains("UNREAD")

            for (header in messageDetails.payload.headers)
            {

                val headerName = header.name

                if (headerName == "To")
                {
                    to = header.value
                }
                else if (headerName == "Subject")
                {
                    subject = header.value

                }
                else if (headerName == "Return-Path")
                {
                    returnPath = header.value
                }
                else if (headerName == "From")
                {
                    from = header.value
                }
                else if (headerName == "Reply-to")
                {
                    replyTo = header.value
                }

            }

            var temp = ""

            var mimeType = messageDetails.payload.mimeType
            var parts = messageDetails.payload.parts

            when (mimeType)
            {
                "text/plain" ->
                {
                    temp = messageDetails.payload.body.data
                }
                "text/html"  ->
                {
                    temp = messageDetails.payload.body.data
                }
                else         ->
                {
                    for (item in parts)
                    {
                        temp = getBodyWithRecursion(item, "text/html")
                    }
                }
            }

            messageHTML = StringUtils.newStringUtf8(Base64.decodeBase64(temp))


            var theMessage = DataEmailItem(messageDetails.id,
                                           messageDetails.snippet,
                                           messageDetails.historyId,
                                           messageDetails.internalDate,
                                           messageDetails.labelIds,
                                           to,
                                           subject,
                                           returnPath,
                                           from,
                                           replyTo,
                                           messageHTML,
                                           unread)


            if (isFull)
            {
                for (label in messageDetails.labelIds)
                {
                    labelMessageList = DataMaster.currentAccount?.labelMessageMap?.get(label) ?: mutableListOf()
                    labelMessageList.add(theMessage)
                }
            }
            else
            {
                for (label in messageDetails.labelIds)
                {
                    labelMessageList = DataMaster.currentAccount?.labelMessageMap?.get(label) ?: mutableListOf()
                    labelMessageList.add(0, theMessage)
                }
            }

            if (messageDetails.historyId > DataMaster.currentAccount?.latestHistoryID)
            {
                DataMaster.currentAccount?.latestHistoryID = messageDetails.historyId
            }
        }

        override fun onPostExecute(result: List<String>?)
        {
            super.onPostExecute(result)
            swipeRefreshLayout.isRefreshing = false
            DataMaster.currentEmailList.clear()

            for (item in DataMaster.currentAccount?.labelMessageMap?.get(DataMaster.currentFolder) ?: mutableListOf())
            {
                DataMaster.currentEmailList.add(item)
            }

            recyclerView.adapter.notifyDataSetChanged()

            val x: DataAccount? = DataMaster.currentAccount
            if (x is DataAccount)
            {
                DataMaster.saveAccountData(x, this@MainActivity)
            }
            DataMaster.saveMasterData(this@MainActivity)

        }

        override fun onCancelled()
        {
            super.onCancelled()

            when (mLastError)
            {
                is UserRecoverableAuthIOException            ->
                {
                    val x: UserRecoverableAuthIOException? = mLastError as? UserRecoverableAuthIOException
                    startActivityForResult(x?.intent, REQUEST_AUTHORIZATION)
                }

                is GooglePlayServicesAvailabilityIOException ->
                {
                    val x: GooglePlayServicesAvailabilityIOException? = mLastError as? GooglePlayServicesAvailabilityIOException
                }

                is GoogleJsonResponseException               ->
                {
                    DataMaster.currentAccount?.latestHistoryID = BigInteger("0")
                    syncClient()

                }

                else                                         ->
                    Log.e("LastError Messages" , mLastError.toString())
            }

        }

    }

    //Updates label creations and removals on the server side
    inner class MakeLabelUpdateTask constructor(credential: GoogleAccountCredential, type:String, labelName : String) : AsyncTask<Void, Void, List<String>>()
    {
        private var mService: com.google.api.services.gmail.Gmail
        private val mType = type
        private val mLabel = labelName


        private lateinit var mLastError: Exception

        init
        {
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance()
            mService = com.google.api.services.gmail.Gmail.Builder(transport, jsonFactory, credential)
                    .setApplicationName("Gold Star Mail")
                    .build()
        }


        override fun doInBackground(vararg p0: Void?): List<String>?
        {
            return try
            {
                if(mType == "delete")
                {
                    removeLabel()
                }
                else if( mType == "add")
                {
                    addLabel()
                }

                return emptyList()
            }
            catch (e: Exception)
            {
                mLastError = e
                DataMaster.currentAccount?.latestHistoryID = BigInteger("0")
                cancel(true)
                null
            }
        }

        private fun addLabel()
        {
            var label = Label().setName(mLabel).setLabelListVisibility("labelShow").setMessageListVisibility("show")
            label = mService.users().labels().create("me", label).execute()

            if(label != null)
            {
                DataMaster.currentAccount!!.labelMessageMap.put(label.id, mutableListOf())
                DataMaster.currentAccount!!.labelIDMap.put(label.id, label.name)
                DataMaster.currentAccount!!.labelNameMap.put(label.name, label.id)
            }

        }

        private fun removeLabel()
        {
            var label = DataMaster.currentAccount!!.labelNameMap[mLabel]
            mService.users().labels().delete("me", mLabel).execute()
            DataMaster.currentAccount!!.labelNameMap.remove(mLabel)
            DataMaster.currentAccount!!.labelIDMap.remove(label)
        }

        override fun onPostExecute(result: List<String>?)
        {
            super.onPostExecute(result)

            MakeLabelRequestTask(mCredential).execute()

        }

        override fun onCancelled()
        {
            super.onCancelled()

            when (mLastError)
            {
                is UserRecoverableAuthIOException            ->
                {
                    val x: UserRecoverableAuthIOException? = mLastError as? UserRecoverableAuthIOException
                    startActivityForResult(x?.intent, REQUEST_AUTHORIZATION)
                }

                is GooglePlayServicesAvailabilityIOException ->
                {
                    val x: GooglePlayServicesAvailabilityIOException? = mLastError as? GooglePlayServicesAvailabilityIOException
                }

                is GoogleJsonError                           ->
                {
                    val x: GoogleJsonError? = mLastError as? GoogleJsonError
                }

                else                                         ->
                    Log.e("mLastError LabelUpdate" , mLastError.toString())
            }

        }

    }

}
