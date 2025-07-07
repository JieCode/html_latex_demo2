package cn.edu.bnu.html_latex_demo2;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.PictureDrawable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.daquexian.flexiblerichtextview.FlexibleRichTextView;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkRichTextView extends ScrollView {
    private static final int EDIT_PADDING = 10; // edittext常规padding是10dp
    //private static final int EDIT_FIRST_PADDING_TOP = 10; // 第一个EditText的paddingTop值

    private int viewTagIndex = 1; // 新生的view都会打一个tag，对每个view来说，这个tag是唯一的。
    private FlexboxLayout allLayout; // 这个是所有子view的容器，scrollView内部的唯一一个ViewGroup
    private LayoutInflater inflater;
    private TextView lastFocusText; // 最近被聚焦的TextView
    private LayoutTransition mTransitioner; // 只在图片View添加或remove时，触发transition动画
    private int editNormalPadding = 0; //
    private int disappearingImageIndex = 0;
    //private Bitmap bmp;
    private OnClickListener btnListener;//图片点击事件
    private ArrayList<String> imagePaths;//图片地址集合
    private String keywords;//关键词高亮

    private OnRtImageClickListener onRtImageClickListener;

    /**
     * 自定义属性
     **/
    //插入的图片显示高度
    private int rtImageHeight = 0; //为0显示原始高度
    //两张相邻图片间距
    private int rtImageBottom = 10;
    //文字相关属性，初始提示信息，文字大小和颜色
    private String rtTextInitHint = "没有内容";
    //getResources().getDimensionPixelSize(R.dimen.text_size_16)
    private int rtTextSize = 16; //相当于16sp
    private int rtTextColor = Color.parseColor("#757575");
    private int rtTextLineSpace = 8; //相当于8dp
    private Context mContext;
    private String TAG = "MarkRichTextView";

    public MarkRichTextView(Context context) {
        this(context, null);
    }

    public MarkRichTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MarkRichTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        //获取自定义属性
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.RichTextView);
        rtImageHeight = ta.getInteger(R.styleable.RichTextView_rt_view_image_height, 0);
        rtImageBottom = ta.getInteger(R.styleable.RichTextView_rt_view_image_bottom, 10);
        rtTextSize = ta.getDimensionPixelSize(R.styleable.RichTextView_rt_view_text_size, 16);
        //rtTextSize = ta.getInteger(R.styleable.RichTextView_rt_view_text_size, 16);
        rtTextLineSpace = ta.getDimensionPixelSize(R.styleable.RichTextView_rt_view_text_line_space, 8);
        rtTextColor = ta.getColor(R.styleable.RichTextView_rt_view_text_color, Color.parseColor("#757575"));
        rtTextInitHint = ta.getString(R.styleable.RichTextView_rt_view_text_init_hint);

        ta.recycle();

        imagePaths = new ArrayList<>();

        inflater = LayoutInflater.from(context);

        // 1. 初始化allLayout
        allLayout = new FlexboxLayout(context);
        allLayout.setFlexDirection(FlexDirection.ROW);
        //allLayout.setBackgroundColor(Color.WHITE);//去掉背景
        //setupLayoutTransitions();//禁止载入动画
        LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        allLayout.setPadding(50, 15, 50, 15);//设置间距，防止生成图片时文字太靠边
        addView(allLayout, layoutParams);

        btnListener = new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (v instanceof ImageView) {
                    ImageView imageView = (ImageView) v;
                    //int currentItem = imagePaths.indexOf(imageView.getAbsolutePath());
                    //Toast.makeText(getContext(),"点击图片："+currentItem+"："+imageView.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                    // 开放图片点击接口
                    if (onRtImageClickListener != null) {
                        onRtImageClickListener.onRtImageClick(imageView, (String) imageView.getTag());
                    }
                }
            }
        };

        LinearLayout.LayoutParams firstEditParam = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        //editNormalPadding = dip2px(EDIT_PADDING);
        TextView firstText = createTextView(rtTextInitHint, dip2px(context, EDIT_PADDING));
        allLayout.addView(firstText, firstEditParam);
        lastFocusText = firstText;
    }

    private int dip2px(Context context, float dipValue) {
        float m = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * m + 0.5f);
    }

    public interface OnRtImageClickListener {
        void onRtImageClick(View view, String imagePath);
    }

    public void setOnRtImageClickListener(OnRtImageClickListener onRtImageClickListener) {
        this.onRtImageClickListener = onRtImageClickListener;
    }

    /**
     * 清除所有的view
     */
    public void clearAllLayout() {
        allLayout.removeAllViews();
    }

    /**
     * 获得最后一个子view的位置
     */
    public int getLastIndex() {
        int lastEditIndex = allLayout.getChildCount();
        return lastEditIndex;
    }

    /**
     * 生成文本输入框
     */
    public TextView createTextView(String hint, int paddingTop) {
        TextView textView = new TextView(mContext);
        textView.setTag(viewTagIndex++);
        textView.setPadding(editNormalPadding, paddingTop, editNormalPadding, paddingTop);
        textView.setHint(hint);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, rtTextSize);
        textView.setLineSpacing(rtTextLineSpace, 1.0f);
        textView.setTextColor(rtTextColor);
        return textView;
    }

    /**
     * 生成文本输入框
     */
    public TextView createHtmlTextView(String hint, int paddingTop) {
        TextView textView = new TextView(mContext);
        textView.setTag(viewTagIndex++);
        textView.setPadding(editNormalPadding, 0, editNormalPadding, 0);
        textView.setHint(hint);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, rtTextSize);
        textView.setTextColor(rtTextColor);
        // 设置TextView支持HTML标签
        textView.setMovementMethod(LinkMovementMethod.getInstance());


        return textView;
    }


    /**
     * 生成文本输入框
     */
    public FlexibleRichTextView createLatexTextView(String hint, int paddingTop) {
        FlexibleRichTextView textView = new FlexibleRichTextView(mContext);
        textView.setTag(viewTagIndex++);
        textView.setPadding(editNormalPadding, paddingTop, editNormalPadding, paddingTop);
        textView.setHint(hint);
        textView.setTextSize(getResources().getDimensionPixelSize(R.dimen.text_size_16));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, rtTextSize);
        textView.setLineSpacing(rtTextLineSpace, 1.0f);
        textView.setTextColor(rtTextColor);
        return textView;
    }

    /**
     * 生成图片View
     */
    private RelativeLayout createImageLayout() {
        RelativeLayout layout = (RelativeLayout) inflater.inflate(R.layout.edit_imageview, null);
        layout.setTag(viewTagIndex++);
        View closeView = layout.findViewById(R.id.image_close);
        closeView.setVisibility(GONE);
        ImageView imageView = layout.findViewById(R.id.edit_imageView);
        //imageView.setTag(layout.getTag());
        imageView.setOnClickListener(btnListener);
        return layout;
    }

    /**
     * 关键字高亮显示
     *
     * @param target 需要高亮的关键字
     * @param text   需要显示的文字
     * @return spannable 处理完后的结果，记得不要toString()，否则没有效果
     * SpannableStringBuilder textString = TextUtilTools.highlight(item.getItemName(), KnowledgeActivity.searchKey);
     * vHolder.tv_itemName_search.setText(textString);
     */
    public static SpannableStringBuilder highlight(String text, String target) {
        SpannableStringBuilder spannable = new SpannableStringBuilder(text);
        CharacterStyle span;
        try {
            Pattern p = Pattern.compile(target);
            Matcher m = p.matcher(text);
            while (m.find()) {
                span = new ForegroundColorSpan(Color.parseColor("#EE5C42"));// 需要重复！
                spannable.setSpan(span, m.start(), m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return spannable;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    /**
     * 在特定位置插入EditText
     *
     * @param index   位置
     * @param editStr EditText显示的文字
     */
    public void addTextViewAtIndex(final int index, CharSequence editStr, int position, CompleteResultListener listener) {
        try {
            if (!TextUtils.isEmpty(keywords)) {//搜索关键词高亮
                TextView textView = createTextView("", EDIT_PADDING);
                SpannableStringBuilder textStr = highlight(editStr.toString(), keywords);
                textView.setText(textStr);
                allLayout.addView(textView, index);
            } else {
                String htmlContent = editStr.toString();
//                LogUtil.e(TAG, "addTextViewAtIndex htmlContent: " + htmlContent);
                // 检查是否包含数学公式相关标签
                // 新增判断是否有sub、sup、或其他数学公式
                boolean hasMathTags = htmlContent.contains("<sup>") || htmlContent.contains("<sub>") ||
                        htmlContent.contains("\\(") || htmlContent.contains("<latex>");

                if (hasMathTags && !htmlContent.contains("\\(") && !htmlContent.contains("<latex>")) {
                    // 处理包含<sup>、<sub>等标签但不包含LaTeX公式的内容
                    // 处理包含<sup>、<sub>等标签但不包含LaTeX公式的内容
                    TextView htmlTextView = createHtmlTextView("", EDIT_PADDING);

                    // 使用正则表达式查找所有<sup>标签内容
                    Pattern supPattern = Pattern.compile("<sup>(.*?)</sup>");
                    Matcher supMatcher = supPattern.matcher(htmlContent);

                    // 创建一个可以包含样式的字符串
                    SpannableStringBuilder spannableString = new SpannableStringBuilder();

                    int lastEnd = 0;
                    while (supMatcher.find()) {
                        // 添加<sup>标签前的文本
                        spannableString.append(Html.fromHtml(htmlContent.substring(lastEnd, supMatcher.start())));

                        // 获取<sup>标签中的内容
                        String supContent = supMatcher.group(1);

                        // 添加上标内容
                        int start = spannableString.length();
                        spannableString.append(supContent);
                        int end = spannableString.length();

                        // 设置上标样式
                        spannableString.setSpan(new SuperscriptSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        spannableString.setSpan(new RelativeSizeSpan(0.75f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                        lastEnd = supMatcher.end();
                    }

                    // 添加剩余的文本
                    if (lastEnd < htmlContent.length()) {
                        spannableString.append(Html.fromHtml(htmlContent.substring(lastEnd)));
                    }

                    htmlTextView.setText(spannableString);

                    if (listener != null) {
                        htmlTextView.setOnClickListener(view -> listener.complete(position));
                    }

                    allLayout.addView(htmlTextView, index);
                } else if (htmlContent.contains("\\(") || htmlContent.contains("<latex>")) {
                    //代表有公式
                    //去掉htmlContent里除了img标签外的所有标签
                    String regEx = "<(?!img)[^>]+>";
                    Pattern p = Pattern.compile(regEx);
                    Matcher m = p.matcher(htmlContent);
                    htmlContent = m.replaceAll("");
                    //去掉htmlContent里的所有标签
                    htmlContent = htmlContent.replaceAll("\r|\n", "");
                    htmlContent = htmlContent.replaceAll("&there4;", "∴");
                    htmlContent = htmlContent.replaceAll("&ang;", "∠");
                    htmlContent = htmlContent.replaceAll("&bull;", "·");
                    htmlContent = htmlContent.replaceAll("&nbsp;", " ");
                    htmlContent = htmlContent.replaceAll("&perp;", "⊥");
                    htmlContent = htmlContent.replaceAll("&deg;", "°");
                    htmlContent = htmlContent.replaceAll("&gt;", ">");
                    htmlContent = htmlContent.replaceAll("&lt;", "<");
                    htmlContent = htmlContent.replaceAll("&nbsp;", " ");
                    htmlContent = htmlContent.replaceAll("&ge;", "\\geq");
                    htmlContent = htmlContent.replaceAll("&le;", "\\leq");
                    htmlContent = htmlContent.replaceAll("&amp;", "&");
                    htmlContent = htmlContent.replaceAll("&ne;", "\\neq");
                    htmlContent = htmlContent.replaceAll("&apos;", "'");
                    htmlContent = htmlContent.replaceAll("&quot;", "\"");
                    htmlContent = htmlContent.replaceAll("&times;", "×");
                    FlexibleRichTextView flexibleRichTextView = createLatexTextView("", EDIT_PADDING);
                    flexibleRichTextView.setText(htmlContent);
                    if (listener != null) {
                        flexibleRichTextView.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                listener.complete(position);
                            }
                        });
                    }

                    allLayout.addView(flexibleRichTextView, index);
                } else {
                    //没公式时用html展示
                    TextView htmlTextView = createHtmlTextView("", EDIT_PADDING);
                    htmlTextView.setText(Html.fromHtml(htmlContent));
                    if (listener != null) {
                        htmlTextView.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                listener.complete(position);
                            }
                        });
                    }
                    allLayout.addView(htmlTextView, index);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 在特定位置添加ImageView
     */
    public void addImageViewAtIndex(final int index, final String imagePath) {
        if (TextUtils.isEmpty(imagePath)) {
            return;
        }
        imagePaths.add(imagePath);
        RelativeLayout imageLayout = createImageLayout();
        if (imageLayout == null) {
            return;
        }
        if (!imagePath.contains(".svg")) {
            final ImageView imageView = imageLayout.findViewById(R.id.edit_imageView);
            imageView.setTag(imagePath);
            imageView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    String path = (String) view.getTag();
                    List<String> paths = new ArrayList<>();
                    paths.add(path);
                    BnuCheckImgActivity.startActivity(mContext, paths);
                }
            });
            Glide
                    .with(mContext)
                    .load(imagePath)
                    .placeholder(R.mipmap.gray_bg)
                    .fitCenter()
                    .into(imageView);

//            Glide
//                    .with(mContext)
//                    .load(imagePath)
//                    .placeholder(com.sendtion.xrichtext.R.drawable.)
//                    .fitCenter()
//                    .into(imageView);

        } else {
            final ImageView imageView = imageLayout.findViewById(R.id.edit_imageView);
            imageView.setVisibility(GONE);
            final ImageView imageViewSvg = imageLayout.findViewById(R.id.edit_imageView_svg);
            imageViewSvg.setVisibility(VISIBLE);
            imageViewSvg.setTag(imagePath);
            imageViewSvg.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    String path = (String) view.getTag();
                    List<String> paths = new ArrayList<>();
                    paths.add(path);
                    BnuCheckImgActivity.startActivity(mContext, paths);
                }
            });
            Glide.with(this)
                    .as(PictureDrawable.class)
                    .listener(new SvgSoftwareLayerSetter())
                    .placeholder(R.mipmap.default_photo)
                    .fitCenter()
                    .load(imagePath)
                    .into(imageViewSvg);
        }


        // onActivityResult无法触发动画，此处post处理
        allLayout.addView(imageLayout, index);
    }

    /**
     * 根据view的宽度，动态缩放bitmap尺寸
     *
     * @param width view的宽度
     */
    public Bitmap getScaledBitmap(String filePath, int width) {
        if (TextUtils.isEmpty(filePath)) {
            return null;
        }
        BitmapFactory.Options options = null;
        try {
            options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);
            int sampleSize = options.outWidth > width ? options.outWidth / width
                    + 1 : 1;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return BitmapFactory.decodeFile(filePath, options);
    }

    /**
     * 初始化transition动画
     */
    private void setupLayoutTransitions() {
        mTransitioner = new LayoutTransition();
        allLayout.setLayoutTransition(mTransitioner);
        mTransitioner.addTransitionListener(new LayoutTransition.TransitionListener() {

            @Override
            public void startTransition(LayoutTransition transition,
                                        ViewGroup container, View view, int transitionType) {

            }

            @Override
            public void endTransition(LayoutTransition transition,
                                      ViewGroup container, View view, int transitionType) {
                if (!transition.isRunning()
                        && transitionType == LayoutTransition.CHANGE_DISAPPEARING) {
                    // transition动画结束，合并EditText
                    // mergeEditText();
                }
            }
        });
        mTransitioner.setDuration(300);
    }

    public int getRtImageHeight() {
        return rtImageHeight;
    }

    public void setRtImageHeight(int rtImageHeight) {
        this.rtImageHeight = rtImageHeight;
    }

    public int getRtImageBottom() {
        return rtImageBottom;
    }

    public void setRtImageBottom(int rtImageBottom) {
        this.rtImageBottom = rtImageBottom;
    }

    public String getRtTextInitHint() {
        return rtTextInitHint;
    }

    public void setRtTextInitHint(String rtTextInitHint) {
        this.rtTextInitHint = rtTextInitHint;
    }

    public int getRtTextSize() {
        return rtTextSize;
    }

    public void setRtTextSize(int rtTextSize) {
        this.rtTextSize = rtTextSize;
    }

    public int getRtTextColor() {
        return rtTextColor;
    }

    public void setRtTextColor(int rtTextColor) {
        this.rtTextColor = rtTextColor;
    }

    public int getRtTextLineSpace() {
        return rtTextLineSpace;
    }

    public void setRtTextLineSpace(int rtTextLineSpace) {
        this.rtTextLineSpace = rtTextLineSpace;
    }
}
