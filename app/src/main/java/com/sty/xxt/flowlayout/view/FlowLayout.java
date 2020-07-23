package com.sty.xxt.flowlayout.view;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;

public class FlowLayout  extends ViewGroup {
    private static final String TAG = FlowLayout.class.getSimpleName();
    private int mHorizontalSpacing = dp2px(16); //每个item横向间距
    private int mVerticalSpacing = dp2px(8); //每个item纵向间距
    private List<List<View>> allLines; //记录所有的行，一行一行地存储
    private List<Integer> lineHeights; //记录每一行的行高
    private List<View> lineViews; //记录一行中的所有View

    public FlowLayout(Context context) {
        this(context, null);
    }

    public FlowLayout(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlowLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public FlowLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initMeasureParams();
    }

    private void initMeasureParams() {
        allLines = new ArrayList<>();
        lineHeights = new ArrayList<>();
        lineViews = new ArrayList<>();
    }

    private void resetMeasureParams() {
        allLines.clear();
        lineHeights.clear();
        lineViews.clear();
    }

    /**
     * 测量
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) { //FlowLayout自己的MeasureSpec
        resetMeasureParams();

        //度量所有的子view
        int childCount = getChildCount();
        //获取本控件的padding
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();

        int selfWidth = MeasureSpec.getSize(widthMeasureSpec); //ViewGroup解析的宽度   1032
        int selfHeight = MeasureSpec.getSize(heightMeasureSpec); //ViewGroup解析的高度 1559
        Log.d(TAG, "onMeasure ----> " + selfWidth + "   " + selfHeight);

        int lineWidthUsed = 0; //记录这行已经使用了多宽的size
        int lineHeight = 0;  //一行的行高

        int parentNeededWidth = 0;  // measure过程中，子View要求的父ViewGroup的宽
        int parentNeededHeight = 0; // measure过程中，子View要求的父ViewGroup的高

        //度量孩子
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);

            LayoutParams childLP = childView.getLayoutParams();
            //将layoutParams转变成为measureSpec
            int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                    paddingLeft + paddingRight, childLP.width);
            int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                    paddingTop + paddingBottom, childLP.height);

            childView.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            //获取子view的度量宽高
            int childMeasuredWidth = childView.getMeasuredWidth();
            int childMeasuredHeight = childView.getMeasuredHeight();

            //通过宽度来判断是否需要换行，通过换行后每行的行高来获取整个ViewGroup的行高
            if(childMeasuredWidth + lineWidthUsed + mHorizontalSpacing > selfWidth) {
                allLines.add(lineViews);
                lineHeights.add(lineHeight);

                //一旦换行，我们就可以判断当前行需要的宽和高了，所以此时要记录下来
                parentNeededHeight = parentNeededHeight + lineHeight + mVerticalSpacing;
                parentNeededWidth = Math.max(parentNeededWidth, lineWidthUsed + mHorizontalSpacing);

                lineViews = new ArrayList<>();  //因为引用的缘故，这里不能用clear()，只能new
                lineWidthUsed = 0;
                lineHeight = 0;
            }

            //view 是分行layout的，所以要记录每一行有哪些view，这样可以方便layout布局
            lineViews.add(childView);
            //每行都会有自己的宽和高
            lineWidthUsed = lineWidthUsed + childMeasuredWidth + mHorizontalSpacing;
            lineHeight = Math.max(lineHeight, childMeasuredHeight);

            //如果当前childView是最后一行的最后一个
            if(i == childCount - 1) { //最后一个
                lineHeights.add(lineHeight);
                allLines.add(lineViews);
                parentNeededWidth = Math.max(parentNeededWidth, lineWidthUsed);
                parentNeededHeight += lineHeight;
            }
        }

        // 根据子View的度量结果，来重新度量自己ViewGroup
        // 作为一个ViewGroup，它自己也是一个View，它的大小也需要根据它的父亲给它提供的宽高来度量
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int realWidth = (widthMode == MeasureSpec.EXACTLY) ? selfWidth : parentNeededWidth;    // 1032:1018 --> 1018
        int realHeight = (heightMode == MeasureSpec.EXACTLY) ? selfHeight : parentNeededHeight;// 1559:492  --> 492

        //store度量自己后的结果
        setMeasuredDimension(realWidth, realHeight);
    }


    /**
     * 将所有的子View布局到屏幕上，同时，由于FlowLayout自己没有特殊要求，所以不需要对自己布局
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Log.d(TAG, "onLayout ----> ");
        int lineCount = allLines.size();

        int curL = 0;
        int curT = 0;
        View view;
        for (int i = 0; i < lineCount; i++) {
            lineViews = allLines.get(i);
            int lineHeight = lineHeights.get(i);
            for (int j = 0; j < lineViews.size(); j++) {
                view = lineViews.get(j);
                int left = curL;
                int top = curT;

                int right = left + view.getMeasuredWidth();
                int bottom = top + view.getMeasuredHeight();
                view.layout(left, top, right, bottom);
                curL = right + mHorizontalSpacing;
            }
            curL = 0;
            curT = curT + lineHeight + mVerticalSpacing;
        }
    }

    public static int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Log.d(TAG, "onSizeChanged");
    }
}
