package org.schabi.newpipe;

// copy from here
// https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.19.5/extractor/src/test/java/org/schabi/newpipe/DownloaderTestImpl.java

/*
 * Created by Christian Schabesberger on 28.01.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * DownloaderTestImpl.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */


import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.Localization;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

public class DownloaderTestImpl extends Downloader {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:68.0) Gecko/20100101 Firefox/68.0";
    private static final String DEFAULT_HTTP_ACCEPT_LANGUAGE = "en";

    private static DownloaderTestImpl instance = null;

    private DownloaderTestImpl() {
    }

    public static DownloaderTestImpl getInstance() {
        if (instance == null) {
            synchronized (DownloaderTestImpl.class) {
                if (instance == null) {
                    instance = new DownloaderTestImpl();
                }
            }
        }
        return instance;
    }

    private void setDefaultHeaders(URLConnection connection) {
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Language", DEFAULT_HTTP_ACCEPT_LANGUAGE);
    }

    @Override
    public Response execute(@Nonnull Request request) throws IOException, ReCaptchaException {
        final String httpMethod = request.httpMethod();
        final String url = request.url();
        final Map<String, List<String>> headers = request.headers();
        @Nullable final byte[] dataToSend = request.dataToSend();
        @Nullable final Localization localization = request.localization();

        final HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();

        connection.setConnectTimeout(30 * 1000); // 30s
        connection.setReadTimeout(30 * 1000); // 30s
        connection.setRequestMethod(httpMethod);

        setDefaultHeaders(connection);

        for (Map.Entry<String, List<String>> pair : headers.entrySet()) {
            final String headerName = pair.getKey();
            final List<String> headerValueList = pair.getValue();

            if (headerValueList.size() > 1) {
                connection.setRequestProperty(headerName, null);
                for (String headerValue : headerValueList) {
                    connection.addRequestProperty(headerName, headerValue);
                }
            } else if (headerValueList.size() == 1) {
                connection.setRequestProperty(headerName, headerValueList.get(0));
            }
        }

        @Nullable OutputStream outputStream = null;
        @Nullable InputStreamReader input = null;
        try {
            if (dataToSend != null && dataToSend.length > 0) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Length", dataToSend.length + "");
                outputStream = connection.getOutputStream();
                outputStream.write(dataToSend);
            }

            final InputStream inputStream = connection.getInputStream();
            final StringBuilder response = new StringBuilder();

            // Not passing any charset for decoding here... something to keep in mind.
            input = new InputStreamReader(inputStream);

            int readCount;
            char[] buffer = new char[32 * 1024];
            while ((readCount = input.read(buffer)) != -1) {
                response.append(buffer, 0, readCount);
            }

            final int responseCode = connection.getResponseCode();
            final String responseMessage = connection.getResponseMessage();
            final Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            final String latestUrl = connection.getURL().toString();

            return new Response(responseCode, responseMessage, responseHeaders, response.toString(), latestUrl);
        } catch (Exception e) {
            final int responseCode = connection.getResponseCode();

            /*
             * HTTP 429 == Too Many Request
             * Receive from Youtube.com = ReCaptcha challenge request
             * See : https://github.com/rg3/youtube-dl/issues/5138
             */
            if (responseCode == 429) {
                throw new ReCaptchaException("reCaptcha Challenge requested", url);
            } else if (responseCode != -1) {
                final String latestUrl = connection.getURL().toString();
                return new Response(responseCode, connection.getResponseMessage(), connection.getHeaderFields(), null, latestUrl);
            }

            throw new IOException("Error occurred while fetching the content", e);
        } finally {
            if (outputStream != null) outputStream.close();
            if (input != null) input.close();
        }
    }
}
