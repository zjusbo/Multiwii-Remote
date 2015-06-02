package view.Arrow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.multiwii.multiwiiremote.MainActivity;

/**
 * Created by sbo on 5/10/2015.
 */
public class ArrowView extends View {

    private Canvas myCanvas;
    private Paint myPaint=new Paint();
    private MainActivity mainActivity;
    private int radius = 100; //unit: px
    private int offset = 5;
    public ArrowView(Context context) {
        super(context);
        init();
        // TODO Auto-generated constructor stub
    }

    public ArrowView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
        // TODO Auto-generated constructor stub
    }

    public ArrowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        // TODO Auto-generated constructor stub
    }

    void init(){
        setPaintDefaultStyle();
        setPaintDisableStyle();
    }

    void setPaintDisableStyle(){
        myPaint.setAntiAlias(true);
        myPaint.setColor(Color.GRAY);
        myPaint.setStyle(Paint.Style.STROKE);
        myPaint.setStrokeWidth(3);
    }
    @Override
    protected void onDraw(Canvas canvas) {
        // TODO Auto-generated method stub
        super.onDraw(canvas);
        if(mainActivity.getChkUsePhoneHeading().isChecked() == false || mainActivity.getConnectState() == false){
            setPaintDisableStyle();
        }
        else{
            setPaintDefaultStyle();
        }

        this.myCanvas=canvas;
        int phoneHeading = mainActivity.getPhoneHeading();//degree, -180 ~ 180
        int planeHeading = mainActivity.getPlaneHeading();//degree, -180 ~ 180
//        int phoneHeading = 0, planeHeading = 0;
        double direction[];
        direction = calDirectionVector(phoneHeading, planeHeading);
        //start x, start y, end x, end y
        drawAL(radius, radius, radius + (int)(direction[0] * radius), radius + (int)(direction[1] * radius));
        drawCircle(radius, radius, radius);
    }

    private double [] calDirectionVector(int phoneHeading, int planeHeading){
        double directionVector[] = new double[2];
        int deltaDegree = planeHeading - phoneHeading;
        if(deltaDegree < -180){
            deltaDegree += 360;
        }
        else if(deltaDegree > 180){
            deltaDegree -= 360;
        }
        double deltaRadians = Math.toRadians(deltaDegree);
        //end point x
        directionVector[0] = Math.sin(deltaRadians);
        //end point y
        directionVector[1] = -1 * Math.cos(deltaRadians);
        return directionVector;
    }
    /**
     * 设置画笔默认样式
     */
    public void setPaintDefaultStyle(){
        myPaint.setAntiAlias(true);
        myPaint.setColor(Color.YELLOW);
        myPaint.setStyle(Paint.Style.STROKE);
        myPaint.setStrokeWidth(4);
    }

    /**
     * 画圆
     * @param x x坐标
     * @param y y坐标
     * @param radius    圆的半径
     */
    public void drawCircle(float x,float y,float radius){
        myCanvas.drawCircle(x, y, radius, myPaint);
        invalidate();
    }

    /**
     * 画箭头
     * @param sx
     * @param sy
     * @param ex
     * @param ey
     */
    public void drawAL(int sx, int sy, int ex, int ey)
    {
        double H = 8; // 箭头高度
        double L = 3.5; // 底边的一半
        int x3 = 0;
        int y3 = 0;
        int x4 = 0;
        int y4 = 0;
        double awrad = Math.atan(L / H); // 箭头角度
        double arraow_len = Math.sqrt(L * L + H * H); // 箭头的长度
        double[] arrXY_1 = rotateVec(ex - sx, ey - sy, awrad, true, arraow_len);
        double[] arrXY_2 = rotateVec(ex - sx, ey - sy, -awrad, true, arraow_len);
        double x_3 = ex - arrXY_1[0]; // (x3,y3)是第一端点
        double y_3 = ey - arrXY_1[1];
        double x_4 = ex - arrXY_2[0]; // (x4,y4)是第二端点
        double y_4 = ey - arrXY_2[1];
        Double X3 = new Double(x_3);
        x3 = X3.intValue();
        Double Y3 = new Double(y_3);
        y3 = Y3.intValue();
        Double X4 = new Double(x_4);
        x4 = X4.intValue();
        Double Y4 = new Double(y_4);
        y4 = Y4.intValue();
        // 画线
        myCanvas.drawLine(sx, sy, ex, ey,myPaint);
        Path triangle = new Path();
        triangle.moveTo(ex, ey);
        triangle.lineTo(x3, y3);
        triangle.lineTo(x4, y4);
        triangle.close();
        myCanvas.drawPath(triangle,myPaint);

    }
    // 计算
    public double[] rotateVec(int px, int py, double ang, boolean isChLen, double newLen)
    {
        double mathstr[] = new double[2];
        // 矢量旋转函数，参数含义分别是x分量、y分量、旋转角、是否改变长度、新长度
        double vx = px * Math.cos(ang) - py * Math.sin(ang);
        double vy = px * Math.sin(ang) + py * Math.cos(ang);
        if (isChLen) {
            double d = Math.sqrt(vx * vx + vy * vy);
            vx = vx / d * newLen;
            vy = vy / d * newLen;
            mathstr[0] = vx;
            mathstr[1] = vy;
        }
        return mathstr;
    }


    public void setMainActivity(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }
}
