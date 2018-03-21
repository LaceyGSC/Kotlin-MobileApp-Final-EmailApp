package com.example.goldstar.goldstarmail

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.support.v7.widget.RecyclerView
import android.view.View

/************************************************************
Created by Lacey Taylor
Date: Dec, 2017
Project: GoldStarMail
Description:
    Decoration used to modify the behavior of ItemTouchHelper
    Does two things, One, it makes the deletion animation smoother during
    translation, and also seems to interfer with ItemTouchHelpers internal
    animation, which removes things with wild abandon

 ***********************************************************/
class DecorationAnimation(context : Context) : RecyclerView.ItemDecoration()
{
    val background = ColorDrawable(context.resources.getColor(R.color.colorRedWarning))

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State)
    {
        //If deletion animaiton was started by parent
        if(parent.itemAnimator.isRunning)
        {
            //Either view can be moving at the same time, so both need to be checked
            var lastViewComingDown : View? = null
            var firstViewComingUp : View? = null

            val left = 0
            val right = parent.width

            var top = 0
            var bottom = 0

            var childCount = parent.childCount

            //Checks to see who is moving what direction, sets that as the moving view
            for(index in 0 until childCount-1)
            {
                var child = parent.layoutManager.getChildAt(index)
                if(child != null)
                {
                    if(child.translationY < 0)
                    {
                        lastViewComingDown = child
                    }
                    else if(child.translationY > 0)
                    {
                        if(firstViewComingUp == null)
                        {
                            firstViewComingUp = child
                        }
                    }
                }
            }

            //determines where to start measuring based on who is moving where
            if(lastViewComingDown != null && firstViewComingUp != null)
            {
                top = lastViewComingDown.bottom + lastViewComingDown.translationY.toInt()
                bottom = firstViewComingUp.top + firstViewComingUp.translationY.toInt()
            }
            else if(lastViewComingDown != null)
            {
                top = lastViewComingDown.bottom + lastViewComingDown.translationY.toInt()
                bottom = lastViewComingDown.bottom
            }
            else if(firstViewComingUp != null)
            {
                top = firstViewComingUp.top
                bottom = firstViewComingUp.bottom + firstViewComingUp.translationY.toInt()
            }

            background.setBounds(left,top,right,bottom)
            background.draw(c)
        }
        super.onDraw(c, parent, state)
    }
}