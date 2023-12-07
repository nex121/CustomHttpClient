package org.example;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class CustomHttpClient {
    private int connectTimeout = 10000; // 默认连接超时时间为10秒
    private int readTimeout = 10000; // 默认读取超时时间为10秒
    private boolean trustAllCertificates = false; // 是否信任所有SSL证书
    private Map<String, String> headers; // 设置headers
    private Proxy proxy;

    public CustomHttpClient() {
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public void setHttpProxy(String host, int port) {
        this.proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
    }

    public void setSocksProxy(String host, int port) {
        this.proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
    }

    public void setHeader(String name, String value) {
        if (headers == null) {
            headers = new java.util.HashMap<>();
        }
        headers.put(name, value);
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setJson() {
        if (headers == null) {
            headers = new java.util.HashMap<>();
        }
        headers.put("Content-Type", "application/json");
    }


    public HttpResponse sendGetRequest(String url) throws IOException {
        HttpURLConnection connection = createConnection(url, "GET", headers);
        return executeRequest(connection);
    }

    public HttpResponse sendPostRequest(String url, String postData) throws IOException {
        HttpURLConnection connection = createConnection(url, "POST", headers);
        // 设置为POST请求
        connection.setDoOutput(true);

        // 写入请求体数据
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(postData.getBytes(StandardCharsets.UTF_8));
        }

        return executeRequest(connection);
    }

    private HttpURLConnection createConnection(String url, String method, Map<String, String> headers) throws IOException {
        URL urlObj = new URL(url);
        HttpURLConnection connection;
        if (proxy != null) {
            connection = (HttpURLConnection) urlObj.openConnection(proxy);
        } else {
            connection = (HttpURLConnection) urlObj.openConnection();
        }

        // 设置请求方法
        connection.setRequestMethod(method);

        // 设置连接超时和读取超时时间
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);

        // 设置请求头
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        // 处理HTTPS信任证书
        if (url.toLowerCase().startsWith("https://") && trustAllCertificates) {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
            httpsConnection.setSSLSocketFactory(TrustAllSSLSocketFactory.get());
            httpsConnection.setHostnameVerifier((hostname, session) -> true);
        }

        return connection;
    }

    private HttpResponse executeRequest(HttpURLConnection connection) throws IOException {
        int statusCode = connection.getResponseCode();

        // 获取响应头
        Map<String, List<String>> headers = connection.getHeaderFields();

        // 读取响应体
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                statusCode < HttpURLConnection.HTTP_BAD_REQUEST ? connection.getInputStream() : connection.getErrorStream()))) {

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            return new HttpResponse(statusCode, response.toString(), headers);
        }
    }

    public static class HttpResponse {
        private final int statusCode;
        private final String body;
        private final Map<String, List<String>> headers;

        public HttpResponse(int statusCode, String body, Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }

        public Map<String, List<String>> getHeaders() {
            return headers;
        }

        public String getHeader(String headerName) {
            if (headers != null) {
                List<String> values = headers.get(headerName);
                if (values != null && !values.isEmpty()) {
                    return values.get(0);
                }
            }
            return null;
        }
    }

    public static class TrustAllSSLSocketFactory {
        public static SSLSocketFactory get() {
            try {
                // 创建一个信任所有证书的SSLContext
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }}, new SecureRandom());

                return sslContext.getSocketFactory();
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException("Failed to create TrustAllSSLSocketFactory", e);
            }
        }
    }

    public static void main(String[] args) {
        try {
            // 示例用法
            CustomHttpClient httpClient = new CustomHttpClient();
            String url = "http://httpbin.org/get";

            // 使用代理
//            httpClient.setSocksProxy("127.0.0.1", 10808);
            httpClient.setHttpProxy("127.0.0.1", 8080);

            // 忽略证书
            httpClient.setTrustAllCertificates(true);

            // 设置http头
            httpClient.setHeader("User-Agent", "Custom User Agent");

            // 发送GET请求
            HttpResponse getResponse = httpClient.sendGetRequest(url);
            System.out.println("GET Response Code: " + getResponse.getStatusCode());
            System.out.println("GET Response Headers: " + getResponse.getHeaders());
            System.out.println("GET Response Header: " + getResponse.getHeader("Server"));
            System.out.println("GET Response Body: " + getResponse.getBody());

            // 发送POST请求
            String postData = "title=test&body=test&userId=1";

            httpClient.setJson();
            HttpResponse postResponse = httpClient.sendPostRequest(url, postData);
            System.out.println("POST Response Code: " + postResponse.getStatusCode());
            System.out.println("POST Response Body: " + postResponse.getBody());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
