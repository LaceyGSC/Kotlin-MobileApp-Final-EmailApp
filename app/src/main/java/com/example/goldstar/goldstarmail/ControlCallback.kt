package com.example.goldstar.goldstarmail

import android.content.Context
import android.graphics.Canvas
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.MotionEvent

/************************************************************
Created by Lacey Taylor
Date: Dec, 2017
Project: GoldStarMail
Description:
 Custom extention of the ItemTouchHelper Simple callback, which is used
 to detect swipes of the viewHolder, which direction, and to hand that info
 off to the adapter to start handling menu or removal

 ***********************************************************/
class ControlCallback(context : Context, var mAdapter: CustomAdapter) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
{
    enum class ButtonState
    {
        GONE,
        LEFT_SWIPE, //Go left, shows undo
        RIGHT_SWIPE // Go right, shows menu
    }

    var context : Context = context //Weird things start happening if you use the one in the constructor, declared local to avoid

    private var buttonShowState = ButtonState.GONE


    //Used for drag and drop, don't need for this project
    override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?): Boolean
    {
        return false
    }

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int
    {
        var position = viewHolder.adapterPosition
        var testAdapter = recyclerView.adapter as CustomAdapter

        //Returns no direction if item is pending removal or action, stops it from allowing another action
        //to take place till it slides back or is gone
        if(testAdapter.isPendingRemoval(position) || testAdapter.isPendingAction(position))
        {
            return 0
        }

        return super.getSwipeDirs(recyclerView, viewHolder)
    }

    //Adds to adapters list of pending, depending on which direction the swipe was detected
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int)
    {
        var position = viewHolder.adapterPosition

        if(buttonShowState == ButtonState.LEFT_SWIPE)
        {
            mAdapter.pendingRemoval(position)
        }
        else if(buttonShowState == ButtonState.RIGHT_SWIPE)
        {
            mAdapter.pendingAction(position)
        }

    }

    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean)
    {
        var itemView = viewHolder.itemView

        //onChildDraw calls twice for some reason, this return prevents if from calling on an older view
        if(viewHolder.adapterPosition == -1)
        {
            return
        }

        //If view is being swiped, starts listeners to detect direction, velocity, ect
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE)
        {
            setTouchListener(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }


    private fun setTouchListener(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean)
    {

        recyclerView.setOnTouchListener({ v, event ->


                                            if (dX == 0F)
                                            {
                                                buttonShowState = ButtonState.GONE
                                            }
                                            if (dX < 0)
                                            {
                                                buttonShowState = ButtonState.LEFT_SWIPE
                                            }
                                            else if (dX > 0)
                                            {
                                                buttonShowState = ButtonState.RIGHT_SWIPE
                                            }

                                            if (buttonShowState != ButtonState.GONE)
                                            {
                                                setTouchDownListener(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                                            }

                                            false
                                        })
    }

    private fun setTouchDownListener(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean)
    {

        recyclerView.setOnTouchListener { v, event ->

            if (event.action == MotionEvent.ACTION_DOWN)
            {
                setTouchUpListener(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            false
        }
    }

    private fun setTouchUpListener(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean)
    {

        recyclerView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP)
            {
                onChildDraw(c, recyclerView, viewHolder, 0f, dY, actionState, isCurrentlyActive)
                recyclerView.setOnTouchListener { v, event -> false }

                buttonShowState = ButtonState.GONE
            }
            false
        }
    }



}