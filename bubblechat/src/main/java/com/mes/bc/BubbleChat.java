package com.mes.bc;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

/**
 * XML-ready chat bubble container. Put any views (TextView, ImageView, etc.) inside it.
 *
 * Features:
 *  - 4 tail styles (triangle, rounded, curved "Telegram", corner) + tail alignment
 *  - Telegram-like grouping: the tail is shown only on the LAST message of a group
 *  - smaller corner radius between grouped messages
 *  - appear / disappear animations (toggleable), optional tail animation
 *  - gradient fill, max width, selection tint, flash highlight, shake
 *
 * NOTE: view padding = inner padding of the content. Space for the tail and shadow
 * is added automatically.
 */
public class BubbleChat extends FrameLayout {

    // ---- arrow side ----
    public static final int ARROW_NONE = 0;
    public static final int ARROW_LEFT = 1;
    public static final int ARROW_RIGHT = 2;

    // ---- tail styles ----
    public static final int TAIL_TRIANGLE = 0;
    public static final int TAIL_ROUNDED = 1;
    public static final int TAIL_CURVED = 2;   // Telegram-like hook
    public static final int TAIL_CORNER = 3;   // no protrusion, one corner becomes sharp

    // ---- tail vertical alignment ----
    public static final int ALIGN_TOP = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_BOTTOM = 2;
    public static final int ALIGN_CUSTOM = 3;  // uses arrowPosition (0..1)

    // ---- appear animations ----
    public static final int ANIM_NONE = 0;
    public static final int ANIM_FADE = 1;
    public static final int ANIM_SCALE = 2;
    public static final int ANIM_SLIDE_UP = 3;
    public static final int ANIM_SLIDE_SIDE = 4;
    public static final int ANIM_POP = 5;

    private static final int T_TAIL = 0, T_SELECT = 1, T_FLASH = 2;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bubblePath = new Path();
    private final Path tailPath = new Path();
    private final RectF rect = new RectF();

    // look
    private int bubbleColor = 0xFF277A65;
    private boolean hasGradient = false;
    private int gradientEndColor = 0;
    private float gradientAngle = 90f;
    private boolean gradientDirty = true;

    // tail
    private int arrowSide = ARROW_LEFT;
    private int tailStyle = TAIL_TRIANGLE;
    private int tailAlign = ALIGN_CUSTOM;
    private float arrowWidth, arrowHeight;
    private float arrowPosition = 0.22f;
    private float tailOffset = 0f;
    private float tailCornerRadius;
    private boolean showArrow = true;        // master switch for tails
    private boolean animateTail = false;
    private float tailProgress = 1f;

    // corners
    private float tl, tr, br, bl;
    private float groupedCorner = -1f;       // <0 = disabled

    // stroke / shadow
    private int strokeColor = 0x00000000;
    private float strokeWidth = 0f;
    private int shadowColor = 0x00000000;
    private float shadowRadius = 0f, shadowDx = 0f, shadowDy;

    // grouping
    private long groupWindowMs = 60000L;
    private boolean joinedWithPrevious = false;
    private boolean joinedWithNext = false;

    // size
    private int maxWidth = 0;

    // animations
    private boolean animationsEnabled = true;
    private int appearAnimation = ANIM_NONE;
    private long animationDuration = 220L;
    private boolean animateOnAttach = false;
    private boolean appearPlayed = false;

    // selection / flash
    private int selectedColor = 0x33FFFFFF;
    private boolean selectedFlag = false;
    private float selectedAlpha = 0f;
    private float flashAlpha = 0f;

    private ValueAnimator tailAnimator, selectAnimator, flashAnimator;

    // padding that we added ourselves (so it can be recalculated)
    private int aL, aT, aR, aB;

    public BubbleChat(Context context) { super(context); init(context, null); }
    public BubbleChat(Context context, AttributeSet attrs) { super(context, attrs); init(context, attrs); }
    public BubbleChat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setWillNotDraw(false);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStyle(Paint.Style.FILL);
        overlayPaint.setStyle(Paint.Style.FILL);

