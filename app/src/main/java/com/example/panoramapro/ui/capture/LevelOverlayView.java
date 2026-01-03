package com.example.panoramapro.ui.capture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 全景拍摄辅助水平仪视图
 * 可视化显示当前的俯仰角(Pitch)和横滚角(Roll)偏差
 */
public class LevelOverlayView extends View {

    // 绘制画笔
    private Paint targetPaint;      // 靶心画笔
    private Paint ballPaint;        // 移动小球画笔
    private Paint linePaint;        // 辅助线画笔
    private Paint textPaint;        // 文字提示画笔

    // 屏幕中心点
    private float centerX;
    private float centerY;

    // 当前角度偏移量
    private float pitchOffset = 0; // 上下倾斜偏移
    private float rollOffset = 0;  // 左右倾斜偏移

    // 阈值（与Fragment中保持一致）
    private float maxPitchThreshold = 20.0f;
    private float maxRollThreshold = 30.0f;

    // 灵敏度（像素/度）：每一度偏移在屏幕上移动多少像素
    private static final float SENSITIVITY = 15.0f;

    // 半径尺寸
    private static final float TARGET_RADIUS = 80.0f; // 靶心半径
    private static final float BALL_RADIUS = 30.0f;   // 小球半径

    // 状态颜色
    private int colorNormal = Color.GREEN;
    private int colorWarning = Color.YELLOW;
    private int colorError = Color.RED;

    public LevelOverlayView(Context context) {
        super(context);
        init();
    }

    public LevelOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 初始化靶心画笔（空心圆环）
        targetPaint = new Paint();
        targetPaint.setStyle(Paint.Style.STROKE);
        targetPaint.setStrokeWidth(8f);
        targetPaint.setAntiAlias(true);
        targetPaint.setColor(Color.WHITE);

        // 初始化小球画笔（实心圆）
        ballPaint = new Paint();
        ballPaint.setStyle(Paint.Style.FILL);
        ballPaint.setAntiAlias(true);
        ballPaint.setColor(colorNormal);
        ballPaint.setAlpha(200); // 稍微透明

        // 初始化辅助线画笔
        linePaint = new Paint();
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(4f);
        linePaint.setAlpha(100);

        // 文字画笔
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(4, 0, 0, Color.BLACK); // 文字阴影
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
    }

    /**
     * 更新方向数据
     * @param currentOrientation 当前方向 [方位角, 俯仰角, 横滚角]
     * @param baseOrientation 基准方向（如果没有则传null）
     */
    public void updateOrientation(float[] currentOrientation, float[] baseOrientation) {
        if (baseOrientation == null) {
            // 如果没有基准（还没拍第一张），我们希望手机保持水平
            // 这里假设水平时 pitch=0, roll=0 (这取决于手机握持方式，通常竖屏拍照 pitch~=0, roll~=0)
            // 如果是全景模式，通常希望 Pitch 接近 90度(竖立) 或 0度(平放)，这里根据你的业务逻辑调整
            // 假设是竖持手机拍摄全景，我们比较当前值和绝对水平

            // 注意：这里简化处理，显示相对于"平稳"的偏移。
            // 实际上全景拍摄第一张时，用户怎么拿都行，重点是后续照片要和第一张一致

            this.pitchOffset = 0; // 第一张照片前，可以设为0或显示绝对水平偏差
            this.rollOffset = 0;

            // 实际上，为了让用户第一张拍好，我们可以显示绝对水平偏差
            // 但为了简化，未拍摄时我们将小球置中，提示"请保持平稳拍摄第一张"

        } else {
            // 计算相对于基准方向的偏移
            this.pitchOffset = currentOrientation[1] - baseOrientation[1];
            this.rollOffset = currentOrientation[2] - baseOrientation[2];
        }

        // 触发重绘
        invalidate();
    }

    /**
     * 设置阈值
     */
    public void setThresholds(float pitch, float roll) {
        this.maxPitchThreshold = pitch;
        this.maxRollThreshold = roll;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. 绘制水平和垂直中心辅助线（淡淡的十字线）
        canvas.drawLine(centerX - 200, centerY, centerX + 200, centerY, linePaint);
        canvas.drawLine(centerX, centerY - 200, centerX, centerY + 200, linePaint);

        // 2. 计算小球位置
        // Pitch (俯仰) 影响 Y 轴：手机头向上翘(Pitch变大)，小球应该向上跑 (或者向下，取决于你想模拟气泡还是准星)
        // 模拟"准星"模式：手机往上指，准星往上跑。
        float ballY = centerY - (pitchOffset * SENSITIVITY);
        // Roll (横滚) 影响 X 轴
        float ballX = centerX + (rollOffset * SENSITIVITY); // 加减号控制方向

        // 限制小球不要跑出屏幕太远
        ballY = Math.max(centerY - 400, Math.min(centerY + 400, ballY));
        ballX = Math.max(centerX - 400, Math.min(centerX + 400, ballX));

        // 3. 判断偏移程度并设置颜色
        boolean pitchBad = Math.abs(pitchOffset) > maxPitchThreshold;
        boolean rollBad = Math.abs(rollOffset) > maxRollThreshold;
        boolean isWarning = Math.abs(pitchOffset) > (maxPitchThreshold / 2) || Math.abs(rollOffset) > (maxRollThreshold / 2);

        if (pitchBad || rollBad) {
            targetPaint.setColor(colorError);
            ballPaint.setColor(colorError);
            drawWarningText(canvas, "请调整角度！");
        } else if (isWarning) {
            targetPaint.setColor(colorWarning);
            ballPaint.setColor(colorWarning);
        } else {
            targetPaint.setColor(colorNormal);
            ballPaint.setColor(colorNormal);
        }

        // 4. 绘制中心靶心 (固定)
        canvas.drawCircle(centerX, centerY, TARGET_RADIUS, targetPaint);

        // 5. 绘制移动小球
        canvas.drawCircle(ballX, ballY, BALL_RADIUS, ballPaint);

        // 6. 绘制连接线 (增强视觉指引)
        canvas.drawLine(centerX, centerY, ballX, ballY, linePaint);
    }

    private void drawWarningText(Canvas canvas, String text) {
        canvas.drawText(text, centerX, centerY - TARGET_RADIUS - 40, textPaint);
    }
}
