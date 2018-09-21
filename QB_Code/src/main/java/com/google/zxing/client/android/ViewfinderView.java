/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 * 翻译：这是个覆盖上照相机上层的view,他不但添加了矩形的选景框，还有带动画的点的扫描动画
 * 功能：zxing自自定义的扫描选景框
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

    private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
    private static final long ANIMATION_DELAY = 80L;   // 延时时间
    private static final int CURRENT_POINT_OPACITY = 0xA0;
    private static final int MAX_RESULT_POINTS = 20;   // 麻点的最多数量是20个
    private static final int POINT_SIZE = 6;  // 麻点的半径

    private CameraManager cameraManager;
    private final Paint paint;
    private Bitmap resultBitmap;
    private final int maskColor;
    private final int resultColor;
    private final int laserColor;
    private final int resultPointColor;
    private int scannerAlpha;
    private List<ResultPoint> possibleResultPoints;
    private List<ResultPoint> lastPossibleResultPoints;

    private Bitmap scanLight; // 扫描线bitmap
    private int scanLineTop; // 扫描线移动的y
    private int SCAN_VELOCITY; // 扫描线移动速度
    private boolean isCircle; // 是否展示小圆点

    // This constructor is used when the class is built from an XML resource.
    // 这个构造方法，是在xml中构建的
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        // 一次定义，全局使用
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);   // 创建一个画笔
        Resources resources = getResources();       // 获取本地的资源
        maskColor = resources.getColor(R.color.viewfinder_mask);  //界面的背景颜色(扫描框后面的背景颜色)
        resultColor = resources.getColor(R.color.result_view);
        laserColor = resources.getColor(R.color.viewfinder_laser); // 设置二维码的扫描线
        resultPointColor = resources.getColor(R.color.possible_result_points); //扫描线周围的闪烁的点的颜色
        scannerAlpha = 0;
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = null;

        initInnerRect(context, attrs);  //
    }


    /**
     * 功能：提供给外面调用的方法，将Cameramanager对象引入
     * @param cameraManager
     */
    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    @SuppressLint("DrawAllocation")
    @Override
    public void onDraw(Canvas canvas) {
        if (cameraManager == null) {
            return; // not ready yet, early draw before done configuring
        }
        Rect frame = cameraManager.getFramingRect();  // 从cameraManager中获取设定的扫描窗口的大小
        Rect previewFrame = cameraManager.getFramingRectInPreview();
        if (frame == null || previewFrame == null) {
            return;
        }
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        Log.d("zwk", "canvas" + "widht" + width + "---height" + height);

        //这里的frame是扫描框的大小
        paint.setColor(resultBitmap != null ? resultColor : maskColor); // 为画笔设置颜色，这里是画扫描框周围的矩形背景
        Log.d("zwk", frame.top + "--" + frame.bottom + "--" + frame.left + "--" + frame.right);
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);

        drawFourCorner(canvas, frame);  // 为扫描框绘制四个角

        if (resultBitmap != null) {  //扫描结果后，返回的图片不为空
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(CURRENT_POINT_OPACITY);  // 设置画笔的透明度

            canvas.drawBitmap(null, null, frame, paint);  // 绘制这个矩形
        } else {

            // Draw a red "laser scanner" line through the middle to show decoding is active
            // 在中间位置画一条红色的扫描线
            paint.setColor(laserColor);  // 设置激光线的颜色
            paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);  // 设置画笔的透明度
            scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;  // 透明度的值=在透明度数据中选中下一个
            int middle = frame.height() / 2 + frame.top;  // 设置激光线的位置在手机屏幕的中间
            // 绘制激光线。
            //     canvas.drawRect(frame.left + 2, middle - 1, frame.right - 1, middle + 2, paint);
            drawScanLight(canvas, frame);

            float scaleX = frame.width() / (float) previewFrame.width();
            float scaleY = frame.height() / (float) previewFrame.height();

            Log.d("previewFragme", previewFrame.width() + "----" + previewFrame.height());

            List<ResultPoint> currentPossible = possibleResultPoints;
            List<ResultPoint> currentLast = lastPossibleResultPoints;
            int frameLeft = frame.left;
            int frameTop = frame.top;
            if (currentPossible.isEmpty()) {
                lastPossibleResultPoints = null;
            } else {
                possibleResultPoints = new ArrayList<>(5);
                lastPossibleResultPoints = currentPossible;
                paint.setAlpha(CURRENT_POINT_OPACITY);     // 设置激光线周围的麻点的透明度
                paint.setColor(resultPointColor);          // 设置麻点的颜色
//                synchronized (currentPossible) {
//                    for (ResultPoint point : currentPossible) {
//                        canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),   // 绘制麻点
//                                frameTop + (int) (point.getY() * scaleY),
//                                POINT_SIZE, paint);
//                    }
//                }
            }

            if (isCircle) {    // 移动激光线的设置
                for (ResultPoint point : currentPossible) {
                    canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 6.0f, paint);
                }
            }

            if (currentLast != null) {
                paint.setAlpha(CURRENT_POINT_OPACITY / 2);
                paint.setColor(resultPointColor);

//                synchronized (currentLast) {
//                    float radius = POINT_SIZE / 2.0f;
//                    for (ResultPoint point : currentLast) {
//                        canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
//                                frameTop + (int) (point.getY() * scaleY),
//                                radius, paint);
//                    }
//                }

                if (isCircle) {   // 移动激光线的设置
                    for (ResultPoint point : currentLast) {
                        canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 3.0f, paint);
                    }
                }
            }

            // Request another update at the animation interval, but only repaint the laser line,
            // not the entire viewfinder mask. ，中间红线的位置
            postInvalidateDelayed(ANIMATION_DELAY,
                    frame.left - POINT_SIZE,
                    frame.top - POINT_SIZE,
                    frame.right + POINT_SIZE,
                    frame.bottom + POINT_SIZE);

        }
    }

    public void drawViewfinder() {
        Bitmap resultBitmap = this.resultBitmap;
        this.resultBitmap = null;
        if (resultBitmap != null) {
            resultBitmap.recycle();  // 释放bitmap资源
        }
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live scanning display.
     * 翻译 绘制一个带麻点的高亮的图。
     *
     * @param barcode An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode) {
        resultBitmap = barcode;
        invalidate();
    }

    /**
     * 功能：添加麻点的数量
     *
     * @param point
     */
    public void addPossibleResultPoint(ResultPoint point) {
        List<ResultPoint> points = possibleResultPoints;
        synchronized (points) {
            points.add(point);
            int size = points.size();
            if (size > MAX_RESULT_POINTS) {  // 如果麻点的个数超了，则
                // trim it
                points.subList(0, size - MAX_RESULT_POINTS / 2).clear(); // 切取前多少个清除
            }
        }
    }


    /**
     * 绘制扫描框上的四个边角
     *
     * @param canvas 面板
     * @param frame  扫描框矩形
     */
    private void drawFourCorner(Canvas canvas, Rect frame) {
        paint.setColor(getResources().getColor(R.color.result_points));  // 设置四个角的颜色

        // 左上角
        canvas.drawRect(frame.left, frame.top, frame.left + 30, frame.top + 10, paint);  // 左上角横线
        canvas.drawRect(frame.left, frame.top, frame.left + 10, frame.top + 30, paint);  // 左上角竖线

        // 右上角
        canvas.drawRect(frame.right - 30, frame.top, frame.right, frame.top + 10, paint);
        canvas.drawRect(frame.right - 10, frame.top, frame.right, frame.top + 30, paint);

        // 左下角
        canvas.drawRect(frame.left, frame.bottom - 10, frame.left + 30, frame.bottom, paint);
        canvas.drawRect(frame.left, frame.bottom - 30, frame.left + 10, frame.bottom, paint);

        // 右下角
        canvas.drawRect(frame.right - 30, frame.bottom - 10, frame.right, frame.bottom, paint);
        canvas.drawRect(frame.right - 10, frame.bottom - 30, frame.right, frame.bottom, paint);
    }

    /**
     * 绘制移动扫描线
     *
     * @param canvas
     * @param frame
     */
    private void drawScanLight(Canvas canvas, Rect frame) {

        if (scanLineTop == 0) {
            scanLineTop = frame.top;
        }

        if (scanLineTop >= frame.bottom - 30) {
            scanLineTop = frame.top;
        } else {
            scanLineTop += SCAN_VELOCITY;
        }
        Rect scanRect = new Rect(frame.left, scanLineTop, frame.right, scanLineTop + 30);
        canvas.drawBitmap(scanLight, null, scanRect, paint);
    }

    /**
     * 功能：设置移动的激光线的设置
     *
     * @param context
     * @param attrs
     */
    private void initInnerRect(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.innerrect);

        // 扫描bitmap
        Drawable drawable = ta.getDrawable(R.styleable.innerrect_inner_scan_bitmap);
        if (drawable != null) {
        }

        // 扫描控件, 从控件属性中获取
        scanLight = BitmapFactory.decodeResource(getResources(),
                ta.getResourceId(R.styleable.innerrect_inner_scan_bitmap, R.drawable.scanline));

        // 扫描速度
        SCAN_VELOCITY = ta.getInt(R.styleable.innerrect_inner_scan_speed, 5);
        isCircle = ta.getBoolean(R.styleable.innerrect_inner_scan_iscircle, true);

        ta.recycle();
    }


}