package com.mes.bc;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.BlurMaskFilter;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * XML-ready chat bubble container. Put any views (TextView, ImageView, etc.) inside it.
 * Grouping rule: same sender + gap <= groupWindowMs => hide arrow.
 */
public class BubbleChat extends FrameLayout {
    public static final int ARROW_NONE = 0;
    public static final int ARROW_LEFT = 1;
    public static final int ARROW_RIGHT = 2;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bubblePath = new Path();
    private final RectF rect = new RectF();

    private int bubbleColor = 0xFF277A65;
    private int arrowSide = ARROW_LEFT;
    private float arrowWidth = 10f, arrowHeight = 9f, arrowPosition = 0.22f;
    private float tl = 18f, tr = 18f, br = 18f, bl = 18f;
    private int strokeColor = 0x00000000;
    private float strokeWidth = 0f;
    private int shadowColor = 0x00000000;
    private float shadowRadius = 0f, shadowDx = 0f, shadowDy = 2f;
    private long groupWindowMs = 60000L;
    private boolean showArrow = true;

    public BubbleChat(Context context) { super(context); init(context, null); }
    public BubbleChat(Context context, AttributeSet attrs) { super(context, attrs); init(context, attrs); }
    public BubbleChat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStyle(Paint.Style.FILL);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BubbleChat);
            bubbleColor = a.getColor(R.styleable.BubbleChat_bc_bubbleColor, bubbleColor);
            arrowSide = a.getInt(R.styleable.BubbleChat_bc_arrowSide, arrowSide);
            arrowWidth = a.getDimension(R.styleable.BubbleChat_bc_arrowWidth, dp(10));
            arrowHeight = a.getDimension(R.styleable.BubbleChat_bc_arrowHeight, dp(9));
            arrowPosition = a.getFloat(R.styleable.BubbleChat_bc_arrowPosition, arrowPosition);
            tl = a.getDimension(R.styleable.BubbleChat_bc_cornerTopLeft, dp(18));
            tr = a.getDimension(R.styleable.BubbleChat_bc_cornerTopRight, dp(18));
            br = a.getDimension(R.styleable.BubbleChat_bc_cornerBottomRight, dp(18));
            bl = a.getDimension(R.styleable.BubbleChat_bc_cornerBottomLeft, dp(18));
            strokeColor = a.getColor(R.styleable.BubbleChat_bc_strokeColor, strokeColor);
            strokeWidth = a.getDimension(R.styleable.BubbleChat_bc_strokeWidth, 0);
            shadowColor = a.getColor(R.styleable.BubbleChat_bc_shadowColor, shadowColor);
            shadowRadius = a.getDimension(R.styleable.BubbleChat_bc_shadowRadius, 0);
            shadowDx = a.getDimension(R.styleable.BubbleChat_bc_shadowDx, 0);
            shadowDy = a.getDimension(R.styleable.BubbleChat_bc_shadowDy, dp(2));
            groupWindowMs = a.getInt(R.styleable.BubbleChat_bc_groupWindowMs, 60000);
            showArrow = a.getBoolean(R.styleable.BubbleChat_bc_showArrow, true);
            a.recycle();
        }
        setWillNotDraw(false);
        setPadding(getPaddingLeft() + (int) arrowHeight, getPaddingTop(),
                getPaddingRight() + (int) arrowHeight, getPaddingBottom());
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float reserve = showArrow && arrowSide != ARROW_NONE ? arrowHeight : 0f;
        float left = getPaddingLeft();
        float top = getPaddingTop();
        float right = getWidth() - getPaddingRight();
        float bottom = getHeight() - getPaddingBottom();

        // The arrow extends outward from the selected side; keep the body inside the view.
        if (showArrow && arrowSide == ARROW_LEFT) left += reserve;
        if (showArrow && arrowSide == ARROW_RIGHT) right -= reserve;
        if (right <= left || bottom <= top) return;

        rect.set(left, top, right, bottom);
        float[] radii = {tl,tl,tr,tr,br,br,bl,bl};
        bubblePath.reset();
        bubblePath.addRoundRect(rect, radii, Path.Direction.CW);

        if (showArrow && arrowSide != ARROW_NONE) {
            float cy = top + (bottom - top) * clamp(arrowPosition, 0.05f, 0.95f);
            Path arrow = new Path();
            if (arrowSide == ARROW_LEFT) {
                arrow.moveTo(left + 1, cy - arrowWidth / 2);
                arrow.lineTo(left - arrowHeight, cy);
                arrow.lineTo(left + 1, cy + arrowWidth / 2);
            } else {
                arrow.moveTo(right - 1, cy - arrowWidth / 2);
                arrow.lineTo(right + arrowHeight, cy);
                arrow.lineTo(right - 1, cy + arrowWidth / 2);
            }
            arrow.close();
            bubblePath.addPath(arrow);
        }

        if (shadowColor != 0 && shadowRadius > 0) {
            shadowPaint.setColor(bubbleColor);
            shadowPaint.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor);
            canvas.drawPath(bubblePath, shadowPaint);
            shadowPaint.clearShadowLayer();
        }
        fillPaint.setColor(bubbleColor);
        canvas.drawPath(bubblePath, fillPaint);

        if (strokeWidth > 0) {
            strokePaint.setColor(strokeColor);
            strokePaint.setStrokeWidth(strokeWidth);
            canvas.drawPath(bubblePath, strokePaint);
        }
    }

    private float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    /** Call while binding a chat row. previousTimestamp=0 means this is the first message. */
    public void setMessageGrouping(long currentTimestamp, long previousTimestamp, boolean sameSender) {
        boolean first = previousTimestamp <= 0;
        long gap = first ? Long.MAX_VALUE : Math.max(0L, currentTimestamp - previousTimestamp);
        showArrow = first || !sameSender || gap > groupWindowMs;
        invalidate();
    }

    /** Convenience API when the adapter already knows whether this row is grouped. */
    public void setGroupedWithPrevious(boolean grouped) {
        showArrow = !grouped;
        invalidate();
    }

    public void setGroupWindowMs(long millis) { groupWindowMs = Math.max(0L, millis); }
    public void setShowArrow(boolean show) { showArrow = show; invalidate(); }
    public void setBubbleColor(int color) { bubbleColor = color; invalidate(); }
    public void setArrow(int side, float widthPx, float heightPx, float position) {
        arrowSide = side; arrowWidth = widthPx; arrowHeight = heightPx;
        arrowPosition = clamp(position, 0.05f, 0.95f); invalidate();
    }
    public void setCornerRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        tl=topLeft; tr=topRight; br=bottomRight; bl=bottomLeft; invalidate();
    }
    public void setStroke(int color, float widthPx) { strokeColor=color; strokeWidth=widthPx; invalidate(); }
    public void setShadow(int color, float radiusPx, float dxPx, float dyPx) {
        shadowColor=color; shadowRadius=radiusPx; shadowDx=dxPx; shadowDy=dyPx; invalidate();
    }
              }
