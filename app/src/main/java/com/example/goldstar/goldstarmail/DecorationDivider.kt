package com.example.goldstar.goldstarmail

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.v7.widget.RecyclerView
import android.view.View

/************************************************************
Created by Lacey Taylor
Date: Nov, 2017
Project: GoldStarMail
Description:
    Simple divider override to make custom color for
    dividing line between email cards

 ***********************************************************/
class DecorationDivider : RecyclerView.ItemDecoration()
{
    private lateinit var mDivider : Drawable

    fun DividerItemDecoration(divider : Drawable)
    {
        mDivider = divider
    }

    override fun getItemOffsets(outRect: Rect?, view: View?, parent: RecyclerView?, state: RecyclerView.State?)
    {
        super.getItemOffsets(outRect, view, parent, state)

        if(parent?.getChildAdapterPosition(view) == 0)
        {
            return
        }

        outRect?.top = mDivider.intrinsicHeight
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State)
    {
        super.onDraw(c, parent, state)

        var dividerLeft = parent.paddingLeft
        var dividerRight = parent.width - parent.paddingRight

        var childCount = parent.childCount

        for(count in 0..childCount)
        {
            var child = parent.getChildAt(count)

            var params : RecyclerView.LayoutParams = child.layoutParams as RecyclerView.LayoutParams

            var dividerTop = child.bottom + params.bottomMargin
            var dividerBottom = dividerTop + mDivider.intrinsicHeight

            mDivider.setBounds(dividerLeft,dividerTop,dividerRight,dividerBottom)
            mDivider.draw(c)
        }
    }
}