        arrowWidth = dp(10);
        arrowHeight = dp(9);
        tailCornerRadius = dp(4);
        tl = tr = br = bl = dp(18);
        shadowDy = dp(2);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BubbleChat);
            bubbleColor = a.getColor(R.styleable.BubbleChat_bc_bubbleColor, bubbleColor);
            if (a.hasValue(R.styleable.BubbleChat_bc_gradientEndColor)) {
                hasGradient = true;
                gradientEndColor = a.getColor(R.styleable.BubbleChat_bc_gradientEndColor, bubbleColor);
            }
            gradientAngle = a.getFloat(R.styleable.BubbleChat_bc_gradientAngle, gradientAngle);

            arrowSide = a.getInt(R.styleable.BubbleChat_bc_arrowSide, arrowSide);
            tailStyle = a.getInt(R.styleable.BubbleChat_bc_tailStyle, tailStyle);
            tailAlign = a.getInt(R.styleable.BubbleChat_bc_tailAlign, tailAlign);
            arrowWidth = a.getDimension(R.styleable.BubbleChat_bc_arrowWidth, arrowWidth);
            arrowHeight = a.getDimension(R.styleable.BubbleChat_bc_arrowHeight, arrowHeight);
            arrowPosition = a.getFloat(R.styleable.BubbleChat_bc_arrowPosition, arrowPosition);
            tailOffset = a.getDimension(R.styleable.BubbleChat_bc_tailOffset, tailOffset);
            tailCornerRadius = a.getDimension(R.styleable.BubbleChat_bc_tailCornerRadius, tailCornerRadius);
            showArrow = a.getBoolean(R.styleable.BubbleChat_bc_showArrow, true);
            animateTail = a.getBoolean(R.styleable.BubbleChat_bc_animateTail, false);

            tl = a.getDimension(R.styleable.BubbleChat_bc_cornerTopLeft, tl);
            tr = a.getDimension(R.styleable.BubbleChat_bc_cornerTopRight, tr);
            br = a.getDimension(R.styleable.BubbleChat_bc_cornerBottomRight, br);
            bl = a.getDimension(R.styleable.BubbleChat_bc_cornerBottomLeft, bl);
            groupedCorner = a.getDimension(R.styleable.BubbleChat_bc_groupedCorner, -1f);

            strokeColor = a.getColor(R.styleable.BubbleChat_bc_strokeColor, strokeColor);
            strokeWidth = a.getDimension(R.styleable.BubbleChat_bc_strokeWidth, 0);
            shadowColor = a.getColor(R.styleable.BubbleChat_bc_shadowColor, shadowColor);
            shadowRadius = a.getDimension(R.styleable.BubbleChat_bc_shadowRadius, 0);
            shadowDx = a.getDimension(R.styleable.BubbleChat_bc_shadowDx, 0);
            shadowDy = a.getDimension(R.styleable.BubbleChat_bc_shadowDy, shadowDy);

            groupWindowMs = a.getInt(R.styleable.BubbleChat_bc_groupWindowMs, 60000);
            maxWidth = a.getDimensionPixelSize(R.styleable.BubbleChat_bc_maxWidth, 0);

            animationsEnabled = a.getBoolean(R.styleable.BubbleChat_bc_animationsEnabled, true);
            appearAnimation = a.getInt(R.styleable.BubbleChat_bc_appearAnimation, ANIM_NONE);
            animationDuration = a.getInt(R.styleable.BubbleChat_bc_animationDuration, 220);
            animateOnAttach = a.getBoolean(R.styleable.BubbleChat_bc_animateOnAttach, false);

            selectedColor = a.getColor(R.styleable.BubbleChat_bc_selectedColor, selectedColor);
            a.recycle();
        }
        updateTail(false);
        applyInsets();
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    // =====================================================================
    // Insets: room for the tail and the shadow is added to the padding
    // =====================================================================

    private float reserve() {
        return (arrowSide != ARROW_NONE && tailStyle != TAIL_CORNER) ? arrowHeight : 0f;
    }

    private int shadowInset() {
        if (shadowColor == 0 || shadowRadius <= 0) return 0;
        return (int) Math.ceil(shadowRadius + Math.max(Math.abs(shadowDx), Math.abs(shadowDy)));
    }

    private void applyInsets() {
        int in = shadowInset();
        int r = (int) Math.ceil(reserve());
        int nl = in + (arrowSide == ARROW_LEFT ? r : 0);
        int nr = in + (arrowSide == ARROW_RIGHT ? r : 0);
        if (nl != aL || in != aT || nr != aR || in != aB) {
            setPadding(getPaddingLeft() - aL + nl, getPaddingTop() - aT + in,
                    getPaddingRight() - aR + nr, getPaddingBottom() - aB + in);
            aL = nl; aT = in; aR = nr; aB = in;
        }
        // software layer is only needed for the shadow layer
        int wanted = in > 0 ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_NONE;
        if (getLayerType() != wanted) setLayerType(wanted, null);
        gradientDirty = true;
        invalidate();
    }

    // =====================================================================
    // Measure / size
    // =====================================================================

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (maxWidth > 0) {
            int mode = MeasureSpec.getMode(widthSpec);
            int size = MeasureSpec.getSize(widthSpec);
            if (mode == MeasureSpec.UNSPECIFIED || size > maxWidth) {
                widthSpec = MeasureSpec.makeMeasureSpec(maxWidth,
                        mode == MeasureSpec.EXACTLY ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST);
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        gradientDirty = true;
        updatePivot();
    }

    private void updatePivot() {
        // scale animations grow out of the tail corner, like in messengers
        setPivotX(arrowSide == ARROW_RIGHT ? getWidth() : (arrowSide == ARROW_LEFT ? 0f : getWidth() / 2f));
        setPivotY(getHeight());
    }

    // =====================================================================
    // Drawing
    // =====================================================================

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float inset = shadowInset() + strokeWidth / 2f;
        float res = reserve();
        float left = inset + (arrowSide == ARROW_LEFT ? res : 0f);
        float top = inset;
        float right = getWidth() - inset - (arrowSide == ARROW_RIGHT ? res : 0f);
        float bottom = getHeight() - inset;
        if (right <= left || bottom <= top) return;

        rect.set(left, top, right, bottom);
        bubblePath.reset();
        bubblePath.addRoundRect(rect, effectiveRadii(), Path.Direction.CW);

        if (arrowSide != ARROW_NONE && tailStyle != TAIL_CORNER && tailProgress > 0.01f) {
            tailPath.reset();
            buildTail(tailPath, left, top, right, bottom, tailProgress);
            // UNION so overlapping parts never cancel each other out
            if (!bubblePath.op(tailPath, Path.Op.UNION)) bubblePath.addPath(tailPath);
        }

        if (shadowColor != 0 && shadowRadius > 0) {
            shadowPaint.setColor(bubbleColor);
            shadowPaint.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor);
            canvas.drawPath(bubblePath, shadowPaint);
            shadowPaint.clearShadowLayer();
        }

        if (hasGradient) {
            if (gradientDirty) {
                double rad = Math.toRadians(gradientAngle);
                float cx = rect.centerX(), cy = rect.centerY();
                float dx = (float) Math.cos(rad) * rect.width() / 2f;
                float dy = (float) Math.sin(rad) * rect.height() / 2f;
                fillPaint.setShader(new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy,
                        bubbleColor, gradientEndColor, Shader.TileMode.CLAMP));
                gradientDirty = false;
            }
        } else {
            fillPaint.setShader(null);
            fillPaint.setColor(bubbleColor);
        }
        canvas.drawPath(bubblePath, fillPaint);

        float oa = Math.max(selectedAlpha, flashAlpha);
        if (oa > 0f) {
            overlayPaint.setColor(selectedColor);
            overlayPaint.setAlpha((int) (Color.alpha(selectedColor) * oa));
            canvas.drawPath(bubblePath, overlayPaint);
        }

        if (strokeWidth > 0) {
            strokePaint.setColor(strokeColor);
            strokePaint.setStrokeWidth(strokeWidth);
            canvas.drawPath(bubblePath, strokePaint);
        }
    }

    /** Corner radii with grouping and "corner" tail adjustments applied. */
    private float[] effectiveRadii() {
        float a = tl, b = tr, c = br, d = bl;

        // smaller radius between grouped messages (on the tail side)
        if (arrowSide != ARROW_NONE && groupedCorner >= 0f) {
            if (arrowSide == ARROW_LEFT) {
                if (joinedWithPrevious) a = Math.min(a, groupedCorner);
                if (joinedWithNext) d = Math.min(d, groupedCorner);
            } else {
                if (joinedWithPrevious) b = Math.min(b, groupedCorner);
                if (joinedWithNext) c = Math.min(c, groupedCorner);
            }
        }

        // the corner that merges into the tail
        boolean mergesIntoTail = tailStyle == TAIL_CORNER
                || (tailStyle == TAIL_CURVED && tailAlign == ALIGN_BOTTOM && tailOffset <= 0f);
        if (mergesIntoTail && arrowSide != ARROW_NONE
                && (tailAlign == ALIGN_TOP || tailAlign == ALIGN_BOTTOM)) {
            float target = tailStyle == TAIL_CORNER ? tailCornerRadius : 0f;
            boolean bottomAlign = tailAlign == ALIGN_BOTTOM;
            if (arrowSide == ARROW_LEFT) {
                if (bottomAlign) d = lerp(d, target, tailProgress); else a = lerp(a, target, tailProgress);
            } else {
                if (bottomAlign) c = lerp(c, target, tailProgress); else b = lerp(b, target, tailProgress);
            }
        }
        return new float[]{a, a, b, b, c, c, d, d};
    }

    private void buildTail(Path t, float left, float top, float right, float bottom, float p) {
        final float edge = arrowSide == ARROW_LEFT ? left : right;
        final float o = arrowSide == ARROW_LEFT ? -1f : 1f; // outward direction
        final float in = -o;                                // inward (1px overlap with the body)
        final float h = arrowHeight * p;
        final float w = arrowWidth * p;

        float cy;
        switch (tailAlign) {
            case ALIGN_TOP:    cy = top + tailOffset + arrowWidth / 2f; break;
            case ALIGN_BOTTOM: cy = bottom - tailOffset - arrowWidth / 2f; break;
            case ALIGN_CENTER: cy = (top + bottom) / 2f; break;
            default:           cy = top + (bottom - top) * clamp(arrowPosition, 0.05f, 0.95f); break;
        }
        if (bottom - top > arrowWidth) cy = clamp(cy, top + arrowWidth / 2f, bottom - arrowWidth / 2f);

        switch (tailStyle) {
            case TAIL_ROUNDED:
                t.moveTo(edge + in, cy - w / 2f);
                t.cubicTo(edge + o * h * 0.5f, cy - w * 0.45f,
                          edge + o * h, cy - w * 0.14f,
                          edge + o * h, cy);
                t.cubicTo(edge + o * h, cy + w * 0.14f,
                          edge + o * h * 0.5f, cy + w * 0.45f,
                          edge + in, cy + w / 2f);
                break;

            case TAIL_CURVED: {
                float yb = cy + arrowWidth / 2f; // tip sits on this line
                t.moveTo(edge + in * 2f, yb - w);
                t.cubicTo(edge, yb - w * 0.45f,
                          edge + o * h * 0.35f, yb - w * 0.03f,
                          edge + o * h, yb);
                t.lineTo(edge + in * arrowWidth * 0.8f, yb);
                break;
            }

            default: // TAIL_TRIANGLE
                t.moveTo(edge + in, cy - w / 2f);
                t.lineTo(edge + o * h, cy);
                t.lineTo(edge + in, cy + w / 2f);
                break;
        }
        t.close();
    }

    // =====================================================================
    // Grouping (Telegram-like): the tail lives on the LAST message of a group
    // =====================================================================

    /**
     * Call while binding a chat row. Timestamps <= 0 mean "there is no such neighbour".
     * Messages are joined when the sender is the same and the gap <= groupWindowMs.
     * Joined with the next message => this message loses its tail.
     */
    public void setMessageGrouping(long previousTimestamp, boolean sameSenderAsPrevious,
                                   long currentTimestamp, long nextTimestamp,
                                   boolean sameSenderAsNext) {
        boolean prev = previousTimestamp > 0 && sameSenderAsPrevious
                && Math.abs(currentTimestamp - previousTimestamp) <= groupWindowMs;
        boolean next = nextTimestamp > 0 && sameSenderAsNext
                && Math.abs(nextTimestamp - currentTimestamp) <= groupWindowMs;
        setGrouping(prev, next);
    }

    /** Low-level variant when the adapter already knows the answer. */
    public void setGrouping(boolean joinedWithPrevious, boolean joinedWithNext) {
        this.joinedWithPrevious = joinedWithPrevious;
        this.joinedWithNext = joinedWithNext;
        updateTail(true);
    }

    private void updateTail(boolean allowAnimation) {
        float target = (showArrow && !joinedWithNext && arrowSide != ARROW_NONE) ? 1f : 0f;
        if (tailAnimator != null) tailAnimator.cancel();
        if (allowAnimation && animateTail && animationsEnabled && isLaidOut()) {
            tailAnimator = startAnim(T_TAIL, 160, tailProgress, target);
        } else {
            tailProgress = target;
        }
        invalidate();
    }

    // =====================================================================
    // Animations
    // =====================================================================

    /** Plays the appear animation (if enabled). Call it for NEW messages only. */
    public void playAppear() {
        appearPlayed = true;
        animate().cancel();
        resetTransforms();
        if (!animationsEnabled || appearAnimation == ANIM_NONE) return;
        updatePivot();

        final float side = arrowSide == ARROW_RIGHT ? 1f : -1f;
        ViewPropertyAnimator a = animate().setDuration(animationDuration)
                .setInterpolator(new DecelerateInterpolator());
        switch (appearAnimation) {
            case ANIM_FADE:
                setAlpha(0f);
                a.alpha(1f);
                break;
            case ANIM_SCALE:
                setAlpha(0
