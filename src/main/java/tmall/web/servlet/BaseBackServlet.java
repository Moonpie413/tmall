package tmall.web.servlet;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tmall.service.*;
import tmall.util.ImageUtil;
import tmall.util.Page;

import javax.imageio.ImageIO;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 作者: wangxh
 * 创建日期: 17-3-4
 * 简介:
 */
public abstract class BaseBackServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BaseBackServlet.class);

    public abstract String add(HttpServletRequest request, HttpServletResponse response, Page page) ;
    public abstract String delete(HttpServletRequest request, HttpServletResponse response, Page page) ;
    public abstract String edit(HttpServletRequest request, HttpServletResponse response, Page page) ;
    public abstract String update(HttpServletRequest request, HttpServletResponse response, Page page) ;
    public abstract String list(HttpServletRequest request, HttpServletResponse response, Page page) ;

    protected final CategoryService categoryService = new CategoryService();
    protected final ProductService productService = new ProductService();
    protected final ProductImageService productImageService = new ProductImageService();
    protected final PropertyService propertyService = new PropertyService();
    protected final PropertyValueService propertyValueService = new PropertyValueService();
    protected final UserService userService = new UserService();
    protected final OrderService orderService = new OrderService();
    protected final OrderItemService orderItemService = new OrderItemService();

    @Override
    public void init() throws ServletException {
        super.init();
    }

    /**
     * 利用反射根据传过来的method参数判断需要执行哪个方法
     * 具体方法由子类实现
     * @param req request
     * @param resp response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String startStr = req.getParameter("page.start");
        String countStr = req.getParameter("page.count");
        int start = Integer.parseInt(
                startStr == null || "".equals(startStr) ? "0" : req.getParameter("page.start"));
        int count = Integer.parseInt(
                countStr == null || "".equals(countStr) ? "5" : req.getParameter("page.count"));
        Page page = new Page(start, count);
        String methodStr = (String) req.getAttribute("method");
        // 设置默认的方法
        if (methodStr == null) methodStr = "list";
        Method method;
        try {
            method = this.getClass().getMethod(methodStr, HttpServletRequest.class, HttpServletResponse.class,
                    Page.class);
            final String[] redirect = new String[1];
            try {
                redirect[0] = (String) method.invoke(this, req, resp, page);
            } catch (InvocationTargetException e) {
                redirect[0] = "%服务器出错</br>" + Arrays.toString(e.getStackTrace());
                Arrays.stream(e.getStackTrace()).forEach( (s) -> redirect[0] = redirect[0] + s + "</br>" );
                logger.error("方法 {} 返回值为空", methodStr, e);
            }
                /* @: 客户端跳转
                 * %: 页面输出
                 * others: 服务器跳转
                 */
            logger.info("跳转: " + redirect[0]);
            if(redirect[0].startsWith("@"))
                resp.sendRedirect(redirect[0].substring(1));
            else if(redirect[0].startsWith("%")) {
                resp.setContentType("text/html;charset=utf-8");
                resp.getWriter().print(redirect[0].substring(1));
            }
            else
                req.getRequestDispatcher(redirect[0]).forward(req, resp);
        } catch (NoSuchMethodException e) {
            logger.error("没有指定的方法{}", methodStr, e);
        } catch (IllegalAccessException e) {
            logger.error("方法 {} 反射出错", methodStr, e);
        }
    }

    /**
     * 从request中取出上传文件并返回文件流
     * 数据从表单提交后会分成两份, 根据isFromField判断是文件还是表单数据
     * @param req
     * @param params
     * @return
     */
    public InputStream parseUpload(HttpServletRequest req, Map<String, String> params) {
        DiskFileItemFactory fileItemFactory = new DiskFileItemFactory();
        fileItemFactory.setSizeThreshold(1024 * 10240);
        ServletFileUpload fileUpload = new ServletFileUpload(fileItemFactory);
        List<FileItem> fileItems = null;
        InputStream in = null;
        try {
            fileItems = fileUpload.parseRequest(req);
            if (fileItems != null) {
                for (FileItem item : fileItems) {
                    if (!item.isFormField()) in = item.getInputStream();
                    else {
                        String paramName = item.getFieldName();
                        String paramValue = new String(item.getString().getBytes("ISO-8859-1"), "UTF-8");
                        params.put(paramName, paramValue);
                    }
                }
            }
        } catch (FileUploadException e) {
            logger.error("从请求中解析文件异常", e);
        } catch (IOException e) {
            logger.error("文件读取异常", e);
        }
        return in;
    }
        /**
     * 文件保存
     * @param file
     * @param in
     */
    protected void saveFile(File file, InputStream in) {
        logger.info("保存图片文件: {}", file.getName());
        try {
            if ((in != null && in.available() != 0)) {
                try (FileOutputStream out = new FileOutputStream(file)){
                    byte[] buff = new byte[1024];
                    while (in.read(buff) != -1) {
                        out.write(buff);
                    }
                    out.flush();
                    BufferedImage img = ImageUtil.change2jpg(file);
                    ImageIO.write(img, "jpg", file);
                }
            }
        } catch (IOException e) {
            logger.error("文件 {} 保存失败", file.getName(), e);
        }
    }

}
