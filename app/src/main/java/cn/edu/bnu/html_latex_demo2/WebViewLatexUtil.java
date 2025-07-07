package cn.edu.bnu.html_latex_demo2;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * @CreateDate: 2025/6/11
 * @author: jingjie
 * @desc: 用于WebView加载HTML和Latex的工具类
 */
public class WebViewLatexUtil {
    private static final String TAG = "WebViewLatexUtil";
    private static volatile WebViewLatexUtil instance;
    private Context mContext;
    private WebViewListener listener;

    public WebViewLatexUtil(Context context, WebViewListener listener) {
        this.mContext = context;
        this.listener = listener;
    }

    /**
     * 准备用于WebView加载的HTML内容，包含KaTeX的CSS和JS，并处理<latex>标签。
     *
     * @param originalHtml 原始的HTML字符串
     * @return 配置好的WebView实例，已加载HTML内容
     */
    public void renderContent(WebView webView, String originalHtml) {
//        设置WebView的LayoutParams，使其能被正确添加到LinearLayout
       ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
               ViewGroup.LayoutParams.WRAP_CONTENT,
               ViewGroup.LayoutParams.WRAP_CONTENT // 高度自适应
       );
       webView.setLayoutParams(params);

        // 配置WebView设置
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDefaultTextEncodingName("UTF-8");
        // 缩放控制（可选，如果不想让用户缩放可以禁用）
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false); // 隐藏缩放按钮

        // 优化缓存
        String appCachePath = mContext.getCacheDir().getAbsolutePath();
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT); // 默认缓存模式

        // 提高渲染质量
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);

        // 允许混合内容（HTTPS页面加载HTTP资源）
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // 隐藏滚动条
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        // 注入JS接口
        webView.addJavascriptInterface(new JsBridge(), "AndroidBridge");

        loadMathContent(webView, originalHtml);
    }

    /**
     * 加载HTML内容并渲染为WebView
     * @param originalHtml
     * @return WebView
     */
    public WebView renderContent(String originalHtml){
        WebView webView = new WebView(mContext);
        renderContent(webView, originalHtml);
        return webView;
    }

    private void loadMathContent(WebView webView, String htmlContent) {
        // 构建完整的HTML页面结构
        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html>\n")
                .append("<html>\n")
                .append("<head>\n")
                .append("    <meta charset=\"UTF-8\">\n")
                .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1\">\n")
                .append("    <title>Math Content</title>\n")
                .append("    <style>\n")
                .append("        ::-webkit-scrollbar {\n")
                .append("            display: none;\n")
                .append("        }\n")
                .append("        body {\n")
                .append("            font-size: 14px;\n")
                .append("            line-height: 1.2;\n")
                .append("            color: #333;\n")
                .append("            margin: 0;\n")
                .append("            padding: 0;\n")
                .append("            overflow-x: hidden;\n")
                .append("            display: inline-block;\n")
                .append("            width: auto;\n")
                .append("        }\n")
                .append("        #content {\n")
                .append("            display: inline-block;\n")
                .append("            width: auto;\n")
                .append("        }\n")
                .append("        .math-container {\n")
                .append("            background-color: #fff;\n")
//                .append("            padding: 10px;\n")
//                .append("            border-radius: 5px;\n")
//                .append("            margin: 10px 0;\n")
                .append("            text-align: left;\n")
                .append("            overflow-x: auto;\n")
                .append("        }\n")
                .append("        .loading {\n")
                .append("            text-align: left;\n")
//                .append("            padding: 20px;\n")
                .append("            color: #fff;\n")
                .append("        }\n")
                .append("    </style>\n")
                .append("    <script>\n")
                .append("        function renderMath() {\n")
                .append("            var latexElements = document.querySelectorAll('latex');\n")
                .append("            latexElements.forEach(function(el) {\n")
                .append("                var container = document.createElement('div');\n")
                .append("                container.className = 'math-container';\n")
                .append("                container.innerHTML = '\\\\[' + el.textContent + '\\\\]';\n")
                .append("                el.parentNode.replaceChild(container, el);\n")
                .append("            });\n")
                .append("\n")
                .append("            var imgs = document.querySelectorAll('img');\n")
                .append("            imgs.forEach(function(img) {\n")
                .append("                img.style.cursor = 'pointer';\n")
                .append("                img.onclick = function() {\n")
                .append("                    AndroidBridge.onImageClick(img.src);\n")
                .append("                };\n")
                .append("            });\n")
                .append("\n")
                .append("            MathJax.Hub.Config({\n")
                .append("                extensions: [\"tex2jax.js\", \"mhchem.js\"],\n")
                .append("                jax: [\"input/TeX\", \"output/HTML-CSS\"],\n")
                .append("                tex2jax: {\n")
                .append("                    inlineMath: [ ['$','$'], [\"\\\\(\",\"\\\\)\"] ],\n")
                .append("                    displayMath: [ ['$$','$$'], [\"\\\\[\",\"\\\\]\"] ],\n")
                .append("                    processEscapes: true\n")
                .append("                },\n")
                .append("                TeX: {\n")
                .append("                    extensions: [\"mhchem.js\"]\n")
                .append("                },\n")
                .append("                \"HTML-CSS\": { \n")
                .append("                    availableFonts: [\"TeX\"],\n")
                .append("                    scale: 100\n")
                .append("                },\n")
                .append("               messageStyle: \"none\" \n")
                .append("            });\n")
                .append("\n")
                .append("            MathJax.Hub.Queue([\"Typeset\", MathJax.Hub]);\n")
                .append("        }\n")
                .append("    </script>\n")
                .append("    <script type=\"text/javascript\" src=\"https://eval-oss.aicfe.cn/slpstatic/web/MathJax-2.7.5/MathJax.js?config=TeX-AMS-MML_HTMLorMML\">MathJax.Hub.Config({tex2jax: {inlineMath: [['$','$'], ['(',')']]}});</script>\n")
                .append("    <script type=\"text/javascript\" src=\"https://eval-oss.aicfe.cn/slpstatic/web/MathJax-2.7.5/extensions/TeX/extpfeil.js\"></script>\n")
                .append("    <script type=\"text/javascript\" src=\"https://eval-oss.aicfe.cn/slpstatic/web/MathJax-2.7.5/extensions/TeX/mhchem.js\"></script>\n")
                .append("</head>\n")
                .append("<body onload=\"renderMath()\">\n")
                .append("    <div id=\"content\">").append(htmlContent).append("</div>\n")
                .append("</body>\n")
                .append("</html>");

        Log.e(TAG, "Final HTML to load in WebView: " + fullHtml.toString());
        // 加载HTML内容
        webView.loadDataWithBaseURL(
                "file:///android_asset/",  // 用于解析相对路径的基础URL
                fullHtml.toString(),
                "text/html",
                "UTF-8",
                null
        );
    }

    private class JsBridge {
        @JavascriptInterface
        public void onImageClick(String url) {
            if (listener != null) {
                listener.onImageClick(url);
            }
        }
    }

    public interface WebViewListener {
        void onImageClick(String url);
    }
}