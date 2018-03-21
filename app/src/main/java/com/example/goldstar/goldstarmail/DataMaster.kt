package com.example.goldstar.goldstarmail

import android.content.Context
import android.util.Log
import fromFile
import toFile
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/************************************************************
Created by Lacey Taylor
Date: Nov, 2017
Project: GoldStarMail
Description:
Master singleton to contain the list of account object that have
been setup and initialized from server/saved data


 ***********************************************************/
object DataMaster
{
    var currentAccount: DataAccount? = null
    var currentFolder : String = "INBOX"
    var accountList: MutableList<DataAccount> = mutableListOf()
    var currentEmailList: MutableList<DataEmailItem> = mutableListOf()

    var currentAccountName = currentAccount?.name ?: ""
    var currentFolderName = currentFolder
    var accountListCount = accountList.size


    private const val datasetDirectoryName : String = "CachedData"
    private const val dataExtention : String = ".gsm"
    private const val masterExtention : String = ".mst"

    fun saveMasterData(context : Context)
    {
        val filepath = File(context.filesDir, datasetDirectoryName)
        if(!filepath.exists())
        {
            filepath.mkdirs()
        }

        val itemFilePath = File(filepath.absolutePath + File.separator + "MasterInfo" + masterExtention)

        currentAccountName = currentAccount?.name ?: ""
        currentFolderName = currentFolder
        accountListCount = accountList.size

        var toWrite = currentAccountName + "\n" + currentFolderName + "\n" + accountListCount

        if(itemFilePath.exists())
        {
            itemFilePath.delete()
            toWrite.toFile(itemFilePath.toString(), false)
        }
        else
        {
            toWrite.toFile(itemFilePath.toString(), false)
        }

    }

    fun loadMasterData(context : Context)
    {
        val filepath = File(context.filesDir, datasetDirectoryName)
        val itemFilePath = File(filepath.absolutePath + File.separator + "MasterInfo" + masterExtention)

        if(itemFilePath.exists())
        {
            var fis = FileInputStream(itemFilePath)
            var bfr = BufferedReader(InputStreamReader(fis))

            currentAccountName = bfr.readLine()
            currentFolderName = bfr.readLine()
            accountListCount = bfr.readLine().toInt()
        }
        else
        {
            currentAccountName = ""
            currentFolderName = "INBOX"
            accountListCount = 0
        }

    }

    fun saveAccountData(account : DataAccount?,context : Context)
    {
        val filePath = File(context.filesDir, datasetDirectoryName)

        if (!filePath.exists())
        {
            filePath.mkdirs()
        }

        val itemFilePath = File(filePath.absolutePath + File.separator + account?.name + dataExtention)

        if(itemFilePath.exists())
        {
            itemFilePath.delete()
            account?.toJSON()?.toFile(itemFilePath.toString(), false)
        }
        else
        {
            account?.toJSON()?.toFile(itemFilePath.toString(), false)
        }

    }

    fun loadAccountData(context : Context)
    {
        val filePath = File(context.filesDir, datasetDirectoryName)

        var count = 0

        if(filePath.exists())
        {
            accountList.clear()
            for(file in filePath.listFiles())
            {
                if(file.absolutePath.endsWith(".mst"))
                {

                }
                else
                {
                    accountList.add(DataAccount.fromJSON(String.fromFile(file.toString()) ?: ""))
                }
                Log.e("Number of accounts", count.toString())
                count++
            }
        }

    }

    fun deleteAccountData(account: DataAccount?, context: Context)
    {
        val filePath = File(context.filesDir, datasetDirectoryName)
        val itemFilePath = File(filePath.absolutePath + File.separator + account?.name + dataExtention)

        if(itemFilePath.exists())
        {
            itemFilePath.delete()
            accountList.remove(account)
        }
    }
}