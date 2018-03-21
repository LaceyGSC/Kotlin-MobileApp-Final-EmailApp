package com.example.goldstar.goldstarmail

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.AsyncTask
import android.os.Handler
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.ModifyMessageRequest
import java.text.SimpleDateFormat
import java.util.*


/************************************************************
Created by Lacey Taylor
Date: Dec, 2017
Project: GoldStarMail
Description:
 Lovely little nightmare of a class that is handling a bit of everything.
 Adapter handles actions for the views in the recycler view

 Internal Class EmailViewHolder
    - View holder which connects the xml fields with the adapter

 Inner Class UpdateEmailStatus
    - ASync task class to handle small updates to individual emails. Used to
    update labels to mimic reading, deleting/trash, and moving



 ***********************************************************/
class CustomAdapter(context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>()
{
    val context = context

    private val PENDING_REMOVAL_TIMEOUT = 3000L //3 sec
    private val PENDING_ACTION_TIMEOUT = 4000L  //4 sec
    private var itemsPendingRemoval = mutableListOf<DataEmailItem>()
    private var itemsPendingAction = mutableListOf<DataEmailItem>()

    //runnable maps allow for multiple actions to take place at the same time
    //and for easy cancelling
    private var handler: Handler = Handler()
    private var pendingRunablesMap = mutableMapOf<DataEmailItem, Runnable>()
    private var pendingActionMap = mutableMapOf<DataEmailItem, Runnable>()

    private var targetFolder = ""

    private var onEmailItemSelectedListener: OnEmailItemSelectedListener? = null

    interface OnEmailItemSelectedListener
    {
        fun emailItemSelected(index: Int, gameItem: DataEmailItem)
    }

    fun setOnEmailItemSelectedListener(onEmailItemSelectedListener: OnEmailItemSelectedListener)
    {
        this.onEmailItemSelectedListener = onEmailItemSelectedListener
    }

    fun setOnEmailItemSelectedListener(onEmailItemSelectedListener: ((index: Int, emailItem: DataEmailItem) -> Unit))
    {
        this.onEmailItemSelectedListener = object : OnEmailItemSelectedListener
        {
            override fun emailItemSelected(index: Int, emailItem: DataEmailItem)
            {
                onEmailItemSelectedListener(index, emailItem)
            }
        }
    }

    var selectedItemIndex: Int = RecyclerView.NO_POSITION
        set(newSelectedItemIndex)
        {
            notifyItemChanged(field)
            field = newSelectedItemIndex
            notifyItemChanged(field)
        }


    override fun getItemCount(): Int
    {
        return DataMaster.currentEmailList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder
    {
        val layoutInflater: LayoutInflater = LayoutInflater.from(parent.context)


        return EmailViewHolder(layoutInflater.inflate(R.layout.email_item_row_view, parent, false) as ViewGroup)
                .apply {
                    mainCardLayout.setOnClickListener { clickedView: View ->

                        selectedItemIndex = adapterPosition
                        onEmailItemSelectedListener?.emailItemSelected(adapterPosition, DataMaster.currentEmailList[adapterPosition])

                        //This block determines if the message was previously unread, and marks as such once clicked
                        //Does this by moving labels around for gmail, and by updating a bool field for more sane people
                        if (DataMaster.currentEmailList[adapterPosition].unread)
                        {
                            if (DataMaster.currentAccount!!.labelMessageMap["UNREAD"]!!.contains(DataMaster.currentEmailList[adapterPosition]))
                            {
                                DataMaster.currentAccount!!.labelMessageMap["UNREAD"]!!.remove(DataMaster.currentEmailList[adapterPosition])
                                DataMaster.currentEmailList[adapterPosition].labels.remove("UNREAD")
                            }

                            //Starts update to server side, which removes UNREAD label from server copy of email
                            UpdateEmailStatus("read", DataMaster.currentEmailList[adapterPosition]).execute()

                            DataMaster.currentEmailList[adapterPosition].unread = false
                        }
                        else
                        {
                            //If somehow click on read message still in unread, clears it
                            DataMaster.currentAccount!!.labelMessageMap["UNREAD"]!!.remove(DataMaster.currentEmailList[adapterPosition])
                        }

                        //Activity to read the email that coresponds to clicked item
                        val intent = Intent(context, ReadEmailActivity::class.java)
                        intent.putExtra("Position", adapterPosition)
                        context.startActivity(intent)

                        //Instant removal if in the overall unread folder
                        if (DataMaster.currentFolder == "UNREAD")
                        {
                            notifyItemRemoved(adapterPosition)
                        }

                        //Saves account data every time something is clicked
                        DataMaster.saveAccountData(DataMaster.currentAccount, context)
                    }
                }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int)
    {
        var testViewHolder = holder as EmailViewHolder
        val item = DataMaster.currentEmailList[position]

        when
        {
            //This test means that if the view has to be rebound due to a scroll, it will still show the same pending/action/idle status
            itemsPendingRemoval.contains(item) -> //Shows the undo View
            {
                testViewHolder.itemView.background = ColorDrawable(context.resources.getColor(R.color.colorRedWarning))
                testViewHolder.mainCardLayout.visibility = View.GONE

                testViewHolder.submenuCardLayout.visibility = View.GONE
                setMenuButtonListeners(false, testViewHolder, position)

                testViewHolder.undoButton.visibility = View.VISIBLE
                testViewHolder.undoButton.setOnClickListener {

                    //If undo is clicked, grabs the runnable, removes it, checks to see if its still good then cancels the action
                    var pendingRemovalRunnable = pendingRunablesMap[item]
                    pendingRunablesMap.remove(item)
                    if (pendingRemovalRunnable != null)
                    {
                        handler.removeCallbacks(pendingRemovalRunnable)
                        itemsPendingRemoval.remove(item)
                        notifyItemChanged(position)
                    }

                }

            }
            itemsPendingAction.contains(item)  -> //Shows the menu view
            {
                testViewHolder.itemView.background = ColorDrawable(context.getColor(R.color.colorPrimary))
                testViewHolder.mainCardLayout.visibility = View.GONE

                testViewHolder.submenuCardLayout.visibility = View.VISIBLE
                setMenuButtonListeners(true, testViewHolder, position)

                testViewHolder.undoButton.visibility = View.GONE
                testViewHolder.undoButton.setOnClickListener(null)
            }
            else                               ->// Else see the normal email view
            {
                testViewHolder.itemView.background = ColorDrawable(Color.WHITE)
                testViewHolder.mainCardLayout.visibility = View.VISIBLE
                if (!item.unread)
                {
                    testViewHolder.mainCardLayout.background = ColorDrawable(context.resources.getColor(R.color.colorCardBackgroundRead))
                }
                else
                {
                    testViewHolder.mainCardLayout.background = ColorDrawable(context.resources.getColor(R.color.colorCardBackground))
                }

                var simpleDate = SimpleDateFormat("MMM d")
                var time = simpleDate.format(Date(item.internalDate))
                var fromName = item.from.split("<")

                testViewHolder.senderTextView.text = fromName[0]
                testViewHolder.firstLineTextView.text = item.snippet
                testViewHolder.subjectTextView.text = item.subject
                testViewHolder.timeDateTextView.text = time

                testViewHolder.submenuCardLayout.visibility = View.INVISIBLE
                setMenuButtonListeners(false, testViewHolder, position)

                testViewHolder.undoButton.visibility = View.GONE
                testViewHolder.undoButton.setOnClickListener(null)
            }
        }
    }

    //Sets listeners for the under menu each time it is brought into view
    //They are nulled when out of view
    private fun setMenuButtonListeners(active: Boolean, holder: RecyclerView.ViewHolder, index: Int)
    {
        var testViewHolder = holder as EmailViewHolder


        if (active)//Menu is visible
        {
            testViewHolder.moveButton.setOnClickListener {

                //Alert dialog pops up with list of folders to send to
                //Certain folders are restricted, cause people are stupid and will definatly put
                //random emails in the sent folder for fun
                val builder = AlertDialog.Builder(context)
                builder.setTitle("Move to folder...")

                var folderNames = mutableListOf<String>()

                val reservedNames = listOf("CATEGORY_PERSONAL",
                                           "CATEGORY_SOCIAL",
                                           "CATEGORY_UPDATES",
                                           "CATEGORY_FORUMS",
                                           "CHAT",
                                           "SENT",
                                           "Label_2",
                                           "CATEGORY_PROMOTIONS",
                                           "DRAFT",
                                           "STARRED",
                                           "UNREAD",
                                           "Label_1")


                for (labelName in DataMaster.currentAccount!!.labelMessageMap.keys)
                {
                    if (!reservedNames.contains(labelName) && labelName != DataMaster.currentFolder)
                    {
                        var folderName = DataMaster.currentAccount!!.labelIDMap[labelName] ?: ""
                        folderNames.add(folderName)
                    }
                }

                val items = folderNames.toTypedArray()

                builder.setItems(items, DialogInterface.OnClickListener { dialogInterface, i ->

                    //Gets folder name
                    //Updates email with new label
                    //Finishes moving them in the client
                    targetFolder = items[i]
                    UpdateEmailStatus("move", DataMaster.currentEmailList[index]).execute()
                    moveLabels(index)

                })

                var alert = builder.create()
                alert.show()
            }

            testViewHolder.replyButton.setOnClickListener {

                //Starts SendEmail Activity with prepopulated fields
                val intent = Intent(context, SendEmailActivity::class.java)
                intent.putExtra("Sender", DataMaster.currentAccount?.name)
                intent.putExtra("Position", index.toString())
                intent.putExtra("Type", "reply")
                context.startActivity(intent)

            }
        }
        else
        {

            testViewHolder.moveButton.setOnClickListener(null)
            testViewHolder.ruleButton.setOnClickListener(null)
            testViewHolder.replyButton.setOnClickListener(null)
            testViewHolder.todoButton.setOnClickListener(null)
        }
    }

    //Adds items to the map of pending removals
    fun pendingRemoval(position: Int)
    {
        val item = DataMaster.currentEmailList[position]
        if (!itemsPendingRemoval.contains(item))
        {
            itemsPendingRemoval.add(item)
            notifyItemChanged(position)

            var pendingRemovalRunnable = Runnable {

                kotlin.run {
                    remove(DataMaster.currentEmailList.indexOf(item))
                }
            }

            handler.postDelayed(pendingRemovalRunnable, PENDING_REMOVAL_TIMEOUT)
            pendingRunablesMap.put(item, pendingRemovalRunnable)
        }
    }

    //Adds items to map of pending actions
    fun pendingAction(position: Int)
    {
        val item = DataMaster.currentEmailList[position]
        if (!itemsPendingAction.contains(item))
        {
            itemsPendingAction.add(item)
            notifyItemChanged(position)

            var pendingActionRunnable = Runnable {
                kotlin.run {
                    returnAction(DataMaster.currentEmailList.indexOf(item))
                }
            }

            handler.postDelayed(pendingActionRunnable, PENDING_ACTION_TIMEOUT)
            pendingActionMap.put(item, pendingActionRunnable)
        }
    }

    //Called when runnable reaches end/time out
    //Clears it out, makes sure it exists
    //Then trashes it on the client side and the server side
    //by sending update to trash label
    fun remove(position: Int)
    {
        val item = DataMaster.currentEmailList[position]

        if (isPendingAction(position))
        {
            var pendingActionRunnable = pendingActionMap[item]
            pendingActionMap.remove(item)

            if (pendingActionRunnable != null)
            {
                handler.removeCallbacks(pendingActionRunnable)
                itemsPendingAction.remove(item)
                notifyItemChanged(position)
            }
        }

        if (itemsPendingRemoval.contains(item))
        {
            itemsPendingRemoval.remove(item)
        }

        if (DataMaster.currentEmailList.contains(item))
        {
            UpdateEmailStatus("delete", item).execute()

            for (label in item.labels)
            {
                if (DataMaster.currentAccount!!.labelMessageMap[label]!!.contains(item))
                {
                    DataMaster.currentAccount!!.labelMessageMap[label]!!.remove(item)
                }
            }

            item.labels.clear()
            item.labels.add("TRASH")
            DataMaster.currentAccount!!.labelMessageMap["TRASH"]!!.add(item)

            DataMaster.currentEmailList.removeAt(position)
            notifyItemRemoved(position)
        }

    }

    //called at end of action runnable to revert to normal
    fun returnAction(position: Int)
    {
        val item = DataMaster.currentEmailList[position]
        if (itemsPendingAction.contains(item))
        {
            itemsPendingAction.remove(item)
        }

        if (DataMaster.currentEmailList.contains(item))
        {
            notifyItemChanged(position)
        }
    }

    fun isPendingRemoval(position: Int): Boolean
    {
        val item = DataMaster.currentEmailList[position]
        return itemsPendingRemoval.contains(item)
    }

    fun isPendingAction(position: Int): Boolean
    {
        val item = DataMaster.currentEmailList[position]
        return itemsPendingAction.contains(item)
    }

    //Moves the labels internally to match changes
    //Checks to see if email exists in location first, then removes
    //or deletes it as needed
    fun moveLabels(position: Int)
    {
        val item = DataMaster.currentEmailList[position]

        var pendingRemovalRunnable = pendingActionMap[item]
        pendingActionMap.remove(item)
        if (pendingRemovalRunnable != null)
        {
            handler.removeCallbacks(pendingRemovalRunnable)
            itemsPendingAction.remove(item)
            notifyItemChanged(position)
        }

        if(item.labels.contains(DataMaster.currentFolder))
        {
            item.labels.remove(DataMaster.currentFolder)
        }
        if(DataMaster.currentAccount!!.labelMessageMap[DataMaster.currentFolder]!!.contains(item))
        {
            DataMaster.currentAccount!!.labelMessageMap[DataMaster.currentFolder]!!.remove(item)
        }

        if(!item.labels.contains(targetFolder))
        {
            item.labels.add(targetFolder)
        }

        if(!DataMaster.currentAccount!!.labelMessageMap[DataMaster!!.currentAccount!!.labelNameMap[targetFolder]]!!.contains(item))
        {
            DataMaster.currentAccount!!.labelMessageMap[DataMaster!!.currentAccount!!.labelNameMap[targetFolder]]!!.add(item)
        }

        DataMaster.currentEmailList.removeAt(position)
        notifyItemRemoved(position)
    }

    internal class EmailViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.email_item_row_view, parent, false))
    {

        var mainCardLayout: LinearLayout = itemView.findViewById(R.id.mainCardLayout)
        var submenuCardLayout: LinearLayout = itemView.findViewById(R.id.submenuCardLayout)

        var senderTextView: TextView = itemView.findViewById(R.id.senderCardTextView)
        var firstLineTextView: TextView = itemView.findViewById(R.id.firstlineCardTextView)
        var timeDateTextView: TextView = itemView.findViewById(R.id.timeDateCardTextView)
        var subjectTextView: TextView = itemView.findViewById(R.id.subjectCardTextView)

        var moveButton: Button = itemView.findViewById(R.id.move_button)
        var ruleButton: Button = itemView.findViewById(R.id.rule_button)
        var replyButton: Button = itemView.findViewById(R.id.reply_button)
        var todoButton: Button = itemView.findViewById(R.id.todo_button)

        var undoButton: Button = itemView.findViewById(R.id.undo_button)

    }

    inner class UpdateEmailStatus(type: String, item: DataEmailItem) : AsyncTask<Void, Void, List<String>>()
    {

        private var mService: com.google.api.services.gmail.Gmail
        private lateinit var mLastError: Exception
        private val item = item
        var type = type

        init
        {
            //Info used for gmail connection/authentication
            val SCOPES = arrayOf(GmailScopes.MAIL_GOOGLE_COM)
            val credential = GoogleAccountCredential.usingOAuth2(context.applicationContext, SCOPES.asList()).setBackOff(ExponentialBackOff())
            credential.selectedAccountName = DataMaster.currentAccount!!.name
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance()
            mService = com.google.api.services.gmail.Gmail.Builder(transport, jsonFactory, credential)
                    .setApplicationName("Gold Star Mail")
                    .build()

        }

        //Determines what type of action to run
        override fun doInBackground(vararg params: Void?): List<String>
        {
            try
            {
                when (type)
                {
                    "delete" ->
                    {
                        deleteEmail()
                    }
                    "read"   ->
                    {
                        readEmail()
                    }
                    "move"   ->
                    {
                        moveEmail()
                    }
                }

            }
            catch (ex: Exception)
            {
                mLastError = ex
                Log.e("Send Last Error", mLastError.toString())
                cancel(true)
                return emptyList()
            }

            return emptyList()
        }

        //Trashing gets its own method, removes all other labels
        private fun deleteEmail()
        {
            mService.users().messages().trash("me", item.id).execute()

        }

        //Reading is done by removing the unread label from any one message
        //(whatever works, but it sounds stupid to me when I thing it)
        private fun readEmail()
        {
            var list = mutableListOf<String>()
            var mods = ModifyMessageRequest().setRemoveLabelIds(list)
            mService.users().messages().modify("me", item.id, mods).execute()


        }

        //Removes one label, adds corresponding new label, sends update of both
        //at the same time
        private fun moveEmail()
        {
            var listRemove = mutableListOf<String>()
            listRemove.add(DataMaster.currentFolder)

            var listAdd = mutableListOf<String>()
            listAdd.add(DataMaster.currentAccount!!.labelNameMap[targetFolder] ?: "INBOX")

            var mods = ModifyMessageRequest().setRemoveLabelIds(listRemove).setAddLabelIds(listAdd)
            mService.users().messages().modify("me", item.id, mods).execute()

        }

    }

}