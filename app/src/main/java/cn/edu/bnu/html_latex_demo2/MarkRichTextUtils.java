package cn.edu.bnu.html_latex_demo2;

import android.graphics.Color;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MarkRichTextUtils {


    private static String TAG = "MarkRichTextUtils";

    public static void showRichText(MarkRichTextView tv_note_content, String content) {
        tv_note_content.post(new Runnable() {
            @Override
            public void run() {
                dealWithContent(tv_note_content, content, null);
            }
        });
    }

    /**
     * 带文字点击返回监听
     *
     * @param tv_note_content
     * @param content
     */
    public static void showRichTextListener(MarkRichTextView tv_note_content, String content, CompleteResultListener listener) {
        tv_note_content.post(new Runnable() {
            @Override
            public void run() {
                dealWithContent(tv_note_content, content, listener);
            }
        });
    }


    private static void dealWithContent(MarkRichTextView tv_note_content, String myContent, CompleteResultListener listener) {
        //showEditData(myContent);
        tv_note_content.clearAllLayout();
        showDataSync(tv_note_content, myContent, listener);

        // 图片点击事件
        tv_note_content.setOnRtImageClickListener(new MarkRichTextView.OnRtImageClickListener() {
            @Override
            public void onRtImageClick(View view, String imagePath) {
                try {
                    ArrayList<String> imageList = getTextFromHtml(myContent, true);
                    int currentPosition = imageList.indexOf(imagePath);
                    List<Uri> dataList = new ArrayList<>();
                    for (int i = 0; i < imageList.size(); i++) {
                        dataList.add(RithImgUtil.getUriFromPath(imageList.get(i)));
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 异步方式显示数据
     */
    private static void showDataSync(MarkRichTextView tv_note_content, final String html, CompleteResultListener listener) {

        Observable.create(new ObservableOnSubscribe<String>() {
                    @Override
                    public void subscribe(ObservableEmitter<String> emitter) {
                        showEditData(emitter, html);
                    }
                })
                //.onBackpressureBuffer()
                .subscribeOn(Schedulers.io())//生产事件在io
                .observeOn(AndroidSchedulers.mainThread())//消费事件在UI线程
                .subscribe(new Observer<String>() {
                    @Override
                    public void onComplete() {
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(String text) {
                        try {
                            if (tv_note_content != null) {
                                if (text.contains("<img") && text.contains("src=")) {
                                    //imagePath可能是本地路径，也可能是网络地址
                                    String imagePath = getImgSrc(text);
                                    //剔除反斜杠
                                    imagePath = imagePath.replace("\\", "");
                                    if (imagePath.contains("http")) {
                                        tv_note_content.addImageViewAtIndex(tv_note_content.getLastIndex(), imagePath);
                                    }else{
                                        // 补全路径
                                        imagePath = "https://" + imagePath;
                                        if (imagePath.contains("////")){
                                            imagePath = imagePath.replace("////", "//");
                                        }
                                        tv_note_content.addImageViewAtIndex(tv_note_content.getLastIndex(), imagePath);
                                    }
                                } else {
                                    int position = 0;
                                    if (tv_note_content.getTag() != null) {
                                        position = (int) tv_note_content.getTag();
                                    }
                                    tv_note_content.addTextViewAtIndex(tv_note_content.getLastIndex(), text, position, listener);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
    }


    /**
     * 显示数据
     */
    private static void showEditData(ObservableEmitter<String> emitter, String html) {
        if (TextUtils.isEmpty(html)){
            emitter.onComplete();
        }
        try {
            List<String> textList = cutStringByImgTag(html);
            for (int i = 0; i < textList.size(); i++) {
                String text = textList.get(i);
                emitter.onNext(text);
            }
            emitter.onComplete();
        } catch (Exception e) {
            e.printStackTrace();
            emitter.onError(e);
            LogUtil.e(TAG + " showEditData error: ", e.getMessage());
        }
    }

    /**
     * @param targetStr 要处理的字符串
     * @description 切割字符串，将文本和img标签碎片化，如"ab<img>cd"转换为"ab"、"<img>"、"cd"
     */
    public static List<String> cutStringByImgTag(String targetStr) {
        List<String> splitTextList = new ArrayList<String>();
        List<String> resultList = new ArrayList<String>();
        if (TextUtils.isEmpty(targetStr)){
            return resultList;
        }
        targetStr = targetStr.replaceAll("src=\\\\", "src=");
        LogUtil.e(TAG + " targetStr完整内容: ", targetStr);
        try {
            // 预处理：将<sup>和</sup>标签替换为特殊标记，以防止被分割
            targetStr = targetStr.replaceAll("<sup>", "##SUP_START##");
            targetStr = targetStr.replaceAll("</sup>", "##SUP_END##");
            targetStr = targetStr.replaceAll("<sub>", "##SUB_START##");
            targetStr = targetStr.replaceAll("</sub>", "##SUB_END##");

            String regex = "<img[^>]*?src=(?:['\"]|`)(.*?)(?:['\"]|`)[^>]*?>";
            // 尝试使用Pattern.DOTALL标志
            Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(targetStr);
            int lastIndex = 0;
            try {
                while (matcher.find()) {
//                    LogUtil.e(TAG + " cutStringByImgTag matcher.find:", matcher.find());
                    if (matcher.start() > lastIndex) {
                        splitTextList.add(targetStr.substring(lastIndex, matcher.start()));
//                        LogUtil.e(TAG + " cutStringByImgTag matcher.start() > lastIndex splitTextList.add:", targetStr.substring(lastIndex, matcher.start()));
                    }
                    splitTextList.add(targetStr.substring(matcher.start(), matcher.end()));
//                    LogUtil.e(TAG + " cutStringByImgTag matcher.find splitTextList.add:", targetStr.substring(matcher.start(), matcher.end()));
                    lastIndex = matcher.end();
                }
            } catch (Exception e) {
                e.printStackTrace();
//                LogUtil.e(TAG + " cutStringByImgTag error: ", e.getMessage());
            }
//            LogUtil.e(TAG + " cutStringByImgTag end matcher.find:@ lastIndex=@ targetStr.length=", matcher.find(), lastIndex, targetStr.length());
            if (lastIndex != targetStr.length()) {
                splitTextList.add(targetStr.substring(lastIndex));
//                LogUtil.e(TAG + " cutStringByImgTag lastIndex != targetStr.length() splitTextList.add:", targetStr.substring(lastIndex));
            }
            // 处理splitTextList的结果
            //将splitTextList里的<br/>替换为""
            for (int i = 0; i < splitTextList.size(); i++) {
                String splitText = splitTextList.get(i);
                if (splitText.contains("textentryinteraction")) {
                    splitTextList.set(i, splitText.replaceAll("<textentryinteraction.*?</textentryinteraction>", "_______"));
                }
                // 处理img标签后面的文本内容
                if (splitText.contains("<img") && splitText.contains(">")) {
                    if (splitText.indexOf("<img") > 0 || splitText.indexOf(">") < splitText.length() - 1) {
                        if (splitText.indexOf("<img") > 0) {
                            int imgTagIndex = splitText.indexOf("<img") - 1;
                            String textBeforeImg = splitText.substring(0, imgTagIndex);
                            if (!TextUtils.isEmpty(textBeforeImg)){
                                resultList.add(textBeforeImg);
                                splitText = splitText.substring(imgTagIndex);
//                                LogUtil.e(TAG + " cutStringByImgTag resultList.add(textBeforeImg) 添加非空格数据:");
                            }
//                            LogUtil.e(TAG + " cutStringByImgTag resultList.add(textBeforeImg):", textBeforeImg);
                        }
                        if (splitText.length() > splitText.indexOf(">") + 1) {
                            String imgTag = splitText.substring(0, splitText.indexOf(">") + 1);
                            String textAfterImg = splitText.substring(splitText.indexOf(">") + 1);
                            resultList.add(imgTag);
//                            LogUtil.e(TAG + " cutStringByImgTag resultList.add(imgTag):", imgTag);
                            resultList.add(textAfterImg);
//                            LogUtil.e(TAG + " cutStringByImgTag resultList.add(textAfterImg):", textAfterImg);
                        }
                    } else {
                        resultList.add(splitText);
//                        LogUtil.e(TAG + " cutStringByImgTag resultList.add(text):", splitText);
                    }
                } else {
                    resultList.add(splitText);
//                    LogUtil.e(TAG + " cutStringByImgTag resultList.add(text):", splitText);
                }
            }
            for (int i = 0; i < resultList.size(); i++) {
                // 恢复特殊标记为原始标签
                String text = resultList.get(i);
                text = text.replaceAll("##SUP_START##", "<sup>");
                text = text.replaceAll("##SUP_END##", "</sup>");
                text = text.replaceAll("##SUB_START##", "<sub>");
                text = text.replaceAll("##SUB_END##", "</sub>");
                resultList.set(i, text);
            }
        } catch (Exception e) {
            resultList.clear();
            resultList.add(targetStr);
            LogUtil.e(TAG + " cutStringByImgTag return targetStr error: ", e.getMessage());
        }
        LogUtil.eJson(TAG + " cutStringByImgTag resultList:", resultList);
        return resultList;
    }

    /**
     * 切割字符串，将html里的文本和img标签碎片化，如"文本1<img>文本2"转换为"文本1"、"<img>"、"文本2"
     *
     * @param html
     */
    public static List<String> cutContent(String html) {
        List<String> listStr = new ArrayList<>();
        Pattern pattern = Pattern.compile("<img.*?src=\\\'(.*?)\\\'.*?>");
        Matcher matcher = pattern.matcher(html);
        int lastIndex = 0;
        while (matcher.find()) {
            if (matcher.start() > lastIndex) {
                listStr.add(html.substring(lastIndex, matcher.start()));

            }
            listStr.add(html.substring(matcher.start(), matcher.end()));
            lastIndex = matcher.end();
        }
        if (lastIndex != html.length()) {
            listStr.add(html.substring(lastIndex, html.length()));
        }
        return listStr;
    }


    /**
     * 获取img标签中的src值
     *
     * @param content
     * @return
     */
    public static String getImgSrc(String content) {
        String str_src = null;
        //目前img标签标示有3种表达式
        //<img alt="" src="1.jpg"/>   <img alt="" src="1.jpg"></img>     <img alt="" src="1.jpg">
        //开始匹配content中的<img />标签
        Pattern p_img = Pattern.compile("<(img|IMG)(.*?)(/>|></img>|>)");
        Matcher m_img = p_img.matcher(content);
        boolean result_img = m_img.find();
        if (result_img) {
            while (result_img) {
                //获取到匹配的<img />标签中的内容
                String str_img = m_img.group(2);

                //开始匹配<img />标签中的src
                Pattern p_src = Pattern.compile("(src|SRC)=(\"|\')(.*?)(\"|\')");
                Matcher m_src = p_src.matcher(str_img);
                if (m_src.find()) {
                    str_src = m_src.group(3);
                }
                //结束匹配<img />标签中的src

                //匹配content中是否存在下一个<img />标签，有则继续以上步骤匹配<img />标签中的src
                result_img = m_img.find();
            }
        }
        return str_src;
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
        CharacterStyle span = null;

        Pattern p = Pattern.compile(target);
        Matcher m = p.matcher(text);
        while (m.find()) {
            span = new ForegroundColorSpan(Color.parseColor("#EE5C42"));// 需要重复！
            spannable.setSpan(span, m.start(), m.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    /**
     * 从html文本中提取图片地址，或者文本内容
     *
     * @param html       传入html文本
     * @param isGetImage true获取图片，false获取文本
     * @return
     */
    public static ArrayList<String> getTextFromHtml(String html, boolean isGetImage) {
        ArrayList<String> imageList = new ArrayList<>();
        ArrayList<String> textList = new ArrayList<>();
        //根据img标签分割出图片和字符串
        List<String> list = cutStringByImgTag(html);
        for (int i = 0; i < list.size(); i++) {
            String text = list.get(i);
            if (text.contains("<img") && text.contains("src=")) {
                //从img标签中获取图片地址
                String imagePath = getImgSrc(text);
                imageList.add(imagePath);
            } else {
                textList.add(text);
            }
        }
        //判断是获取图片还是文本
        if (isGetImage) {
            return imageList;
        } else {
            return textList;
        }
    }


    public static String getQuestionTypeStr(String type) {
        String strType = "";
        switch (type) {
            case "single_choice":
                strType = "单选题";
                break;
            case "multiple_choice":
                strType = "多选题";
                break;
            case "judgment":
                strType = "判断题";
                break;
            case "completion":
                strType = "填空题";
                break;
            case "complex":
                strType = "复合题";
                break;
            case "subjectivity":
                strType = "问答题";
                break;
        }
        return strType;
    }

}
