package com.atlassian.buildeng.kubernetes.metrics;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;


public class QueryPrometheus {
    private static final Integer MAX_RETRIES = 3;
    private static final Integer RETRY_DELAY_SECONDS = 1;

    /**
     * Static method to query prometheus.
     */
    public static JSONObject query(String prometheusUrl, String query, String stepPeriod, Long start, Long end)
        throws URISyntaxException, IOException {
        URI uri = new URIBuilder(prometheusUrl)
            .setPath("api/v1/query_range")
            .setParameter("query", query)
            .setParameter("step", stepPeriod)
            .setParameter("start", Long.toString(start))
            .setParameter("end", Long.toString(end))
            .build();

        String response = "";
        for (int retryCount = 0; retryCount < MAX_RETRIES; retryCount++) {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Charset", "UTF-8");
            connection.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
            connection.setReadTimeout((int) Duration.ofSeconds(60).toMillis());

            try {
                response = IOUtils.toString(connection.getInputStream(), "UTF-8");
            } catch (IOException e) {
                //Wait 1s before retrying
                if (retryCount < MAX_RETRIES - 1) {
                    try {
                        TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
                    } catch (InterruptedException ex) {
                        continue;
                    }
                } else {
                    //If request fails on the last try, throw the exception
                    throw e;
                }
            } finally {
                connection.disconnect();
            }
        }
        return new JSONObject(response);

    }
}
