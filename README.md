# 自定义`View`理论分析与自定义`ViewGroup`之流式布局

[TOC]

## 一、自定义`View`理论

### 1.1 自定义`View`的分类

自定义`View`主要分为两类：自定义`View`和自定义`ViewGroup`。

#### 1.1.1 自定义`View`

在没有现成的`View`，需要自己实现的时候，就使用自定义`View`，一般继承自`View`，`SurfaceView`或其它的`View`。

#### 1.1.2 自定义`ViewGroup`

自定义`ViewGroup`一般是利用现有的组件，根据特定的布局方式来组成新的组件，大多继承自`ViewGroup`或各种`Layout`。

### 1.2 自定义`View`核心要素

自定义`View`的核心在于测量（onMeasure）、布局（onLayout）和绘制（onDraw），分先后顺序。  

![image](https://github.com/tianyalu/XxtFlowLayout/raw/master/show/custom_view_key_point.png)  

> 1. 自定义`View`主要是实现`onMeasure()`和`onDraw()`;  
> 2. 自定义`ViewGroup`主要是实现`onMeasure()`和`onLayout()`。

#### 1.2.1 `onMeasure()` 之`MeasureSpec`

`MeasureSpec` (32位`int`类型) = `Mode(2) + Size(30)` 。  

```java
private static final int MODE_SHIFT = 30;
private static final int MODE_MASK  = 0x3 << MODE_SHIFT;
public static final int UNSPECIFIED = 0 << MODE_SHIFT;
public static final int EXACTLY     = 1 << MODE_SHIFT;
public static final int AT_MOST     = 2 << MODE_SHIFT;
public static int makeMeasureSpec(@IntRange(from = 0, to = (1 << MeasureSpec.MODE_SHIFT) - 1) 	int size, @MeasureSpecMode int mode) {
  if (sUseBrokenMakeMeasureSpec) {
    return size + mode;
  } else {
    return (size & ~MODE_MASK) | (mode & MODE_MASK);
  }
}
```

`MeasureSpec`是`View`中的内部类，基本上都是二进制运算。由于`int`是32位的，用高两位表示`mode`，低30位表示`size`，`MODE_SHIFT` = 30 的作用是移位。

`Mode`含义如下：  

> `UNSPECIFIED`: 不对`View`大小做限制，系统使用；  
> `EXACTLY`: 确切的大小，如100dp；  
> `AT_MOST`: 大小不可超过某数值，如`match_parent`表示最大不能超过父`View`的最大`Size`。

普通`View`的`MeasureSpec`的创建规则如下表所示：  

| childLayoutParams\parentSpecMode |       EXACTLY        |       AT_MOST        |     UNSPECIFIED     |
| :------------------------------: | :------------------: | :------------------: | :-----------------: |
|              dp/px               | EXACTLY (childSize)  | EXACTLY (childSize)  | EXACTLY (childSize) |
|           match_parent           | EXACTLY (parentSize) | AT_MOST (parentSize) |   UNSPECIFIED (0)   |
|           wrap_content           | AT_MOST (parentSize) | AT_MOST (parentSize) |   UNSPECIFIED (0)   |

### 1.3 自定义`View`绘制流程

#### 1.3.1 自定义`View`绘制流程图

![image](https://github.com/tianyalu/XxtFlowLayout/raw/master/show/custom_view_process.png)  

测量时自上而下进行测量：  

![image](https://github.com/tianyalu/XxtFlowLayout/raw/master/show/custom_view_tree_travesal.png)  

1.3.2 自定义`View`绘制时序图

![image](https://github.com/tianyalu/XxtFlowLayout/raw/master/show/custom_view_sequence.png)  

### 1.4 补充要点

#### 1.4.1 视图坐标系

`Android`屏幕坐标系：  

![image](https://github.com/tianyalu/XxtFlowLayout/raw/master/show/android_screen_coordinate_system.png)  

`View`视图坐标系：  

![image](https://github.com/tianyalu/XxtFlowLayout/raw/master/show/view_coordinate_system.png)  

#### 1.4.2 `view` 获取宽度区别

`getMeasuredWidth()`:  

> 通过`setMeasuredDimension()`方法进行设置；  
> 在`measure()`过程结束后就可以获取到对应的值。

`getWidth()`:  

> 通过视图右边的坐标减去左边的坐标计算出来；  
> 在`layout()`过程结束后才能获取到对应的值。

#### 1.4.3 `Fragment  getActivity() == null`

`Fragment` 在 `attach() ---> detach()` 生命周期期间获取到的`getActivity()`才有值，在此之外为`null`。

### 1.5 问题

#### 1.5.1 `View` 调用`onMeasure()`至少两次的原因

以`Android API 28（9.0）` 为例，`ViewRootImpl#performTraversals()`代码片段如下：

```java
//...
// Ask host how big it wants to be -->第一次
performMeasure(childWidthMeasureSpec, childHeightMeasureSpec); //大约2394行
//...
//1.由于第一次执行newSurface必定为true，需要先创建Surface嘛
//为true则会执行else语句，所以第一次执行并不会执行 performDraw方法，即View的onDraw方法不会得到调用
//第二次执行则为false，并未创建新的Surface，第二次才会执行 performDraw方法
if (!cancelDraw && !newSurface) {  //2607行
  if (!skipDraw || mReportNextDraw) {
    if (mPendingTransitions != null && mPendingTransitions.size() > 0) {
      for (int i = 0; i < mPendingTransitions.size(); ++i) {
        mPendingTransitions.get(i).startChangingAnimations();
      }
      mPendingTransitions.clear();
    }

    performDraw();
  }
} else {
  //2.viewVisibility是wm.add的那个View的属性，View的默认值都是可见的
  if (viewVisibility == View.VISIBLE) {
    // Try again
    //3.再执行一次 scheduleTraversals，也就是会再执行一次performTraversals --> 第二次
    scheduleTraversals();
  } else if (mPendingTransitions != null && mPendingTransitions.size() > 0) {
    for (int i = 0; i < mPendingTransitions.size(); ++i) {
      mPendingTransitions.get(i).endChangingAnimations();
    }
    mPendingTransitions.clear();
  }
}
```

参考：[View为什么会至少进行2次onMeasure、onLayout](https://www.jianshu.com/p/733c7e9fb284)  

#### 1.5.2 子`View`的`size`大于父`ViewGroup`的`size`时的表现

 `MeasureSpec`父子`Mode`都为`EXACTLY`，此时子`View`的`size`大于父`ViewGroup`的`size`时，实际显示以父`ViewGroup`（以`LinearLayout`为例）大小为准，子`View`大小依然为其原来指定大小，但超出父容器部分不展示。

以`Android API 29（10.0）` 为例，`LinearLayout$measureHorizontal()`源码片段如下：  

```java
void measureHorizontal(int widthMeasureSpec, int heightMeasureSpec) {
  //...
  // Reconcile our calculated size with the widthMeasureSpec
  int widthSizeAndState = resolveSizeAndState(widthSize, widthMeasureSpec, 0); //1333行
	//...
  setMeasuredDimension(widthSizeAndState | (childState&MEASURED_STATE_MASK),  //1479行
                       resolveSizeAndState(maxHeight, heightMeasureSpec,
                                           (childState<<MEASURED_HEIGHT_STATE_SHIFT)));
  //...
}
```

`resolveSizeAndState()`源码：  

```java
public static int resolveSizeAndState(int size, int measureSpec, int childMeasuredState) {
  final int specMode = MeasureSpec.getMode(measureSpec);
  final int specSize = MeasureSpec.getSize(measureSpec);
  final int result;
  switch (specMode) {
    case MeasureSpec.AT_MOST:
      if (specSize < size) {
        result = specSize | MEASURED_STATE_TOO_SMALL;
      } else {
        result = size;
      }
      break;
    case MeasureSpec.EXACTLY: //注意这里
      result = specSize;  
      break;
    case MeasureSpec.UNSPECIFIED:
    default:
      result = size;
  }
  return result | (childMeasuredState & MEASURED_STATE_MASK);
}
```



## 二、自定义`ViewGroup`之流式布局实现

### 2.1 实现效果

自定义流式布局效果如下图所示：  

![image](https://github.com/tianyalu/XxtFlowLayout/raw/master/show/show.png)  

### 2.2 实现步骤

自定义`ViewGroup`主要需要实现视图测量（`onMeasure()`）和子视图布局（`onLayout()`）。

#### 2.2.1 视图测量`onMeasure()`

测量时先测量`ViewGroup`自己的宽高（`selfWidth,selfHeight`），然后遍历并测量子`View`的大小，将子`View`及其测量结果保存下来，一个一个处理，用子`View`的宽度+设置的间隔 和`ViewGroup`的宽度做比较来决定是否需要换行。换行时累积每行中最大子`View`的高度`parentNeededHeight`，记录每行最大实际宽度`parentNeededWidth`，最后通过`MeasureSpec`和 `ViewGroup`测量的宽高以及子`View`所需要的宽高来最终决定`ViewGroup`的实际宽高，并通过`setMeasuredDimension()`方法设置。

```java
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
```

#### 2.2.2 视图布局`onLayout()`

根据上一步测量的结果对每一个子`View`布局，通过`view.layout()`方法为其设置具体坐标。

```java
/**
 * 将所有的子View布局到屏幕上，同时，由于FlowLayout自己没有特殊要求，所以不需要对自己布局
 */
@Override
protected void onLayout(boolean changed, int l, int t, int r, int b) {
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
```

